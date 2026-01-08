package fyi.goodbye.fridgy.ui.auth

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fyi.goodbye.fridgy.MainActivity
import fyi.goodbye.fridgy.testutils.AndroidFirebaseTestSetup
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignupInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        AndroidFirebaseTestSetup.initializeForInstrumentedTests()
        // Ensure no user is signed in so signup/login screens are shown; restart Activity
        try {
            composeRule.activityRule.scenario.onActivity { activity ->
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                } catch (_: Exception) {
                }
                try {
                    // finish current Activity safely and launch a fresh one so auth state is re-evaluated
                    activity.finish()
                    androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    @Test
    fun signupScreen_isDisplayed() {
        // Wait for the primary action button tag to appear, then assert
        composeRule.waitUntil(30_000) {
            try {
                composeRule.onAllNodesWithTag("signupButton").fetchSemanticsNodes().isNotEmpty()
            } catch (_: Throwable) {
                false
            }
        }

        // Basic smoke: primary Sign Up button exists on startup
        composeRule.onNodeWithTag("signupButton").assertExists()
    }
}
