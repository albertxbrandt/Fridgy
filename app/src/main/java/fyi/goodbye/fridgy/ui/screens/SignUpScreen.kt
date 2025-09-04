package fyi.goodbye.fridgy.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.* // Ensure this is importing Material 3
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
import fyi.goodbye.fridgy.ui.theme.FridgyTextBlue
import fyi.goodbye.fridgy.ui.elements.SquaredInput
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.elements.SquaredButton
import fyi.goodbye.fridgy.ui.viewmodels.SignupViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onSignupSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: SignupViewModel = viewModel()
) {

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
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .background(FridgyWhite),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Fridgy Logo",
                modifier = Modifier
                    .size(160.dp)
                    .padding(bottom = 32.dp)
            )

            Text(
                text = "Join Fridgy Today!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = FridgyTextBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Unlock effortless fridge management.",
                fontSize = 16.sp,
                color = FridgyTextBlue.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Email Input Field
            SquaredInput(
                value = viewModel.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                enabled = !viewModel.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Input Field
            SquaredInput(
                value = viewModel.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                enabled = !viewModel.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm Password Input Field
            SquaredInput(
                value = viewModel.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = { Text("Confirm Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier =
                    Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
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

            SquaredButton (
                onClick = viewModel::signup,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FridgyDarkBlue, contentColor = FridgyWhite),
                enabled = !viewModel.isLoading
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(color = FridgyWhite, modifier = Modifier.size(24.dp))
                } else {
                    Text("Sign Up", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onNavigateToLogin,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = !viewModel.isLoading
            ) {
                Text(
                    text = "Already have an account? Log In",
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
            onSignupSuccess = {
                println("Signup success!")
            },
            onNavigateToLogin = {
                println("Navigate to Login")
            }
        )
    }
}