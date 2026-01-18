package fyi.goodbye.fridgy.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color gradients for different fridge types (freezer, fridge, pantry).
 * Used in FridgeCard icon backgrounds to provide visual distinction.
 */
object FridgeTypeColors {
    /**
     * Light blue gradient for freezer type.
     * From: Light Sky Blue → Sky Blue → Deep Sky Blue
     */
    val FreezerGradient =
        listOf(
            Color(0xFF81D4FA),
            Color(0xFF4FC3F7),
            Color(0xFF29B6F6)
        )

    /**
     * Orange gradient for pantry type.
     * From: Light Orange → Orange → Dark Orange
     */
    val PantryGradient =
        listOf(
            Color(0xFFFFB74D),
            Color(0xFFFF9800),
            Color(0xFFF57C00)
        )

    /**
     * Blue gradient for fridge type (default).
     * From: Light Blue → Blue → Dark Blue
     */
    val FridgeGradient =
        listOf(
            Color(0xFF42A5F5),
            Color(0xFF1E88E5),
            Color(0xFF1565C0)
        )
}
