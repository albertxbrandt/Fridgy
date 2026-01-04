package fyi.goodbye.fridgy.ui.adminPanel.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.theme.FridgyTheme

/**
 * Content displayed when a non-admin user attempts to access the admin panel.
 *
 * Shows a lock icon with an unauthorized access message. This is a fallback
 * state that shouldn't normally be seen since the admin panel button is only
 * visible to admin users.
 */
@Composable
fun UnauthorizedAccessContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.unauthorized_access),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.no_admin_privileges),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UnauthorizedAccessContentPreview() {
    FridgyTheme {
        UnauthorizedAccessContent()
    }
}
