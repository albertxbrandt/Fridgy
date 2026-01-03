package fyi.goodbye.fridgy.ui.auth

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel responsible for managing the user signup process.
 *
 * This class handles input validation, Firebase Authentication account creation,
 * and the subsequent creation of a user profile document in Firestore. It exposes
 * various UI states to the [SignupScreen] including loading status and error messages.
 */
class SignupViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

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

    private val signupSucess = MutableSharedFlow<Boolean>()

    /** A stream of success events used to trigger navigation after a successful signup. */
    val signupSuccess = signupSucess.asSharedFlow()

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
                val existingProfiles =
                    firestore.collection("userProfiles")
                        .whereEqualTo("username", trimmedUsername)
                        .get()
                        .await()

                if (!existingProfiles.isEmpty) {
                    errorMessage = getApplication<Application>().getString(R.string.error_username_taken, trimmedUsername)
                    isLoading = false
                    return@launch
                }

                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val uid = authResult.user?.uid

                if (uid != null) {
                    val timestamp = System.currentTimeMillis()

                    // Create private user data (email, createdAt)
                    val userMap =
                        hashMapOf(
                            "email" to email,
                            "createdAt" to timestamp
                        )

                    // Create public profile data (username only)
                    val profileMap =
                        hashMapOf(
                            "username" to trimmedUsername
                        )
                    // Write both documents
                    firestore.collection("users").document(uid).set(userMap).await()
                    firestore.collection("userProfiles").document(uid).set(profileMap).await()

                    signupSucess.emit(true)
                }
            } catch (e: Exception) {
                Log.w("SignupViewModel", "createUserWithEmail:failure", e)
                errorMessage = e.message ?: getApplication<Application>().getString(R.string.error_signup_failed)
            } finally {
                isLoading = false
            }
        }
    }
}
