package fyi.goodbye.fridgy.ui.adminPanel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.AdminUserDisplay
import fyi.goodbye.fridgy.models.Category
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.ui.adminPanel.components.*
import fyi.goodbye.fridgy.ui.shared.CategoryViewModel
import fyi.goodbye.fridgy.ui.shared.components.ErrorState
import fyi.goodbye.fridgy.ui.shared.components.LoadingState
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue
import fyi.goodbye.fridgy.ui.theme.FridgyPrimary
import fyi.goodbye.fridgy.ui.theme.FridgyWhite

/**
 * Admin panel screen that displays system-wide statistics and data.
 * Only accessible to users with admin privileges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminPanelViewModel = viewModel(factory = AdminPanelViewModel.provideFactory()),
    categoryViewModel: CategoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val categoryState by categoryViewModel.uiState.collectAsState()

    // State for dialogs
    var userToEdit by remember { mutableStateOf<AdminUserDisplay?>(null) }
    var userToDelete by remember { mutableStateOf<AdminUserDisplay?>(null) }
    var productToEdit by remember { mutableStateOf<Product?>(null) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = FridgyPrimary,
                        titleContentColor = FridgyWhite,
                        navigationIconContentColor = FridgyWhite,
                        actionIconContentColor = FridgyWhite
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
                    LoadingState(color = FridgyPrimary)
                }

                AdminPanelViewModel.AdminUiState.Unauthorized -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Unauthorized Access",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "You don't have admin privileges",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is AdminPanelViewModel.AdminUiState.Error -> {
                    ErrorState(
                        message = "Error Loading Data\n${state.message}",
                        showIcon = true
                    )
                }

                is AdminPanelViewModel.AdminUiState.Success -> {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Statistics Cards
                        item {
                            Text(
                                stringResource(R.string.system_statistics),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = FridgyDarkBlue
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatCard(
                                    title = stringResource(R.string.users),
                                    value = state.totalUsers.toString(),
                                    icon = Icons.Default.Person,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = stringResource(R.string.products),
                                    value = state.totalProducts.toString(),
                                    icon = Icons.Default.ShoppingCart,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = stringResource(R.string.fridges),
                                    value = state.totalFridges.toString(),
                                    icon = Icons.Default.Home,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Users Section
                        item {
                            Text(
                                stringResource(R.string.recent_users),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = FridgyDarkBlue,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }

                        items(state.users.take(10)) { user ->
                            UserListItem(
                                user = user,
                                onEdit = { userToEdit = user },
                                onDelete = { userToDelete = user }
                            )
                        }

                        // Products Section
                        item {
                            Text(
                                stringResource(R.string.recent_products),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = FridgyDarkBlue,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }

                        items(state.products.take(10)) { product ->
                            ProductListItem(
                                product = product,
                                onEdit = { productToEdit = product },
                                onDelete = { productToDelete = product }
                            )
                        }

                        // Categories Section
                        item {
                            Row(
                                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.food_categories),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FridgyDarkBlue
                                )
                                IconButton(onClick = { showAddCategoryDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_category))
                                }
                            }
                        }

                        when (val catState = categoryState) {
                            is CategoryViewModel.CategoryUiState.Success -> {
                                items(catState.categories) { category ->
                                    CategoryListItem(
                                        category = category,
                                        onEdit = { categoryToEdit = category },
                                        onDelete = { categoryToDelete = category }
                                    )
                                }
                            }
                            is CategoryViewModel.CategoryUiState.Loading -> {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                            is CategoryViewModel.CategoryUiState.Error -> {
                                item {
                                    Text(
                                        "Error loading categories: ${catState.message}",
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // User Edit Dialog
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

    // User Delete Confirmation Dialog
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

    // Product Edit Dialog
    productToEdit?.let { product ->
        EditProductDialog(
            product = product,
            onDismiss = { productToEdit = null },
            onConfirm = { name, brand, category ->
                viewModel.updateProduct(product.upc, name, brand, category)
                productToEdit = null
            }
        )
    }

    // Product Delete Confirmation Dialog
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

    // Add Category Dialog
    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name, order ->
                categoryViewModel.createCategory(name, order)
                showAddCategoryDialog = false
            }
        )
    }

    // Category Edit Dialog
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

    // Category Delete Confirmation Dialog
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
