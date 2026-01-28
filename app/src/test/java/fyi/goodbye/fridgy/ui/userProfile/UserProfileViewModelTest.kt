package fyi.goodbye.fridgy.ui.userProfile

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import fyi.goodbye.fridgy.models.entities.UserProfile
import fyi.goodbye.fridgy.repositories.UserRepository
import fyi.goodbye.fridgy.ui.shared.UiState
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockUserRepository: UserRepository
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var viewModel: UserProfileViewModel

    private val testUserId = "test-user-123"
    private val testEmail = "test@example.com"
    private val testUsername = "testuser"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockUserRepository = mockk(relaxed = true)
        mockAuth = mockk(relaxed = true)
        mockUser = mockk(relaxed = true)

        every { mockAuth.currentUser } returns mockUser
        every { mockUser.uid } returns testUserId
        every { mockUser.email } returns testEmail
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state is Loading`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(any()) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Loading)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loadUserProfile updates state to Success with user data`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Success)
                assertEquals(testUsername, (state as UiState.Success).data.username)
                assertEquals(testEmail, state.data.email)
            }
        }

    @Test
    fun `loadUserProfile with null user profile shows Unknown username`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns null

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Success)
                assertEquals("Unknown", (state as UiState.Success).data.username)
                assertEquals(testEmail, state.data.email)
            }
        }

    @Test
    fun `loadUserProfile with no authenticated user shows Error`() =
        runTest {
            every { mockAuth.currentUser } returns null

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("No authenticated user", (state as UiState.Error).message)
            }
        }

    @Test
    fun `loadUserProfile with no email shows Error`() =
        runTest {
            every { mockUser.email } returns null

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("No email found", (state as UiState.Error).message)
            }
        }

    @Test
    fun `updateUsername with valid username succeeds`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )
            coEvery { mockUserRepository.isUsernameTaken(any()) } returns false
            coEvery { mockUserRepository.updateUsername(any()) } returns Unit

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.updateUsername("newusername")
            advanceUntilIdle()

            viewModel.usernameUpdateState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Success)
            }

            coVerify { mockUserRepository.updateUsername("newusername") }
        }

    @Test
    fun `updateUsername with username less than 3 characters shows Error`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.updateUsername("ab")
            advanceUntilIdle()

            viewModel.usernameUpdateState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("Username must be at least 3 characters", (state as UiState.Error).message)
            }

            coVerify(exactly = 0) { mockUserRepository.updateUsername(any()) }
        }

    @Test
    fun `updateUsername with username more than 20 characters shows Error`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.updateUsername("thisusernameiswaytoolong123")
            advanceUntilIdle()

            viewModel.usernameUpdateState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("Username must be 20 characters or less", (state as UiState.Error).message)
            }

            coVerify(exactly = 0) { mockUserRepository.updateUsername(any()) }
        }

    @Test
    fun `updateUsername with invalid characters shows Error`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.updateUsername("user@name!")
            advanceUntilIdle()

            viewModel.usernameUpdateState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals(
                    "Username can only contain letters, numbers, hyphens, and underscores",
                    (state as UiState.Error).message
                )
            }

            coVerify(exactly = 0) { mockUserRepository.updateUsername(any()) }
        }

    @Test
    fun `updateUsername with same username shows Error`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.updateUsername(testUsername)
            advanceUntilIdle()

            viewModel.usernameUpdateState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("This is already your username", (state as UiState.Error).message)
            }

            coVerify(exactly = 0) { mockUserRepository.updateUsername(any()) }
        }

    @Test
    fun `updateUsername with taken username shows Error`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )
            coEvery { mockUserRepository.isUsernameTaken("takenusername") } returns true

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.updateUsername("takenusername")
            advanceUntilIdle()

            viewModel.usernameUpdateState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("Username is already taken", (state as UiState.Error).message)
            }

            coVerify(exactly = 0) { mockUserRepository.updateUsername(any()) }
        }

    @Test
    fun `updateUsername trims whitespace`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )
            coEvery { mockUserRepository.isUsernameTaken(any()) } returns false
            coEvery { mockUserRepository.updateUsername(any()) } returns Unit

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.updateUsername("  newusername  ")
            advanceUntilIdle()

            coVerify { mockUserRepository.updateUsername("newusername") }
        }

    @Test
    fun `updateUsername with repository error shows Error`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )
            coEvery { mockUserRepository.isUsernameTaken(any()) } returns false
            coEvery { mockUserRepository.updateUsername(any()) } throws Exception("Network error")

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.updateUsername("newusername")
            advanceUntilIdle()

            viewModel.usernameUpdateState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("Network error", (state as UiState.Error).message)
            }
        }

    @Test
    fun `deleteAccount succeeds`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )
            coEvery { mockUserRepository.deleteAccount() } returns Unit

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.deleteAccount()
            advanceUntilIdle()

            viewModel.accountDeletionState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Success)
            }

            coVerify { mockUserRepository.deleteAccount() }
        }

    @Test
    fun `deleteAccount with repository error shows Error`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )
            coEvery { mockUserRepository.deleteAccount() } throws Exception("Deletion failed")

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.deleteAccount()
            advanceUntilIdle()

            viewModel.accountDeletionState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("Deletion failed", (state as UiState.Error).message)
            }
        }

    @Test
    fun `resetUsernameUpdateState resets state to Idle`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            // Set state to Error first
            viewModel.updateUsername("ab")
            advanceUntilIdle()

            // Reset it
            viewModel.resetUsernameUpdateState()

            viewModel.usernameUpdateState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Idle)
            }
        }

    @Test
    fun `resetAccountDeletionState resets state to Idle`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )
            coEvery { mockUserRepository.deleteAccount() } throws Exception("Error")

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            // Set state to Error first
            viewModel.deleteAccount()
            advanceUntilIdle()

            // Reset it
            viewModel.resetAccountDeletionState()

            viewModel.accountDeletionState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Idle)
            }
        }

    @Test
    fun `updateUsername shows Loading state during operation`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )
            coEvery { mockUserRepository.isUsernameTaken(any()) } returns false
            coEvery { mockUserRepository.updateUsername(any()) } returns Unit

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.usernameUpdateState.test {
                val initialState = awaitItem()
                assertTrue(initialState is UiState.Idle)

                viewModel.updateUsername("newusername")

                val loadingState = awaitItem()
                assertTrue(loadingState is UiState.Loading)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteAccount shows Loading state during operation`() =
        runTest {
            coEvery { mockUserRepository.getUserProfile(testUserId) } returns
                UserProfile(
                    uid = testUserId,
                    username = testUsername,
                )
            coEvery { mockUserRepository.deleteAccount() } returns Unit

            viewModel = UserProfileViewModel(mockUserRepository, mockAuth)
            advanceUntilIdle()

            viewModel.accountDeletionState.test {
                val initialState = awaitItem()
                assertTrue(initialState is UiState.Idle)

                viewModel.deleteAccount()

                val loadingState = awaitItem()
                assertTrue(loadingState is UiState.Loading)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
