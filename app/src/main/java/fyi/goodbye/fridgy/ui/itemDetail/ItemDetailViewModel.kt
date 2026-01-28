package fyi.goodbye.fridgy.ui.itemDetail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.repositories.ItemRepository
import fyi.goodbye.fridgy.repositories.ProductRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the item detail screen displaying all instances of a product in a fridge.
 *
 * This ViewModel manages:
 * - Loading and displaying all instances of a product (grouped by UPC)
 * - Adding new instances with expiration dates
 * - Deleting individual item instances
 * - Resolving usernames for "added by" and "updated by" fields
 *
 * ## Instance-Based Model
 * Items are stored as individual instances rather than aggregated quantities.
 * Each instance has its own expiration date and tracking metadata.
 * The detail screen shows all instances of the same product (same UPC).
 *
 * ## State Management
 * Uses [ItemDetailUiState] sealed interface with Loading, Success, and Error states.
 * Success state includes the list of item instances and product information.
 *
 * @param application Application context for string resources.
 * @param fridgeRepository Repository for fridge information.
 * @param itemRepository Repository for item operations.
 * @param productRepository Repository for product information.
 * @param fridgeId The ID of the fridge containing the items.
 * @param itemId The ID of the initially selected item (used to determine UPC).
 *
 * @see Item For item instance data model
 * @see Product For product information model
 */
@HiltViewModel
class ItemDetailViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        savedStateHandle: SavedStateHandle,
        private val fridgeRepository: FridgeRepository,
        private val itemRepository: ItemRepository,
        private val productRepository: ProductRepository
    ) : ViewModel() {
        private val fridgeId: String = savedStateHandle.get<String>("fridgeId") ?: ""
        private val itemId: String = savedStateHandle.get<String>("itemId") ?: ""
        private val _uiState = MutableStateFlow<ItemDetailUiState>(ItemDetailUiState.Loading)
        val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

        private val _userNames = MutableStateFlow<Map<String, String>>(emptyMap())
        val userNames: StateFlow<Map<String, String>> = _userNames.asStateFlow()

        private val _pendingItemForDate = MutableStateFlow<String?>(null)
        val pendingItemForDate: StateFlow<String?> = _pendingItemForDate.asStateFlow()

        private val _pendingItemForEdit = MutableStateFlow<Item?>(null)
        val pendingItemForEdit: StateFlow<Item?> = _pendingItemForEdit.asStateFlow()

        private val _pendingItemForMove = MutableStateFlow<Item?>(null)
        val pendingItemForMove: StateFlow<Item?> = _pendingItemForMove.asStateFlow()

        private val _availableFridges = MutableStateFlow<List<fyi.goodbye.fridgy.models.Fridge>>(emptyList())
        val availableFridges: StateFlow<List<fyi.goodbye.fridgy.models.Fridge>> = _availableFridges.asStateFlow()

        // Store the UPC from the first load to track items after deletion
        private var trackedUpc: String? = null

        // Job reference to cancel previous collector if loadDetails() is called again
        private var itemsJob: Job? = null

        init {
            loadDetails()
            loadAvailableFridges()
        }

        private fun loadAvailableFridges() {
            viewModelScope.launch {
                try {
                    // Get the current fridge to find its household
                    val currentFridge = fridgeRepository.getFridgeById(fridgeId)
                    if (currentFridge != null) {
                        // Get all fridges in the same household (just the initial value)
                        fridgeRepository.getFridgesForHousehold(currentFridge.householdId)
                            .take(1) // Take only the first emission to avoid keeping the coroutine alive
                            .collect { fridges ->
                                // Filter out the current fridge
                                _availableFridges.value = fridges.filter { it.id != fridgeId }
                            }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load available fridges: ${e.message}")
                }
            }
        }

        private fun loadDetails() {
            // Cancel any existing collection to prevent multiple collectors
            itemsJob?.cancel()
            itemsJob =
                viewModelScope.launch {
                    _uiState.value = ItemDetailUiState.Loading
                    try {
                        itemRepository.getItemsForFridge(fridgeId).collect { displayItems ->
                            // On first load, find the clicked item to get its UPC
                            if (trackedUpc == null) {
                                val clickedItem = displayItems.find { it.item.id == itemId }
                                if (clickedItem != null) {
                                    trackedUpc = clickedItem.item.upc
                                } else {
                                    _uiState.value = ItemDetailUiState.Error(context.getString(R.string.error_item_not_found))
                                    return@collect
                                }
                            }

                            // Use stored UPC to find all instances (works even after deletions)
                            val upc = trackedUpc ?: return@collect
                            val allInstances =
                                displayItems
                                    .filter { it.item.upc == upc }
                                    .map { it.item }
                                    .sortedWith(
                                        compareBy<Item> { it.expirationDate == null }
                                            .thenBy { it.expirationDate ?: Long.MAX_VALUE }
                                    )

                            if (allInstances.isNotEmpty()) {
                                val product =
                                    displayItems.find { it.item.upc == upc }?.product
                                        ?: productRepository.getProductInfo(upc)
                                if (product != null) {
                                    // Load usernames BEFORE setting Success state to avoid "Loading..." flash
                                    loadUserNames(allInstances)
                                    _uiState.value = ItemDetailUiState.Success(allInstances, product)
                                } else {
                                    _uiState.value = ItemDetailUiState.Error(context.getString(R.string.error_product_not_found))
                                }
                            } else {
                                // All instances deleted - keep current success state, let UI handle navigation
                                val currentState = _uiState.value
                                if (currentState is ItemDetailUiState.Success) {
                                    _uiState.value = ItemDetailUiState.Success(emptyList(), currentState.product)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        _uiState.value = ItemDetailUiState.Error(e.message ?: context.getString(R.string.error_failed_to_load_details))
                    }
                }
        }

        private suspend fun loadUserNames(items: List<Item>) {
            val uids = items.flatMap { listOf(it.addedBy, it.lastUpdatedBy) }.filter { it.isNotEmpty() }.distinct()
            if (uids.isEmpty()) return

            // Batch fetch all user profiles at once (uses UserRepository's LRU cache)
            val userProfiles = fridgeRepository.getUsersByIds(uids)
            val names = _userNames.value.toMutableMap()

            userProfiles.forEach { (uid, profile) ->
                names[uid] = profile.username
            }

            // Fill in any missing users with "Unknown User"
            uids.forEach { uid ->
                if (!names.containsKey(uid)) {
                    names[uid] = context.getString(R.string.unknown_user)
                }
            }

            _userNames.value = names
        }

        /**
         * Deletes a specific item instance by its ID.
         * Since items are now individual instances, we delete this specific item.
         */
        fun deleteItem(itemIdToDelete: String) {
            viewModelScope.launch {
                try {
                    itemRepository.deleteItem(fridgeId, itemIdToDelete)
                } catch (e: Exception) {
                    Timber.e("Failed to delete item: ${e.message}")
                }
            }
        }

        /**
         * Adds a new instance of the current product with expiration date
         */
        fun addNewInstanceWithDate(expirationDate: Long?) {
            _pendingItemForDate.value = null
            val currentState = _uiState.value
            if (currentState is ItemDetailUiState.Success) {
                val upc = currentState.items.first().upc
                // Directly add the new instance with expiration date
                addNewInstance(upc, expirationDate)
            }
        }

        /**
         * Adds a new instance of the current product
         */
        private fun addNewInstance(
            upc: String,
            expirationDate: Long?
        ) {
            viewModelScope.launch {
                try {
                    itemRepository.addItemToFridge(fridgeId, upc, expirationDate)
                } catch (e: Exception) {
                    Timber.e("Failed to add new instance: ${e.message}")
                }
            }
        }

        /**
         * Shows the date picker dialog for adding a new instance
         */
        fun showAddInstanceDialog() {
            val currentState = _uiState.value
            if (currentState is ItemDetailUiState.Success) {
                _pendingItemForDate.value = currentState.items.first().upc
            }
        }

        /**
         * Cancels the date picker dialog
         */
        fun cancelDatePicker() {
            _pendingItemForDate.value = null
            _pendingItemForEdit.value = null
        }

        /**
         * Gets the product for displaying in the date picker dialog
         */
        suspend fun getProductForDisplay(upc: String): Product? {
            return productRepository.getProductInfo(upc)
        }

        /**
         * Shows the date picker dialog for editing an existing item's expiration
         */
        fun showEditExpirationDialog(item: Item) {
            _pendingItemForEdit.value = item
        }

        /**
         * Updates the expiration date of an existing item instance
         */
        fun updateItemExpiration(expirationDate: Long?) {
            val itemToUpdate = _pendingItemForEdit.value
            _pendingItemForEdit.value = null

            if (itemToUpdate != null) {
                viewModelScope.launch {
                    try {
                        itemRepository.updateItemExpirationDate(
                            fridgeId = fridgeId,
                            itemId = itemToUpdate.id,
                            expirationDate = expirationDate
                        )
                    } catch (e: Exception) {
                        Timber.e("Failed to update expiration: ${e.message}")
                    }
                }
            }
        }

        /**
         * Shows the move item dialog for selecting target fridge
         */
        fun showMoveItemDialog(item: Item) {
            _pendingItemForMove.value = item
        }

        /**
         * Cancels the move item dialog
         */
        fun cancelMoveItem() {
            _pendingItemForMove.value = null
        }

        /**
         * Moves an item instance to another fridge
         * Uses GlobalScope to ensure operation completes even if user navigates away
         */
        fun moveItemToFridge(targetFridgeId: String) {
            val itemToMove = _pendingItemForMove.value
            _pendingItemForMove.value = null

            if (itemToMove != null) {
                // Use GlobalScope to ensure move completes even if user navigates away
                GlobalScope.launch {
                    try {
                        itemRepository.moveItem(
                            sourceFridgeId = fridgeId,
                            targetFridgeId = targetFridgeId,
                            itemId = itemToMove.id
                        )
                        Timber.d("Successfully moved item ${itemToMove.id} to fridge $targetFridgeId")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to move item: ${e.message}")
                    }
                }
            }
        }

        sealed interface ItemDetailUiState {
            data object Loading : ItemDetailUiState

            data class Success(val items: List<Item>, val product: Product) : ItemDetailUiState

            data class Error(val message: String) : ItemDetailUiState
        }
    }
