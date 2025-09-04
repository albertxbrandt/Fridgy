package fyi.goodbye.fridgy.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.repositories.FridgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FridgeInventoryViewModel(
    private val fridgeRepository: FridgeRepository = FridgeRepository(),
    private val fridgeId: String
) : ViewModel() {

    // UI state for the specific fridge being displayed
    private val _displayFridgeState = MutableStateFlow<FridgeDetailUiState>(FridgeDetailUiState.Loading)
    val displayFridgeState: StateFlow<FridgeDetailUiState> = _displayFridgeState.asStateFlow()

    // UI state for the list of items in this fridge
    private val _itemsUiState = MutableStateFlow<ItemsUiState>(ItemsUiState.Loading)
    val itemsUiState: StateFlow<ItemsUiState> = _itemsUiState.asStateFlow()

    // State for adding item operation
    private val _isAddingItem = MutableStateFlow(false)
    val isAddingItem: StateFlow<Boolean> = _isAddingItem.asStateFlow()

    // State for adding item error
    private val _addItemError = MutableStateFlow<String?>(null)
    val addItemError: StateFlow<String?> = _addItemError.asStateFlow()

    init {
        // Fetch the specific fridge details (e.g., name, owner)
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
                Log.e("FridgeInventoryVM", "Error fetching fridge details for $fridgeId: ${e.message}", e)
            }
        }

        // Listen for real-time updates to items in this fridge
        viewModelScope.launch {
            _itemsUiState.value = ItemsUiState.Loading
            try {
                fridgeRepository.getItemsForFridge(fridgeId).collectLatest { items ->
                    _itemsUiState.value = ItemsUiState.Success(items)
                }
            } catch (e: Exception) {
                _itemsUiState.value = ItemsUiState.Error(e.message ?: "Failed to load items.")
                Log.e("FridgeInventoryVM", "Error collecting items for fridge $fridgeId: ${e.message}", e)
            }
        }
    }

    fun addItem(upc: String, quantity: Int) {
        _addItemError.value = null // Clear previous errors
        _isAddingItem.value = true // Set loading state

        viewModelScope.launch {
            try {
                val newItem = Item(upc = upc, quantity = quantity) // Create a basic item
                fridgeRepository.addItemToFridge(fridgeId, newItem)
                Log.d("FridgeInventoryVM", "Successfully added item $upc to fridge $fridgeId")
                // The itemsUiState flow will automatically update due to the real-time listener in the repository
            } catch (e: Exception) {
                Log.e("FridgeInventoryVM", "Failed to add item: ${e.message}", e)
                _addItemError.value = e.message ?: "Failed to add item."
            } finally {
                _isAddingItem.value = false // Clear loading state
            }
        }
    }

    // Sealed interface for fridge details UI state
    sealed interface FridgeDetailUiState {
        data object Loading : FridgeDetailUiState
        data class Success(val fridge: DisplayFridge) : FridgeDetailUiState
        data class Error(val message: String) : FridgeDetailUiState
    }

    // Sealed interface for items UI state
    sealed interface ItemsUiState {
        data object Loading : ItemsUiState
        data class Success(val items: List<Item>) : ItemsUiState
        data class Error(val message: String) : ItemsUiState
    }

    // Companion object to provide the factory for creating the ViewModel with arguments
    companion object {
        // Factory method to create FridgeInventoryViewModel with arguments
        fun provideFactory(fridgeId: String, fridgeRepository: FridgeRepository = FridgeRepository()): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    // Create an instance of FridgeInventoryViewModel, injecting the dependencies
                    // No SavedStateHandle needed here if fridgeId is passed directly
                    FridgeInventoryViewModel(fridgeRepository, fridgeId)
                }
            }
        }
    }
}