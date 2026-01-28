package fyi.goodbye.fridgy.ui.fridgeList

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.display.DisplayFridge
import fyi.goodbye.fridgy.models.entities.HouseholdRole
import fyi.goodbye.fridgy.ui.elements.FridgeCard
import fyi.goodbye.fridgy.ui.shared.components.EmptyState
import fyi.goodbye.fridgy.ui.shared.components.ErrorState
import fyi.goodbye.fridgy.ui.shared.components.LoadingState

/**
 * Screen displaying a list of fridges within a specific household.
 *
 * It provides functionality to:
 * - View a list of fridges in the household.
 * - Create a new fridge via a popup dialog.
 * - Navigate to a specific fridge's inventory.
 * - Navigate back to the household list.
 * - Navigate to household settings.
 *
 * @param onNavigateToFridgeInventory Callback to navigate to the inventory of a selected fridge.
 * @param onNavigateToHouseholdSettings Callback to navigate to household settings.
 * @param onSwitchHousehold Callback to navigate to household selection screen.
 * @param viewModel The state holder for the fridge list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeListScreen(
    onNavigateToFridgeInventory: (DisplayFridge) -> Unit,
    onNavigateToHouseholdSettings: () -> Unit,
    onSwitchHousehold: () -> Unit,
    onShoppingListClick: (String) -> Unit,
    viewModel: FridgeListViewModel = hiltViewModel()
) {
    var showAddFridgeDialog by remember { mutableStateOf(false) }
    var newFridgeName by remember { mutableStateOf("") }
    var newFridgeType by remember { mutableStateOf("fridge") }
    var newFridgeLocation by remember { mutableStateOf("") }

    // Helper function to reset dialog state
    fun resetDialogState() {
        newFridgeName = ""
        newFridgeType = "fridge"
        newFridgeLocation = ""
    }

    val fridgeUiState by viewModel.fridgesUiState.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val userRole by viewModel.userRole.collectAsState()

    // Check if user can manage fridges (owner or manager)
    val canManageFridges =
        userRole?.let {
            it == HouseholdRole.OWNER ||
                it == HouseholdRole.MANAGER
        } ?: false

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.fridges),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                navigationIcon = {
                    IconButton(onClick = onSwitchHousehold) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = stringResource(R.string.cd_switch_household),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHouseholdSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_household_settings),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = { onShoppingListClick(viewModel.householdId) },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = stringResource(R.string.cd_open_shopping_list)
                    )
                }
                // Only show add fridge button for managers and owners
                if (canManageFridges) {
                    FloatingActionButton(
                        onClick = { showAddFridgeDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, stringResource(R.string.cd_add_new_fridge))
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = fridgeUiState) {
            FridgeListViewModel.FridgeUiState.Loading -> {
                LoadingState(
                    modifier = Modifier.padding(paddingValues),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is FridgeListViewModel.FridgeUiState.Error -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    ErrorState(
                        message = state.message,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { /* Retry logic could be added to ViewModel */ }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            is FridgeListViewModel.FridgeUiState.Success -> {
                val fridges = state.fridges
                if (fridges.isEmpty()) {
                    EmptyState(
                        message = stringResource(R.string.no_fridges_yet),
                        modifier = Modifier.padding(paddingValues)
                    )
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        // OPTIMIZATION: Add key for item identity
                        items(fridges, key = { it.id }) { fridge ->
                            // OPTIMIZATION: Stable callback reference prevents recomposition
                            val onCardClick =
                                remember(fridge.id) {
                                    { _: DisplayFridge -> onNavigateToFridgeInventory(fridge) }
                                }
                            FridgeCard(fridge = fridge, onClick = onCardClick)
                        }
                    }
                }
            }
        }
    }

    if (showAddFridgeDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddFridgeDialog = false
                resetDialogState()
            },
            containerColor = Color.White,
            title = {
                Text(
                    text = stringResource(R.string.create_new_fridge),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = newFridgeName,
                        onValueChange = { newFridgeName = it },
                        label = { Text(stringResource(R.string.fridge_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.fridge_type_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("fridge", "freezer", "pantry").forEach { type ->
                                FilterChip(
                                    selected = newFridgeType == type,
                                    onClick = { newFridgeType = type },
                                    label = {
                                        Text(
                                            text = type.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newFridgeLocation,
                        onValueChange = { newFridgeLocation = it },
                        label = { Text(stringResource(R.string.location_optional)) },
                        placeholder = { Text(stringResource(R.string.location_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (newFridgeName.isNotBlank()) {
                            viewModel.createNewFridge(newFridgeName, newFridgeType, newFridgeLocation)
                            showAddFridgeDialog = false
                            resetDialogState()
                        }
                    },
                    enabled = newFridgeName.isNotBlank()
                ) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddFridgeDialog = false
                    resetDialogState()
                }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}
