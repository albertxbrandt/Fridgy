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

class SignupViewModel : ViewModel() {
    // Authentication Instance
    private val auth = FirebaseAuth.getInstance()
    // Firestore instance
    private val firestore = FirebaseFirestore.getInstance()

    // UI state for email, password, confirm password
    var email by mutableStateOf("")
        private set // Only VM can modify

    var password by mutableStateOf("")
        private set

    var confirmPassword by mutableStateOf("")
        private set

    // UI state for displaying error messages
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // UI state for showing loading indicator
    var isLoading by mutableStateOf(false)
        private set

    // One-time event for successful signup to trigger nav
    private val _signupSucess = MutableSharedFlow<Boolean>()
    val signupSuccess = _signupSucess.asSharedFlow()

    fun onEmailChange(newEmail: String) {
        email = newEmail
        errorMessage = null
    }

    fun onPasswordChange(newPassword: String) {
        password = newPassword
        errorMessage = null
    }

    fun onConfirmPasswordChange(newConfirmPassword: String) {
        confirmPassword = newConfirmPassword
        errorMessage = null
    }

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

        isLoading = true // Show loading indicator

        viewModelScope.launch {
            try {
                // Firebase authentication: Create user
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                Log.d("SignupViewModel", "createUserWithEmail:success")

                // Get the new user's uid
                val uid = authResult.user?.uid

                if (uid != null) {
                    // Firestore: Create User Document
                    val userMap = hashMapOf(
                        "email" to email,
                        "username" to email.substringBefore("@"),
                        "createdAt" to System.currentTimeMillis(),
                        "ownerOfFridges" to emptyList<String>(),
                        "memberOfFridges" to emptyList<String>(),
                        "invitations" to emptyList<String>()
                    )

                    firestore.collection("users").document(uid).set(userMap).await()
                    Log.d("SignupViewModel", "User document created in Firestore for UID: $uid")

                    // Emit success event
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