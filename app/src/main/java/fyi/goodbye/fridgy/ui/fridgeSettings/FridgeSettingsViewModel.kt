package fyi.goodbye.fridgy.ui.fridgeSettings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.repositories.FridgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing the settings of a specific fridge.
 *
 * This includes viewing fridge details and deleting the fridge.
 * Member management is handled at the household level.
 *
 * @property fridgeRepository The repository for database operations.
 * @property fridgeId The unique ID of the fridge being managed.
 */
class FridgeSettingsViewModel(
    application: Application,
    private val fridgeRepository: FridgeRepository = FridgeRepository(),
    private val fridgeId: String
) : AndroidViewModel(application) {
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
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

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
                } else {
                    _uiState.value = FridgeSettingsUiState.Error(getApplication<Application>().getString(R.string.error_fridge_not_found))
                }
            } catch (e: Exception) {
                _uiState.value = FridgeSettingsUiState.Error(e.message ?: getApplication<Application>().getString(R.string.error_failed_to_load_fridge))
                Log.e("FridgeSettingsVM", "Error fetching fridge details for $fridgeId: ${e.message}", e)
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

    companion object {
        /** Factory method to create an instance of [FridgeSettingsViewModel] with a [fridgeId]. */
        fun provideFactory(
            fridgeId: String,
            fridgeRepository: FridgeRepository = FridgeRepository()
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                    FridgeSettingsViewModel(app, fridgeRepository, fridgeId)
                }
            }
        }
    }
}
