package fyi.goodbye.fridgy.ui.shared.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Reusable loading state component that displays a centered circular progress indicator.
 * Used across multiple screens to show loading state consistently.
 *
 * @param modifier Modifier to apply to the container Box
 * @param color Color of the progress indicator
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (color != Color.Unspecified) {
            CircularProgressIndicator(color = color)
        } else {
            CircularProgressIndicator()
        }
    }
}
