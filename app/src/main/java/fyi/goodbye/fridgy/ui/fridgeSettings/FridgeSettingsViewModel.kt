package fyi.goodbye.fridgy.ui.fridgeSettings

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsible for managing the settings of a specific fridge.
 *
 * This includes viewing fridge details and deleting the fridge.
 * Member management is handled at the household level.
 *
 * @property fridgeRepository The repository for database operations.
 * @property fridgeId The unique ID of the fridge being managed.
 */
@HiltViewModel
class FridgeSettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        savedStateHandle: SavedStateHandle,
        private val fridgeRepository: FridgeRepository,
        private val householdRepository: HouseholdRepository,
        private val firebaseAuth: FirebaseAuth
    ) : ViewModel() {
        private val fridgeId: String = savedStateHandle.get<String>("fridgeId") ?: ""
        private val _uiState = MutableStateFlow<FridgeSettingsUiState>(FridgeSettingsUiState.Loading)

        /** The current UI state of the fridge settings (Loading, Success, or Error). */
        val uiState: StateFlow<FridgeSettingsUiState> = _uiState.asStateFlow()

        private val _isDeleting = MutableStateFlow(false)

        /** Indicates if a delete operation is currently in progress. */
        val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

        private val _actionError = MutableStateFlow<String?>(null)

        /** Any error message resulting from a failed delete action. */
        val actionError: StateFlow<String?> = _actionError.asStateFlow()

        /** The User ID of the currently authenticated user. */
        val currentUserId = firebaseAuth.currentUser?.uid

    private val _isHouseholdOwner = MutableStateFlow(false)
    
    /** Indicates if the current user is the owner of the household this fridge belongs to. */
    val isHouseholdOwner: StateFlow<Boolean> = _isHouseholdOwner.asStateFlow()

    init {
        loadFridgeDetails()
    }

    private fun loadFridgeDetails() {
            viewModelScope.launch {
                _uiState.value = FridgeSettingsUiState.Loading
                try {
                    val displayFridge = fridgeRepository.getFridgeById(fridgeId)
                    val rawFridge = fridgeRepository.getRawFridgeById(fridgeId)

                    if (displayFridge != null && rawFridge != null) {
                        _uiState.value = FridgeSettingsUiState.Success(displayFridge, rawFridge)
                    
                        // Check if current user is household owner
                        val householdId = displayFridge.householdId
                        if (householdId.isNotEmpty() && currentUserId != null) {
                            try {
                                val household = householdRepository.getHouseholdById(householdId)
                                _isHouseholdOwner.value = household?.createdBy == currentUserId
                            } catch (e: Exception) {
                                Log.e("FridgeSettingsVM", "Error checking household ownership: ${e.message}")
                                _isHouseholdOwner.value = false
                            }
                        }
                    } else {
                        _uiState.value = FridgeSettingsUiState.Error("Fridge not found")
                    }
                } catch (e: Exception) {
                    _uiState.value = FridgeSettingsUiState.Error(e.message ?: "Unknown error")
                    Log.e("FridgeSettingsVM", "Error loading fridge details: ${e.message}")
                }
            }
        }

        /**
         * Permanently deletes the fridge and all its data.
         *
         * @param onSuccess Callback triggered after successful deletion.
         */
        fun deleteFridge(onSuccess: () -> Unit) {
            _isDeleting.value = true
            _actionError.value = null
            viewModelScope.launch {
                try {
                    // Log debugging info
                    val fridge = fridgeRepository.getRawFridgeById(fridgeId)
                    Log.d("FridgeSettingsVM", "Attempting to delete fridge: $fridgeId")
                    Log.d("FridgeSettingsVM", "Fridge householdId: ${fridge?.householdId}")
                    Log.d("FridgeSettingsVM", "Current user ID: $currentUserId")
                    Log.d("FridgeSettingsVM", "Is household owner: ${_isHouseholdOwner.value}")
                    
                    fridgeRepository.deleteFridge(fridgeId)
                    onSuccess()
                } catch (e: Exception) {
                    _actionError.value = e.message
                    Log.e("FridgeSettingsVM", "Error deleting fridge: ${e.message}")
                } finally {
                    _isDeleting.value = false
                }
            }
        }

        /** Sealed interface representing the possible UI states for fridge settings. */
        sealed interface FridgeSettingsUiState {
            data object Loading : FridgeSettingsUiState

            data class Success(val fridge: DisplayFridge, val fridgeData: Fridge) : FridgeSettingsUiState

            data class Error(val message: String) : FridgeSettingsUiState
        }
    }