package fyi.goodbye.fridgy.ui.householdSettings

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.display.DisplayHousehold
import fyi.goodbye.fridgy.models.entities.HouseholdRole
import fyi.goodbye.fridgy.models.entities.InviteCode
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import fyi.goodbye.fridgy.repositories.MembershipRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * ViewModel responsible for managing household settings.
 *
 * Handles member management, invite code generation, and household deletion/leave.
 */
@HiltViewModel
class HouseholdSettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        savedStateHandle: SavedStateHandle,
        private val householdRepository: HouseholdRepository,
        private val membershipRepository: MembershipRepository,
        private val firebaseAuth: FirebaseAuth
    ) : ViewModel() {
        private val householdId: String = savedStateHandle.get<String>("householdId") ?: ""
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

        // Job reference for cancelling the household listener
        private var householdJob: Job? = null

        // Job reference for cancelling the invite codes listener
        private var inviteCodesJob: Job? = null

        private val _actionError = MutableStateFlow<String?>(null)
        val actionError: StateFlow<String?> = _actionError.asStateFlow()

        val currentUserId = firebaseAuth.currentUser?.uid

        init {
            loadHouseholdDetails()
            loadInviteCodes()
        }

        private fun loadHouseholdDetails() {
            householdJob =
                viewModelScope.launch {
                    _uiState.value = HouseholdSettingsUiState.Loading
                    try {
                        // Use snapshot listener for real-time updates
                        householdRepository.getHouseholdFlow(householdId).collectLatest { household ->
                            if (!isLeavingOrDeleted && household != null) {
                                // Convert to DisplayHousehold
                                val displayHousehold = householdRepository.getDisplayHouseholdById(householdId)
                                if (displayHousehold != null) {
                                    _uiState.value = HouseholdSettingsUiState.Success(displayHousehold)
                                } else {
                                    _uiState.value =
                                        HouseholdSettingsUiState.Error(
                                            context.getString(R.string.error_fridge_not_found)
                                        )
                                }
                            } else if (!isLeavingOrDeleted) {
                                _uiState.value =
                                    HouseholdSettingsUiState.Error(
                                        context.getString(R.string.error_fridge_not_found)
                                    )
                            }
                        }
                    } catch (e: Exception) {
                        if (!isLeavingOrDeleted) {
                            _uiState.value =
                                HouseholdSettingsUiState.Error(
                                    e.message ?: context.getString(R.string.error_failed_to_load_fridge)
                                )
                            Timber.e(e, "Error loading household: ${e.message}")
                        }
                    }
                }
        }

        private fun loadInviteCodes() {
            inviteCodesJob =
                viewModelScope.launch {
                    try {
                        membershipRepository.getInviteCodesFlow(householdId).collectLatest { codes ->
                            if (!isLeavingOrDeleted) {
                                _inviteCodes.value = codes.sortedByDescending { it.createdAt }
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore errors if we're leaving/deleted (permissions revoked)
                        if (!isLeavingOrDeleted) {
                            Timber.e("Error loading invite codes: ${e.message}")
                        }
                    }
                }
        }

        /**
         * Cancels all active Firestore listeners to prevent permission errors.
         */
        private fun cancelAllListeners() {
            householdJob?.cancel()
            householdJob = null
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
                    val expiresAt =
                        if (expiresInDays != null) {
                            Instant.now().plus(expiresInDays.toLong(), ChronoUnit.DAYS).toEpochMilli()
                        } else {
                            null
                        }

                    val code = membershipRepository.createInviteCode(householdId, expiresAt)
                    _newInviteCode.value = code
                } catch (e: Exception) {
                    Timber.e("Error creating invite code: ${e.message}")
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
                    membershipRepository.revokeInviteCode(householdId, code)
                } catch (e: Exception) {
                    Timber.e("Error revoking invite code: ${e.message}")
                    _actionError.value = "Failed to revoke invite code: ${e.message}"
                }
            }
        }

        /**
         * Updates a member's role in the household.
         * Uses optimistic update to avoid full page reload.
         */
        fun updateMemberRole(
            userId: String,
            newRole: HouseholdRole
        ) {
            viewModelScope.launch {
                try {
                    // Update in background without reload - Firestore listener will update UI
                    membershipRepository.updateMemberRole(householdId, userId, newRole)
                } catch (e: Exception) {
                    Timber.e("Error updating member role: ${e.message}")
                    _actionError.value = "Failed to update member role: ${e.message}"
                    // Reload on error to revert optimistic update
                    loadHouseholdDetails()
                }
            }
        }

        /**
         */
        fun removeMember(userId: String) {
            viewModelScope.launch {
                try {
                    membershipRepository.removeMember(householdId, userId)
                    loadHouseholdDetails() // Refresh
                } catch (e: Exception) {
                    Timber.e("Error removing member: ${e.message}")
                    _actionError.value = "Failed to remove member: ${e.message}"
                }
            }
        }

        /**
         * Leaves the household. Current user is removed from members list.
         * Completes the operation first, then navigates to prevent cancellation.
         */
        fun leaveHousehold(onSuccess: () -> Unit) {
            _isDeletingOrLeaving.value = true
            _actionError.value = null
            isLeavingOrDeleted = true // Set flag to prevent Flow errors

            // Cancel all listeners BEFORE leaving to prevent permission errors
            cancelAllListeners()

            // Perform leave operation BEFORE navigating to ensure it completes
            viewModelScope.launch {
                try {
                    membershipRepository.leaveHousehold(householdId)
                    Timber.d("Successfully left household")

                    // Navigate away AFTER successful leave operation
                    onSuccess()
                } catch (e: Exception) {
                    // Show error to user since we haven't navigated yet
                    _actionError.value = e.message ?: "Failed to leave household"
                    _isDeletingOrLeaving.value = false
                    Timber.e("Error leaving household: ${e.message}")
                }
            }
        }

        /**
         * Deletes the household entirely. Only the owner can do this.
         * Completes the operation first, then navigates to prevent cancellation.
         */
        fun deleteHousehold(onSuccess: () -> Unit) {
            _isDeletingOrLeaving.value = true
            _actionError.value = null
            isLeavingOrDeleted = true // Set flag to prevent Flow errors

            // Cancel all listeners BEFORE deleting to prevent permission errors
            cancelAllListeners()

            // Perform delete operation BEFORE navigating to ensure it completes
            viewModelScope.launch {
                try {
                    householdRepository.deleteHousehold(householdId)
                    Timber.d("Successfully deleted household")

                    // Navigate away AFTER successful deletion
                    onSuccess()
                } catch (e: Exception) {
                    // Show error to user since we haven't navigated yet
                    _actionError.value = e.message ?: "Failed to delete household"
                    _isDeletingOrLeaving.value = false
                    Timber.e("Error deleting household: ${e.message}")
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
    }
