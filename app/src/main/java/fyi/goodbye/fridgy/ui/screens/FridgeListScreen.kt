package fyi.goodbye.fridgy.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape // <--- NEW IMPORT for the badge shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue
import fyi.goodbye.fridgy.ui.theme.FridgyLightBlue
import fyi.goodbye.fridgy.ui.theme.FridgyTextBlue
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
import fyi.goodbye.fridgy.ui.viewmodels.FridgeListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeListScreen(
    onNavigateToFridgeInventory: (DisplayFridge) -> Unit,
    onAddFridgeClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onLogout: () -> Unit,
    viewModel: FridgeListViewModel = viewModel()
) {
    var showNotificationsDialog by remember { mutableStateOf(false) }
    // State to control the visibility of the "!" badge
    var hasNewNotifications by remember { mutableStateOf(true) } // <--- NEW STATE (set to true for demonstration)
    val fridgeUiState by viewModel.fridgesUiState.collectAsState()
    val auth = remember { FirebaseAuth.getInstance() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "My Fridges",
                        color = FridgyWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = FridgyDarkBlue
                ),
                actions = {
                    IconButton(
                        onClick = {
                            showNotificationsDialog = true
                            hasNewNotifications = false // Optionally clear badge when dialog is opened
                        }
                    ) {
                        Box { // <--- Box to layer icon and badge
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = FridgyWhite
                            )
                            // Notification Badge
                            if (hasNewNotifications) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp) // Size of the badge circle
                                        .align(Alignment.TopEnd) // Position at top-end corner
                                        .offset(x = 3.dp, y = (-3).dp) // Fine-tune position
                                        .background(Color.Red, CircleShape), // Red circular background
                                    contentAlignment = Alignment.Center // Center content (the "!")
                                ) {
                                    Text(
                                        text = "!",
                                        color = Color.White,
                                        fontSize = 10.sp, // Small font size for "!"
                                        fontWeight = FontWeight.Black // Bold exclamation
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onProfileClick) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = FridgyWhite
                        )
                    }
                    // Logout Button <--- NEW BUTTON
                    IconButton(onClick = {
                        auth.signOut() // Sign out from Firebase
                        onLogout()     // Call the logout callback to navigate
                    }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout",
                            tint = FridgyWhite
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.createNewFridge("Untitled") },
                containerColor = FridgyDarkBlue,
                contentColor = FridgyWhite
            ) {
                Icon(Icons.Default.Add, "Add new fridge")
            }
        },
        containerColor = FridgyLightBlue
    ) { paddingValues ->
        // Use a 'when' expression to render UI based on the current fridgeUiState
        when (val state = fridgeUiState) { // Smart-cast 'state' to its specific subtype
            // Loading State: Show a progress indicator
            FridgeListViewModel.FridgeUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = FridgyDarkBlue)
                }
            }
            // Error State: Display an error message and a retry button (optional)
            is FridgeListViewModel.FridgeUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Error: ${state.message}", // Access message from the Error state
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(onClick = { /* TODO: Implement a retry mechanism in ViewModel if needed */ }) {
                        Text("Retry")
                    }
                }
            }
            // Success State: Display the list of fridges or an empty state message
            is FridgeListViewModel.FridgeUiState.Success -> {
                val fridges = state.fridges // Access the list of fridges from the Success state
                if (fridges.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No fridges yet! Click the '+' button to create one.",
                            fontSize = 18.sp,
                            color = FridgyTextBlue.copy(alpha = 0.8f),
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Display the list of fridges using LazyColumn
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp), // Spacing between fridge cards
                        contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
                    ) {
                        items(fridges) { fridge ->
                            FridgeCard(fridge = fridge) { clickedFridge ->
                                onNavigateToFridgeInventory(clickedFridge)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNotificationsDialog) {
        NotificationsDialog(
            onDismissRequest = { showNotificationsDialog = false }
        )
    }
}

@Composable
fun FridgeCard(fridge: DisplayFridge, onClick: (DisplayFridge) -> Unit) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clickable { onClick(fridge) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridgyWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fridge.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FridgyDarkBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Created by: ${fridge.creatorDisplayName}",
                    fontSize = 14.sp,
                    color = FridgyTextBlue.copy(alpha = 0.7f)
                )
                Text(
                    text = "Members: ${fridge.members.size}",
                    fontSize = 14.sp,
                    color = FridgyTextBlue.copy(alpha = 0.7f)
                )
                Text(
                    text = "Added: ${dateFormatter.format(Date(fridge.createdAt))}",
                    fontSize = 12.sp,
                    color = FridgyTextBlue.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun NotificationsDialog(
    onDismissRequest: () -> Unit
) {
    val dummyNotifications = listOf(
        "Invitation to 'Party Fridge' from Bob!",
        "Milk in 'Home Fridge' expires tomorrow.",
        "Alice accepted your invitation to 'Office Fridge'."
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Notifications",
                color = FridgyDarkBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                if (dummyNotifications.isEmpty()) {
                    Text(
                        text = "No new notifications.",
                        color = FridgyTextBlue.copy(alpha = 0.8f)
                    )
                } else {
                    dummyNotifications.forEach { notification ->
                        Text(
                            text = "• $notification",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = FridgyTextBlue
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close", color = FridgyDarkBlue)
            }
        },
        containerColor = FridgyWhite,
        shape = RoundedCornerShape(16.dp)
    )
}


@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
fun PreviewFridgeListScreen() {
    FridgyTheme {
        FridgeListScreen(
            onNavigateToFridgeInventory = { fridgeId ->
                Log.d("FridgeListScreen", "Navigate to inventory for $fridgeId")
            },
            onAddFridgeClick = {
                Log.d("FridgeListScreen", "Add Fridge clicked")
            },
            onNotificationsClick = {
                Log.d("FridgeListScreen", "Notifications icon clicked (handled internally)")
            },
            onProfileClick = {
                Log.d("FridgeListScreen", "Profile clicked")
            },
            {
                Log.d("FridgeListScreen", "Logout")
            }
        )
    }
}