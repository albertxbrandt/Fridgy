package fyi.goodbye.fridgy.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.repositories.FridgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing the settings and administrative actions of a specific fridge.
 * 
 * This includes inviting new members, leaving a fridge, or deleting a fridge (for owners).
 * It observes and exposes the detailed state of the fridge using [FridgeSettingsUiState].
 *
 * @property fridgeRepository The repository for database operations.
 * @property fridgeId The unique ID of the fridge being managed.
 */
class FridgeSettingsViewModel(
    private val fridgeRepository: FridgeRepository = FridgeRepository(),
    private val fridgeId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<FridgeSettingsUiState>(FridgeSettingsUiState.Loading)
    /** The current UI state of the fridge settings (Loading, Success, or Error). */
    val uiState: StateFlow<FridgeSettingsUiState> = _uiState.asStateFlow()

    private val _isInviting = MutableStateFlow(false)
    /** Indicates if an invitation is currently being sent. */
    val isInviting: StateFlow<Boolean> = _isInviting.asStateFlow()

    private val _inviteError = MutableStateFlow<String?>(null)
    /** Any error message resulting from a failed invitation attempt. */
    val inviteError: StateFlow<String?> = _inviteError.asStateFlow()

    private val _inviteSuccess = MutableStateFlow(false)
    /** Indicates if an invitation was successfully sent. */
    val inviteSuccess: StateFlow<Boolean> = _inviteSuccess.asStateFlow()

    private val _isDeletingOrLeaving = MutableStateFlow(false)
    /** Indicates if a delete or leave operation is currently in progress. */
    val isDeletingOrLeaving: StateFlow<Boolean> = _isDeletingOrLeaving.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    /** Any error message resulting from a failed delete or leave action. */
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    /** The User ID of the currently authenticated user. */
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    init {
        loadFridgeDetails()
    }

    private fun loadFridgeDetails() {
        viewModelScope.launch {
            _uiState.value = FridgeSettingsUiState.Loading
            try {
                val fridge = fridgeRepository.getFridgeById(fridgeId)
                if (fridge != null) {
                    _uiState.value = FridgeSettingsUiState.Success(fridge)
                } else {
                    _uiState.value = FridgeSettingsUiState.Error("Fridge not found.")
                }
            } catch (e: Exception) {
                _uiState.value = FridgeSettingsUiState.Error(e.message ?: "Failed to load fridge details.")
                Log.e("FridgeSettingsVM", "Error fetching fridge details for $fridgeId: ${e.message}", e)
            }
        }
    }

    /**
     * Invites a new member to the fridge by their email address.
     * 
     * @param email The email address of the user to invite.
     */
    fun inviteMember(email: String) {
        if (email.isBlank()) return
        
        _isInviting.value = true
        _inviteError.value = null
        _inviteSuccess.value = false
        
        viewModelScope.launch {
            try {
                fridgeRepository.inviteUserByEmail(fridgeId, email)
                _inviteSuccess.value = true
                _isInviting.value = false
                loadFridgeDetails() // Refresh list to show pending
            } catch (e: Exception) {
                _inviteError.value = e.message ?: "Failed to send invitation."
                _isInviting.value = false
            }
        }
    }

    /** Clears the current invitation success or error status. */
    fun clearInviteStatus() {
        _inviteError.value = null
        _inviteSuccess.value = false
    }

    /**
     * Removes a member from the fridge. Only for owners.
     * 
     * @param userId The ID of the member to remove.
     */
    fun removeMember(userId: String) {
        viewModelScope.launch {
            try {
                fridgeRepository.removeMember(fridgeId, userId)
                loadFridgeDetails() // Refresh
            } catch (e: Exception) {
                Log.e("FridgeSettingsVM", "Error removing member: ${e.message}")
            }
        }
    }

    /**
     * Revokes a pending invitation. Only for owners.
     * 
     * @param userId The ID of the user whose invite should be revoked.
     */
    fun revokeInvite(userId: String) {
        viewModelScope.launch {
            try {
                fridgeRepository.revokeInvite(fridgeId, userId)
                loadFridgeDetails() // Refresh
            } catch (e: Exception) {
                Log.e("FridgeSettingsVM", "Error revoking invite: ${e.message}")
            }
        }
    }

    /**
     * Removes the current user from the fridge's membership.
     * 
     * @param onSuccess Callback triggered after successful removal.
     */
    fun leaveFridge(onSuccess: () -> Unit) {
        _isDeletingOrLeaving.value = true
        _actionError.value = null
        viewModelScope.launch {
            try {
                fridgeRepository.leaveFridge(fridgeId)
                onSuccess()
            } catch (e: Exception) {
                _actionError.value = e.message
                Log.e("FridgeSettingsVM", "Error leaving fridge: ${e.message}")
            } finally {
                _isDeletingOrLeaving.value = false
            }
        }
    }

    /**
     * Permanently deletes the fridge and all its data.
     * 
     * @param onSuccess Callback triggered after successful deletion.
     */
    fun deleteFridge(onSuccess: () -> Unit) {
        _isDeletingOrLeaving.value = true
        _actionError.value = null
        viewModelScope.launch {
            try {
                fridgeRepository.deleteFridge(fridgeId)
                onSuccess()
            } catch (e: Exception) {
                _actionError.value = e.message
                Log.e("FridgeSettingsVM", "Error deleting fridge: ${e.message}")
            } finally {
                _isDeletingOrLeaving.value = false
            }
        }
    }

    /** Sealed interface representing the possible UI states for fridge settings. */
    sealed interface FridgeSettingsUiState {
        data object Loading : FridgeSettingsUiState
        data class Success(val fridge: DisplayFridge) : FridgeSettingsUiState
        data class Error(val message: String) : FridgeSettingsUiState
    }

    companion object {
        /** Factory method to create an instance of [FridgeSettingsViewModel] with a [fridgeId]. */
        fun provideFactory(fridgeId: String, fridgeRepository: FridgeRepository = FridgeRepository()): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    FridgeSettingsViewModel(fridgeRepository, fridgeId)
                }
            }
        }
    }
}
