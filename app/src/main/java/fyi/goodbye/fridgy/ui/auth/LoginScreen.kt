package fyi.goodbye.fridgy.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.shared.components.SquaredInput
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import kotlinx.coroutines.flow.collectLatest

/**
 * Composable screen that allows users to log in to their account.
 *
 * It provides input fields for email and password, displays error messages,
 * and handles the transition to the [SignupScreen] or main application list.
 *
 * @param onLoginSuccess Callback triggered when the user successfully authenticates.
 * @param onNavigateToSignup Callback triggered when the user clicks the "Sign Up" button.
 * @param viewModel The state holder for login logic.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    // Navigate to the next screen when the loginSuccess event is received
    LaunchedEffect(Unit) {
        viewModel.loginSuccess.collectLatest { success ->
            if (success) {
                onLoginSuccess()
            }
        }
    }

    AuthScreenLayout(
        title = stringResource(R.string.welcome_to_fridgy),
        subtitle = stringResource(R.string.manage_fridge_effortlessly),
        errorMessage = viewModel.errorMessage,
        isLoading = viewModel.isLoading,
        primaryButtonText = stringResource(R.string.login),
        onPrimaryClick = viewModel::login,
        secondaryButtonText = stringResource(R.string.dont_have_account),
        onSecondaryClick = onNavigateToSignup
    ) {
        SquaredInput(
            value = viewModel.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text(stringResource(R.string.email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("emailInput"),
            enabled = !viewModel.isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        SquaredInput(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("passwordInput"),
            enabled = !viewModel.isLoading
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewLoginScreen() {
    FridgyTheme {
        LoginScreen(
            onLoginSuccess = {},
            onNavigateToSignup = {}
        )
    }
}
