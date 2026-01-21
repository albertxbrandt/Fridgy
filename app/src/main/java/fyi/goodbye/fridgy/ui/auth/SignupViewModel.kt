package fyi.goodbye.fridgy.ui.auth

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.repositories.UserRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing the user signup process.
 *
 * This class handles input validation, Firebase Authentication account creation,
 * and the subsequent creation of a user profile document in Firestore. It exposes
 * various UI states to the [SignupScreen] including loading status and error messages.
 * 
 * Uses [UserRepository] for all Firebase operations following MVVM architecture.
 */
class SignupViewModel(
    application: Application,
    private val userRepository: UserRepository = UserRepository()
) : AndroidViewModel(application) {

    /** The email address entered by the user. */
    var email by mutableStateOf("")
        private set

    /** The username entered by the user. */
    var username by mutableStateOf("")
        private set

    /** The password entered by the user. */
    var password by mutableStateOf("")
        private set

    /** The confirmation password entered by the user. */
    var confirmPassword by mutableStateOf("")
        private set

    /** Any error message resulting from validation or network operations. */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Indicates whether a signup operation is currently in progress. */
    var isLoading by mutableStateOf(false)
        private set

    private val _signupSuccess = MutableSharedFlow<Boolean>()

    /** A stream of success events used to trigger navigation after a successful signup. */
    val signupSuccess = _signupSuccess.asSharedFlow()

    /** Updates the email state and clears any existing error message. */
    fun onEmailChange(newEmail: String) {
        email = newEmail
        errorMessage = null
    }

    /** Updates the username state and clears any existing error message. */
    fun onUsernameChange(newUsername: String) {
        username = newUsername
        errorMessage = null
    }

    /** Updates the password state and clears any existing error message. */
    fun onPasswordChange(newPassword: String) {
        password = newPassword
        errorMessage = null
    }

    /** Updates the confirm password state and clears any existing error message. */
    fun onConfirmPasswordChange(newConfirmPassword: String) {
        confirmPassword = newConfirmPassword
        errorMessage = null
    }

    /**
     * Attempts to sign up a new user with the current email and password states.
     *
     * Performs basic validation, then creates a Firebase Auth user. If successful,
     * it creates corresponding documents in both 'users' and 'userProfiles' collections.
     */
    fun signup() {
        errorMessage = null
        if (email.isBlank() || username.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            errorMessage = getApplication<Application>().getString(R.string.error_please_fill_all_fields)
            return
        }

        // Validate username format: letters, numbers, underscore, period only
        val trimmedUsername = username.trim()
        if (!trimmedUsername.matches(Regex("^[a-zA-Z0-9_.]+$"))) {
            errorMessage = getApplication<Application>().getString(R.string.error_username_invalid_chars)
            return
        }

        // Validate username length
        if (trimmedUsername.length < 3) {
            errorMessage = getApplication<Application>().getString(R.string.error_username_too_short)
            return
        }

        if (trimmedUsername.length > 16) {
            errorMessage = getApplication<Application>().getString(R.string.error_username_too_long)
            return
        }

        if (password != confirmPassword) {
            errorMessage = getApplication<Application>().getString(R.string.error_passwords_do_not_match)
            return
        }

        isLoading = true

        viewModelScope.launch {
            try {
                // Check if username already exists
                if (userRepository.isUsernameTaken(trimmedUsername)) {
                    errorMessage = getApplication<Application>().getString(R.string.error_username_taken, trimmedUsername)
                    isLoading = false
                    return@launch
                }

                // Sign up the user (creates auth account and Firestore documents)
                userRepository.signUp(email, password, trimmedUsername)
                _signupSuccess.emit(true)
            } catch (e: Exception) {
                Log.w("SignupViewModel", "createUserWithEmail:failure", e)
                errorMessage = e.message ?: getApplication<Application>().getString(R.string.error_signup_failed)
            } finally {
                isLoading = false
            }
        }
    }
}
