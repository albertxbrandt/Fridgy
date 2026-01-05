package fyi.goodbye.fridgy.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.shared.components.SquaredButton
import fyi.goodbye.fridgy.ui.shared.components.SquaredInput
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue
import fyi.goodbye.fridgy.ui.theme.FridgyTextBlue
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import fyi.goodbye.fridgy.ui.theme.FridgyWhite

/**
 * Shared layout wrapper for authentication screens (Login and Signup).
 *
 * Provides consistent styling including:
 * - Centered column layout with Fridgy branding
 * - Logo, title, and subtitle header
 * - Error message display area
 * - Primary action button with loading state
 * - Secondary navigation text button
 *
 * @param title The main heading text (e.g., "Welcome to Fridgy")
 * @param subtitle The secondary text below the title
 * @param errorMessage Optional error message to display (null hides it)
 * @param isLoading Whether a loading operation is in progress
 * @param primaryButtonText Text for the main action button
 * @param onPrimaryClick Callback when the primary button is clicked
 * @param secondaryButtonText Text for the secondary navigation link
 * @param onSecondaryClick Callback when the secondary button is clicked
 * @param content The form content (input fields) specific to each screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreenLayout(
    title: String,
    subtitle: String,
    errorMessage: String?,
    isLoading: Boolean,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonText: String,
    onSecondaryClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
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
            // Logo
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = stringResource(R.string.cd_fridge_logo),
                modifier =
                    Modifier
                        .size(160.dp)
                        .padding(bottom = 32.dp)
            )

            // Title
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = FridgyTextBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Subtitle
            Text(
                text = subtitle,
                fontSize = 16.sp,
                color = FridgyTextBlue.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Form content (input fields)
            content()

            // Error message
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary action button
            SquaredButton(
                onClick = onPrimaryClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FridgyDarkBlue,
                    contentColor = FridgyWhite
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = FridgyWhite,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = primaryButtonText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Secondary navigation button
            TextButton(
                onClick = onSecondaryClick,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = !isLoading
            ) {
                Text(
                    text = secondaryButtonText,
                    color = FridgyTextBlue,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun PreviewAuthScreenLayout() {
    FridgyTheme {
        AuthScreenLayout(
            title = "Welcome to Fridgy",
            subtitle = "Manage your fridge effortlessly",
            errorMessage = null,
            isLoading = false,
            primaryButtonText = "Login",
            onPrimaryClick = {},
            secondaryButtonText = "Don't have an account?",
            onSecondaryClick = {}
        ) {
            // Sample content for preview
            SquaredInput(
                value = "",
                onValueChange = {},
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
