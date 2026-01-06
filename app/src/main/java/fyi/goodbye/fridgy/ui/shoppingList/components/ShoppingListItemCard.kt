package fyi.goodbye.fridgy.ui.shoppingList.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
import fyi.goodbye.fridgy.ui.viewmodels.ShoppingListViewModel

/**
 * Card displaying a single item in the shopping list.
 * 
 * Shows product information including name, brand (if available), store location (if specified),
 * quantity, and provides controls to check off or delete the item.
 * 
 * **Visual Features:**
 * - Checked items display with strikethrough text decoration
 * - Brand and store information shown as secondary text when available
 * - Quantity always displayed
 * - Delete button with icon for quick removal
 * - Checkbox for marking items as purchased
 * 
 * @param itemWithProduct Combined data containing the shopping list item and associated product details
 * @param onCheckedChange Callback invoked when the checkbox is toggled (checked/unchecked)
 * @param onDeleteClick Callback invoked when delete button is pressed to remove item from list
 */
@Composable
fun ShoppingListItemCard(
    itemWithProduct: ShoppingListViewModel.ShoppingListItemWithProduct,
    onCheckedChange: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val item = itemWithProduct.item
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FridgyWhite),
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FridgyDarkBlue,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
                )
                if (itemWithProduct.productBrand.isNotEmpty()) {
                    Text(
                        text = itemWithProduct.productBrand,
                        fontSize = 14.sp,
                        color = FridgyDarkBlue.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (item.store.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.store_label, item.store),
                        fontSize = 14.sp,
                        color = FridgyDarkBlue.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.quantity_label_shopping, item.quantity),
                    fontSize = 14.sp,
                    color = FridgyDarkBlue.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_remove_item),
                    tint = FridgyDarkBlue.copy(alpha = 0.6f)
                )
            }

            Checkbox(
                checked = item.checked,
                onCheckedChange = { onCheckedChange() },
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = FridgyDarkBlue,
                        uncheckedColor = FridgyDarkBlue.copy(alpha = 0.6f)
                    )
            )
        }
    }
}
