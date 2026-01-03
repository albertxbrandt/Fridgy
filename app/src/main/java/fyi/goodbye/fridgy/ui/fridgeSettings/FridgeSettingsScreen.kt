package fyi.goodbye.fridgy.ui.fridgeSettings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.shared.components.LoadingState
import fyi.goodbye.fridgy.ui.shared.components.SimpleErrorState
import fyi.goodbye.fridgy.ui.theme.*

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
                title = {
                    Text(
                        stringResource(R.string.fridge_settings),
                        color = FridgyWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = FridgyWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = uiState) {
            is FridgeSettingsViewModel.FridgeSettingsUiState.Loading -> {
                LoadingState(modifier = Modifier.padding(paddingValues))
            }
            is FridgeSettingsViewModel.FridgeSettingsUiState.Error -> {
                SimpleErrorState(
                    message = "Error: ${state.message}",
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is FridgeSettingsViewModel.FridgeSettingsUiState.Success -> {
                val fridge = state.fridge
                val isOwner = fridge.createdByUid == viewModel.currentUserId

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SettingsSection(title = stringResource(R.string.general_info)) {
                            Column {
                                SettingsItem(label = stringResource(R.string.name), value = fridge.name)
                                SettingsItem(label = stringResource(R.string.owner), value = fridge.creatorDisplayName)
                                SettingsItem(label = stringResource(R.string.location), value = stringResource(R.string.location_kitchen))
                            }
                        }
                    }

                    item {
                        SettingsSection(
                            title = stringResource(R.string.members),
                            action =
                                if (isOwner) {
                                    {
                                        IconButton(onClick = { showInviteDialog = true }) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = stringResource(R.string.cd_invite),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                } else {
                                    null
                                }
                        ) {
                            Column {
                                // Existing members (excluding the owner)
                                fridge.memberUsers.forEach { member ->
                                    if (member.uid != fridge.createdByUid) {
                                        SwipeToDismissMember(
                                            name = member.username,
                                            isOwner = isOwner,
                                            onRemove = { viewModel.removeMember(member.uid) }
                                        )
                                    }
                                }
                                // Pending invites
                                fridge.pendingInviteUsers.forEach { invitedUser ->
                                    SwipeToDismissMember(
                                        name = "${invitedUser.username} (Pending)",
                                        isOwner = isOwner,
                                        onRemove = { viewModel.revokeInvite(invitedUser.uid) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                if (isOwner) {
                                    showDeleteConfirmDialog = true
                                } else {
                                    showLeaveConfirmDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isDeletingOrLeaving
                        ) {
                            if (isDeletingOrLeaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onError,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    if (isOwner) stringResource(R.string.delete_fridge) else stringResource(R.string.leave_fridge),
                                    color = MaterialTheme.colorScheme.onError
                                )
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
            title = {
                Text(
                    stringResource(R.string.invite_member),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = inviteEmail,
                        onValueChange = { inviteEmail = it },
                        label = { Text(stringResource(R.string.username)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isInviting,
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
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
                        Text(stringResource(R.string.send_invite), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                    Text(stringResource(R.string.cancel))
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
            title = {
                Text(
                    stringResource(R.string.delete_fridge),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(stringResource(R.string.delete_confirmation_message))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.type_confirm), fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.confirm_text)) },
                        colors =
                            OutlinedTextFieldDefaults.colors(
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
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    confirmText = ""
                }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            title = {
                Text(
                    stringResource(R.string.leave_fridge),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(R.string.leave_fridge_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.leaveFridge {
                        showLeaveConfirmDialog = false
                        onDeleteSuccess()
                    }
                }) {
                    Text(
                        stringResource(R.string.leave),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
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
        SettingsItem(label = stringResource(R.string.member), value = name)
        return
    }

    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = {
                if (it == SwipeToDismissBoxValue.EndToStart) {
                    onRemove()
                    true
                } else {
                    false
                }
            }
        )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color =
                when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                    else -> Color.Transparent
                }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(color, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_remove),
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        },
        content = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                SettingsItem(label = stringResource(R.string.member), value = name)
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
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                action?.invoke()
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            content()
        }
    }
}

/**
 * A simple label-value pair displayed in a single row within a settings section.
 */
@Composable
fun SettingsItem(
    label: String,
    value: String
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}
