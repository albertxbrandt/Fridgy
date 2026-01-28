package fyi.goodbye.fridgy.ui.shoppingList.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Fridge

/**
 * Dialog for marking partial pickup of a shopping list item.
 *
 * When users check off an item, this dialog allows them to specify how many
 * units they actually obtained and which fridge they will place them in.
 * If the obtained quantity equals the requested quantity, the item is fully checked off.
 * Otherwise, it shows partial status.
 *
 * **Features:**
 * - Quantity picker with +/- buttons
 * - Fridge selection dropdown
 * - Displays "Picked up X of Y" format
 * - Defaults to full quantity (mark as complete)
 *
 * @param itemName The display name of the item being picked up
 * @param requestedQuantity Total quantity that was requested
 * @param currentObtained Current obtained quantity (for editing), defaults to 0
 * @param availableFridges List of fridges in the household to choose from
 * @param currentTargetFridgeId Currently selected fridge ID (for editing)
 * @param onDismiss Callback when dialog is cancelled
 * @param onConfirm Callback with the obtained quantity and target fridge ID when confirmed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartialPickupDialog(
    itemName: String,
    requestedQuantity: Int,
    currentObtained: Int = 0,
    availableFridges: List<Fridge> = emptyList(),
    currentTargetFridgeId: String = "",
    onDismiss: () -> Unit,
    onConfirm: (obtainedQuantity: Int, targetFridgeId: String) -> Unit
) {
    var obtainedQuantity by remember {
        mutableIntStateOf(if (currentObtained > 0) currentObtained else requestedQuantity)
    }
    var selectedFridgeId by remember {
        mutableStateOf(currentTargetFridgeId.ifEmpty { availableFridges.firstOrNull()?.id ?: "" })
    }
    var expandedFridgeDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
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

                // Fridge Selection Dropdown
                if (availableFridges.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.which_fridge),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        ExposedDropdownMenuBox(
                            expanded = expandedFridgeDropdown,
                            onExpandedChange = { expandedFridgeDropdown = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = availableFridges.find { it.id == selectedFridgeId }?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = expandedFridgeDropdown
                                    )
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                colors = OutlinedTextFieldDefaults.colors(),
                                shape = MaterialTheme.shapes.medium
                            )

                            ExposedDropdownMenu(
                                expanded = expandedFridgeDropdown,
                                onDismissRequest = { expandedFridgeDropdown = false }
                            ) {
                                availableFridges.forEach { fridge ->
                                    DropdownMenuItem(
                                        text = { Text(fridge.name) },
                                        onClick = {
                                            selectedFridgeId = fridge.id
                                            expandedFridgeDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(obtainedQuantity, selectedFridgeId) },
                enabled = obtainedQuantity > 0 && (availableFridges.isEmpty() || selectedFridgeId.isNotEmpty())
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}
