package fyi.goodbye.fridgy.ui.shoppingList.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.shared.components.SquaredButton
import fyi.goodbye.fridgy.ui.shared.components.SquaredInput

/**
 * Dialog for manually adding an item to the shopping list.
 * 
 * This dialog is shown when users can't find a product in the search results and need to
 * add a custom item. The item will be added with a generic name (not linked to the products
 * database) along with the specified quantity.
 * 
 * **Input Fields:**
 * - Item Name: Required text field for the product/item name
 * - Quantity: Numeric field defaulting to 1
 * 
 * **Validation:**
 * - Item name must not be blank to enable Add button
 * - Quantity falls back to 1 if invalid input provided
 * 
 * @param fridgeId The ID of the fridge (currently unused but available for future features)
 * @param onDismiss Callback invoked when dialog is cancelled or dismissed
 * @param onScanClick Callback to switch to barcode scanning (currently unused)
 * @param onAddManual Callback invoked with (itemName, quantity) when Add button is pressed
 */
@Composable
fun AddShoppingListItemDialog(
    fridgeId: String,
    onDismiss: () -> Unit,
    onScanClick: () -> Unit,
    onAddManual: (String, Int) -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_item_manually)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SquaredInput(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text(stringResource(R.string.item_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SquaredInput(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(R.string.quantity)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            SquaredButton(
                onClick = {
                    if (itemName.isNotBlank()) {
                        onAddManual(itemName.trim(), quantity.toIntOrNull() ?: 1)
                    }
                },
                enabled = itemName.isNotBlank()
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
