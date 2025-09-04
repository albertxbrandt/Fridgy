package fyi.goodbye.fridgy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.Item
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FridgeViewModel : ViewModel() {
    private val repository = FridgeRepository()

    // Current state
    private val _fridges = MutableStateFlow<List<Fridge>>(emptyList())
    val fridges: StateFlow<List<Fridge>> = _fridges

    private val _currentFridgeItems = MutableStateFlow<List<Item>>(emptyList())
    val currentFridgeItems: StateFlow<List<Item>> = _currentFridgeItems

    private val _selectedFridge = MutableStateFlow<Fridge?>(null)
    val selectedFridge: StateFlow<Fridge?> = _selectedFridge

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadFridges()
    }

    fun loadFridges() {
        viewModelScope.launch {
            _isLoading.value = true
            _fridges.value = repository.getFridges()
            _isLoading.value = false
        }
    }

    fun selectFridge(fridge: Fridge?) {
        _selectedFridge.value = fridge
        if (fridge != null) { //added
            loadItemsForFridge(fridge.id)
        }
    }

    private fun loadItemsForFridge(fridgeId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _currentFridgeItems.value = repository.getItemsForFridge(fridgeId)
            _isLoading.value = false
        }
    }

    fun addItem(quantity: Int, upc: String = "") {
        val selectedFridge = _selectedFridge.value ?: return

        val newItem = Item(
            upc = upc,
            quantity = quantity,
            addedBy = "user@example.com", // We'll add proper user management later
            lastUpdatedBy = "user@example.com"
        )

        viewModelScope.launch {
            if (repository.addItem(newItem)) {
                loadItemsForFridge(selectedFridge.id) // Refresh the list
            }
        }
    }

    fun updateQuantity(itemId: String, newQuantity: Int) {
        viewModelScope.launch {
            if (repository.updateItemQuantity(itemId, newQuantity, "user@example.com")) {
                _selectedFridge.value?.let { loadItemsForFridge(it.id) }
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            if (repository.deleteItem(itemId)) {
                _selectedFridge.value?.let { loadItemsForFridge(it.id) }
            }
        }
    }
}