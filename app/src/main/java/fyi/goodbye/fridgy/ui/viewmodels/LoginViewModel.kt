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

class LoginViewModel : ViewModel() {

    // Authentication instance
    private val auth = FirebaseAuth.getInstance()

    // UI state for email and password
    var email by mutableStateOf("")
        private set // Only allow modification from viewmodel

    var password by mutableStateOf("")
        private set

    // UI state for displaying error msgs
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // UI state for showing loading indicator
    var isLoading by mutableStateOf(false)
        private set

    // One-time event for successful login to trigger navigation
    private val _loginSuccess = MutableSharedFlow<Boolean>()
    val loginSuccess = _loginSuccess.asSharedFlow()

    fun onEmailChange(newEmail: String) {
        email = newEmail
        errorMessage = null // Clear error when user types
    }

    fun onPasswordChange(newPassword: String) {
        password = newPassword
        errorMessage = null
    }

    fun login() {
        errorMessage = null // Clear any previous errors

        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Please enter both email and password."
            return
        }

        isLoading = true // Show loading

        viewModelScope.launch {
            try {
                // Firebase authentication: Sign in user
                auth.signInWithEmailAndPassword(email, password).await()
                Log.d("LoginViewModel", "signInWithEmail:success")

                // Emit success event
                _loginSuccess.emit(true)

            } catch (e: Exception) {
                Log.w("LoginViewModel", "signInWithEmail:failure", e)
                errorMessage = e.message ?: "Login failed. Please try again."
            } finally {
                isLoading = false // Hide loading indicator
            }
        }
    }

}