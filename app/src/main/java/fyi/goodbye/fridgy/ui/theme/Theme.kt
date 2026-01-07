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
        primaryContainer = Color(0xFF1E3A8A),
        onPrimaryContainer = Color(0xFFDEEAFF),
        secondary = FridgySecondary,
        onSecondary = FridgyOnSecondary,
        secondaryContainer = Color(0xFF334155),
        onSecondaryContainer = FridgySecondaryContainer,
        tertiary = FridgyTertiary,
        onTertiary = FridgyOnTertiary,
        tertiaryContainer = Color(0xFF312E81),
        onTertiaryContainer = FridgyTertiaryContainer,
        background = Color(0xFF0F172A),
        onBackground = Color(0xFFE2E8F0),
        surface = Color(0xFF1E293B),
        onSurface = Color(0xFFE2E8F0),
        surfaceVariant = Color(0xFF334155),
        onSurfaceVariant = Color(0xFFCBD5E1),
        error = FridgyError,
        onError = FridgyOnError,
        errorContainer = FridgyErrorContainer,
        onErrorContainer = FridgyOnErrorContainer,
        outline = Color(0xFF64748B),
        outlineVariant = Color(0xFF475569)
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
        tertiaryContainer = FridgyTertiaryContainer,
        onTertiaryContainer = FridgyOnTertiaryContainer,
        background = FridgyBackground,
        onBackground = FridgyOnSurface,
        surface = FridgySurface,
        onSurface = FridgyOnSurface,
        surfaceVariant = FridgySurfaceVariant,
        onSurfaceVariant = FridgyOnSurfaceVariant,
        error = FridgyError,
        onError = FridgyOnError,
        errorContainer = FridgyErrorContainer,
        onErrorContainer = FridgyOnErrorContainer,
        outline = FridgyOutline,
        outlineVariant = FridgyOutlineVariant
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
        shapes = FridgyShapes,
        content = content
    )
}
