package fyi.goodbye.fridgy.ui.viewmodels

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.repositories.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing the inventory and basic details of a specific fridge.
 */
class FridgeInventoryViewModel(
    private val fridgeRepository: FridgeRepository,
    private val productRepository: ProductRepository,
    private val fridgeId: String
) : ViewModel() {

    private val _displayFridgeState = MutableStateFlow<FridgeDetailUiState>(FridgeDetailUiState.Loading)
    val displayFridgeState: StateFlow<FridgeDetailUiState> = _displayFridgeState.asStateFlow()

    private val _itemsUiState = MutableStateFlow<ItemsUiState>(ItemsUiState.Loading)
    val itemsUiState: StateFlow<ItemsUiState> = _itemsUiState.asStateFlow()

    private val _isAddingItem = MutableStateFlow(false)
    val isAddingItem: StateFlow<Boolean> = _isAddingItem.asStateFlow()

    private val _addItemError = MutableStateFlow<String?>(null)
    val addItemError: StateFlow<String?> = _addItemError.asStateFlow()

    private val _pendingScannedUpc = MutableStateFlow<String?>(null)
    val pendingScannedUpc: StateFlow<String?> = _pendingScannedUpc.asStateFlow()

    init {
        loadFridgeDetails()
        listenToInventoryUpdates()
    }

    private fun loadFridgeDetails() {
        viewModelScope.launch {
            _displayFridgeState.value = FridgeDetailUiState.Loading
            try {
                val fridge = fridgeRepository.getFridgeById(fridgeId)
                if (fridge != null) {
                    _displayFridgeState.value = FridgeDetailUiState.Success(fridge)
                } else {
                    _displayFridgeState.value = FridgeDetailUiState.Error("Fridge not found.")
                }
            } catch (e: Exception) {
                _displayFridgeState.value = FridgeDetailUiState.Error(e.message ?: "Failed to load fridge details.")
            }
        }
    }

    private fun listenToInventoryUpdates() {
        viewModelScope.launch {
            _itemsUiState.value = ItemsUiState.Loading
            try {
                fridgeRepository.getItemsForFridge(fridgeId).collectLatest { items ->
                    _itemsUiState.value = ItemsUiState.Success(items)
                }
            } catch (e: Exception) {
                _itemsUiState.value = ItemsUiState.Error(e.message ?: "Failed to load items.")
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
                    addItemToFridge(upc, product.name, product.imageUrl)
                } else {
                    _pendingScannedUpc.value = upc
                }
            } catch (e: Exception) {
                Log.e("FridgeInventoryVM", "Error handling scan: ${e.message}")
            }
        }
    }

    /**
     * Saves a brand new product to the global database AND adds it to the current fridge.
     */
    fun createAndAddProduct(upc: String, name: String, brand: String, category: String, imageUri: Uri?) {
        _pendingScannedUpc.value = null
        _isAddingItem.value = true
        
        viewModelScope.launch {
            try {
                val initialProduct = Product(
                    upc = upc,
                    name = name,
                    brand = brand,
                    category = category,
                    lastUpdated = System.currentTimeMillis()
                )
                
                val savedProduct = productRepository.saveProductWithImage(initialProduct, imageUri)
                addItemToFridge(upc, savedProduct.name, savedProduct.imageUrl)
                
                Log.d("FridgeInventoryVM", "Successfully created and added product: $name")
            } catch (e: Exception) {
                Log.e("FridgeInventoryVM", "Failed to create product: ${e.message}")
                _addItemError.value = "Failed to create product: ${e.message}"
            } finally {
                _isAddingItem.value = false
            }
        }
    }

    private suspend fun addItemToFridge(upc: String, name: String, imageUrl: String?) {
        try {
            val newItem = Item(
                upc = upc,
                name = name,
                imageUrl = imageUrl,
                quantity = 1
            )
            fridgeRepository.addItemToFridge(fridgeId, newItem)
        } catch (e: Exception) {
            _addItemError.value = "Failed to add item to fridge: ${e.message}"
        }
    }

    fun cancelPendingProduct() {
        _pendingScannedUpc.value = null
    }

    sealed interface FridgeDetailUiState {
        data object Loading : FridgeDetailUiState
        data class Success(val fridge: DisplayFridge) : FridgeDetailUiState
        data class Error(val message: String) : FridgeDetailUiState
    }

    sealed interface ItemsUiState {
        data object Loading : ItemsUiState
        data class Success(val items: List<Item>) : ItemsUiState
        data class Error(val message: String) : ItemsUiState
    }

    companion object {
        fun provideFactory(
            fridgeId: String, 
            fridgeRepository: FridgeRepository = FridgeRepository(),
            productRepository: ProductRepository = ProductRepository()
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    FridgeInventoryViewModel(fridgeRepository, productRepository, fridgeId)
                }
            }
        }
    }
}
