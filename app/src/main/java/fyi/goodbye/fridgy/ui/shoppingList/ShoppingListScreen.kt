package fyi.goodbye.fridgy.ui.shoppingList

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.shoppingList.components.AddShoppingListItemDialog
import fyi.goodbye.fridgy.ui.shoppingList.components.ProductSearchResultCard
import fyi.goodbye.fridgy.ui.shoppingList.components.ShoppingListItemCard
import fyi.goodbye.fridgy.ui.shared.components.SearchBar
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
import fyi.goodbye.fridgy.ui.viewmodels.ShoppingListViewModel

/**
 * Screen displaying the shopping list for a fridge.
 * 
 * This screen allows users to:
 * - View all items on their shopping list with real-time updates
 * - Check off items as they're purchased
 * - Search for products to add from the crowdsourced database
 * - Add items manually if not found in the database
 * - Scan barcodes to quickly add products
 * - Remove items from the shopping list
 * 
 * **Features:**
 * - Real-time synchronization with Firestore
 * - Barcode scanning integration via navigation
 * - Product search with dynamic results from global products collection
 * - Manual item creation for products not in database
 * - Visual indication of checked/completed items (strikethrough)
 * 
 * **Keyboard Handling:**
 * Uses `adjustNothing` with manual `imePadding()` and `consumeWindowInsets()` to prevent
 * extra padding above keyboard while maintaining proper content visibility.
 * 
 * @param fridgeId The ID of the fridge whose shopping list is being displayed
 * @param onBackClick Callback invoked when back button is pressed
 * @param onScanClick Callback to navigate to barcode scanner, receives fridgeId
 * @param navController Navigation controller for handling barcode scan results via savedStateHandle
 * @param viewModel ViewModel managing shopping list state and operations, auto-provided with factory
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    fridgeId: String,
    onBackClick: () -> Unit,
    onScanClick: (String) -> Unit,
    navController: NavController,
    viewModel: ShoppingListViewModel = viewModel(factory = ShoppingListViewModel.provideFactory(fridgeId))
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    // Handle barcode scan result from BarcodeScannerScreen
    LaunchedEffect(navController.currentBackStackEntry) {
        navController.currentBackStackEntry?.savedStateHandle?.get<String>("scannedUpc")?.let { scannedUpc ->
            // Add scanned item to shopping list with default quantity of 1 and empty store
            viewModel.addItem(scannedUpc, 1, "")
            // Clear the scanned result from saved state
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("scannedUpc")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.shopping_list),
                        color = FridgyWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = FridgyWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FridgyDarkBlue)
            )
        },
        containerColor = FridgyWhite
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
        ) {
            Column {
                // Search Bar
            SearchBar(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = stringResource(R.string.search_products),
                onClearClick = { viewModel.updateSearchQuery("") },
                onScanClick = { onScanClick(fridgeId) }
            )

            // Show search results or shopping list
            if (searchQuery.isNotEmpty()) {
                // Search Results
                if (searchResults.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_products_found),
                            fontSize = 16.sp,
                            color = FridgyDarkBlue.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            TextButton(
                                onClick = { showAddDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.cant_find_product),
                                        fontSize = 14.sp,
                                        color = FridgyDarkBlue.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.click_add_manually),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                        items(searchResults, key = { it.upc }) { product ->
                            ProductSearchResultCard(
                                product = product,
                                onAddClick = {
                                    viewModel.addItem(product.upc, 1, "")
                                    viewModel.updateSearchQuery("")
                                }
                            )
                        }
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                TextButton(
                                    onClick = { showAddDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "Can't find what you're looking for?",
                                            fontSize = 14.sp,
                                            color = FridgyDarkBlue.copy(alpha = 0.8f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Click here to add a product manually",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            } else {
                // Shopping List Content
                when (val state = uiState) {
                    is ShoppingListViewModel.UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is ShoppingListViewModel.UiState.Success -> {
                        if (state.items.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_items_in_shopping_list),
                                    fontSize = 16.sp,
                                    color = FridgyDarkBlue.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item { Spacer(modifier = Modifier.height(4.dp)) }
                                items(state.items, key = { it.item.upc }) { itemWithProduct ->
                                    ShoppingListItemCard(
                                        itemWithProduct = itemWithProduct,
                                        onCheckedChange = {
                                            viewModel.toggleItemChecked(
                                                itemWithProduct.item.upc,
                                                itemWithProduct.item.checked
                                            )
                                        },
                                        onDeleteClick = { viewModel.removeItem(itemWithProduct.item.upc) }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(8.dp)) }
                            }
                        }
                    }
                    is ShoppingListViewModel.UiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
            }
        }
    }

    // Add Item Dialog
    if (showAddDialog) {
        AddShoppingListItemDialog(
            fridgeId = fridgeId,
            onDismiss = { showAddDialog = false },
            onScanClick = {
                showAddDialog = false
                onScanClick(fridgeId)
            },
            onAddManual = { name, quantity ->
                viewModel.addManualItem(name, quantity)
                showAddDialog = false
            }
        )
    }
}
