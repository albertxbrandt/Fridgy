package fyi.goodbye.fridgy.ui.fridgeSettings

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.Fridge
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
class FridgeSettingsViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockContext: Context
    private lateinit var mockFridgeRepository: FridgeRepository
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: FridgeSettingsViewModel

    private val testFridgeId = "fridge-123"
    private val testHouseholdId = "household-456"
    private val testUserId = "user-789"

    private val testDisplayFridge =
        DisplayFridge(
            id = testFridgeId,
            name = "Main Fridge",
            type = "fridge",
            householdId = testHouseholdId,
            createdByUid = testUserId,
            creatorDisplayName = "Test User",
            createdAt = System.currentTimeMillis()
        )

    private val testRawFridge =
        Fridge(
            id = testFridgeId,
            name = "Main Fridge",
            type = "fridge",
            householdId = testHouseholdId,
            createdBy = testUserId,
            createdAt = System.currentTimeMillis()
        )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)        mockContext = mockk(relaxed = true)
        mockFridgeRepository = mockk(relaxed = true)
        mockAuth = mockk(relaxed = true)
        mockUser = mockk(relaxed = true)

        every { mockAuth.currentUser } returns mockUser
        every { mockUser.uid } returns testUserId
        every { mockContext.getString(R.string.error_fridge_not_found) } returns "Fridge not found"
        every { mockContext.getString(R.string.error_failed_to_load_fridge) } returns "Failed to load fridge"

        // Default repository behaviors
        coEvery { mockFridgeRepository.getFridgeById(any(), any()) } returns testDisplayFridge
        coEvery { mockFridgeRepository.getRawFridgeById(any()) } returns testRawFridge

        savedStateHandle =
            SavedStateHandle().apply {
                set("fridgeId", testFridgeId)
            }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state is Loading`() =
        runTest {
            viewModel =
                FridgeSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockAuth
                )

            assertTrue(viewModel.uiState.value is UiState.Loading)
        }

    @Test
    fun `currentUserId returns authenticated user id`() =
        runTest {
            viewModel =
                FridgeSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockAuth
                )

            assertEquals(testUserId, viewModel.currentUserId)
        }

    @Test
    fun `currentUserId returns null when not authenticated`() =
        runTest {
            every { mockAuth.currentUser } returns null

            viewModel =
                FridgeSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockAuth
                )

            assertNull(viewModel.currentUserId)
        }

    @Test
    fun `loadFridgeDetails emits Success with fridge data`() =
        runTest {
            viewModel =
                FridgeSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockAuth
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Success)
                val data = (state as UiState.Success).data
                assertEquals("Main Fridge", data.fridge.name)
                assertEquals(testFridgeId, data.fridgeData.id)
            }
        }

    @Test
    fun `loadFridgeDetails emits Error when displayFridge not found`() =
        runTest {
            coEvery { mockFridgeRepository.getFridgeById(testFridgeId, any()) } returns null

            viewModel =
                FridgeSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockAuth
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("Fridge not found", (state as UiState.Error).message)
            }
        }

    @Test
    fun `loadFridgeDetails emits Error when rawFridge not found`() =
        runTest {
            coEvery { mockFridgeRepository.getRawFridgeById(testFridgeId) } returns null

            viewModel =
                FridgeSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockAuth
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("Fridge not found", (state as UiState.Error).message)
            }
        }

    @Test
    fun `loadFridgeDetails handles repository exception`() =
        runTest {
            coEvery { mockFridgeRepository.getFridgeById(testFridgeId, any()) } throws Exception("Network error")

            viewModel =
                FridgeSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockAuth
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertTrue((state as UiState.Error).message.contains("Network error"))
            }
        }

    @Test
    fun `deleteFridge calls repository and invokes callback`() =
        runTest {
            coEvery { mockFridgeRepository.deleteFridge(any()) } returns Unit
            var callbackInvoked = false

            viewModel =
                FridgeSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockAuth
                )

            viewModel.deleteFridge { callbackInvoked = true }
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockFridgeRepository.deleteFridge(testFridgeId) }
            assertTrue(callbackInvoked)
        }

    @Test
    fun `deleteFridge sets isDeleting during operation`() =
        runTest {
            coEvery { mockFridgeRepository.deleteFridge(any()) } returns Unit

            viewModel =
                FridgeSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockAuth
                )

            assertFalse(viewModel.isDeleting.value)

            viewModel.deleteFridge {}
            testDispatcher.scheduler.advanceUntilIdle()

            // Should be false after completion
            assertFalse(viewModel.isDeleting.value)
        }

    @Test
    fun `deleteFridge handles repository exception and sets error`() =
        runTest {
            coEvery { mockFridgeRepository.deleteFridge(any()) } throws Exception("Permission denied")
            var callbackInvoked = false

            viewModel =
                FridgeSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockAuth
                )

            viewModel.deleteFridge { callbackInvoked = true }
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.actionError.test {
                val error = awaitItem()
                assertEquals("Permission denied", error)
            }
            assertFalse(callbackInvoked)
        }

    @Test
    fun `deleteFridge clears previous error`() =
        runTest {
            coEvery { mockFridgeRepository.deleteFridge(any()) } returns Unit

            viewModel =
                FridgeSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockAuth
                )

            viewModel.deleteFridge {}
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.actionError.test {
                val error = awaitItem()
                assertNull(error)
            }
        }

    @Test
    fun `isDeleting is false initially`() =
        runTest {
            viewModel =
                FridgeSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockAuth
                )

            viewModel.isDeleting.test {
                val isDeleting = awaitItem()
                assertFalse(isDeleting)
            }
        }

    @Test
    fun `actionError is null initially`() =
        runTest {
            viewModel =
                FridgeSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockAuth
                )

            viewModel.actionError.test {
                val error = awaitItem()
                assertNull(error)
            }
        }
}

