package fyi.goodbye.fridgy.ui.householdSettings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.InviteCode
import fyi.goodbye.fridgy.ui.fridgeSettings.SettingsItem
import fyi.goodbye.fridgy.ui.fridgeSettings.SettingsSection
import fyi.goodbye.fridgy.ui.shared.components.LoadingState
import fyi.goodbye.fridgy.ui.shared.components.SimpleErrorState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen displaying household settings including members and invite codes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdSettingsScreen(
    householdId: String,
    onBackClick: () -> Unit,
    onDeleteSuccess: () -> Unit,
    viewModel: HouseholdSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val inviteCodes by viewModel.inviteCodes.collectAsState()
    val isCreatingInvite by viewModel.isCreatingInvite.collectAsState()
    val newInviteCode by viewModel.newInviteCode.collectAsState()
    val isDeletingOrLeaving by viewModel.isDeletingOrLeaving.collectAsState()
    val actionError by viewModel.actionError.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var showCreateInviteDialog by remember { mutableStateOf(false) }
    var showNewCodeDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val dateFormatter =
        remember {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        }

    // Show new code dialog when a code is created
    LaunchedEffect(newInviteCode) {
        if (newInviteCode != null) {
            showCreateInviteDialog = false
            showNewCodeDialog = true
        }
    }

    // Show error snackbar
    LaunchedEffect(actionError) {
        if (actionError != null) {
            snackbarHostState.showSnackbar(actionError!!)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.household_settings),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = uiState) {
            is HouseholdSettingsViewModel.HouseholdSettingsUiState.Loading -> {
                LoadingState(modifier = Modifier.padding(paddingValues))
            }
            is HouseholdSettingsViewModel.HouseholdSettingsUiState.Error -> {
                SimpleErrorState(
                    message = stringResource(R.string.error_prefix, state.message),
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is HouseholdSettingsViewModel.HouseholdSettingsUiState.Success -> {
                val household = state.household
                val isOwner = household.createdByUid == viewModel.currentUserId

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // General Info
                    SettingsSection(title = stringResource(R.string.general_info)) {
                        Column {
                            SettingsItem(
                                label = stringResource(R.string.name),
                                value = household.name
                            )
                            SettingsItem(
                                label = stringResource(R.string.owner),
                                value = household.ownerDisplayName
                            )
                            SettingsItem(
                                label = stringResource(R.string.fridges),
                                value = household.fridgeCount.toString()
                            )
                        }
                    }

                    // Members (scrollable if many)
                    SettingsSection(title = stringResource(R.string.members)) {
                        Column(
                            modifier =
                                Modifier.heightIn(max = 120.dp)
                                    .verticalScroll(rememberScrollState())
                        ) {
                            household.memberUsers.forEach { member ->
                                SwipeToDismissMember(
                                    name =
                                        if (member.uid == household.createdByUid) {
                                            stringResource(R.string.owner_suffix, member.username)
                                        } else {
                                            member.username
                                        },
                                    isOwner = isOwner,
                                    canRemove = member.uid != household.createdByUid,
                                    onRemove = { viewModel.removeMember(member.uid) }
                                )
                            }
                        }
                    }

                    // Invite Codes (Owner only) - fills remaining space
                    if (isOwner) {
                        SettingsSection(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.invite_codes),
                            action = {
                                IconButton(
                                    onClick = { showCreateInviteDialog = true },
                                    enabled = !isCreatingInvite
                                ) {
                                    if (isCreatingInvite) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = stringResource(R.string.create_invite_code),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        ) {
                            if (inviteCodes.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.no_invite_codes_yet),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                // Scrollable invite codes list
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    inviteCodes.forEach { code ->
                                        InviteCodeItem(
                                            inviteCode = code,
                                            dateFormatter = dateFormatter,
                                            onCopy = {
                                                clipboardManager.setText(AnnotatedString(code.code))
                                            },
                                            onShare = {
                                                val shareText =
                                                    context.getString(
                                                        R.string.share_invite_message,
                                                        household.name,
                                                        code.code
                                                    )
                                                val intent =
                                                    Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                                    }
                                                context.startActivity(
                                                    Intent.createChooser(intent, "Share Invite Code")
                                                )
                                            },
                                            onRevoke = { viewModel.revokeInviteCode(code.code) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Non-owner: add spacer to push button to bottom
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Leave/Delete button
                    Button(
                        onClick = {
                            if (isOwner) {
                                showDeleteConfirmDialog = true
                            } else {
                                showLeaveConfirmDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
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
                                text =
                                    if (isOwner) {
                                        stringResource(R.string.delete_household)
                                    } else {
                                        stringResource(R.string.leave_household)
                                    },
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }
            }
        }
    }

    // Create Invite Dialog
    if (showCreateInviteDialog) {
        CreateInviteCodeDialog(
            onDismiss = { showCreateInviteDialog = false },
            onCreate = { expiresInDays ->
                viewModel.createInviteCode(expiresInDays)
            }
        )
    }

    // New Code Created Dialog
    if (showNewCodeDialog && newInviteCode != null) {
        AlertDialog(
            onDismissRequest = {
                showNewCodeDialog = false
                viewModel.clearNewInviteCode()
            },
            title = {
                Text(
                    stringResource(R.string.invite_code_created),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = newInviteCode!!.code,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 8.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (newInviteCode!!.expiresAt != null) {
                        Text(
                            text =
                                stringResource(
                                    R.string.invite_code_expires,
                                    dateFormatter.format(Date(newInviteCode!!.expiresAt!!))
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.invite_code_never_expires),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                val state = uiState
                val householdName =
                    if (state is HouseholdSettingsViewModel.HouseholdSettingsUiState.Success) {
                        state.household.name
                    } else {
                        ""
                    }

                FilledTonalButton(
                    onClick = {
                        val shareText =
                            context.getString(
                                R.string.share_invite_message,
                                householdName,
                                newInviteCode!!.code
                            )
                        val intent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                        context.startActivity(
                            Intent.createChooser(intent, context.getString(R.string.share_invite_code_chooser))
                        )
                    }
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.share_invite_code))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(newInviteCode!!.code))
                    showNewCodeDialog = false
                    viewModel.clearNewInviteCode()
                }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.cd_copy))
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    stringResource(R.string.delete_household),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(R.string.delete_household_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.type_confirm),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.confirm_text)) },
                        shape = MaterialTheme.shapes.medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteHousehold {
                            showDeleteConfirmDialog = false
                            onDeleteSuccess()
                        }
                    },
                    enabled = confirmText == "CONFIRM",
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                ) {
                    Text(stringResource(R.string.delete))
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
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Leave Confirmation Dialog
    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            title = {
                Text(
                    stringResource(R.string.leave_household),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    stringResource(R.string.leave_household_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.leaveHousehold {
                            showLeaveConfirmDialog = false
                            onDeleteSuccess()
                        }
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                ) {
                    Text(stringResource(R.string.leave))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun CreateInviteCodeDialog(
    onDismiss: () -> Unit,
    onCreate: (Int?) -> Unit
) {
    var selectedExpiry by remember { mutableStateOf(7) } // Default 7 days

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.create_invite_code),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.invite_code_validity_question),
                    style = MaterialTheme.typography.bodyMedium
                )

                Column {
                    @Composable
                    fun expiryOptions() =
                        listOf(
                            1 to stringResource(R.string.one_day),
                            7 to stringResource(R.string.seven_days),
                            30 to stringResource(R.string.thirty_days),
                            -1 to stringResource(R.string.invite_code_never_expires)
                        )
                    expiryOptions().forEach { (days, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedExpiry == days,
                                onClick = { selectedExpiry = days }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onCreate(if (selectedExpiry == -1) null else selectedExpiry)
                }
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun InviteCodeItem(
    inviteCode: InviteCode,
    dateFormatter: SimpleDateFormat,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRevoke: () -> Unit
) {
    val isExpired = inviteCode.isExpired()
    val isUsed = inviteCode.usedBy != null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color =
            if (isExpired || isUsed || !inviteCode.isActive) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = inviteCode.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (isExpired || isUsed || !inviteCode.isActive) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                )

                when {
                    isUsed -> {
                        Text(
                            text = stringResource(R.string.invite_code_used),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    isExpired -> {
                        Text(
                            text = stringResource(R.string.invite_code_expired),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    !inviteCode.isActive -> {
                        Text(
                            text = stringResource(R.string.invite_code_revoked),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    inviteCode.expiresAt != null -> {
                        Text(
                            text =
                                stringResource(
                                    R.string.expires_on,
                                    dateFormatter.format(Date(inviteCode.expiresAt!!))
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.invite_code_never_expires),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (inviteCode.isValid()) {
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = stringResource(R.string.cd_share),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRevoke, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_revoke),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissMember(
    name: String,
    isOwner: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    if (!isOwner || !canRemove) {
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
