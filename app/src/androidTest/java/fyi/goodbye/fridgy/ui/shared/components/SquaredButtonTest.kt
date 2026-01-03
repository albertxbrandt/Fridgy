package fyi.goodbye.fridgy.ui.shared.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import org.junit.Rule
import org.junit.Test

class SquaredButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun squaredButton_displaysText() {
        composeTestRule.setContent {
            FridgyTheme {
                SquaredButton(onClick = {}) {
                    androidx.compose.material3.Text("Click Me")
                }
            }
        }

        composeTestRule.onNodeWithText("Click Me").assertExists()
    }

    @Test
    fun squaredButton_isClickable() {
        var clicked = false

        composeTestRule.setContent {
            FridgyTheme {
                SquaredButton(onClick = { clicked = true }) {
                    androidx.compose.material3.Text("Click Me")
                }
            }
        }

        composeTestRule.onNodeWithText("Click Me").performClick()
        assert(clicked)
    }

    @Test
    fun squaredButton_canBeDisabled() {
        composeTestRule.setContent {
            FridgyTheme {
                SquaredButton(
                    onClick = {},
                    enabled = false
                ) {
                    androidx.compose.material3.Text("Disabled Button")
                }
            }
        }

        composeTestRule.onNodeWithText("Disabled Button")
            .assertExists()
            .assertIsNotEnabled()
    }

    @Test
    fun squaredButton_canBeEnabled() {
        composeTestRule.setContent {
            FridgyTheme {
                SquaredButton(
                    onClick = {},
                    enabled = true
                ) {
                    androidx.compose.material3.Text("Enabled Button")
                }
            }
        }

        composeTestRule.onNodeWithText("Enabled Button")
            .assertExists()
            .assertIsEnabled()
    }
}
