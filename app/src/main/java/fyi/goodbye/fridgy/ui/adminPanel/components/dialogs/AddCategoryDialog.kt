package fyi.goodbye.fridgy.ui.adminPanel.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fyi.goodbye.fridgy.R

/**
 * Dialog for adding a new product category in the admin panel.
 *
 * Provides text fields for entering the category name and sort order.
 * Lower sort order numbers appear first in the category list.
 *
 * @param onDismiss Callback invoked when the dialog is dismissed
 * @param onConfirm Callback invoked with the category name and order when confirmed
 */
@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, order: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var orderText by remember { mutableStateOf("999") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_category)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = orderText,
                    onValueChange = { orderText = it },
                    label = { Text(stringResource(R.string.sort_order)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.lower_numbers_first),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val order = orderText.toIntOrNull() ?: 999
                    onConfirm(name, order)
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
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
fun AddCategoryDialogPreview() {
    AddCategoryDialog(
        onDismiss = {},
        onConfirm = { _, _ -> }
    )
}
