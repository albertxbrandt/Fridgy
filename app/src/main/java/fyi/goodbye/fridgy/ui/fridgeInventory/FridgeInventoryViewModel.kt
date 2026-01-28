package fyi.goodbye.fridgy.ui.fridgeInventory

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.HouseholdRole
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import fyi.goodbye.fridgy.repositories.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Combined model for displaying an item with its global product details.
 * @property localImageUri Optional local URI for images that haven't been uploaded to Storage yet.
 * @deprecated Use DisplayItem instead
 */
@Deprecated("Use DisplayItem from models package")
data class InventoryItem(
    val item: Item,
    val product: Product,
    val localImageUri: Uri? = null
)

/**
 * ViewModel responsible for managing the inventory and basic details of a specific fridge.
 */
@HiltViewModel
class FridgeInventoryViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        savedStateHandle: SavedStateHandle,
        private val fridgeRepository: FridgeRepository,
        private val householdRepository: HouseholdRepository,
        private val productRepository: ProductRepository,
        private val firebaseAuth: FirebaseAuth
    ) : ViewModel() {
        private val fridgeId: String = savedStateHandle.get<String>("fridgeId") ?: ""
        private val initialFridgeName: String = savedStateHandle.get<String>("fridgeName") ?: ""

        private val currentUserId: String? = firebaseAuth.currentUser?.uid
        private val _displayFridgeState =
            MutableStateFlow<FridgeDetailUiState>(
                if (initialFridgeName.isNotBlank()) {
                    FridgeDetailUiState.Success(
                        DisplayFridge(
                            id = fridgeId,
                            name = initialFridgeName,
                            householdId = "",
                            createdAt = null
                        )
                    )
                } else {
                    FridgeDetailUiState.Loading
                }
            )
        val displayFridgeState: StateFlow<FridgeDetailUiState> = _displayFridgeState.asStateFlow()

        /** The householdId of the current fridge, or null if not loaded yet. */
        val householdId: String?
            get() = (_displayFridgeState.value as? FridgeDetailUiState.Success)?.fridge?.householdId

        /**
         * Indicates whether the current user is the owner of the household this fridge belongs to.
         * Household owners have permission to edit and delete fridges.
         * Derived from both fridge and household state to update automatically.
         */
        val isCurrentUserOwner: StateFlow<Boolean> =
            _displayFridgeState
                .map { state ->
                    when (state) {
                        is FridgeDetailUiState.Success -> {
                            val householdId = state.fridge.householdId
                            if (householdId.isEmpty() || currentUserId == null) {
                                false
                            } else {
                                try {
                                    val household = householdRepository.getHouseholdById(householdId)
                                    household?.createdBy == currentUserId
                                } catch (e: Exception) {
                                    Log.e("FridgeInventoryVM", "Error checking household ownership: ${e.message}")
                                    false
                                }
                            }
                        }
                        else -> false
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = false
                )

        /**
         * Indicates whether the current user can manage this fridge (OWNER or MANAGER).
         * This controls access to fridge settings and administrative functions.
         * Derived from both fridge and household state to update automatically.
         */
        val canManageFridge: StateFlow<Boolean> =
            _displayFridgeState
                .map { state ->
                    when (state) {
                        is FridgeDetailUiState.Success -> {
                            val householdId = state.fridge.householdId
                            if (householdId.isEmpty() || currentUserId == null) {
                                false
                            } else {
                                try {
                                    val household = fridgeRepository.householdRepository.getHouseholdById(householdId)
                                    val userRole = household?.getRoleForUser(currentUserId)
                                    userRole == HouseholdRole.OWNER || userRole == HouseholdRole.MANAGER
                                } catch (e: Exception) {
                                    Log.e(
                                        "FridgeInventoryVM",
                                        "Error checking fridge management permission: ${e.message}"
                                    )
                                    false
                                }
                            }
                        }
                        else -> false
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = false
                )

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

        // State for showing expiration date picker after scanning
        private val _pendingItemForDate = MutableStateFlow<String?>(null)
        val pendingItemForDate: StateFlow<String?> = _pendingItemForDate.asStateFlow()

        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        // OPTIMIZATION: Filtered items state - combines items with debounced search query
        val filteredItemsUiState: StateFlow<ItemsUiState> =
            combine(
                _itemsUiState,
                // Debounce search input to reduce recomputations
                _searchQuery.debounce(150)
            ) { itemsState, query ->
                when (itemsState) {
                    is ItemsUiState.Success -> {
                        if (query.isNotBlank()) {
                            val filtered =
                                itemsState.items.filter { inventoryItem ->
                                    inventoryItem.product.name.contains(query, ignoreCase = true) ||
                                        inventoryItem.product.brand.contains(query, ignoreCase = true) ||
                                        inventoryItem.item.upc.contains(query, ignoreCase = true)
                                }
                            ItemsUiState.Success(filtered)
                        } else {
                            itemsState
                        }
                    }
                    else -> itemsState
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ItemsUiState.Loading
            )

        // OPTIMIZATION: Group items by UPC in ViewModel instead of Composable
        // Prevents regrouping on every recomposition
        data class GroupedItem(
            val upc: String,
            val items: List<InventoryItem>
        )

        val groupedItemsState: StateFlow<List<GroupedItem>> =
            filteredItemsUiState
                .map { state ->
                    when (state) {
                        is ItemsUiState.Success -> {
                            state.items
                                .groupBy { it.product.upc }
                                .map { (upc, items) -> GroupedItem(upc, items) }
                        }
                        else -> emptyList()
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList()
                )

        init {
            // Preload items from cache for instant display
            viewModelScope.launch {
                val cachedItems = fridgeRepository.preloadItemsFromCache(fridgeId)
                if (cachedItems.isNotEmpty()) {
                    // PERFORMANCE FIX: Batch fetch products instead of N individual queries
                    val upcs = cachedItems.map { it.upc }
                    val productsMap = productRepository.getProductsByUpcs(upcs)

                    // Map cached items to products and show immediately
                    val inventoryItems =
                        cachedItems.map { item ->
                            val product =
                                productsMap[item.upc]
                                    ?: Product(upc = item.upc, name = context.getString(R.string.unknown_product))
                            InventoryItem(item, product)
                        }
                    _itemsUiState.value = ItemsUiState.Success(inventoryItems)
                }
            }

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
                        _displayFridgeState.value = FridgeDetailUiState.Error(context.getString(R.string.error_fridge_not_found))
                    }
                } catch (e: Exception) {
                    _displayFridgeState.value = FridgeDetailUiState.Error(e.message ?: context.getString(R.string.error_failed_to_load_fridge))
                }
            }
        }

        private fun listenToInventoryUpdates() {
            viewModelScope.launch {
                // Don't reset to Loading - we might already have cached data showing

                try {
                    // Repository now returns DisplayItem (without product info loaded yet)
                    fridgeRepository.getItemsForFridge(fridgeId)
                        .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions
                        .collectLatest { displayItems ->
                            // PERFORMANCE FIX: Batch fetch all products instead of N individual queries
                            val upcs = displayItems.map { it.item.upc }.distinct()
                            val productsMap = productRepository.getProductsByUpcs(upcs)

                            // Map display items to inventory items with batch-fetched products
                            val inventoryItems =
                                displayItems.map { displayItem ->
                                    val product =
                                        productsMap[displayItem.item.upc]
                                            ?: Product(
                                                upc = displayItem.item.upc,
                                                name = context.getString(R.string.unknown_product)
                                            )

                                    @Suppress("DEPRECATION")
                                    InventoryItem(
                                        item = displayItem.item,
                                        product = product,
                                        localImageUri = null
                                    )
                                }
                            _itemsUiState.value = ItemsUiState.Success(inventoryItems)
                        }
                } catch (e: Exception) {
                    // Handle permission errors gracefully (e.g., when fridge is deleted/left)
                    if (e.message?.contains("PERMISSION_DENIED") == true) {
                        Log.w("FridgeInventoryVM", "Permission denied - fridge may have been deleted or left")
                        // Keep existing state, don't crash
                    } else {
                        _itemsUiState.value = ItemsUiState.Error(e.message ?: "Unknown error")
                    }
                }
            }
        }

        /**
         * Handles a scanned barcode UPC, checking if the product exists in the global
         * product database before adding it to the fridge.
         *
         * If the product exists, prompts for expiration date.
         * If the product doesn't exist, sets [pendingScannedUpc] to trigger the
         * new product dialog where the user can add product details.
         *
         * @param upc The scanned UPC/barcode string.
         */
        fun onBarcodeScanned(upc: String) {
            viewModelScope.launch {
                try {
                    val product = productRepository.getProductInfo(upc)
                    if (product != null) {
                        // Product exists, show date picker
                        _pendingItemForDate.value = upc
                    } else {
                        // Product doesn't exist, show create product dialog
                        _pendingScannedUpc.value = upc
                    }
                } catch (e: Exception) {
                    Log.e("FridgeInventoryVM", "Error handling scan: ${e.message}")
                }
            }
        }

        /**
         * Adds an item with optional expiration date.
         */
        fun addItemWithDate(
            upc: String,
            expirationDate: Long?
        ) {
            _pendingItemForDate.value = null
            viewModelScope.launch {
                addItemToFridge(upc, expirationDate)
            }
        }

        /**
         * Cancels the expiration date picker.
         */
        fun cancelDatePicker() {
            _pendingItemForDate.value = null
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
            imageUri: Uri?,
            size: Double?,
            unit: String?
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
                        imageUrl = "",
                        size = size,
                        unit = unit,
                        lastUpdated = null
                    )

            /*
            Note: Optimistic updates are disabled for instance-based items.
            We rely on Firestore snapshot listeners for real-time updates.
                    upc = upc,
                    addedAt = System.currentTimeMillis()
                )

            // Layer 1: Inject into ViewModel's local state immediately
            optimisticItems.value = optimisticItems.value + InventoryItem(optimisticItem, optimisticProduct, imageUri)
                 */

                try {
                    // Layer 2: Push to Repository Cache
                    productRepository.injectToCache(optimisticProduct)

                    // Save product to Firestore FIRST, then add item to fridge
                    // This ensures other devices see the product data when the item appears
                    productRepository.saveProductWithImage(optimisticProduct, imageUri)
                    addItemToFridge(upc)

                    Log.d("FridgeInventoryVM", "Product and item added successfully: $name")
                } catch (e: Exception) {
                    Log.e("FridgeInventoryVM", "Failed to create product: ${e.message}")
                    _addItemError.value = context.getString(R.string.error_failed_to_add_item, e.message ?: "")
                    // Rollback optimistic item on failure
                    optimisticItems.value = optimisticItems.value.filter { it.item.upc != upc }
                } finally {
                    _isAddingItem.value = false
                }
            }
        }

        /**
         * Adds an item to the fridge with an optional expiration date.
         *
         * @param upc The Universal Product Code of the item
         * @param expirationDate Optional expiration date in milliseconds since epoch
         */
        private suspend fun addItemToFridge(
            upc: String,
            expirationDate: Long? = null
        ) {
            try {
                fridgeRepository.addItemToFridge(fridgeId, upc, expirationDate)
            } catch (e: Exception) {
                _addItemError.value = context.getString(R.string.error_failed_to_add_item, e.message ?: "")
            }
        }

        fun cancelPendingProduct() {
            _pendingScannedUpc.value = null
        }

        /**
         * Gets product info for display purposes (used in dialogs).
         */
        suspend fun getProductForDisplay(upc: String) = productRepository.getProductInfo(upc)

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
    }
