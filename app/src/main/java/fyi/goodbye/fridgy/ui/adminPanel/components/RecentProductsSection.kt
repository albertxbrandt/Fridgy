package fyi.goodbye.fridgy.ui.adminPanel.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue

@Composable
fun RecentProductsSection(
    products: List<Product>,
    onEditProduct: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.recent_products),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = FridgyDarkBlue,
            modifier = Modifier.padding(top = 16.dp)
        )

        products.take(10).forEach { product ->
            ProductListItem(
                product = product,
                onEdit = { onEditProduct(product) },
                onDelete = { onDeleteProduct(product) }
            )
        }
    }
}

@Preview(showBackground = false)
@Composable
fun RecentProductsSectionPreview() {
    RecentProductsSection(
        products = listOf(
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
