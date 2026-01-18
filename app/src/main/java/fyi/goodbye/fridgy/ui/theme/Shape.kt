package fyi.goodbye.fridgy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Modern shape system with consistent rounded corners throughout the app.
 *
 * - extraSmall: 8dp - Small UI elements, chips
 * - small: 12dp - Buttons, text fields
 * - medium: 16dp - Cards, dialogs
 * - large: 20dp - Large cards, bottom sheets
 * - extraLarge: 24dp - Special surfaces
 */
val FridgyShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(24.dp)
    )
