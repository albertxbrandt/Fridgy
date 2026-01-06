package fyi.goodbye.fridgy.ui.shoppingList.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue
import fyi.goodbye.fridgy.ui.theme.FridgyWhite

/**
 * Card displaying a product search result from the global products database.
 * 
 * Used when users search for products to add to their shopping list. Shows product
 * information and provides an "Add" button to quickly add the item to the list.
 * 
 * **Display Information:**
 * - Product name (primary text)
 * - Brand (secondary text, if available)
 * - UPC barcode (tertiary text)
 * - Add button with icon
 * 
 * @param product The product data from the search results
 * @param onAddClick Callback invoked when the Add button is pressed to add product to shopping list
 */
@Composable
fun ProductSearchResultCard(
    product: Product,
    onAddClick: () -> Unit
) {
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
                    text = product.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FridgyDarkBlue
                )
                if (product.brand.isNotEmpty()) {
                    Text(
                        text = product.brand,
                        fontSize = 14.sp,
                        color = FridgyDarkBlue.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.upc_label_product, product.upc),
                    fontSize = 12.sp,
                    color = FridgyDarkBlue.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Button(
                onClick = onAddClick,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = FridgyDarkBlue,
                        contentColor = FridgyWhite
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.add))
            }
        }
    }
}
