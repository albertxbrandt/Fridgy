package fyi.goodbye.fridgy.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import fyi.goodbye.fridgy.constants.FirestoreCollections
import fyi.goodbye.fridgy.constants.FirestoreFields
import fyi.goodbye.fridgy.models.display.DisplayHousehold
import fyi.goodbye.fridgy.models.entities.Household
import fyi.goodbye.fridgy.models.entities.HouseholdRole
import fyi.goodbye.fridgy.models.entities.UserProfile
import fyi.goodbye.fridgy.models.entities.canDeleteHousehold
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
import timber.log.Timber

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
 * @param userRepository The UserRepository for user profile operations.
 * @see FridgeRepository For fridge-level operations
 * @see MembershipRepository For invite codes and member management
 */
class HouseholdRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository
) {
    // In-memory cache for households to reduce Firestore reads
    private val householdCache = mutableMapOf<String, Household>()

    // ==================== Household CRUD ====================

    /**
     * Returns a Flow of households the current user is a member of.
     * Returns an empty list if user has no households or if there's a permission error.
     */
    fun getHouseholdsForCurrentUser(): Flow<List<Household>> =
        callbackFlow {
            val currentUserId =
                auth.currentUser?.uid ?: run {
                    Timber.d("No current user, sending empty list")
                    trySend(emptyList()).isSuccess
                    close()
                    return@callbackFlow
                }

            Timber.d("Starting households listener for user: $currentUserId")

            val listener =
                firestore.collection(FirestoreCollections.HOUSEHOLDS)
                    .whereArrayContains(FirestoreFields.MEMBERS, currentUserId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Timber.e("Error fetching households: ${e.message}")
                            // Send empty list on error instead of closing with exception
                            trySend(emptyList()).isSuccess
                            return@addSnapshotListener
                        }
                        val households =
                            snapshot?.documents?.mapNotNull { doc ->
                                doc.toObject(Household::class.java)?.copy(id = doc.id)
                            } ?: emptyList()
                        Timber.d("Fetched ${households.size} households")
                        trySend(households).isSuccess
                    }
            awaitClose {
                Timber.d("Closing households listener")
                listener.remove()
            }
        }
            .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions

    /**
     * Gets a single household by ID.
     * Uses in-memory cache -> Firestore cache -> network for optimal performance.
     */
    suspend fun getHouseholdById(householdId: String): Household? {
        // Check in-memory cache first
        householdCache[householdId]?.let { return it }

        return try {
            // Try Firestore cache first
            val cacheDoc =
                try {
                    firestore.collection(FirestoreCollections.HOUSEHOLDS)
                        .document(householdId)
                        .get(Source.CACHE)
                        .await()
                } catch (e: Exception) {
                    null
                }

            val doc =
                if (cacheDoc?.exists() == true) {
                    cacheDoc
                } else {
                    // Fallback to network
                    firestore.collection(FirestoreCollections.HOUSEHOLDS)
                        .document(householdId)
                        .get(Source.DEFAULT)
                        .await()
                }

            if (!doc.exists()) {
                Timber.d("Household $householdId does not exist")
                return null
            }

            val household = doc.toObject(Household::class.java)?.copy(id = doc.id)
            // Cache the result
            household?.let { householdCache[householdId] = it }
            household
        } catch (e: Exception) {
            // Silently handle permission errors - user likely no longer has access
            if (e.message?.contains("PERMISSION_DENIED") == true) {
                Timber.d("Permission denied for household $householdId - user likely removed")
                // Remove from cache if permission denied
                householdCache.remove(householdId)
            } else {
                Timber.e("Error fetching household $householdId: ${e.message}")
            }
            null
        }
    }

    /**
     * Gets a Flow of a single household by ID with real-time updates.
     * Updates the in-memory cache as new data arrives.
     */
    fun getHouseholdFlow(householdId: String): Flow<Household?> =
        callbackFlow {
            val listener =
                firestore.collection(FirestoreCollections.HOUSEHOLDS)
                    .document(householdId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Timber.e("Error listening to household: ${e.message}")
                            trySend(null).isSuccess
                            return@addSnapshotListener
                        }
                        val household = snapshot?.toObject(Household::class.java)?.copy(id = snapshot.id)
                        // Update cache with fresh data
                        household?.let { householdCache[householdId] = it }
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
                firestore.collection(FirestoreCollections.FRIDGES)
                    .whereEqualTo(FirestoreFields.HOUSEHOLD_ID, householdId)
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
                Timber.e("Error in getDisplayHouseholdsForCurrentUser: ${e.message}")
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
                firestore.collection(FirestoreCollections.HOUSEHOLDS)
                    .whereArrayContains(FirestoreFields.MEMBERS, currentUserId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Timber.e("Error in households listener: ${e.message}")
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
                firestore.collection(FirestoreCollections.FRIDGES)
                    .whereEqualTo(FirestoreFields.HOUSEHOLD_ID, householdId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            // Handle permission errors gracefully (user might not have access yet)
                            if (e.message?.contains("PERMISSION_DENIED") == true) {
                                Timber.d(
                                    "Permission denied for fridge count in household $householdId - setting count to 0"
                                )
                                trySend(0).isSuccess
                            } else {
                                Timber.e("Error in fridge count listener for household $householdId: ${e.message}")
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

        val docRef = firestore.collection(FirestoreCollections.HOUSEHOLDS).document()
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
        firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId)
            .update(FirestoreFields.NAME, newName)
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
            firestore.collection(
                FirestoreCollections.HOUSEHOLDS
            ).document(householdId).collection(FirestoreCollections.SHOPPING_LIST)
        )

        // Delete shopping list presence subcollection (paginated)
        deleteCollectionInBatches(
            firestore.collection(
                FirestoreCollections.HOUSEHOLDS
            ).document(householdId).collection(FirestoreCollections.SHOPPING_LIST_PRESENCE)
        )

        // Delete the household document itself
        firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId).delete().await()

        // Remove from cache
        householdCache.remove(householdId)

        Timber.d("Successfully deleted household: $householdId")
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
                firestore.collection(FirestoreCollections.FRIDGES)
                    .whereEqualTo(FirestoreFields.HOUSEHOLD_ID, householdId)
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
                deleteCollectionInBatches(fridgeDoc.reference.collection(FirestoreCollections.ITEMS))

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
    private suspend fun deleteCollectionInBatches(collection: CollectionReference) {
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
     * Delegates to UserRepository for centralized caching and batch fetching.
     * Exposed for use by MembershipRepository and ShoppingListRepository.
     *
     * @param userIds List of user IDs to fetch profiles for
     * @return Map of user ID to UserProfile
     */
    suspend fun getUsersByIds(userIds: List<String>): Map<String, UserProfile> = userRepository.getUsersByIds(userIds)
}
