package fyi.goodbye.fridgy.ui.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.models.ShoppingListItem
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.repositories.HouseholdRepository
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
 * 
 * The shopping list is now at the household level, shared across all fridges.
 */
class ShoppingListViewModel(
    private val householdId: String,
    private val productRepository: ProductRepository
) : ViewModel() {
    private val repository = HouseholdRepository()
    private val fridgeRepository = FridgeRepository()
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

    private val _activeViewers = MutableStateFlow<List<HouseholdRepository.ActiveViewer>>(emptyList())
    val activeViewers: StateFlow<List<HouseholdRepository.ActiveViewer>> = _activeViewers.asStateFlow()

    private val _availableFridges = MutableStateFlow<List<Fridge>>(emptyList())
    val availableFridges: StateFlow<List<Fridge>> = _availableFridges.asStateFlow()

    init {
        loadShoppingList()
        observeActiveViewers()
        loadAvailableFridges()
    }

    /**
     * Starts broadcasting presence and keeps it alive with periodic updates.
     */
    fun startPresence() {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            // Set initial presence
            repository.setShoppingListPresence(householdId)
            
            // Update presence every 15 seconds to keep it fresh
            while (isActive) {
                delay(15_000)
                repository.setShoppingListPresence(householdId)
            }
        }
    }

    /**
     * Stops broadcasting presence when user leaves the screen.
     */
    fun stopPresence() {
        presenceJob?.cancel()
        viewModelScope.launch {
            repository.removeShoppingListPresence(householdId)
        }
    }

    private fun observeActiveViewers() {
        viewModelScope.launch {
            repository.getShoppingListPresence(householdId).collect { viewers ->
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                // Filter out current user from the list
                _activeViewers.value = viewers.filter { it.userId != currentUserId }
            }
        }
    }

    private fun loadAvailableFridges() {
        viewModelScope.launch {
            fridgeRepository.getFridgesForHousehold(householdId).collect { fridges ->
                _availableFridges.value = fridges
            }
        }
    }

    private fun loadShoppingList() {
        viewModelScope.launch {
            repository.getShoppingListItems(householdId).collect { items ->
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
        totalQuantity: Int,
        targetFridgeId: String = ""
    ) {
        viewModelScope.launch {
            try {
                repository.updateShoppingListItemPickup(
                    householdId = householdId,
                    upc = upc,
                    obtainedQuantity = obtainedQuantity,
                    totalQuantity = totalQuantity,
                    targetFridgeId = targetFridgeId
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to update item: ${e.message}")
            }
        }
    }

    fun removeItem(upc: String) {
        viewModelScope.launch {
            try {
                repository.removeShoppingListItem(householdId, upc)
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
                repository.addShoppingListItem(householdId, upc, quantity, store, customName)
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
                    householdId = householdId,
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
                repository.completeShoppingSession(householdId)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to complete shopping: ${e.message}")
            }
        }
    }

    /**
     * Checks if a UPC exists in the product database.
     */
    suspend fun checkProductExists(upc: String): Product? {
        return try {
            productRepository.getProductInfo(upc)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Links a manual shopping list item to a real product by updating its UPC.
     * This removes the old manual entry and creates a new one with the real UPC.
     */
    fun linkManualItemToProduct(oldManualUpc: String, newUpc: String, quantity: Int, store: String, customName: String) {
        viewModelScope.launch {
            try {
                // Remove old manual item
                repository.removeShoppingListItem(householdId, oldManualUpc)
                
                // Add new item with real UPC
                repository.addShoppingListItem(
                    householdId = householdId,
                    upc = newUpc,
                    quantity = quantity,
                    store = store,
                    customName = "" // Clear custom name since we now have real product
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to link product: ${e.message}")
            }
        }
    }

    /**
     * Creates a new product in the database with image upload and links it to the manual shopping list item.
     * 
     * @param oldManualUpc The UPC of the manual item to replace
     * @param newUpc The UPC for the new product
     * @param name Product name
     * @param brand Product brand (optional)
     * @param category Product category
     * @param imageUri URI of the product image to upload (optional)
     * @param quantity Quantity needed
     * @param store Store location (optional)
     * @param onSuccess Callback invoked after successful creation and linking
     */
    fun createProductAndLink(
        oldManualUpc: String,
        newUpc: String,
        name: String,
        brand: String,
        category: String,
        imageUri: android.net.Uri?,
        quantity: Int,
        store: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Create product object
                val product = Product(
                    upc = newUpc,
                    name = name,
                    brand = brand,
                    category = category,
                    imageUrl = "",
                    lastUpdated = System.currentTimeMillis()
                )
                
                // Save product with image to database
                productRepository.saveProductWithImage(product, imageUri)
                
                // Link manual item to new product
                linkManualItemToProduct(
                    oldManualUpc = oldManualUpc,
                    newUpc = newUpc,
                    quantity = quantity,
                    store = store,
                    customName = ""
                )
                
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to create product: ${e.message}")
            }
        }
    }

    companion object {
        fun provideFactory(
            householdId: String,
            productRepository: ProductRepository? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                    val repo = productRepository ?: ProductRepository(app.applicationContext)
                    return ShoppingListViewModel(householdId, repo) as T
                }
            }
    }
}
