package fyi.goodbye.fridgy.ui.fridgeInventory

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.repositories.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Combined model for displaying an item with its global product details.
 */
data class InventoryItem(
    val item: Item,
    val product: Product
)

/**
 * ViewModel responsible for managing the inventory and basic details of a specific fridge.
 */
class FridgeInventoryViewModel(
    application: Application,
    private val fridgeRepository: FridgeRepository,
    private val productRepository: ProductRepository,
    private val fridgeId: String,
    initialFridgeName: String = ""
) : AndroidViewModel(application) {
    private val _displayFridgeState =
        MutableStateFlow<FridgeDetailUiState>(
            if (initialFridgeName.isNotBlank()) {
                FridgeDetailUiState.Success(
                    DisplayFridge(
                        id = fridgeId,
                        name = initialFridgeName,
                        createdByUid = "",
                        creatorDisplayName = "",
                        memberUsers = emptyList(),
                        pendingInviteUsers = emptyList(),
                        createdAt = 0L
                    )
                )
            } else {
                FridgeDetailUiState.Loading
            }
        )
    val displayFridgeState: StateFlow<FridgeDetailUiState> = _displayFridgeState.asStateFlow()

    private val _itemsUiState = MutableStateFlow<ItemsUiState>(ItemsUiState.Loading)
    val itemsUiState: StateFlow<ItemsUiState> = _itemsUiState.asStateFlow()

    // Local list of optimistic items to be merged with remote data for instant UI
    private val optimisticItems = MutableStateFlow<List<InventoryItem>>(emptyList())

    private val _isAddingItem = MutableStateFlow(false)
    val isAddingItem: StateFlow<Boolean> = _isAddingItem.asStateFlow()

    private val _addItemError = MutableStateFlow<String?>(null)
    val addItemError: StateFlow<String?> = _addItemError.asStateFlow()

    private val _pendingScannedUpc = MutableStateFlow<String?>(null)
    val pendingScannedUpc: StateFlow<String?> = _pendingScannedUpc.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadFridgeDetails()
        listenToInventoryUpdates()
    }

    private fun loadFridgeDetails() {
        viewModelScope.launch {
            // OPTIMIZATION: Don't show loading state, repository will use cache first
            // Skip fetching user details (fetchUserDetails = false) since inventory screen doesn't need member list
            try {
                val fridge = fridgeRepository.getFridgeById(fridgeId, fetchUserDetails = false)
                if (fridge != null) {
                    _displayFridgeState.value = FridgeDetailUiState.Success(fridge)
                } else {
                    _displayFridgeState.value = FridgeDetailUiState.Error(getApplication<Application>().getString(R.string.error_fridge_not_found))
                }
            } catch (e: Exception) {
                _displayFridgeState.value = FridgeDetailUiState.Error(e.message ?: getApplication<Application>().getString(R.string.error_failed_to_load_fridge))
            }
        }
    }

    private fun listenToInventoryUpdates() {
        viewModelScope.launch {
            _itemsUiState.value = ItemsUiState.Loading

            // Combine remote items with our local optimistic items and search query
            combine(
                fridgeRepository.getItemsForFridge(fridgeId),
                optimisticItems,
                _searchQuery
            ) { remoteItems, optimistic, query ->
                // Filter out optimistic items that have now been confirmed by remote data
                val remoteUpcs = remoteItems.map { it.upc }.toSet()
                val pendingOptimistic = optimistic.filter { it.item.upc !in remoteUpcs }

                // Map remote items to products (using cache for speed)
                val mappedRemote =
                    remoteItems.map { item ->
                        val product = productRepository.getProductInfo(item.upc) ?: Product(upc = item.upc, name = getApplication<Application>().getString(R.string.unknown_product))
                        InventoryItem(item, product)
                    }

                // Final display list: Remote items first, then any still-pending optimistic items
                val combinedList = mappedRemote + pendingOptimistic

                // Apply search filter if query is not empty
                if (query.isNotBlank()) {
                    combinedList.filter { inventoryItem ->
                        inventoryItem.product.name.contains(query, ignoreCase = true) ||
                            inventoryItem.product.brand.contains(query, ignoreCase = true) ||
                            inventoryItem.item.upc.contains(query, ignoreCase = true)
                    }
                } else {
                    combinedList
                }
            }
                .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions
                .collectLatest { combinedList ->
                    _itemsUiState.value = ItemsUiState.Success(combinedList)
                }
        }
    }

    /**
     * Checks if a scanned UPC exists in the global database.
     */
    fun onBarcodeScanned(upc: String) {
        viewModelScope.launch {
            try {
                val product = productRepository.getProductInfo(upc)
                if (product != null) {
                    addItemToFridge(upc)
                } else {
                    _pendingScannedUpc.value = upc
                }
            } catch (e: Exception) {
                Log.e("FridgeInventoryVM", "Error handling scan: ${e.message}")
            }
        }
    }

    /**
     * Saves a brand new product and adds it to the current fridge.
     * Uses a double-layered optimistic update for truly instant UI response.
     */
    fun createAndAddProduct(
        upc: String,
        name: String,
        brand: String,
        category: String,
        imageUri: Uri?
    ) {
        _pendingScannedUpc.value = null
        _isAddingItem.value = true

        viewModelScope.launch {
            val optimisticProduct =
                Product(
                    upc = upc,
                    name = name,
                    brand = brand,
                    category = category,
                    imageUrl = imageUri?.toString(),
                    lastUpdated = System.currentTimeMillis()
                )

            val optimisticItem =
                Item(
                    upc = "optimistic_$upc",
                    quantity = 1,
                    addedAt = System.currentTimeMillis()
                )

            // Layer 1: Inject into ViewModel's local state immediately
            optimisticItems.value = optimisticItems.value + InventoryItem(optimisticItem, optimisticProduct)

            try {
                // Layer 2: Push to Repository Cache
                productRepository.injectToCache(optimisticProduct)

                // Background tasks
                launch { productRepository.saveProductWithImage(optimisticProduct, imageUri) }
                launch { fridgeRepository.addItemToFridge(fridgeId, upc) }

                Log.d("FridgeInventoryVM", "Optimistic add successful for: $name")
            } catch (e: Exception) {
                Log.e("FridgeInventoryVM", "Failed to create product: ${e.message}")
                _addItemError.value = getApplication<Application>().getString(R.string.error_failed_to_add_item, e.message ?: "")
                // Rollback optimistic item on failure
                optimisticItems.value = optimisticItems.value.filter { it.item.upc != upc }
            } finally {
                _isAddingItem.value = false
            }
        }
    }

    private suspend fun addItemToFridge(upc: String) {
        try {
            fridgeRepository.addItemToFridge(fridgeId, upc)
        } catch (e: Exception) {
            _addItemError.value = getApplication<Application>().getString(R.string.error_failed_to_add_item, e.message ?: "")
        }
    }

    fun cancelPendingProduct() {
        _pendingScannedUpc.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    sealed interface FridgeDetailUiState {
        data object Loading : FridgeDetailUiState

        data class Success(val fridge: DisplayFridge) : FridgeDetailUiState

        data class Error(val message: String) : FridgeDetailUiState
    }

    sealed interface ItemsUiState {
        data object Loading : ItemsUiState

        data class Success(val items: List<InventoryItem>) : ItemsUiState

        data class Error(val message: String) : ItemsUiState
    }

    companion object {
        fun provideFactory(
            fridgeId: String,
            initialName: String = "",
            fridgeRepository: FridgeRepository = FridgeRepository(),
            productRepository: ProductRepository? = null
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                    val repo = productRepository ?: ProductRepository(app.applicationContext)
                    FridgeInventoryViewModel(app, fridgeRepository, repo, fridgeId, initialName)
                }
            }
        }
    }
}
