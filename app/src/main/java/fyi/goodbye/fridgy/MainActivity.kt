package fyi.goodbye.fridgy

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.ui.screens.*
import fyi.goodbye.fridgy.ui.theme.FridgyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Initialize App Check
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        
        if (BuildConfig.DEBUG) {
            // Use Debug Provider for emulators/local development
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d("Fridgy_AppCheck", "Firebase App Check initialized with Debug Provider")
            
            // Explicitly request a token to force the debug secret to be printed in logcat early
            firebaseAppCheck.getAppCheckToken(false).addOnSuccessListener { token ->
                Log.d("Fridgy_AppCheck", "Initial token retrieved: ${token.token.take(10)}...")
            }.addOnFailureListener { e ->
                Log.e("Fridgy_AppCheck", "Failed to retrieve initial token: ${e.message}")
            }
        } else {
            // Use Play Integrity for production
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }

        val auth = FirebaseAuth.getInstance()

        setContent {
            FridgyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val startDestination = if (auth.currentUser != null) "fridgeList" else "login"

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate("fridgeList") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onNavigateToSignup = { navController.navigate("signup") }
                            )
                        }
                        composable("signup") {
                            SignupScreen(
                                onSignupSuccess = {
                                    navController.navigate("fridgeList") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onNavigateToLogin = { navController.popBackStack() }
                            )
                        }
                        composable("fridgeList") {
                            FridgeListScreen(
                                onNavigateToFridgeInventory = { displayFridge ->
                                    navController.navigate("fridgeInventory/${displayFridge.id}")
                                },
                                onAddFridgeClick = { },
                                onNotificationsClick = { },
                                onProfileClick = { },
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
                            val fridgeId = backStackEntry.arguments?.getString("fridgeId") ?: ""

                            FridgeInventoryScreen(
                                fridgeId = fridgeId,
                                navController= navController,
                                onBackClick = { navController.popBackStack() },
                                onSettingsClick = { id -> 
                                    navController.navigate("fridgeSettings/$id")
                                },
                                onAddItemClick = { currentFridgeId ->
                                    navController.navigate("barcodeScanner/$currentFridgeId")
                                },
                                onItemClick = { fId, iId -> 
                                    navController.navigate("itemDetail/$fId/$iId")
                                }
                            )
                        }
                        composable(
                            route = "itemDetail/{fridgeId}/{itemId}",
                            arguments = listOf(
                                navArgument("fridgeId") { type = NavType.StringType },
                                navArgument("itemId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val fridgeId = backStackEntry.arguments?.getString("fridgeId") ?: ""
                            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                            ItemDetailScreen(
                                fridgeId = fridgeId,
                                itemId = itemId,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "fridgeSettings/{fridgeId}",
                            arguments = listOf(
                                navArgument("fridgeId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val fridgeId = backStackEntry.arguments?.getString("fridgeId") ?: ""
                            FridgeSettingsScreen(
                                fridgeId = fridgeId,
                                onBackClick = { navController.popBackStack() },
                                onDeleteSuccess = { 
                                    navController.popBackStack("fridgeList", inclusive = false)
                                }
                            )
                        }
                        composable(
                            route = "barcodeScanner/{fridgeId}",
                            arguments = listOf(
                                navArgument("fridgeId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val fridgeId = backStackEntry.arguments?.getString("fridgeId") ?: ""

                            BarcodeScannerScreen(
                                onBarcodeScanned = { scannedUpc ->
                                    navController.popBackStack()
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
