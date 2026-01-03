package fyi.goodbye.fridgy.ui.auth

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import org.junit.Rule
import org.junit.Test

class SignUpScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun signUpScreen_displaysAllElements() {
        composeTestRule.setContent {
            FridgyTheme {
                SignUpScreen(
                    onSignUpSuccess = {},
                    onNavigateToLogin = {}
                )
            }
        }

        // Verify title exists
        composeTestRule.onNodeWithText("Join Fridgy Today!").assertExists()
        composeTestRule.onNodeWithText("Unlock effortless fridge management.").assertExists()

        // Verify all input fields exist
        composeTestRule.onNodeWithText("Email").assertExists()
        composeTestRule.onNodeWithText("Username").assertExists()
        composeTestRule.onNodeWithText("Password").assertExists()
        composeTestRule.onNodeWithText("Confirm Password").assertExists()

        // Verify sign up button exists
        composeTestRule.onNodeWithText("Sign Up").assertExists()

        // Verify login link exists
        composeTestRule.onNodeWithText("Already have an account? Log In").assertExists()
    }

    @Test
    fun signUpScreen_signUpButtonIsInitiallyDisabled() {
        composeTestRule.setContent {
            FridgyTheme {
                SignUpScreen(
                    onSignUpSuccess = {},
                    onNavigateToLogin = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Sign Up").assertIsNotEnabled()
    }

    @Test
    fun signUpScreen_canEnterEmail() {
        composeTestRule.setContent {
            FridgyTheme {
                SignUpScreen(
                    onSignUpSuccess = {},
                    onNavigateToLogin = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Email")
            .performTextInput("test@example.com")
    }

    @Test
    fun signUpScreen_canEnterUsername() {
        composeTestRule.setContent {
            FridgyTheme {
                SignUpScreen(
                    onSignUpSuccess = {},
                    onNavigateToLogin = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Username")
            .performTextInput("testuser")
    }

    @Test
    fun signUpScreen_canEnterPassword() {
        composeTestRule.setContent {
            FridgyTheme {
                SignUpScreen(
                    onSignUpSuccess = {},
                    onNavigateToLogin = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Password")
            .performTextInput("password123")
    }

    @Test
    fun signUpScreen_canEnterConfirmPassword() {
        composeTestRule.setContent {
            FridgyTheme {
                SignUpScreen(
                    onSignUpSuccess = {},
                    onNavigateToLogin = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Confirm Password")
            .performTextInput("password123")
    }

    @Test
    fun signUpScreen_loginLinkTriggersNavigation() {
        var navigationTriggered = false

        composeTestRule.setContent {
            FridgyTheme {
                SignUpScreen(
                    onSignUpSuccess = {},
                    onNavigateToLogin = { navigationTriggered = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Already have an account? Log In")
            .performClick()

        assert(navigationTriggered)
    }
}
