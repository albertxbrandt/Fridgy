package fyi.goodbye.fridgy.ui.shoppingList

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.ui.shoppingList.components.AddShoppingListItemDialog
import fyi.goodbye.fridgy.ui.shoppingList.components.AddItemFromSearchDialog
import fyi.goodbye.fridgy.ui.shoppingList.components.PartialPickupDialog
import fyi.goodbye.fridgy.ui.shoppingList.components.ProductSearchResultCard
import fyi.goodbye.fridgy.ui.shoppingList.components.ShoppingListItemCard
import fyi.goodbye.fridgy.ui.shared.components.SearchBar
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
    val activeViewers by viewModel.activeViewers.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showPickupDialog by remember { mutableStateOf<ShoppingListViewModel.ShoppingListItemWithProduct?>(null) }
    var productToAdd by remember { mutableStateOf<Product?>(null) }
    var scannedUpcForDialog by remember { mutableStateOf<String?>(null) }
    var showDoneShoppingDialog by remember { mutableStateOf(false) }
    var showViewersDropdown by remember { mutableStateOf(false) }

    // Manage presence lifecycle
    DisposableEffect(Unit) {
        viewModel.startPresence()
        onDispose {
            viewModel.stopPresence()
        }
    }

    // Handle barcode scan result from BarcodeScannerScreen
    LaunchedEffect(navController.currentBackStackEntry) {
        navController.currentBackStackEntry?.savedStateHandle?.get<String>("scannedUpc")?.let { scannedUpc ->
            // Show dialog to get quantity and store for scanned item
            scannedUpcForDialog = scannedUpc
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
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    // Show active viewers count with dropdown
                    if (activeViewers.isNotEmpty()) {
                        Box {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .padding(end = 8.dp),
                                onClick = { showViewersDropdown = !showViewersDropdown }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = Color(0xFF4CAF50),
                                                shape = MaterialTheme.shapes.extraSmall
                                            )
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.n_others_viewing,
                                            activeViewers.size
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            
                            DropdownMenu(
                                expanded = showViewersDropdown,
                                onDismissRequest = { showViewersDropdown = false }
                            ) {
                                Text(
                                    text = stringResource(R.string.currently_viewing),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                activeViewers.forEach { viewer ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = viewer.username,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        onClick = { /* Could navigate to user profile or do nothing */ },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(
                                                        color = Color(0xFF4CAF50),
                                                        shape = MaterialTheme.shapes.extraSmall
                                                    )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            shape = MaterialTheme.shapes.medium,
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
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.click_add_manually),
                                        style = MaterialTheme.typography.bodySmall,
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
                                    productToAdd = product
                                }
                            )
                        }
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = MaterialTheme.shapes.medium,
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
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Click here to add a product manually",
                                            style = MaterialTheme.typography.bodySmall,
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
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
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
                                            onCheckClick = {
                                                showPickupDialog = itemWithProduct
                                            },
                                            onDeleteClick = { viewModel.removeItem(itemWithProduct.item.upc) }
                                        )
                                    }
                                    item { Spacer(modifier = Modifier.height(88.dp)) }
                                }

                                // Done Shopping Button - visible when any items have been picked up (partial or full)
                                val hasPickedUpItems = state.items.any { (it.item.obtainedQuantity ?: 0) > 0 }
                                if (hasPickedUpItems) {
                                    FloatingActionButton(
                                        onClick = { showDoneShoppingDialog = true },
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(16.dp),
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = stringResource(R.string.done_shopping),
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Text(
                                                text = stringResource(R.string.done_shopping),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
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
                                style = MaterialTheme.typography.bodyLarge
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
            onAddManual = { name, quantity, store ->
                viewModel.addManualItem(name, quantity, store)
                showAddDialog = false
            }
        )
    }
    
    // Add Item From Search Dialog
    productToAdd?.let { product ->
        AddItemFromSearchDialog(
            product = product,
            onDismiss = { productToAdd = null },
            onConfirm = { quantity, store ->
                viewModel.addItem(product.upc, quantity, store)
                viewModel.updateSearchQuery("")
                productToAdd = null
            }
        )
    }
    
    // Partial Pickup Dialog
    showPickupDialog?.let { itemWithProduct ->
        PartialPickupDialog(
            itemName = itemWithProduct.productName,
            requestedQuantity = itemWithProduct.item.quantity,
            currentObtained = itemWithProduct.item.obtainedQuantity ?: 0,
            onDismiss = { showPickupDialog = null },
            onConfirm = { obtainedQty ->
                viewModel.updateItemPickup(
                    upc = itemWithProduct.item.upc,
                    obtainedQuantity = obtainedQty,
                    totalQuantity = itemWithProduct.item.quantity
                )
                showPickupDialog = null
            }
        )
    }
    
    // Scanned Item Dialog - prompt for quantity and store
    scannedUpcForDialog?.let { upc ->
        AddItemFromSearchDialog(
            product = Product(upc = upc, name = "", brand = "", category = "", imageUrl = "", lastUpdated = 0),
            onDismiss = { scannedUpcForDialog = null },
            onConfirm = { quantity, store ->
                viewModel.addItem(upc, quantity, store)
                scannedUpcForDialog = null
            }
        )
    }

    // Done Shopping Confirmation Dialog
    if (showDoneShoppingDialog) {
        AlertDialog(
            onDismissRequest = { showDoneShoppingDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.done_shopping),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.done_shopping_confirmation),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        showDoneShoppingDialog = false
                        viewModel.completeShopping {
                            onBackClick()
                        }
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDoneShoppingDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
