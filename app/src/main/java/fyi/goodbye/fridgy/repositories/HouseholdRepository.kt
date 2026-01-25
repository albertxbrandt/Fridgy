package fyi.goodbye.fridgy.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.models.DisplayHousehold
import fyi.goodbye.fridgy.models.Household
import fyi.goodbye.fridgy.models.HouseholdRole
import fyi.goodbye.fridgy.models.InviteCode
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.models.ShoppingListItem
import fyi.goodbye.fridgy.models.UserProfile
import fyi.goodbye.fridgy.models.canDeleteHousehold
import fyi.goodbye.fridgy.models.canManageInviteCodes
import fyi.goodbye.fridgy.models.canModifyUser
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
 * Repository for managing households, member management, and household-level operations.
 *
 * This is the primary repository for multi-user collaboration features in Fridgy:
 * - Household CRUD operations (create, read, update, delete)
 * - Member management via invite codes (join, leave, remove)
 * - Household-level shopping list operations
 * - Real-time presence tracking for collaborative shopping
 * - Shopping list notifications for active shoppers
 * - Legacy fridge migration support
 *
 * ## Household Model
 * A household is a shared space containing one or more fridges. Users can be members
 * of multiple households. Each household has an owner (creator) and members.
 *
 * ## Invite Code System
 * Users join households by redeeming 6-character alphanumeric codes. Codes can have
 * optional expiration dates and are single-use by default.
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
 * @see InviteCode For invite code data model
 */
class HouseholdRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val notificationRepository: NotificationRepository
) {
    companion object {
        private const val TAG = "HouseholdRepository"
        private const val INVITE_CODE_LENGTH = 6
        private const val INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Exclude confusing chars (I, O, 0, 1)
        private const val PRESENCE_TIMEOUT_MS = 30_000L // 30 seconds for active presence
        private const val RECENT_VIEWER_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes for notifications
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
                memberRoles = mapOf(currentUser.uid to HouseholdRole.OWNER.name),
                createdAt = System.currentTimeMillis()
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
     * Deletes a household and all associated data (fridges, shopping list, invite codes).
     * Only the owner can delete a household.
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

        // Delete invite codes for this household
        val inviteCodes =
            firestore.collection("inviteCodes")
                .whereEqualTo("householdId", householdId)
                .get()
                .await()
        inviteCodes.documents.forEach { batch.delete(it.reference) }

        // Delete the household document
        batch.delete(firestore.collection("households").document(householdId))

        batch.commit().await()
    }

    // ==================== Member Management ====================

    /**
     * Updates the role of a member in the household.
     * Only the owner can change roles.
     *
     * @param householdId The household ID.
     * @param userId The user whose role to update.
     * @param newRole The new role to assign.
     */
    suspend fun updateMemberRole(
        householdId: String,
        userId: String,
        newRole: HouseholdRole
    ) {
        val household =
            getHouseholdById(householdId)
                ?: throw IllegalStateException("Household not found")

        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        // Only owner can change roles
        if (household.createdBy != currentUserId) {
            throw IllegalStateException("Only the owner can change user roles")
        }

        // Cannot change the owner's role
        if (userId == household.createdBy) {
            throw IllegalStateException("Cannot change the owner's role")
        }

        val updatedRoles = household.memberRoles.toMutableMap()
        updatedRoles[userId] = newRole.name

        firestore.collection("households").document(householdId)
            .update("memberRoles", updatedRoles)
            .await()
    }

    /**
     * Removes a member from a household. Only the owner can remove members.
     * The owner cannot be removed.
     */
    suspend fun removeMember(
        householdId: String,
        userId: String
    ) {
        val household =
            getHouseholdById(householdId)
                ?: throw IllegalStateException("Household not found")

        if (userId == household.createdBy) {
            throw IllegalStateException("Cannot remove the owner from the household")
        }

        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val currentUserRole = household.getRoleForUser(currentUserId)
        val targetUserRole = household.getRoleForUser(userId)

        // Check if current user has permission to remove the target user
        if (!currentUserRole.canModifyUser(targetUserRole)) {
            throw IllegalStateException("You don't have permission to remove this user")
        }

        val batch = firestore.batch()
        val householdRef = firestore.collection("households").document(householdId)

        // Remove from members list
        batch.update(householdRef, "members", FieldValue.arrayRemove(userId))

        // Remove from roles map
        val updatedRoles = household.memberRoles.toMutableMap()
        updatedRoles.remove(userId)
        batch.update(householdRef, "memberRoles", updatedRoles)

        batch.commit().await()
    }

    /**
     * Allows the current user to leave a household.
     * The owner cannot leave - they must delete the household or transfer ownership.
     */
    suspend fun leaveHousehold(householdId: String) {
        val currentUserId =
            auth.currentUser?.uid
                ?: throw IllegalStateException("User not logged in")

        // Try to get household, but if it fails (permission denied, network issue, etc.),
        // still attempt to remove user from members list
        try {
            val household = getHouseholdById(householdId)

            if (household != null) {
                if (currentUserId == household.createdBy) {
                    throw IllegalStateException("Owner cannot leave the household. Delete it instead.")
                }
            } else {
                // Household might not be fetchable due to permission issues
                // Log and proceed with removal attempt
                Log.w(TAG, "Could not fetch household $householdId before leaving, proceeding with removal")
            }
        } catch (e: Exception) {
            // Log error but continue with removal - permission issues shouldn't block leaving
            Log.w(TAG, "Error fetching household $householdId before leaving: ${e.message}, proceeding with removal")
        }

        // Attempt to remove user from household regardless of fetch result
        try {
            firestore.collection("households").document(householdId)
                .update("members", FieldValue.arrayRemove(currentUserId))
                .await()
            Log.d(TAG, "Successfully removed user from household $householdId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove user from household: ${e.message}")
            throw e
        }
    }

    // ==================== Invite Code System ====================

    /**
     * Generates a random 6-character alphanumeric invite code.
     */
    private fun generateInviteCode(): String {
        return (1..INVITE_CODE_LENGTH)
            .map { INVITE_CODE_CHARS[Random.nextInt(INVITE_CODE_CHARS.length)] }
            .joinToString("")
    }

    /**
     * Creates a new invite code for a household.
     *
     * @param householdId The household to create the code for.
     * @param expiresAt Optional expiration timestamp (null = never expires).
     * @return The created InviteCode object.
     */
    suspend fun createInviteCode(
        householdId: String,
        expiresAt: Long? = null
    ): InviteCode {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")
        val household =
            getHouseholdById(householdId)
                ?: throw IllegalStateException("Household not found")

        // Check permission
        val userRole = household.getRoleForUser(currentUser.uid)
        if (!userRole.canManageInviteCodes()) {
            throw IllegalStateException("You don't have permission to create invite codes")
        }

        // Generate unique code with collision check
        var code: String
        var attempts = 0
        do {
            code = generateInviteCode()
            val existing = firestore.collection("inviteCodes").document(code).get().await()
            attempts++
            if (attempts > 10) throw IllegalStateException("Failed to generate unique code")
        } while (existing.exists())

        val inviteCode =
            InviteCode(
                code = code,
                householdId = householdId,
                householdName = household.name,
                createdBy = currentUser.uid,
                createdAt = System.currentTimeMillis(),
                expiresAt = expiresAt,
                isActive = true
            )

        Log.d("HouseholdRepository", "Creating invite code: $code for household: $householdId, isActive: true")
        firestore.collection("inviteCodes").document(code).set(inviteCode).await()
        Log.d("HouseholdRepository", "Invite code created successfully")
        return inviteCode
    }

    /**
     * Gets all active invite codes for a household.
     */
    suspend fun getInviteCodesForHousehold(householdId: String): List<InviteCode> {
        return try {
            val snapshot =
                firestore.collection("inviteCodes")
                    .whereEqualTo("householdId", householdId)
                    .whereEqualTo("active", true)
                    .get()
                    .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toObject(InviteCode::class.java)?.copy(code = doc.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching invite codes: ${e.message}")
            emptyList()
        }
    }

    /**
     * Returns a Flow of invite codes for real-time updates.
     * Emits empty list on permission errors (e.g., when user leaves household).
     */
    fun getInviteCodesFlow(householdId: String): Flow<List<InviteCode>> =
        callbackFlow {
            Log.d("HouseholdRepository", "Starting invite codes listener for household: $householdId")
            val listener =
                firestore.collection("inviteCodes")
                    .whereEqualTo("householdId", householdId)
                    .whereEqualTo("active", true)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.e("HouseholdRepository", "Error loading invite codes: ${e.message}", e)
                            // Don't crash - just close the flow gracefully
                            // This happens when user loses permission (e.g., leaves household)
                            channel.close()
                            return@addSnapshotListener
                        }
                        val codes =
                            snapshot?.documents?.mapNotNull { doc ->
                                doc.toObject(InviteCode::class.java)?.copy(code = doc.id)
                            } ?: emptyList()
                        Log.d("HouseholdRepository", "Loaded ${codes.size} invite codes")
                        trySend(codes).isSuccess
                    }
            awaitClose { listener.remove() }
        }
            .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions

    /**
     * Revokes (deactivates) an invite code.
     */
    suspend fun revokeInviteCode(
        householdId: String,
        code: String
    ) {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")
        val household =
            getHouseholdById(householdId)
                ?: throw IllegalStateException("Household not found")

        // Check permission
        val userRole = household.getRoleForUser(currentUser.uid)
        if (!userRole.canManageInviteCodes()) {
            throw IllegalStateException("You don't have permission to revoke invite codes")
        }

        firestore.collection("inviteCodes").document(code)
            .update("active", false)
            .await()
    }

    /**
     * Redeems an invite code to join a household.
     *
     * @param code The invite code to redeem.
     * @return The Household that was joined, or throws exception on failure.
     */
    suspend fun redeemInviteCode(code: String): String {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")
        val upperCode = code.uppercase().trim()

        val codeDoc = firestore.collection("inviteCodes").document(upperCode).get().await()
        if (!codeDoc.exists()) {
            throw IllegalStateException("Invalid invite code")
        }

        val inviteCode =
            codeDoc.toObject(InviteCode::class.java)?.copy(code = upperCode)
                ?: throw IllegalStateException("Invalid invite code")

        // Validate code
        if (!inviteCode.isActive) {
            throw IllegalStateException("This invite code has been revoked")
        }
        if (inviteCode.usedBy != null) {
            throw IllegalStateException("This invite code has already been used")
        }
        if (inviteCode.isExpired()) {
            throw IllegalStateException("This invite code has expired")
        }

        // Add user to household with MEMBER role using batch write
        val batch = firestore.batch()

        val householdRef = firestore.collection("households").document(inviteCode.householdId)

        // Add to members array
        batch.update(householdRef, "members", FieldValue.arrayUnion(currentUser.uid))

        // Add role to memberRoles map
        batch.update(
            householdRef,
            "memberRoles.${currentUser.uid}",
            HouseholdRole.MEMBER.name
        )

        val codeRef = firestore.collection("inviteCodes").document(upperCode)
        batch.update(
            codeRef,
            mapOf(
                "usedBy" to currentUser.uid,
                "usedAt" to System.currentTimeMillis()
            )
        )

        batch.commit().await()

        // Return the household ID - the UI will navigate there and load the household
        return inviteCode.householdId
    }

    /**
     * Checks if the current user is already a member of the specified household.
     */
    suspend fun isUserMemberOfHousehold(householdId: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false
        return try {
            val household = getHouseholdById(householdId) ?: return false
            household.members.contains(currentUserId)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking membership: ${e.message}")
            false
        }
    }

    /**
     * Validates an invite code and returns invite code info for confirmation UI.
     * Does not redeem the code. Returns invite code with embedded household name.
     * Note: We don't fetch the full household since the user isn't a member yet.
     */
    suspend fun validateInviteCode(code: String): InviteCode? {
        val upperCode = code.uppercase().trim()

        return try {
            val codeDoc = firestore.collection("inviteCodes").document(upperCode).get().await()
            if (!codeDoc.exists()) return null

            val inviteCode =
                codeDoc.toObject(InviteCode::class.java)?.copy(code = upperCode)
                    ?: return null

            if (!inviteCode.isValid()) return null

            inviteCode
        } catch (e: Exception) {
            Log.e(TAG, "Error validating invite code: ${e.message}")
            null
        }
    }

    // ==================== Shopping List Operations ====================
    // (Moved from FridgeRepository - now at household level)

    /**
     * Returns a Flow of shopping list items for a household.
     * Closes gracefully on permission errors.
     */
    fun getShoppingListItems(householdId: String): Flow<List<ShoppingListItem>> =
        callbackFlow {
            val colRef =
                firestore.collection("households").document(householdId)
                    .collection("shoppingList")

            val listener =
                colRef.addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e(TAG, "Error loading shopping list: ${e.message}")
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
                addedAt = System.currentTimeMillis(),
                addedBy = currentUser,
                quantity = quantity,
                store = store,
                customName = customName
            )

        // Add item to shopping list
        firestore.collection("households").document(householdId)
            .collection("shoppingList").document(upc)
            .set(item)
            .await()

        // Get display name for notification (product name if customName is empty)
        val displayName =
            if (customName.isNotEmpty()) {
                customName
            } else {
                // Fetch product name from products collection
                try {
                    val product = firestore.collection("products").document(upc).get().await()
                    product.getString("name") ?: upc
                } catch (e: Exception) {
                    Log.w(TAG, "Could not fetch product name for UPC: $upc", e)
                    upc
                }
            }

        // Send notifications to recent shoppers
        notifyRecentShoppers(householdId, displayName)
    }

    /**
     * Removes an item from the shopping list.
     */
    suspend fun removeShoppingListItem(
        householdId: String,
        upc: String
    ) {
        firestore.collection("households").document(householdId)
            .collection("shoppingList").document(upc)
            .delete()
            .await()
    }

    /**
     * Updates the current user's obtained quantity and target fridge for an item.
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
            firestore.collection("households").document(householdId)
                .collection("shoppingList").document(upc)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(itemRef)
            val currentObtainedBy = snapshot.get("obtainedBy") as? Map<String, Long> ?: emptyMap()
            val currentTargetFridgeId = snapshot.get("targetFridgeId") as? Map<String, String> ?: emptyMap()

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
                    "obtainedBy" to updatedObtainedBy,
                    "targetFridgeId" to updatedTargetFridgeId,
                    "obtainedQuantity" to newTotal,
                    "checked" to checked,
                    "lastUpdatedBy" to currentUserId,
                    "lastUpdatedAt" to System.currentTimeMillis()
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
            firestore.collection("households").document(householdId)
                .collection("shoppingList")

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
                        firestore.collection("fridges")
                            .document(targetFridgeId)
                            .collection("items")

                    repeat(userQuantity) {
                        // Auto-generate ID
                        val newItemRef = itemsCollection.document()
                        val newItem =
                            Item(
                                upc = item.upc,
                                // No expiration from shopping list
                                expirationDate = null,
                                addedBy = currentUserId,
                                addedAt = System.currentTimeMillis(),
                                lastUpdatedBy = currentUserId,
                                lastUpdatedAt = System.currentTimeMillis()
                            )
                        batch.set(newItemRef, newItem)
                    }

                    Log.d(TAG, "Adding $userQuantity instance(s) of ${item.upc} to fridge $targetFridgeId")

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
                                "quantity" to remainingQuantityNeeded,
                                "obtainedBy" to remainingObtainedBy,
                                "targetFridgeId" to remainingTargetFridgeId,
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
            Log.d(TAG, "Shopping session completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error completing shopping session", e)
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

            Log.d(TAG, "=== Shopping List Notification Debug ===")
            Log.d(TAG, "Checking for recent viewers in household: $householdId")
            Log.d(TAG, "Item added: $itemName by user: $currentUserId")

            // Get recent viewers (last 30 minutes)
            val recentViewers = getRecentShoppingListViewers(householdId)

            Log.d(TAG, "Found ${recentViewers.size} total recent viewers")
            recentViewers.forEach { viewer ->
                val minutesAgo = (System.currentTimeMillis() - viewer.lastSeenTimestamp) / 60000
                Log.d(TAG, "  - ${viewer.username} (${viewer.userId}) - last seen $minutesAgo minutes ago")
            }

            val viewersToNotify = recentViewers.filter { it.userId != currentUserId }

            if (viewersToNotify.isEmpty()) {
                Log.d(TAG, "No users to notify (excluding current user)")
                return
            }

            Log.d(TAG, "Sending notifications to ${viewersToNotify.size} users")

            // Send in-app notifications to each recent viewer
            viewersToNotify.forEach { viewer ->
                Log.d(TAG, "Sending notification to: ${viewer.username}")
                notificationRepository.sendInAppNotification(
                    userId = viewer.userId,
                    title = "New item added to shopping list",
                    body = "$itemName was just added",
                    type = fyi.goodbye.fridgy.models.NotificationType.ITEM_ADDED,
                    relatedFridgeId = null,
                    relatedItemId = itemName
                )
            }

            Log.d(TAG, "Successfully notified ${viewersToNotify.size} recent shoppers")
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying recent shoppers", e)
            // Don't throw - notification failure shouldn't block adding items
        }
    }

    /**
     * Gets users who have viewed the shopping list in the last 30 minutes.
     * Returns a list of ActiveViewer objects with user info and last seen timestamp.
     */
    private suspend fun getRecentShoppingListViewers(householdId: String): List<ActiveViewer> {
        return try {
            val currentTime = System.currentTimeMillis()
            val thirtyMinutesAgo = currentTime - RECENT_VIEWER_TIMEOUT_MS

            Log.d(TAG, "Querying presence documents for household: $householdId")
            Log.d(TAG, "Current time: $currentTime, cutoff time: $thirtyMinutesAgo")
            Log.d(TAG, "Looking for presence within last ${RECENT_VIEWER_TIMEOUT_MS / 60000} minutes")

            val presenceSnapshot =
                firestore.collection("households").document(householdId)
                    .collection("shoppingListPresence")
                    .get()
                    .await()

            Log.d(TAG, "Found ${presenceSnapshot.documents.size} presence documents total")

            val recentUserData =
                presenceSnapshot.documents.mapNotNull { doc ->
                    val lastSeen = doc.getTimestamp("lastSeen")?.toDate()?.time ?: 0
                    val userId = doc.getString("userId")

                    val minutesAgo = if (lastSeen > 0) (currentTime - lastSeen) / 60000 else -1
                    Log.d(TAG, "Presence doc: userId=$userId, lastSeen=$lastSeen (${minutesAgo}min ago)")

                    // Check if user viewed within last 30 minutes
                    if (userId != null && lastSeen >= thirtyMinutesAgo) {
                        Log.d(TAG, "  -> INCLUDED (within 30 min)")
                        userId to lastSeen
                    } else {
                        if (userId != null) {
                            Log.d(TAG, "  -> EXCLUDED (too old or invalid)")
                        }
                        null
                    }
                }

            if (recentUserData.isEmpty()) {
                Log.d(TAG, "No recent viewers found after filtering")
                return emptyList()
            }

            Log.d(TAG, "Fetching user profiles for ${recentUserData.size} recent viewers")

            // Batch fetch user profiles
            val userIds = recentUserData.map { it.first }
            val profiles = getUsersByIds(userIds)

            recentUserData.mapNotNull { (userId, lastSeen) ->
                val username = profiles[userId]?.username ?: return@mapNotNull null
                ActiveViewer(userId, username, lastSeen)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recent shopping list viewers", e)
            emptyList()
        }
    }

    /**
     * Sets the current user as actively viewing the shopping list.
     * Also performs periodic cleanup of stale presence documents (older than 24 hours).
     */
    suspend fun setShoppingListPresence(householdId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val presenceRef =
            firestore.collection("households").document(householdId)
                .collection("shoppingListPresence").document(currentUserId)

        presenceRef.set(
            mapOf(
                "userId" to currentUserId,
                "lastSeen" to FieldValue.serverTimestamp()
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
                firestore.collection("households").document(householdId)
                    .collection("shoppingListPresence")
                    .get()
                    .await()

            val batch = firestore.batch()
            var deleteCount = 0

            stalePresence.documents.forEach { doc ->
                val userId = doc.getString("userId")
                val lastSeen = doc.getTimestamp("lastSeen")?.toDate()?.time ?: 0

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
                    Log.d(TAG, "Marking stale presence for deletion: userId=$userId, lastSeen=$lastSeen")
                }
            }

            if (deleteCount > 0) {
                batch.commit().await()
                Log.d(TAG, "Cleaned up $deleteCount stale presence documents")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up stale presence", e)
            // Don't throw - cleanup failure shouldn't affect presence updates
        }
    }

    /**
     * Removes the current user's presence from the shopping list.
     * Note: This is kept for explicit removal scenarios, but typically
     * we don't remove presence to preserve lastSeen for notifications.
     */
    suspend fun removeShoppingListPresence(householdId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        firestore.collection("households").document(householdId)
            .collection("shoppingListPresence").document(currentUserId)
            .delete()
            .await()
    }

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
     */
    fun getShoppingListPresence(householdId: String): Flow<List<ActiveViewer>> =
        callbackFlow {
            val presenceRef =
                firestore.collection("households").document(householdId)
                    .collection("shoppingListPresence")

            val listener =
                presenceRef.addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e(TAG, "Error loading shopping list presence: ${e.message}")
                        channel.close()
                        return@addSnapshotListener
                    }

                    val currentTime = System.currentTimeMillis()

                    // Collect active user IDs and their timestamps first (no async here)
                    val activeUserData =
                        snapshot?.documents?.mapNotNull { doc ->
                            val lastSeen = doc.getTimestamp("lastSeen")?.toDate()?.time ?: 0
                            val userId = doc.getString("userId")
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
                            Log.e(TAG, "Error fetching user profiles for presence", ex)
                            trySend(emptyList()).isSuccess
                        }
                    }
                }

            awaitClose { listener.remove() }
        }
            .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions

    // ==================== Migration Helper ====================

    /**
     * Checks if the current user has any fridges without a householdId (legacy fridges).
     * Used for first-run migration flow.
     *
     * Note: This is disabled in the new architecture since fridges no longer have
     * a members field. Migration from the old model should be handled via a
     * separate admin tool or Cloud Function if needed.
     */
    suspend fun hasOrphanFridges(): Boolean {
        // Disabled - the new architecture doesn't support querying fridges
        // by member since membership is now at the household level
        return false
    }

    /**
     * Migrates orphan fridges (without householdId) to a new or existing household.
     * Creates a default household named "My Home" if user has no households.
     *
     * @return The household ID that the fridges were migrated to.
     */
    suspend fun migrateOrphanFridges(): String {
        val currentUserId =
            auth.currentUser?.uid
                ?: throw IllegalStateException("User not logged in.")

        // Check if user already has a household
        val existingHouseholds =
            firestore.collection("households")
                .whereArrayContains("members", currentUserId)
                .get()
                .await()

        val targetHouseholdId =
            if (existingHouseholds.isEmpty) {
                // Create a default household
                val newHousehold = createHousehold("My Home")
                newHousehold.id
            } else {
                existingHouseholds.documents.first().id
            }

        // Find all orphan fridges where user is in members list
        val orphanFridges =
            firestore.collection("fridges")
                .whereArrayContains("members", currentUserId)
                .get()
                .await()

        val batch = firestore.batch()

        orphanFridges.documents.forEach { doc ->
            val householdId = doc.getString("householdId")
            if (householdId.isNullOrEmpty()) {
                // Migrate this fridge to the target household
                batch.update(doc.reference, "householdId", targetHouseholdId)

                // Also migrate shopping list if it exists as a subcollection
                // (Shopping list data stays with fridge during migration,
                // but new items go to household level)
            }
        }

        batch.commit().await()
        Log.d(TAG, "Migrated ${orphanFridges.size()} orphan fridges to household $targetHouseholdId")

        return targetHouseholdId
    }

    // ==================== Utility Methods ====================

    /**
     * Fetches multiple user profiles by their IDs.
     */
    private suspend fun getUsersByIds(userIds: List<String>): Map<String, UserProfile> {
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
