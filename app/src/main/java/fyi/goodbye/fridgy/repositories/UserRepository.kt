package fyi.goodbye.fridgy.repositories

import android.util.Log
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.models.UserProfile
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing user-related data operations.
 *
 * Handles:
 * - User authentication (signup, login, logout)
 * - User profile CRUD operations
 * - Username availability checks
 *
 * This repository abstracts Firebase Auth and Firestore operations
 * for user management, following the MVVM architecture pattern.
 *
 * @param firestore The Firestore instance for database operations.
 * @param auth The Auth instance for authentication.
 */
class UserRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    companion object {
        private const val TAG = "UserRepository"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_USER_PROFILES = "userProfiles"
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
                firestore.collection(COLLECTION_USER_PROFILES)
                    .whereEqualTo("username", username)
                    .get()
                    .await()
            !existingProfiles.isEmpty
        } catch (e: Exception) {
            Log.e(TAG, "Error checking username availability", e)
            // Return true (taken) on error to be safe
            true
        }
    }

    /**
     * Creates a new user account with email and password.
     *
     * @param email The user's email address.
     * @param password The user's password.
     * @return The AuthResult from Firebase Auth.
     * @throws Exception If account creation fails.
     */
    suspend fun createAuthAccount(
        email: String,
        password: String
    ): AuthResult {
        return auth.createUserWithEmailAndPassword(email, password).await()
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
                "email" to email,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

        // Create public profile data (username only)
        val profileMap =
            hashMapOf(
                "username" to username
            )

        // Write both documents
        firestore.collection(COLLECTION_USERS).document(uid).set(userMap).await()
        firestore.collection(COLLECTION_USER_PROFILES).document(uid).set(profileMap).await()

        Log.d(TAG, "Created user documents for UID: $uid")
    }

    /**
     * Signs up a new user with email, password, and username.
     * This is a convenience method that combines account creation and document creation.
     *
     * @param email The user's email address.
     * @param password The user's password.
     * @param username The user's chosen username (should be validated and checked for availability first).
     * @return The user's UID if successful.
     * @throws Exception If signup fails at any step.
     */
    suspend fun signUp(
        email: String,
        password: String,
        username: String
    ): String {
        val authResult = createAuthAccount(email, password)
        val uid =
            authResult.user?.uid
                ?: throw IllegalStateException("User created but UID is null")

        createUserDocuments(uid, email, username)
        return uid
    }

    /**
     * Signs in an existing user with email and password.
     *
     * @param email The user's email address.
     * @param password The user's password.
     * @return The AuthResult from Firebase Auth.
     * @throws Exception If sign in fails.
     */
    suspend fun signIn(
        email: String,
        password: String
    ): AuthResult {
        return auth.signInWithEmailAndPassword(email, password).await()
    }

    /**
     * Signs out the current user.
     */
    fun signOut() {
        auth.signOut()
        Log.d(TAG, "User signed out")
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
                firestore.collection(COLLECTION_USER_PROFILES)
                    .document(userId)
                    .get()
                    .await()
            doc.toObject(UserProfile::class.java)?.copy(uid = userId)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user profile: ${e.message}", e)
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
        firestore.collection(COLLECTION_USER_PROFILES)
            .document(userId)
            .update("username", newUsername)
            .await()

        // Update username in all fridges where user is a member
        val fridgesQuery =
            firestore.collection("fridges")
                .whereNotEqualTo("members.$userId", null)
                .get()
                .await()

        val batch = firestore.batch()
        for (doc in fridgesQuery.documents) {
            batch.update(doc.reference, "members.$userId", newUsername)
        }
        batch.commit().await()

        Log.d(TAG, "Updated username to: $newUsername")
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
                firestore.collection("fridges")
                    .whereNotEqualTo("members.$userId", null)
                    .get()
                    .await()

            val batch = firestore.batch()
            for (doc in fridgesQuery.documents) {
                // Remove from members map
                batch.update(doc.reference, "members.$userId", com.google.firebase.firestore.FieldValue.delete())
                // Remove from pendingInvites if present
                batch.update(doc.reference, "pendingInvites.$userId", com.google.firebase.firestore.FieldValue.delete())
            }
            batch.commit().await()

            // 2. Delete FCM tokens
            val tokensQuery =
                firestore.collection("fcmTokens")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

            val tokenBatch = firestore.batch()
            for (doc in tokensQuery.documents) {
                tokenBatch.delete(doc.reference)
            }
            tokenBatch.commit().await()

            // 3. Delete user documents
            firestore.collection(COLLECTION_USERS).document(userId).delete().await()
            firestore.collection(COLLECTION_USER_PROFILES).document(userId).delete().await()

            // 4. Delete Firebase Auth account (must be last)
            auth.currentUser?.delete()?.await()

            Log.d(TAG, "Successfully deleted user account: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting account", e)
            throw e
        }
    }
}
