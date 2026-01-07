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
import fyi.goodbye.fridgy.models.Category

/**
 * Dialog for editing an existing product category in the admin panel.
 *
 * Pre-populates fields with the current category name and sort order.
 * Lower sort order numbers appear first in the category list.
 *
 * @param category The category being edited (provides initial values)
 * @param onDismiss Callback invoked when the dialog is dismissed
 * @param onConfirm Callback invoked with the updated name and order when saved
 */
@Composable
fun EditCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onConfirm: (name: String, order: Int) -> Unit
) {
    var name by remember { mutableStateOf(category.name) }
    var orderText by remember { mutableStateOf(category.order.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                stringResource(R.string.edit_category),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = orderText,
                    onValueChange = { orderText = it },
                    label = { Text(stringResource(R.string.sort_order)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                Text(
                    stringResource(R.string.lower_numbers_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    val order = orderText.toIntOrNull() ?: 999
                    onConfirm(name, order)
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
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
fun EditCategoryDialogPreview() {
    val sampleCategory =
        Category(
            id = "1",
            name = "Fruits",
            order = 1
        )
    EditCategoryDialog(
        category = sampleCategory,
        onDismiss = {},
        onConfirm = { _, _ -> }
    )
}
