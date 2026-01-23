package fyi.goodbye.fridgy.ui.adminPanel.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.models.AdminUserDisplay
import fyi.goodbye.fridgy.models.Category
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.ui.adminPanel.components.sections.*
import fyi.goodbye.fridgy.ui.shared.UiState

/**
 * Main content layout for the admin panel when data is successfully loaded.
 *
 * Displays all admin panel sections in a scrollable LazyColumn:
 * - System statistics (users, products, fridges counts)
 * - Recent users list with management actions
 * - Recent products list with management actions
 * - Categories list with management actions
 *
 * @param totalUsers Total number of registered users in the system
 * @param totalProducts Total number of products in the crowdsourced database
 * @param totalFridges Total number of fridges created across all users
 * @param users List of users to display in the recent users section
 * @param products List of products to display in the recent products section
 * @param categoryState Current state of category loading
 * @param onEditUser Callback for editing a user
 * @param onDeleteUser Callback for deleting a user
 * @param onEditProduct Callback for editing a product
 * @param onDeleteProduct Callback for deleting a product
 * @param onAddCategory Callback for adding a new category
 * @param onEditCategory Callback for editing a category
 * @param onDeleteCategory Callback for deleting a category
 */
@Composable
fun AdminSuccessContent(
    totalUsers: Int,
    totalProducts: Int,
    totalFridges: Int,
    users: List<AdminUserDisplay>,
    products: List<Product>,
    categoryState: UiState<List<Category>>,
    onEditUser: (AdminUserDisplay) -> Unit,
    onDeleteUser: (AdminUserDisplay) -> Unit,
    onEditProduct: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Statistics Cards
        item(key = "statistics") {
            SystemStatisticsSection(
                totalUsers = totalUsers,
                totalProducts = totalProducts,
                totalFridges = totalFridges
            )
        }

        // Users Section
        item(key = "users") {
            RecentUsersSection(
                users = users,
                onEditUser = onEditUser,
                onDeleteUser = onDeleteUser
            )
        }

        // Products Section
        item(key = "products") {
            RecentProductsSection(
                products = products,
                onEditProduct = onEditProduct,
                onDeleteProduct = onDeleteProduct
            )
        }

        // Categories Section
        item(key = "categories") {
            CategoriesSection(
                categoryState = categoryState,
                onAddCategory = onAddCategory,
                onEditCategory = onEditCategory,
                onDeleteCategory = onDeleteCategory
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AdminSuccessContentPreview() {
    AdminSuccessContent(
        totalUsers = 150,
        totalProducts = 1200,
        totalFridges = 75,
        users =
            listOf(
                AdminUserDisplay(
                    uid = "user1",
                    username = "johndoe",
                    email = "johndoe@example.com",
                    createdAt = System.currentTimeMillis()
                )
            ),
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
        categoryState =
            UiState.Success(
                data =
                    listOf(
                        Category(id = "1", name = "Dairy", order = 1),
                        Category(id = "2", name = "Vegetables", order = 2),
                        Category(id = "3", name = "Fruits", order = 3)
                    )
            ),
        onEditUser = {},
        onDeleteUser = {},
        onEditProduct = {},
        onDeleteProduct = {},
        onAddCategory = {},
        onEditCategory = {},
        onDeleteCategory = {}
    )
}
