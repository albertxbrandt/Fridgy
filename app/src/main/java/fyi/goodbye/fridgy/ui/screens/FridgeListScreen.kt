package fyi.goodbye.fridgy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.ui.elements.FridgeCard
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue
import fyi.goodbye.fridgy.ui.theme.FridgyLightBlue
import fyi.goodbye.fridgy.ui.theme.FridgyTextBlue
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
import fyi.goodbye.fridgy.ui.viewmodels.FridgeListViewModel

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
    onLogout: () -> Unit,
    viewModel: FridgeListViewModel = viewModel()
) {
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showAddFridgeDialog by remember { mutableStateOf(false) }
    var newFridgeName by remember { mutableStateOf("") }

    val fridgeUiState by viewModel.fridgesUiState.collectAsState()
    val invites by viewModel.invites.collectAsState()
    val auth = remember { FirebaseAuth.getInstance() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "My Fridges",
                        color = FridgyWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = FridgyDarkBlue
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
                                        containerColor = Color.Red,
                                        contentColor = Color.White,
                                        modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                                    ) {
                                        Text(
                                            text = invites.size.toString(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = FridgyWhite
                            )
                        }
                    }
                    IconButton(onClick = onProfileClick) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = FridgyWhite
                        )
                    }
                    IconButton(onClick = {
                        auth.signOut()
                        onLogout()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout",
                            tint = FridgyWhite
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddFridgeDialog = true },
                containerColor = FridgyDarkBlue,
                contentColor = FridgyWhite
            ) {
                Icon(Icons.Default.Add, "Add new fridge")
            }
        },
        containerColor = FridgyLightBlue
    ) { paddingValues ->
        when (val state = fridgeUiState) {
            FridgeListViewModel.FridgeUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = FridgyDarkBlue)
                }
            }
            is FridgeListViewModel.FridgeUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(onClick = { /* Retry logic could be added to ViewModel */ }) {
                        Text("Retry")
                    }
                }
            }
            is FridgeListViewModel.FridgeUiState.Success -> {
                val fridges = state.fridges
                if (fridges.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No fridges yet! Click the '+' button to create one.",
                            fontSize = 18.sp,
                            color = FridgyTextBlue.copy(alpha = 0.8f),
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center
                        )
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
                        items(fridges) { fridge ->
                            FridgeCard(fridge = fridge) { clickedFridge ->
                                onNavigateToFridgeInventory(clickedFridge)
                            }
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
                    text = "Create New Fridge",
                    color = FridgyWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                OutlinedTextField(
                    value = newFridgeName,
                    onValueChange = { newFridgeName = it },
                    label = { Text("Fridge Name", color = FridgyWhite.copy(alpha = 0.7f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FridgyWhite,
                        unfocusedTextColor = FridgyWhite,
                        focusedBorderColor = FridgyWhite,
                        unfocusedBorderColor = FridgyWhite.copy(alpha = 0.5f),
                        focusedLabelColor = FridgyWhite,
                        cursorColor = FridgyWhite
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFridgeName.isNotBlank()) {
                            viewModel.createNewFridge(newFridgeName)
                            showAddFridgeDialog = false
                            newFridgeName = ""
                        }
                    },
                    enabled = newFridgeName.isNotBlank()
                ) {
                    Text("Create", color = FridgyWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddFridgeDialog = false
                    newFridgeName = ""
                }) {
                    Text("Cancel", color = FridgyWhite.copy(alpha = 0.7f))
                }
            },
            containerColor = FridgyDarkBlue,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * A dialog that displays a list of pending fridge invitations.
 * 
 * Users can accept or decline invitations directly from this dialog.
 *
 * @param invites The list of pending [Fridge] invitations.
 * @param onAccept Callback when an invitation is accepted.
 * @param onDecline Callback when an invitation is declined.
 * @param onDismissRequest Callback to close the dialog.
 */
@Composable
fun NotificationsDialog(
    invites: List<Fridge>,
    onAccept: (Fridge) -> Unit,
    onDecline: (Fridge) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Invitations",
                color = FridgyDarkBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            LazyColumn {
                if (invites.isEmpty()) {
                    item {
                        Text(
                            text = "No pending invitations.",
                            color = FridgyTextBlue.copy(alpha = 0.8f)
                        )
                    }
                } else {
                    items(invites) { invite ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Invite to join '${invite.name}'",
                                fontSize = 16.sp,
                                color = FridgyTextBlue,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TextButton(onClick = { onDecline(invite) }) {
                                    Text("Decline", color = Color.Red)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                TextButton(onClick = { onAccept(invite) }) {
                                    Text("Accept", color = FridgyDarkBlue)
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 8.dp),
                                color = FridgyLightBlue.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close", color = FridgyDarkBlue)
            }
        },
        containerColor = FridgyWhite,
        shape = RoundedCornerShape(16.dp)
    )
}
