# Fridgy - AI Coding Instructions

## Project Overview
Fridgy is a family-oriented fridge inventory management Android app built with **Jetpack Compose**, **Firebase**, **Kotlin**, and **Hilt** for dependency injection. Users create shared "fridges", add items via barcode scanning, and maintain real-time shopping lists. The app emphasizes real-time collaboration using Firestore's snapshot listeners.

**Package:** `fyi.goodbye.fridgy`  
**Min SDK:** 25 | **Target SDK:** 36 | **JVM Target:** 11

## Architecture

### MVVM Pattern with Hilt DI (Strictly Enforced)
The app follows **Model-View-ViewModel** architecture with **Hilt** for dependency injection. Never bypass this pattern:

- **Models** (`models/`): Plain data classes representing domain entities. Use Firestore annotations (`@DocumentId`) for Firebase integration.
- **Feature Modules** (`ui/<feature>/`):
  - Screen composables and their ViewModels live together in the same package
  - Example: `ui/magiclink/MagicLinkScreen.kt` + `ui/magiclink/MagicLinkViewModel.kt`
  - ViewModels annotated with `@HiltViewModel` and use `@Inject constructor()`
  - Feature-specific components in `ui/<feature>/components/`
- **Shared Components** (`ui/shared/components/`): Reusable UI components used across multiple features (e.g., `SquaredButton`, `SquaredInput`)
- **ViewModels**: Business logic layer. Manage UI state using `StateFlow`/`MutableStateFlow`. All expose sealed `UiState` interfaces with `Loading`, `Success`, and `Error` states. Dependencies injected via Hilt.
- **Repositories** (`repositories/`): Data layer abstraction. Handle Firestore and Storage operations. Use Kotlin Flows for real-time updates. Provided via Hilt modules.
  - `FridgeRepository`: Manages fridges, items, and invitations with caching strategy
  - `ProductRepository`: Crowdsourced product database with local cache

**Critical Rules**: 
- Views never access repositories directly
- ViewModels never reference Android framework components (Context, Activity, etc.) directly - inject Application context if needed
- All ViewModels must use `@HiltViewModel` annotation
- All repositories and dependencies must be provided through Hilt modules

### Navigation
Single `MainActivity` with `NavHost`. Routes are string-based with arguments:
- `magicLink` → `fridgeList` → `fridgeInventory/{fridgeId}` → `itemDetail/{fridgeId}/{itemId}`
- Barcode scanner: `barcodeScanner/{fridgeId}` → returns result via `savedStateHandle`
- Settings: `fridgeSettings/{fridgeId}`
- **Deep links**: `fridgy://auth` opens app from magic link email

Pass data between screens using `navController.currentBackStackEntry?.savedStateHandle`.

## Firebase Integration

### Authentication & App Check
- **Passwordless authentication** via magic links sent through email (no passwords)
- **Email service**: Resend (3,000 emails/month free tier)
  - Domain: fridgyapp.com (verified)
  - Sender: noreply@fridgyapp.com
- **Cloud Functions**: `sendMagicLink` function handles email sending
  - Uses Firebase Admin SDK to generate magic links
  - Custom branded HTML email template
  - Masks Firebase domain typo using redirect page
- **Secrets**: RESEND_API_KEY stored in Firebase Secret Manager
- App Check initialized in `MainActivity.onCreate()`:
  - **Debug builds**: `DebugAppCheckProviderFactory` (check logcat for debug token)
  - **Production**: `PlayIntegrityAppCheckProviderFactory`

### Firestore Collections
```
users/
  {uid}/
    createdAt: number
    email: String
    username: String

products/  // Global crowdsourced database
  {upc}/
    brand: String
    category: String
    imageUrl: String (Firebase Storage path)
    lastUpdated: number
    name: String

fridges/
  {fridgeId}/
    createdAt: number
    createdBy: String (uid)
    name: String
    members: Map<String, String>  // {uid}: username
    pendingInvites: Map<String, String>  // {uid}: username
    items/  // subcollection
      {upc}/
        addedAt: number
        addedBy: String (uid)
        lastUpdatedAt: number
        lastUpdatedBy: String (uid)
        quantity: number
        upc: String
```

**Querying fridges**: Use `whereNotEqualTo("members.$currentUserId", null)` to find user's fridges.

### Firebase Storage
Product images stored at `products/{upc}.jpg`. Upload uses `putFile()`, access via signed URLs with `getDownloadUrl()`.

## UI Patterns

### Compose Structure
- **Feature Screens** (`ui/<feature>/`):
  - Screen composable: `ui/<feature>/<Feature>Screen.kt`
  - ViewModel: `ui/<feature>/<Feature>ViewModel.kt`
  - Feature components: `ui/<feature>/components/` (only used by this feature)
- **Shared Components** (`ui/shared/components/`): Reusable components like `SquaredButton`, `SquaredInput`, `FridgeCard`, `InventoryItemCard`
- **Theme** (`ui/theme/`): Custom color scheme with `FridgyPrimary`, `FridgyWhite`, `FridgyDarkBlue` (see `Color.kt`)

### Custom Components
- **SquaredButton**: Zero-radius buttons (`RoundedCornerShape(0.dp)`) for brand consistency
- **SquaredInput**: Matching text fields with consistent styling

### State Management
```kotlin
sealed interface MyUiState {
    data object Loading : MyUiState
    data class Success(val data: MyData) : MyUiState
    data class Error(val message: String) : MyUiState
}

private val _uiState = MutableStateFlow<MyUiState>(Loading)
val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()
```

Use `collectAsState()` in composables to observe state changes.

### Performance Optimizations
- **Use `derivedStateOf`** for computed values to avoid unnecessary recompositions (see `FridgeInventoryScreen`)
- **Repository caching**: Both `FridgeRepository` and `ProductRepository` implement in-memory caching to minimize Firestore reads
- **Firestore Source.CACHE**: Use `get(Source.CACHE)` when appropriate to read from local cache before hitting network
- **Close `ImageProxy`** in camera analyzer's `addOnCompleteListener` (not `addOnSuccessListener`) to prevent frame processing backlog
- **LazyColumn/LazyVerticalGrid**: Always use `key` parameter for item identity to optimize recomposition
- **Use `remember { }` and `rememberSaveable { }`** for expensive objects (DateFormatter, Camera executors, etc.)
- **Avoid recomposition triggers**: Wrap stable callbacks in `remember { }`, use `derivedStateOf` for derived state
- **Minimize Flow emissions**: Use `distinctUntilChanged()` on StateFlows when appropriate
- **Image loading**: Coil handles caching automatically; use `AsyncImage` with proper placeholders

### Additional Performance Guidelines
- **Debounce user input**: Use `debounce()` operator on search/filter flows
- **Pagination**: Implement Firestore query limits and "load more" for large collections
- **Background thread work**: Keep coroutines on `Dispatchers.IO` for database/network operations
- **Avoid nested LazyColumns**: Use single scrollable container with mixed item types instead
- **Profile with Android Studio**: Use Layout Inspector and Profiler to identify bottlenecks

## Key Features

### Barcode Scanning
- Uses CameraX + ML Kit (`com.google.mlkit:barcode-scanning`)
- Formats: `UPC_A`, `EAN_13`
- **Critical**: Always call `imageProxy.close()` in `addOnCompleteListener` to allow next frame processing
- Scanner screen returns scanned UPC via `savedStateHandle` to calling screen

### Real-time Collaboration
- Fridges use Firestore snapshot listeners via `callbackFlow`
- Invitations stored in `pendingInvites` map on fridge document
- Members see live updates when items are added/removed by others

### Image Loading
- Coil for async image loading (`coil-compose`)
- Firebase Storage URLs loaded directly into `AsyncImage` composables

## Development Workflow

### Building & Running
```bash
# Build debug APK
./gradlew assembleDebug

# Install and run on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests "fyi.goodbye.fridgy.viewmodels.LoginViewModelTest"

# Generate test coverage report
./gradlew testDebugUnitTestCoverage
```

### CI/CD Best Practices
```yaml
# Recommended GitHub Actions workflow structure:
# .github/workflows/android-ci.yml

# On every push/PR:
- Lint check (./gradlew lint)
- Unit tests (./gradlew test)
- Build debug APK (./gradlew assembleDebug)
- Run instrumented tests on Firebase Test Lab or GitHub Actions emulator

# On release branch:
- Build release APK/AAB with signing
- Run full test suite
- Upload to Google Play (internal/beta track)
- Generate changelog from commits

# Quality gates:
- All tests must pass
- Lint errors must be resolved (warnings acceptable)
- Code coverage minimum threshold (recommend 70%+)
```

### Dependency Management
Uses **Version Catalog** (`gradle/libs.versions.toml`). Reference dependencies as:
```kotlin
implementation(libs.androidx.core.ktx)
implementation(libs.firebase.firestore.ktx)
```

## Testing Strategy

### Unit Tests (`app/src/test/`)
Test ViewModels and Repositories in isolation without Android dependencies.

**Required dependencies** (add to `app/build.gradle.kts`):
```kotlin
testImplementation("junit:junit:4.13.2")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
testImplementation("app.cash.turbine:turbine:1.0.0") // For testing Flows
testImplementation("io.mockk:mockk:1.13.8") // For mocking Firebase/dependencies
testImplementation("androidx.arch.core:core-testing:2.2.0") // For InstantTaskExecutorRule
```

**Example ViewModel test structure**:
```kotlin
// app/src/test/java/fyi/goodbye/fridgy/viewmodels/LoginViewModelTest.kt
class LoginViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var viewModel: LoginViewModel
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Mock Firebase Auth if needed
        viewModel = LoginViewModel()
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `login with empty email shows error`() = runTest {
        viewModel.email.value = ""
        viewModel.password.value = "password123"
        viewModel.login()
        
        assertEquals("Email cannot be empty", viewModel.errorMessage.value)
    }
    
    @Test
    fun `successful login emits success state`() = runTest {
        // Use Turbine to test Flow emissions
        viewModel.loginSuccess.test {
            viewModel.performLogin("test@example.com", "password123")
            assertTrue(awaitItem())
        }
    }
}
```

**Example Repository test**:
```kotlin
// app/src/test/java/fyi/goodbye/fridgy/repositories/FridgeRepositoryTest.kt
class FridgeRepositoryTest {
    private lateinit var repository: FridgeRepository
    private val mockFirestore = mockk<FirebaseFirestore>()
    
    @Test
    fun `getFridgeById returns cached fridge when available`() = runTest {
        // Test caching logic
        val fridgeId = "test-fridge-id"
        val result = repository.getFridgeById(fridgeId)
        assertNotNull(result)
    }
}
```

### UI Tests with Compose (`app/src/androidTest/`)
Test user interactions and UI behavior on actual/emulated devices.

**Required dependencies**:
```kotlin
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
androidTestImplementation("io.mockk:mockk-android:1.13.8")
debugImplementation("androidx.compose.ui:ui-test-manifest")
```

**Example Compose UI test**:
```kotlin
// app/src/androidTest/java/fyi/goodbye/fridgy/ui/LoginScreenTest.kt
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun loginButton_isDisabledWhenFieldsAreEmpty() {
        composeTestRule.setContent {
            FridgyTheme {
                LoginScreen(
                    onLoginSuccess = {},
                    onNavigateToSignup = {}
                )
            }
        }
        
        // Check initial state
        composeTestRule.onNodeWithText("Login").assertIsNotEnabled()
    }
    
    @Test
    fun enteringCredentials_enablesLoginButton() {
        composeTestRule.setContent {
            FridgyTheme {
                LoginScreen(
                    onLoginSuccess = {},
                    onNavigateToSignup = {}
                )
            }
        }
        
        // Enter email
        composeTestRule.onNodeWithTag("emailInput")
            .performTextInput("test@example.com")
        
        // Enter password
        composeTestRule.onNodeWithTag("passwordInput")
            .performTextInput("password123")
        
        // Verify button is enabled
        composeTestRule.onNodeWithText("Login").assertIsEnabled()
    }
    
    @Test
    fun clickingSignupLink_triggersNavigation() {
        var navigationTriggered = false
        
        composeTestRule.setContent {
            FridgyTheme {
                LoginScreen(
                    onLoginSuccess = {},
                    onNavigateToSignup = { navigationTriggered = true }
                )
            }
        }
        
        composeTestRule.onNodeWithText("Don't have an account? Sign Up")
            .performClick()
        
        assertTrue(navigationTriggered)
    }
}
```

**Testing custom composables**:
```kotlin
// app/src/androidTest/java/fyi/goodbye/fridgy/ui/elements/SquaredButtonTest.kt
class SquaredButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun squaredButton_hasZeroCornerRadius() {
        composeTestRule.setContent {
            FridgyTheme {
                SquaredButton(onClick = {}) {
                    Text("Test Button")
                }
            }
        }
        
        composeTestRule.onNodeWithText("Test Button").assertExists()
    }
}
```

### Testing Firebase Integration
**Mock Firebase in tests** to avoid network calls:
```kotlin
// Use MockK to mock Firebase services
val mockAuth = mockk<FirebaseAuth>()
val mockFirestore = mockk<FirebaseFirestore>()

every { mockAuth.currentUser?.uid } returns "test-user-id"
every { mockFirestore.collection(any()) } returns mockk()
```

**For integration tests**, use **Firebase Local Emulator Suite**:
```bash
# Install Firebase tools
npm install -g firebase-tools

# Start emulators for testing
firebase emulators:start --only auth,firestore,storage

# Configure app to use emulators in test configuration
# Add to test setup:
FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
```

### Test Coverage Goals
- **ViewModels**: 80%+ coverage (all state transitions, error handling)
- **Repositories**: 70%+ coverage (focus on data transformation logic)
- **UI Components**: Test user interactions, not implementation details
- **Navigation**: Test all route transitions and argument passing

### Testing Best Practices
1. **Arrange-Act-Assert pattern**: Structure all tests consistently
2. **One assertion per test**: Keep tests focused and readable
3. **Use test tags**: Add `Modifier.testTag("tagName")` to composables for reliable selection
4. **Test user behavior, not implementation**: Avoid testing internal state
5. **Run tests before every commit**: Integrate with Git hooks or CI/CD
6. **Mock external dependencies**: Never rely on real Firebase in unit tests

### Debugging Firebase
- **App Check debug token**: Check logcat filter `Fridgy_AppCheck` on first launch
- **Authentication**: Use Firebase Console to view/manage test users
- **Firestore rules**: Managed separately in Firebase Console (not in app code)

## Common Patterns

### Creating a new screen
1. Create feature package: `ui/<feature>/`
2. Create `@Composable` function: `ui/<feature>/<Feature>Screen.kt`
3. Create `@HiltViewModel` class: `ui/<feature>/<Feature>ViewModel.kt` with `@Inject constructor()`
4. Create feature components (if needed): `ui/<feature>/components/`
5. Add navigation route in `MainActivity.NavHost`
6. Use `hiltViewModel()` to get ViewModel instance in composable

### Adding Firestore operations
1. Add method to appropriate repository (or create new one)
2. Use `suspend` functions for one-time queries
3. Use `Flow<T>` with `callbackFlow` for real-time listeners
4. Handle exceptions and return null or throw as appropriate

### Working with Firebase Auth
```kotlin
val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
// Always null-check - user might not be authenticated
```

## Code Style
- **String Resources**: ALL user-facing strings MUST be in `strings.xml` - never hardcode strings in Kotlin/Compose code
  - Use `stringResource(R.string.key_name)` in Composables
  - Use `context.getString(R.string.key_name)` in ViewModels/non-Compose code
  - Exception: Navigation routes, API keys, technical constants
- **Component Decomposition**: Break down screens into smaller components to keep file sizes manageable
  - Extract reusable UI sections into separate `@Composable` functions
  - Place feature-specific components in `ui/<feature>/components/`
  - Move shared components to `ui/shared/components/`
  - Aim for screen files under 500 lines when possible
- Use explicit `StateFlow` types for public properties
- Prefix private StateFlows with `_` (e.g., `_uiState`)
- Document all data classes with KDoc, especially Firestore models
- Use `remember { }` for expensive computations or objects that shouldn't recreate on recomposition
- Prefer Material3 components over Material2

## Testing Notes
- Ensure Firebase emulators or test project for integration testing
- Test both authenticated and unauthenticated states
- Verify barcode scanner cleanup (executor shutdown, scanner close)
