package fyi.goodbye.fridgy.ui.shared.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable empty state component that displays a centered message when no content is available.
 * Used across multiple screens to show empty states consistently.
 *
 * @param message The message to display
 * @param modifier Modifier to apply to the container Column
 * @param showIcon Whether to show an icon above the message
 * @param icon The icon to display (defaults to Info icon)
 * @param iconSize Size of the icon
 * @param fontSize Size of the text
 * @param color Color of the text and icon
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = false,
    icon: ImageVector = Icons.Default.Info,
    iconSize: Dp = 48.dp,
    fontSize: TextUnit = 18.sp,
    color: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
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
            fontSize = fontSize,
            color = color,
            textAlign = TextAlign.Center,
            lineHeight = (fontSize.value * 1.33).sp
        )
    }
}
