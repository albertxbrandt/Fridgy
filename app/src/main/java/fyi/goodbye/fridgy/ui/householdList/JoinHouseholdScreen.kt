package fyi.goodbye.fridgy.ui.householdList

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.goodbye.fridgy.R

/**
 * Screen for joining a household with an invite code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinHouseholdScreen(
    onNavigateBack: () -> Unit,
    onJoinSuccess: (String) -> Unit,
    viewModel: JoinHouseholdViewModel = viewModel(factory = JoinHouseholdViewModel.provideFactory())
) {
    val uiState by viewModel.uiState.collectAsState()
    val inviteCode by viewModel.inviteCode.collectAsState()

    // Handle success navigation
    LaunchedEffect(uiState) {
        if (uiState is JoinHouseholdViewModel.JoinHouseholdUiState.Success) {
            val householdId = (uiState as JoinHouseholdViewModel.JoinHouseholdUiState.Success).householdId
            onJoinSuccess(householdId)
        }
    }

    JoinHouseholdContent(
        inviteCode = inviteCode,
        uiState = uiState,
        onInviteCodeChange = { viewModel.updateInviteCode(it) },
        onValidateCode = { viewModel.validateCode() },
        onJoinHousehold = { viewModel.joinHousehold() },
        onResetState = { viewModel.resetState() },
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinHouseholdContent(
    inviteCode: String,
    uiState: JoinHouseholdViewModel.JoinHouseholdUiState,
    onInviteCodeChange: (String) -> Unit,
    onValidateCode: () -> Unit,
    onJoinHousehold: () -> Unit,
    onResetState: () -> Unit,
    onNavigateBack: () -> Unit
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.join_household),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.enter_invite_code),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Enter the 6-character invite code you received from a household member.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Invite code input
            OutlinedTextField(
                value = inviteCode,
                onValueChange = {
                    onInviteCodeChange(it)
                    if (uiState is JoinHouseholdViewModel.JoinHouseholdUiState.Error) {
                        onResetState()
                    }
                },
                label = { Text(stringResource(R.string.invite_code)) },
                placeholder = { Text("ABC123") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                textStyle =
                    MaterialTheme.typography.headlineMedium.copy(
                        textAlign = TextAlign.Center,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp)
                    ),
                isError = uiState is JoinHouseholdViewModel.JoinHouseholdUiState.Error,
                enabled =
                    uiState !is JoinHouseholdViewModel.JoinHouseholdUiState.Validating &&
                        uiState !is JoinHouseholdViewModel.JoinHouseholdUiState.Joining
            )

            // Error message
            if (uiState is JoinHouseholdViewModel.JoinHouseholdUiState.Error) {
                Text(
                    text = (uiState as JoinHouseholdViewModel.JoinHouseholdUiState.Error).message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            // Show household info when code is valid
            if (uiState is JoinHouseholdViewModel.JoinHouseholdUiState.CodeValid) {
                val householdName = (uiState as JoinHouseholdViewModel.JoinHouseholdUiState.CodeValid).householdName
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
                                .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "You're about to join:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = householdName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action button
            when (val state = uiState) {
                is JoinHouseholdViewModel.JoinHouseholdUiState.Idle,
                is JoinHouseholdViewModel.JoinHouseholdUiState.Error -> {
                    Button(
                        onClick = onValidateCode,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = inviteCode.length == 6
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }
                is JoinHouseholdViewModel.JoinHouseholdUiState.Validating -> {
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Validating...")
                    }
                }
                is JoinHouseholdViewModel.JoinHouseholdUiState.CodeValid -> {
                    Button(
                        onClick = onJoinHousehold,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.join))
                    }
                }
                is JoinHouseholdViewModel.JoinHouseholdUiState.Joining -> {
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Joining...")
                    }
                }
                is JoinHouseholdViewModel.JoinHouseholdUiState.Success -> {
                    // Will navigate via LaunchedEffect
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun JoinHouseholdPreview() {
    fyi.goodbye.fridgy.ui.theme.FridgyTheme {
        JoinHouseholdContent(
            inviteCode = "",
            uiState = JoinHouseholdViewModel.JoinHouseholdUiState.Idle,
            onInviteCodeChange = {},
            onValidateCode = {},
            onJoinHousehold = {},
            onResetState = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun JoinHouseholdPreviewWithCode() {
    fyi.goodbye.fridgy.ui.theme.FridgyTheme {
        JoinHouseholdContent(
            inviteCode = "ABC123",
            uiState = JoinHouseholdViewModel.JoinHouseholdUiState.CodeValid("Smith Family"),
            onInviteCodeChange = {},
            onValidateCode = {},
            onJoinHousehold = {},
            onResetState = {},
            onNavigateBack = {}
        )
    }
}
