package fyi.goodbye.fridgy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.goodbye.fridgy.ui.theme.*
import fyi.goodbye.fridgy.ui.viewmodels.FridgeSettingsViewModel

/**
 * Screen displaying the settings and management options for a specific fridge.
 * 
 * Allows users to view members, invite new members (if owner), leave the fridge,
 * or delete the fridge (if owner). It uses confirmation dialogs for destructive actions.
 *
 * @param fridgeId The unique ID of the fridge.
 * @param onBackClick Callback to return to the previous screen.
 * @param onDeleteSuccess Callback triggered after a successful delete or leave operation.
 * @param viewModel The state holder for fridge settings logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeSettingsScreen(
    fridgeId: String,
    onBackClick: () -> Unit,
    onDeleteSuccess: () -> Unit,
    viewModel: FridgeSettingsViewModel = viewModel(factory = FridgeSettingsViewModel.provideFactory(fridgeId))
) {
    val uiState by viewModel.uiState.collectAsState()
    val isInviting by viewModel.isInviting.collectAsState()
    val inviteError by viewModel.inviteError.collectAsState()
    val inviteSuccess by viewModel.inviteSuccess.collectAsState()
    val isDeletingOrLeaving by viewModel.isDeletingOrLeaving.collectAsState()
    
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteEmail by remember { mutableStateOf("") }
    
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }
    
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(inviteSuccess) {
        if (inviteSuccess) {
            snackbarHostState.showSnackbar("Invitation sent successfully!")
            viewModel.clearInviteStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Fridge Settings", color = FridgyWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FridgyWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = uiState) {
            is FridgeSettingsViewModel.FridgeSettingsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is FridgeSettingsViewModel.FridgeSettingsUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is FridgeSettingsViewModel.FridgeSettingsUiState.Success -> {
                val fridge = state.fridge
                val isOwner = fridge.createdByUid == viewModel.currentUserId
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SettingsSection(title = "General Info") {
                            Column {
                                SettingsItem(label = "Name", value = fridge.name)
                                SettingsItem(label = "Owner", value = fridge.creatorDisplayName)
                                SettingsItem(label = "Location", value = "Kitchen")
                            }
                        }
                    }

                    item {
                        SettingsSection(
                            title = "Members",
                            action = if (isOwner) {
                                {
                                    IconButton(onClick = { showInviteDialog = true }) {
                                        Icon(Icons.Default.Add, contentDescription = "Invite", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            } else null
                        ) {
                            Column {
                                // Existing members (excluding the owner)
                                fridge.members.forEach { (uid, memberName) ->
                                    if (uid != fridge.createdByUid) {
                                        SwipeToDismissMember(
                                            name = memberName,
                                            isOwner = isOwner,
                                            onRemove = { viewModel.removeMember(uid) }
                                        )
                                    }
                                }
                                // Pending invites
                                fridge.pendingInvites.forEach { (uid, pendingName) ->
                                    SwipeToDismissMember(
                                        name = "$pendingName (Pending)",
                                        isOwner = isOwner,
                                        onRemove = { viewModel.revokeInvite(uid) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { 
                                if (isOwner) showDeleteConfirmDialog = true 
                                else showLeaveConfirmDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isDeletingOrLeaving
                        ) {
                            if (isDeletingOrLeaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                            } else {
                                Text(if (isOwner) "Delete Fridge" else "Leave Fridge", color = MaterialTheme.colorScheme.onError)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isInviting) showInviteDialog = false },
            title = { Text("Invite Member", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = inviteEmail,
                        onValueChange = { inviteEmail = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isInviting,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    if (inviteError != null) {
                        Text(
                            text = inviteError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.inviteMember(inviteEmail)
                    },
                    enabled = !isInviting && inviteEmail.isNotBlank()
                ) {
                    if (isInviting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Send Invite", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showInviteDialog = false
                        inviteEmail = ""
                        viewModel.clearInviteStatus()
                    },
                    enabled = !isInviting
                ) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Automatically close invite dialog on success
    LaunchedEffect(inviteSuccess) {
        if (inviteSuccess) {
            showInviteDialog = false
            inviteEmail = ""
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Fridge", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("This action cannot be undone. All items and members will be removed.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Type CONFIRM to confirm deletion:", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("CONFIRM") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.error,
                            focusedLabelColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFridge {
                            showDeleteConfirmDialog = false
                            onDeleteSuccess()
                        }
                    },
                    enabled = confirmText == "CONFIRM"
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteConfirmDialog = false 
                    confirmText = ""
                }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            title = { Text("Leave Fridge", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to leave this fridge? You will need an invite to join again.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.leaveFridge {
                        showLeaveConfirmDialog = false
                        onDeleteSuccess()
                    }
                }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * A swipe-to-dismiss component for removing members or revoking invites.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissMember(
    name: String,
    isOwner: Boolean,
    onRemove: () -> Unit
) {
    if (!isOwner) {
        SettingsItem(label = "Member", value = name)
        return
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        },
        content = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                SettingsItem(label = "Member", value = name)
            }
        }
    )
}

/**
 * A reusable container for a settings group.
 */
@Composable
fun SettingsSection(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                action?.invoke()
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

/**
 * A simple label-value pair displayed in a single row within a settings section.
 */
@Composable
fun SettingsItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(text = value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}
