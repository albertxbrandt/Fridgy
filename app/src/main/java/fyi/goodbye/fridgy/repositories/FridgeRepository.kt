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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository class responsible for handling all data operations related to Fridges and Items.
 */
class FridgeRepository {
    /**
     * Returns a Flow of shopping list items for a fridge (subcollection).
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
                    val activeViewers = mutableListOf<ActiveViewer>()

                    snapshot?.documents?.forEach { doc ->
                        val lastSeen = doc.getTimestamp("lastSeen")?.toDate()?.time ?: 0
                        val userId = doc.getString("userId")

                        // Consider active if seen within last 30 seconds
                        if (userId != null && (currentTime - lastSeen) < 30_000) {
                            // Fetch username from userProfiles collection
                            firestore.collection("userProfiles").document(userId)
                                .get()
                                .addOnSuccessListener { userDoc ->
                                    val username = userDoc.getString("username") ?: "Unknown User"
                                    activeViewers.add(ActiveViewer(userId, username, lastSeen))
                                    // Send updated list after each username is fetched
                                    trySend(activeViewers.toList()).isSuccess
                                }
                        }
                    }

                    // Send empty list if no active viewers
                    if (snapshot?.documents?.isEmpty() == true) {
                        trySend(emptyList()).isSuccess
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
     * - Adds their obtained items to fridge inventory
     * - Removes their contribution from shopping list
     * - Keeps items if other users still need them
     */
    suspend fun completeShoppingSession(fridgeId: String) {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in.")
        val shoppingListRef = firestore.collection("fridges").document(fridgeId).collection("shoppingList")
        val itemsRef = firestore.collection("fridges").document(fridgeId).collection("items")

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
                val userQuantity = item.obtainedBy[currentUserId] ?: 0

                if (userQuantity > 0) {
                    // Add user's obtained quantity to fridge inventory
                    val itemRef = itemsRef.document(item.upc)
                    val fridgeItemSnapshot = itemRef.get().await()

                    if (fridgeItemSnapshot.exists()) {
                        // Item exists, increment quantity
                        val currentQty = fridgeItemSnapshot.getLong("quantity") ?: 0L
                        batch.update(
                            itemRef,
                            mapOf(
                                "quantity" to currentQty + userQuantity,
                                "lastUpdatedBy" to currentUserId,
                                "lastUpdatedAt" to System.currentTimeMillis()
                            )
                        )
                    } else {
                        // TODO: Update for instance-based items
                        // Need to create userQuantity individual instances
                        // For now, skip item creation in shopping list completion
                        /*
                        val newItem =
                            Item(
                                upc = item.upc,
                                quantity = userQuantity,
                                addedBy = currentUserId,
                                addedAt = System.currentTimeMillis(),
                                lastUpdatedBy = currentUserId,
                                lastUpdatedAt = System.currentTimeMillis()
                            )
                        batch.set(itemRef, newItem)
                        */
                    }

                    // Update shopping list item
                    val shoppingItemRef = shoppingListRef.document(item.upc)
                    val remainingObtainedBy = item.obtainedBy.toMutableMap()
                    remainingObtainedBy.remove(currentUserId)

                    // Calculate remaining quantity needed after removing current user's contribution
                    val totalObtainedByAllUsers = item.obtainedBy.values.sum()
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
        } catch (e: Exception) {
            Log.e(TAG, "Error completing shopping session", e)
            throw e
        }
    }

    /**
     * Returns a Flow of the shopping list UPCs for a fridge.
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
     * Adds a UPC to the fridge's shopping list.
     */
    suspend fun addItemToShoppingList(
        fridgeId: String,
        upc: String
    ) {
        firestore.collection("fridges").document(fridgeId)
            .update("shoppingList", FieldValue.arrayUnion(upc)).await()
    }

    /**
     * Removes a UPC from the fridge's shopping list.
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
     * @deprecated Use getFridgesForHousehold instead. This is kept for migration compatibility.
     */
    @Deprecated("Use getFridgesForHousehold with householdId", ReplaceWith("getFridgesForHousehold(householdId)"))
    fun getFridgesForCurrentUser(): Flow<List<Fridge>> =
        callbackFlow {
            val currentUserId = auth.currentUser?.uid ?: return@callbackFlow

            // Legacy query - will find fridges without householdId
            val fridgesListenerRegistration =
                firestore.collection("fridges")
                    .whereArrayContains("members", currentUserId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            // Check if this is a permission error
                            if (e.message?.contains("PERMISSION_DENIED") == true) {
                                Log.w("FridgeRepo", "Permission denied fetching fridges - clearing cache")
                                fridgeCache = emptyList()
                            } else {
                                Log.e("FridgeRepo", "Error listening to fridges: ${e.message}", e)
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
     * @deprecated Invites are now handled at household level via invite codes.
     */
    @Deprecated("Invites moved to HouseholdRepository with invite codes")
    fun getInvitesForCurrentUser(): Flow<List<Fridge>> =
        callbackFlow {
            // Return empty flow - invites are now handled at household level
            trySend(emptyList()).isSuccess
            awaitClose { }
        }

    suspend fun getRawFridgeById(fridgeId: String): Fridge? {
        return try {
            val doc = firestore.collection("fridges").document(fridgeId).get().await()
            doc.toFridgeCompat()
        } catch (e: Exception) {
            Log.e("FridgeRepo", "Error fetching raw fridge: ${e.message}")
            null
        }
    }

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

    /**
     * @deprecated Member management moved to HouseholdRepository with invite codes.
     */
    @Deprecated("Use HouseholdRepository.createInviteCode instead")
    suspend fun inviteUserByEmail(
        fridgeId: String,
        email: String
    ) {
        throw UnsupportedOperationException("Invites are now handled at household level via invite codes")
    }

    /**
     * @deprecated Member management moved to HouseholdRepository with invite codes.
     */
    @Deprecated("Use HouseholdRepository.redeemInviteCode instead")
    suspend fun acceptInvite(fridgeId: String) {
        throw UnsupportedOperationException("Invites are now handled at household level via invite codes")
    }

    /**
     * @deprecated Member management moved to HouseholdRepository with invite codes.
     */
    @Deprecated("Invites handled at household level")
    suspend fun declineInvite(fridgeId: String) {
        throw UnsupportedOperationException("Invites are now handled at household level via invite codes")
    }

    /**
     * @deprecated Member management moved to HouseholdRepository.
     */
    @Deprecated("Use HouseholdRepository.removeMember instead")
    suspend fun removeMember(
        fridgeId: String,
        userId: String
    ) {
        throw UnsupportedOperationException("Member management is now at household level")
    }

    /**
     * @deprecated Member management moved to HouseholdRepository.
     */
    @Deprecated("Invites handled at household level")
    suspend fun revokeInvite(
        fridgeId: String,
        userId: String
    ) {
        throw UnsupportedOperationException("Invites are now handled at household level via invite codes")
    }

    /**
     * @deprecated Member management moved to HouseholdRepository.
     */
    @Deprecated("Use HouseholdRepository.leaveHousehold instead")
    suspend fun leaveFridge(fridgeId: String) {
        throw UnsupportedOperationException("Member management is now at household level")
    }

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
        expirationDate: Long? = null,
        size: Double? = null,
        unit: String? = null
    ): Item {
        Log.d("FridgeRepo", "Adding item instance for UPC: $upc with expiration: $expirationDate, size: $size, unit: $unit")
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")

        // Get the fridge to access householdId (required for security rules)
        val fridgeDoc = firestore.collection("fridges").document(fridgeId).get().await()
        val householdId =
            fridgeDoc.getString("householdId")
                ?: throw IllegalStateException("Fridge has no householdId")

        val newItem = Item(
            upc = upc,
            expirationDate = expirationDate,
            size = size,
            unit = unit,
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
        Log.d("FridgeRepo", "Item size: ${newItem.size}")
        Log.d("FridgeRepo", "Item unit: ${newItem.unit}")
        
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

    suspend fun deleteFridge(fridgeId: String) {
        val fridgeRef = firestore.collection("fridges").document(fridgeId)
        val items = fridgeRef.collection("items").get().await()
        val batch = firestore.batch()
        items.documents.forEach { batch.delete(it.reference) }
        batch.delete(fridgeRef)
        batch.commit().await()
    }

    suspend fun getUserById(userId: String): User? {
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            doc.toObject(User::class.java)?.copy(uid = doc.id)
        } catch (e: Exception) {
            null
        }
    }

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
