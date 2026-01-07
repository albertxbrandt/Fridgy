package fyi.goodbye.fridgy.ui.adminPanel.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Category
import fyi.goodbye.fridgy.models.Product

/**
 * Dialog for editing product information in the admin panel.
 *
 * Pre-populates fields with the current product name, brand, and category.
 * Category is selected from a dropdown list of available categories.
 * Part of the crowdsourced product database management.
 *
 * @param product The product being edited (provides initial values)
 * @param categories List of available categories to choose from
 * @param onDismiss Callback invoked when the dialog is dismissed
 * @param onConfirm Callback invoked with updated name, brand, and category when saved
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProductDialog(
    product: Product,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, brand: String, category: String) -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var brand by remember { mutableStateOf(product.brand) }
    var category by remember { mutableStateOf(product.category) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                stringResource(R.string.edit_product),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.product_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text(stringResource(R.string.brand_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )

                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.category)) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.sortedBy { it.name }.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    category = cat.name
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(name, brand, category) },
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
fun EditProductDialogPreview() {
    val sampleProduct =
        Product(
            upc = "1",
            name = "Sample Product",
            brand = "Sample Brand",
            category = "Dairy"
        )
    val sampleCategories =
        listOf(
            Category(id = "1", name = "Dairy", order = 1),
            Category(id = "2", name = "Meat", order = 2),
            Category(id = "3", name = "Produce", order = 3)
        )
    EditProductDialog(
        product = sampleProduct,
        categories = sampleCategories,
        onDismiss = {},
        onConfirm = { _, _, _ -> }
    )
}
