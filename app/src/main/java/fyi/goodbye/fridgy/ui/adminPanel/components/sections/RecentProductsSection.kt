package fyi.goodbye.fridgy.ui.adminPanel.components.sections

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.ui.adminPanel.components.items.ProductListItem

/**
 * Section displaying recent products in the admin panel.
 *
 * Shows up to 10 of the most recently added products from the crowdsourced database.
 * Each product has edit and delete actions available.
 *
 * @param products The list of products to display (sorted by most recent)
 * @param onEditProduct Callback invoked when edit is tapped for a product
 * @param onDeleteProduct Callback invoked when delete is tapped for a product
 */
@Composable
fun RecentProductsSection(
    products: List<Product>,
    onEditProduct: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit
) {
    Log.d("RecentProductsSection", "Rendering with ${products.size} products")

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.recent_products),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp)
        )

        Log.d("RecentProductsSection", "About to iterate ${products.take(10).size} products")
        products.take(10).forEachIndexed { index, product ->
            Log.d("RecentProductsSection", "Rendering product $index: ${product.name}")
            ProductListItem(
                product = product,
                onEdit = { onEditProduct(product) },
                onDelete = { onDeleteProduct(product) }
            )
        }
        Log.d("RecentProductsSection", "Finished rendering all products")
    }
}

@Preview(showBackground = false)
@Composable
fun RecentProductsSectionPreview() {
    RecentProductsSection(
        products =
            listOf(
                Product(
                    upc = "123",
                    name = "Milk",
                    brand = "DairyBest",
                    category = "Dairy"
                ),
                Product(
                    upc = "321",
                    name = "Eggs",
                    brand = "FarmFresh",
                    category = "Poultry"
                )
            ),
        onEditProduct = {},
        onDeleteProduct = {}
    )
}
