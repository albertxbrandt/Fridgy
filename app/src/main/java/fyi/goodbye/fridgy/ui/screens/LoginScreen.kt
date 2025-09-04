package fyi.goodbye.fridgy.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.elements.SquaredButton
import fyi.goodbye.fridgy.ui.elements.SquaredInput
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue
import fyi.goodbye.fridgy.ui.theme.FridgyTextBlue
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
import fyi.goodbye.fridgy.ui.viewmodels.LoginViewModel
import kotlinx.coroutines.flow.collectLatest


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    viewModel: LoginViewModel = viewModel() // Get ViewModel Instance
) {
    // Collect one-time success event from ViewModel
    LaunchedEffect(Unit) {
        viewModel.loginSuccess.collectLatest { success ->
            if (success) {
                onLoginSuccess()
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

            // App Name / Tagline
            Text(
                text = "Welcome to Fridgy!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = FridgyTextBlue, // <--- CHANGED: Text color to White
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Manage your fridge, effortlessly.",
                fontSize = 16.sp,
                color = FridgyTextBlue.copy(alpha = 0.7f), // <--- CHANGED: Text color to White (slightly transparent)
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

            // Error Message Display (color remains default error red)
            viewModel.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Login Button (background remains DarkerBlue, text White)
            SquaredButton(
                onClick = viewModel::login,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FridgyDarkBlue, contentColor = FridgyWhite),
                enabled = !viewModel.isLoading
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(color = FridgyWhite, modifier = Modifier.size(24.dp))
                } else {
                    Text("Login", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Don't have an account? Sign Up button
            TextButton(
                onClick = onNavigateToSignup,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = !viewModel.isLoading
            ) {
                Text(
                    text = "Don't have an account? Sign Up",
                    color = FridgyTextBlue, // <--- CHANGED: Text color to White
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewLoginScreen() {
    FridgyTheme {
        LoginScreen(
            onLoginSuccess = {
                println("Login clicked")
            },
            onNavigateToSignup = {
                println("Navigate to Signup")
            }
        )
    }
}