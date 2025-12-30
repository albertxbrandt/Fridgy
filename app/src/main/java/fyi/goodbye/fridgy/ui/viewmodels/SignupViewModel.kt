package fyi.goodbye.fridgy.ui.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
class SignupViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /** The email address entered by the user. */
    var email by mutableStateOf("")
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

    private val _signupSucess = MutableSharedFlow<Boolean>()
    /** A stream of success events used to trigger navigation after a successful signup. */
    val signupSuccess = _signupSucess.asSharedFlow()

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

    /** Updates the confirm password state and clears any existing error message. */
    fun onConfirmPasswordChange(newConfirmPassword: String) {
        confirmPassword = newConfirmPassword
        errorMessage = null
    }

    /**
     * Attempts to sign up a new user with the current email and password states.
     * 
     * Performs basic validation, then creates a Firebase Auth user. If successful,
     * it creates a corresponding document in the 'users' collection in Firestore.
     */
    fun signup() {
        errorMessage = null
        if (email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            errorMessage = "Please fill in all fields."
            return
        }

        if (password != confirmPassword) {
            errorMessage = "Passwords do not match."
            return
        }

        isLoading = true

        viewModelScope.launch {
            try {
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val uid = authResult.user?.uid

                if (uid != null) {
                    val userMap = hashMapOf(
                        "email" to email,
                        "username" to email.substringBefore("@"),
                        "createdAt" to System.currentTimeMillis()
                    )

                    firestore.collection("users").document(uid).set(userMap).await()
                    _signupSucess.emit(true)
                }
            } catch (e: Exception) {
                Log.w("SignupViewModel", "createUserWithEmail:failure", e)
                errorMessage = e.message ?: "Sign up failed. Please try again."
            } finally {
                isLoading = false
            }
        }
    }
}
