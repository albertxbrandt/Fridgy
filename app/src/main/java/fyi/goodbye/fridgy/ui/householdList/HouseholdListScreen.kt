package fyi.goodbye.fridgy.ui.householdList

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayHousehold
import fyi.goodbye.fridgy.ui.elements.HouseholdCard
import fyi.goodbye.fridgy.ui.shared.components.CollapsibleSidebar
import fyi.goodbye.fridgy.ui.shared.components.EmptyState
import fyi.goodbye.fridgy.ui.shared.components.ErrorState
import fyi.goodbye.fridgy.ui.shared.components.LoadingState
import fyi.goodbye.fridgy.ui.shared.components.SidebarMenuItem

/**
 * The main screen displaying a list of the user's households.
 *
 * It provides functionality to:
 * - View a list of households the user belongs to.
 * - Create a new household via a popup dialog.
 * - Join a household with an invite code.
 * - Navigate to a specific household's fridges.
 * - Logout of the application.
 *
 * @param onNavigateToHousehold Callback to navigate to a household's fridge list.
 * @param onNavigateToJoinHousehold Callback to navigate to join household screen.
 * @param onNavigateToNotifications Callback to navigate to notifications.
 * @param onNavigateToAdminPanel Callback to navigate to admin panel.
 * @param onLogout Callback to navigate to the login screen after signing out.
 * @param viewModel The state holder for the household list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdListScreen(
    onNavigateToHousehold: (DisplayHousehold) -> Unit,
    onNavigateToJoinHousehold: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToAdminPanel: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HouseholdListViewModel = viewModel(factory = HouseholdListViewModel.provideFactory())
) {
    var showAddHouseholdDialog by remember { mutableStateOf(false) }
    var newHouseholdName by remember { mutableStateOf("") }
    var isSidebarOpen by remember { mutableStateOf(false) }
    var showMigrationDialog by remember { mutableStateOf(false) }
    
    val householdsUiState by viewModel.householdsUiState.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val isCreatingHousehold by viewModel.isCreatingHousehold.collectAsState()
    val createHouseholdError by viewModel.createHouseholdError.collectAsState()
    val needsMigration by viewModel.needsMigration.collectAsState()
    val isMigrating by viewModel.isMigrating.collectAsState()
    val auth = remember { FirebaseAuth.getInstance() }
    
    // Show migration dialog when needed
    LaunchedEffect(needsMigration) {
        if (needsMigration) {
            showMigrationDialog = true
        }
    }

    // Define sidebar menu items
    val sidebarMenuItems = buildList {
        add(
            SidebarMenuItem(
                icon = Icons.Default.Notifications,
                label = "Notifications",
                onClick = {
                    isSidebarOpen = false
                    onNavigateToNotifications()
                }
            )
        )
        add(
            SidebarMenuItem(
                icon = Icons.Default.Link,
                label = "Join Household",
                onClick = {
                    isSidebarOpen = false
                    onNavigateToJoinHousehold()
                }
            )
        )
        add(
            SidebarMenuItem(
                icon = Icons.Default.AccountCircle,
                label = "Account",
                onClick = { isSidebarOpen = false }
            )
        )
        add(
            SidebarMenuItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "Logout",
                onClick = {
                    auth.signOut()
                    onLogout()
                }
            )
        )
        if (isAdmin) {
            add(
                SidebarMenuItem(
                    icon = Icons.Default.AdminPanelSettings,
                    label = "Admin Panel",
                    onClick = {
                        isSidebarOpen = false
                        onNavigateToAdminPanel()
                    }
                )
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.my_households),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    IconButton(onClick = { isSidebarOpen = !isSidebarOpen }) {
                        Icon(
                            imageVector = if (isSidebarOpen) Icons.Default.Close else Icons.Default.Menu,
                            contentDescription = if (isSidebarOpen) "Close menu" else "Open menu",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddHouseholdDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.cd_create_household))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        CollapsibleSidebar(
            isOpen = isSidebarOpen,
            onDismiss = { isSidebarOpen = false },
            menuItems = sidebarMenuItems
        ) {
            when (val state = householdsUiState) {
                HouseholdListViewModel.HouseholdUiState.Loading -> {
                    LoadingState(
                        modifier = Modifier.padding(paddingValues),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is HouseholdListViewModel.HouseholdUiState.Error -> {
                    Column(
                        modifier = Modifier
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
                    }
                }
                is HouseholdListViewModel.HouseholdUiState.Success -> {
                    val households = state.households
                    if (households.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            EmptyState(
                                message = stringResource(R.string.no_households_yet),
                                modifier = Modifier.weight(1f)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Quick action buttons for empty state
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(bottom = 80.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onNavigateToJoinHousehold
                                ) {
                                    Icon(
                                        Icons.Default.Link,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.join_household))
                                }
                                FilledTonalButton(
                                    onClick = { showAddHouseholdDialog = true }
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.create_household))
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(households, key = { it.id }) { household ->
                                val onCardClick = remember(household.id) {
                                    { _: DisplayHousehold -> onNavigateToHousehold(household) }
                                }
                                HouseholdCard(household = household, onClick = onCardClick)
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Household Dialog
    if (showAddHouseholdDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isCreatingHousehold) {
                    showAddHouseholdDialog = false
                    newHouseholdName = ""
                    viewModel.clearError()
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.create_household),
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
                        value = newHouseholdName,
                        onValueChange = { newHouseholdName = it },
                        label = { Text(stringResource(R.string.household_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        enabled = !isCreatingHousehold
                    )
                    
                    if (createHouseholdError != null) {
                        Text(
                            text = createHouseholdError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        viewModel.createNewHousehold(newHouseholdName)
                        if (createHouseholdError == null) {
                            showAddHouseholdDialog = false
                            newHouseholdName = ""
                        }
                    },
                    enabled = newHouseholdName.isNotBlank() && !isCreatingHousehold
                ) {
                    if (isCreatingHousehold) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.create))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddHouseholdDialog = false
                        newHouseholdName = ""
                        viewModel.clearError()
                    },
                    enabled = !isCreatingHousehold
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
    
    // Migration Dialog
    if (showMigrationDialog) {
        AlertDialog(
            onDismissRequest = { /* Don't allow dismissing */ },
            title = {
                Text(
                    text = "Migration Required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "We've updated Fridgy to use Households! Your existing fridges need to be migrated to a new household.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "This will create a new household called \"My Household\" and move all your fridges into it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        viewModel.migrateOrphanFridges()
                        showMigrationDialog = false
                    },
                    enabled = !isMigrating
                ) {
                    if (isMigrating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Migrate Now")
                    }
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
