package fyi.goodbye.fridgy.ui.shared.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.R

/**
 * A reusable confirmation dialog for destructive or important actions.
 *
 * Provides a consistent look and feel for confirmation dialogs throughout the app,
 * with optional text confirmation for high-risk actions.
 *
 * @param title The dialog title text.
 * @param message The dialog message/description text.
 * @param confirmButtonText The text for the confirm button.
 * @param dismissButtonText The text for the dismiss button (defaults to "Cancel").
 * @param isDestructive Whether this is a destructive action (shows error color).
 * @param requiresTextConfirmation Whether user must type a confirmation word.
 * @param confirmationWord The word user must type to confirm (default: "CONFIRM").
 * @param confirmationHint Hint text for the confirmation input field.
 * @param onConfirm Callback when user confirms the action.
 * @param onDismiss Callback when user dismisses the dialog.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmButtonText: String,
    dismissButtonText: String = stringResource(R.string.cancel),
    isDestructive: Boolean = false,
    requiresTextConfirmation: Boolean = false,
    confirmationWord: String = "CONFIRM",
    confirmationHint: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = if (isDestructive) MaterialTheme.colorScheme.error else Color.Unspecified,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (requiresTextConfirmation) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = confirmationHint ?: stringResource(R.string.type_confirm),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(confirmationWord) },
                        shape = MaterialTheme.shapes.medium
                    )
                }
            } else {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            if (isDestructive) {
                Button(
                    onClick = {
                        onConfirm()
                    },
                    enabled = !requiresTextConfirmation || confirmText == confirmationWord,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                ) {
                    Text(confirmButtonText)
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        onConfirm()
                    },
                    enabled = !requiresTextConfirmation || confirmText == confirmationWord
                ) {
                    Text(confirmButtonText)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissButtonText)
            }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}

/**
 * A simplified confirmation dialog for simple yes/no confirmations.
 *
 * @param title The dialog title text.
 * @param message The dialog message/description text.
 * @param onConfirm Callback when user confirms.
 * @param onDismiss Callback when user dismisses.
 */
@Composable
fun SimpleConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmationDialog(
        title = title,
        message = message,
        confirmButtonText = stringResource(R.string.confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * A confirmation dialog styled for delete actions with error colors.
 *
 * @param title The dialog title text.
 * @param message The dialog message/description text.
 * @param requiresTextConfirmation Whether user must type "CONFIRM" to proceed.
 * @param onConfirm Callback when user confirms deletion.
 * @param onDismiss Callback when user dismisses.
 */
@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    requiresTextConfirmation: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmationDialog(
        title = title,
        message = message,
        confirmButtonText = stringResource(R.string.delete),
        isDestructive = true,
        requiresTextConfirmation = requiresTextConfirmation,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * A confirmation dialog styled for leave/exit actions with error colors.
 *
 * @param title The dialog title text.
 * @param message The dialog message/description text.
 * @param onConfirm Callback when user confirms leaving.
 * @param onDismiss Callback when user dismisses.
 */
@Composable
fun LeaveConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmationDialog(
        title = title,
        message = message,
        confirmButtonText = stringResource(R.string.leave),
        isDestructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
