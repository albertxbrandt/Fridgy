package fyi.goodbye.fridgy.ui.adminPanel.components.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fyi.goodbye.fridgy.R

/**
 * A reusable confirmation dialog for delete operations in the admin panel.
 *
 * Displays a customizable title and message with Cancel and Delete buttons.
 * The Delete button is styled with error colors to indicate a destructive action.
 *
 * @param title The dialog title (e.g., "Delete User")
 * @param message The confirmation message explaining what will be deleted
 * @param onDismiss Callback invoked when the dialog is dismissed or cancelled
 * @param onConfirm Callback invoked when the delete action is confirmed
 */
@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Preview
@Composable
fun DeleteConfirmationDialogPreview() {
    DeleteConfirmationDialog(
        title = "Delete User",
        message = "Are you sure you want to delete this user? This action cannot be undone.",
        onDismiss = {},
        onConfirm = {}
    )
}
