package fyi.goodbye.fridgy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dagger.hilt.android.AndroidEntryPoint
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import fyi.goodbye.fridgy.repositories.NotificationRepository
import fyi.goodbye.fridgy.ui.adminPanel.AdminPanelScreen
import fyi.goodbye.fridgy.ui.auth.MagicLinkScreen
import fyi.goodbye.fridgy.ui.fridgeInventory.BarcodeScannerScreen
import fyi.goodbye.fridgy.ui.fridgeInventory.FridgeInventoryScreen
import fyi.goodbye.fridgy.ui.fridgeList.FridgeListScreen
import fyi.goodbye.fridgy.ui.fridgeSettings.FridgeSettingsScreen
import fyi.goodbye.fridgy.ui.householdList.HouseholdListScreen
import fyi.goodbye.fridgy.ui.householdSettings.HouseholdSettingsScreen
import fyi.goodbye.fridgy.ui.itemDetail.ItemDetailScreen
import fyi.goodbye.fridgy.ui.notifications.NotificationsScreen
import fyi.goodbye.fridgy.ui.shoppingList.ShoppingListScreen
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import fyi.goodbye.fridgy.utils.UserPreferences
import kotlinx.coroutines.launch
import javax.inject.Inject

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
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Repository for managing push notifications and FCM tokens. */
    @Inject
    lateinit var notificationRepository: NotificationRepository

    /** Repository for managing household data and membership validation. */
    @Inject
    lateinit var householdRepository: HouseholdRepository

    /** Handler for magic link deep links. */
    @Inject
    lateinit var magicLinkHandler: fyi.goodbye.fridgy.ui.auth.MagicLinkHandler

    /** Tracks when a new intent with invite code arrives */
    private val inviteIntentTrigger = mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Handle magic link if the app was launched from one
        handleMagicLinkIntent(intent)

        // Handle invite code deep link if present
        handleInviteDeepLink(intent)

        // Configure Firestore for optimal offline performance
        // Note: Persistence is enabled by default in recent Firestore SDK versions
        // Explicitly configure cache size for better performance
        val firestore = FirebaseFirestore.getInstance()
        try {
            val settings =
                FirebaseFirestoreSettings.Builder()
                    .build()
            firestore.firestoreSettings = settings
            Log.d("Fridgy_Firestore", "Firestore configured with offline persistence (default)")
        } catch (e: Exception) {
            Log.w("Fridgy_Firestore", "Firestore settings already configured: ${e.message}")
        }

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

        // Request notification permission and initialize FCM token
        requestNotificationPermissionAndInitializeFCM()

        setContent {
            FridgyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    // Handle pending invite code from deep link
                    val inviteTrigger = inviteIntentTrigger.value
                    LaunchedEffect(inviteTrigger) {
                        val prefs = getSharedPreferences("fridgy_prefs", MODE_PRIVATE)
                        val pendingCode = prefs.getString("pending_invite_code", null)
                        if (pendingCode != null) {
                            Log.d("Fridgy_DeepLink", "Found pending invite code in MainActivity, navigating to householdList")
                            // Give auth state time to settle
                            kotlinx.coroutines.delay(300)
                            // Navigate to household list where the dialog will open
                            if (auth.currentUser != null) {
                                navController.navigate("householdList") {
                                    popUpTo(navController.graph.id) { inclusive = false }
                                    launchSingleTop = true
                                }
                            } else {
                                Log.w("Fridgy_DeepLink", "User not logged in, cannot process invite code")
                                // Clear the code since user can't join without being logged in
                                prefs.edit().remove("pending_invite_code").apply()
                            }
                        }
                    }

                    // Handle deep linking from notifications
                    LaunchedEffect(Unit) {
                        intent?.let { notificationIntent ->
                            val notificationType = notificationIntent.getStringExtra("notificationType")
                            val fridgeId = notificationIntent.getStringExtra("fridgeId")
                            val itemId = notificationIntent.getStringExtra("itemId")

                            if (notificationType != null) {
                                Log.d(
                                    "Fridgy_DeepLink",
                                    "Handling notification: type=$notificationType, fridgeId=$fridgeId, itemId=$itemId"
                                )

                                // Wait a bit for auth state to settle
                                kotlinx.coroutines.delay(500)

                                when (notificationType) {
                                    "FRIDGE_INVITE" -> {
                                        navController.navigate("fridgeList") {
                                            popUpTo(navController.graph.id) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                    "ITEM_ADDED", "ITEM_REMOVED", "ITEM_LOW_STOCK" -> {
                                        if (fridgeId != null && itemId != null) {
                                            navController.navigate("itemDetail/$fridgeId/$itemId") {
                                                popUpTo(navController.graph.id) { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        } else if (fridgeId != null) {
                                            navController.navigate("fridgeInventory/$fridgeId/Fridge") {
                                                popUpTo(navController.graph.id) { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                    "MEMBER_JOINED", "MEMBER_LEFT" -> {
                                        if (fridgeId != null) {
                                            navController.navigate("fridgeInventory/$fridgeId/Fridge") {
                                                popUpTo(navController.graph.id) { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Request camera permission on app launch for barcode scanning
                    val permissionLauncher =
                        rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { isGranted ->
                            if (isGranted) {
                                Log.d("Fridgy_Permission", "Camera permission granted")
                            } else {
                                Log.w("Fridgy_Permission", "Camera permission denied - barcode scanning will not work")
                            }
                        }

                    // Request camera permission if not already granted
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }

                    // Get user preferences for last selected household
                    val userPreferences = remember { UserPreferences.getInstance(this@MainActivity) }

                    // Check for pending invite code from deep link
                    val hasPendingInvite = remember {
                        getSharedPreferences("fridgy_prefs", MODE_PRIVATE)
                            .getString("pending_invite_code", null) != null
                    }

                    // Determine start destination:
                    // - If not logged in: login
                    // - If has pending invite: householdList (to show join dialog)
                    // - If logged in with last household: fridgeList/{householdId}
                    // - If logged in with no last household: householdList
                    val lastHouseholdId = remember { userPreferences.getLastSelectedHouseholdId() }
                    val startDestination =
                        remember {
                            when {
                                auth.currentUser == null -> "login"
                                hasPendingInvite -> "householdList"
                                lastHouseholdId != null -> "fridgeList/$lastHouseholdId"
                                else -> "householdList"
                            }
                        }

                    // Validate that the last household still exists and user is still a member
                    val needsHouseholdValidation =
                        remember { mutableStateOf(lastHouseholdId != null && auth.currentUser != null) }

                    LaunchedEffect(needsHouseholdValidation.value) {
                        if (needsHouseholdValidation.value && lastHouseholdId != null) {
                            try {
                                val household = householdRepository.getHouseholdById(lastHouseholdId)
                                val currentUserId = auth.currentUser?.uid
                                if (household == null || currentUserId !in household.members) {
                                    // Household no longer exists or user is no longer a member
                                    Log.d("Fridgy_Nav", "Last household no longer valid, clearing preference")
                                    userPreferences.clearLastSelectedHouseholdId()
                                    navController.navigate("householdList") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("Fridgy_Nav", "Error validating household: ${e.message}")
                                // On error, still go to household list to be safe
                                userPreferences.clearLastSelectedHouseholdId()
                                navController.navigate("householdList") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                            needsHouseholdValidation.value = false
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) {
                        composable("login") {
                            MagicLinkScreen(
                                onAuthSuccess = {
                                    navController.navigate("householdList") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Household List - main screen after login
                        composable("householdList") {
                            HouseholdListScreen(
                                onNavigateToHousehold = { household ->
                                    // Save selected household for quick access on next launch
                                    userPreferences.setLastSelectedHouseholdId(household.id)
                                    navController.navigate("fridgeList/${household.id}") {
                                        launchSingleTop = true
                                    }
                                },
                                onJoinHouseholdSuccess = { householdId ->
                                    navController.navigate("fridgeList/$householdId") {
                                        popUpTo("householdList") { inclusive = false }
                                    }
                                },
                                onNavigateToNotifications = {
                                    navController.navigate("notifications") {
                                        launchSingleTop = true
                                    }
                                },
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

                        // Fridge list for a specific household
                        composable(
                            route = "fridgeList/{householdId}",
                            arguments =
                                listOf(
                                    navArgument("householdId") { type = NavType.StringType }
                                )
                        ) { backStackEntry ->
                            val householdId = backStackEntry.arguments?.getString("householdId") ?: ""
                            FridgeListScreen(
                                onNavigateToFridgeInventory = { displayFridge ->
                                    navController.navigate(
                                        "fridgeInventory/${displayFridge.id}/${Uri.encode(displayFridge.name)}"
                                    ) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToHouseholdSettings = {
                                    navController.navigate("householdSettings/$householdId") {
                                        launchSingleTop = true
                                    }
                                },
                                onSwitchHousehold = {
                                    // Navigate to household list to pick a different household
                                    navController.navigate("householdList") {
                                        // Pop up to the root so back button doesn't go to old household
                                        popUpTo("householdList") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onShoppingListClick = { householdId ->
                                    navController.navigate("shoppingList/$householdId") {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        // Household settings
                        composable(
                            route = "householdSettings/{householdId}",
                            arguments =
                                listOf(
                                    navArgument("householdId") { type = NavType.StringType }
                                )
                        ) { backStackEntry ->
                            val householdId = backStackEntry.arguments?.getString("householdId") ?: ""
                            HouseholdSettingsScreen(
                                householdId = householdId,
                                onBackClick = { navController.popBackStack() },
                                onDeleteSuccess = {
                                    // Clear saved household preference when leaving or deleting
                                    userPreferences.clearLastSelectedHouseholdId()
                                    navController.navigate("householdList") {
                                        popUpTo("householdList") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("adminPanel") {
                            AdminPanelScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("notifications") {
                            NotificationsScreen(
                                onBackClick = { navController.popBackStack() },
                                onNotificationClick = { notification ->
                                    // Handle notification click - navigate based on type
                                    when {
                                        notification.relatedFridgeId != null && notification.relatedItemId != null -> {
                                            navController.navigate(
                                                "itemDetail/${notification.relatedFridgeId}/${notification.relatedItemId}"
                                            ) {
                                                launchSingleTop = true
                                            }
                                        }
                                        notification.relatedFridgeId != null -> {
                                            navController.navigate(
                                                "fridgeInventory/${notification.relatedFridgeId}/Fridge"
                                            ) {
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }
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
                            route = "shoppingList/{householdId}",
                            arguments =
                                listOf(
                                    navArgument("householdId") { type = NavType.StringType }
                                )
                        ) { backStackEntry ->
                            val householdId = backStackEntry.arguments?.getString("householdId") ?: ""
                            ShoppingListScreen(
                                householdId = householdId,
                                onBackClick = { navController.popBackStack() },
                                onScanClick = { currentHouseholdId ->
                                    // TODO: barcode scanner needs to be updated to work with householdId
                                    navController.navigate("barcodeScanner/$currentHouseholdId") {
                                        launchSingleTop = true
                                    }
                                },
                                navController = navController
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
                                    // Navigate back to fridge list and clear everything above it to prevent
                                    // permission errors from lingering Firestore listeners
                                    navController.navigate("fridgeList") {
                                        popUpTo(
                                            0
                                        ) { inclusive = false } // Clear entire back stack except initial destination
                                        launchSingleTop = true
                                    }
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

                        // User profile screen
                        composable("userProfile") {
                            fyi.goodbye.fridgy.ui.userProfile.UserProfileScreen(
                                onBackClick = { navController.popBackStack() },
                                onAccountDeleted = {
                                    // Clear all preferences and navigate to login
                                    userPreferences.clearLastSelectedHouseholdId()
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Request notification permission (Android 13+) and initialize FCM token.
     * For Android 12 and below, FCM token is initialized immediately.
     */
    private fun requestNotificationPermissionAndInitializeFCM() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires runtime permission for notifications
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted, initialize FCM
                    Log.d("Fridgy_FCM", "Notification permission already granted")
                    initializeFCMToken()
                }
                else -> {
                    // Request permission
                    Log.d("Fridgy_FCM", "Requesting notification permission")
                    registerForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted) {
                            Log.d("Fridgy_FCM", "Notification permission granted by user")
                            initializeFCMToken()
                        } else {
                            Log.w("Fridgy_FCM", "Notification permission denied - push notifications will not work")
                        }
                    }.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Android 12 and below don't require permission
            Log.d("Fridgy_FCM", "Android 12 or below - no permission needed")
            initializeFCMToken()
        }
    }

    /**
     * Initialize FCM token and save to Firestore.
     * This should be called after notification permission is granted (Android 13+)
     * or immediately on app start (Android 12 and below).
     */
    private fun initializeFCMToken() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.d("Fridgy_FCM", "User not authenticated - skipping FCM token initialization")
            return
        }

        lifecycleScope.launch {
            Log.d("Fridgy_FCM", "Initializing FCM token for user: ${currentUser.uid}")
            notificationRepository.refreshFcmToken()
                .onSuccess {
                    Log.d("Fridgy_FCM", "FCM token initialized successfully")
                }
                .onFailure { error ->
                    Log.e("Fridgy_FCM", "Failed to initialize FCM token: ${error.message}", error)
                }
        }
    }

    /**
     * Handle new intents when the activity is already running.
     * This is needed for magic link handling when app is in singleTask launch mode.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("Fridgy_MagicLink", "onNewIntent received")
        handleMagicLinkIntent(intent)
        handleInviteDeepLink(intent)
    }

    /**
     * Check if the intent contains a magic link and pass it to the handler.
     * Handles both direct Firebase links and redirects from our web page via fridgy:// scheme.
     */
    private fun handleMagicLinkIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null) {
            Log.d("Fridgy_MagicLink", "Checking intent data: $data")

            // Check if it's our custom scheme redirect from the web page
            // The web page redirects: fridgy://auth?apiKey=...&oobCode=...&mode=signIn
            if (data.scheme == "fridgy" && data.host == "auth") {
                Log.d("Fridgy_MagicLink", "Custom scheme magic link detected")

                // Reconstruct the original Firebase link format for verification
                val originalLink = "https://fridgyapp.com/auth${data.query?.let { "?$it" } ?: ""}"
                Log.d("Fridgy_MagicLink", "Reconstructed link: $originalLink")

                val auth = FirebaseAuth.getInstance()
                if (auth.isSignInWithEmailLink(originalLink)) {
                    Log.d("Fridgy_MagicLink", "Valid magic link, passing to handler")
                    // Create a new intent with the reconstructed URL
                    val reconstructedIntent =
                        Intent(intent).apply {
                            setData(android.net.Uri.parse(originalLink))
                        }
                    magicLinkHandler.handleIntent(reconstructedIntent)
                } else {
                    Log.w("Fridgy_MagicLink", "Link failed Firebase validation")
                }
                return
            }

            // Also handle direct https links (in case user opens link directly)
            val auth = FirebaseAuth.getInstance()
            if (auth.isSignInWithEmailLink(data.toString())) {
                Log.d("Fridgy_MagicLink", "Direct HTTPS magic link detected, passing to handler")
                magicLinkHandler.handleIntent(intent)
            }
        }
    }

    /**
     * Handle household invite deep links from web URLs.
     * Extracts invite code from URL query parameter and stores it for navigation.
     * Supports both custom scheme (fridgy://invite?code=ABC) and https (https://fridgyapp.com/invite?code=ABC)
     */
    private fun handleInviteDeepLink(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "fridgy" && uri.host == "invite") {
                val inviteCode = uri.getQueryParameter("code")
                if (inviteCode != null) {
                    Log.d("Fridgy_DeepLink", "Handling invite code: $inviteCode")
                    // Store the invite code in shared preferences to auto-fill in join dialog
                    getSharedPreferences("fridgy_prefs", MODE_PRIVATE)
                        .edit()
                        .putString("pending_invite_code", inviteCode)
                        .apply()
                    // Trigger the LaunchedEffect to handle navigation
                    inviteIntentTrigger.value = System.currentTimeMillis()
                } else {
                    Log.w("Fridgy_DeepLink", "Invite link missing code parameter: $uri")
                }
            }
        }
    }
}
