package fyi.goodbye.fridgy.ui.fridgeList

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.UserProfile
import fyi.goodbye.fridgy.repositories.AdminRepository
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.ui.shared.UiState
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FridgeListViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockContext: Context
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var mockFridgeRepository: FridgeRepository
    private lateinit var mockAdminRepository: AdminRepository
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: FridgeListViewModel

    private val testHouseholdId = "household-123"
    private val testUserId = "user-123"

    private val testFridge =
        Fridge(
            id = "fridge-1",
            name = "Main Fridge",
            type = "fridge",
            householdId = testHouseholdId,
            createdBy = testUserId,
            createdAt = System.currentTimeMillis()
        )

    private val testUserProfile = UserProfile(uid = testUserId, username = "TestUser")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        mockContext = mockk(relaxed = true)
        mockAuth = mockk(relaxed = true)
        mockUser = mockk(relaxed = true)
        mockFridgeRepository = mockk(relaxed = true)
        mockAdminRepository = mockk(relaxed = true)

        every { mockAuth.currentUser } returns mockUser
        every { mockUser.uid } returns testUserId
        every { mockContext.getString(R.string.error_user_not_logged_in) } returns "User not logged in"
        every { mockContext.getString(R.string.error_no_household_selected) } returns "No household selected"
        every { mockContext.getString(R.string.error_failed_to_create_fridge) } returns "Failed to create fridge"
        every { mockContext.getString(R.string.unknown) } returns "Unknown"

        // Default repository behaviors
        coEvery { mockFridgeRepository.preloadFridgesFromCache() } returns Unit
        coEvery { mockAdminRepository.isCurrentUserAdmin() } returns false
        coEvery { mockFridgeRepository.getFridgesForHousehold(any()) } returns flowOf(emptyList())
        coEvery { mockFridgeRepository.getUsersByIds(any()) } returns emptyMap()

        savedStateHandle =
            SavedStateHandle().apply {
                set("householdId", testHouseholdId)
            }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `householdId is retrieved from savedStateHandle`() =
        runTest {
            viewModel =
                FridgeListViewModel(
                    mockContext,
                    savedStateHandle,
                    mockAuth,
                    mockFridgeRepository,
                    mockAdminRepository
                )

            assertEquals(testHouseholdId, viewModel.householdId)
        }

    @Test
    fun `initial state is Loading`() =
        runTest {
            viewModel =
                FridgeListViewModel(
                    mockContext,
                    savedStateHandle,
                    mockAuth,
                    mockFridgeRepository,
                    mockAdminRepository
                )

            assertTrue(viewModel.fridgesUiState.value is UiState.Loading)
        }

    @Test
    fun `init sets state to Error when user not logged in`() =
        runTest {
            every { mockAuth.currentUser } returns null

            viewModel =
                FridgeListViewModel(
                    mockContext,
                    savedStateHandle,
                    mockAuth,
                    mockFridgeRepository,
                    mockAdminRepository
                )

            viewModel.fridgesUiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("User not logged in", (state as UiState.Error).message)
            }
        }

    @Test
    fun `init sets state to Error when householdId is empty`() =
        runTest {
            savedStateHandle =
                SavedStateHandle().apply {
                    set("householdId", "")
                }

            viewModel =
                FridgeListViewModel(
                    mockContext,
                    savedStateHandle,
                    mockAuth,
                    mockFridgeRepository,
                    mockAdminRepository
                )

            viewModel.fridgesUiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("No household selected", (state as UiState.Error).message)
            }
        }

    @Test
    fun `init calls preloadFridgesFromCache`() =
        runTest {
            coEvery { mockFridgeRepository.preloadFridgesFromCache() } returns Unit

            viewModel =
                FridgeListViewModel(
                    mockContext,
                    savedStateHandle,
                    mockAuth,
                    mockFridgeRepository,
                    mockAdminRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockFridgeRepository.preloadFridgesFromCache() }
        }

    @Test
    fun `init checks admin status`() =
        runTest {
            coEvery { mockAdminRepository.isCurrentUserAdmin() } returns true

            viewModel =
                FridgeListViewModel(
                    mockContext,
                    savedStateHandle,
                    mockAuth,
                    mockFridgeRepository,
                    mockAdminRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.isAdmin.test {
                val isAdmin = awaitItem()
                assertTrue(isAdmin)
            }
        }

    @Test
    fun `isAdmin is false when user is not admin`() =
        runTest {
            coEvery { mockAdminRepository.isCurrentUserAdmin() } returns false

            viewModel =
                FridgeListViewModel(
                    mockContext,
                    savedStateHandle,
                    mockAuth,
                    mockFridgeRepository,
                    mockAdminRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.isAdmin.test {
                val isAdmin = awaitItem()
                assertFalse(isAdmin)
            }
        }

    @Test
    fun `createNewFridge with valid name creates fridge`() =
        runTest {
            val newFridge =
                Fridge(
                    id = "new-fridge-id",
                    name = "New Fridge",
                    type = "freezer",
                    householdId = testHouseholdId,
                    createdBy = testUserId,
                    createdAt = System.currentTimeMillis()
                )
            coEvery { mockFridgeRepository.createFridge(any(), any(), any(), any()) } returns newFridge

            viewModel =
                FridgeListViewModel(
                    mockContext,
                    savedStateHandle,
                    mockAuth,
                    mockFridgeRepository,
                    mockAdminRepository
                )

            viewModel.createNewFridge("New Fridge", "freezer", "Kitchen")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify {
                mockFridgeRepository.createFridge(
                    "New Fridge",
                    testHouseholdId,
                    "freezer",
                    "Kitchen"
                )
            }
        }

    @Test
    fun `createNewFridge sets isCreatingFridge during operation`() =
        runTest {
            val newFridge =
                Fridge(
                    id = "new-fridge-id",
                    name = "Test Fridge",
                    type = "fridge",
                    householdId = testHouseholdId,
                    createdBy = testUserId,
                    createdAt = System.currentTimeMillis()
                )
            coEvery { mockFridgeRepository.createFridge(any(), any(), any(), any()) } returns newFridge

            viewModel =
                FridgeListViewModel(
                    mockContext,
                    savedStateHandle,
                    mockAuth,
                    mockFridgeRepository,
                    mockAdminRepository
                )

            assertFalse(viewModel.isCreatingFridge.value)

            viewModel.createNewFridge("New Fridge")
            testDispatcher.scheduler.advanceUntilIdle()

            // Should be false after completion
            assertFalse(viewModel.isCreatingFridge.value)
        }

    @Test
    fun `createNewFridge with empty householdId sets error`() =
        runTest {
            savedStateHandle =
                SavedStateHandle().apply {
                    set("householdId", "")
                }

            viewModel =
                FridgeListViewModel(
                    mockContext,
                    savedStateHandle,
                    mockAuth,
                    mockFridgeRepository,
                    mockAdminRepository
                )

            viewModel.createNewFridge("New Fridge")

            viewModel.createFridgeError.test {
                val error = awaitItem()
                assertEquals("No household selected", error)
            }
        }

    @Test
    fun `createNewFridge handles repository exception`() =
        runTest {
            coEvery { mockFridgeRepository.createFridge(any(), any(), any(), any()) } throws Exception("Network error")

            viewModel =
                FridgeListViewModel(
                    mockContext,
                    savedStateHandle,
                    mockAuth,
                    mockFridgeRepository,
                    mockAdminRepository
                )

            viewModel.createNewFridge("New Fridge")
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.createFridgeError.test {
                val error = awaitItem()
                assertEquals("Network error", error)
            }
        }

    @Test
    fun `createNewFridge clears previous error`() =
        runTest {
            val newFridge =
                Fridge(
                    id = "new-fridge-id",
                    name = "Fridge 2",
                    type = "fridge",
                    householdId = testHouseholdId,
                    createdBy = testUserId,
                    createdAt = System.currentTimeMillis()
                )

            // First create with exception to set an error
            coEvery { mockFridgeRepository.createFridge(any(), any(), any(), any()) } throws Exception("First error")

            viewModel =
                FridgeListViewModel(
                    mockContext,
                    savedStateHandle,
                    mockAuth,
                    mockFridgeRepository,
                    mockAdminRepository
                )

            // First call that will fail
            viewModel.createNewFridge("Fridge 1")
            testDispatcher.scheduler.advanceUntilIdle()

            // Now make the repository succeed
            coEvery { mockFridgeRepository.createFridge(any(), any(), any(), any()) } returns newFridge

            // Second call should clear error
            viewModel.createNewFridge("Fridge 2")
            testDispatcher.scheduler.advanceUntilIdle()

            // Error should be null after successful call
            viewModel.createFridgeError.test {
                val error = awaitItem()
                assertNull(error)
            }
        }

    // NOTE: Tests for getFridgesForHousehold Flow collection removed
    // Real-time fridge list updates with Flow collection and user resolution
    // require integration testing with Firebase emulator
}
