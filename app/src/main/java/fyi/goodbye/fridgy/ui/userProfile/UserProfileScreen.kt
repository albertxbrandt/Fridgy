package fyi.goodbye.fridgy.ui.userProfile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.shared.UiState
import fyi.goodbye.fridgy.ui.shared.components.SquaredInput

/**
 * User profile management screen.
 *
 * Allows users to:
 * - View their current username and email
 * - Update their username
 * - Delete their account (with confirmation)
 *
 * ## Navigation
 * - Accessed from the sidebar menu "Account" button
 * - Returns to household list on back press
 * - Navigates to magic link screen after account deletion
 *
 * ## Features
 * - Real-time username validation
 * - Duplicate username checking
 * - Confirmation dialog for account deletion
 * - Loading states and error handling
 *
 * @param onBackClick Callback invoked when back button is pressed.
 * @param onAccountDeleted Callback invoked after successful account deletion (navigate to login).
 * @param viewModel ViewModel managing profile state, injected via Hilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onBackClick: () -> Unit,
    onAccountDeleted: () -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val usernameUpdateState by viewModel.usernameUpdateState.collectAsState()
    val accountDeletionState by viewModel.accountDeletionState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showUsernameDialog by remember { mutableStateOf(false) }

    // Handle account deletion success
    LaunchedEffect(accountDeletionState) {
        if (accountDeletionState is UiState.Success) {
            onAccountDeleted()
        }
    }

    // Handle username update feedback
    LaunchedEffect(usernameUpdateState) {
        when (usernameUpdateState) {
            is UiState.Success -> {
                snackbarHostState.showSnackbar("Username updated successfully")
                viewModel.resetUsernameUpdateState()
                showUsernameDialog = false
            }
            is UiState.Error -> {
                snackbarHostState.showSnackbar(
                    (usernameUpdateState as UiState.Error).message
                )
                viewModel.resetUsernameUpdateState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.account),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = uiState) {
            is UiState.Idle,
            is UiState.Loading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            is UiState.Success -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Profile Information Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.profile_information),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Email (read-only)
                        Column {
                            Text(
                                text = stringResource(R.string.email),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = state.data.email,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Username
                        Column {
                            Text(
                                text = stringResource(R.string.username),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = state.data.username,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        OutlinedButton(
                            onClick = { showUsernameDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.change_username))
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Danger Zone
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.danger_zone),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )

                        Text(
                            text = stringResource(R.string.delete_account_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = { showDeleteConfirmation = true },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = accountDeletionState !is UiState.Loading
                        ) {
                            if (accountDeletionState is UiState.Loading) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            Text(stringResource(R.string.delete_account))
                        }
                    }
                }
            }
        }
    }

    // Username Update Dialog
    if (showUsernameDialog) {
        UsernameUpdateDialog(
            currentUsername = (uiState as? UiState.Success)?.data?.username ?: "",
            isUpdating = usernameUpdateState is UiState.Loading,
            onDismiss = {
                showUsernameDialog = false
                viewModel.resetUsernameUpdateState()
            },
            onConfirm = { newUsername ->
                viewModel.updateUsername(newUsername)
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    text = stringResource(R.string.delete_account_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(stringResource(R.string.delete_account_confirm_message))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deleteAccount()
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Dialog for updating username with validation.
 */
@Composable
private fun UsernameUpdateDialog(
    currentUsername: String,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newUsername by remember { mutableStateOf(currentUsername) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = stringResource(R.string.change_username),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.change_username_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SquaredInput(
                    value = newUsername,
                    onValueChange = { newUsername = it },
                    label = { Text(stringResource(R.string.new_username)) },
                    enabled = !isUpdating
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newUsername) },
                enabled = !isUpdating && newUsername.trim().isNotEmpty()
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUpdating
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
