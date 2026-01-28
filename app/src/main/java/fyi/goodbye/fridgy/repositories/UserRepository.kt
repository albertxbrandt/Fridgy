package fyi.goodbye.fridgy.repositories

import timber.log.Timber
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.constants.FirestoreCollections
import fyi.goodbye.fridgy.constants.FirestoreFields
import fyi.goodbye.fridgy.models.UserProfile
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing user-related data operations.
 *
 * Handles:
 * - User authentication (magic link-based, managed externally)
 * - User profile CRUD operations
 * - Username availability checks
 *
 * This repository abstracts Firebase Auth and Firestore operations
 * for user management, following the MVVM architecture pattern.
 *
 * Note: Authentication is handled via magic link (email link authentication).
 * This repository provides helper methods for creating user documents after
 * successful authentication.
 *
 * @param firestore The Firestore instance for database operations.
 * @param auth The Auth instance for authentication.
 */
class UserRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    companion object {
        
    }

    /**
     * Gets the current authenticated user's UID.
     *
     * @return The current user's UID, or null if not authenticated.
     */
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    /**
     * Checks if a username is already taken.
     *
     * @param username The username to check.
     * @return True if the username is already in use, false otherwise.
     */
    suspend fun isUsernameTaken(username: String): Boolean {
        return try {
            val existingProfiles =
                firestore.collection(FirestoreCollections.USER_PROFILES)
                    .whereEqualTo(FirestoreFields.USERNAME, username)
                    .get()
                    .await()
            !existingProfiles.isEmpty
        } catch (e: Exception) {
            Timber.e(e, "Error checking username availability")
            // Return true (taken) on error to be safe
            true
        }
    }

    /**
     * Creates the user's Firestore documents after successful authentication.
     * Creates both the private 'users' document and public 'userProfiles' document.
     *
     * @param uid The user's UID from Firebase Auth.
     * @param email The user's email address.
     * @param username The user's chosen username.
     * @throws Exception If document creation fails.
     */
    suspend fun createUserDocuments(
        uid: String,
        email: String,
        username: String
    ) {
        // Create private user data (email, createdAt)
        val userMap =
            hashMapOf(
                FirestoreFields.EMAIL to email,
                FirestoreFields.CREATED_AT to FieldValue.serverTimestamp()
            )

        // Create public profile data (username only)
        val profileMap =
            hashMapOf(
                FirestoreFields.USERNAME to username
            )

        // Write both documents
        firestore.collection(FirestoreCollections.USERS).document(uid).set(userMap).await()
        firestore.collection(FirestoreCollections.USER_PROFILES).document(uid).set(profileMap).await()

        Timber.d("Created user documents for UID: $uid")
    }

    /**
     * Signs out the current user.
     */
    fun signOut() {
        auth.signOut()
        Timber.d("User signed out")
    }

    /**
     * Gets the public profile for a user.
     *
     * @param userId The UID of the user.
     * @return The UserProfile, or null if not found.
     */
    suspend fun getUserProfile(userId: String): UserProfile? {
        return try {
            val doc =
                firestore.collection(FirestoreCollections.USER_PROFILES)
                    .document(userId)
                    .get()
                    .await()
            doc.toObject(UserProfile::class.java)?.copy(uid = userId)
        } catch (e: Exception) {
            Timber.e(e, "Error fetching user profile: ${e.message}")
            null
        }
    }

    /**
     * Updates the username for the current user.
     * Updates both the public userProfile document and all references in fridges.
     *
     * @param newUsername The new username to set.
     * @throws Exception If the update fails.
     */
    suspend fun updateUsername(newUsername: String) {
        val userId =
            getCurrentUserId()
                ?: throw IllegalStateException("No authenticated user")

        // Update public profile
        firestore.collection(FirestoreCollections.USER_PROFILES)
            .document(userId)
            .update(FirestoreFields.USERNAME, newUsername)
            .await()

        // Update username in all fridges where user is a member
        val fridgesQuery =
            firestore.collection(FirestoreCollections.FRIDGES)
                .whereNotEqualTo("${FirestoreFields.MEMBERS}.$userId", null)
                .get()
                .await()

        val batch = firestore.batch()
        for (doc in fridgesQuery.documents) {
            batch.update(doc.reference, "${FirestoreFields.MEMBERS}.$userId", newUsername)
        }
        batch.commit().await()

        Timber.d("Updated username to: $newUsername")
    }

    /**
     * Deletes the current user's account and all associated data.
     * This includes:
     * - User auth account
     * - User documents (users, userProfiles)
     * - Removing user from all fridges
     * - Deleting user's FCM tokens
     *
     * @throws Exception If deletion fails.
     */
    suspend fun deleteAccount() {
        val userId =
            getCurrentUserId()
                ?: throw IllegalStateException("No authenticated user")

        try {
            // 1. Remove user from all fridges
            val fridgesQuery =
                firestore.collection(FirestoreCollections.FRIDGES)
                    .whereNotEqualTo("${FirestoreFields.MEMBERS}.$userId", null)
                    .get()
                    .await()

            val batch = firestore.batch()
            for (doc in fridgesQuery.documents) {
                // Remove from members map
                batch.update(doc.reference, "${FirestoreFields.MEMBERS}.$userId", FieldValue.delete())
                // Remove from pendingInvites if present
                batch.update(doc.reference, "${FirestoreFields.PENDING_INVITES}.$userId", FieldValue.delete())
            }
            batch.commit().await()

            // 2. Delete FCM tokens
            val tokensQuery =
                firestore.collection(FirestoreCollections.FCM_TOKENS)
                    .whereEqualTo(FirestoreFields.USER_ID, userId)
                    .get()
                    .await()

            val tokenBatch = firestore.batch()
            for (doc in tokensQuery.documents) {
                tokenBatch.delete(doc.reference)
            }
            tokenBatch.commit().await()

            // 3. Delete user documents
            firestore.collection(FirestoreCollections.USERS).document(userId).delete().await()
            firestore.collection(FirestoreCollections.USER_PROFILES).document(userId).delete().await()

            // 4. Delete Firebase Auth account (must be last)
            auth.currentUser?.delete()?.await()

            Timber.d("Successfully deleted user account: $userId")
        } catch (e: Exception) {
            Timber.e(e, "Error deleting account")
            throw e
        }
    }
}

