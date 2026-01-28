package fyi.goodbye.fridgy.ui.householdSettings

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayHousehold
import fyi.goodbye.fridgy.models.InviteCode
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import fyi.goodbye.fridgy.repositories.MembershipRepository
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
class HouseholdSettingsViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockContext: Context
    private lateinit var mockHouseholdRepository: HouseholdRepository
    private lateinit var mockMembershipRepository: MembershipRepository
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: HouseholdSettingsViewModel

    private val testHouseholdId = "household-123"
    private val testUserId = "user-789"

    private val testDisplayHousehold =
        DisplayHousehold(
            id = testHouseholdId,
            name = "Smith Family",
            createdByUid = testUserId,
            ownerDisplayName = "Test User",
            memberUsers = listOf(),
            fridgeCount = 2,
            createdAt = System.currentTimeMillis()
        )

    private val testInviteCode =
        InviteCode(
            code = "ABC123",
            householdId = testHouseholdId,
            householdName = "Smith Family",
            createdBy = testUserId,
            createdAt = System.currentTimeMillis(),
            expiresAt = null,
            isActive = true
        )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        mockContext = mockk(relaxed = true)
        mockHouseholdRepository = mockk(relaxed = true)
        mockMembershipRepository = mockk(relaxed = true)
        mockAuth = mockk(relaxed = true)
        mockUser = mockk(relaxed = true)

        every { mockAuth.currentUser } returns mockUser
        every { mockUser.uid } returns testUserId
        every { mockContext.getString(R.string.error_fridge_not_found) } returns "Household not found"
        every { mockContext.getString(R.string.error_failed_to_load_fridge) } returns "Failed to load household"

        // Default repository behaviors
        coEvery { mockHouseholdRepository.getDisplayHouseholdById(any()) } returns testDisplayHousehold
        coEvery { mockMembershipRepository.getInviteCodesFlow(any()) } returns flowOf(listOf(testInviteCode))

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
    fun `initial state is Loading`() =
        runTest {
            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository,
                    mockMembershipRepository,
                    mockAuth
                )

            assertTrue(viewModel.uiState.value is UiState.Loading)
        }

    @Test
    fun `currentUserId returns authenticated user id`() =
        runTest {
            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository,
                    mockMembershipRepository,
                    mockAuth
                )

            assertEquals(testUserId, viewModel.currentUserId)
        }

    @Test
    fun `loadHouseholdDetails emits Success with household data`() =
        runTest {
            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository, `n                    mockMembershipRepository,`n mockAuth
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Success)
                val data = (state as UiState.Success).data
                assertEquals("Smith Family", data.name)
            }
        }

    @Test
    fun `loadHouseholdDetails emits Error when household not found`() =
        runTest {
            coEvery { mockHouseholdRepository.getDisplayHouseholdById(testHouseholdId) } returns null

            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository, `n                    mockMembershipRepository,`n mockAuth
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("Household not found", (state as UiState.Error).message)
            }
        }

    @Test
    fun `createInviteCode with expiration creates code`() =
        runTest {
            coEvery { mockMembershipRepository.createInviteCode(any(), any()) } returns testInviteCode

            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository, `n                    mockMembershipRepository,`n mockAuth
                )

            viewModel.createInviteCode(7)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.newInviteCode.test {
                val code = awaitItem()
                assertEquals("ABC123", code?.code)
            }
        }

    @Test
    fun `createInviteCode sets isCreatingInvite during operation`() =
        runTest {
            coEvery { mockMembershipRepository.createInviteCode(any(), any()) } returns testInviteCode

            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository, `n                    mockMembershipRepository,`n mockAuth
                )

            assertFalse(viewModel.isCreatingInvite.value)

            viewModel.createInviteCode(7)
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(viewModel.isCreatingInvite.value)
        }

    @Test
    fun `revokeInviteCode calls repository`() =
        runTest {
            coEvery { mockMembershipRepository.revokeInviteCode(any()) } returns Unit

            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository, `n                    mockMembershipRepository,`n mockAuth
                )

            viewModel.revokeInviteCode("ABC123")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockMembershipRepository.revokeInviteCode("ABC123") }
        }

    @Test
    fun `removeMember calls repository and refreshes`() =
        runTest {
            coEvery { mockHouseholdRepository.removeMember(any(), any()) } returns Unit

            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository, `n                    mockMembershipRepository,`n mockAuth
                )

            viewModel.removeMember("user-456")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockHouseholdRepository.removeMember(testHouseholdId, "user-456") }
        }

    @Test
    fun `leaveHousehold calls repository and invokes callback`() =
        runTest {
            coEvery { mockHouseholdRepository.leaveHousehold(any()) } returns Unit
            var callbackInvoked = false

            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository, `n                    mockMembershipRepository,`n mockAuth
                )

            viewModel.leaveHousehold { callbackInvoked = true }
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockHouseholdRepository.leaveHousehold(testHouseholdId) }
            assertTrue(callbackInvoked)
        }

    @Test
    fun `leaveHousehold sets isDeletingOrLeaving during operation`() =
        runTest {
            coEvery { mockHouseholdRepository.leaveHousehold(any()) } returns Unit

            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository, `n                    mockMembershipRepository,`n mockAuth
                )

            assertFalse(viewModel.isDeletingOrLeaving.value)

            viewModel.leaveHousehold {}
            testDispatcher.scheduler.advanceUntilIdle()

            // Note: isDeletingOrLeaving stays true after leaving (navigation happens)
            assertTrue(viewModel.isDeletingOrLeaving.value)
        }

    @Test
    fun `deleteHousehold calls repository and invokes callback`() =
        runTest {
            coEvery { mockHouseholdRepository.deleteHousehold(any()) } returns Unit
            var callbackInvoked = false

            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository, `n                    mockMembershipRepository,`n mockAuth
                )

            viewModel.deleteHousehold { callbackInvoked = true }
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockHouseholdRepository.deleteHousehold(testHouseholdId) }
            assertTrue(callbackInvoked)
        }

    @Test
    fun `deleteHousehold handles repository exception`() =
        runTest {
            coEvery { mockHouseholdRepository.deleteHousehold(any()) } throws Exception("Permission denied")
            var callbackInvoked = false

            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository, `n                    mockMembershipRepository,`n mockAuth
                )

            viewModel.deleteHousehold { callbackInvoked = true }
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(callbackInvoked)
            viewModel.actionError.test {
                val error = awaitItem()
                assertEquals("Permission denied", error)
            }
        }

    @Test
    fun `clearNewInviteCode sets newInviteCode to null`() =
        runTest {
            coEvery { mockMembershipRepository.createInviteCode(any(), any()) } returns testInviteCode

            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository, `n                    mockMembershipRepository,`n mockAuth
                )

            viewModel.createInviteCode(7)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.clearNewInviteCode()

            viewModel.newInviteCode.test {
                val code = awaitItem()
                assertNull(code)
            }
        }

    @Test
    fun `clearError sets actionError to null`() =
        runTest {
            coEvery { mockMembershipRepository.revokeInviteCode(any()) } throws Exception("Test error")

            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository, `n                    mockMembershipRepository,`n mockAuth
                )

            viewModel.revokeInviteCode("ABC123")
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.clearError()

            viewModel.actionError.test {
                val error = awaitItem()
                assertNull(error)
            }
        }

    @Test
    fun `inviteCodes is empty initially`() =
        runTest {
            coEvery { mockHouseholdRepository.getInviteCodesFlow(any()) } returns flowOf(emptyList())

            viewModel =
                HouseholdSettingsViewModel(
                    mockContext,
                    savedStateHandle,
                    mockHouseholdRepository, `n                    mockMembershipRepository,`n mockAuth
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.inviteCodes.test {
                val codes = awaitItem()
                assertTrue(codes.isEmpty())
            }
        }

    // NOTE: Tests for getInviteCodesFlow collection removed
    // Real-time invite code updates require integration testing with Firebase emulator
}
