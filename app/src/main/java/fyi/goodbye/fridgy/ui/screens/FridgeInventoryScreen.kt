package fyi.goodbye.fridgy.ui.screens

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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
import fyi.goodbye.fridgy.ui.viewmodels.FridgeInventoryViewModel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Screen displaying the inventory of items for a specific fridge.
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

    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val fridgeName = when(val state = fridgeDetailUiState) {
        is FridgeInventoryViewModel.FridgeDetailUiState.Success -> state.fridge.name
        is FridgeInventoryViewModel.FridgeDetailUiState.Error -> "Error Loading Fridge"
        FridgeInventoryViewModel.FridgeDetailUiState.Loading -> "Loading Fridge..."
    }

    val isOwner = when(val state = fridgeDetailUiState) {
        is FridgeInventoryViewModel.FridgeDetailUiState.Success -> state.fridge.createdByUid == currentUserId
        else -> false
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(addItemError) {
        addItemError?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = "Dismiss",
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

        if (scannedUpc != null && targetFridgeId == fridgeId) {
            Log.d("FridgeInventoryScreen", "Received UPC from scanner: $scannedUpc")
            viewModel.onBarcodeScanned(scannedUpc)
            
            // CRITICAL: Clear the result so it doesn't trigger again on recomposition
            savedStateHandle.remove<String>("scannedUpc")
            savedStateHandle.remove<String>("targetFridgeId")
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
                            contentDescription = "Back",
                            tint = FridgyWhite
                        )
                    }
                },
                actions = {
                    if (isOwner) {
                        IconButton(onClick = { onSettingsClick(fridgeId) }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Fridge Settings",
                                tint = FridgyWhite
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
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
                Icon(Icons.Default.Add, "Add new item")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = itemsUiState) {
            FridgeInventoryViewModel.ItemsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is FridgeInventoryViewModel.ItemsUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is FridgeInventoryViewModel.ItemsUiState.Success -> {
                val items = state.items
                if (items.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No items in this fridge yet! Click the '+' button to add some.",
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            InventoryItemCard(item = item) { clickedItemId ->
                                onItemClick(fridgeId, clickedItemId)
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
    var selectedCategory by remember { mutableStateOf("Other") }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val context = LocalContext.current
    val categories = listOf("Dairy", "Meat", "Produce", "Bakery", "Frozen", "Pantry", "Other")

    val tempFile = remember { File(context.cacheDir, "temp_product_${System.currentTimeMillis()}.jpg") }
    val tempUri = remember { FileProvider.getUriForFile(context, "fyi.goodbye.fridgy.fileprovider", tempFile) }
    
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) capturedImageUri = tempUri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Product Detected", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("We don't recognize barcode $upc. Please add it to our database.", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { cameraLauncher.launch(tempUri) },
                    contentAlignment = Alignment.Center
                ) {
                    if (capturedImageUri != null) {
                        AsyncImage(
                            model = capturedImageUri,
                            contentDescription = "Captured Product",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Text("Take Product Photo", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    label = { Text("Product Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = productBrand,
                    onValueChange = { productBrand = it },
                    label = { Text("Brand (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Category", fontWeight = FontWeight.Medium)
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
                Text("Save & Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * A card representing a single inventory item in the grid.
 */
@Composable
fun InventoryItemCard(item: Item, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable { onClick(item.id) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (item.imageUrl != null) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(text = "🍎", fontSize = 32.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Qty: ${item.quantity}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
