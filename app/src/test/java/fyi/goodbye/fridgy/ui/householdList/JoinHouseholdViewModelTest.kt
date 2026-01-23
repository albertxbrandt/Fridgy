package fyi.goodbye.fridgy.ui.householdList

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.InviteCode
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import fyi.goodbye.fridgy.ui.shared.UiState
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JoinHouseholdViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockContext: Context
    private lateinit var mockHouseholdRepository: HouseholdRepository
    private lateinit var viewModel: JoinHouseholdViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockContext = mockk(relaxed = true)
        mockHouseholdRepository = mockk(relaxed = true)

        every { mockContext.getString(R.string.error_invalid_invite_code) } returns "Invalid invite code"
        every { mockContext.getString(R.string.error_failed_to_join_household) } returns "Failed to join household"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state is Idle`() =
        runTest {
            viewModel = JoinHouseholdViewModel(mockContext, mockHouseholdRepository)

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Success)
                assertTrue((state as UiState.Success).data is JoinHouseholdViewModel.JoinState.Idle)
            }
        }

    @Test
    fun `inviteCode is empty initially`() =
        runTest {
            viewModel = JoinHouseholdViewModel(mockContext, mockHouseholdRepository)

            viewModel.inviteCode.test {
                val code = awaitItem()
                assertEquals("", code)
            }
        }

    @Test
    fun `updateInviteCode converts to uppercase and filters non-alphanumeric`() =
        runTest {
            viewModel = JoinHouseholdViewModel(mockContext, mockHouseholdRepository)

            viewModel.updateInviteCode("abc123!@#")

            viewModel.inviteCode.test {
                val code = awaitItem()
                assertEquals("ABC123", code)
            }
        }

    @Test
    fun `updateInviteCode limits to 6 characters`() =
        runTest {
            viewModel = JoinHouseholdViewModel(mockContext, mockHouseholdRepository)

            viewModel.updateInviteCode("ABCDEFGHIJ")

            viewModel.inviteCode.test {
                val code = awaitItem()
                assertEquals("ABCDEF", code)
            }
        }

    @Test
    fun `validateCode with less than 6 characters sets Error`() =
        runTest {
            viewModel = JoinHouseholdViewModel(mockContext, mockHouseholdRepository)

            viewModel.updateInviteCode("ABC12")
            viewModel.validateCode()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("Invalid invite code", (state as UiState.Error).message)
            }
        }

    @Test
    fun `validateCode with valid code and not member sets CodeValid`() =
        runTest {
            val inviteCode =
                InviteCode(
                    code = "ABC123",
                    householdId = "household-123",
                    householdName = "Smith Family",
                    createdBy = "user-1",
                    createdAt = System.currentTimeMillis(),
                    expiresAt = null,
                    isActive = true
                )
            coEvery { mockHouseholdRepository.validateInviteCode(any()) } returns inviteCode
            coEvery { mockHouseholdRepository.isUserMemberOfHousehold(any()) } returns false

            viewModel = JoinHouseholdViewModel(mockContext, mockHouseholdRepository)

            viewModel.updateInviteCode("ABC123")
            viewModel.validateCode()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Success)
                val joinState = (state as UiState.Success).data
                assertTrue(joinState is JoinHouseholdViewModel.JoinState.CodeValid)
                assertEquals("Smith Family", (joinState as JoinHouseholdViewModel.JoinState.CodeValid).householdName)
            }
        }

    @Test
    fun `validateCode with valid code and already member sets AlreadyMember`() =
        runTest {
            val inviteCode =
                InviteCode(
                    code = "ABC123",
                    householdId = "household-123",
                    householdName = "Smith Family",
                    createdBy = "user-1",
                    createdAt = System.currentTimeMillis(),
                    expiresAt = null,
                    isActive = true
                )
            coEvery { mockHouseholdRepository.validateInviteCode(any()) } returns inviteCode
            coEvery { mockHouseholdRepository.isUserMemberOfHousehold(any()) } returns true

            viewModel = JoinHouseholdViewModel(mockContext, mockHouseholdRepository)

            viewModel.updateInviteCode("ABC123")
            viewModel.validateCode()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Success)
                val joinState = (state as UiState.Success).data
                assertTrue(joinState is JoinHouseholdViewModel.JoinState.AlreadyMember)
            }
        }

    @Test
    fun `validateCode with invalid code sets Error`() =
        runTest {
            coEvery { mockHouseholdRepository.validateInviteCode(any()) } returns null

            viewModel = JoinHouseholdViewModel(mockContext, mockHouseholdRepository)

            viewModel.updateInviteCode("INVALID")
            viewModel.validateCode()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("Invalid invite code", (state as UiState.Error).message)
            }
        }

    @Test
    fun `validateCode handles repository exception`() =
        runTest {
            coEvery { mockHouseholdRepository.validateInviteCode(any()) } throws Exception("Network error")

            viewModel = JoinHouseholdViewModel(mockContext, mockHouseholdRepository)

            viewModel.updateInviteCode("ABC123")
            viewModel.validateCode()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("Network error", (state as UiState.Error).message)
            }
        }

    @Test
    fun `joinHousehold calls repository and sets Success state`() =
        runTest {
            coEvery { mockHouseholdRepository.redeemInviteCode(any()) } returns "household-123"

            viewModel = JoinHouseholdViewModel(mockContext, mockHouseholdRepository)

            viewModel.updateInviteCode("ABC123")
            viewModel.joinHousehold()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockHouseholdRepository.redeemInviteCode("ABC123") }

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Success)
                val joinState = (state as UiState.Success).data
                assertTrue(joinState is JoinHouseholdViewModel.JoinState.Success)
                assertEquals("household-123", (joinState as JoinHouseholdViewModel.JoinState.Success).householdId)
            }
        }

    @Test
    fun `joinHousehold handles repository exception`() =
        runTest {
            coEvery { mockHouseholdRepository.redeemInviteCode(any()) } throws Exception("Expired code")

            viewModel = JoinHouseholdViewModel(mockContext, mockHouseholdRepository)

            viewModel.updateInviteCode("ABC123")
            viewModel.joinHousehold()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
                assertEquals("Expired code", (state as UiState.Error).message)
            }
        }

    @Test
    fun `resetState sets state back to Idle`() =
        runTest {
            coEvery { mockHouseholdRepository.redeemInviteCode(any()) } returns "household-123"

            viewModel = JoinHouseholdViewModel(mockContext, mockHouseholdRepository)

            viewModel.updateInviteCode("ABC123")
            viewModel.joinHousehold()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.resetState()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Success)
                assertTrue((state as UiState.Success).data is JoinHouseholdViewModel.JoinState.Idle)
            }
        }
}
