package fyi.goodbye.fridgy.ui.fridgeList

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.HouseholdRole
import fyi.goodbye.fridgy.repositories.AdminRepository
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import fyi.goodbye.fridgy.repositories.ItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsible for managing the list of fridges within a household.
 *
 * It coordinates with the [FridgeRepository] to provide real-time updates for
 * the fridges belonging to a specific household.
 *
 * @param context Application context for accessing string resources.
 * @param savedStateHandle Handle for accessing navigation arguments (householdId).
 * @param auth Firebase Auth instance for user identification.
 * @param fridgeRepository Repository for fridge operations.
 * @param adminRepository Repository for admin status checks.
 */
@HiltViewModel
class FridgeListViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        savedStateHandle: SavedStateHandle,
        private val auth: FirebaseAuth,
        private val fridgeRepository: FridgeRepository,
        private val itemRepository: ItemRepository,
        private val householdRepository: HouseholdRepository,
        private val adminRepository: AdminRepository
    ) : ViewModel() {
        /** The household ID this fridge list belongs to. */
        val householdId: String = savedStateHandle.get<String>("householdId") ?: ""

        private val _fridgesUiState = MutableStateFlow<FridgeUiState>(FridgeUiState.Loading)

        /** The current state of the fridges list (Loading, Success, or Error). */
        val fridgesUiState: StateFlow<FridgeUiState> = _fridgesUiState.asStateFlow()

        private val _isAdmin = MutableStateFlow(false)

        /** Indicates if the current user has admin privileges. */
        val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

        private val _userRole = MutableStateFlow<HouseholdRole?>(null)

        /** The current user's role in this household. */
        val userRole: StateFlow<HouseholdRole?> = _userRole.asStateFlow()

        private val _isCreatingFridge = MutableStateFlow(false)

        /** Indicates if a new fridge is currently being created. */
        val isCreatingFridge: StateFlow<Boolean> = _isCreatingFridge.asStateFlow()

        private val createdFridgeError = MutableStateFlow<String?>(null)

        /** Any error message resulting from a failed fridge creation attempt. */
        val createFridgeError: StateFlow<String?> = createdFridgeError.asStateFlow()

        init {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId == null) {
                _fridgesUiState.value = FridgeUiState.Error(context.getString(R.string.error_user_not_logged_in))
            } else if (householdId.isEmpty()) {
                _fridgesUiState.value = FridgeUiState.Error(context.getString(R.string.error_no_household_selected))
            } else {
                // Preload fridges from cache for instant display
                viewModelScope.launch {
                    fridgeRepository.preloadFridgesFromCache()
                }

                // Check admin status
                viewModelScope.launch {
                    _isAdmin.value = adminRepository.isCurrentUserAdmin()
                }

                // Listen for real-time role updates in this household
                viewModelScope.launch {
                    householdRepository.getHouseholdFlow(householdId).collectLatest { household ->
                        _userRole.value = household?.getRoleForUser(currentUserId)
                    }
                }

                // Collect real-time stream of fridges in this household
                viewModelScope.launch {
                    fridgeRepository.getFridgesForHousehold(householdId).collectLatest { fridges ->
                        // Fetch item counts for each fridge
                        val displayFridges =
                            fridges.map { fridge ->
                                val itemCount = itemRepository.getItemCount(fridge.id)
                                DisplayFridge(
                                    id = fridge.id,
                                    name = fridge.name,
                                    type = fridge.type,
                                    householdId = fridge.householdId,
                                    createdAt = fridge.createdAt,
                                    itemCount = itemCount
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
        fun createNewFridge(
            name: String,
            type: String = "fridge",
            location: String = ""
        ) {
            if (householdId.isEmpty()) {
                createdFridgeError.value = context.getString(R.string.error_no_household_selected)
                return
            }

            createdFridgeError.value = null
            _isCreatingFridge.value = true
            viewModelScope.launch {
                try {
                    fridgeRepository.createFridge(name, householdId, type, location)
                } catch (e: Exception) {
                    createdFridgeError.value = e.message ?: context.getString(R.string.error_failed_to_create_fridge)
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
    }
