package fyi.goodbye.fridgy

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.ui.screens.BarcodeScannerScreen
import fyi.goodbye.fridgy.ui.screens.FridgeInventoryScreen
import fyi.goodbye.fridgy.ui.screens.LoginScreen
import fyi.goodbye.fridgy.ui.screens.SignupScreen
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import fyi.goodbye.fridgy.ui.screens.FridgeListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth outside Composable for checking current user
        val auth = FirebaseAuth.getInstance()

        setContent {
            FridgyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController() // Create NavController

                    // Check if user is already logged in
                    val startDestination = if (auth.currentUser != null) "fridgeList" else "login"

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate("fridgeList") {
                                        // Pop up to the start destination of the graph to avoid
                                        // having multiple copies of the same destination on the stack
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onNavigateToSignup = { navController.navigate("signup") }
                            )
                        }
                        composable("signup") {
                            SignupScreen(
                                onSignupSuccess = {
                                    // After signup, navigate to fridge list, clearing back stack
                                    navController.navigate("fridgeList") {
                                        popUpTo("login") { inclusive = true } // Clear login/signup from stack
                                    }
                                },
                                onNavigateToLogin = { navController.popBackStack() } // Go back to login
                            )
                        }
                        composable("fridgeList") {
                            FridgeListScreen(
                                onNavigateToFridgeInventory = { displayFridge ->
                                    navController.navigate("fridgeInventory/${displayFridge.id}")
                                },
                                onAddFridgeClick = { /* TODO: Implement create fridge screen navigation */ },
                                onNotificationsClick = { /* Handled internally by FridgeListScreen */ },
                                onProfileClick = { /* TODO: Implement profile screen navigation */ },
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo(navController.graph.id) {inclusive = true}
                                    }
                                }
                            )
                        }
                        composable (
                            route = "fridgeInventory/{fridgeId}",
                            arguments = listOf(
                                navArgument("fridgeId") {type = NavType.StringType}
                            )
                        ) { backStackEntry ->
                            // The ViewModel will use the fridgeId from SavedStateHandle to fetch DisplayFridge
                            val fridgeId = backStackEntry.arguments?.getString("fridgeId") ?: ""

                            // Pass the fridgeId to FridgeInventoryScreen. The screen will then inject its ViewModel.
                            FridgeInventoryScreen(
                                fridgeId = fridgeId, // Pass only the ID
                                navController= navController,
                                onBackClick = { navController.popBackStack() },
                                onSettingsClick = { id -> Log.d("Nav", "Settings for $id clicked") },
                                onAddItemClick = { currentFridgeId -> // Callback to add item, will now navigate to scanner
                                    // Pass the fridgeId to the scanner screen so it knows which fridge to add to
                                    navController.navigate("barcodeScanner/$currentFridgeId")
                                },
                                onItemClick = { fId, iId -> Log.d("Nav", "Item $iId in $fId clicked") }
                            )
                        }
                        composable(
                            route = "barcodeScanner/{fridgeId}", // Pass fridgeId to scanner
                            arguments = listOf(
                                navArgument("fridgeId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val fridgeId = backStackEntry.arguments?.getString("fridgeId") ?: ""

                            BarcodeScannerScreen(
                                onBarcodeScanned = { scannedUpc ->
                                    Log.d("MainActivity", "Barcode scanned: $scannedUpc for fridge $fridgeId")

                                    // Pop back from scanner, and pass data to the FridgeInventoryScreen's SavedStateHandle
                                    navController.popBackStack()

                                    // Ensure you're setting the data to the SavedStateHandle of the destination (FridgeInventoryScreen)
                                    // not the current one. The previousBackStackEntry is the FridgeInventoryScreen's entry.
                                    navController.currentBackStackEntry?.savedStateHandle?.set("scannedUpc", scannedUpc)
                                    navController.currentBackStackEntry?.savedStateHandle?.set("targetFridgeId", fridgeId)
                                },
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
//class MainActivity : ComponentActivity() {
//    private val repo = FridgeRepository()
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        enableEdgeToEdge() // Enables edge-to-edge display for modern Android UI
//        setContent {
//            FridgeApp()
//        }
//
//
//
//    }
//
//    @Composable
//    fun TwoButtonScreen() {
//        val scope = rememberCoroutineScope()
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(16.dp),
//            verticalArrangement = Arrangement.Center,
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Button(
//                onClick = {
//                    scope.launch {
//                        val uEmail = "alleg0mail@gmail.com"
//                        val myFridges = repo.getFridges().filter { it.createdBy == uEmail }
//                        val fridge = myFridges.get(0)
//                        val item = GroceryItem(
//                            fridgeId = fridge.id,
//                            upc = "4321",
//                            quantity = 1,
//                            addedBy = uEmail,
//                            addedAt = System.currentTimeMillis(),
//                            lastUpdatedBy = uEmail,
//                            lastUpdatedAt = System.currentTimeMillis()
//                        )
//
//                        repo.addItem(item)
//                    }
//                },
//                modifier = Modifier.padding(bottom = 8.dp)
//            ) {
//                Text(text = "Button 1")
//            }
//            Button(
//                onClick = {
//                    scope.launch {
//                        val uEmail = "alleg0mail@gmail.com"
//                        val myFridges = repo.getFridges().filter { it.createdBy == uEmail }
//                        val fridge = myFridges.get(0)
//
//                        val items = repo.getItemsForFridge(fridge.id)
//
//                        Log.d("Firebase", items.size.toString())
//
//                        for (item in items) {
//                            Log.d("Firebase", item.addedBy)
//                            Log.d("Firebase", item.upc)
//
//                        }
//                    }
//                }
//            ) {
//                Text(text = "Button 2")
//            }
//        }
//    }
//}
