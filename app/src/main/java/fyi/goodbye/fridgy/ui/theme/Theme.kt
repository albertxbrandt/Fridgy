package fyi.goodbye.fridgy.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
    darkColorScheme(
        primary = FridgyPrimary,
        onPrimary = FridgyOnPrimary,
        primaryContainer = FridgyPrimaryContainer,
        onPrimaryContainer = FridgyOnPrimaryContainer,
        secondary = FridgySecondary,
        onSecondary = FridgyOnSecondary,
        secondaryContainer = FridgySecondaryContainer,
        onSecondaryContainer = FridgyOnSecondaryContainer,
        tertiary = FridgyTertiary,
        onTertiary = FridgyOnTertiary,
        background = Color(0xFF1A1C1E),
        surface = Color(0xFF1A1C1E),
        error = FridgyError,
        onError = FridgyOnError
    )

private val LightColorScheme =
    lightColorScheme(
        primary = FridgyPrimary,
        onPrimary = FridgyOnPrimary,
        primaryContainer = FridgyPrimaryContainer,
        onPrimaryContainer = FridgyOnPrimaryContainer,
        secondary = FridgySecondary,
        onSecondary = FridgyOnSecondary,
        secondaryContainer = FridgySecondaryContainer,
        onSecondaryContainer = FridgyOnSecondaryContainer,
        tertiary = FridgyTertiary,
        onTertiary = FridgyOnTertiary,
        background = FridgyBackground,
        surface = FridgySurface,
        onSurface = FridgyOnSurface,
        surfaceVariant = FridgySurfaceVariant,
        onSurfaceVariant = FridgyOnSurfaceVariant,
        error = FridgyError,
        onError = FridgyOnError
    )

@Composable
fun FridgyTheme(
    // Forced to false to maintain light brand identity regardless of system settings
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
