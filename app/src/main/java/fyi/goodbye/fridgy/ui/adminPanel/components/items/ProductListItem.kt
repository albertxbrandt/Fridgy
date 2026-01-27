package fyi.goodbye.fridgy.ui.adminPanel.components.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Product

/**
 * A list item component for displaying product information in the admin panel.
 *
 * Shows the product's icon, name, brand/category, and provides edit/delete actions.
 * Used in the "Recent Products" section of the admin dashboard.
 *
 * @param product The product data to display (name, brand, category)
 * @param onEdit Callback invoked when the edit button is tapped
 * @param onDelete Callback invoked when the delete button is tapped
 */
@Composable
fun ProductListItem(
    product: Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AdminListItem(
        primaryText = product.name,
        secondaryText = stringResource(R.string.product_brand_category, product.brand, product.category),
        leadingIcon = Icons.Default.ShoppingCart,
        onEdit = onEdit,
        onDelete = onDelete,
        editContentDescription = stringResource(R.string.cd_edit_product),
        deleteContentDescription = stringResource(R.string.cd_delete_product)
    )
}

@Preview
@Composable
fun PreviewProductListItem() {
    ProductListItem(
        product =
            Product(
                upc = "1",
                name = "Milk",
                brand = "DairyBest",
                imageUrl = "www.google.com",
                category = "Dairy",
                lastUpdated = null
            ),
        onEdit = {},
        onDelete = {}
    )
}
