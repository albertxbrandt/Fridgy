package fyi.goodbye.fridgy.repositories


import fyi.goodbye.fridgy.constants.FirestoreCollections
import fyi.goodbye.fridgy.constants.FirestoreFields
import timber.log.Timber
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.models.NotificationType
import fyi.goodbye.fridgy.models.ShoppingListItem
import fyi.goodbye.fridgy.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * Repository for managing household shopping list operations.
 *
 * Handles shopping list functionality at the household level:
 * - Adding, updating, and removing shopping list items
 * - Tracking user pickup status (obtained quantities and target fridges)
 * - Completing shopping sessions (moving items to fridges)
 * - Real-time presence tracking for active shoppers
 * - Notifications to recent viewers when items are added
 * - Periodic cleanup of stale presence documents
 *
 * ## Shopping List Model
 * Each household has a shopping list where members can add items they need.
 * Items track:
 * - Who added them
 * - Total quantity needed
 * - Per-user obtained quantities
 * - Per-user target fridges
 *
 * ## Presence Tracking
 * Tracks which users are actively viewing the shopping list:
 * - Updates timestamp when user views the list
 * - Active presence: viewed within last 30 seconds
 * - Recent viewer: viewed within last 30 minutes (for notifications)
 * - Automatic cleanup of stale presence (>24 hours old)
 *
 * ## Notifications
 * When a user adds an item, notifications are sent to users who viewed
 * the list in the last 30 minutes (excluding the user who added the item).
 *
 * @param firestore The Firestore instance for database operations
 * @param auth The Auth instance for user identification
 * @param notificationRepository The NotificationRepository for sending notifications
 * @param householdRepository The HouseholdRepository for user profile lookups
 */
class ShoppingListRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val notificationRepository: NotificationRepository,
    private val householdRepository: HouseholdRepository
) {
    companion object {
        
        private const val PRESENCE_TIMEOUT_MS = 30_000L // 30 seconds for active presence
        private const val RECENT_VIEWER_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes for notifications
    }

    /**
     * Returns a Flow of shopping list items for a household.
     * Closes gracefully on permission errors.
     *
     * @param householdId The household ID
     * @return Flow of shopping list items with real-time updates
     */
    fun getShoppingListItems(householdId: String): Flow<List<ShoppingListItem>> =
        callbackFlow {
            val colRef =
                firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId)
                    .collection(FirestoreCollections.SHOPPING_LIST)

            val listener =
                colRef.addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Timber.e("Error loading shopping list: ${e.message}")
                        channel.close()
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
     * Adds an item to the household's shopping list and notifies recent viewers.
     *
     * Sends notifications to users who viewed the shopping list in the last 30 minutes,
     * excluding the user who added the item.
     *
     * @param householdId The household ID
     * @param upc The product UPC code
     * @param quantity The quantity needed
     * @param store Optional store name
     * @param customName Optional custom name for the item
     * @throws IllegalStateException if user is not logged in
     */
    suspend fun addShoppingListItem(
        householdId: String,
        upc: String,
        quantity: Int = 1,
        store: String = "",
        customName: String = ""
    ) {
        val currentUser =
            auth.currentUser?.uid
                ?: throw IllegalStateException("User not logged in.")

        val item =
            ShoppingListItem(
                upc = upc,
                addedBy = currentUser,
                quantity = quantity,
                store = store,
                customName = customName
            )

        // Add item to shopping list (addedAt and lastUpdatedAt set via @ServerTimestamp)
        firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId)
            .collection(FirestoreCollections.SHOPPING_LIST).document(upc)
            .set(item)
            .await()

        // Get display name for notification (product name if customName is empty)
        val displayName =
            if (customName.isNotEmpty()) {
                customName
            } else {
                // Fetch product name from products collection
                try {
                    val product = firestore.collection(FirestoreCollections.PRODUCTS).document(upc).get().await()
                    product.getString(FirestoreFields.NAME) ?: upc
                } catch (e: Exception) {
                    Timber.w(e, "Could not fetch product name for UPC: $upc")
                    upc
                }
            }

        // Send notifications to recent shoppers
        notifyRecentShoppers(householdId, displayName)
    }

    /**
     * Removes an item from the shopping list.
     *
     * @param householdId The household ID
     * @param upc The product UPC code
     */
    suspend fun removeShoppingListItem(
        householdId: String,
        upc: String
    ) {
        firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId)
            .collection(FirestoreCollections.SHOPPING_LIST).document(upc)
            .delete()
            .await()
    }

    /**
     * Updates the current user's obtained quantity and target fridge for an item.
     *
     * Uses a Firestore transaction to ensure atomic updates to the shopping list item.
     *
     * @param householdId The household ID
     * @param upc The product UPC code
     * @param obtainedQuantity The quantity obtained by current user
     * @param totalQuantity The total quantity needed
     * @param targetFridgeId The fridge where items will be placed
     * @throws IllegalStateException if user is not logged in
     */
    suspend fun updateShoppingListItemPickup(
        householdId: String,
        upc: String,
        obtainedQuantity: Int,
        totalQuantity: Int,
        targetFridgeId: String
    ) {
        val currentUserId =
            auth.currentUser?.uid
                ?: throw IllegalStateException("User not logged in.")

        val itemRef =
            firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId)
                .collection(FirestoreCollections.SHOPPING_LIST).document(upc)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(itemRef)
            val currentObtainedBy = snapshot.get(FirestoreFields.OBTAINED_BY) as? Map<String, Long> ?: emptyMap()
            val currentTargetFridgeId = snapshot.get(FirestoreFields.TARGET_FRIDGE_ID) as? Map<String, String> ?: emptyMap()

            // Update obtained quantity map
            val updatedObtainedBy = currentObtainedBy.toMutableMap()
            if (obtainedQuantity > 0) {
                updatedObtainedBy[currentUserId] = obtainedQuantity.toLong()
            } else {
                updatedObtainedBy.remove(currentUserId)
            }

            // Update target fridge map
            val updatedTargetFridgeId = currentTargetFridgeId.toMutableMap()
            if (obtainedQuantity > 0 && targetFridgeId.isNotEmpty()) {
                updatedTargetFridgeId[currentUserId] = targetFridgeId
            } else {
                updatedTargetFridgeId.remove(currentUserId)
            }

            val newTotal = updatedObtainedBy.values.sum().toInt()
            val checked = newTotal >= totalQuantity

            transaction.update(
                itemRef,
                mapOf(
                    FirestoreFields.OBTAINED_BY to updatedObtainedBy,
                    FirestoreFields.TARGET_FRIDGE_ID to updatedTargetFridgeId,
                    FirestoreFields.OBTAINED_QUANTITY to newTotal,
                    FirestoreFields.CHECKED to checked,
                    FirestoreFields.LAST_UPDATED_BY to currentUserId,
                    FirestoreFields.LAST_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
        }.await()
    }

    /**
     * Completes the current user's shopping session by moving their obtained items to designated fridges.
     *
     * This operation performs an atomic batch write that:
     * 1. Adds user's obtained quantities to their selected fridges (creates new items or increments existing)
     * 2. Removes user's contribution from shopping list items
     * 3. Deletes shopping list items that no other users need
     *
     * **Important**: This function performs sequential reads for each item to check existence before writing.
     * It works reliably with large shopping lists (tested with 22+ items) because the reads happen
     * outside the batch operation and don't count against Firestore's 10-read batch limit.
     *
     * @param householdId The ID of the household whose shopping session is being completed
     * @throws IllegalStateException if user is not logged in
     * @throws Exception if Firestore operations fail (e.g., network issues, permission denied)
     */
    suspend fun completeShoppingSession(householdId: String) {
        val currentUserId =
            auth.currentUser?.uid
                ?: throw IllegalStateException("User not logged in.")

        val shoppingListRef =
            firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId)
                .collection(FirestoreCollections.SHOPPING_LIST)

        try {
            val snapshot = shoppingListRef.get().await()
            val items =
                snapshot.documents.mapNotNull {
                    it.toObject(ShoppingListItem::class.java)
                }

            val batch = firestore.batch()

            items.forEach { item ->
                val userQuantity = item.obtainedBy[currentUserId]?.toInt() ?: 0
                val targetFridgeId = item.targetFridgeId[currentUserId]

                if (userQuantity > 0 && targetFridgeId != null) {
                    // Create individual item instances for each unit obtained
                    // Items are now stored as individual instances, not aggregated by UPC
                    val itemsCollection =
                        firestore.collection(FirestoreCollections.FRIDGES)
                            .document(targetFridgeId)
                            .collection(FirestoreCollections.ITEMS)

                    repeat(userQuantity) {
                        // Auto-generate ID
                        val newItemRef = itemsCollection.document()
                        // Use HashMap with FieldValue.serverTimestamp() to match security rules
                        val newItemData = hashMapOf<String, Any?>(
                            "upc" to item.upc,
                            "expirationDate" to null,
                            "addedBy" to currentUserId,
                            "lastUpdatedBy" to currentUserId,
                            "addedAt" to FieldValue.serverTimestamp(),
                            "lastUpdatedAt" to FieldValue.serverTimestamp()
                        )
                        batch.set(newItemRef, newItemData)
                    }

                    Timber.d("Adding $userQuantity instance(s) of ${item.upc} to fridge $targetFridgeId")

                    // Update shopping list item
                    val shoppingItemRef = shoppingListRef.document(item.upc)
                    val remainingObtainedBy = item.obtainedBy.toMutableMap()
                    val remainingTargetFridgeId = item.targetFridgeId.toMutableMap()
                    remainingObtainedBy.remove(currentUserId)
                    remainingTargetFridgeId.remove(currentUserId)

                    val remainingQuantityNeeded = item.quantity - userQuantity
                    val newTotalObtained = remainingObtainedBy.values.sum()

                    if (remainingQuantityNeeded <= 0) {
                        batch.delete(shoppingItemRef)
                    } else {
                        batch.update(
                            shoppingItemRef,
                            mapOf(
                                FirestoreFields.QUANTITY to remainingQuantityNeeded,
                                FirestoreFields.OBTAINED_BY to remainingObtainedBy,
                                FirestoreFields.TARGET_FRIDGE_ID to remainingTargetFridgeId,
                                FirestoreFields.OBTAINED_QUANTITY to newTotalObtained,
                                FirestoreFields.CHECKED to false,
                                FirestoreFields.LAST_UPDATED_BY to currentUserId,
                                FirestoreFields.LAST_UPDATED_AT to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }

            batch.commit().await()
            Timber.d("Shopping session completed successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error completing shopping session")
            throw e
        }
    }

    /**
     * Sends notifications to users who have viewed the shopping list in the last 30 minutes.
     * Excludes the user who added the item.
     *
     * @param householdId The household ID
     * @param itemName The name of the item that was added
     */
    private suspend fun notifyRecentShoppers(
        householdId: String,
        itemName: String
    ) {
        try {
            val currentUserId = auth.currentUser?.uid ?: return

            Timber.d("=== Shopping List Notification Debug ===")
            Timber.d("Checking for recent viewers in household: $householdId")
            Timber.d("Item added: $itemName by user: $currentUserId")

            // Get recent viewers (last 30 minutes)
            val recentViewers = getRecentShoppingListViewers(householdId)

            Timber.d("Found ${recentViewers.size} total recent viewers")
            recentViewers.forEach { viewer ->
                val minutesAgo = (System.currentTimeMillis() - viewer.lastSeenTimestamp) / 60000
                Timber.d("  - ${viewer.username} (${viewer.userId}) - last seen $minutesAgo minutes ago")
            }

            val viewersToNotify = recentViewers.filter { it.userId != currentUserId }

            if (viewersToNotify.isEmpty()) {
                Timber.d("No users to notify (excluding current user)")
                return
            }

            Timber.d("Sending notifications to ${viewersToNotify.size} users")

            // Send in-app notifications to each recent viewer
            viewersToNotify.forEach { viewer ->
                Timber.d("Sending notification to: ${viewer.username}")
                notificationRepository.sendInAppNotification(
                    userId = viewer.userId,
                    title = "New item added to shopping list",
                    body = "$itemName was just added",
                    type = NotificationType.ITEM_ADDED,
                    relatedFridgeId = null,
                    relatedItemId = itemName
                )
            }

            Timber.d("Successfully notified ${viewersToNotify.size} recent shoppers")
        } catch (e: Exception) {
            Timber.e(e, "Error notifying recent shoppers")
            // Don't throw - notification failure shouldn't block adding items
        }
    }

    /**
     * Gets users who have viewed the shopping list in the last 30 minutes.
     * Returns a list of ActiveViewer objects with user info and last seen timestamp.
     *
     * @param householdId The household ID
     * @return List of active viewers within the last 30 minutes
     */
    private suspend fun getRecentShoppingListViewers(householdId: String): List<ActiveViewer> {
        return try {
            val currentTime = System.currentTimeMillis()
            val thirtyMinutesAgo = currentTime - RECENT_VIEWER_TIMEOUT_MS

            Timber.d("Querying presence documents for household: $householdId")
            Timber.d("Current time: $currentTime, cutoff time: $thirtyMinutesAgo")
            Timber.d("Looking for presence within last ${RECENT_VIEWER_TIMEOUT_MS / 60000} minutes")

            val presenceSnapshot =
                firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId)
                    .collection(FirestoreCollections.SHOPPING_LIST_PRESENCE)
                    .get()
                    .await()

            Timber.d("Found ${presenceSnapshot.documents.size} presence documents total")

            val recentUserData =
                presenceSnapshot.documents.mapNotNull { doc ->
                    val lastSeen = doc.getTimestamp(FirestoreFields.LAST_SEEN)?.toDate()?.time ?: 0
                    val userId = doc.getString(FirestoreFields.USER_ID)

                    val minutesAgo = if (lastSeen > 0) (currentTime - lastSeen) / 60000 else -1
                    Timber.d("Presence doc: userId=$userId, lastSeen=$lastSeen (${minutesAgo}min ago)")

                    // Check if user viewed within last 30 minutes
                    if (userId != null && lastSeen >= thirtyMinutesAgo) {
                        Timber.d("  -> INCLUDED (within 30 min)")
                        userId to lastSeen
                    } else {
                        if (userId != null) {
                            Timber.d("  -> EXCLUDED (too old or invalid)")
                        }
                        null
                    }
                }

            if (recentUserData.isEmpty()) {
                Timber.d("No recent viewers found after filtering")
                return emptyList()
            }

            Timber.d("Fetching user profiles for ${recentUserData.size} recent viewers")

            // Batch fetch user profiles
            val userIds = recentUserData.map { it.first }
            val profiles = getUsersByIds(userIds)

            recentUserData.mapNotNull { (userId, lastSeen) ->
                val username = profiles[userId]?.username ?: return@mapNotNull null
                ActiveViewer(userId, username, lastSeen)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching recent shopping list viewers")
            emptyList()
        }
    }

    /**
     * Sets the current user as actively viewing the shopping list.
     * Also performs periodic cleanup of stale presence documents (older than 24 hours).
     *
     * @param householdId The household ID
     */
    suspend fun setShoppingListPresence(householdId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val presenceRef =
            firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId)
                .collection(FirestoreCollections.SHOPPING_LIST_PRESENCE).document(currentUserId)

        presenceRef.set(
            mapOf(
                FirestoreFields.USER_ID to currentUserId,
                FirestoreFields.LAST_SEEN to FieldValue.serverTimestamp()
            )
        ).await()

        // Periodically clean up old presence documents (every ~10 calls)
        // This prevents database bloat while keeping recent activity for notifications
        if (Random.nextInt(10) == 0) {
            cleanupStalePresence(householdId, excludeUserId = currentUserId)
        }
    }

    /**
     * Removes presence documents older than 24 hours to prevent database bloat.
     * Called periodically by setShoppingListPresence().
     *
     * @param householdId The household ID
     * @param excludeUserId User ID to exclude from cleanup (typically the current user)
     */
    private suspend fun cleanupStalePresence(
        householdId: String,
        excludeUserId: String? = null
    ) {
        try {
            val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)

            val stalePresence =
                firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId)
                    .collection(FirestoreCollections.SHOPPING_LIST_PRESENCE)
                    .get()
                    .await()

            val batch = firestore.batch()
            var deleteCount = 0

            stalePresence.documents.forEach { doc ->
                val userId = doc.getString(FirestoreFields.USER_ID)
                val lastSeen = doc.getTimestamp(FirestoreFields.LAST_SEEN)?.toDate()?.time ?: 0

                // Skip if:
                // 1. This is the excluded user (current user)
                // 2. lastSeen is 0 (document just created, timestamp not set yet)
                // 3. lastSeen is within last 24 hours (not stale)
                if (userId == excludeUserId) {
                    // Don't delete current user's presence
                    return@forEach
                }

                if (lastSeen == 0L) {
                    // Document just created, serverTimestamp not populated yet
                    return@forEach
                }

                if (lastSeen < oneDayAgo) {
                    batch.delete(doc.reference)
                    deleteCount++
                    Timber.d("Marking stale presence for deletion: userId=$userId, lastSeen=$lastSeen")
                }
            }

            if (deleteCount > 0) {
                batch.commit().await()
                Timber.d("Cleaned up $deleteCount stale presence documents")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up stale presence")
            // Don't throw - cleanup failure shouldn't affect presence updates
        }
    }

    /**
     * Removes the current user's presence from the shopping list.
     * Note: This is kept for explicit removal scenarios, but typically
     * we don't remove presence to preserve lastSeen for notifications.
     *
     * @param householdId The household ID
     */
    suspend fun removeShoppingListPresence(householdId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId)
            .collection(FirestoreCollections.SHOPPING_LIST_PRESENCE).document(currentUserId)
            .delete()
            .await()
    }

    /**
     * Data class representing an active viewer of the shopping list.
     *
     * @property userId The user's Firebase Auth UID
     * @property username The user's display username
     * @property lastSeenTimestamp The timestamp (milliseconds) when user last viewed the list
     */
    data class ActiveViewer(
        val userId: String,
        val username: String,
        val lastSeenTimestamp: Long
    )

    /**
     * Returns a Flow of active viewers for the shopping list.
     * Closes gracefully on permission errors.
     *
     * Thread-safe implementation that batch fetches user profiles using coroutines
     * instead of async callbacks to avoid race conditions on shared mutable state.
     *
     * @param householdId The household ID
     * @return Flow of active viewers (viewed within last 30 seconds)
     */
    fun getShoppingListPresence(householdId: String): Flow<List<ActiveViewer>> =
        callbackFlow {
            val presenceRef =
                firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId)
                    .collection(FirestoreCollections.SHOPPING_LIST_PRESENCE)

            val listener =
                presenceRef.addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Timber.e("Error loading shopping list presence: ${e.message}")
                        channel.close()
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
     * Fetches user profiles by their IDs in a batch operation.
     *
     * @param userIds List of user IDs to fetch
     * @return Map of user ID to UserProfile
     */
    private suspend fun getUsersByIds(userIds: List<String>): Map<String, UserProfile> {
        return householdRepository.getUsersByIds(userIds)
    }
}


