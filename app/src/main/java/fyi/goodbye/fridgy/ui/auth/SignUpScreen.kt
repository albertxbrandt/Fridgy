package fyi.goodbye.fridgy.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.shared.components.SquaredButton
import fyi.goodbye.fridgy.ui.shared.components.SquaredInput
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue
import fyi.goodbye.fridgy.ui.theme.FridgyTextBlue
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onSignupSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: SignupViewModel = viewModel()
) {
    // Navigate to the next screen when the signupSuccess event is received
    LaunchedEffect(Unit) {
        viewModel.signupSuccess.collectLatest { success ->
            if (success) {
                onSignupSuccess()
            }
        }
    }

    Scaffold(
        containerColor = FridgyWhite
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .background(FridgyWhite),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = stringResource(R.string.cd_fridge_logo),
                modifier =
                    Modifier
                        .size(160.dp)
                        .padding(bottom = 32.dp)
            )

            Text(
                text = stringResource(R.string.join_fridgy_today),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = FridgyTextBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(R.string.unlock_effortless_management),
                fontSize = 16.sp,
                color = FridgyTextBlue.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 48.dp)
            )

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

            viewModel.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            SquaredButton(
                onClick = viewModel::signup,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FridgyDarkBlue, contentColor = FridgyWhite),
                enabled = !viewModel.isLoading
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(color = FridgyWhite, modifier = Modifier.size(24.dp))
                } else {
                    Text(stringResource(R.string.sign_up), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onNavigateToLogin,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = !viewModel.isLoading
            ) {
                Text(
                    text = stringResource(R.string.already_have_account),
                    color = FridgyTextBlue,
                    fontSize = 16.sp
                )
            }
        }
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
