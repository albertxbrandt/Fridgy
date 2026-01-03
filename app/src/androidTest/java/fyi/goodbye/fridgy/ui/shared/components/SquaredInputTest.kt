package fyi.goodbye.fridgy.ui.shared.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import org.junit.Rule
import org.junit.Test

class SquaredInputTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun squaredInput_displaysLabel() {
        composeTestRule.setContent {
            FridgyTheme {
                SquaredInput(
                    value = "",
                    onValueChange = {},
                    label = "Email"
                )
            }
        }

        composeTestRule.onNodeWithText("Email").assertExists()
    }

    @Test
    fun squaredInput_acceptsTextInput() {
        var inputValue = ""

        composeTestRule.setContent {
            FridgyTheme {
                SquaredInput(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = "Username"
                )
            }
        }

        composeTestRule.onNodeWithText("Username")
            .performTextInput("testuser")

        assert(inputValue == "testuser")
    }

    @Test
    fun squaredInput_canBePassword() {
        composeTestRule.setContent {
            FridgyTheme {
                SquaredInput(
                    value = "",
                    onValueChange = {},
                    label = "Password",
                    isPassword = true
                )
            }
        }

        composeTestRule.onNodeWithText("Password").assertExists()
    }

    @Test
    fun squaredInput_displaysPlaceholder() {
        composeTestRule.setContent {
            FridgyTheme {
                SquaredInput(
                    value = "",
                    onValueChange = {},
                    label = "Email",
                    placeholder = "Enter your email"
                )
            }
        }

        composeTestRule.onNodeWithText("Enter your email").assertExists()
    }

    @Test
    fun squaredInput_clearsPlaceholderWhenTyping() {
        var inputValue = ""

        composeTestRule.setContent {
            FridgyTheme {
                SquaredInput(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = "Email",
                    placeholder = "Enter your email"
                )
            }
        }

        composeTestRule.onNodeWithText("Email")
            .performTextInput("test@example.com")

        // Placeholder should not be visible when there's text
        composeTestRule.onNodeWithText("Enter your email").assertDoesNotExist()
    }
}
