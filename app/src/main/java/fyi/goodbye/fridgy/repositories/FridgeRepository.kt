package fyi.goodbye.fridgy.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.DisplayItem
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.models.User
import fyi.goodbye.fridgy.models.UserProfile
import fyi.goodbye.fridgy.utils.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
 * @see HouseholdRepository For household-level shopping list operations (preferred)
 * @see ProductRepository For product information lookup
 */
class FridgeRepository {
    /**
     * Returns a Flow of shopping list items for a fridge subcollection.
     *
     * @param fridgeId The ID of the fridge to get shopping list items from.
     * @return Flow emitting list of shopping list items, updates in real-time.
     */
    fun getShoppingListItems(fridgeId: String): Flow<List<fyi.goodbye.fridgy.models.ShoppingListItem>> =
        callbackFlow {
            val colRef = firestore.collection("fridges").document(fridgeId).collection("shoppingList")
            val listener =
                colRef.addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e("FridgeRepo", "Error fetching shopping list items: ${e.message}", e)
                        // Send empty list instead of closing with error to prevent app crash
                        trySend(emptyList()).isSuccess
                        return@addSnapshotListener
                    }
                    val items =
                        snapshot?.documents?.mapNotNull {
                            it.toObject(fyi.goodbye.fridgy.models.ShoppingListItem::class.java)
                        } ?: emptyList()
                    trySend(items).isSuccess
                }
            awaitClose { listener.remove() }
        }

    /**
     * Sets the current user as actively viewing the shopping list.
     * Uses Firestore serverTimestamp for automatic cleanup of stale presence.
     */
    suspend fun setShoppingListPresence(fridgeId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val presenceRef =
            firestore.collection("fridges").document(fridgeId)
                .collection("shoppingListPresence").document(currentUserId)

        presenceRef.set(
            mapOf(
                "userId" to currentUserId,
                "lastSeen" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /**
     * Removes the current user's presence from the shopping list.
     */
    suspend fun removeShoppingListPresence(fridgeId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        firestore.collection("fridges").document(fridgeId)
            .collection("shoppingListPresence").document(currentUserId)
            .delete().await()
    }

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
                firestore.collection("fridges").document(fridgeId)
                    .collection("shoppingListPresence")

            val listener =
                presenceRef.addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e("FridgeRepo", "Error fetching shopping list presence: ${e.message}", e)
                        // Send empty list instead of closing with error to prevent app crash
                        trySend(emptyList()).isSuccess
                        return@addSnapshotListener
                    }

                    val currentTime = System.currentTimeMillis()
                    
                    // Collect active user IDs and their timestamps first (no async here)
                    val activeUserData = snapshot?.documents?.mapNotNull { doc ->
                        val lastSeen = doc.getTimestamp("lastSeen")?.toDate()?.time ?: 0
                        val userId = doc.getString("userId")
                        // Consider active if seen within last 30 seconds
                        if (userId != null && (currentTime - lastSeen) < PRESENCE_TIMEOUT_MS) {
                            userId to lastSeen
                        } else null
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
                            
                            val viewers = activeUserData.mapNotNull { (userId, lastSeen) ->
                                val username = profiles[userId]?.username ?: return@mapNotNull null
                                ActiveViewer(userId, username, lastSeen)
                            }
                            trySend(viewers).isSuccess
                        } catch (ex: Exception) {
                            Log.e("FridgeRepo", "Error fetching user profiles for presence", ex)
                            trySend(emptyList()).isSuccess
                        }
                    }
                }

            awaitClose { listener.remove() }
        }

    /**
     * Adds a UPC to the fridge's shopping list subcollection, with quantity and store.
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
            fyi.goodbye.fridgy.models.ShoppingListItem(
                upc = upc,
                addedAt = System.currentTimeMillis(),
                addedBy = currentUser,
                quantity = quantity,
                store = store,
                customName = customName
            )
        firestore.collection("fridges").document(fridgeId)
            .collection("shoppingList").document(upc).set(item).await()
    }

    /**
     * Removes a UPC from the fridge's shopping list subcollection.
     */
    suspend fun removeShoppingListItem(
        fridgeId: String,
        upc: String
    ) {
        firestore.collection("fridges").document(fridgeId)
            .collection("shoppingList").document(upc).delete().await()
    }

    /**
     * Updates the current user's obtained quantity atomically using Firestore transactions.
     * Prevents race conditions when multiple users shop simultaneously.
     */
    suspend fun updateShoppingListItemPickup(
        fridgeId: String,
        upc: String,
        obtainedQuantity: Int,
        totalQuantity: Int
    ) {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in.")
        val itemRef =
            firestore.collection("fridges").document(fridgeId)
                .collection("shoppingList").document(upc)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(itemRef)
            val currentObtainedBy = snapshot.get("obtainedBy") as? Map<String, Long> ?: emptyMap()

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
                    "obtainedBy" to updatedObtainedBy,
                    "obtainedQuantity" to newTotal,
                    "checked" to checked,
                    "lastUpdatedBy" to currentUserId,
                    "lastUpdatedAt" to System.currentTimeMillis()
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
        val shoppingListRef = firestore.collection("fridges").document(fridgeId).collection("shoppingList")
        val itemsRef = firestore.collection("fridges").document(fridgeId).collection("items")
        
        // Get the householdId for the fridge (needed for item creation)
        val fridgeDoc = firestore.collection("fridges").document(fridgeId).get().await()
        val householdId = fridgeDoc.getString("householdId") ?: ""

        try {
            // Get all shopping list items
            val snapshot = shoppingListRef.get().await()
            val items =
                snapshot.documents.mapNotNull {
                    it.toObject(fyi.goodbye.fridgy.models.ShoppingListItem::class.java)
                }

            // Process in a batch
            val batch = firestore.batch()

            items.forEach { item ->
                val userQuantity = item.obtainedBy[currentUserId]?.toInt() ?: 0

                if (userQuantity > 0) {
                    // Create individual item instances for each unit obtained
                    repeat(userQuantity) {
                        val newItemRef = itemsRef.document() // Auto-generate ID
                        val newItem = Item(
                            upc = item.upc,
                            expirationDate = null,
                            addedBy = currentUserId,
                            addedAt = System.currentTimeMillis(),
                            lastUpdatedBy = currentUserId,
                            lastUpdatedAt = System.currentTimeMillis(),
                            householdId = householdId
                        )
                        batch.set(newItemRef, newItem)
                    }

                    Log.d(TAG, "Adding $userQuantity instance(s) of ${item.upc} to fridge $fridgeId")

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
                                "quantity" to remainingQuantityNeeded,
                                "obtainedBy" to remainingObtainedBy,
                                "obtainedQuantity" to newTotalObtained,
                                "checked" to false,
                                "lastUpdatedBy" to currentUserId,
                                "lastUpdatedAt" to System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            batch.commit().await()
            Log.d(TAG, "Shopping session completed successfully for fridge $fridgeId")
        } catch (e: Exception) {
            Log.e(TAG, "Error completing shopping session", e)
            throw e
        }
    }

    /**
     * Returns a Flow of shopping list UPCs for a fridge.
     *
     * This is a legacy method that reads from the fridge document's shopping list array.
     * For household-level shopping lists, use [HouseholdRepository.getShoppingListItems].
     *
     * @param fridgeId The ID of the fridge.
     * @return Flow emitting list of UPC strings.
     */
    fun getShoppingList(fridgeId: String): Flow<List<String>> =
        callbackFlow {
            val docRef = firestore.collection("fridges").document(fridgeId)
            val listener =
                docRef.addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e("FridgeRepo", "Error fetching shopping list: ${e.message}", e)
                        // Send empty list instead of closing with error to prevent app crash
                        trySend(emptyList()).isSuccess
                        return@addSnapshotListener
                    }
                    val shoppingList = snapshot?.get("shoppingList") as? List<String> ?: emptyList()
                    trySend(shoppingList).isSuccess
                }
            awaitClose { listener.remove() }
        }

    /**
     * Adds a UPC to the fridge's shopping list array.
     *
     * @param fridgeId The ID of the fridge.
     * @param upc The Universal Product Code to add.
     */
    suspend fun addItemToShoppingList(
        fridgeId: String,
        upc: String
    ) {
        firestore.collection("fridges").document(fridgeId)
            .update("shoppingList", FieldValue.arrayUnion(upc)).await()
    }

    /**
     * Removes a UPC from the fridge's shopping list array.
     *
     * @param fridgeId The ID of the fridge.
     * @param upc The Universal Product Code to remove.
     */
    suspend fun removeItemFromShoppingList(
        fridgeId: String,
        upc: String
    ) {
        firestore.collection("fridges").document(fridgeId)
            .update("shoppingList", FieldValue.arrayRemove(upc)).await()
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var fridgeCache: List<Fridge> = emptyList()

    /**
     * Preloads user's fridges from Firestore cache to memory for instant cold-start access.
     * Should be called on app initialization to populate cache before UI loads.
     * This reads from Firestore's local persistence layer without network access.
     */
    suspend fun preloadFridgesFromCache() {
        try {
            val currentUserId = auth.currentUser?.uid ?: return
            val snapshot =
                firestore.collection("fridges")
                    .whereArrayContains("members", currentUserId)
                    .get(Source.CACHE)
                    .await()

            fridgeCache = snapshot.documents.mapNotNull { it.toFridgeCompat() }
            Log.d("FridgeRepo", "Preloaded ${fridgeCache.size} fridges from cache")
        } catch (e: Exception) {
            Log.w("FridgeRepo", "Could not preload from cache (likely first run): ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FridgeRepository"
        private const val PRESENCE_TIMEOUT_MS = 30_000L

        /** Maximum number of user profiles to cache. */
        private const val USER_PROFILE_CACHE_SIZE = 100

        /**
         * LRU cache for user profiles, shared across repository instances.
         * Evicts least recently used profiles when capacity is reached.
         */
        private val userProfileCache = LruCache<String, UserProfile>(USER_PROFILE_CACHE_SIZE)
    }

    /**
     * Converts a Firestore document to a Fridge, handling both old and new formats.
     * This provides backward compatibility during data migration.
     */
    private fun DocumentSnapshot.toFridgeCompat(): Fridge? {
        try {
            val id = this.id
            val name = this.getString("name") ?: ""
            val type = this.getString("type") ?: "fridge"
            val location = this.getString("location") ?: ""
            val householdId = this.getString("householdId") ?: ""
            val createdBy = this.getString("createdBy") ?: ""
            val createdAt = this.getLong("createdAt") ?: System.currentTimeMillis()

            return Fridge(
                id = id,
                name = name,
                type = type,
                location = location,
                householdId = householdId,
                createdBy = createdBy,
                createdAt = createdAt
            )
        } catch (e: Exception) {
            Log.e("FridgeRepo", "Error parsing fridge document: ${e.message}")
            return null
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
                firestore.collection("fridges")
                    .whereEqualTo("householdId", householdId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            // Check if this is a permission error
                            if (e.message?.contains("PERMISSION_DENIED") == true) {
                                Log.w("FridgeRepo", "Permission denied for household $householdId - user likely removed. Clearing cache.")
                                // Clear any cached fridges from this household
                                fridgeCache = fridgeCache.filter { it.householdId != householdId }
                            } else {
                                Log.e("FridgeRepo", "Error listening to fridges for household: ${e.message}", e)
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

    /**
     * Fetches a raw Fridge object by ID without user profile resolution.
     *
     * @param fridgeId The ID of the fridge to fetch.
     * @return The Fridge object, or null if not found or on error.
     */
    suspend fun getRawFridgeById(fridgeId: String): Fridge? {
        return try {
            val doc = firestore.collection("fridges").document(fridgeId).get().await()
            doc.toFridgeCompat()
        } catch (e: Exception) {
            Log.e("FridgeRepo", "Error fetching raw fridge: ${e.message}")
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
                            firestore.collection("fridges").document(fridgeId).get(Source.CACHE).await()
                        } catch (e: Exception) {
                            null
                        }

                    if (cacheDoc?.exists() == true) {
                        cacheDoc.toFridgeCompat()
                    } else {
                        // Fallback to network if cache miss
                        val doc = firestore.collection("fridges").document(fridgeId).get(Source.DEFAULT).await()
                        doc.toFridgeCompat()
                    }
                } catch (e: Exception) {
                    null
                }
            } ?: return null

        // Fetch creator display name if needed
        val creatorName =
            if (fetchUserDetails && fridge.createdBy.isNotEmpty()) {
                val usersMap = getUsersByIds(listOf(fridge.createdBy))
                usersMap[fridge.createdBy]?.username ?: "Unknown"
            } else {
                ""
            }

        return DisplayFridge(
            id = fridge.id,
            name = fridge.name,
            type = fridge.type,
            householdId = fridge.householdId,
            createdByUid = fridge.createdBy,
            creatorDisplayName = creatorName,
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

        val newFridgeDocRef = firestore.collection("fridges").document()
        val fridgeId = newFridgeDocRef.id

        val newFridge =
            Fridge(
                id = fridgeId,
                name = fridgeName,
                type = fridgeType,
                location = fridgeLocation,
                householdId = householdId,
                createdBy = currentUser.uid,
                createdAt = System.currentTimeMillis()
            )

        newFridgeDocRef.set(newFridge).await()
        return newFridge
    }

    // NOTE: Member management (invite, accept, decline, remove, revoke, leave) has been moved
    // to HouseholdRepository with invite code-based joining. The old fridge-level invitation
    // system is no longer supported.

    /**
     * Preloads items for a specific fridge from Firestore cache to memory.
     * Should be called when opening a fridge inventory to enable instant display.
     * This reads from Firestore's local persistence layer without network access.
     */
    suspend fun preloadItemsFromCache(fridgeId: String): List<Item> {
        return try {
            val snapshot =
                firestore.collection("fridges")
                    .document(fridgeId)
                    .collection("items")
                    .get(Source.CACHE)
                    .await()

            val items = snapshot.documents.mapNotNull { it.toObject(Item::class.java) }
            Log.d("FridgeRepo", "Preloaded ${items.size} items from cache for fridge $fridgeId")
            items
        } catch (e: Exception) {
            Log.w("FridgeRepo", "Could not preload items from cache: ${e.message}")
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
                firestore.collection("fridges").document(fridgeId).collection("items")
                    .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, e ->
                        if (e != null) {
                            Log.e("FridgeRepo", "Error fetching items for fridge $fridgeId: ${e.message}", e)
                            // Send empty list instead of closing with error to prevent app crash
                            trySend(emptyList()).isSuccess
                            return@addSnapshotListener
                        }
                        
                        val items = snapshot?.documents?.mapNotNull { 
                            it.toObject(Item::class.java)?.copy(id = it.id)
                        } ?: emptyList()
                        
                        Log.d("FridgeRepo", "Items snapshot received for fridge $fridgeId: ${items.size} items")
                        
                        // Create DisplayItems without product info (ViewModel will fetch products)
                        val displayItems = items.map { item ->
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

    /**
     * Adds a new item instance to a fridge.
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
        Log.d("FridgeRepo", "Adding item instance for UPC: $upc with expiration: $expirationDate")
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")

        // Get the fridge to access householdId (required for security rules)
        val fridgeDoc = firestore.collection("fridges").document(fridgeId).get().await()
        val householdId =
            fridgeDoc.getString("householdId")
                ?: throw IllegalStateException("Fridge has no householdId")

        val newItem = Item(
            upc = upc,
            expirationDate = expirationDate,
            addedBy = currentUser.uid,
            addedAt = System.currentTimeMillis(),
            lastUpdatedBy = currentUser.uid,
            lastUpdatedAt = System.currentTimeMillis(),
            householdId = householdId
        )

        Log.d("FridgeRepo", "=== ITEM CREATION DEBUG ===")
        Log.d("FridgeRepo", "Current User UID: ${currentUser.uid}")
        Log.d("FridgeRepo", "Fridge ID: $fridgeId")
        Log.d("FridgeRepo", "Household ID: $householdId")
        Log.d("FridgeRepo", "Item UPC: $upc")
        Log.d("FridgeRepo", "Item householdId: ${newItem.householdId}")
        Log.d("FridgeRepo", "Item addedBy: ${newItem.addedBy}")
        Log.d("FridgeRepo", "Item lastUpdatedBy: ${newItem.lastUpdatedBy}")
        Log.d("FridgeRepo", "Item expirationDate: ${newItem.expirationDate}")
        
        // Check if user is actually a member of this household
        try {
            val householdDoc = firestore.collection("households").document(householdId).get().await()
            val members = householdDoc.get("members") as? List<*>
            Log.d("FridgeRepo", "Household members: $members")
            Log.d("FridgeRepo", "Is current user a member? ${members?.contains(currentUser.uid)}")
        } catch (e: Exception) {
            Log.e("FridgeRepo", "Failed to check household membership", e)
        }

        try {
            val docRef = firestore.collection("fridges")
                .document(fridgeId)
                .collection("items")
                .add(newItem)
                .await()
            
            Log.d("FridgeRepo", "Added item instance: ${docRef.id} (UPC: $upc)")
            return newItem.copy(id = docRef.id)
        } catch (e: Exception) {
            Log.e("FridgeRepo", "Error adding item to fridge", e)
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
            firestore.collection("fridges")
                .document(fridgeId)
                .collection("items")
                .document(itemId)
                .update(
                    mapOf(
                        "expirationDate" to expirationDate,
                        "lastUpdatedBy" to currentUser.uid,
                        "lastUpdatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
            
            Log.d("FridgeRepo", "Updated expiration date for item: $itemId")
        } catch (e: Exception) {
            Log.e("FridgeRepo", "Error updating expiration date", e)
            throw e
        }
    }

    /**
     * Legacy method for backward compatibility during migration.
     * @deprecated Use updateItemExpirationDate instead
     */
    @Deprecated("Items are now individual instances, use deleteItem instead")
    suspend fun updateItemQuantity(
        fridgeId: String,
        itemId: String,
        newQuantity: Int
    ) {
        // For backward compatibility, if quantity is 0, delete the item
        if (newQuantity <= 0) {
            deleteItem(fridgeId, itemId)
        }
        // Note: Increasing quantity should now add new item instances
    }

    /**
     * Deletes a specific item instance from a fridge.
     * 
     * @param fridgeId Target fridge ID
     * @param itemId The unique item instance ID to delete
     */
    suspend fun deleteItem(fridgeId: String, itemId: String) {
        try {
            firestore.collection("fridges")
                .document(fridgeId)
                .collection("items")
                .document(itemId)
                .delete()
                .await()
            
            Log.d("FridgeRepo", "Deleted item: $itemId")
        } catch (e: Exception) {
            Log.e("FridgeRepo", "Error deleting item", e)
            throw e
        }
    }

    /**
     * Deletes a fridge and all its items.
     *
     * Performs a batch delete of all items in the fridge's subcollection
     * followed by the fridge document itself.
     *
     * @param fridgeId The ID of the fridge to delete.
     */
    suspend fun deleteFridge(fridgeId: String) {
        val fridgeRef = firestore.collection("fridges").document(fridgeId)
        val items = fridgeRef.collection("items").get().await()
        val batch = firestore.batch()
        items.documents.forEach { batch.delete(it.reference) }
        batch.delete(fridgeRef)
        batch.commit().await()
    }

    /**
     * Fetches a user by ID from the users collection.
     *
     * @param userId The user's Firebase UID.
     * @return User object, or null if not found.
     */
    suspend fun getUserById(userId: String): User? {
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
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
            val doc = firestore.collection("userProfiles").document(userId).get().await()
            doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetches multiple users' public profiles by their IDs.
     * Returns a map of userId to UserProfile for easy lookup.
     * This queries the userProfiles collection which contains only public data (username).
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
                        firestore.collection("userProfiles")
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

            Log.d(
                "FridgeRepo",
                "Fetched ${result.size} user profiles (${result.size - missingIds.size} from cache, ${missingIds.size} from network)"
            )
            result
        } catch (e: Exception) {
            Log.e("FridgeRepo", "Error fetching user profiles by IDs: ${e.message}", e)
            emptyMap()
        }
    }
}
