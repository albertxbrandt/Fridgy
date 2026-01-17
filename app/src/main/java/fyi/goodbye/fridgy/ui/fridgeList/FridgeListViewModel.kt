package fyi.goodbye.fridgy.ui.fridgeList

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.repositories.AdminRepository
import fyi.goodbye.fridgy.repositories.FridgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing the list of fridges within a household.
 *
 * It coordinates with the [FridgeRepository] to provide real-time updates for
 * the fridges belonging to a specific household.
 *
 * @param application The application context.
 * @param savedStateHandle Handle for accessing navigation arguments.
 * @param fridgeRepository Repository for fridge operations.
 * @param adminRepository Repository for admin status checks.
 */
class FridgeListViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val fridgeRepository: FridgeRepository = FridgeRepository(),
    private val adminRepository: AdminRepository = AdminRepository()
) : AndroidViewModel(application) {
    
    /** The household ID this fridge list belongs to. */
    val householdId: String = savedStateHandle.get<String>("householdId") ?: ""
    
    private val _fridgesUiState = MutableStateFlow<FridgeUiState>(FridgeUiState.Loading)

    /** The current state of the fridges list (Loading, Success, or Error). */
    val fridgesUiState: StateFlow<FridgeUiState> = _fridgesUiState.asStateFlow()

    private val auth = FirebaseAuth.getInstance()

    private val _isAdmin = MutableStateFlow(false)

    /** Indicates if the current user has admin privileges. */
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _isCreatingFridge = MutableStateFlow(false)

    /** Indicates if a new fridge is currently being created. */
    val isCreatingFridge: StateFlow<Boolean> = _isCreatingFridge.asStateFlow()

    private val createdFridgeError = MutableStateFlow<String?>(null)

    /** Any error message resulting from a failed fridge creation attempt. */
    val createFridgeError: StateFlow<String?> = createdFridgeError.asStateFlow()

    init {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            _fridgesUiState.value = FridgeUiState.Error(getApplication<Application>().getString(R.string.error_user_not_logged_in))
        } else if (householdId.isEmpty()) {
            _fridgesUiState.value = FridgeUiState.Error(getApplication<Application>().getString(R.string.error_no_household_selected))
        } else {
            // Preload fridges from cache for instant display
            viewModelScope.launch {
                fridgeRepository.preloadFridgesFromCache()
            }

            // Check admin status
            viewModelScope.launch {
                _isAdmin.value = adminRepository.isCurrentUserAdmin()
            }

            // Collect real-time stream of fridges in this household
            viewModelScope.launch {
                fridgeRepository.getFridgesForHousehold(householdId).collectLatest { fridges ->
                    // Fetch creator user data
                    val creatorIds = fridges.map { it.createdBy }.distinct()
                    val usersMap = fridgeRepository.getUsersByIds(creatorIds)

                    val displayFridges = fridges.map { fridge ->
                        val creatorName = usersMap[fridge.createdBy]?.username 
                            ?: getApplication<Application>().getString(R.string.unknown)

                        DisplayFridge(
                            id = fridge.id,
                            name = fridge.name,
                            type = fridge.type,
                            householdId = fridge.householdId,
                            createdByUid = fridge.createdBy,
                            creatorDisplayName = creatorName,
                            createdAt = fridge.createdAt
                        )
                    }
                    _fridgesUiState.value = FridgeUiState.Success(displayFridges)
                }
            }
        }
    }

    /**
     * Creates a new fridge with the given name within the current household.
     *
     * @param name The name of the new fridge to create.
     * @param type The type of storage (fridge, freezer, pantry).
     * @param location Optional physical location description.
     */
    fun createNewFridge(name: String, type: String = "fridge", location: String = "") {
        if (householdId.isEmpty()) {
            createdFridgeError.value = getApplication<Application>().getString(R.string.error_no_household_selected)
            return
        }
        
        createdFridgeError.value = null
        _isCreatingFridge.value = true
        viewModelScope.launch {
            try {
                fridgeRepository.createFridge(name, householdId, type, location)
            } catch (e: Exception) {
                createdFridgeError.value = e.message ?: getApplication<Application>().getString(R.string.error_failed_to_create_fridge)
            } finally {
                _isCreatingFridge.value = false
            }
        }
    }

    /** Sealed interface representing the UI states for the fridge list. */
    sealed interface FridgeUiState {
        data object Loading : FridgeUiState

        data class Success(val fridges: List<DisplayFridge>) : FridgeUiState

        data class Error(val message: String) : FridgeUiState
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY]!!
                    val savedStateHandle = createSavedStateHandle()
                    FridgeListViewModel(app, savedStateHandle)
                }
            }
    }
}
