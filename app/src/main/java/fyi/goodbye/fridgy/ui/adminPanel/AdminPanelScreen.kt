package fyi.goodbye.fridgy.ui.adminPanel

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.AdminUserDisplay
import fyi.goodbye.fridgy.models.Category
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.ui.adminPanel.components.AdminSuccessContent
import fyi.goodbye.fridgy.ui.adminPanel.components.UnauthorizedAccessContent
import fyi.goodbye.fridgy.ui.adminPanel.components.dialogs.*
import fyi.goodbye.fridgy.ui.shared.CategoryViewModel
import fyi.goodbye.fridgy.ui.shared.UiState
import fyi.goodbye.fridgy.ui.shared.components.ErrorState
import fyi.goodbye.fridgy.ui.shared.components.LoadingState
import fyi.goodbye.fridgy.ui.theme.FridgyTheme

/**
 * Admin panel screen that displays system-wide statistics and data.
 * Only accessible to users with admin privileges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminPanelViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val categoryState by categoryViewModel.uiState.collectAsState()

    Log.d("AdminPanelScreen", "Current UI state: ${uiState::class.simpleName}")

    // State for dialogs
    var userToEdit by remember { mutableStateOf<AdminUserDisplay?>(null) }
    var userToDelete by remember { mutableStateOf<AdminUserDisplay?>(null) }
    var productToEdit by remember { mutableStateOf<Product?>(null) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    Log.d(
        "AdminPanelScreen",
        "Dialog states - userToEdit: ${userToEdit != null}, productToEdit: ${productToEdit != null}, userToDelete: ${userToDelete != null}"
    )

    AdminPanelScreenContent(
        uiState = uiState,
        categoryState = categoryState,
        onNavigateBack = onNavigateBack,
        onRefresh = { viewModel.refresh() },
        onEditUser = { userToEdit = it },
        onDeleteUser = { userToDelete = it },
        onEditProduct = { productToEdit = it },
        onDeleteProduct = { productToDelete = it },
        onAddCategory = { showAddCategoryDialog = true },
        onEditCategory = { categoryToEdit = it },
        onDeleteCategory = { categoryToDelete = it }
    )

    // Dialogs
    userToEdit?.let { user ->
        EditUserDialog(
            user = user,
            onDismiss = { userToEdit = null },
            onConfirm = { username, email ->
                viewModel.updateUser(user.uid, username, email)
                userToEdit = null
            }
        )
    }

    userToDelete?.let { user ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.delete_user),
            message = stringResource(R.string.delete_user_confirmation, user.username),
            onDismiss = { userToDelete = null },
            onConfirm = {
                viewModel.deleteUser(user.uid)
                userToDelete = null
            }
        )
    }

    productToEdit?.let { product ->
        // Get categories from categoryState
        val categories =
            when (val state = categoryState) {
                is CategoryViewModel.CategoryUiState.Success -> state.categories
                else -> emptyList()
            }

        EditProductDialog(
            product = product,
            categories = categories,
            onDismiss = { productToEdit = null },
            onConfirm = { name, brand, category ->
                viewModel.updateProduct(product.upc, name, brand, category)
                productToEdit = null
            }
        )
    }

    productToDelete?.let { product ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.delete_product),
            message = stringResource(R.string.delete_product_confirmation, product.name),
            onDismiss = { productToDelete = null },
            onConfirm = {
                viewModel.deleteProduct(product.upc)
                productToDelete = null
            }
        )
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name, order ->
                categoryViewModel.createCategory(name, order)
                showAddCategoryDialog = false
            }
        )
    }

    categoryToEdit?.let { category ->
        EditCategoryDialog(
            category = category,
            onDismiss = { categoryToEdit = null },
            onConfirm = { name, order ->
                categoryViewModel.updateCategory(category.id, name, order)
                categoryToEdit = null
            }
        )
    }

    categoryToDelete?.let { category ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.delete_category),
            message = stringResource(R.string.delete_category_confirmation, category.name),
            onDismiss = { categoryToDelete = null },
            onConfirm = {
                categoryViewModel.deleteCategory(category.id)
                categoryToDelete = null
            }
        )
    }
}

/**
 * Stateless content composable for preview support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreenContent(
    uiState: AdminPanelViewModel.AdminUiState,
    categoryState: CategoryViewModel.CategoryUiState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onEditUser: (AdminUserDisplay) -> Unit,
    onDeleteUser: (AdminUserDisplay) -> Unit,
    onEditProduct: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_panel)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
            )
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            when (val state = uiState) {
                AdminPanelViewModel.AdminUiState.Loading -> {
                    Log.d("AdminPanelScreen", "Rendering Loading state")
                    LoadingState(color = MaterialTheme.colorScheme.primary)
                }

                AdminPanelViewModel.AdminUiState.Unauthorized -> {
                    Log.d("AdminPanelScreen", "Rendering Unauthorized state")
                    UnauthorizedAccessContent()
                }

                is AdminPanelViewModel.AdminUiState.Error -> {
                    Log.d("AdminPanelScreen", "Rendering Error state: ${state.message}")
                    ErrorState(
                        message = stringResource(R.string.error_loading_data_prefix, state.message),
                        showIcon = true
                    )
                }

                is AdminPanelViewModel.AdminUiState.Success -> {
                    Log.d(
                        "AdminPanelScreen",
                        "Rendering Success state - users: ${state.users.size}, products: ${state.products.size}"
                    )
                    Log.d("AdminPanelScreen", "categoryState type: ${categoryState::class.simpleName}")
                    Log.d(
                        "AdminPanelScreen",
                        "totalUsers: ${state.totalUsers}, totalProducts: ${state.totalProducts}, totalFridges: ${state.totalFridges}"
                    )

                    // Convert CategoryUiState to UiState<List<Category>>
                    val categoryUiState = when (categoryState) {
                        is CategoryViewModel.CategoryUiState.Loading -> UiState.Loading
                        is CategoryViewModel.CategoryUiState.Success -> UiState.Success(categoryState.categories)
                        is CategoryViewModel.CategoryUiState.Error -> UiState.Error(categoryState.message)
                    }

                    AdminSuccessContent(
                        totalUsers = state.totalUsers,
                        totalProducts = state.totalProducts,
                        totalFridges = state.totalFridges,
                        users = state.users,
                        products = state.products,
                        categoryState = categoryUiState,
                        onEditUser = onEditUser,
                        onDeleteUser = onDeleteUser,
                        onEditProduct = onEditProduct,
                        onDeleteProduct = onDeleteProduct,
                        onAddCategory = onAddCategory,
                        onEditCategory = onEditCategory,
                        onDeleteCategory = onDeleteCategory
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Success State")
@Composable
fun AdminPanelScreenPreview() {
    FridgyTheme {
        AdminPanelScreenContent(
            uiState =
                AdminPanelViewModel.AdminUiState.Success(
                    totalUsers = 156,
                    totalProducts = 327,
                    totalFridges = 48,
                    users =
                        listOf(
                            AdminUserDisplay(
                                uid = "user1",
                                username = "johndoe",
                                email = "john@example.com",
                                createdAt = System.currentTimeMillis()
                            ),
                            AdminUserDisplay(
                                uid = "user2",
                                username = "janedoe",
                                email = "jane@example.com",
                                createdAt = System.currentTimeMillis()
                            )
                        ),
                    products =
                        listOf(
                            Product(
                                upc = "123456789",
                                name = "Organic Milk",
                                brand = "Organic Valley",
                                category = "Dairy",
                                imageUrl = "",
                                lastUpdated = System.currentTimeMillis()
                            ),
                            Product(
                                upc = "987654321",
                                name = "Whole Wheat Bread",
                                brand = "Nature's Own",
                                category = "Bakery",
                                imageUrl = "",
                                lastUpdated = System.currentTimeMillis()
                            )
                        ),
                    fridges = emptyList()
                ),
            categoryState =
                CategoryViewModel.CategoryUiState.Success(
                    categories =
                        listOf(
                            Category(id = "1", name = "Dairy", order = 1),
                            Category(id = "2", name = "Bakery", order = 2),
                            Category(id = "3", name = "Produce", order = 3)
                        )
                ),
            onNavigateBack = {},
            onRefresh = {},
            onEditUser = {},
            onDeleteUser = {},
            onEditProduct = {},
            onDeleteProduct = {},
            onAddCategory = {},
            onEditCategory = {},
            onDeleteCategory = {}
        )
    }
}

@Preview(showBackground = true, name = "Loading State")
@Composable
fun AdminPanelScreenLoadingPreview() {
    FridgyTheme {
        AdminPanelScreenContent(
            uiState = AdminPanelViewModel.AdminUiState.Loading,
            categoryState = CategoryViewModel.CategoryUiState.Loading,
            onNavigateBack = {},
            onRefresh = {},
            onEditUser = {},
            onDeleteUser = {},
            onEditProduct = {},
            onDeleteProduct = {},
            onAddCategory = {},
            onEditCategory = {},
            onDeleteCategory = {}
        )
    }
}

@Preview(showBackground = true, name = "Unauthorized State")
@Composable
fun AdminPanelScreenUnauthorizedPreview() {
    FridgyTheme {
        AdminPanelScreenContent(
            uiState = AdminPanelViewModel.AdminUiState.Unauthorized,
            categoryState = CategoryViewModel.CategoryUiState.Loading,
            onNavigateBack = {},
            onRefresh = {},
            onEditUser = {},
            onDeleteUser = {},
            onEditProduct = {},
            onDeleteProduct = {},
            onAddCategory = {},
            onEditCategory = {},
            onDeleteCategory = {}
        )
    }
}

@Preview(showBackground = true, name = "Error State")
@Composable
fun AdminPanelScreenErrorPreview() {
    FridgyTheme {
        AdminPanelScreenContent(
            uiState = AdminPanelViewModel.AdminUiState.Error("Failed to load admin data. Please try again."),
            categoryState = CategoryViewModel.CategoryUiState.Error("Failed to load categories"),
            onNavigateBack = {},
            onRefresh = {},
            onEditUser = {},
            onDeleteUser = {},
            onEditProduct = {},
            onDeleteProduct = {},
            onAddCategory = {},
            onEditCategory = {},
            onDeleteCategory = {}
        )
    }
}
