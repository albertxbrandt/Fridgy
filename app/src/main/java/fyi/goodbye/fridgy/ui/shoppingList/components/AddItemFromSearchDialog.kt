package fyi.goodbye.fridgy.ui.shoppingList.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.ui.shared.components.SquaredButton
import fyi.goodbye.fridgy.ui.shared.components.SquaredInput

/**
 * Dialog for adding a searched product to the shopping list with quantity and store.
 * 
 * Shown when user clicks "Add" on a product search result. Allows specifying:
 * - Quantity needed (with +/- picker)
 * - Store where to buy (optional text field)
 * 
 * @param product The product to be added to shopping list
 * @param onDismiss Callback when dialog is cancelled
 * @param onConfirm Callback with (quantity, store) when Add button is pressed
 */
@Composable
fun AddItemFromSearchDialog(
    product: Product,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var quantity by remember { mutableIntStateOf(1) }
    var store by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                if (product.name.isNotEmpty()) "Add ${product.name}" 
                else "Add Scanned Item"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.quantity))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            enabled = quantity > 1
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.decrease_quantity))
                        }
                        Text(
                            text = quantity.toString(),
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = { quantity++ }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.increase_quantity))
                        }
                    }
                }
                
                SquaredInput(
                    value = store,
                    onValueChange = { store = it },
                    label = { Text(stringResource(R.string.store_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            SquaredButton(
                onClick = { onConfirm(quantity, store.trim()) }
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
