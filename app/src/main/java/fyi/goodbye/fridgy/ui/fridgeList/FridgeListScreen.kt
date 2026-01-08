package fyi.goodbye.fridgy.ui.fridgeList

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.ui.elements.FridgeCard
import fyi.goodbye.fridgy.ui.shared.components.EmptyState
import fyi.goodbye.fridgy.ui.shared.components.ErrorState
import fyi.goodbye.fridgy.ui.shared.components.LoadingState
import fyi.goodbye.fridgy.ui.shared.components.CollapsibleSidebar
import fyi.goodbye.fridgy.ui.shared.components.SidebarMenuItem

/**
 * The main dashboard screen displaying a list of the user's fridges.
 *
 * It provides functionality to:
 * - View a list of fridges the user belongs to.
 * - Create a new fridge via a popup dialog.
 * - View and manage pending fridge invitations (Accept/Decline).
 * - Navigate to a specific fridge's inventory.
 * - Logout of the application.
 *
 * @param onNavigateToFridgeInventory Callback to navigate to the inventory of a selected fridge.
 * @param onAddFridgeClick Callback for when the add button is clicked (handled internally by a dialog).
 * @param onNotificationsClick Callback for the notification icon (handled internally).
 * @param onProfileClick Callback to navigate to the user's profile.
 * @param onLogout Callback to navigate to the login screen after signing out.
 * @param viewModel The state holder for the fridge list and invitations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeListScreen(
    onNavigateToFridgeInventory: (DisplayFridge) -> Unit,
    onAddFridgeClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onNavigateToAdminPanel: () -> Unit,
    onLogout: () -> Unit,
    viewModel: FridgeListViewModel = viewModel(factory = FridgeListViewModel.provideFactory())
) {
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showAddFridgeDialog by remember { mutableStateOf(false) }
    var newFridgeName by remember { mutableStateOf("") }
    var newFridgeType by remember { mutableStateOf("fridge") }
    var newFridgeLocation by remember { mutableStateOf("") }
    var isSidebarOpen by remember { mutableStateOf(false) }
    
    // Helper function to reset dialog state
    fun resetDialogState() {
        newFridgeName = ""
        newFridgeType = "fridge"
        newFridgeLocation = ""
    }

    val fridgeUiState by viewModel.fridgesUiState.collectAsState()
    val invites by viewModel.invites.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val auth = remember { FirebaseAuth.getInstance() }

    // Define sidebar menu items
    val sidebarMenuItems = buildList {
        add(
            SidebarMenuItem(
                icon = Icons.Default.Notifications,
                label = "Notifications",
                onClick = {
                    isSidebarOpen = false
                    onNotificationsClick()
                }
            )
        )
        add(
            SidebarMenuItem(
                icon = Icons.Default.AccountCircle,
                label = "Account",
                onClick = onProfileClick
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
        // Show admin option only for admins
        if (isAdmin) {
            add(
                SidebarMenuItem(
                    icon = Icons.Default.AdminPanelSettings,
                    label = "Admin Panel",
                    onClick = onNavigateToAdminPanel
                )
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.my_fridges),
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
                    // Notification badge indicator
                    if (invites.isNotEmpty()) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.offset(x = 20.dp, y = (-8).dp)
                        ) {
                            Text(
                                text = invites.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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
                onClick = { showAddFridgeDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.cd_add_new_fridge))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        CollapsibleSidebar(
            isOpen = isSidebarOpen,
            onDismiss = { isSidebarOpen = false },
            menuItems = sidebarMenuItems
        ) {
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
        } // End CollapsibleSidebar
    }

    if (showNotificationsDialog) {
        NotificationsDialog(
            invites = invites,
            onAccept = { viewModel.acceptInvite(it.id) },
            onDecline = { viewModel.declineInvite(it.id) },
            onDismissRequest = { showNotificationsDialog = false }
        )
    }

    if (showAddFridgeDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddFridgeDialog = false
                resetDialogState()
            },
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
                        label = { Text("Location (Optional)") },
                        placeholder = { Text("e.g., Kitchen, Garage") },
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
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

/**
 * A modern dialog that displays a list of pending fridge invitations.
 *
 * Features clean typography and proper Material 3 theming.
 * Users can accept or decline invitations directly from this dialog.
 *
 * @param invites The list of pending [DisplayFridge] invitations.
 * @param onAccept Callback when an invitation is accepted.
 * @param onDecline Callback when an invitation is declined.
 * @param onDismissRequest Callback to close the dialog.
 */
@Composable
fun NotificationsDialog(
    invites: List<DisplayFridge>,
    onAccept: (DisplayFridge) -> Unit,
    onDecline: (DisplayFridge) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.invitations),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn {
                if (invites.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_pending_invitations),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(invites) { invite ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.invite_to_join, invite.name),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onDecline(invite) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                    ) {
                                        Text(stringResource(R.string.decline))
                                    }
                                    Button(
                                        onClick = { onAccept(invite) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.accept))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.close))
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    )
}
