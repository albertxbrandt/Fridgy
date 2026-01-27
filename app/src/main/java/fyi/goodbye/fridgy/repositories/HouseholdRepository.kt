package fyi.goodbye.fridgy.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.models.DisplayHousehold
import fyi.goodbye.fridgy.models.Household
import fyi.goodbye.fridgy.models.HouseholdRole
import fyi.goodbye.fridgy.models.UserProfile
import fyi.goodbye.fridgy.models.canDeleteHousehold
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing household core operations.
 *
 * This repository handles household CRUD operations:
 * - Household CRUD operations (create, read, update, delete)
 *
 * Invite codes and member management are handled by MembershipRepository.
 * Shopping list operations are handled by ShoppingListRepository.
 *
 * ## Household Model
 * A household is a shared space containing one or more fridges. Users can be members
 * of multiple households. Each household has an owner (creator) and members.
 *
 * ## Shopping List Notifications
 * When a user adds an item to the shopping list, notifications are sent to:
 * - Users who viewed the list in the last 30 minutes
 * - Excludes the user who added the item
 *
 * ## Thread Safety
 * - Uses coroutines for async operations
 * - Presence tracking uses batch coroutine fetching to avoid race conditions
 * - Shopping list updates use Firestore transactions for atomicity
 *
 * @param firestore The Firestore instance for database operations.
 * @param auth The Auth instance for user identification.
 * @param notificationRepository The NotificationRepository for sending notifications.
 * @see FridgeRepository For fridge-level operations
 * @see MembershipRepository For invite codes and member management
 */
class HouseholdRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val notificationRepository: NotificationRepository
) {
    companion object {
        private const val TAG = "HouseholdRepository"
    }

    // ==================== Household CRUD ====================

    /**
     * Returns a Flow of households the current user is a member of.
     * Returns an empty list if user has no households or if there's a permission error.
     */
    fun getHouseholdsForCurrentUser(): Flow<List<Household>> =
        callbackFlow {
            val currentUserId =
                auth.currentUser?.uid ?: run {
                    Log.d(TAG, "No current user, sending empty list")
                    trySend(emptyList()).isSuccess
                    close()
                    return@callbackFlow
                }

            Log.d(TAG, "Starting households listener for user: $currentUserId")

            val listener =
                firestore.collection("households")
                    .whereArrayContains("members", currentUserId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.e(TAG, "Error fetching households: ${e.message}")
                            // Send empty list on error instead of closing with exception
                            trySend(emptyList()).isSuccess
                            return@addSnapshotListener
                        }
                        val households =
                            snapshot?.documents?.mapNotNull { doc ->
                                doc.toObject(Household::class.java)?.copy(id = doc.id)
                            } ?: emptyList()
                        Log.d(TAG, "Fetched ${households.size} households")
                        trySend(households).isSuccess
                    }
            awaitClose {
                Log.d(TAG, "Closing households listener")
                listener.remove()
            }
        }
            .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions

    /**
     * Gets a single household by ID.
     */
    suspend fun getHouseholdById(householdId: String): Household? {
        return try {
            val doc = firestore.collection("households").document(householdId).get().await()
            if (!doc.exists()) {
                Log.d(TAG, "Household $householdId does not exist")
                return null
            }
            doc.toObject(Household::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            // Silently handle permission errors - user likely no longer has access
            if (e.message?.contains("PERMISSION_DENIED") == true) {
                Log.d(TAG, "Permission denied for household $householdId - user likely removed")
            } else {
                Log.e(TAG, "Error fetching household $householdId: ${e.message}")
            }
            null
        }
    }

    /**
     * Gets a Flow of a single household by ID with real-time updates.
     */
    fun getHouseholdFlow(householdId: String): Flow<Household?> =
        callbackFlow {
            val listener =
                firestore.collection("households")
                    .document(householdId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.e(TAG, "Error listening to household: ${e.message}")
                            trySend(null).isSuccess
                            return@addSnapshotListener
                        }
                        val household = snapshot?.toObject(Household::class.java)?.copy(id = snapshot.id)
                        trySend(household).isSuccess
                    }
            awaitClose {
                listener.remove()
            }
        }
            .distinctUntilChanged()

    /**
     * Gets a household with resolved user profiles for display.
     */
    suspend fun getDisplayHouseholdById(householdId: String): DisplayHousehold? {
        val household = getHouseholdById(householdId) ?: return null

        // Fetch user profiles for all members
        val userProfiles = getUsersByIds(household.members + listOf(household.createdBy))
        val memberUsers = household.members.mapNotNull { userProfiles[it] }
        val ownerName = userProfiles[household.createdBy]?.username ?: "Unknown"

        // Count fridges in this household
        val fridgeCount =
            try {
                firestore.collection("fridges")
                    .whereEqualTo("householdId", householdId)
                    .get()
                    .await()
                    .size()
            } catch (e: Exception) {
                0
            }

        return DisplayHousehold(
            id = household.id,
            name = household.name,
            createdByUid = household.createdBy,
            ownerDisplayName = ownerName,
            memberUsers = memberUsers,
            memberRoles = household.memberRoles,
            fridgeCount = fridgeCount,
            createdAt = household.createdAt
        )
    }

    /**
     * Returns a Flow of DisplayHouseholds for the current user with real-time updates.
     * This includes live fridge counts that update when fridges are added/removed.
     *
     * PERFORMANCE FIX: Refactored to avoid nested listeners memory leak.
     * Previously created fridge count listeners inside household listener, causing potential leaks.
     * Now uses separate flows combined together for proper lifecycle management.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getDisplayHouseholdsForCurrentUser(): Flow<List<DisplayHousehold>> =
        getHouseholdsForCurrentUserFlow()
            .flatMapLatest { households: List<Household> ->
                if (households.isEmpty()) {
                    return@flatMapLatest flowOf(emptyList<DisplayHousehold>())
                }

                // Create separate flow for each household's fridge count
                val householdFlows: List<Flow<Pair<Household, Int>>> =
                    households.map { household: Household ->
                        getFridgeCountFlow(household.id).map { fridgeCount: Int ->
                            Pair(household, fridgeCount)
                        }
                    }

                // Combine all household+count flows
                combine(householdFlows) { householdPairs: Array<Pair<Household, Int>> ->
                    householdPairs.toList()
                }.flatMapLatest { householdPairs: List<Pair<Household, Int>> ->
                    flow {
                        // Collect all unique user IDs
                        val allUserIds: List<String> =
                            householdPairs.flatMap { (household: Household, _: Int) ->
                                household.members + household.createdBy
                            }.distinct()

                        // Batch fetch user profiles
                        val userProfileMap: Map<String, UserProfile> = getUsersByIds(allUserIds)

                        // Build DisplayHousehold objects
                        val displayHouseholds: List<DisplayHousehold> =
                            householdPairs.map { (household: Household, fridgeCount: Int) ->
                                val memberUsers: List<UserProfile> =
                                    household.members.mapNotNull { userId: String ->
                                        userProfileMap[userId]
                                    }
                                val ownerName: String = userProfileMap[household.createdBy]?.username ?: "Unknown"

                                DisplayHousehold(
                                    id = household.id,
                                    name = household.name,
                                    createdByUid = household.createdBy,
                                    ownerDisplayName = ownerName,
                                    memberUsers = memberUsers,
                                    memberRoles = household.memberRoles,
                                    fridgeCount = fridgeCount,
                                    createdAt = household.createdAt
                                )
                            }

                        emit(displayHouseholds)
                    }
                }
            }
            .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions
            .catch { e: Throwable ->
                Log.e(TAG, "Error in getDisplayHouseholdsForCurrentUser: ${e.message}")
                emit(emptyList())
            }

    /**
     * Returns a Flow of households for the current user with real-time updates.
     */
    private fun getHouseholdsForCurrentUserFlow(): Flow<List<Household>> =
        callbackFlow {
            val currentUserId =
                auth.currentUser?.uid ?: run {
                    trySend(emptyList()).isSuccess
                    close()
                    return@callbackFlow
                }

            val listener =
                firestore.collection("households")
                    .whereArrayContains("members", currentUserId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.e(TAG, "Error in households listener: ${e.message}")
                            trySend(emptyList()).isSuccess
                            return@addSnapshotListener
                        }

                        val households =
                            snapshot?.documents?.mapNotNull { doc ->
                                doc.toObject(Household::class.java)?.copy(id = doc.id)
                            } ?: emptyList()

                        trySend(households).isSuccess
                    }

            awaitClose { listener.remove() }
        }

    /**
     * Returns a Flow of fridge count for a specific household with real-time updates.
     */
    private fun getFridgeCountFlow(householdId: String): Flow<Int> =
        callbackFlow {
            val listener =
                firestore.collection("fridges")
                    .whereEqualTo("householdId", householdId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            // Handle permission errors gracefully (user might not have access yet)
                            if (e.message?.contains("PERMISSION_DENIED") == true) {
                                Log.d(
                                    TAG,
                                    "Permission denied for fridge count in household $householdId - setting count to 0"
                                )
                                trySend(0).isSuccess
                            } else {
                                Log.e(TAG, "Error in fridge count listener for household $householdId: ${e.message}")
                                trySend(0).isSuccess
                            }
                            return@addSnapshotListener
                        }

                        val fridgeCount = snapshot?.size() ?: 0
                        trySend(fridgeCount).isSuccess
                    }

            awaitClose { listener.remove() }
        }

    /**
     * Creates a new household with the current user as owner and sole member.
     *
     * @param name The name of the household.
     * @return The created Household object.
     */
    suspend fun createHousehold(name: String): Household {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")

        val docRef = firestore.collection("households").document()
        val household =
            Household(
                id = docRef.id,
                name = name,
                createdBy = currentUser.uid,
                members = listOf(currentUser.uid),
                memberRoles = mapOf(currentUser.uid to HouseholdRole.OWNER.name)
                // createdAt set via @ServerTimestamp
            )

        docRef.set(household).await()
        return household
    }

    /**
     * Updates the name of a household. Only the owner can do this.
     */
    suspend fun updateHouseholdName(
        householdId: String,
        newName: String
    ) {
        firestore.collection("households").document(householdId)
            .update("name", newName)
            .await()
    }

    /**
     * Deletes a household and all associated data (fridges, shopping list).
     * Only the owner can delete a household.
     * Note: Invite codes are deleted by MembershipRepository.
     *
     * PERFORMANCE FIX: Uses paginated deletion to handle large households safely.
     * Processes documents in batches of 200 to avoid memory issues and Firestore limits.
     */
    suspend fun deleteHousehold(householdId: String) {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")
        val household =
            getHouseholdById(householdId)
                ?: throw IllegalStateException("Household not found")

        // Check permission
        val userRole = household.getRoleForUser(currentUser.uid)
        if (!userRole.canDeleteHousehold()) {
            throw IllegalStateException("Only the owner can delete the household")
        }

        // Delete all fridges and their items (paginated)
        deleteFridgesForHousehold(householdId)

        // Delete shopping list subcollection (paginated)
        deleteCollectionInBatches(
            firestore.collection("households").document(householdId).collection("shoppingList")
        )

        // Delete shopping list presence subcollection (paginated)
        deleteCollectionInBatches(
            firestore.collection("households").document(householdId).collection("shoppingListPresence")
        )

        // Delete the household document itself
        firestore.collection("households").document(householdId).delete().await()

        Log.d(TAG, "Successfully deleted household: $householdId")
    }

    /**
     * Deletes all fridges for a household and their items subcollections.
     * Uses pagination to handle large numbers of fridges safely.
     */
    private suspend fun deleteFridgesForHousehold(householdId: String) {
        val batchSize = 100 // Conservative limit to avoid hitting Firestore's 500 operation limit
        var hasMore = true

        while (hasMore) {
            val fridgesSnapshot =
                firestore.collection("fridges")
                    .whereEqualTo("householdId", householdId)
                    .limit(batchSize.toLong())
                    .get()
                    .await()

            if (fridgesSnapshot.isEmpty) {
                hasMore = false
                break
            }

            // For each fridge, delete its items subcollection first, then the fridge itself
            for (fridgeDoc in fridgesSnapshot.documents) {
                // Delete items subcollection for this fridge
                deleteCollectionInBatches(fridgeDoc.reference.collection("items"))

                // Delete the fridge document
                fridgeDoc.reference.delete().await()
            }

            // If we got fewer documents than the batch size, we're done
            hasMore = fridgesSnapshot.size() >= batchSize
        }
    }

    /**
     * Deletes all documents in a collection using batched writes.
     * Processes documents in chunks to avoid memory issues and Firestore limits.
     *
     * @param collection The collection reference to delete
     */
    private suspend fun deleteCollectionInBatches(collection: com.google.firebase.firestore.CollectionReference) {
        val batchSize = 200 // Documents per batch
        var hasMore = true

        while (hasMore) {
            val snapshot = collection.limit(batchSize.toLong()).get().await()

            if (snapshot.isEmpty) {
                hasMore = false
                break
            }

            // Delete documents in batches of up to 500 operations (Firestore limit)
            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()

            // If we got fewer documents than the batch size, we're done
            hasMore = snapshot.size() >= batchSize
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Fetches multiple user profiles by their IDs.
     * Exposed for use by MembershipRepository and ShoppingListRepository.
     *
     * @param userIds List of user IDs to fetch profiles for
     * @return Map of user ID to UserProfile
     */
    suspend fun getUsersByIds(userIds: List<String>): Map<String, UserProfile> {
        if (userIds.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, UserProfile>()

        try {
            userIds.distinct().chunked(10).forEach { chunk ->
                val snapshot =
                    firestore.collection("userProfiles")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                        .get()
                        .await()

                snapshot.documents.forEach { doc ->
                    doc.toObject(UserProfile::class.java)?.let { profile ->
                        result[doc.id] = profile.copy(uid = doc.id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user profiles: ${e.message}")
        }

        return result
    }
}
