package fyi.goodbye.fridgy.testutils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Initializes Firebase for instrumented tests and points services to local emulators.
 * Call from @Before in androidTest classes.
 */
object AndroidFirebaseTestSetup {
    fun initializeForInstrumentedTests() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            FirebaseApp.initializeApp(context)
        } catch (e: IllegalStateException) {
            // already initialized
        }
        // Use host machine localhost for Android emulator
        try {
            FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
        } catch (e: IllegalStateException) {
            // Auth instance already initialized; ignore in instrumented test runs
        }

        try {
            FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8555)
        } catch (e: IllegalStateException) {
            // Firestore instance already initialized; ignore in instrumented test runs
        }
    }
}
