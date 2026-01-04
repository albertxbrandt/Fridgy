package fyi.goodbye.fridgy.ui.itemDetail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.repositories.ProductRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ItemDetailViewModel(
    application: Application,
    private val fridgeRepository: FridgeRepository,
    private val productRepository: ProductRepository,
    private val fridgeId: String,
    private val itemId: String
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<ItemDetailUiState>(ItemDetailUiState.Loading)
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    private val _userNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val userNames: StateFlow<Map<String, String>> = _userNames.asStateFlow()

    // Job reference to cancel previous collector if loadDetails() is called again
    private var itemsJob: Job? = null

    init {
        loadDetails()
    }

    private fun loadDetails() {
        // Cancel any existing collection to prevent multiple collectors
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            _uiState.value = ItemDetailUiState.Loading
            try {
                fridgeRepository.getItemsForFridge(fridgeId).collect { items ->
                    val item = items.find { it.upc == itemId }
                    if (item != null) {
                        val product = productRepository.getProductInfo(item.upc)
                        if (product != null) {
                            _uiState.value = ItemDetailUiState.Success(item, product)
                            loadUserNames(item)
                        } else {
                            _uiState.value = ItemDetailUiState.Error(getApplication<Application>().getString(R.string.error_product_not_found))
                        }
                    } else {
                        _uiState.value = ItemDetailUiState.Error(getApplication<Application>().getString(R.string.error_item_not_found))
                    }
                }
            } catch (e: Exception) {
                _uiState.value = ItemDetailUiState.Error(e.message ?: getApplication<Application>().getString(R.string.error_failed_to_load_details))
            }
        }
    }

    private fun loadUserNames(item: Item) {
        viewModelScope.launch {
            val uids = setOf(item.addedBy, item.lastUpdatedBy).filter { it.isNotEmpty() }
            val names = _userNames.value.toMutableMap()
            uids.forEach { uid ->
                if (!names.containsKey(uid)) {
                    val userProfile = fridgeRepository.getUserProfileById(uid)
                    names[uid] = userProfile?.username ?: getApplication<Application>().getString(R.string.unknown_user)
                }
            }
            _userNames.value = names
        }
    }

    fun updateQuantity(newQuantity: Int) {
        if (newQuantity < 0) return
        viewModelScope.launch {
            try {
                fridgeRepository.updateItemQuantity(fridgeId, itemId, newQuantity)
            } catch (e: Exception) {
                Log.e("ItemDetailVM", "Failed to update quantity: ${e.message}")
            }
        }
    }

    sealed interface ItemDetailUiState {
        data object Loading : ItemDetailUiState

        data class Success(val item: Item, val product: Product) : ItemDetailUiState

        data class Error(val message: String) : ItemDetailUiState
    }

    companion object {
        fun provideFactory(
            fridgeId: String,
            itemId: String,
            fridgeRepository: FridgeRepository = FridgeRepository(),
            productRepository: ProductRepository? = null
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                    val repo = productRepository ?: ProductRepository(app.applicationContext)
                    ItemDetailViewModel(app, fridgeRepository, repo, fridgeId, itemId)
                }
            }
    }
}
