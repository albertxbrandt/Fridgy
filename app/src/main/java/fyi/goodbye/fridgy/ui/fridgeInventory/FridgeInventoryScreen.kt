package fyi.goodbye.fridgy.ui.fridgeInventory

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.elements.InventoryItemCard
import fyi.goodbye.fridgy.ui.shared.CategoryViewModel
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
import kotlinx.coroutines.launch
import java.io.File

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
    navController: NavController,
    onBackClick: () -> Unit,
    onSettingsClick: (String) -> Unit,
    onAddItemClick: (String) -> Unit,
    onItemClick: (String, String) -> Unit,
    viewModel: FridgeInventoryViewModel = viewModel(factory = FridgeInventoryViewModel.provideFactory(fridgeId))
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
                placeholder = { Text("Search by name or UPC") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    Row {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
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
                                contentDescription = "Scan barcode",
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
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is FridgeInventoryViewModel.ItemsUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = state.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is FridgeInventoryViewModel.ItemsUiState.Success -> {
                        val items = state.items
                        if (items.isEmpty()) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text =
                                        if (searchQuery.isNotEmpty()) {
                                            "No items match \"$searchQuery\""
                                        } else {
                                            stringResource(R.string.no_items_in_fridge)
                                        },
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    lineHeight = 24.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
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
                                items(items, key = { it.item.id }) { inventoryItem ->
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
}

/**
 * Dialog for entering details for a new, unknown product.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProductDialog(
    upc: String,
    onConfirm: (String, String, String, Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    var productName by remember { mutableStateOf("") }
    var productBrand by remember { mutableStateOf("") }

    // Load categories from database
    val categoryViewModel: CategoryViewModel = viewModel()
    val categoryState by categoryViewModel.uiState.collectAsState()

    val categories =
        when (val state = categoryState) {
            is CategoryViewModel.CategoryUiState.Success -> state.categories.map { it.name }
            else -> listOf("Other") // Fallback if categories haven't loaded yet
        }

    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "Other") }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current

    // OPTIMIZATION: Memoize file paths to avoid file system calls on every recomposition
    val (tempFile, tempUri) =
        remember {
            val file = File(context.cacheDir, "temp_product_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "fyi.goodbye.fridgy.fileprovider", file)
            file to uri
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) capturedImageUri = tempUri
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_product_detected), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.product_not_recognized, upc), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .clickable { cameraLauncher.launch(tempUri) },
                    contentAlignment = Alignment.Center
                ) {
                    if (capturedImageUri != null) {
                        AsyncImage(
                            model = capturedImageUri,
                            contentDescription = stringResource(R.string.cd_captured_product),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Text(stringResource(R.string.take_product_photo), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    label = { Text(stringResource(R.string.product_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = productBrand,
                    onValueChange = { productBrand = it },
                    label = { Text(stringResource(R.string.brand_optional)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.category), fontWeight = FontWeight.Medium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(productName, productBrand, selectedCategory, capturedImageUri) },
                enabled = productName.isNotBlank()
            ) {
                Text(stringResource(R.string.save_and_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
