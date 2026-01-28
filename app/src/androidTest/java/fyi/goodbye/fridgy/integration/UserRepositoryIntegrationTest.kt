package fyi.goodbye.fridgy.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.FirebaseTestUtils
import fyi.goodbye.fridgy.repositories.UserRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for UserRepository with Firebase Local Emulator Suite.
 *
 * ## Prerequisites
 * 1. Firebase emulators must be running: `firebase emulators:start`
 * 2. Run tests: `./gradlew connectedAndroidTest`
 *
 * These tests use real Firebase operations against local emulators,
 * providing confidence that repositories work correctly with actual Firebase SDKs.
 */
@RunWith(AndroidJUnit4::class)
class UserRepositoryIntegrationTest {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var repository: UserRepository

    @Before
    fun setup() {
        // Connect to Firebase emulators
        FirebaseTestUtils.useEmulators()

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        repository = UserRepository(firestore, auth)

        // Clean slate for each test
        runBlocking {
            FirebaseTestUtils.clearFirestoreData()
            FirebaseTestUtils.signOut()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            FirebaseTestUtils.signOut()
            FirebaseTestUtils.clearFirestoreData()
        }
    }

    @Test
    fun getCurrentUserId_returnsNullWhenNotAuthenticated() {
        val userId = repository.getCurrentUserId()
        assertNull(userId)
    }

    @Test
    fun getCurrentUserId_returnsUserIdWhenAuthenticated() =
        runBlocking {
            // Create and sign in test user
            val createdUserId = FirebaseTestUtils.createTestUser("test1@example.com", "password123")

            val userId = repository.getCurrentUserId()

            assertNotNull(userId)
            assertEquals(createdUserId, userId)
        }

    @Test
    fun isUsernameTaken_returnsFalseForNewUsername() =
        runBlocking {
            val isTaken = repository.isUsernameTaken("uniqueusername")

            assertFalse(isTaken)
        }

    @Test
    fun isUsernameTaken_returnsTrueForExistingUsername() =
        runBlocking {
            // Create user with username
            val userId = FirebaseTestUtils.createTestUser("user@example.com", "password123")
            repository.createUserDocuments(userId, "user@example.com", "existinguser")

            val isTaken = repository.isUsernameTaken("existinguser")

            assertTrue(isTaken)
        }

    @Test
    fun createUserDocuments_createsUserAndProfileDocuments() =
        runBlocking {
            val userId = FirebaseTestUtils.createTestUser("new@example.com", "password123")

            repository.createUserDocuments(userId, "new@example.com", "newuser")

            // Verify users document
            val userDoc = firestore.collection("users").document(userId).get().await()
            assertTrue(userDoc.exists())
            assertEquals("new@example.com", userDoc.getString("email"))
            assertNotNull(userDoc.getLong("createdAt"))

            // Verify userProfiles document
            val profileDoc = firestore.collection("userProfiles").document(userId).get().await()
            assertTrue(profileDoc.exists())
            assertEquals("newuser", profileDoc.getString("username"))
        }

    @Test
    fun getUserProfile_returnsProfileForExistingUser() =
        runBlocking {
            val userId = FirebaseTestUtils.createTestUser("profile@example.com", "password123")
            repository.createUserDocuments(userId, "profile@example.com", "profileuser")

            val profile = repository.getUserProfile(userId)

            assertNotNull(profile)
            assertEquals("profileuser", profile!!.username)
            assertEquals(userId, profile.uid)
        }

    @Test
    fun getUserProfile_returnsNullForNonexistentUser() =
        runBlocking {
            val profile = repository.getUserProfile("nonexistent-uid")

            assertNull(profile)
        }

    @Test
    fun updateUsername_updatesProfileDocument() =
        runBlocking {
            val userId = FirebaseTestUtils.createTestUser("update@example.com", "password123")
            repository.createUserDocuments(userId, "update@example.com", "oldusername")

            repository.updateUsername("newusername")

            val profile = repository.getUserProfile(userId)
            assertNotNull(profile)
            assertEquals("newusername", profile!!.username)
        }

    @Test
    fun deleteAccount_removesUserAndProfileDocuments() =
        runBlocking {
            val userId = FirebaseTestUtils.createTestUser("delete@example.com", "password123")
            repository.createUserDocuments(userId, "delete@example.com", "deleteuser")

            repository.deleteAccount()

            // Verify documents deleted
            val userDoc = firestore.collection("users").document(userId).get().await()
            assertFalse(userDoc.exists())

            val profileDoc = firestore.collection("userProfiles").document(userId).get().await()
            assertFalse(profileDoc.exists())

            // Verify auth user deleted (should be signed out)
            assertNull(auth.currentUser)
        }

    @Test
    fun signOut_clearsCurrentUser() =
        runBlocking {
            FirebaseTestUtils.createTestUser("signout@example.com", "password123")
            assertNotNull(auth.currentUser)

            repository.signOut()

            assertNull(auth.currentUser)
        }
}
