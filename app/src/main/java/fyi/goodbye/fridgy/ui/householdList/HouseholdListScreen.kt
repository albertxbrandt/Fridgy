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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayHousehold
import fyi.goodbye.fridgy.models.UserProfile
import fyi.goodbye.fridgy.ui.elements.HouseholdCard
import fyi.goodbye.fridgy.ui.shared.components.CollapsibleSidebar
import fyi.goodbye.fridgy.ui.shared.components.ErrorState
import fyi.goodbye.fridgy.ui.shared.components.LoadingState
import fyi.goodbye.fridgy.ui.shared.components.SidebarMenuItem
import fyi.goodbye.fridgy.ui.theme.FridgyTheme

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
    onJoinHouseholdSuccess: (String) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToAdminPanel: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HouseholdListViewModel = hiltViewModel()
) {
    val householdsUiState by viewModel.householdsUiState.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val isCreatingHousehold by viewModel.isCreatingHousehold.collectAsState()
    val createHouseholdError by viewModel.createHouseholdError.collectAsState()
    val needsMigration by viewModel.needsMigration.collectAsState()
    val isMigrating by viewModel.isMigrating.collectAsState()

    HouseholdListContent(
        householdsUiState = householdsUiState,
        isAdmin = isAdmin,
        isCreatingHousehold = isCreatingHousehold,
        createHouseholdError = createHouseholdError,
        needsMigration = needsMigration,
        isMigrating = isMigrating,
        onNavigateToHousehold = onNavigateToHousehold,
        onJoinHouseholdSuccess = onJoinHouseholdSuccess,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToAdminPanel = onNavigateToAdminPanel,
        onLogout = {
            viewModel.logout()
            onLogout()
        },
        onCreateHousehold = { name -> viewModel.createNewHousehold(name) },
        onClearError = { viewModel.clearError() },
        onMigrateOrphanFridges = { viewModel.migrateOrphanFridges() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HouseholdListContent(
    householdsUiState: HouseholdListViewModel.HouseholdUiState,
    isAdmin: Boolean,
    isCreatingHousehold: Boolean,
    createHouseholdError: String?,
    needsMigration: Boolean,
    isMigrating: Boolean,
    onNavigateToHousehold: (DisplayHousehold) -> Unit,
    onJoinHouseholdSuccess: (String) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToAdminPanel: () -> Unit,
    onLogout: () -> Unit,
    onCreateHousehold: (String) -> Unit,
    onClearError: () -> Unit,
    onMigrateOrphanFridges: () -> Unit
) {
    var showAddHouseholdDialog by remember { mutableStateOf(false) }
    var newHouseholdName by remember { mutableStateOf("") }
    var isSidebarOpen by remember { mutableStateOf(false) }
    var showMigrationDialog by remember { mutableStateOf(false) }
    var showJoinHouseholdDialog by remember { mutableStateOf(false) }

    // Show migration dialog when needed
    LaunchedEffect(needsMigration) {
        if (needsMigration) {
            showMigrationDialog = true
        }
    }

    // Define sidebar menu items
    val sidebarMenuItems =
        buildList {
            add(
                SidebarMenuItem(
                    icon = Icons.Default.Notifications,
                    label = stringResource(R.string.notifications),
                    onClick = {
                        isSidebarOpen = false
                        onNavigateToNotifications()
                    }
                )
            )
            add(
                SidebarMenuItem(
                    icon = Icons.Default.Link,
                    label = stringResource(R.string.join_household),
                    onClick = {
                        isSidebarOpen = false
                        showJoinHouseholdDialog = true
                    }
                )
            )
            add(
                SidebarMenuItem(
                    icon = Icons.Default.AccountCircle,
                    label = stringResource(R.string.account),
                    onClick = { isSidebarOpen = false }
                )
            )
            add(
                SidebarMenuItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = stringResource(R.string.logout),
                    onClick = onLogout
                )
            )
            if (isAdmin) {
                add(
                    SidebarMenuItem(
                        icon = Icons.Default.AdminPanelSettings,
                        label = stringResource(R.string.admin_panel),
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
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                actions = {
                    IconButton(onClick = { isSidebarOpen = !isSidebarOpen }) {
                        Icon(
                            imageVector = if (isSidebarOpen) Icons.Default.Close else Icons.Default.Menu,
                            contentDescription =
                                stringResource(
                                    if (isSidebarOpen) R.string.cd_close_menu else R.string.cd_open_menu
                                ),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // Only show FAB when there are households (empty state has inline buttons)
            if (householdsUiState is HouseholdListViewModel.HouseholdUiState.Success &&
                (householdsUiState as HouseholdListViewModel.HouseholdUiState.Success).households.isNotEmpty()
            ) {
                FloatingActionButton(
                    onClick = { showAddHouseholdDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.cd_create_household))
                }
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
                    }
                }
                is HouseholdListViewModel.HouseholdUiState.Success -> {
                    val households = state.households
                    if (households.isEmpty()) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                                    .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_households_yet),
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Quick action buttons for empty state
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showJoinHouseholdDialog = true }
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
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(households, key = { it.id }) { household ->
                                val onCardClick =
                                    remember(household.id) {
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
                    onClearError()
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
                        onCreateHousehold(newHouseholdName)
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
                        onClearError()
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
                    text = stringResource(R.string.migration_required),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.migration_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.migration_details),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        onMigrateOrphanFridges()
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
                        Text(stringResource(R.string.migrate_now))
                    }
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Join Household Dialog
    if (showJoinHouseholdDialog) {
        JoinHouseholdDialog(
            onDismiss = { showJoinHouseholdDialog = false },
            onJoinSuccess = { householdId ->
                showJoinHouseholdDialog = false
                onJoinHouseholdSuccess(householdId)
            }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HouseholdListPreviewLoading() {
    FridgyTheme {
        HouseholdListContent(
            householdsUiState = HouseholdListViewModel.HouseholdUiState.Loading,
            isAdmin = false,
            isCreatingHousehold = false,
            createHouseholdError = null,
            needsMigration = false,
            isMigrating = false,
            onNavigateToHousehold = {},
            onJoinHouseholdSuccess = {},
            onNavigateToNotifications = {},
            onNavigateToAdminPanel = {},
            onLogout = {},
            onCreateHousehold = {},
            onClearError = {},
            onMigrateOrphanFridges = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HouseholdListPreviewEmpty() {
    FridgyTheme {
        HouseholdListContent(
            householdsUiState = HouseholdListViewModel.HouseholdUiState.Success(emptyList()),
            isAdmin = false,
            isCreatingHousehold = false,
            createHouseholdError = null,
            needsMigration = false,
            isMigrating = false,
            onNavigateToHousehold = {},
            onJoinHouseholdSuccess = {},
            onNavigateToNotifications = {},
            onNavigateToAdminPanel = {},
            onLogout = {},
            onCreateHousehold = {},
            onClearError = {},
            onMigrateOrphanFridges = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HouseholdListPreviewWithHouseholds() {
    FridgyTheme {
        HouseholdListContent(
            householdsUiState =
                HouseholdListViewModel.HouseholdUiState.Success(
                    listOf(
                        DisplayHousehold(
                            id = "1",
                            name = "Smith Family",
                            createdByUid = "user1",
                            ownerDisplayName = "John Smith",
                            memberUsers =
                                listOf(
                                    UserProfile(uid = "user1", username = "John Smith"),
                                    UserProfile(uid = "user2", username = "Jane Smith"),
                                    UserProfile(uid = "user3", username = "Bobby Smith"),
                                    UserProfile(uid = "user4", username = "Sally Smith")
                                ),
                            fridgeCount = 3,
                            createdAt = System.currentTimeMillis()
                        ),
                        DisplayHousehold(
                            id = "2",
                            name = "Beach House",
                            createdByUid = "user1",
                            ownerDisplayName = "John Smith",
                            memberUsers =
                                listOf(
                                    UserProfile(uid = "user1", username = "John Smith"),
                                    UserProfile(uid = "user5", username = "Mike Johnson")
                                ),
                            fridgeCount = 1,
                            createdAt = System.currentTimeMillis()
                        ),
                        DisplayHousehold(
                            id = "3",
                            name = "Office Kitchen",
                            createdByUid = "user2",
                            ownerDisplayName = "Jane Smith",
                            memberUsers =
                                listOf(
                                    UserProfile(uid = "user2", username = "Jane Smith"),
                                    UserProfile(uid = "user6", username = "Alice Brown"),
                                    UserProfile(uid = "user7", username = "Bob White"),
                                    UserProfile(uid = "user8", username = "Charlie Green"),
                                    UserProfile(uid = "user9", username = "Diana Blue"),
                                    UserProfile(uid = "user10", username = "Eve Red"),
                                    UserProfile(uid = "user11", username = "Frank Yellow"),
                                    UserProfile(uid = "user12", username = "Grace Purple")
                                ),
                            fridgeCount = 2,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                ),
            isAdmin = true,
            isCreatingHousehold = false,
            createHouseholdError = null,
            needsMigration = false,
            isMigrating = false,
            onNavigateToHousehold = {},
            onJoinHouseholdSuccess = {},
            onNavigateToNotifications = {},
            onNavigateToAdminPanel = {},
            onLogout = {},
            onCreateHousehold = {},
            onClearError = {},
            onMigrateOrphanFridges = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HouseholdListPreviewError() {
    FridgyTheme {
        HouseholdListContent(
            householdsUiState = HouseholdListViewModel.HouseholdUiState.Error("Failed to load households"),
            isAdmin = false,
            isCreatingHousehold = false,
            createHouseholdError = null,
            needsMigration = false,
            isMigrating = false,
            onNavigateToHousehold = {},
            onJoinHouseholdSuccess = {},
            onNavigateToNotifications = {},
            onNavigateToAdminPanel = {},
            onLogout = {},
            onCreateHousehold = {},
            onClearError = {},
            onMigrateOrphanFridges = {}
        )
    }
}
