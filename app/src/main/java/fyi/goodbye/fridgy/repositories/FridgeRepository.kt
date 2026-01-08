package fyi.goodbye.fridgy.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.Item
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
                        close(e)
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
     * Updates the checked status of a shopping list item.
     */
    suspend fun updateShoppingListItemPickup(
        fridgeId: String,
        upc: String,
        obtainedQuantity: Int,
        totalQuantity: Int
    ) {
        val checked = obtainedQuantity >= totalQuantity
        firestore.collection("fridges").document(fridgeId)
            .collection("shoppingList").document(upc)
            .update(
                mapOf(
                    "checked" to checked,
                    "obtainedQuantity" to obtainedQuantity
                )
            ).await()
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
                        close(e)
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
            val snapshot = firestore.collection("fridges")
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
     * Converts a Firestore document to a Fridge, handling both old Map and new List formats.
     * This provides backward compatibility during data migration.
     */
    private fun DocumentSnapshot.toFridgeCompat(): Fridge? {
        try {
            // Try to parse as new format first
            return this.toObject(Fridge::class.java)?.copy(id = this.id)
        } catch (e: Exception) {
            // Fallback: manually parse for old Map format
            try {
                val id = this.id
                val name = this.getString("name") ?: ""
                val createdBy = this.getString("createdBy") ?: ""
                val createdAt = this.getLong("createdAt") ?: System.currentTimeMillis()

                // Handle members - can be Map or List
                val members =
                    when (val membersField = this.get("members")) {
                        is List<*> -> membersField.filterIsInstance<String>()
                        is Map<*, *> -> membersField.keys.filterIsInstance<String>()
                        else -> emptyList()
                    }

                // Handle pendingInvites - can be Map or List
                val pendingInvites =
                    when (val invitesField = this.get("pendingInvites")) {
                        is List<*> -> invitesField.filterIsInstance<String>()
                        is Map<*, *> -> invitesField.keys.filterIsInstance<String>()
                        else -> emptyList()
                    }

                return Fridge(
                    id = id,
                    name = name,
                    createdBy = createdBy,
                    members = members,
                    pendingInvites = pendingInvites,
                    createdAt = createdAt
                )
            } catch (e2: Exception) {
                Log.e("FridgeRepo", "Error parsing fridge document: ${e2.message}")
                return null
            }
        }
    }

    fun getFridgesForCurrentUser(): Flow<List<Fridge>> =
        callbackFlow {
            val currentUserId = auth.currentUser?.uid ?: return@callbackFlow

            // Optimized query using whereArrayContains (all data migrated to List format)
            val fridgesListenerRegistration =
                firestore.collection("fridges")
                    .whereArrayContains("members", currentUserId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            close(e)
                            return@addSnapshotListener
                        }
                        val fridgesList = snapshot?.documents?.mapNotNull { it.toFridgeCompat() } ?: emptyList()
                        fridgeCache = fridgesList
                        trySend(fridgesList).isSuccess
                    }
            awaitClose { fridgesListenerRegistration.remove() }
        }

    fun getInvitesForCurrentUser(): Flow<List<Fridge>> =
        callbackFlow {
            val currentUserId = auth.currentUser?.uid ?: return@callbackFlow

            // Optimized query using whereArrayContains (all data migrated to List format)
            val invitesListener =
                firestore.collection("fridges")
                    .whereArrayContains("pendingInvites", currentUserId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            close(e)
                            return@addSnapshotListener
                        }
                        val invites = snapshot?.documents?.mapNotNull { it.toFridgeCompat() } ?: emptyList()
                        trySend(invites).isSuccess
                    }
            awaitClose { invitesListener.remove() }
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

        // OPTIMIZATION: Skip expensive user fetching if not needed (e.g., inventory screen)
        if (!fetchUserDetails) {
            return DisplayFridge(
                id = fridge.id,
                name = fridge.name,
                createdByUid = fridge.createdBy,
                creatorDisplayName = "",
                memberUsers = emptyList(),
                pendingInviteUsers = emptyList(),
                createdAt = fridge.createdAt,
                type = fridge.type
            )
        }

        // Fetch all user data for members and invites
        val allUserIds = (fridge.members + fridge.pendingInvites + listOf(fridge.createdBy)).distinct()
        val usersMap = getUsersByIds(allUserIds)

        val memberUsers = fridge.members.mapNotNull { usersMap[it] }
        val inviteUsers = fridge.pendingInvites.mapNotNull { usersMap[it] }
        val creatorName = usersMap[fridge.createdBy]?.username ?: "Unknown"

        return DisplayFridge(
            id = fridge.id,
            name = fridge.name,
            createdByUid = fridge.createdBy,
            creatorDisplayName = creatorName,
            memberUsers = memberUsers,
            pendingInviteUsers = inviteUsers,
            createdAt = fridge.createdAt,
            type = fridge.type
        )
    }

    suspend fun createFridge(fridgeName: String, fridgeType: String = "fridge", fridgeLocation: String = ""): Fridge {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")

        val newFridgeDocRef = firestore.collection("fridges").document()
        val fridgeId = newFridgeDocRef.id

        val newFridge =
            Fridge(
                id = fridgeId,
                name = fridgeName,
                type = fridgeType,
                location = fridgeLocation,
                createdBy = currentUser.uid,
                members = listOf(currentUser.uid),
                createdAt = System.currentTimeMillis()
            )

        newFridgeDocRef.set(newFridge).await()
        return newFridge
    }

    suspend fun inviteUserByEmail(
        fridgeId: String,
        email: String
    ) {
        // Changed to invite by username since emails should remain private
        // This method name is kept for compatibility but now searches by username
        val snapshot = firestore.collection("userProfiles").whereEqualTo("username", email).get().await()
        val userDoc = snapshot.documents.firstOrNull() ?: throw Exception("User not found.")
        val userUid = userDoc.id
        val username = userDoc.getString("username") ?: email

        val fridgeDoc = firestore.collection("fridges").document(fridgeId).get().await()
        val fridge = fridgeDoc.toObject(Fridge::class.java) ?: throw Exception("Fridge not found.")

        if (fridge.members.contains(userUid)) throw Exception("User is already a member.")
        if (fridge.pendingInvites.contains(userUid)) throw Exception("Invitation already sent.")

        // Update fridge with pending invite
        firestore.collection("fridges").document(fridgeId)
            .update("pendingInvites", FieldValue.arrayUnion(userUid))
            .await()
        
        // Create notification for the invited user
        val currentUser = auth.currentUser
        val inviterName = currentUser?.displayName ?: "Someone"
        val notificationData = hashMapOf(
            "userId" to userUid,
            "title" to "Fridge Invitation",
            "body" to "You were invited to join '${fridge.name}'",
            "type" to "FRIDGE_INVITE",
            "relatedFridgeId" to fridgeId,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        
        firestore.collection("notifications")
            .add(notificationData)
            .await()
    }

    suspend fun acceptInvite(fridgeId: String) {
        val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in.")

        val fridgeRef = firestore.collection("fridges").document(fridgeId)

        firestore.runTransaction { transaction ->
            transaction.update(fridgeRef, "members", FieldValue.arrayUnion(currentUserId))
            transaction.update(fridgeRef, "pendingInvites", FieldValue.arrayRemove(currentUserId))
        }.await()
        
        // Mark related invitation notification as read
        try {
            val notificationQuery = firestore.collection("notifications")
                .whereEqualTo("userId", currentUserId)
                .whereEqualTo("type", "FRIDGE_INVITE")
                .whereEqualTo("relatedFridgeId", fridgeId)
                .get()
                .await()
            
            notificationQuery.documents.forEach { doc ->
                doc.reference.update("isRead", true).await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mark invitation notification as read", e)
        }
    }

    suspend fun declineInvite(fridgeId: String) {
        val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in.")
        firestore.collection("fridges").document(fridgeId)
            .update("pendingInvites", FieldValue.arrayRemove(currentUserId)).await()
        
        // Delete related invitation notification
        try {
            val notificationQuery = firestore.collection("notifications")
                .whereEqualTo("userId", currentUserId)
                .whereEqualTo("type", "FRIDGE_INVITE")
                .whereEqualTo("relatedFridgeId", fridgeId)
                .get()
                .await()
            
            notificationQuery.documents.forEach { doc ->
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete invitation notification", e)
        }
    }

    suspend fun removeMember(
        fridgeId: String,
        userId: String
    ) {
        firestore.collection("fridges").document(fridgeId)
            .update("members", FieldValue.arrayRemove(userId))
            .await()
    }

    suspend fun revokeInvite(
        fridgeId: String,
        userId: String
    ) {
        firestore.collection("fridges").document(fridgeId)
            .update("pendingInvites", FieldValue.arrayRemove(userId))
            .await()
    }

    suspend fun leaveFridge(fridgeId: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("fridges").document(fridgeId)
            .update("members", FieldValue.arrayRemove(uid))
            .await()
    }

    /**
     * Preloads items for a specific fridge from Firestore cache to memory.
     * Should be called when opening a fridge inventory to enable instant display.
     * This reads from Firestore's local persistence layer without network access.
     */
    suspend fun preloadItemsFromCache(fridgeId: String): List<Item> {
        return try {
            val snapshot = firestore.collection("fridges")
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

    fun getItemsForFridge(fridgeId: String): Flow<List<Item>> =
        callbackFlow {
            // Use SnapshotListener with local cache support
            val listener =
                firestore.collection("fridges").document(fridgeId).collection("items")
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            // Gracefully handle permission errors (fridge deleted/left)
                            if (e.message?.contains("PERMISSION_DENIED") == true) {
                                Log.w("FridgeRepo", "Permission denied for fridge $fridgeId - sending empty list")
                                trySend(emptyList()).isSuccess
                                close() // Close flow gracefully without error
                            } else {
                                close(e)
                            }
                            return@addSnapshotListener
                        }
                        val items = snapshot?.documents?.mapNotNull { it.toObject(Item::class.java) } ?: emptyList()
                        trySend(items).isSuccess
                    }
            awaitClose { listener.remove() }
        }

    /**
     * Adds or increments an item in the fridge.
     * Uses a non-blocking Firestore update to ensure instant local response.
     */
    suspend fun addItemToFridge(
        fridgeId: String,
        upc: String
    ) {
        Log.d("FridgeRepo", "Starting addItemToFridge for UPC: $upc")
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")
        val itemRef =
            firestore.collection("fridges").document(fridgeId)
                .collection("items").document(upc)

        try {
            // First, try a simple non-transactional update for speed (Firestore handles local latency)
            val snapshot = itemRef.get(Source.CACHE).await()
            if (snapshot.exists()) {
                itemRef.update(
                    "quantity",
                    FieldValue.increment(1),
                    "lastUpdatedBy",
                    currentUser.uid,
                    "lastUpdatedAt",
                    System.currentTimeMillis()
                ).await()
            } else {
                val itemToAdd =
                    Item(
                        upc = upc,
                        quantity = 1,
                        addedBy = currentUser.uid,
                        addedAt = System.currentTimeMillis(),
                        lastUpdatedBy = currentUser.uid,
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                itemRef.set(itemToAdd).await()
            }
            Log.d("FridgeRepo", "Item $upc locally added to fridge $fridgeId")
        } catch (e: Exception) {
            // Fallback to transaction if cache fetch fails or other issues occur
            Log.w("FridgeRepo", "Local add failed, falling back to transaction: ${e.message}")
            firestore.runTransaction { transaction ->
                val serverSnapshot = transaction.get(itemRef)
                if (serverSnapshot.exists()) {
                    transaction.update(
                        itemRef,
                        "quantity",
                        FieldValue.increment(1),
                        "lastUpdatedBy",
                        currentUser.uid,
                        "lastUpdatedAt",
                        System.currentTimeMillis()
                    )
                } else {
                    val itemToAdd =
                        Item(
                            upc = upc,
                            quantity = 1,
                            addedBy = currentUser.uid,
                            addedAt = System.currentTimeMillis(),
                            lastUpdatedBy = currentUser.uid,
                            lastUpdatedAt = System.currentTimeMillis()
                        )
                    transaction.set(itemRef, itemToAdd)
                }
            }.await()
        }
    }

    suspend fun updateItemQuantity(
        fridgeId: String,
        itemId: String,
        newQuantity: Int
    ) {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")
        if (newQuantity <= 0) {
            firestore.collection("fridges").document(fridgeId)
                .collection("items").document(itemId).delete().await()
        } else {
            firestore.collection("fridges").document(fridgeId)
                .collection("items").document(itemId)
                .update(
                    "quantity",
                    newQuantity,
                    "lastUpdatedBy",
                    currentUser.uid,
                    "lastUpdatedAt",
                    System.currentTimeMillis()
                ).await()
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
