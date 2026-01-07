package fyi.goodbye.fridgy.ui.shoppingList.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.viewmodels.ShoppingListViewModel

/**
 * Modern card displaying a single item in the shopping list.
 * 
 * Features clean typography, subtle elevation, and intuitive controls.
 * Shows product information including name, brand (if available), store location (if specified),
 * quantity, and provides controls to check off or delete the item.
 * 
 * **Visual Features:**
 * - Checked items display with strikethrough text decoration
 * - Brand and store information shown as secondary text when available
 * - Quantity always displayed
 * - Delete button with outlined icon for consistency
 * - Checkbox for marking items as purchased
 * 
 * @param itemWithProduct Combined data containing the shopping list item and associated product details
 * @param onCheckClick Callback invoked when checkbox is clicked to open pickup dialog
 * @param onDeleteClick Callback invoked when delete button is pressed to remove item from list
 */
@Composable
fun ShoppingListItemCard(
    itemWithProduct: ShoppingListViewModel.ShoppingListItemWithProduct,
    onCheckClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val item = itemWithProduct.item
    val isPartiallyPicked = item.obtainedQuantity != null && item.obtainedQuantity < item.quantity
    val isFullyPicked = item.obtainedQuantity != null && item.obtainedQuantity >= item.quantity
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = itemWithProduct.productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (isFullyPicked) TextDecoration.LineThrough else TextDecoration.None
                )
                if (itemWithProduct.productBrand.isNotEmpty()) {
                    Text(
                        text = itemWithProduct.productBrand,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (item.store.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.store_label, item.store),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                when {
                    isPartiallyPicked -> {
                        Text(
                            text = stringResource(R.string.picked_up_x_of_y, item.obtainedQuantity!!, item.quantity),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    !isFullyPicked -> {
                        Text(
                            text = stringResource(R.string.quantity_label_shopping, item.quantity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.cd_remove_item),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }

            Checkbox(
                checked = isFullyPicked || item.checked,
                onCheckedChange = { onCheckClick() },
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = if (isPartiallyPicked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
            )
        }
    }
}
