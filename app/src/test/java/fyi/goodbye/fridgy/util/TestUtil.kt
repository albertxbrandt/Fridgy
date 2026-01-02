package fyi.goodbye.fridgy.util

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic

/**
 * Utility object providing common mocking patterns for Firebase services in tests.
 * 
 * Use these helpers to quickly set up Firebase mocks without repeating boilerplate code.
 */
object TestUtil {

    /**
     * Creates a mocked FirebaseAuth instance with a logged-in user.
     * 
     * @param userId The UID to assign to the mocked user. Defaults to "test-user-id".
     * @param userEmail The email to assign to the mocked user. Defaults to "test@example.com".
     * @return A mocked FirebaseAuth instance.
     */
    fun createMockFirebaseAuth(
        userId: String = "test-user-id",
        userEmail: String = "test@example.com"
    ): FirebaseAuth {
        val mockAuth = mockk<FirebaseAuth>(relaxed = true)
        val mockUser = mockk<FirebaseUser>(relaxed = true)
        
        every { mockUser.uid } returns userId
        every { mockUser.email } returns userEmail
        every { mockAuth.currentUser } returns mockUser
        
        return mockAuth
    }

    /**
     * Creates a mocked FirebaseAuth instance with no logged-in user.
     * 
     * @return A mocked FirebaseAuth instance with currentUser = null.
     */
    fun createMockFirebaseAuthNoUser(): FirebaseAuth {
        val mockAuth = mockk<FirebaseAuth>(relaxed = true)
        every { mockAuth.currentUser } returns null
        return mockAuth
    }

    /**
     * Creates a mocked FirebaseFirestore instance with basic collection/document setup.
     * 
     * @return A mocked FirebaseFirestore instance.
     */
    fun createMockFirestore(): FirebaseFirestore {
        val mockFirestore = mockk<FirebaseFirestore>(relaxed = true)
        val mockCollection = mockk<CollectionReference>(relaxed = true)
        val mockDocument = mockk<DocumentReference>(relaxed = true)
        
        every { mockFirestore.collection(any()) } returns mockCollection
        every { mockCollection.document(any()) } returns mockDocument
        
        return mockFirestore
    }

    /**
     * Mocks FirebaseAuth.getInstance() to return a custom mock instance.
     * Call this in test setup when using FirebaseAuth.getInstance() directly in code.
     * 
     * @param mockAuth The FirebaseAuth mock to return from getInstance().
     */
    fun mockFirebaseAuthInstance(mockAuth: FirebaseAuth) {
        mockkStatic(FirebaseAuth::class)
        every { FirebaseAuth.getInstance() } returns mockAuth
    }

    /**
     * Mocks FirebaseFirestore.getInstance() to return a custom mock instance.
     * Call this in test setup when using FirebaseFirestore.getInstance() directly in code.
     * 
     * @param mockFirestore The FirebaseFirestore mock to return from getInstance().
     */
    fun mockFirestoreInstance(mockFirestore: FirebaseFirestore) {
        mockkStatic(FirebaseFirestore::class)
        every { FirebaseFirestore.getInstance() } returns mockFirestore
    }
}
