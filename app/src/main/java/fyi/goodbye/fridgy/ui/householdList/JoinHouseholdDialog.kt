package fyi.goodbye.fridgy.ui.householdList

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.shared.UiState

/**
 * Dialog for joining a household with an invite code.
 */
@Composable
fun JoinHouseholdDialog(
    onDismiss: () -> Unit,
    onJoinSuccess: (String) -> Unit,
    viewModel: JoinHouseholdViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val inviteCode by viewModel.inviteCode.collectAsState()

    // Handle success navigation
    LaunchedEffect(uiState) {
        val currentState = uiState
        if (currentState is UiState.Success && currentState.data is JoinHouseholdViewModel.JoinState.Success) {
            val householdId = (currentState.data as JoinHouseholdViewModel.JoinState.Success).householdId
            onJoinSuccess(householdId)
        }
    }

    JoinHouseholdDialogContent(
        inviteCode = inviteCode,
        uiState = uiState,
        onInviteCodeChange = { viewModel.updateInviteCode(it) },
        onValidateCode = { viewModel.validateCode() },
        onJoinHousehold = { viewModel.joinHousehold() },
        onResetState = { viewModel.resetState() },
        onDismiss = onDismiss
    )
}

@Composable
private fun JoinHouseholdDialogContent(
    inviteCode: String,
    uiState: UiState<JoinHouseholdViewModel.JoinState>,
    onInviteCodeChange: (String) -> Unit,
    onValidateCode: () -> Unit,
    onJoinHousehold: () -> Unit,
    onResetState: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!(
                    uiState is UiState.Success &&
                        (
                            uiState.data is JoinHouseholdViewModel.JoinState.Validating ||
                                uiState.data is JoinHouseholdViewModel.JoinState.Joining
                        )
                )
            ) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = stringResource(R.string.join_household),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.enter_invite_code_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Invite code input
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = {
                        onInviteCodeChange(it)
                        if (uiState is UiState.Error) {
                            onResetState()
                        }
                    },
                    label = { Text(stringResource(R.string.invite_code)) },
                    placeholder = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "ABC123",
                                style =
                                    MaterialTheme.typography.headlineSmall.copy(
                                        letterSpacing =
                                            androidx.compose.ui.unit.TextUnit(
                                                8f,
                                                androidx.compose.ui.unit.TextUnitType.Sp
                                            )
                                    )
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                    textStyle =
                        MaterialTheme.typography.headlineSmall.copy(
                            textAlign = TextAlign.Center,
                            letterSpacing =
                                androidx.compose.ui.unit.TextUnit(
                                    8f,
                                    androidx.compose.ui.unit.TextUnitType.Sp
                                )
                        ),
                    isError = uiState is UiState.Error,
                    enabled =
                        !(
                            uiState is UiState.Success &&
                                (
                                    uiState.data is JoinHouseholdViewModel.JoinState.Validating ||
                                        uiState.data is JoinHouseholdViewModel.JoinState.Joining
                                )
                        )
                )

                // Error message
                if (uiState is UiState.Error) {
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Show household info when code is valid
                if (uiState is UiState.Success && uiState.data is JoinHouseholdViewModel.JoinState.CodeValid) {
                    val householdName = (uiState.data as JoinHouseholdViewModel.JoinState.CodeValid).householdName
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.about_to_join),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = householdName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Show info when user is already a member
                if (uiState is UiState.Success && uiState.data is JoinHouseholdViewModel.JoinState.AlreadyMember) {
                    val householdName = (uiState.data as JoinHouseholdViewModel.JoinState.AlreadyMember).householdName
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.already_member_of),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = householdName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (uiState) {
                UiState.Idle,
                is UiState.Loading,
                is UiState.Error -> {
                    Button(
                        onClick = onValidateCode,
                        enabled = inviteCode.length == 6
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }
                is UiState.Success -> {
                    when (val state = uiState.data) {
                        JoinHouseholdViewModel.JoinState.Idle -> {
                            Button(
                                onClick = onValidateCode,
                                enabled = inviteCode.length == 6
                            ) {
                                Text(stringResource(R.string.confirm))
                            }
                        }
                        is JoinHouseholdViewModel.JoinState.Validating -> {
                            Button(
                                onClick = { },
                                enabled = false
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.validating))
                            }
                        }
                        is JoinHouseholdViewModel.JoinState.CodeValid -> {
                            Button(
                                onClick = onJoinHousehold
                            ) {
                                Text(stringResource(R.string.join))
                            }
                        }
                        is JoinHouseholdViewModel.JoinState.AlreadyMember -> {
                            Button(
                                onClick = { },
                                enabled = false,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                            ) {
                                Text(stringResource(R.string.already_a_member))
                            }
                        }
                        is JoinHouseholdViewModel.JoinState.Joining -> {
                            Button(
                                onClick = { },
                                enabled = false
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.joining))
                            }
                        }
                        is JoinHouseholdViewModel.JoinState.Success -> {
                            // Will navigate via LaunchedEffect
                            Button(
                                onClick = { },
                                enabled = false
                            ) {
                                Text(stringResource(R.string.join))
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (!(
                    uiState is UiState.Success &&
                        (
                            uiState.data is JoinHouseholdViewModel.JoinState.Validating ||
                                uiState.data is JoinHouseholdViewModel.JoinState.Joining
                        )
                )
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Preview(showBackground = true)
@Composable
private fun JoinHouseholdDialogPreview() {
    fyi.goodbye.fridgy.ui.theme.FridgyTheme {
        JoinHouseholdDialogContent(
            inviteCode = "",
            uiState = UiState.Success(JoinHouseholdViewModel.JoinState.Idle),
            onInviteCodeChange = {},
            onValidateCode = {},
            onJoinHousehold = {},
            onResetState = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun JoinHouseholdDialogPreviewWithCode() {
    fyi.goodbye.fridgy.ui.theme.FridgyTheme {
        JoinHouseholdDialogContent(
            inviteCode = "ABC123",
            uiState = UiState.Success(JoinHouseholdViewModel.JoinState.CodeValid("Smith Family")),
            onInviteCodeChange = {},
            onValidateCode = {},
            onJoinHousehold = {},
            onResetState = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun JoinHouseholdDialogPreviewAlreadyMember() {
    fyi.goodbye.fridgy.ui.theme.FridgyTheme {
        JoinHouseholdDialogContent(
            inviteCode = "ABC123",
            uiState = UiState.Success(JoinHouseholdViewModel.JoinState.AlreadyMember("Smith Family")),
            onInviteCodeChange = {},
            onValidateCode = {},
            onJoinHousehold = {},
            onResetState = {},
            onDismiss = {}
        )
    }
}
