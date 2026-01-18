package fyi.goodbye.fridgy.ui.householdList

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fyi.goodbye.fridgy.R
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
            _uiState.value =
                JoinHouseholdUiState.Error(
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
                    _uiState.value =
                        JoinHouseholdUiState.Error(
                            getApplication<Application>().getString(R.string.error_invalid_invite_code)
                        )
                }
            } catch (e: Exception) {
                _uiState.value =
                    JoinHouseholdUiState.Error(
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
                _uiState.value =
                    JoinHouseholdUiState.Error(
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
        fun provideFactory(): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                    JoinHouseholdViewModel(app)
                }
            }
    }
}
