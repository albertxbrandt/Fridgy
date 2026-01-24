package fyi.goodbye.fridgy.ui.userProfile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import fyi.goodbye.fridgy.repositories.UserRepository
import fyi.goodbye.fridgy.ui.shared.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing user profile operations.
 *
 * Handles:
 * - Loading current user profile
 * - Updating username
 * - Account deletion
 * - Username validation
 *
 * ## State Management
 * Uses shared [UiState] with Loading, Success, and Error states.
 * Success state contains [UserProfileData] with current username and email.
 * Update and deletion operations use [UiState] with Unit type for operation tracking.
 *
 * ## Usage
 * ```kotlin
 * @Composable
 * fun UserProfileScreen(viewModel: UserProfileViewModel = hiltViewModel()) {
 *     val uiState by viewModel.uiState.collectAsState()
 *     // ...
 * }
 * ```
 *
 * @param userRepository Repository for user data operations.
 * @param auth Firebase Auth instance for current user info.
 */
@HiltViewModel
class UserProfileViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
        private val auth: FirebaseAuth
    ) : ViewModel() {
        /**
         * Data class representing user profile information displayed in the UI.
         *
         * @property username The user's current username.
         * @property email The user's email address.
         */
        data class UserProfileData(
            val username: String,
            val email: String
        )

        private val _uiState = MutableStateFlow<UiState<UserProfileData>>(UiState.Loading)
        val uiState: StateFlow<UiState<UserProfileData>> = _uiState.asStateFlow()

        private val _usernameUpdateState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
        val usernameUpdateState: StateFlow<UiState<Unit>> = _usernameUpdateState.asStateFlow()

        private val _accountDeletionState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
        val accountDeletionState: StateFlow<UiState<Unit>> = _accountDeletionState.asStateFlow()

        init {
            loadUserProfile()
        }

        /**
         * Loads the current user's profile data.
         */
        private fun loadUserProfile() {
            viewModelScope.launch {
                try {
                    val userId =
                        auth.currentUser?.uid
                            ?: throw IllegalStateException("No authenticated user")
                    val email =
                        auth.currentUser?.email
                            ?: throw IllegalStateException("No email found")

                    val profile = userRepository.getUserProfile(userId)
                    val username = profile?.username ?: "Unknown"

                    _uiState.value = UiState.Success(UserProfileData(username, email))
                } catch (e: Exception) {
                    _uiState.value = UiState.Error(e.message ?: "Failed to load profile")
                }
            }
        }

        /**
         * Updates the user's username after validation.
         *
         * Checks if the new username is different from current and not already taken.
         *
         * @param newUsername The new username to set.
         */
        fun updateUsername(newUsername: String) {
            viewModelScope.launch {
                try {
                    _usernameUpdateState.value = UiState.Loading

                    // Validate username format
                    val trimmedUsername = newUsername.trim()
                    if (trimmedUsername.length < 3) {
                        _usernameUpdateState.value = UiState.Error("Username must be at least 3 characters")
                        return@launch
                    }
                    if (trimmedUsername.length > 20) {
                        _usernameUpdateState.value = UiState.Error("Username must be 20 characters or less")
                        return@launch
                    }
                    if (!trimmedUsername.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                        _usernameUpdateState.value =
                            UiState.Error("Username can only contain letters, numbers, hyphens, and underscores")
                        return@launch
                    }

                    // Check if username is different from current
                    val currentState = _uiState.value
                    if (currentState is UiState.Success && currentState.data.username == trimmedUsername) {
                        _usernameUpdateState.value = UiState.Error("This is already your username")
                        return@launch
                    }

                    // Check if username is taken
                    if (userRepository.isUsernameTaken(trimmedUsername)) {
                        _usernameUpdateState.value = UiState.Error("Username is already taken")
                        return@launch
                    }

                    // Update username
                    userRepository.updateUsername(trimmedUsername)

                    // Refresh profile
                    loadUserProfile()

                    _usernameUpdateState.value = UiState.Success(Unit)
                } catch (e: Exception) {
                    _usernameUpdateState.value = UiState.Error(e.message ?: "Failed to update username")
                }
            }
        }

        /**
         * Deletes the user's account and all associated data.
         *
         * This action is irreversible and will:
         * - Remove user from all fridges
         * - Delete all user data
         * - Delete the authentication account
         */
        fun deleteAccount() {
            viewModelScope.launch {
                try {
                    _accountDeletionState.value = UiState.Loading
                    userRepository.deleteAccount()
                    _accountDeletionState.value = UiState.Success(Unit)
                } catch (e: Exception) {
                    _accountDeletionState.value = UiState.Error(e.message ?: "Failed to delete account")
                }
            }
        }

        /**
         * Resets the username update state back to idle.
         */
        fun resetUsernameUpdateState() {
            _usernameUpdateState.value = UiState.Idle
        }

        /**
         * Resets the account deletion state back to idle.
         */
        fun resetAccountDeletionState() {
            _accountDeletionState.value = UiState.Idle
        }
    }
