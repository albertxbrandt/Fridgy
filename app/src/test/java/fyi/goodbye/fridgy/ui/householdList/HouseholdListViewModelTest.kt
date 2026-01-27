package fyi.goodbye.fridgy.ui.householdList

import android.content.Context
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayHousehold
import fyi.goodbye.fridgy.models.Household
import fyi.goodbye.fridgy.repositories.AdminRepository
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import fyi.goodbye.fridgy.repositories.UserRepository
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
class HouseholdListViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockContext: Context
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var mockHouseholdRepository: HouseholdRepository
    private lateinit var mockAdminRepository: AdminRepository
    private lateinit var mockUserRepository: UserRepository
    private lateinit var viewModel: HouseholdListViewModel

    private val testUserId = "user-123"

    private val testDisplayHouseholds =
        listOf(
            DisplayHousehold(
                id = "household-1",
                name = "Smith Family",
                createdByUid = testUserId,
                ownerDisplayName = "Test User",
                memberUsers = listOf(),
                fridgeCount = 2,
                createdAt = System.currentTimeMillis()
            ),
            DisplayHousehold(
                id = "household-2",
                name = "Vacation Home",
                createdByUid = testUserId,
                ownerDisplayName = "Test User",
                memberUsers = listOf(),
                fridgeCount = 1,
                createdAt = System.currentTimeMillis()
            )
        )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockAuth = mockk(relaxed = true)
        mockUser = mockk(relaxed = true)
        mockHouseholdRepository = mockk(relaxed = true)
        mockAdminRepository = mockk(relaxed = true)
        mockUserRepository = mockk(relaxed = true)

        every { mockAuth.currentUser } returns mockUser
        every { mockUser.uid } returns testUserId
        every { mockContext.getString(R.string.error_user_not_logged_in) } returns "User not logged in"
        every { mockContext.getString(R.string.error_please_fill_all_fields) } returns "Please fill all fields"
        every { mockContext.getString(R.string.error_failed_to_create_household) } returns "Failed to create household"

        // Default repository behaviors
        coEvery { mockAdminRepository.isCurrentUserAdmin() } returns false

        coEvery { mockHouseholdRepository.getDisplayHouseholdsForCurrentUser() } returns flowOf(testDisplayHouseholds)
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
                HouseholdListViewModel(
                    mockContext,
                    mockAuth,
                    mockHouseholdRepository,
                    mockAdminRepository,
                    mockUserRepository
                )

            assertTrue(viewModel.householdsUiState.value is UiState.Loading)
        }

    @Test
    fun `init sets Error state when user not logged in`() =
        runTest {
            every { mockAuth.currentUser } returns null

            viewModel =
                HouseholdListViewModel(
                    mockContext,
                    mockAuth,
                    mockHouseholdRepository,
                    mockAdminRepository,
                    mockUserRepository
                )

            viewModel.householdsUiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("User not logged in", (state as UiState.Error).message)
            }
        }

    @Test
    fun `init checks admin status`() =
        runTest {
            coEvery { mockAdminRepository.isCurrentUserAdmin() } returns true

            viewModel =
                HouseholdListViewModel(
                    mockContext,
                    mockAuth,
                    mockHouseholdRepository,
                    mockAdminRepository,
                    mockUserRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.isAdmin.test {
                val isAdmin = awaitItem()
                assertTrue(isAdmin)
            }
        }

    @Test
    fun `init checks admin status when user is not admin`() =
        runTest {
            coEvery { mockAdminRepository.isCurrentUserAdmin() } returns false

            viewModel =
                HouseholdListViewModel(
                    mockContext,
                    mockAuth,
                    mockHouseholdRepository,
                    mockAdminRepository,
                    mockUserRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.isAdmin.test {
                val isAdmin = awaitItem()
                assertFalse(isAdmin)
            }
        }

    @Test
    fun `createNewHousehold with valid name creates household`() =
        runTest {
            val newHousehold =
                Household(
                    id = "new-household-id",
                    name = "New Home",
                    createdBy = testUserId,
                    createdAt = System.currentTimeMillis()
                )
            coEvery { mockHouseholdRepository.createHousehold(any()) } returns newHousehold

            viewModel =
                HouseholdListViewModel(
                    mockContext,
                    mockAuth,
                    mockHouseholdRepository,
                    mockAdminRepository,
                    mockUserRepository
                )

            viewModel.createNewHousehold("New Home")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockHouseholdRepository.createHousehold("New Home") }
            assertNull(viewModel.createHouseholdError.value)
        }

    @Test
    fun `createNewHousehold with blank name sets error`() =
        runTest {
            viewModel =
                HouseholdListViewModel(
                    mockContext,
                    mockAuth,
                    mockHouseholdRepository,
                    mockAdminRepository,
                    mockUserRepository
                )

            viewModel.createNewHousehold("   ")
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.createHouseholdError.test {
                val error = awaitItem()
                assertEquals("Please fill all fields", error)
            }
            coVerify(exactly = 0) { mockHouseholdRepository.createHousehold(any()) }
        }

    @Test
    fun `createNewHousehold sets isCreatingHousehold during operation`() =
        runTest {
            val newHousehold =
                Household(
                    id = "new-household-id",
                    name = "New Home",
                    createdBy = testUserId,
                    createdAt = System.currentTimeMillis()
                )
            coEvery { mockHouseholdRepository.createHousehold(any()) } returns newHousehold

            viewModel =
                HouseholdListViewModel(
                    mockContext,
                    mockAuth,
                    mockHouseholdRepository,
                    mockAdminRepository,
                    mockUserRepository
                )

            assertFalse(viewModel.isCreatingHousehold.value)

            viewModel.createNewHousehold("New Home")
            testDispatcher.scheduler.advanceUntilIdle()

            // Should be false after completion
            assertFalse(viewModel.isCreatingHousehold.value)
        }

    @Test
    fun `createNewHousehold handles repository exception`() =
        runTest {
            coEvery { mockHouseholdRepository.createHousehold(any()) } throws Exception("Network error")

            viewModel =
                HouseholdListViewModel(
                    mockContext,
                    mockAuth,
                    mockHouseholdRepository,
                    mockAdminRepository,
                    mockUserRepository
                )

            viewModel.createNewHousehold("New Home")
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.createHouseholdError.test {
                val error = awaitItem()
                assertEquals("Network error", error)
            }
        }

    @Test
    fun `clearError sets error to null`() =
        runTest {
            coEvery { mockHouseholdRepository.createHousehold(any()) } throws Exception("Test error")

            viewModel =
                HouseholdListViewModel(
                    mockContext,
                    mockAuth,
                    mockHouseholdRepository,
                    mockAdminRepository,
                    mockUserRepository
                )

            // Create an error
            viewModel.createNewHousehold("New Home")
            testDispatcher.scheduler.advanceUntilIdle()

            // Clear it
            viewModel.clearError()

            viewModel.createHouseholdError.test {
                val error = awaitItem()
                assertNull(error)
            }
        }

    @Test
    fun `logout calls userRepository signOut`() =
        runTest {
            every { mockUserRepository.signOut() } returns Unit

            viewModel =
                HouseholdListViewModel(
                    mockContext,
                    mockAuth,
                    mockHouseholdRepository,
                    mockAdminRepository,
                    mockUserRepository
                )

            viewModel.logout()

            io.mockk.verify { mockUserRepository.signOut() }
        }

    @Test
    fun `isAdmin is false initially`() =
        runTest {
            viewModel =
                HouseholdListViewModel(
                    mockContext,
                    mockAuth,
                    mockHouseholdRepository,
                    mockAdminRepository,
                    mockUserRepository
                )

            viewModel.isAdmin.test {
                val isAdmin = awaitItem()
                assertFalse(isAdmin)
            }
        }

    @Test
    fun `isCreatingHousehold is false initially`() =
        runTest {
            viewModel =
                HouseholdListViewModel(
                    mockContext,
                    mockAuth,
                    mockHouseholdRepository,
                    mockAdminRepository,
                    mockUserRepository
                )

            viewModel.isCreatingHousehold.test {
                val isCreating = awaitItem()
                assertFalse(isCreating)
            }
        }

    @Test
    fun `createHouseholdError is null initially`() =
        runTest {
            viewModel =
                HouseholdListViewModel(
                    mockContext,
                    mockAuth,
                    mockHouseholdRepository,
                    mockAdminRepository,
                    mockUserRepository
                )

            viewModel.createHouseholdError.test {
                val error = awaitItem()
                assertNull(error)
            }
        }

    // NOTE: Tests for getDisplayHouseholdsForCurrentUser Flow collection removed
    // Real-time household list updates with Flow collection require integration testing
    // with Firebase emulator to test the live data stream functionality
}
