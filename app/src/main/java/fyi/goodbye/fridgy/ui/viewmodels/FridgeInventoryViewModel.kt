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

/**
 * ViewModel responsible for managing the inventory and basic details of a specific fridge.
 * 
 * It observes real-time updates for items within the fridge and provides functionality
 * to add new items. It also fetches the high-level [DisplayFridge] details for the UI header.
 *
 * @property fridgeRepository The repository for database operations.
 * @property fridgeId The unique ID of the fridge whose inventory is being displayed.
 */
class FridgeInventoryViewModel(
    private val fridgeRepository: FridgeRepository = FridgeRepository(),
    private val fridgeId: String
) : ViewModel() {

    private val _displayFridgeState = MutableStateFlow<FridgeDetailUiState>(FridgeDetailUiState.Loading)
    /** The UI state for the specific fridge being displayed (Loading, Success, or Error). */
    val displayFridgeState: StateFlow<FridgeDetailUiState> = _displayFridgeState.asStateFlow()

    private val _itemsUiState = MutableStateFlow<ItemsUiState>(ItemsUiState.Loading)
    /** The real-time UI state for the list of items in this fridge. */
    val itemsUiState: StateFlow<ItemsUiState> = _itemsUiState.asStateFlow()

    private val _isAddingItem = MutableStateFlow(false)
    /** Indicates whether an item is currently being added to the fridge. */
    val isAddingItem: StateFlow<Boolean> = _isAddingItem.asStateFlow()

    private val _addItemError = MutableStateFlow<String?>(null)
    /** Any error message resulting from a failed add-item operation. */
    val addItemError: StateFlow<String?> = _addItemError.asStateFlow()

    init {
        // Fetch specific fridge details (e.g., name, owner) for the header
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

        // Listen for real-time updates to items in this fridge's inventory
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

    /**
     * Adds a new item to the fridge's inventory using its barcode (UPC).
     * 
     * @param upc The barcode of the item to add.
     * @param quantity The number of units to add.
     */
    fun addItem(upc: String, quantity: Int) {
        _addItemError.value = null
        _isAddingItem.value = true

        viewModelScope.launch {
            try {
                val newItem = Item(upc = upc, quantity = quantity)
                fridgeRepository.addItemToFridge(fridgeId, newItem)
                Log.d("FridgeInventoryVM", "Successfully added item $upc to fridge $fridgeId")
            } catch (e: Exception) {
                Log.e("FridgeInventoryVM", "Failed to add item: ${e.message}", e)
                _addItemError.value = e.message ?: "Failed to add item."
            } finally {
                _isAddingItem.value = false
            }
        }
    }

    /** Sealed interface representing the UI state for fridge header details. */
    sealed interface FridgeDetailUiState {
        data object Loading : FridgeDetailUiState
        data class Success(val fridge: DisplayFridge) : FridgeDetailUiState
        data class Error(val message: String) : FridgeDetailUiState
    }

    /** Sealed interface representing the UI state for the inventory item grid. */
    sealed interface ItemsUiState {
        data object Loading : ItemsUiState
        data class Success(val items: List<Item>) : ItemsUiState
        data class Error(val message: String) : ItemsUiState
    }

    companion object {
        /** Factory method to create [FridgeInventoryViewModel] with a required [fridgeId]. */
        fun provideFactory(fridgeId: String, fridgeRepository: FridgeRepository = FridgeRepository()): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    FridgeInventoryViewModel(fridgeRepository, fridgeId)
                }
            }
        }
    }
}
