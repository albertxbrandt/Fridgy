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
 * Composable screen that allows new users to register for an account.
 *
 * It provides input fields for email, password, and password confirmation.
 * It handles validation feedback and navigation back to the login screen.
 *
 * @param onSignupSuccess Callback triggered when the account is successfully created.
 * @param onNavigateToLogin Callback triggered when the user wants to return to the login screen.
 * @param viewModel The state holder for signup logic.
 */
@Composable
fun SignupScreen(
    onSignupSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: SignupViewModel = hiltViewModel()
) {
    // Navigate to the next screen when the signupSuccess event is received
    LaunchedEffect(Unit) {
        viewModel.signupSuccess.collectLatest { success ->
            if (success) {
                onSignupSuccess()
            }
        }
    }

    AuthScreenLayout(
        title = stringResource(R.string.join_fridgy_today),
        subtitle = stringResource(R.string.unlock_effortless_management),
        errorMessage = viewModel.errorMessage,
        isLoading = viewModel.isLoading,
        primaryButtonText = stringResource(R.string.sign_up),
        onPrimaryClick = viewModel::signup,
        secondaryButtonText = stringResource(R.string.already_have_account),
        onSecondaryClick = onNavigateToLogin
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
                    .testTag("signupEmailInput"),
            enabled = !viewModel.isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        SquaredInput(
            value = viewModel.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text(stringResource(R.string.username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("signupUsernameInput"),
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
                    .testTag("signupPasswordInput"),
            enabled = !viewModel.isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        SquaredInput(
            value = viewModel.confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChange,
            label = { Text(stringResource(R.string.confirm_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("signupConfirmPasswordInput"),
            enabled = !viewModel.isLoading
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewSignupScreen() {
    FridgyTheme {
        SignupScreen(
            onSignupSuccess = {},
            onNavigateToLogin = {}
        )
    }
}
