package fyi.goodbye.fridgy.ui.fridgeSettings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.shared.components.LoadingState
import fyi.goodbye.fridgy.ui.shared.components.SimpleErrorState

/**
 * Screen displaying the settings for a specific fridge.
 *
 * Allows users to view fridge details and delete the fridge (if creator).
 * Member management has moved to the household level.
 *
 * @param fridgeId The unique ID of the fridge.
 * @param onBackClick Callback to return to the previous screen.
 * @param onDeleteSuccess Callback triggered after a successful delete operation.
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
    val isDeleting by viewModel.isDeleting.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.fridge_settings),
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
                    message = stringResource(R.string.error_prefix, state.message),
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is FridgeSettingsViewModel.FridgeSettingsUiState.Success -> {
                val fridge = state.fridge
                val isCreator = fridge.createdByUid == viewModel.currentUserId

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
                                SettingsItem(
                                    label = stringResource(R.string.created_by),
                                    value = fridge.creatorDisplayName
                                )
                                SettingsItem(
                                    label = stringResource(R.string.type),
                                    value = state.fridgeData.type.replaceFirstChar { it.uppercase() }
                                )
                                if (state.fridgeData.location.isNotBlank()) {
                                    SettingsItem(
                                        label = stringResource(R.string.location),
                                        value = state.fridgeData.location
                                    )
                                }
                            }
                        }
                    }

                    // Only the creator can delete the fridge
                    if (isCreator) {
                        item {
                            Button(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isDeleting
                            ) {
                                if (isDeleting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onError,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.delete_fridge),
                                        color = MaterialTheme.colorScheme.onError
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    stringResource(R.string.delete_fridge),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(R.string.delete_confirmation_message),
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
                        viewModel.deleteFridge {
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
}

/**
 * A reusable container for a settings group.
 */
@Composable
fun SettingsSection(
    modifier: Modifier = Modifier,
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
