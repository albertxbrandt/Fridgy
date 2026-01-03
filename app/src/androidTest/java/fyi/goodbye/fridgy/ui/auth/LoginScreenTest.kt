package fyi.goodbye.fridgy.ui.auth

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginScreen_displaysAllElements() {
        composeTestRule.setContent {
            FridgyTheme {
                LoginScreen(
                    onLoginSuccess = {},
                    onNavigateToSignup = {}
                )
            }
        }

        // Verify title and welcome message exist
        composeTestRule.onNodeWithText("Welcome to Fridgy!").assertExists()
        composeTestRule.onNodeWithText("Manage your fridge, effortlessly.").assertExists()

        // Verify input fields exist
        composeTestRule.onNodeWithText("Email").assertExists()
        composeTestRule.onNodeWithText("Password").assertExists()

        // Verify login button exists
        composeTestRule.onNodeWithText("Login").assertExists()

        // Verify signup link exists
        composeTestRule.onNodeWithText("Don't have an account? Sign Up").assertExists()
    }

    @Test
    fun loginScreen_loginButtonIsInitiallyDisabled() {
        composeTestRule.setContent {
            FridgyTheme {
                LoginScreen(
                    onLoginSuccess = {},
                    onNavigateToSignup = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Login").assertIsNotEnabled()
    }

    @Test
    fun loginScreen_canEnterEmail() {
        composeTestRule.setContent {
            FridgyTheme {
                LoginScreen(
                    onLoginSuccess = {},
                    onNavigateToSignup = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Email")
            .performTextInput("test@example.com")
    }

    @Test
    fun loginScreen_canEnterPassword() {
        composeTestRule.setContent {
            FridgyTheme {
                LoginScreen(
                    onLoginSuccess = {},
                    onNavigateToSignup = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Password")
            .performTextInput("password123")
    }

    @Test
    fun loginScreen_signupLinkTriggersNavigation() {
        var navigationTriggered = false

        composeTestRule.setContent {
            FridgyTheme {
                LoginScreen(
                    onLoginSuccess = {},
                    onNavigateToSignup = { navigationTriggered = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Don't have an account? Sign Up")
            .performClick()

        assert(navigationTriggered)
    }
}
