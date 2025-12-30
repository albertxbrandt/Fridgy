package fyi.goodbye.fridgy.ui.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel responsible for managing the user login process.
 * 
 * It handles user input for email and password, interacts with Firebase Authentication
 * to sign in users, and exposes UI states such as loading status and error messages.
 */
class LoginViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    /** The email address entered by the user. */
    var email by mutableStateOf("")
        private set

    /** The password entered by the user. */
    var password by mutableStateOf("")
        private set

    /** Any error message resulting from the login attempt. */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Indicates whether a login operation is currently in progress. */
    var isLoading by mutableStateOf(false)
        private set

    private val _loginSuccess = MutableSharedFlow<Boolean>()
    /** A stream of success events used to trigger navigation after a successful login. */
    val loginSuccess = _loginSuccess.asSharedFlow()

    /** Updates the email state and clears any existing error message. */
    fun onEmailChange(newEmail: String) {
        email = newEmail
        errorMessage = null
    }

    /** Updates the password state and clears any existing error message. */
    fun onPasswordChange(newPassword: String) {
        password = newPassword
        errorMessage = null
    }

    /**
     * Attempts to sign in the user using the current email and password states.
     * 
     * If successful, emits a [loginSuccess] event. Otherwise, updates [errorMessage].
     */
    fun login() {
        errorMessage = null

        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Please enter both email and password."
            return
        }

        isLoading = true

        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _loginSuccess.emit(true)
            } catch (e: Exception) {
                Log.w("LoginViewModel", "signInWithEmail:failure", e)
                errorMessage = e.message ?: "Login failed. Please try again."
            } finally {
                isLoading = false
            }
        }
    }
}
