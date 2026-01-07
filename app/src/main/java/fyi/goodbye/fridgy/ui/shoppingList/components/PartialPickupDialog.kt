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
import fyi.goodbye.fridgy.ui.shared.components.SquaredButton

/**
 * Dialog for marking partial pickup of a shopping list item.
 * 
 * When users check off an item, this dialog allows them to specify how many
 * units they actually obtained. If the obtained quantity equals the requested
 * quantity, the item is fully checked off. Otherwise, it shows partial status.
 * 
 * **Features:**
 * - Quantity picker with +/- buttons
 * - Displays "Picked up X of Y" format
 * - Defaults to full quantity (mark as complete)
 * 
 * @param itemName The display name of the item being picked up
 * @param requestedQuantity Total quantity that was requested
 * @param currentObtained Current obtained quantity (for editing), defaults to 0
 * @param onDismiss Callback when dialog is cancelled
 * @param onConfirm Callback with the obtained quantity when confirmed
 */
@Composable
fun PartialPickupDialog(
    itemName: String,
    requestedQuantity: Int,
    currentObtained: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var obtainedQuantity by remember { 
        mutableIntStateOf(if (currentObtained > 0) currentObtained else requestedQuantity) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                stringResource(R.string.mark_item_picked_up),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = itemName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = stringResource(R.string.how_many_obtained),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                // Quantity Picker
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 20.dp)
                    ) {
                        IconButton(
                            onClick = { if (obtainedQuantity > 0) obtainedQuantity-- },
                            enabled = obtainedQuantity > 0
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = stringResource(R.string.decrease_quantity),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Text(
                            text = stringResource(R.string.picked_up_x_of_y, obtainedQuantity, requestedQuantity),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            textAlign = TextAlign.Center
                        )
                        
                        IconButton(
                            onClick = { if (obtainedQuantity < requestedQuantity) obtainedQuantity++ },
                            enabled = obtainedQuantity < requestedQuantity
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.increase_quantity),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(obtainedQuantity) }
            ) {
                Text(stringResource(R.string.confirm))
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
