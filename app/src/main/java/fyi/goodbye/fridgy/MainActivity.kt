package fyi.goodbye.fridgy

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import fyi.goodbye.fridgy.ui.adminPanel.AdminPanelScreen
import fyi.goodbye.fridgy.ui.auth.LoginScreen
import fyi.goodbye.fridgy.ui.auth.SignupScreen
import fyi.goodbye.fridgy.ui.fridgeInventory.BarcodeScannerScreen
import fyi.goodbye.fridgy.ui.fridgeInventory.FridgeInventoryScreen
import fyi.goodbye.fridgy.ui.fridgeList.FridgeListScreen
import fyi.goodbye.fridgy.ui.fridgeSettings.FridgeSettingsScreen
import fyi.goodbye.fridgy.ui.itemDetail.ItemDetailScreen
import fyi.goodbye.fridgy.ui.theme.FridgyTheme

/**
 * Main entry point for the Fridgy application.
 *
 * This activity handles:
 * - Firebase initialization (Auth, Firestore, App Check)
 * - Navigation setup using Jetpack Compose Navigation
 * - App Check configuration (Debug provider for development, Play Integrity for production)
 *
 * ## Navigation Routes
 * - `login` / `signup` - Authentication screens
 * - `fridgeList` - Main screen showing user's fridges and invitations
 * - `fridgeInventory/{fridgeId}` - Inventory screen for a specific fridge
 * - `itemDetail/{fridgeId}/{itemId}` - Detail view for a specific item
 * - `barcodeScanner/{fridgeId}` - Barcode scanner for adding items
 * - `fridgeSettings/{fridgeId}` - Settings/member management for a fridge
 * - `adminPanel` - Admin dashboard (admin users only)
 *
 * ## App Check Setup
 * - Debug builds use `DebugAppCheckProviderFactory` - check logcat for debug token
 * - Production builds use `PlayIntegrityAppCheckProviderFactory`
 *
 * @see FridgyTheme for the app's Material 3 theme configuration
 */
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

                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) {
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate("fridgeList") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onNavigateToSignup = {
                                    navController.navigate("signup") {
                                        launchSingleTop = true
                                    }
                                }
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
                                    navController.navigate("fridgeInventory/${displayFridge.id}/${Uri.encode(displayFridge.name)}") {
                                        launchSingleTop = true
                                    }
                                },
                                onAddFridgeClick = { },
                                onNotificationsClick = { },
                                onProfileClick = { },
                                onNavigateToAdminPanel = {
                                    navController.navigate("adminPanel") {
                                        launchSingleTop = true
                                    }
                                },
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("adminPanel") {
                            AdminPanelScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            "fridgeInventory/{fridgeId}/{fridgeName}",
                            arguments =
                                listOf(
                                    navArgument("fridgeId") { type = NavType.StringType },
                                    navArgument("fridgeName") { type = NavType.StringType }
                                )
                        ) { backStackEntry ->
                            val fridgeId = backStackEntry.arguments?.getString("fridgeId") ?: ""
                            val fridgeName = backStackEntry.arguments?.getString("fridgeName") ?: ""

                            FridgeInventoryScreen(
                                fridgeId = fridgeId,
                                initialFridgeName = fridgeName,
                                navController = navController,
                                onBackClick = { navController.popBackStack() },
                                onSettingsClick = { id ->
                                    navController.navigate("fridgeSettings/$id") {
                                        launchSingleTop = true
                                    }
                                },
                                onAddItemClick = { currentFridgeId ->
                                    navController.navigate("barcodeScanner/$currentFridgeId") {
                                        launchSingleTop = true
                                    }
                                },
                                onItemClick = { fId, iId ->
                                    navController.navigate("itemDetail/$fId/$iId") {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable(
                            route = "itemDetail/{fridgeId}/{itemId}",
                            arguments =
                                listOf(
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
                            arguments =
                                listOf(
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
                            arguments =
                                listOf(
                                    navArgument("fridgeId") { type = NavType.StringType }
                                )
                        ) { backStackEntry ->
                            val fridgeId = backStackEntry.arguments?.getString("fridgeId") ?: ""

                            BarcodeScannerScreen(
                                onBarcodeScanned = { scannedUpc ->
                                    navController.popBackStack()
                                    navController.currentBackStackEntry?.savedStateHandle?.set("scannedUpc", scannedUpc)
                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                        "targetFridgeId",
                                        fridgeId
                                    )
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
