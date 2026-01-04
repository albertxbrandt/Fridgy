package fyi.goodbye.fridgy.ui.adminPanel.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.AdminUserDisplay

/**
 * Dialog for editing user information in the admin panel.
 *
 * Pre-populates fields with the current username and email.
 * Note: This only updates the user's profile data, not their Firebase Auth credentials.
 *
 * @param user The user being edited (provides initial values)
 * @param onDismiss Callback invoked when the dialog is dismissed
 * @param onConfirm Callback invoked with updated username and email when saved
 */
@Composable
fun EditUserDialog(
    user: AdminUserDisplay,
    onDismiss: () -> Unit,
    onConfirm: (username: String, email: String) -> Unit
) {
    var username by remember { mutableStateOf(user.username) }
    var email by remember { mutableStateOf(user.email) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_user)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(username, email) },
                enabled = username.isNotBlank() && email.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Preview
@Composable
fun EditUserDialogPreview() {
    val sampleUser =
        AdminUserDisplay(
            uid = "123",
            username = "johndoe",
            email = "johndoe@example.com",
            createdAt = 1767421399
        )
    EditUserDialog(
        user = sampleUser,
        onDismiss = {},
        onConfirm = { _, _ -> }
    )
}
