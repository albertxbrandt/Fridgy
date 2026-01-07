package fyi.goodbye.fridgy.ui.fridgeList

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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

    val fridgeUiState by viewModel.fridgesUiState.collectAsState()
    val invites by viewModel.invites.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val auth = remember { FirebaseAuth.getInstance() }

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
                navigationIcon = {
                    // Admin Panel Button (only visible to admins)
                    if (isAdmin) {
                        IconButton(onClick = onNavigateToAdminPanel) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.cd_admin_panel),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                actions = {
                    IconButton(
                        onClick = {
                            showNotificationsDialog = true
                        }
                    ) {
                        BadgedBox(
                            badge = {
                                if (invites.isNotEmpty()) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                                    ) {
                                        Text(
                                            text = invites.size.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = stringResource(R.string.cd_notifications),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    IconButton(onClick = onProfileClick) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = stringResource(R.string.cd_profile),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    IconButton(onClick = {
                        auth.signOut()
                        onLogout()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = stringResource(R.string.cd_logout),
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
                newFridgeName = ""
            },
            title = {
                Text(
                    text = stringResource(R.string.create_new_fridge),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newFridgeName,
                    onValueChange = { newFridgeName = it },
                    label = { Text(stringResource(R.string.fridge_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (newFridgeName.isNotBlank()) {
                            viewModel.createNewFridge(newFridgeName)
                            showAddFridgeDialog = false
                            newFridgeName = ""
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
                    newFridgeName = ""
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
