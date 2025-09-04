package fyi.goodbye.fridgy.ui.screens

import androidx.compose.ui.text.style.TextAlign
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue
import fyi.goodbye.fridgy.ui.theme.FridgyLightBlue
import fyi.goodbye.fridgy.ui.theme.FridgyTextBlue
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.ui.viewmodels.FridgeInventoryViewModel
import kotlinx.coroutines.launch

// Removed SimpleDateFormat and Date/Locale imports as expirationDate is no longer directly in Item

/*
 * FridgeInventoryScreen composable
 * Displays the items within a specific fridge in a grid layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeInventoryScreen(
    fridgeId: String, // CHANGED: Now takes only fridgeId as String
    navController: NavController, // <--- NEW: NavController parameter
    onBackClick: () -> Unit,
    onSettingsClick: (String) -> Unit,
    onAddItemClick: (String) -> Unit,
    onItemClick: (String, String) -> Unit,
    viewModel: FridgeInventoryViewModel = viewModel(factory = FridgeInventoryViewModel.provideFactory(fridgeId)) // NEW: Inject ViewModel
) {
    // Observe the UI states from the ViewModel
    val fridgeDetailUiState by viewModel.displayFridgeState.collectAsState()
    val itemsUiState by viewModel.itemsUiState.collectAsState()
    val isAddingItem by viewModel.isAddingItem.collectAsState() // Observe adding item loading
    val addItemError by viewModel.addItemError.collectAsState() // Observe adding item error

    val fridgeName = when(val state = fridgeDetailUiState) {
        is FridgeInventoryViewModel.FridgeDetailUiState.Success -> state.fridge.name
        is FridgeInventoryViewModel.FridgeDetailUiState.Error -> "Error Loading Fridge"
        FridgeInventoryViewModel.FridgeDetailUiState.Loading -> "Loading Fridge..."
    }

    // Show a Snackbar for add item errors
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(addItemError) {
        addItemError?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Failed to add item: $message",
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Long
                )
                // Optionally clear the error after showing
                // viewModel.clearAddItemError() // You'd add this function to ViewModel
            }
        }
    }

    // NEW: LaunchedEffect to check for scanned UPC when screen becomes active
    LaunchedEffect(navController) {
        // Access SavedStateHandle from the current back stack entry for data passed back
        navController?.currentBackStackEntry?.savedStateHandle?.let { savedStateHandle ->
            // Retrieve scannedUpc and targetFridgeId
            val scannedUpc = savedStateHandle.get<String>("scannedUpc")
            val targetFridgeId = savedStateHandle.get<String>("targetFridgeId")

            // Process only if a UPC was scanned and it belongs to this fridge
            if (scannedUpc != null && targetFridgeId == fridgeId) {
                Log.d("FridgeInventoryScreen", "Received scanned UPC: $scannedUpc for fridge $targetFridgeId")
                // Now, add the item using the scanned UPC via the ViewModel
                viewModel.addItem(upc = scannedUpc, quantity = 1) // Default quantity 1 for now

                // Clear the SavedStateHandle to prevent processing the same UPC again on recomposition
                savedStateHandle.remove<String>("scannedUpc")
                savedStateHandle.remove<String>("targetFridgeId")
            }
        }
    }

    Scaffold(
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
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = FridgyWhite
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onSettingsClick(fridgeId) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Fridge Settings",
                            tint = FridgyWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FridgyDarkBlue
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddItemClick(fridgeId) },
                containerColor = FridgyDarkBlue,
                contentColor = FridgyWhite
            ) {
                Icon(Icons.Default.Add, "Add new item")
            }
        },
        containerColor = FridgyLightBlue
    ) { paddingValues ->
        // Handle UI states for items
        when (val state = itemsUiState) {
            FridgeInventoryViewModel.ItemsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FridgyDarkBlue)
                }
            }
            is FridgeInventoryViewModel.ItemsUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    // TODO: Add retry button or more sophisticated error handling
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
                            color = FridgyTextBlue.copy(alpha = 0.8f),
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items) { item ->
                            InventoryItemCard(item = item) { clickedItemId ->
                                onItemClick(fridgeId, clickedItemId)
                            }
                        }
                    }
                }
            }
        }
    }
}

/*
 * InventoryItemCard composable
 * Displays a single item in the fridge inventory grid based on the new Item data class.
 */
@Composable
fun InventoryItemCard(item: Item, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clickable { onClick(item.id) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridgyWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(FridgyLightBlue, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔎",
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.upc,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = FridgyDarkBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Qty: ${item.quantity}",
                fontSize = 14.sp,
                color = FridgyTextBlue.copy(alpha = 0.8f)
            )
        }
    }
}

/*
 * Preview function for FridgeInventoryScreen
 */
//@Preview(showBackground = true, widthDp = 360, heightDp = 720)
//@Composable
//fun PreviewFridgeInventoryScreen() {
//    FridgyTheme {
//        FridgeInventoryScreen(
//            fridgeId = "fridge123", // Now expects just the ID
//            onBackClick = { Log.d("FridgeInventoryScreen", "Back clicked") },
//            onSettingsClick = { id -> Log.d("FridgeInventoryScreen", "Settings for fridge $id clicked") },
//            onAddItemClick = { id -> Log.d("FridgeInventoryScreen", "Add item to fridge $id clicked") },
//            onItemClick = { fridgeId, itemId -> Log.d("FridgeInventoryScreen", "Item $itemId in fridge $fridgeId clicked") }
//        )
//    }
//}