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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.ui.viewmodels.FridgeInventoryViewModel
import kotlinx.coroutines.launch

/**
 * Screen displaying the inventory of items for a specific fridge.
 * 
 * It provides a grid view of all products, allowing users to:
 * - See real-time updates of item quantities.
 * - Add new items via barcode scanning.
 * - Access fridge-specific settings (if owner).
 * - Receive feedback for background operations via Snackbars.
 *
 * @param fridgeId The unique ID of the fridge.
 * @param navController Controller for navigating between screens.
 * @param onBackClick Callback to return to the fridge list.
 * @param onSettingsClick Callback to navigate to fridge settings.
 * @param onAddItemClick Callback to initiate adding a new item (scanner).
 * @param onItemClick Callback when a specific item card is selected.
 * @param viewModel The state holder for fridge inventory logic.
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
    val isAddingItem by viewModel.isAddingItem.collectAsState()
    val addItemError by viewModel.addItemError.collectAsState()

    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid }

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
                    message = "Failed to add item: $message",
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    LaunchedEffect(navController) {
        navController.currentBackStackEntry?.savedStateHandle?.let { savedStateHandle ->
            val scannedUpc = savedStateHandle.get<String>("scannedUpc")
            val targetFridgeId = savedStateHandle.get<String>("targetFridgeId")

            if (scannedUpc != null && targetFridgeId == fridgeId) {
                Log.d("FridgeInventoryScreen", "Received scanned UPC: $scannedUpc")
                viewModel.addItem(upc = scannedUpc, quantity = 1)
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

/**
 * A card representing a single inventory item in the grid.
 *
 * @param item The [Item] data to display.
 * @param onClick Callback triggered when the item card is selected.
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
