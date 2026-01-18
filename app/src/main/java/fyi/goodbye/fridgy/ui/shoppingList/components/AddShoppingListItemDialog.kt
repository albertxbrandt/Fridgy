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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.R
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
 * @param householdId The ID of the household (currently unused but available for future features)
 * @param onDismiss Callback invoked when dialog is cancelled or dismissed
 * @param onScanClick Callback to switch to barcode scanning (currently unused)
 * @param onAddManual Callback invoked with (itemName, quantity, store) when Add button is pressed
 */
@Composable
fun AddShoppingListItemDialog(
    householdId: String,
    initialItemName: String = "",
    onDismiss: () -> Unit,
    onScanClick: () -> Unit,
    onAddManual: (String, Int, String) -> Unit
) {
    var itemName by remember { mutableStateOf(initialItemName) }
    var quantity by remember { mutableIntStateOf(1) }
    var store by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.add_item_manually),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SquaredInput(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text(stringResource(R.string.item_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.quantity),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (quantity > 1) quantity-- },
                                enabled = quantity > 1
                            ) {
                                Icon(
                                    Icons.Default.Remove,
                                    contentDescription = stringResource(R.string.decrease_quantity),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = quantity.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.widthIn(min = 32.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(onClick = { quantity++ }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.increase_quantity),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
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
            FilledTonalButton(
                onClick = {
                    if (itemName.isNotBlank()) {
                        onAddManual(itemName.trim(), quantity, store.trim())
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
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    )
}
