package fyi.goodbye.fridgy.repositories


import fyi.goodbye.fridgy.constants.FirestoreCollections
import fyi.goodbye.fridgy.constants.FirestoreFields
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.models.HouseholdRole
import fyi.goodbye.fridgy.models.InviteCode
import fyi.goodbye.fridgy.models.canManageInviteCodes
import fyi.goodbye.fridgy.models.canModifyUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import kotlin.random.Random


/**
 * Repository for managing household membership operations.
 *
 * Handles all membership-related operations within households:
 * - Invite code system (create, validate, redeem, revoke)
 * - Joining households via invite codes
 * - Updating member roles
 * - Removing members from households
 * - Leaving households
 *
 * ## Invite Code System
 * Users join households by redeeming 6-character alphanumeric codes. Codes can have
 * optional expiration dates and are single-use by default.
 *
 * ## Role-Based Permissions
 * - Only owners/managers can create/revoke invite codes
 * - Only owners can change roles
 * - Owners cannot be removed or change their own role
 * - Role hierarchy determines who can modify whom
 *
 * @param firestore The Firestore instance for database operations
 * @param auth The Auth instance for user identification
 * @param householdRepository The HouseholdRepository for fetching household data
 */
class MembershipRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val householdRepository: HouseholdRepository
) {
    companion object {
        private const val TAG = "MembershipRepository"
        private const val INVITE_CODE_LENGTH = 6
        private const val INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Exclude confusing chars (I, O, 0, 1)
    }

    /**
     * Updates the role of a member in the household.
     * Only the owner can change roles.
     *
     * @param householdId The household ID
     * @param userId The user whose role to update
     * @param newRole The new role to assign
     * @throws IllegalStateException if user is not logged in, household not found,
     *         user is not the owner, or attempting to change owner's role
     */
    suspend fun updateMemberRole(
        householdId: String,
        userId: String,
        newRole: HouseholdRole
    ) {
        val household =
            householdRepository.getHouseholdById(householdId)
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

        firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId)
            .update(FirestoreFields.MEMBER_ROLES, updatedRoles)
            .await()
    }

    /**
     * Removes a member from a household. Only the owner can remove members.
     * The owner cannot be removed.
     *
     * @param householdId The household ID
     * @param userId The user to remove
     * @throws IllegalStateException if attempting to remove owner, current user lacks permission,
     *         or user is not logged in
     */
    suspend fun removeMember(
        householdId: String,
        userId: String
    ) {
        val household =
            householdRepository.getHouseholdById(householdId)
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
        val householdRef = firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId)

        // Remove from members list
        batch.update(householdRef, FirestoreFields.MEMBERS, FieldValue.arrayRemove(userId))

        // Remove from roles map
        val updatedRoles = household.memberRoles.toMutableMap()
        updatedRoles.remove(userId)
        batch.update(householdRef, FirestoreFields.MEMBER_ROLES, updatedRoles)

        batch.commit().await()
    }

    /**
     * Allows the current user to leave a household.
     * The owner cannot leave - they must delete the household or transfer ownership.
     *
     * @param householdId The household ID to leave
     * @throws IllegalStateException if user is not logged in or is the owner
     */
    suspend fun leaveHousehold(householdId: String) {
        val currentUserId =
            auth.currentUser?.uid
                ?: throw IllegalStateException("User not logged in")

        // Try to get household, but if it fails (permission denied, network issue, etc.),
        // still attempt to remove user from members list
        try {
            val household = householdRepository.getHouseholdById(householdId)

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
            firestore.collection(FirestoreCollections.HOUSEHOLDS).document(householdId)
                .update(FirestoreFields.MEMBERS, FieldValue.arrayRemove(currentUserId))
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
            householdRepository.getHouseholdById(householdId)
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
            val existing = firestore.collection(FirestoreCollections.INVITE_CODES).document(code).get().await()
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

        Log.d(TAG, "Creating invite code: $code for household: $householdId, isActive: true")
        firestore.collection(FirestoreCollections.INVITE_CODES).document(code).set(inviteCode).await()
        Log.d(TAG, "Invite code created successfully")
        return inviteCode
    }

    /**
     * Gets all active invite codes for a household.
     */
    suspend fun getInviteCodesForHousehold(householdId: String): List<InviteCode> {
        return try {
            val snapshot =
                firestore.collection(FirestoreCollections.INVITE_CODES)
                    .whereEqualTo(FirestoreFields.HOUSEHOLD_ID, householdId)
                    .whereEqualTo(FirestoreFields.ACTIVE, true)
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
            Log.d(TAG, "Starting invite codes listener for household: $householdId")
            val listener =
                firestore.collection(FirestoreCollections.INVITE_CODES)
                    .whereEqualTo(FirestoreFields.HOUSEHOLD_ID, householdId)
                    .whereEqualTo(FirestoreFields.ACTIVE, true)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.e(TAG, "Error loading invite codes: ${e.message}", e)
                            // Don't crash - just close the flow gracefully
                            // This happens when user loses permission (e.g., leaves household)
                            channel.close()
                            return@addSnapshotListener
                        }
                        val codes =
                            snapshot?.documents?.mapNotNull { doc ->
                                doc.toObject(InviteCode::class.java)?.copy(code = doc.id)
                            } ?: emptyList()
                        Log.d(TAG, "Loaded ${codes.size} invite codes")
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
            householdRepository.getHouseholdById(householdId)
                ?: throw IllegalStateException("Household not found")

        // Check permission
        val userRole = household.getRoleForUser(currentUser.uid)
        if (!userRole.canManageInviteCodes()) {
            throw IllegalStateException("You don't have permission to revoke invite codes")
        }

        firestore.collection(FirestoreCollections.INVITE_CODES).document(code)
            .update(FirestoreFields.ACTIVE, false)
            .await()
    }

    /**
     * Redeems an invite code to join a household.
     *
     * @param code The invite code to redeem.
     * @return The household ID that was joined, or throws exception on failure.
     */
    suspend fun redeemInviteCode(code: String): String {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")
        val upperCode = code.uppercase().trim()

        val codeDoc = firestore.collection(FirestoreCollections.INVITE_CODES).document(upperCode).get().await()
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

        val householdRef = firestore.collection(FirestoreCollections.HOUSEHOLDS).document(inviteCode.householdId)

        // Add to members array
        batch.update(householdRef, FirestoreFields.MEMBERS, FieldValue.arrayUnion(currentUser.uid))

        // Add role to memberRoles map
        batch.update(
            householdRef,
            "${FirestoreFields.MEMBER_ROLES}.${currentUser.uid}",
            HouseholdRole.MEMBER.name
        )

        val codeRef = firestore.collection(FirestoreCollections.INVITE_CODES).document(upperCode)
        batch.update(
            codeRef,
            mapOf(
                FirestoreFields.USED_BY to currentUser.uid,
                FirestoreFields.USED_AT to System.currentTimeMillis()
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
            val household = householdRepository.getHouseholdById(householdId) ?: return false
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
            val codeDoc = firestore.collection(FirestoreCollections.INVITE_CODES).document(upperCode).get().await()
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
}
