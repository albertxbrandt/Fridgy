package fyi.goodbye.fridgy.repositories

import timber.log.Timber
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import fyi.goodbye.fridgy.constants.FirestoreCollections
import fyi.goodbye.fridgy.constants.FirestoreFields
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.DisplayItem
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.models.ShoppingListItem
import fyi.goodbye.fridgy.models.User
import fyi.goodbye.fridgy.models.UserProfile
import fyi.goodbye.fridgy.models.canManageFridges
import fyi.goodbye.fridgy.utils.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing fridges and their inventory items in Firestore.
 *
 * This repository handles all data operations related to:
 * - Fridge CRUD operations (create, read, update, delete)
 * - Item management within fridges (add, update, delete instances)
 * - Shopping list operations at the fridge level (legacy)
 * - User presence tracking for collaborative shopping
 * - User profile caching with LRU eviction
 *
 * ## Architecture Notes
 * - Items are stored as individual instances in a subcollection (`fridges/{fridgeId}/items`)
 * - Each item instance can have its own expiration date
 * - User profiles are cached using an LRU cache to minimize Firestore reads
 * - Real-time updates are provided via Kotlin Flows using Firestore snapshot listeners
 *
 * ## Thread Safety
 * - Uses coroutines for async operations
 * - Presence tracking uses batch coroutine fetching to avoid race conditions
 *
 * @param firestore The Firestore instance for database operations.
 * @param auth The Auth instance for user identification.
 * @param householdRepository The HouseholdRepository for permission checks.
 * @see HouseholdRepository For household-level shopping list operations (preferred)
 * @see ProductRepository For product information lookup
 */
class FridgeRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    val householdRepository: HouseholdRepository
) {

    companion object {
        private const val PRESENCE_TIMEOUT_MS = 30_000L
        private const val USER_PROFILE_CACHE_SIZE = 100

        /**
         * LRU cache for user profiles, shared across repository instances.
         * Evicts least recently used profiles when capacity is reached.
         */
        private val userProfileCache = LruCache<String, UserProfile>(USER_PROFILE_CACHE_SIZE)
    }

    private var fridgeCache: List<Fridge> = emptyList()

    // ========================================
    // USER PROFILE FETCHING
    // ========================================

    /**
     * Fetches a user by ID from the users collection.
     *
     * @param userId The user's Firebase UID.
     * @return User object, or null if not found.
     */
    suspend fun getUserById(userId: String): User? {
        return try {
            val doc = firestore.collection(FirestoreCollections.USERS).document(userId).get().await()
            doc.toObject(User::class.java)?.copy(uid = doc.id)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetches a user's public profile by ID.
     *
     * @param userId The user's Firebase UID.
     * @return UserProfile with public data (username), or null if not found.
     */
    suspend fun getUserProfileById(userId: String): UserProfile? {
        return try {
            val doc = firestore.collection(FirestoreCollections.USER_PROFILES).document(userId).get().await()
            doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetches multiple users' public profiles by their IDs.
     * Returns a map of userId to UserProfile for easy lookup.
     * This queries the userProfiles collection which contains only public data (username).
     *
     * Uses LRU cache to minimize network requests. Batches Firestore queries in chunks of 10
     * due to 'in' query limitations.
     *
     * @param userIds List of user Firebase UIDs to fetch.
     * @return Map of userId to UserProfile, empty map if fetch fails.
     */
    suspend fun getUsersByIds(userIds: List<String>): Map<String, UserProfile> {
        if (userIds.isEmpty()) return emptyMap()

        return try {
            // OPTIMIZATION: Check cache first, only fetch missing users
            val result = mutableMapOf<String, UserProfile>()
            val missingIds = mutableListOf<String>()

            userIds.forEach { userId ->
                val cached = userProfileCache[userId]
                if (cached != null) {
                    result[userId] = cached
                } else {
                    missingIds.add(userId)
                }
            }

            // Only fetch users not in cache
            if (missingIds.isNotEmpty()) {
                // Firestore 'in' queries are limited to 10 items, so batch if needed
                missingIds.chunked(10).forEach { chunk ->
                    val snapshot =
                        firestore.collection(FirestoreCollections.USER_PROFILES)
                            .whereIn(FieldPath.documentId(), chunk)
                            .get()
                            .await()

                    snapshot.documents.forEach { doc ->
                        doc.toObject(UserProfile::class.java)?.let { profile ->
                            val profileWithUid = profile.copy(uid = doc.id)
                            result[doc.id] = profileWithUid
                            // Cache for future use
                            userProfileCache[doc.id] = profileWithUid
                        }
                    }
                }
            }

            Timber.d(
                "Fetched ${result.size} user profiles (${result.size - missingIds.size} from cache, ${missingIds.size} from network)"
            )
            result
        } catch (e: Exception) {
            Timber.e(e, "Error fetching user profiles by IDs: ${e.message}")
            emptyMap()
        }
    }

    // ========================================
    // FRIDGE CRUD OPERATIONS
    // ========================================

    /**
     * Converts a Firestore document to a Fridge, handling both old and new formats.
     * This provides backward compatibility during data migration.
     * Note: createdBy field is ignored as fridges are now household-owned.
     */
    private fun DocumentSnapshot.toFridgeCompat(): Fridge? {
        return try {
            this.toObject(Fridge::class.java)?.copy(id = this.id)
        } catch (e: Exception) {
            Timber.e("Error parsing fridge document: ${e.message}")
            return null
        }
    }

    /**
     * Preloads user's fridges from Firestore cache to memory for instant cold-start access.
     * Should be called on app initialization to populate cache before UI loads.
     * This reads from Firestore's local persistence layer without network access.
     *
     * Uses current user's ID to filter fridges from Firestore cache.
     */
    suspend fun preloadFridgesFromCache() {
        try {
            val currentUserId = auth.currentUser?.uid ?: return
            val snapshot =
                firestore.collection(FirestoreCollections.FRIDGES)
                    .whereArrayContains(FirestoreFields.MEMBERS, currentUserId)
                    .get(Source.CACHE)
                    .await()

            fridgeCache = snapshot.documents.mapNotNull { it.toFridgeCompat() }
            Timber.d("Preloaded ${fridgeCache.size} fridges from cache")
        } catch (e: Exception) {
            Timber.w("Could not preload from cache (likely first run): ${e.message}")
        }
    }

    /**
     * Returns a Flow of all fridges belonging to a household.
     *
     * Emits updates in real-time when fridges are added, modified, or removed.
     * Handles permission errors gracefully by emitting empty list.
     *
     * @param householdId The ID of the household.
     * @return Flow emitting list of fridges, updates in real-time.
     */
    fun getFridgesForHousehold(householdId: String): Flow<List<Fridge>> =
        callbackFlow {
            val fridgesListenerRegistration =
                firestore.collection(FirestoreCollections.FRIDGES)
                    .whereEqualTo(FirestoreFields.HOUSEHOLD_ID, householdId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            // Check if this is a permission error
                            if (e.message?.contains("PERMISSION_DENIED") == true) {
                                Timber.w(
                                    "Permission denied for household $householdId - user likely removed. Clearing cache."
                                )
                                // Clear any cached fridges from this household
                                fridgeCache = fridgeCache.filter { it.householdId != householdId }
                            } else {
                                Timber.e(e, "Error listening to fridges for household: ${e.message}")
                            }
                            // Send empty list instead of closing with error to prevent app crash
                            trySend(emptyList()).isSuccess
                            return@addSnapshotListener
                        }
                        val fridgesList = snapshot?.documents?.mapNotNull { it.toFridgeCompat() } ?: emptyList()
                        fridgeCache = fridgesList
                        trySend(fridgesList).isSuccess
                    }
            awaitClose { fridgesListenerRegistration.remove() }
        }
            .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions

    /**
     * Fetches a raw Fridge object by ID without user profile resolution.
     *
     * @param fridgeId The ID of the fridge to fetch.
     * @return The Fridge object, or null if not found or on error.
     */
    suspend fun getRawFridgeById(fridgeId: String): Fridge? {
        return try {
            val doc = firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId).get().await()
            doc.toFridgeCompat()
        } catch (e: Exception) {
            Timber.e("Error fetching raw fridge: ${e.message}")
            null
        }
    }

    /**
     * Fetches a fridge by ID with optional user profile resolution for display.
     *
     * Uses multi-level caching: in-memory cache -> Firestore cache -> network.
     *
     * @param fridgeId The ID of the fridge to fetch.
     * @param fetchUserDetails Whether to fetch creator's username (default: true).
     * @return DisplayFridge with resolved user info, or null if not found.
     */
    suspend fun getFridgeById(
        fridgeId: String,
        fetchUserDetails: Boolean = true
    ): DisplayFridge? {
        val cachedFridge = fridgeCache.find { it.id == fridgeId }
        val fridge =
            if (cachedFridge != null) {
                cachedFridge
            } else {
                try {
                    // OPTIMIZATION: Try cache first for instant response
                    val cacheDoc =
                        try {
                            firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId).get(Source.CACHE).await()
                        } catch (e: Exception) {
                            null
                        }

                    if (cacheDoc?.exists() == true) {
                        cacheDoc.toFridgeCompat()
                    } else {
                        // Fallback to network if cache miss
                        val doc = firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId).get(Source.DEFAULT).await()
                        doc.toFridgeCompat()
                    }
                } catch (e: Exception) {
                    null
                }
            } ?: return null

        return DisplayFridge(
            id = fridge.id,
            name = fridge.name,
            type = fridge.type,
            householdId = fridge.householdId,
            createdAt = fridge.createdAt
        )
    }

    /**
     * Creates a new fridge within a household.
     *
     * @param fridgeName Display name for the fridge.
     * @param householdId The ID of the household this fridge belongs to.
     * @param fridgeType Type of storage ("fridge", "freezer", "pantry").
     * @param fridgeLocation Physical location description.
     * @return The newly created Fridge object with generated ID.
     * @throws IllegalStateException if user is not logged in.
     */
    suspend fun createFridge(
        fridgeName: String,
        householdId: String,
        fridgeType: String = "fridge",
        fridgeLocation: String = ""
    ): Fridge {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")

        val newFridgeDocRef = firestore.collection(FirestoreCollections.FRIDGES).document()
        val fridgeId = newFridgeDocRef.id

        val newFridge =
            Fridge(
                id = fridgeId,
                name = fridgeName,
                type = fridgeType,
                location = fridgeLocation,
                householdId = householdId
                // createdAt set via @ServerTimestamp
            )

        newFridgeDocRef.set(newFridge).await()
        return newFridge
    }

    /**
     * Deletes a fridge and all its items.
     *
     * Performs a batch delete of all items in the fridge's subcollection
     * followed by the fridge document itself.
     *
     * @param fridgeId The ID of the fridge to delete.
     * @throws IllegalStateException if user doesn't have permission.
     */
    suspend fun deleteFridge(fridgeId: String) {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")

        // Get fridge to check household
        val fridgeDoc = firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId).get().await()
        val fridge =
            fridgeDoc.toObject(Fridge::class.java)
                ?: throw IllegalStateException("Fridge not found")

        // Check permission
        val household =
            householdRepository.getHouseholdById(fridge.householdId)
                ?: throw IllegalStateException("Household not found")

        val userRole = household.getRoleForUser(currentUser.uid)
        if (!userRole.canManageFridges()) {
            throw IllegalStateException("You don't have permission to delete fridges")
        }

        val fridgeRef = firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId)

        Timber.d("Attempting to read items for fridge: $fridgeId")
        val items =
            try {
                fridgeRef.collection(FirestoreCollections.ITEMS).get().await()
            } catch (e: Exception) {
                Timber.e("Failed to read items subcollection: ${e.message}")
                throw e
            }

        Timber.d("Successfully read ${items.size()} items, creating batch delete")
        val batch = firestore.batch()
        items.documents.forEach { batch.delete(it.reference) }
        batch.delete(fridgeRef)

        Timber.d("Committing batch delete (${items.size()} items + 1 fridge)")
        batch.commit().await()
        Timber.d("Batch delete successful")
    }

    // ========================================
    // ITEM MANAGEMENT
    // ========================================

    /**
     * Preloads items for a specific fridge from Firestore cache to memory.
     * Should be called when opening a fridge inventory to enable instant display.
     * This reads from Firestore's local persistence layer without network access.
     *
     * @param fridgeId The ID of the fridge to preload items for.
     * @return List of preloaded items, or empty list if cache unavailable.
     */
    suspend fun preloadItemsFromCache(fridgeId: String): List<Item> {
        return try {
            val snapshot =
                firestore.collection(FirestoreCollections.FRIDGES)
                    .document(fridgeId)
                    .collection(FirestoreCollections.ITEMS)
                    .get(Source.CACHE)
                    .await()

            val items = snapshot.documents.mapNotNull { it.toObject(Item::class.java) }
            Timber.d("Preloaded ${items.size} items from cache for fridge $fridgeId")
            items
        } catch (e: Exception) {
            Timber.w("Could not preload items from cache: ${e.message}")
            emptyList()
        }
    }

    /**
     * Gets all items for a fridge with expiration status.
     *
     * Returns a flow that emits whenever items change. Product info is NOT included here -
     * the ViewModel layer should handle fetching product data using ProductRepository.
     *
     * @param fridgeId Target fridge ID
     * @return Flow of DisplayItem list (with null products), sorted by expiration date
     */
    fun getItemsForFridge(fridgeId: String): Flow<List<DisplayItem>> =
        callbackFlow {
            // Use SnapshotListener with metadata changes enabled for immediate updates
            val listener =
                firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId).collection(FirestoreCollections.ITEMS)
                    .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, e ->
                        if (e != null) {
                            Timber.e(e, "Error fetching items for fridge $fridgeId: ${e.message}")
                            // Send empty list instead of closing with error to prevent app crash
                            trySend(emptyList()).isSuccess
                            return@addSnapshotListener
                        }

                        val items =
                            snapshot?.documents?.mapNotNull {
                                it.toObject(Item::class.java)?.copy(id = it.id)
                            } ?: emptyList()

                        Timber.d("Items snapshot received for fridge $fridgeId: ${items.size} items")

                        // Create DisplayItems without product info (ViewModel will fetch products)
                        val displayItems =
                            items.map { item ->
                                DisplayItem.from(item, null)
                            }.sortedWith(
                                compareBy<DisplayItem> {
                                    // Sort expired first, then expiring soon, then by date
                                    when {
                                        it.isExpired -> 0
                                        it.isExpiringSoon -> 1
                                        it.item.expirationDate != null -> 2
                                        else -> 3
                                    }
                                }.thenBy { it.item.expirationDate ?: Long.MAX_VALUE }
                            )

                        trySend(displayItems).isSuccess
                    }
            awaitClose { listener.remove() }
        }
            .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions

    /**
     * Gets the count of items in a fridge.
     *
     * @param fridgeId Target fridge ID
     * @return The number of items in the fridge
     */
    suspend fun getItemCount(fridgeId: String): Int {
        return try {
            val snapshot =
                firestore.collection(FirestoreCollections.FRIDGES)
                    .document(fridgeId)
                    .collection(FirestoreCollections.ITEMS)
                    .get()
                    .await()
            snapshot.size()
        } catch (e: Exception) {
            Timber.e("Error getting item count for fridge $fridgeId: ${e.message}")
            0
        }
    }

    /**
     * Adds a new item instance to a fridge.
     *
     * Items are now stored as individual instances rather than aggregated quantities,
     * allowing each instance to have its own expiration date.
     *
     * @param fridgeId Target fridge ID
     * @param upc The Universal Product Code (barcode) of the item to add
     * @param expirationDate Optional expiration date in milliseconds since epoch
     * @return The created Item instance with its generated ID
     * @throws IllegalStateException if user is not logged in or fridge has no householdId
     * @throws Exception if Firestore operations fail
     */
    suspend fun addItemToFridge(
        fridgeId: String,
        upc: String,
        expirationDate: Long? = null
    ): Item {
        Timber.d("Adding item instance for UPC: $upc with expiration: $expirationDate")
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")

        val newItem =
            Item(
                upc = upc,
                expirationDate = expirationDate,
                addedBy = currentUser.uid
                // addedAt and lastUpdatedAt set via @ServerTimestamp
            )

        try {
            val docRef =
                firestore.collection(FirestoreCollections.FRIDGES)
                    .document(fridgeId)
                    .collection(FirestoreCollections.ITEMS)
                    .add(newItem)
                    .await()

            Timber.d("Added item instance: ${docRef.id} (UPC: $upc)")
            return newItem.copy(id = docRef.id)
        } catch (e: Exception) {
            Timber.e(e, "Error adding item to fridge")
            throw e
        }
    }

    /**
     * Updates the expiration date of a specific item instance.
     *
     * @param fridgeId Target fridge ID
     * @param itemId The unique item instance ID
     * @param expirationDate New expiration date in milliseconds (null to remove expiration)
     */
    suspend fun updateItemExpirationDate(
        fridgeId: String,
        itemId: String,
        expirationDate: Long?
    ) {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")

        try {
            firestore.collection(FirestoreCollections.FRIDGES)
                .document(fridgeId)
                .collection(FirestoreCollections.ITEMS)
                .document(itemId)
                .update(
                    mapOf(
                        FirestoreFields.EXPIRATION_DATE to expirationDate,
                        FirestoreFields.LAST_UPDATED_BY to currentUser.uid,
                        FirestoreFields.LAST_UPDATED_AT to FieldValue.serverTimestamp()
                    )
                )
                .await()

            Timber.d("Updated expiration date for item: $itemId")
        } catch (e: Exception) {
            Timber.e(e, "Error updating expiration date")
            throw e
        }
    }

    /**
     * Deletes a specific item instance from a fridge.
     *
     * @param fridgeId Target fridge ID
     * @param itemId The unique item instance ID to delete
     */
    suspend fun deleteItem(
        fridgeId: String,
        itemId: String
    ) {
        try {
            firestore.collection(FirestoreCollections.FRIDGES)
                .document(fridgeId)
                .collection(FirestoreCollections.ITEMS)
                .document(itemId)
                .delete()
                .await()

            Timber.d("Deleted item: $itemId")
        } catch (e: Exception) {
            Timber.e(e, "Error deleting item")
            throw e
        }
    }

    // ========================================
    // SHOPPING LIST OPERATIONS (FRIDGE-LEVEL)
    // ========================================

    /**
     * Returns a Flow of shopping list items for a fridge subcollection.
     *
     * @param fridgeId The ID of the fridge to get shopping list items from.
     * @return Flow emitting list of shopping list items, updates in real-time.
     */
    fun getShoppingListItems(fridgeId: String): Flow<List<ShoppingListItem>> =
        callbackFlow {
            val colRef = firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId).collection(FirestoreCollections.SHOPPING_LIST)
            val listener =
                colRef.addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Timber.e(e, "Error fetching shopping list items: ${e.message}")
                        // Send empty list instead of closing with error to prevent app crash
                        trySend(emptyList()).isSuccess
                        return@addSnapshotListener
                    }
                    val items =
                        snapshot?.documents?.mapNotNull {
                            it.toObject(ShoppingListItem::class.java)
                        } ?: emptyList()
                    trySend(items).isSuccess
                }
            awaitClose { listener.remove() }
        }
            .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions

    /**
     * Sets the current user as actively viewing the shopping list.
     * Uses Firestore serverTimestamp for automatic cleanup of stale presence.
     */
    suspend fun setShoppingListPresence(fridgeId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val presenceRef =
            firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId)
                .collection(FirestoreCollections.SHOPPING_LIST_PRESENCE).document(currentUserId)

        presenceRef.set(
            mapOf(
                FirestoreFields.USER_ID to currentUserId,
                FirestoreFields.LAST_SEEN to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /**
     * Removes the current user's presence from the shopping list.
     *
     * @param fridgeId The ID of the fridge to remove presence from.
     */
    suspend fun removeShoppingListPresence(fridgeId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId)
            .collection(FirestoreCollections.SHOPPING_LIST_PRESENCE).document(currentUserId)
            .delete().await()
    }

    /**
     * Represents an active viewer of the shopping list.
     *
     * @property userId The user's Firebase UID.
     * @property username The user's display name.
     * @property lastSeenTimestamp The last activity timestamp in milliseconds.
     */
    data class ActiveViewer(
        val userId: String,
        val username: String,
        val lastSeenTimestamp: Long
    )

    /**
     * Returns a Flow of detailed viewer information for users currently viewing the shopping list.
     * Filters out presence older than 30 seconds (stale sessions).
     *
     * Thread-safe implementation that batch fetches user profiles using coroutines
     * instead of async callbacks to avoid race conditions on shared mutable state.
     */
    fun getShoppingListPresence(fridgeId: String): Flow<List<ActiveViewer>> =
        callbackFlow {
            val presenceRef =
                firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId)
                    .collection(FirestoreCollections.SHOPPING_LIST_PRESENCE)

            val listener =
                presenceRef.addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Timber.e(e, "Error fetching shopping list presence: ${e.message}")
                        // Send empty list instead of closing with error to prevent app crash
                        trySend(emptyList()).isSuccess
                        return@addSnapshotListener
                    }

                    val currentTime = System.currentTimeMillis()

                    // Collect active user IDs and their timestamps first (no async here)
                    val activeUserData =
                        snapshot?.documents?.mapNotNull { doc ->
                            val lastSeen = doc.getTimestamp(FirestoreFields.LAST_SEEN)?.toDate()?.time ?: 0
                            val userId = doc.getString(FirestoreFields.USER_ID)
                            // Consider active if seen within last 30 seconds
                            if (userId != null && (currentTime - lastSeen) < PRESENCE_TIMEOUT_MS) {
                                userId to lastSeen
                            } else {
                                null
                            }
                        } ?: emptyList()

                    if (activeUserData.isEmpty()) {
                        trySend(emptyList()).isSuccess
                        return@addSnapshotListener
                    }

                    // Batch fetch all user profiles in a coroutine (thread-safe)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val userIds = activeUserData.map { it.first }
                            val profiles = getUsersByIds(userIds)

                            val viewers =
                                activeUserData.mapNotNull { (userId, lastSeen) ->
                                    val username = profiles[userId]?.username ?: return@mapNotNull null
                                    ActiveViewer(userId, username, lastSeen)
                                }
                            trySend(viewers).isSuccess
                        } catch (ex: Exception) {
                            Timber.e(ex, "Error fetching user profiles for presence")
                            trySend(emptyList()).isSuccess
                        }
                    }
                }

            awaitClose { listener.remove() }
        }
            .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions

    /**
     * Adds a UPC to the fridge's shopping list subcollection, with quantity and store.
     *
     * @param fridgeId The ID of the fridge.
     * @param upc The Universal Product Code to add.
     * @param quantity Number of items to purchase (default: 1).
     * @param store Optional store name for purchase.
     * @param customName Optional custom name for the item.
     * @throws IllegalStateException if user is not logged in.
     */
    suspend fun addShoppingListItem(
        fridgeId: String,
        upc: String,
        quantity: Int = 1,
        store: String = "",
        customName: String = ""
    ) {
        val currentUser = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in.")
        val item =
            ShoppingListItem(
                upc = upc,
                // addedAt set via @ServerTimestamp
                addedBy = currentUser,
                quantity = quantity,
                store = store,
                customName = customName
            )
        firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId)
            .collection(FirestoreCollections.SHOPPING_LIST).document(upc).set(item).await()
    }

    /**
     * Removes a UPC from the fridge's shopping list subcollection.
     *
     * @param fridgeId The ID of the fridge.
     * @param upc The Universal Product Code to remove.
     */
    suspend fun removeShoppingListItem(
        fridgeId: String,
        upc: String
    ) {
        firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId)
            .collection(FirestoreCollections.SHOPPING_LIST).document(upc).delete().await()
    }

    /**
     * Updates the current user's obtained quantity atomically using Firestore transactions.
     * Prevents race conditions when multiple users shop simultaneously.
     *
     * @param fridgeId The ID of the fridge.
     * @param upc The Universal Product Code of the item.
     * @param obtainedQuantity The quantity obtained by current user.
     * @param totalQuantity The total quantity needed.
     * @throws IllegalStateException if user is not logged in.
     */
    suspend fun updateShoppingListItemPickup(
        fridgeId: String,
        upc: String,
        obtainedQuantity: Int,
        totalQuantity: Int
    ) {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in.")
        val itemRef =
            firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId)
                .collection(FirestoreCollections.SHOPPING_LIST).document(upc)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(itemRef)
            val currentObtainedBy = snapshot.get(FirestoreFields.OBTAINED_BY) as? Map<String, Long> ?: emptyMap()

            // Update the map with current user's quantity
            val updatedObtainedBy = currentObtainedBy.toMutableMap()
            if (obtainedQuantity > 0) {
                updatedObtainedBy[currentUserId] = obtainedQuantity.toLong()
            } else {
                updatedObtainedBy.remove(currentUserId)
            }

            // Calculate new total from all users
            val newTotal = updatedObtainedBy.values.sum().toInt()
            val checked = newTotal >= totalQuantity

            transaction.update(
                itemRef,
                mapOf(
                    FirestoreFields.OBTAINED_BY to updatedObtainedBy,
                    FirestoreFields.OBTAINED_QUANTITY to newTotal,
                    FirestoreFields.CHECKED to checked,
                    FirestoreFields.LAST_UPDATED_BY to currentUserId,
                    FirestoreFields.LAST_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
        }.await()
    }

    /**
     * Completes shopping session for current user only:
     * - Adds their obtained items to fridge inventory as individual instances
     * - Removes their contribution from shopping list
     * - Keeps items if other users still need them
     *
     * @param fridgeId The ID of the fridge (legacy fridge-level shopping list).
     */
    suspend fun completeShoppingSession(fridgeId: String) {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in.")
        val shoppingListRef = firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId).collection(FirestoreCollections.SHOPPING_LIST)
        val itemsRef = firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId).collection(FirestoreCollections.ITEMS)

        try {
            // Get all shopping list items
            val snapshot = shoppingListRef.get().await()
            val items =
                snapshot.documents.mapNotNull {
                    it.toObject(ShoppingListItem::class.java)
                }

            // Process in a batch
            val batch = firestore.batch()

            items.forEach { item ->
                val userQuantity = item.obtainedBy[currentUserId]?.toInt() ?: 0

                if (userQuantity > 0) {
                    // Create individual item instances for each unit obtained
                    repeat(userQuantity) {
                        val newItemRef = itemsRef.document() // Auto-generate ID
                        val newItem =
                            Item(
                                upc = item.upc,
                                expirationDate = null,
                                addedBy = currentUserId
                                // addedAt and lastUpdatedAt set via @ServerTimestamp
                            )
                        batch.set(newItemRef, newItem)
                    }

                    Timber.d("Adding $userQuantity instance(s) of ${item.upc} to fridge $fridgeId")

                    // Update shopping list item
                    val shoppingItemRef = shoppingListRef.document(item.upc)
                    val remainingObtainedBy = item.obtainedBy.toMutableMap()
                    remainingObtainedBy.remove(currentUserId)

                    val remainingQuantityNeeded = item.quantity - userQuantity
                    val newTotalObtained = remainingObtainedBy.values.sum()

                    if (remainingQuantityNeeded <= 0) {
                        // Current user got all remaining items - delete from shopping list
                        batch.delete(shoppingItemRef)
                    } else {
                        // Still need more items - update quantity and reset obtained tracking
                        batch.update(
                            shoppingItemRef,
                            mapOf(
                                FirestoreFields.QUANTITY to remainingQuantityNeeded,
                                FirestoreFields.OBTAINED_BY to remainingObtainedBy,
                                FirestoreFields.OBTAINED_QUANTITY to newTotalObtained.toInt(),
                                FirestoreFields.CHECKED to (newTotalObtained >= remainingQuantityNeeded),
                                FirestoreFields.LAST_UPDATED_BY to currentUserId,
                                FirestoreFields.LAST_UPDATED_AT to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }

            batch.commit().await()
            Timber.d("Shopping session completed successfully for fridge $fridgeId")
        } catch (e: Exception) {
            Timber.e(e, "Error completing shopping session")
            throw e
        }
    }
}
