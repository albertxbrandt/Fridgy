package fyi.goodbye.fridgy.ui.shoppingList.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.shared.components.SquaredInput

/**
 * Dialog for entering a UPC code for a manual shopping list item.
 *
 * Prompts the user to scan or manually enter a UPC code for an item that was
 * added manually to the shopping list. This allows linking the manual item to
 * an actual product in the database.
 *
 * @param itemName The name of the manual item being linked
 * @param onDismiss Callback invoked when dialog is cancelled
 * @param onScanClick Callback to open barcode scanner
 * @param onConfirm Callback invoked with UPC when entered manually
 */
@Composable
fun UpcEntryDialog(
    itemName: String,
    onDismiss: () -> Unit,
    onScanClick: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var upcInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = stringResource(R.string.link_product),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.link_product_description, itemName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SquaredInput(
                    value = upcInput,
                    onValueChange = { upcInput = it },
                    label = { Text(stringResource(R.string.upc_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                FilledTonalButton(
                    onClick = onScanClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.scan_barcode))
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(upcInput.trim()) },
                enabled = upcInput.trim().isNotEmpty()
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.skip))
            }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}
