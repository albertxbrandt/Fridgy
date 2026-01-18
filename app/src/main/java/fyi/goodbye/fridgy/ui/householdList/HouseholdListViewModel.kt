package fyi.goodbye.fridgy.ui.householdList

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayHousehold
import fyi.goodbye.fridgy.repositories.AdminRepository
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing the main list of households.
 *
 * It coordinates with the [HouseholdRepository] to provide real-time updates for
 * the households the user belongs to.
 */
class HouseholdListViewModel(
    application: Application,
    private val householdRepository: HouseholdRepository = HouseholdRepository(),
    private val adminRepository: AdminRepository = AdminRepository()
) : AndroidViewModel(application) {
    private val _householdsUiState = MutableStateFlow<HouseholdUiState>(HouseholdUiState.Loading)

    /** The current state of the households list (Loading, Success, or Error). */
    val householdsUiState: StateFlow<HouseholdUiState> = _householdsUiState.asStateFlow()

    private val auth = FirebaseAuth.getInstance()

    private val _isAdmin = MutableStateFlow(false)

    /** Indicates if the current user has admin privileges. */
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _isCreatingHousehold = MutableStateFlow(false)

    /** Indicates if a new household is currently being created. */
    val isCreatingHousehold: StateFlow<Boolean> = _isCreatingHousehold.asStateFlow()

    private val _createHouseholdError = MutableStateFlow<String?>(null)

    /** Any error message resulting from a failed household creation attempt. */
    val createHouseholdError: StateFlow<String?> = _createHouseholdError.asStateFlow()

    private val _needsMigration = MutableStateFlow(false)

    /** Indicates if the user has orphan fridges that need migration. */
    val needsMigration: StateFlow<Boolean> = _needsMigration.asStateFlow()

    private val _isMigrating = MutableStateFlow(false)

    /** Indicates if migration is in progress. */
    val isMigrating: StateFlow<Boolean> = _isMigrating.asStateFlow()

    init {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            _householdsUiState.value =
                HouseholdUiState.Error(
                    getApplication<Application>().getString(R.string.error_user_not_logged_in)
                )
        } else {
            // Check admin status
            viewModelScope.launch {
                _isAdmin.value = adminRepository.isCurrentUserAdmin()
            }

            // Check for orphan fridges that need migration
            viewModelScope.launch {
                try {
                    _needsMigration.value = householdRepository.hasOrphanFridges()
                } catch (e: Exception) {
                    Log.e("HouseholdListVM", "Error checking for orphan fridges: ${e.message}")
                }
            }

            // Collect real-time stream of households the user is a member of
            viewModelScope.launch {
                Log.d("HouseholdListVM", "Starting to collect households flow")
                householdRepository.getHouseholdsForCurrentUser().collectLatest { households ->
                    Log.d("HouseholdListVM", "Received ${households.size} households from flow")
                    val displayHouseholds =
                        households.mapNotNull { household ->
                            try {
                                householdRepository.getDisplayHouseholdById(household.id)
                            } catch (e: Exception) {
                                Log.e("HouseholdListVM", "Error fetching display household: ${e.message}")
                                null
                            }
                        }
                    Log.d(
                        "HouseholdListVM",
                        "Setting UI state to Success with ${displayHouseholds.size} display households"
                    )
                    _householdsUiState.value = HouseholdUiState.Success(displayHouseholds)
                }
            }
        }
    }

    /**
     * Creates a new household with the given name.
     *
     * @param name The name of the new household to create.
     */
    fun createNewHousehold(name: String) {
        if (name.isBlank()) {
            _createHouseholdError.value = getApplication<Application>().getString(R.string.error_please_fill_all_fields)
            return
        }

        _createHouseholdError.value = null
        _isCreatingHousehold.value = true
        viewModelScope.launch {
            try {
                householdRepository.createHousehold(name)
            } catch (e: Exception) {
                _createHouseholdError.value = e.message
                    ?: getApplication<Application>().getString(R.string.error_failed_to_create_household)
            } finally {
                _isCreatingHousehold.value = false
            }
        }
    }

    /**
     * Migrates orphan fridges (fridges without a householdId) to a new household.
     */
    fun migrateOrphanFridges() {
        _isMigrating.value = true
        viewModelScope.launch {
            try {
                householdRepository.migrateOrphanFridges()
                _needsMigration.value = false
            } catch (e: Exception) {
                Log.e("HouseholdListVM", "Error migrating orphan fridges: ${e.message}")
                _createHouseholdError.value = "Failed to migrate fridges: ${e.message}"
            } finally {
                _isMigrating.value = false
            }
        }
    }

    /** Clears the create household error. */
    fun clearError() {
        _createHouseholdError.value = null
    }

    /** Sealed interface representing the UI states for the household list. */
    sealed interface HouseholdUiState {
        data object Loading : HouseholdUiState

        data class Success(val households: List<DisplayHousehold>) : HouseholdUiState

        data class Error(val message: String) : HouseholdUiState
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY]!!
                    HouseholdListViewModel(app)
                }
            }
    }
}
