package fyi.goodbye.fridgy.ui.shared.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable error state component that displays a centered error message with optional icon.
 * Used across multiple screens to show error states consistently.
 *
 * @param message The error message to display
 * @param modifier Modifier to apply to the container Box
 * @param showIcon Whether to show an error icon above the message
 * @param icon The icon to display (defaults to Warning icon)
 * @param iconSize Size of the icon
 * @param color Color of the error text and icon
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    icon: ImageVector = Icons.Default.Warning,
    iconSize: Dp = 64.dp,
    color: Color = MaterialTheme.colorScheme.error
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showIcon) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = color
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                text = message,
                fontSize = 16.sp,
                fontWeight = if (showIcon) FontWeight.Bold else FontWeight.Normal,
                color = color,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Simple error state that only displays text without icon.
 * Useful for inline error messages in smaller spaces.
 */
@Composable
fun SimpleErrorState(
    message: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.error
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = color,
            textAlign = TextAlign.Center
        )
    }
}
