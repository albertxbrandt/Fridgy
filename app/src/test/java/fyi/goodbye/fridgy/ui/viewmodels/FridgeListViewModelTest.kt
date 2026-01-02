package fyi.goodbye.fridgy.ui.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.util.TestUtil
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [FridgeListViewModel].
 *
 * Tests cover fridge list loading, invitation handling, and fridge creation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FridgeListViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockApplication: android.app.Application
    private lateinit var mockRepository: FridgeRepository
    private lateinit var viewModel: FridgeListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockAuth = TestUtil.createMockFirebaseAuth()
        mockFirestore = TestUtil.createMockFirestore()
        mockApplication = TestUtil.createMockApplication()
        mockRepository = mockk<FridgeRepository>(relaxed = true)

        TestUtil.mockFirebaseAuthInstance(mockAuth)
        TestUtil.mockFirestoreInstance(mockFirestore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state is loading when user is authenticated`() =
        runTest {
            // Arrange
            every { mockRepository.getFridgesForCurrentUser() } returns flowOf(emptyList())
            every { mockRepository.getInvitesForCurrentUser() } returns flowOf(emptyList())

            // Act
            viewModel = FridgeListViewModel(mockApplication, mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            // Assert
            viewModel.fridgesUiState.test {
                val state = awaitItem()
                assertTrue(state is FridgeListViewModel.FridgeUiState.Success)
                assertEquals(0, (state as FridgeListViewModel.FridgeUiState.Success).fridges.size)
            }
        }

    @Test
    fun `shows error state when user is not logged in`() =
        runTest {
            // Arrange
            val mockAuthNoUser = TestUtil.createMockFirebaseAuthNoUser()
            TestUtil.mockFirebaseAuthInstance(mockAuthNoUser)

            // Act
            viewModel = FridgeListViewModel(mockApplication, mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            // Assert
            viewModel.fridgesUiState.test {
                val state = awaitItem()
                assertTrue(state is FridgeListViewModel.FridgeUiState.Error)
                assertTrue((state as FridgeListViewModel.FridgeUiState.Error).message.contains("not logged in"))
            }
        }

    @Test
    fun `createNewFridge sets loading state and calls repository`() =
        runTest {
            // Arrange
            every { mockRepository.getFridgesForCurrentUser() } returns flowOf(emptyList())
            every { mockRepository.getInvitesForCurrentUser() } returns flowOf(emptyList())
            coEvery { mockRepository.createFridge(any()) } returns mockk(relaxed = true)

            viewModel = FridgeListViewModel(mockApplication, mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            // Act
            viewModel.createNewFridge("Test Fridge")
            testDispatcher.scheduler.advanceUntilIdle()

            // Assert
            assertFalse(viewModel.isCreatingFridge.value)
            coVerify { mockRepository.createFridge("Test Fridge") }
        }

    @Test
    fun `acceptInvite calls repository acceptInvite`() =
        runTest {
            // Arrange
            every { mockRepository.getFridgesForCurrentUser() } returns flowOf(emptyList())
            every { mockRepository.getInvitesForCurrentUser() } returns flowOf(emptyList())
            coEvery { mockRepository.acceptInvite(any()) } just Runs

            viewModel = FridgeListViewModel(mockApplication, mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            // Act
            viewModel.acceptInvite("fridge-123")
            testDispatcher.scheduler.advanceUntilIdle()

            // Assert
            coVerify { mockRepository.acceptInvite("fridge-123") }
        }

    @Test
    fun `declineInvite calls repository declineInvite`() =
        runTest {
            // Arrange
            every { mockRepository.getFridgesForCurrentUser() } returns flowOf(emptyList())
            every { mockRepository.getInvitesForCurrentUser() } returns flowOf(emptyList())
            coEvery { mockRepository.declineInvite(any()) } just Runs

            viewModel = FridgeListViewModel(mockApplication, mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            // Act
            viewModel.declineInvite("fridge-456")
            testDispatcher.scheduler.advanceUntilIdle()

            // Assert
            coVerify { mockRepository.declineInvite("fridge-456") }
        }
}
