package fyi.goodbye.fridgy.ui.householdList

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.InviteCode
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for joining a household with an invite code.
 */
class JoinHouseholdViewModel(
    application: Application,
    private val householdRepository: HouseholdRepository = HouseholdRepository()
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow<JoinHouseholdUiState>(JoinHouseholdUiState.Idle)
    val uiState: StateFlow<JoinHouseholdUiState> = _uiState.asStateFlow()
    
    private val _inviteCode = MutableStateFlow("")
    val inviteCode: StateFlow<String> = _inviteCode.asStateFlow()
    
    fun updateInviteCode(code: String) {
        // Only allow uppercase alphanumeric characters, max 6 chars
        _inviteCode.value = code.uppercase().filter { it.isLetterOrDigit() }.take(6)
    }
    
    fun validateCode() {
        val code = _inviteCode.value
        if (code.length != 6) {
            _uiState.value = JoinHouseholdUiState.Error(
                getApplication<Application>().getString(R.string.error_invalid_invite_code)
            )
            return
        }
        
        _uiState.value = JoinHouseholdUiState.Validating
        
        viewModelScope.launch {
            try {
                val result = householdRepository.validateInviteCode(code)
                if (result != null) {
                    _uiState.value = JoinHouseholdUiState.CodeValid(result.householdName)
                } else {
                    _uiState.value = JoinHouseholdUiState.Error(
                        getApplication<Application>().getString(R.string.error_invalid_invite_code)
                    )
                }
            } catch (e: Exception) {
                _uiState.value = JoinHouseholdUiState.Error(
                    e.message ?: getApplication<Application>().getString(R.string.error_invalid_invite_code)
                )
            }
        }
    }
    
    fun joinHousehold() {
        val code = _inviteCode.value
        _uiState.value = JoinHouseholdUiState.Joining
        
        viewModelScope.launch {
            try {
                val householdId = householdRepository.redeemInviteCode(code)
                _uiState.value = JoinHouseholdUiState.Success(householdId)
            } catch (e: Exception) {
                _uiState.value = JoinHouseholdUiState.Error(
                    e.message ?: getApplication<Application>().getString(R.string.error_failed_to_join_household)
                )
            }
        }
    }
    
    fun resetState() {
        _uiState.value = JoinHouseholdUiState.Idle
    }
    
    sealed interface JoinHouseholdUiState {
        data object Idle : JoinHouseholdUiState
        data object Validating : JoinHouseholdUiState
        data class CodeValid(val householdName: String) : JoinHouseholdUiState
        data object Joining : JoinHouseholdUiState
        data class Success(val householdId: String) : JoinHouseholdUiState
        data class Error(val message: String) : JoinHouseholdUiState
    }
    
    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                JoinHouseholdViewModel(app)
            }
        }
    }
}

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
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
                    viewModel.updateInviteCode(it)
                    if (uiState is JoinHouseholdViewModel.JoinHouseholdUiState.Error) {
                        viewModel.resetState()
                    }
                },
                label = { Text(stringResource(R.string.invite_code)) },
                placeholder = { Text("ABC123") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    textAlign = TextAlign.Center,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp)
                ),
                isError = uiState is JoinHouseholdViewModel.JoinHouseholdUiState.Error,
                enabled = uiState !is JoinHouseholdViewModel.JoinHouseholdUiState.Validating &&
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
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
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
                        onClick = { viewModel.validateCode() },
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
                        onClick = { viewModel.joinHousehold() },
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
