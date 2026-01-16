package fyi.goodbye.fridgy.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.models.ShoppingListItem
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.repositories.ProductRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for managing shopping list UI state and operations.
 */
class ShoppingListViewModel(private val fridgeId: String) : ViewModel() {
    private val repository = FridgeRepository()
    private val productRepository = ProductRepository()
    private var presenceJob: Job? = null

    data class ShoppingListItemWithProduct(
        val item: ShoppingListItem,
        val productName: String,
        val productBrand: String
    )

    sealed interface UiState {
        data object Loading : UiState

        data class Success(val items: List<ShoppingListItemWithProduct>) : UiState

        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Product>>(emptyList())
    val searchResults: StateFlow<List<Product>> = _searchResults.asStateFlow()

    private val _activeViewers = MutableStateFlow<List<FridgeRepository.ActiveViewer>>(emptyList())
    val activeViewers: StateFlow<List<FridgeRepository.ActiveViewer>> = _activeViewers.asStateFlow()

    init {
        loadShoppingList()
        observeActiveViewers()
    }

    /**
     * Starts broadcasting presence and keeps it alive with periodic updates.
     */
    fun startPresence() {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            // Set initial presence
            repository.setShoppingListPresence(fridgeId)
            
            // Update presence every 15 seconds to keep it fresh
            while (isActive) {
                delay(15_000)
                repository.setShoppingListPresence(fridgeId)
            }
        }
    }

    /**
     * Stops broadcasting presence when user leaves the screen.
     */
    fun stopPresence() {
        presenceJob?.cancel()
        viewModelScope.launch {
            repository.removeShoppingListPresence(fridgeId)
        }
    }

    private fun observeActiveViewers() {
        viewModelScope.launch {
            repository.getShoppingListPresence(fridgeId).collect { viewers ->
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                // Filter out current user from the list
                _activeViewers.value = viewers.filter { it.userId != currentUserId }
            }
        }
    }

    init {
        loadShoppingList()
    }

    private fun loadShoppingList() {
        viewModelScope.launch {
            repository.getShoppingListItems(fridgeId).collect { items ->
                // Fetch product names for each item
                val itemsWithProducts = items.map { item ->
                    // Use custom name if available (manual entry), otherwise fetch from DB
                    if (item.customName.isNotEmpty()) {
                        ShoppingListItemWithProduct(
                            item = item,
                            productName = item.customName,
                            productBrand = ""
                        )
                    } else {
                        val product = productRepository.getProductInfo(item.upc)
                        ShoppingListItemWithProduct(
                            item = item,
                            productName = product?.name ?: "Unknown Product",
                            productBrand = product?.brand ?: ""
                        )
                    }
                }
                _uiState.value = UiState.Success(itemsWithProducts)
            }
        }
    }

    fun updateItemPickup(
        upc: String,
        obtainedQuantity: Int,
        totalQuantity: Int
    ) {
        viewModelScope.launch {
            try {
                repository.updateShoppingListItemPickup(
                    fridgeId = fridgeId,
                    upc = upc,
                    obtainedQuantity = obtainedQuantity,
                    totalQuantity = totalQuantity
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to update item: ${e.message}")
            }
        }
    }

    fun removeItem(upc: String) {
        viewModelScope.launch {
            try {
                repository.removeShoppingListItem(fridgeId, upc)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to remove item: ${e.message}")
            }
        }
    }

    fun addItem(
        upc: String,
        quantity: Int,
        store: String,
        customName: String = ""
    ) {
        viewModelScope.launch {
            try {
                repository.addShoppingListItem(fridgeId, upc, quantity, store, customName)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to add item: ${e.message}")
            }
        }
    }

    fun addManualItem(
        name: String,
        quantity: Int,
        store: String
    ) {
        viewModelScope.launch {
            try {
                // Generate a unique ID for manual entries
                val generatedId = "manual_${System.currentTimeMillis()}"
                repository.addShoppingListItem(
                    fridgeId = fridgeId,
                    upc = generatedId,
                    quantity = quantity,
                    store = store,
                    customName = name
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to add item: ${e.message}")
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
        } else {
            searchProducts(query)
        }
    }

    private fun searchProducts(query: String) {
        viewModelScope.launch {
            try {
                val results = productRepository.searchProductsByName(query)
                _searchResults.value = results
            } catch (e: Exception) {
                // Silently fail search - don't disrupt main UI
                _searchResults.value = emptyList()
            }
        }
    }

    fun completeShopping(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.completeShoppingSession(fridgeId)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to complete shopping: ${e.message}")
            }
        }
    }

    companion object {
        fun provideFactory(fridgeId: String): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ShoppingListViewModel(fridgeId) as T
                }
            }
    }
}
