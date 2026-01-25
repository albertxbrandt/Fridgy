package fyi.goodbye.fridgy

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * Utilities for integration testing with Firebase Local Emulator Suite.
 *
 * These utilities configure Firebase to connect to local emulators instead of
 * production Firebase services. This allows for fast, isolated testing without
 * affecting production data.
 *
 * ## Emulator Setup
 * 1. Install Firebase CLI: `npm install -g firebase-tools`
 * 2. Initialize emulators: `firebase init emulators`
 *    - Select: Authentication, Firestore, Storage
 *    - Use default ports (Auth: 9099, Firestore: 8080, Storage: 9199)
 * 3. Start emulators: `firebase emulators:start`
 *
 * ## Usage in Tests
 * ```kotlin
 * @Before
 * fun setup() {
 *     FirebaseTestUtils.useEmulators()
 *     // ... rest of setup
 * }
 * ```
 *
 * ## Android Emulator Network
 * The IP address 10.0.2.2 is a special alias for the host machine's localhost
 * when running in an Android emulator. This allows the emulator to connect to
 * Firebase emulators running on the host machine.
 */
object FirebaseTestUtils {
    private var emulatorsConfigured = false

    /**
     * Configure Firebase to use local emulators instead of production services.
     * Safe to call multiple times - only configures once.
     */
    fun useEmulators() {
        if (emulatorsConfigured) return

        // Android emulator uses 10.0.2.2 to reach host machine's localhost
        FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
        FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
        FirebaseStorage.getInstance().useEmulator("10.0.2.2", 9199)

        emulatorsConfigured = true
    }

    /**
     * Clear all Firestore data in the emulator.
     * Useful for test isolation - call in @After or @Before.
     */
    suspend fun clearFirestoreData() {
        val firestore = FirebaseFirestore.getInstance()

        // Delete all collections
        val collections =
            listOf(
                "users",
                "userProfiles",
                "admins",
                "households",
                "fridges",
                "products",
                "inviteCodes",
                "notifications"
            )

        collections.forEach { collectionName ->
            try {
                val snapshot = firestore.collection(collectionName).get().await()
                snapshot.documents.forEach { doc ->
                    doc.reference.delete().await()
                }
            } catch (e: Exception) {
                // Collection might not exist, that's fine
            }
        }
    }

    /**
     * Create a test user in Firebase Auth emulator.
     * Returns the created user's UID.
     */
    suspend fun createTestUser(
        email: String = "test@example.com",
        password: String = "password123"
    ): String {
        val auth = FirebaseAuth.getInstance()
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user?.uid ?: throw IllegalStateException("Failed to create test user")
    }

    /**
     * Sign in as a test user.
     * Returns the user's UID.
     */
    suspend fun signInTestUser(
        email: String = "test@example.com",
        password: String = "password123"
    ): String {
        val auth = FirebaseAuth.getInstance()
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user?.uid ?: throw IllegalStateException("Failed to sign in test user")
    }

    /**
     * Sign out the current user.
     */
    fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }

    /**
     * Delete all users from Auth emulator.
     * Note: This requires calling the emulator's REST API directly,
     * as the Firebase SDK doesn't provide a bulk delete method.
     */
    suspend fun clearAuthUsers() {
        // Sign out current user first
        signOut()

        // Note: Full implementation would require HTTP calls to emulator API
        // For now, just sign out. Individual tests should clean up their users.
    }
}
