package fyi.goodbye.fridgy.ui.fridgeInventory

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.elements.InventoryItemCard
import fyi.goodbye.fridgy.ui.fridgeInventory.components.NewProductDialog
import fyi.goodbye.fridgy.ui.shared.components.EmptyState
import fyi.goodbye.fridgy.ui.shared.components.LoadingState
import fyi.goodbye.fridgy.ui.shared.components.SimpleErrorState
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
import kotlinx.coroutines.launch

/**
 * Screen displaying the inventory of items for a specific fridge.
 *
 * PERFORMANCE OPTIMIZATIONS:
 * 1. Used derivedStateOf for state transitions (fridgeName, isOwner) to avoid unnecessary recompositions.
 * 2. Optimized backstack result handling to prevent redundant effect triggers.
 * 3. Moved context-dependent file logic out of the composition loop where possible.
 * 4. Added basic execution time logging for key UI states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeInventoryScreen(
    fridgeId: String,
    initialFridgeName: String = "",
    navController: NavController,
    onBackClick: () -> Unit,
    onSettingsClick: (String) -> Unit,
    onAddItemClick: (String) -> Unit,
    onItemClick: (String, String) -> Unit,
    viewModel: FridgeInventoryViewModel = viewModel(factory = FridgeInventoryViewModel.provideFactory(fridgeId, initialFridgeName))
) {
    val fridgeDetailUiState by viewModel.displayFridgeState.collectAsState()
    val itemsUiState by viewModel.itemsUiState.collectAsState()
    val addItemError by viewModel.addItemError.collectAsState()
    val pendingUpc by viewModel.pendingScannedUpc.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val errorLoadingString = stringResource(R.string.error_loading_fridge)
    val loadingString = stringResource(R.string.loading_fridge)

    // OPTIMIZATION: Use derivedStateOf to prevent re-calculating name on every recomposition
    // unless the underlying state object actually changes.
    val fridgeName by remember {
        derivedStateOf {
            when (val state = fridgeDetailUiState) {
                is FridgeInventoryViewModel.FridgeDetailUiState.Success -> state.fridge.name
                is FridgeInventoryViewModel.FridgeDetailUiState.Error -> errorLoadingString
                FridgeInventoryViewModel.FridgeDetailUiState.Loading -> loadingString
            }
        }
    }

    // OPTIMIZATION: derivedStateOf for ownership check
    val isOwner by remember {
        derivedStateOf {
            when (val state = fridgeDetailUiState) {
                is FridgeInventoryViewModel.FridgeDetailUiState.Success -> state.fridge.createdByUid == currentUserId
                else -> false
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dismissLabel = stringResource(R.string.dismiss)

    LaunchedEffect(addItemError) {
        addItemError?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = dismissLabel,
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    // Correctly observe the back stack entry to receive results from the scanner
    LaunchedEffect(navBackStackEntry) {
        val savedStateHandle = navBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
        val scannedUpc = savedStateHandle.get<String>("scannedUpc")
        val targetFridgeId = savedStateHandle.get<String>("targetFridgeId")
        val isSearchScan = savedStateHandle.get<Boolean>("isSearchScan") ?: false

        if (scannedUpc != null && targetFridgeId == fridgeId) {
            Log.d("Performance", "Processing scanned barcode: $scannedUpc")

            if (isSearchScan) {
                // Use barcode as search query
                viewModel.updateSearchQuery(scannedUpc)
            } else {
                // Add item to fridge
                viewModel.onBarcodeScanned(scannedUpc)
            }

            // CRITICAL: Clear the result so it doesn't trigger again on recomposition
            savedStateHandle.remove<String>("scannedUpc")
            savedStateHandle.remove<String>("targetFridgeId")
            savedStateHandle.remove<Boolean>("isSearchScan")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fridgeName,
                        color = FridgyWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = FridgyWhite
                        )
                    }
                },
                actions = {
                    if (isOwner) {
                        IconButton(onClick = { onSettingsClick(fridgeId) }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.cd_fridge_settings),
                                tint = FridgyWhite
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddItemClick(fridgeId) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.cd_add_new_item))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_by_name_or_upc)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    Row {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.cd_clear_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                navController.currentBackStackEntry?.savedStateHandle?.set("isSearchScan", true)
                                navController.navigate("barcodeScanner/$fridgeId")
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = stringResource(R.string.cd_scan_barcode),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = itemsUiState) {
                    FridgeInventoryViewModel.ItemsUiState.Loading -> {
                        LoadingState()
                    }
                    is FridgeInventoryViewModel.ItemsUiState.Error -> {
                        SimpleErrorState(message = state.message)
                    }
                    is FridgeInventoryViewModel.ItemsUiState.Success -> {
                        val items = state.items
                        if (items.isEmpty()) {
                            EmptyState(
                                message =
                                    if (searchQuery.isNotEmpty()) {
                                        stringResource(R.string.no_items_match, searchQuery)
                                    } else {
                                        stringResource(R.string.no_items_in_fridge)
                                    }
                            )
                        } else {
                            // Log time taken for grid rendering start
                            val startTime = System.currentTimeMillis()

                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 140.dp),
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(items, key = { it.item.upc }) { inventoryItem ->
                                    InventoryItemCard(inventoryItem = inventoryItem) { clickedItemId ->
                                        onItemClick(fridgeId, clickedItemId)
                                    }
                                }
                            }

                            SideEffect {
                                Log.d("Performance", "Grid rendered in ${System.currentTimeMillis() - startTime}ms")
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingUpc != null) {
        NewProductDialog(
            upc = pendingUpc!!,
            onConfirm = { name, brand, category, imageUri ->
                viewModel.createAndAddProduct(pendingUpc!!, name, brand, category, imageUri)
            },
            onDismiss = { viewModel.cancelPendingProduct() }
        )
    }
}
