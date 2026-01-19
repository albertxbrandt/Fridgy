package fyi.goodbye.fridgy.ui.elements

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fyi.goodbye.fridgy.models.SizeUnit

/**
 * Dialog for selecting size and unit for an item.
 * 
 * @param productName The name of the product
 * @param onSizeSelected Called when user confirms with size/unit (null for both means skip)
 * @param onDismiss Called when user cancels
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SizeSelectionDialog(
    productName: String,
    onSizeSelected: (size: Double?, unit: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var sizeText by remember { mutableStateOf("1") }
    var selectedUnit by remember { mutableStateOf<SizeUnit?>(null) }
    var showUnitDropdown by remember { mutableStateOf(false) }
    var customUnit by remember { mutableStateOf("") }
    var showCustomUnitInput by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Add Size/Unit?",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = productName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Size input
                OutlinedTextField(
                    value = sizeText,
                    onValueChange = { sizeText = it },
                    label = { Text("Size/Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Unit dropdown
                ExposedDropdownMenuBox(
                    expanded = showUnitDropdown,
                    onExpandedChange = { showUnitDropdown = it }
                ) {
                    OutlinedTextField(
                        value = when {
                            showCustomUnitInput -> customUnit
                            selectedUnit != null -> selectedUnit!!.displayName
                            else -> ""
                        },
                        onValueChange = { 
                            if (showCustomUnitInput) {
                                customUnit = it
                            }
                        },
                        readOnly = !showCustomUnitInput,
                        label = { Text("Unit") },
                        trailingIcon = {
                            if (!showCustomUnitInput) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showUnitDropdown)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true,
                        enabled = !showCustomUnitInput || selectedUnit == SizeUnit.OTHER
                    )

                    ExposedDropdownMenu(
                        expanded = showUnitDropdown,
                        onDismissRequest = { showUnitDropdown = false }
                    ) {
                        SizeUnit.entries.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.displayName) },
                                onClick = {
                                    selectedUnit = unit
                                    showUnitDropdown = false
                                    showCustomUnitInput = (unit == SizeUnit.OTHER)
                                    if (unit != SizeUnit.OTHER) {
                                        customUnit = ""
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    TextButton(
                        onClick = { onSizeSelected(null, null) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Skip")
                    }

                    Button(
                        onClick = {
                            val size = sizeText.toDoubleOrNull()
                            val unit = when {
                                showCustomUnitInput && customUnit.isNotBlank() -> customUnit
                                selectedUnit != null && selectedUnit != SizeUnit.OTHER -> selectedUnit!!.name
                                else -> null
                            }
                            
                            if (size != null && unit != null) {
                                onSizeSelected(size, unit)
                            } else {
                                // If invalid, just skip
                                onSizeSelected(null, null)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Set")
                    }
                }
            }
        }
    }
}
