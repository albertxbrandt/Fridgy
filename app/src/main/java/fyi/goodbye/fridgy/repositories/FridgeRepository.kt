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
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var fridgeCache: List<Fridge> = emptyList()

    companion object {
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

    suspend fun getFridgeById(fridgeId: String, fetchUserDetails: Boolean = true): DisplayFridge? {
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
                createdAt = fridge.createdAt
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
            createdAt = fridge.createdAt
        )
    }

    suspend fun createFridge(fridgeName: String): Fridge {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")

        val newFridgeDocRef = firestore.collection("fridges").document()
        val fridgeId = newFridgeDocRef.id

        val newFridge =
            Fridge(
                id = fridgeId,
                name = fridgeName,
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

        val fridgeDoc = firestore.collection("fridges").document(fridgeId).get().await()
        val fridge = fridgeDoc.toObject(Fridge::class.java) ?: throw Exception("Fridge not found.")

        if (fridge.members.contains(userUid)) throw Exception("User is already a member.")
        if (fridge.pendingInvites.contains(userUid)) throw Exception("Invitation already sent.")

        firestore.collection("fridges").document(fridgeId)
            .update("pendingInvites", FieldValue.arrayUnion(userUid))
            .await()
    }

    suspend fun acceptInvite(fridgeId: String) {
        val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in.")

        val fridgeRef = firestore.collection("fridges").document(fridgeId)

        firestore.runTransaction { transaction ->
            transaction.update(fridgeRef, "members", FieldValue.arrayUnion(currentUserId))
            transaction.update(fridgeRef, "pendingInvites", FieldValue.arrayRemove(currentUserId))
        }.await()
    }

    suspend fun declineInvite(fridgeId: String) {
        val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in.")
        firestore.collection("fridges").document(fridgeId)
            .update("pendingInvites", FieldValue.arrayRemove(currentUserId)).await()
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

    fun getItemsForFridge(fridgeId: String): Flow<List<Item>> =
        callbackFlow {
            // Use SnapshotListener with local cache support
            val listener =
                firestore.collection("fridges").document(fridgeId).collection("items")
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            close(e)
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
            
            Log.d("FridgeRepo", "Fetched ${result.size} user profiles (${result.size - missingIds.size} from cache, ${missingIds.size} from network)")
            result
        } catch (e: Exception) {
            Log.e("FridgeRepo", "Error fetching user profiles by IDs: ${e.message}", e)
            emptyMap()
        }
    }
}
