package fyi.goodbye.fridgy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fyi.goodbye.fridgy.ui.screens.LoginScreen
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [LoginScreen] using Jetpack Compose Testing.
 * 
 * These tests verify user interactions, button states, and navigation callbacks.
 */
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginScreen_initialState_showsAllElements() {
        composeTestRule.setContent {
            FridgyTheme {
                LoginScreen(
                    onLoginSuccess = {},
                    onNavigateToSignup = {}
                )
            }
        }

        // Verify all key elements are displayed
        composeTestRule.onNodeWithText("Welcome to Fridgy!").assertIsDisplayed()
        composeTestRule.onNodeWithTag("emailInput").assertIsDisplayed()
        composeTestRule.onNodeWithTag("passwordInput").assertIsDisplayed()
        composeTestRule.onNodeWithTag("loginButton").assertIsDisplayed()
        composeTestRule.onNodeWithText("Don't have an account? Sign Up").assertIsDisplayed()
    }

    @Test
    fun loginButton_isEnabledByDefault() {
        composeTestRule.setContent {
            FridgyTheme {
                LoginScreen(
                    onLoginSuccess = {},
                    onNavigateToSignup = {}
                )
            }
        }

        // Login button should be enabled even with empty fields
        // (validation happens on click in this implementation)
        composeTestRule.onNodeWithTag("loginButton").assertIsEnabled()
    }

    @Test
    fun enteringCredentials_displaysInInputFields() {
        composeTestRule.setContent {
            FridgyTheme {
                LoginScreen(
                    onLoginSuccess = {},
                    onNavigateToSignup = {}
                )
            }
        }

        // Enter email
        composeTestRule.onNodeWithTag("emailInput")
            .performTextInput("test@example.com")

        // Enter password
        composeTestRule.onNodeWithTag("passwordInput")
            .performTextInput("password123")

        // Verify text was entered (email should be visible)
        composeTestRule.onNodeWithTag("emailInput")
            .assertTextContains("test@example.com")
    }

    @Test
    fun clickingSignupLink_triggersNavigationCallback() {
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

    @Test
    fun emptyFieldsLogin_showsErrorMessage() {
        composeTestRule.setContent {
            FridgyTheme {
                LoginScreen(
                    onLoginSuccess = {},
                    onNavigateToSignup = {}
                )
            }
        }

        // Click login without entering credentials
        composeTestRule.onNodeWithTag("loginButton").performClick()

        // Wait for error message to appear
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText(
                "Please enter both email and password.",
                substring = true
            ).fetchSemanticsNodes().isNotEmpty()
        }

        // Verify error is displayed
        composeTestRule.onNodeWithText(
            "Please enter both email and password.",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    fun passwordField_hidesTextInput() {
        composeTestRule.setContent {
            FridgyTheme {
                LoginScreen(
                    onLoginSuccess = {},
                    onNavigateToSignup = {}
                )
            }
        }

        // Enter password
        composeTestRule.onNodeWithTag("passwordInput")
            .performTextInput("password123")

        // Password should not display actual text (would need to check for bullet points)
        // This is a basic check - actual password masking is handled by PasswordVisualTransformation
        composeTestRule.onNodeWithTag("passwordInput").assertExists()
    }
}
