package fyi.goodbye.fridgy.ui.auth

import android.content.Context
import android.content.Intent
import timber.log.Timber
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.repositories.UserRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Sealed interface representing the current state of the magic link authentication flow.
 */
sealed interface MagicLinkUiState {
    /** Initial state - user enters email. */
    data object EnterEmail : MagicLinkUiState

    /** Email sent - waiting for user to click the link. */
    data object EmailSent : MagicLinkUiState

    /** New user - needs to enter username before completing signup. */
    data object EnterUsername : MagicLinkUiState

    /** Processing the magic link. */
    data object Processing : MagicLinkUiState

    /** Error state with message. */
    data class Error(val message: String) : MagicLinkUiState
}

/**
 * ViewModel for Magic Link (passwordless) authentication.
 *
 * This ViewModel handles the entire passwordless authentication flow:
 * 1. User enters email and requests a sign-in link
 * 2. Firebase sends an email with a magic link
 * 3. User clicks the link, app handles the deep link
 * 4. If new user, prompt for username
 * 5. Complete sign-in and create user profile if needed
 *
 * @param context Application context for accessing string resources and SharedPreferences.
 * @param auth Firebase Auth instance for authentication operations.
 * @param userRepository Repository for user profile operations.
 * @param magicLinkHandler Handler for receiving magic link intents from MainActivity.
 */
@HiltViewModel
class MagicLinkViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val auth: FirebaseAuth,
        private val userRepository: UserRepository,
        private val magicLinkHandler: MagicLinkHandler
    ) : ViewModel() {
        companion object {
            
            private const val PREFS_NAME = "fridgy_auth"
            private const val KEY_EMAIL_FOR_SIGN_IN = "emailForSignIn"

            /**
             * Custom domain for magic links.
             * The web page at this URL redirects to the app using fridgy:// scheme.
             */
            private const val AUTH_DOMAIN = "fridgyapp.com"

            /** Package name for the Android app. */
            private const val PACKAGE_NAME = "fyi.goodbye.fridgy"
        }

        private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()

        /** Current UI state of the authentication flow. */
        var uiState by mutableStateOf<MagicLinkUiState>(MagicLinkUiState.EnterEmail)
            private set

        /** The email address entered by the user. */
        var email by mutableStateOf("")
            private set

        /** The username entered by the user (for new users). */
        var username by mutableStateOf("")
            private set

        /** Indicates whether an operation is in progress. */
        var isLoading by mutableStateOf(false)
            private set

        private val _authSuccess = MutableSharedFlow<Boolean>()

        /** A stream of success events used to trigger navigation after successful authentication. */
        val authSuccess = _authSuccess.asSharedFlow()

        init {
            // Observe magic link intents from the handler
            viewModelScope.launch {
                magicLinkHandler.pendingIntent
                    .filterNotNull()
                    .collect { intent ->
                        handleMagicLink(intent)
                        magicLinkHandler.clearPendingIntent()
                    }
            }
        }

        /** Updates the email state. */
        fun onEmailChange(newEmail: String) {
            email = newEmail
            // Reset to initial state if user was in error state
            if (uiState is MagicLinkUiState.Error) {
                uiState = MagicLinkUiState.EnterEmail
            }
        }

        /** Updates the username state. */
        fun onUsernameChange(newUsername: String) {
            username = newUsername
            // Reset to username entry state if user was in error state
            if (uiState is MagicLinkUiState.Error) {
                uiState = MagicLinkUiState.EnterUsername
            }
        }

        /**
         * Sends a magic link to the provided email address via SendGrid.
         *
         * The email will contain a link that, when clicked, will sign the user in
         * without requiring a password. This uses a custom Cloud Function to send
         * the email with a fully customized template.
         */
        fun sendMagicLink() {
            if (email.isBlank()) {
                uiState = MagicLinkUiState.Error(context.getString(R.string.error_please_enter_email))
                return
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                uiState = MagicLinkUiState.Error(context.getString(R.string.error_invalid_email_format))
                return
            }

            isLoading = true

            viewModelScope.launch {
                try {
                    // Call the Cloud Function to send magic link via SendGrid
                    val data = hashMapOf("email" to email)
                    val result =
                        functions
                            .getHttpsCallable("sendMagicLink")
                            .call(data)
                            .await()

                    // Save the email locally so we can use it when the user clicks the link
                    saveEmailForSignIn(email)

                    Timber.d("Magic link sent to: $email via SendGrid")
                    uiState = MagicLinkUiState.EmailSent
                } catch (e: Exception) {
                    Timber.e(e, "Failed to send magic link via Cloud Function")
                    uiState = MagicLinkUiState.Error(getErrorMessage(e))
                } finally {
                    isLoading = false
                }
            }
        }

        /**
         * Handles the incoming magic link from a deep link intent.
         *
         * @param intent The intent containing the magic link.
         */
        fun handleMagicLink(intent: Intent?) {
            val emailLink = intent?.data?.toString()

            if (emailLink == null || !auth.isSignInWithEmailLink(emailLink)) {
                Timber.w("Invalid or missing magic link")
                return
            }

            uiState = MagicLinkUiState.Processing
            isLoading = true

            viewModelScope.launch {
                try {
                    // Get the email that was used to send the link
                    val savedEmail = getSavedEmailForSignIn()

                    if (savedEmail == null) {
                        // Edge case: user opened link on different device
                        // We need to ask for the email again
                        uiState =
                            MagicLinkUiState.Error(
                                context.getString(R.string.error_email_not_found_reenter)
                            )
                        isLoading = false
                        return@launch
                    }

                    // Sign in with the email link
                    val result = auth.signInWithEmailLink(savedEmail, emailLink).await()
                    val user = result.user

                    if (user == null) {
                        uiState = MagicLinkUiState.Error(context.getString(R.string.error_auth_failed))
                        isLoading = false
                        return@launch
                    }

                    Timber.d("Successfully signed in with magic link: ${user.uid}")

                    // Clear the saved email
                    clearSavedEmail()

                    // Check if this is a new user (no profile exists)
                    val existingProfile = userRepository.getUserProfile(user.uid)

                    if (existingProfile == null) {
                        // New user - need to collect username
                        email = savedEmail
                        uiState = MagicLinkUiState.EnterUsername
                    } else {
                        // Existing user - sign-in complete
                        _authSuccess.emit(true)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to complete magic link sign-in")
                    uiState = MagicLinkUiState.Error(getErrorMessage(e))
                } finally {
                    isLoading = false
                }
            }
        }

        /**
         * Completes the signup process for new users by creating their profile.
         */
        fun completeSignup() {
            val trimmedUsername = username.trim()

            // Validate username
            if (trimmedUsername.isBlank()) {
                uiState = MagicLinkUiState.Error(context.getString(R.string.error_please_enter_username))
                return
            }

            if (!trimmedUsername.matches(Regex("^[a-zA-Z0-9_.]+$"))) {
                uiState = MagicLinkUiState.Error(context.getString(R.string.error_username_invalid_chars))
                return
            }

            if (trimmedUsername.length < 3) {
                uiState = MagicLinkUiState.Error(context.getString(R.string.error_username_too_short))
                return
            }

            if (trimmedUsername.length > 16) {
                uiState = MagicLinkUiState.Error(context.getString(R.string.error_username_too_long))
                return
            }

            isLoading = true

            viewModelScope.launch {
                try {
                    val currentUser = auth.currentUser
                    if (currentUser == null) {
                        uiState = MagicLinkUiState.Error(context.getString(R.string.error_not_authenticated))
                        isLoading = false
                        return@launch
                    }

                    // Check if username is taken
                    if (userRepository.isUsernameTaken(trimmedUsername)) {
                        uiState =
                            MagicLinkUiState.Error(
                                context.getString(R.string.error_username_taken, trimmedUsername)
                            )
                        isLoading = false
                        return@launch
                    }

                    // Create user profile documents
                    userRepository.createUserDocuments(
                        uid = currentUser.uid,
                        email = currentUser.email ?: email,
                        username = trimmedUsername
                    )

                    Timber.d("User profile created for: ${currentUser.uid}")
                    _authSuccess.emit(true)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create user profile")
                    uiState = MagicLinkUiState.Error(getErrorMessage(e))
                } finally {
                    isLoading = false
                }
            }
        }

        /**
         * Resets the UI state to allow the user to try again.
         */
        fun resetToEmailEntry() {
            uiState = MagicLinkUiState.EnterEmail
            email = ""
            username = ""
        }

        /**
         * Goes back to email entry from the email sent state.
         */
        fun goBackToEmailEntry() {
            uiState = MagicLinkUiState.EnterEmail
        }

        // ==================== Private Helper Methods ====================

        /**
         * Saves the email address to SharedPreferences for later use when the magic link is clicked.
         */
        private fun saveEmailForSignIn(email: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_EMAIL_FOR_SIGN_IN, email)
                .apply()
        }

        /**
         * Retrieves the saved email address from SharedPreferences.
         */
        private fun getSavedEmailForSignIn(): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_EMAIL_FOR_SIGN_IN, null)
        }

        /**
         * Clears the saved email address from SharedPreferences.
         */
        private fun clearSavedEmail() {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_EMAIL_FOR_SIGN_IN)
                .apply()
        }

        /**
         * Converts Firebase exceptions into user-friendly error messages.
         *
         * @param exception The exception to convert
         * @return A localized, user-friendly error message
         */
        private fun getErrorMessage(exception: Exception): String {
            return when (exception) {
                is FirebaseNetworkException -> {
                    "No internet connection. Please check your network and try again."
                }
                is FirebaseFunctionsException -> {
                    when (exception.code) {
                        FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                            "Invalid email address. Please check and try again."
                        FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                            "Authentication failed. Please try again."
                        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                        FirebaseFunctionsException.Code.UNAVAILABLE ->
                            "Service temporarily unavailable. Please try again in a moment."
                        FirebaseFunctionsException.Code.NOT_FOUND ->
                            "Service not found. Please contact support."
                        else -> context.getString(R.string.error_sending_magic_link)
                    }
                }
                is FirebaseAuthException -> {
                    when (exception.errorCode) {
                        "ERROR_INVALID_EMAIL" ->
                            context.getString(R.string.error_invalid_email_format)
                        "ERROR_USER_DISABLED" ->
                            "This account has been disabled. Please contact support."
                        "ERROR_INVALID_ACTION_CODE" ->
                            "This sign-in link is invalid or has expired. Please request a new one."
                        "ERROR_EXPIRED_ACTION_CODE" ->
                            "This sign-in link has expired. Please request a new one."
                        else -> context.getString(R.string.error_auth_failed)
                    }
                }
                is FirebaseException -> {
                    // Generic Firebase exception
                    exception.message ?: "An unexpected error occurred. Please try again."
                }
                else -> {
                    // Unknown exception
                    Timber.w("Unexpected exception type: ${exception::class.simpleName}")
                    exception.message ?: "An unexpected error occurred. Please try again."
                }
            }
        }
    }

