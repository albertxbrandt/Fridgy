package fyi.goodbye.fridgy.ui.householdList

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayHousehold
import fyi.goodbye.fridgy.repositories.AdminRepository
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import fyi.goodbye.fridgy.repositories.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel responsible for managing the main list of households.
 *
 * It coordinates with the [HouseholdRepository] to provide real-time updates for
 * the households the user belongs to.
 *
 * @param context Application context for accessing string resources.
 * @param auth Firebase Auth instance for user identification.
 * @param householdRepository Repository for household operations.
 * @param adminRepository Repository for admin privilege checks.
 * @param userRepository Repository for user operations.
 */
@HiltViewModel
class HouseholdListViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val auth: FirebaseAuth,
        private val householdRepository: HouseholdRepository,
        private val adminRepository: AdminRepository,
        private val userRepository: UserRepository
    ) : ViewModel() {
        private val _householdsUiState = MutableStateFlow<HouseholdUiState>(HouseholdUiState.Loading)

        /** The current state of the households list (Loading, Success, or Error). */
        val householdsUiState: StateFlow<HouseholdUiState> = _householdsUiState.asStateFlow()

        private val _isAdmin = MutableStateFlow(false)

        /** Indicates if the current user has admin privileges. */
        val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

        private val _isCreatingHousehold = MutableStateFlow(false)

        /** Indicates if a new household is currently being created. */
        val isCreatingHousehold: StateFlow<Boolean> = _isCreatingHousehold.asStateFlow()

        private val _createHouseholdError = MutableStateFlow<String?>(null)

        /** Any error message resulting from a failed household creation attempt. */
        val createHouseholdError: StateFlow<String?> = _createHouseholdError.asStateFlow()

        init {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId == null) {
                _householdsUiState.value =
                    HouseholdUiState.Error(
                        context.getString(R.string.error_user_not_logged_in)
                    )
            } else {
                // Check admin status
                viewModelScope.launch {
                    _isAdmin.value = adminRepository.isCurrentUserAdmin()
                }

                // Collect real-time stream of display households with live fridge counts
                viewModelScope.launch {
                    Timber.d("Starting to collect display households flow")
                    householdRepository.getDisplayHouseholdsForCurrentUser().collectLatest { displayHouseholds ->
                        Timber.d("Received ${displayHouseholds.size} display households from flow")
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
                _createHouseholdError.value = context.getString(R.string.error_please_fill_all_fields)
                return
            }

            _createHouseholdError.value = null
            _isCreatingHousehold.value = true
            viewModelScope.launch {
                try {
                    householdRepository.createHousehold(name)
                } catch (e: Exception) {
                    _createHouseholdError.value = e.message
                        ?: context.getString(R.string.error_failed_to_create_household)
                } finally {
                    _isCreatingHousehold.value = false
                }
            }
        }

        /** Clears the create household error. */
        fun clearError() {
            _createHouseholdError.value = null
        }

        /**
         * Signs out the current user.
         * Call the navigation callback after this to navigate to the login screen.
         */
        fun logout() {
            userRepository.signOut()
        }

        /** Sealed interface representing the UI states for the household list. */
        sealed interface HouseholdUiState {
            data object Loading : HouseholdUiState

            data class Success(val households: List<DisplayHousehold>) : HouseholdUiState

            data class Error(val message: String) : HouseholdUiState
        }
    }
