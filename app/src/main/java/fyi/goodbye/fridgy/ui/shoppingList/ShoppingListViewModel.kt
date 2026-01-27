package fyi.goodbye.fridgy.ui.shoppingList

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.models.ShoppingListItem
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import fyi.goodbye.fridgy.repositories.ProductRepository
import fyi.goodbye.fridgy.repositories.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing the household-level shopping list UI and operations.
 *
 * The shopping list is shared across all fridges in a household, enabling
 * collaborative shopping where multiple users can:
 * - Add items to buy (by UPC or manual entry)
 * - Mark items as obtained with quantities
 * - Select target fridges for each obtained item
 * - See who else is currently shopping (presence indicators)
 * - Complete shopping sessions to move items to fridges
 *
 * ## Real-Time Collaboration
 * - Uses Firestore snapshot listeners for live updates
 * - Presence system shows active viewers with 15-second heartbeat
 * - Transactional updates prevent race conditions when multiple users shop
 *
 * ## State Management
 * Uses [UiState] sealed interface with Loading, Success, and Error states.
 * Success state contains [ShoppingListItemWithProduct] which pairs items with product info.
 *
 * @param householdId The ID of the household whose shopping list to manage.
 * @param productRepository Repository for fetching product information.
 *
 * @see HouseholdRepository For underlying shopping list operations
 * @see ShoppingListItem For shopping list item data model
 */
@HiltViewModel
class ShoppingListViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        savedStateHandle: SavedStateHandle,
        private val productRepository: ProductRepository,
        private val repository: HouseholdRepository,
        private val fridgeRepository: FridgeRepository,
        private val firebaseAuth: FirebaseAuth,
        private val userRepository: UserRepository
    ) : ViewModel() {
        private val householdId: String = savedStateHandle.get<String>("householdId") ?: ""
        private var presenceJob: Job? = null

        // Cache usernames to avoid repeated fetches
        private val usernameCache = mutableMapOf<String, String>()

        /**
         * Data class combining a shopping list item with its resolved product information.
         *
         * @property item The shopping list item data from Firestore.
         * @property productName Display name (from product DB or custom entry).
         * @property productBrand Product brand (empty for custom entries).
         * @property addedByUsername Username of the person who added the item.
         */
        data class ShoppingListItemWithProduct(
            val item: ShoppingListItem,
            val productName: String,
            val productBrand: String,
            val addedByUsername: String
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

        /**
         * The current authenticated user's ID, or empty string if not logged in.
         * Exposed for UI to determine user-specific data like target fridges.
         */
        val currentUserId: String
            get() = firebaseAuth.currentUser?.uid ?: ""

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
            presenceJob =
                viewModelScope.launch {
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
         * Does NOT remove presence document - leaves lastSeen timestamp for notifications.
         */
        fun stopPresence() {
            presenceJob?.cancel()
            // Don't call removeShoppingListPresence() - we want to keep the lastSeen timestamp
            // This allows other users to see who was recently shopping (for notifications)
        }

        private fun observeActiveViewers() {
            viewModelScope.launch {
                repository.getShoppingListPresence(householdId).collect { viewers ->
                    val userId = firebaseAuth.currentUser?.uid
                    // Filter out current user from the list
                    _activeViewers.value = viewers.filter { it.userId != userId }
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
                    // Fetch product names and usernames for each item
                    val itemsWithProducts =
                        items.map { item ->
                            // Fetch username (with caching)
                            val username =
                                if (usernameCache.containsKey(item.addedBy)) {
                                    usernameCache[item.addedBy]!!
                                } else {
                                    val profile = userRepository.getUserProfile(item.addedBy)
                                    val fetchedUsername = profile?.username ?: "Unknown User"
                                    usernameCache[item.addedBy] = fetchedUsername
                                    fetchedUsername
                                }

                            // Use custom name if available (manual entry), otherwise fetch from DB
                            if (item.customName.isNotEmpty()) {
                                ShoppingListItemWithProduct(
                                    item = item,
                                    productName = item.customName,
                                    productBrand = "",
                                    addedByUsername = username
                                )
                            } else {
                                val product = productRepository.getProductInfo(item.upc)
                                ShoppingListItemWithProduct(
                                    item = item,
                                    productName = product?.name ?: "Unknown Product",
                                    productBrand = product?.brand ?: "",
                                    addedByUsername = username
                                )
                            }
                        }
                    _uiState.value = UiState.Success(itemsWithProducts)
                }
            }
        }

        /**
         * Updates the current user's obtained quantity and target fridge for an item.
         *
         * Uses Firestore transactions to prevent race conditions when multiple
         * users update the same item simultaneously.
         *
         * @param upc The UPC of the item to update.
         * @param obtainedQuantity How many the current user obtained.
         * @param totalQuantity Total quantity needed.
         * @param targetFridgeId Which fridge to add items to on completion.
         */
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

        /**
         * Removes an item from the shopping list.
         *
         * @param upc The UPC of the item to remove.
         */
        fun removeItem(upc: String) {
            viewModelScope.launch {
                try {
                    repository.removeShoppingListItem(householdId, upc)
                } catch (e: Exception) {
                    _uiState.value = UiState.Error("Failed to remove item: ${e.message}")
                }
            }
        }

        /**
         * Adds an item to the shopping list by UPC.
         *
         * @param upc The Universal Product Code.
         * @param quantity How many to buy.
         * @param store Optional store location hint.
         * @param customName Optional custom name (for manual entries).
         */
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

        /**
         * Adds a manual (non-UPC) item to the shopping list.
         *
         * Generates a unique ID prefixed with "manual_" for manual entries.
         * These can later be linked to real products via [linkManualItemToProduct].
         *
         * @param name Display name for the item.
         * @param quantity How many to buy.
         * @param store Optional store location hint.
         */
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

        init {
            // OPTIMIZATION: Debounce search query to reduce Firebase queries
            viewModelScope.launch {
                _searchQuery
                    .debounce(300) // Wait 300ms after user stops typing
                    .collect { query ->
                        if (query.isBlank()) {
                            _searchResults.value = emptyList()
                        } else {
                            searchProducts(query)
                        }
                    }
            }
        }

        /**
         * Updates the product search query and triggers debounced search.
         *
         * @param query The search query string. Empty clears results.
         */
        fun updateSearchQuery(query: String) {
            _searchQuery.value = query
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

        /**
         * Completes the current user's shopping session.
         *
         * Moves obtained items to their designated fridges and removes
         * fulfilled items from the shopping list.
         *
         * @param onSuccess Callback invoked after successful completion.
         */
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
        fun linkManualItemToProduct(
            oldManualUpc: String,
            newUpc: String,
            quantity: Int,
            store: String,
            customName: String
        ) {
            viewModelScope.launch {
                try {
                    // Remove old manual item
                    repository.removeShoppingListItem(householdId, oldManualUpc)

                    // Add new item with real UPC
                    // Clear custom name since we now have real product
                    repository.addShoppingListItem(
                        householdId = householdId,
                        upc = newUpc,
                        quantity = quantity,
                        store = store,
                        customName = ""
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
                    val product =
                        Product(
                            upc = newUpc,
                            name = name,
                            brand = brand,
                            category = category,
                            imageUrl = "",
                            lastUpdated = null
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
    }
