package fyi.goodbye.fridgy.ui.householdSettings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayHousehold
import fyi.goodbye.fridgy.models.InviteCode
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * ViewModel responsible for managing household settings.
 *
 * Handles member management, invite code generation, and household deletion/leave.
 */
class HouseholdSettingsViewModel(
    application: Application,
    private val householdRepository: HouseholdRepository = HouseholdRepository(),
    private val householdId: String
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow<HouseholdSettingsUiState>(HouseholdSettingsUiState.Loading)
    val uiState: StateFlow<HouseholdSettingsUiState> = _uiState.asStateFlow()
    
    private val _inviteCodes = MutableStateFlow<List<InviteCode>>(emptyList())
    val inviteCodes: StateFlow<List<InviteCode>> = _inviteCodes.asStateFlow()
    
    private val _isCreatingInvite = MutableStateFlow(false)
    val isCreatingInvite: StateFlow<Boolean> = _isCreatingInvite.asStateFlow()
    
    private val _newInviteCode = MutableStateFlow<InviteCode?>(null)
    val newInviteCode: StateFlow<InviteCode?> = _newInviteCode.asStateFlow()
    
    private val _isDeletingOrLeaving = MutableStateFlow(false)
    val isDeletingOrLeaving: StateFlow<Boolean> = _isDeletingOrLeaving.asStateFlow()
    
    // Flag to prevent Flow errors when user is leaving/deleted
    private var isLeavingOrDeleted = false
    
    // Job reference for cancelling the invite codes listener
    private var inviteCodesJob: Job? = null
    
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()
    
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    
    init {
        loadHouseholdDetails()
        loadInviteCodes()
    }
    
    private fun loadHouseholdDetails() {
        viewModelScope.launch {
            _uiState.value = HouseholdSettingsUiState.Loading
            try {
                val displayHousehold = householdRepository.getDisplayHouseholdById(householdId)
                if (displayHousehold != null) {
                    _uiState.value = HouseholdSettingsUiState.Success(displayHousehold)
                } else {
                    _uiState.value = HouseholdSettingsUiState.Error(
                        getApplication<Application>().getString(R.string.error_fridge_not_found)
                    )
                }
            } catch (e: Exception) {
                _uiState.value = HouseholdSettingsUiState.Error(
                    e.message ?: getApplication<Application>().getString(R.string.error_failed_to_load_fridge)
                )
                Log.e("HouseholdSettingsVM", "Error loading household: ${e.message}", e)
            }
        }
    }
    
    private fun loadInviteCodes() {
        inviteCodesJob = viewModelScope.launch {
            try {
                householdRepository.getInviteCodesFlow(householdId).collectLatest { codes ->
                    if (!isLeavingOrDeleted) {
                        _inviteCodes.value = codes.sortedByDescending { it.createdAt }
                    }
                }
            } catch (e: Exception) {
                // Ignore errors if we're leaving/deleted (permissions revoked)
                if (!isLeavingOrDeleted) {
                    Log.e("HouseholdSettingsVM", "Error loading invite codes: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Cancels all active Firestore listeners to prevent permission errors.
     */
    private fun cancelAllListeners() {
        inviteCodesJob?.cancel()
        inviteCodesJob = null
    }
    
    /**
     * Creates a new invite code for this household.
     *
     * @param expiresInDays Number of days until the code expires. Null for no expiration.
     */
    fun createInviteCode(expiresInDays: Int? = 7) {
        _isCreatingInvite.value = true
        _newInviteCode.value = null
        
        viewModelScope.launch {
            try {
                val expiresAt = if (expiresInDays != null) {
                    Instant.now().plus(expiresInDays.toLong(), ChronoUnit.DAYS).toEpochMilli()
                } else {
                    null
                }
                
                val code = householdRepository.createInviteCode(householdId, expiresAt)
                _newInviteCode.value = code
            } catch (e: Exception) {
                Log.e("HouseholdSettingsVM", "Error creating invite code: ${e.message}")
                _actionError.value = "Failed to create invite code: ${e.message}"
            } finally {
                _isCreatingInvite.value = false
            }
        }
    }
    
    /**
     * Revokes an invite code so it can no longer be used.
     */
    fun revokeInviteCode(code: String) {
        viewModelScope.launch {
            try {
                householdRepository.revokeInviteCode(code)
            } catch (e: Exception) {
                Log.e("HouseholdSettingsVM", "Error revoking invite code: ${e.message}")
                _actionError.value = "Failed to revoke invite code: ${e.message}"
            }
        }
    }
    
    /**
     * Removes a member from the household.
     */
    fun removeMember(userId: String) {
        viewModelScope.launch {
            try {
                householdRepository.removeMember(householdId, userId)
                loadHouseholdDetails() // Refresh
            } catch (e: Exception) {
                Log.e("HouseholdSettingsVM", "Error removing member: ${e.message}")
                _actionError.value = "Failed to remove member: ${e.message}"
            }
        }
    }
    
    /**
     * Leaves the household. Current user is removed from members list.
     * Navigates first, then performs the leave operation in the background.
     */
    fun leaveHousehold(onSuccess: () -> Unit) {
        _isDeletingOrLeaving.value = true
        _actionError.value = null
        isLeavingOrDeleted = true  // Set flag to prevent Flow errors
        
        // Cancel all listeners BEFORE leaving to prevent permission errors
        cancelAllListeners()
        
        // Navigate away FIRST to clear all screens with active listeners
        onSuccess()
        
        // Then leave in the background (ViewModel stays alive briefly for this)
        viewModelScope.launch {
            try {
                householdRepository.leaveHousehold(householdId)
                Log.d("HouseholdSettingsVM", "Successfully left household")
            } catch (e: Exception) {
                // Log error but don't show UI since we've already navigated
                Log.e("HouseholdSettingsVM", "Error leaving household: ${e.message}")
            }
        }
    }
    
    /**
     * Deletes the household entirely. Only the owner can do this.
     * Navigates first, then performs the delete operation in the background.
     */
    fun deleteHousehold(onSuccess: () -> Unit) {
        _isDeletingOrLeaving.value = true
        _actionError.value = null
        isLeavingOrDeleted = true  // Set flag to prevent Flow errors
        
        // Cancel all listeners BEFORE deleting to prevent permission errors
        cancelAllListeners()
        
        // Navigate away FIRST to clear all screens with active listeners
        onSuccess()
        
        // Then delete in the background (ViewModel stays alive briefly for this)
        viewModelScope.launch {
            try {
                householdRepository.deleteHousehold(householdId)
                Log.d("HouseholdSettingsVM", "Successfully deleted household")
            } catch (e: Exception) {
                // Log error but don't show UI since we've already navigated
                Log.e("HouseholdSettingsVM", "Error deleting household: ${e.message}")
            }
        }
    }
    
    /** Clears the newly created invite code display. */
    fun clearNewInviteCode() {
        _newInviteCode.value = null
    }
    
    /** Clears any action error. */
    fun clearError() {
        _actionError.value = null
    }
    
    sealed interface HouseholdSettingsUiState {
        data object Loading : HouseholdSettingsUiState
        data class Success(val household: DisplayHousehold) : HouseholdSettingsUiState
        data class Error(val message: String) : HouseholdSettingsUiState
    }
    
    companion object {
        fun provideFactory(
            householdId: String,
            householdRepository: HouseholdRepository = HouseholdRepository()
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                    HouseholdSettingsViewModel(app, householdRepository, householdId)
                }
            }
        }
    }
}
