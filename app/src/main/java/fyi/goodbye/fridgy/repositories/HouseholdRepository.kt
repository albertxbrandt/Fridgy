package fyi.goodbye.fridgy.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.models.DisplayHousehold
import fyi.goodbye.fridgy.models.Household
import fyi.goodbye.fridgy.models.HouseholdRole
import fyi.goodbye.fridgy.models.UserProfile
import fyi.goodbye.fridgy.models.canDeleteHousehold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
     * This is more efficient than using getHouseholdsForCurrentUser() + getDisplayHouseholdById()
     * because it uses a single snapshot listener for all households and their fridge counts.
     */
    fun getDisplayHouseholdsForCurrentUser(): Flow<List<DisplayHousehold>> =
        callbackFlow {
            val currentUserId =
                auth.currentUser?.uid ?: run {
                    trySend(emptyList()).isSuccess
                    close()
                    return@callbackFlow
                }

            // Cache for user profiles to avoid repeated queries
            val userProfileCache = mutableMapOf<String, UserProfile>()

            // Map to track fridge count listeners for each household
            val fridgeCountListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
            val householdData = mutableMapOf<String, Pair<Household, Int>>() // household to (household, fridgeCount)

            fun emitDisplayHouseholds() {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Collect all unique user IDs
                        val allUserIds =
                            householdData.values.flatMap { (household, _) ->
                                household.members + household.createdBy
                            }.distinct()

                        // Fetch missing user profiles
                        val missingUserIds = allUserIds.filter { it !in userProfileCache.keys }
                        if (missingUserIds.isNotEmpty()) {
                            val newProfiles = getUsersByIds(missingUserIds)
                            userProfileCache.putAll(newProfiles)
                        }

                        // Build DisplayHousehold objects
                        val displayHouseholds =
                            householdData.values.map { (household, fridgeCount) ->
                                val memberUsers = household.members.mapNotNull { userProfileCache[it] }
                                val ownerName = userProfileCache[household.createdBy]?.username ?: "Unknown"

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

                        trySend(displayHouseholds).isSuccess
                    } catch (e: Exception) {
                        Log.e(TAG, "Error building display households: ${e.message}")
                    }
                }
            }

            // Listen to household changes
            val householdListener =
                firestore.collection("households")
                    .whereArrayContains("members", currentUserId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.e(TAG, "Error in households listener: ${e.message}")
                            trySend(emptyList()).isSuccess
                            return@addSnapshotListener
                        }

                        val currentHouseholdIds = snapshot?.documents?.map { it.id } ?: emptyList()
                        val households =
                            snapshot?.documents?.mapNotNull { doc ->
                                doc.toObject(Household::class.java)?.copy(id = doc.id)
                            } ?: emptyList()

                        // Remove listeners for households user is no longer part of
                        fridgeCountListeners.keys.filter { it !in currentHouseholdIds }.forEach { oldId ->
                            fridgeCountListeners[oldId]?.remove()
                            fridgeCountListeners.remove(oldId)
                            householdData.remove(oldId)
                        }

                        // Update household data and set up fridge count listeners
                        households.forEach { household ->
                            householdData[household.id] = household to (householdData[household.id]?.second ?: 0)

                            // Set up fridge count listener if not already listening
                            if (!fridgeCountListeners.containsKey(household.id)) {
                                val fridgeListener =
                                    firestore.collection("fridges")
                                        .whereEqualTo("householdId", household.id)
                                        .addSnapshotListener { fridgeSnapshot, fridgeError ->
                                            if (fridgeError != null) {
                                                // Handle permission errors gracefully (user might not have access yet)
                                                if (fridgeError.message?.contains("PERMISSION_DENIED") == true) {
                                                    Log.d(
                                                        TAG,
                                                        "Permission denied for fridge count in household ${household.id} - setting count to 0"
                                                    )
                                                    householdData[household.id] = household to 0
                                                } else {
                                                    Log.e(TAG, "Error in fridge count listener: ${fridgeError.message}")
                                                }
                                                return@addSnapshotListener
                                            }

                                            val fridgeCount = fridgeSnapshot?.size() ?: 0
                                            householdData[household.id] = household to fridgeCount

                                            // Emit updated display households
                                            emitDisplayHouseholds()
                                        }
                                fridgeCountListeners[household.id] = fridgeListener
                            }
                        }

                        // Emit initial data (fridge counts will be updated by their listeners)
                        emitDisplayHouseholds()
                    }

            awaitClose {
                householdListener.remove()
                fridgeCountListeners.values.forEach { it.remove() }
            }
        }
            .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions

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

        val batch = firestore.batch()

        // Delete all fridges in this household
        val fridges =
            firestore.collection("fridges")
                .whereEqualTo("householdId", householdId)
                .get()
                .await()

        fridges.documents.forEach { fridgeDoc ->
            // Delete fridge items subcollection
            val items = fridgeDoc.reference.collection("items").get().await()
            items.documents.forEach { batch.delete(it.reference) }
            batch.delete(fridgeDoc.reference)
        }

        // Delete shopping list subcollection
        val shoppingList =
            firestore.collection("households").document(householdId)
                .collection("shoppingList").get().await()
        shoppingList.documents.forEach { batch.delete(it.reference) }

        // Delete shopping list presence subcollection
        val presence =
            firestore.collection("households").document(householdId)
                .collection("shoppingListPresence").get().await()
        presence.documents.forEach { batch.delete(it.reference) }

        // Delete the household document
        batch.delete(firestore.collection("households").document(householdId))

        batch.commit().await()
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
