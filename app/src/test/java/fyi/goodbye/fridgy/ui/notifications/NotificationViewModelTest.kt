package fyi.goodbye.fridgy.ui.notifications

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import fyi.goodbye.fridgy.models.Notification
import fyi.goodbye.fridgy.models.NotificationType
import fyi.goodbye.fridgy.repositories.NotificationRepository
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
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockRepository: NotificationRepository
    private lateinit var viewModel: NotificationViewModel

    private val testNotifications =
        listOf(
            Notification(
                id = "notif1",
                userId = "user1",
                title = "New item added",
                body = "Milk was added to Fridge",
                type = NotificationType.ITEM_ADDED,
                relatedFridgeId = "fridge1",
                relatedItemId = "item1",
                isRead = false,
                createdAt = Date()
            ),
            Notification(
                id = "notif2",
                userId = "user1",
                title = "Member joined",
                body = "John joined your fridge",
                type = NotificationType.MEMBER_JOINED,
                relatedFridgeId = "fridge1",
                isRead = true,
                createdAt = Date()
            )
        )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = mockk(relaxed = true)

        // Mock Android Log to prevent crashes
        mockkStatic(android.util.Log::class)    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state is Loading`() =
        runTest {
            every { mockRepository.getNotificationsFlow() } returns flowOf(emptyList())
            every { mockRepository.getUnreadCountFlow() } returns flowOf(0)

            viewModel = NotificationViewModel(mockRepository)

            // Initial value should be Loading
            assertEquals(UiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun `uiState emits Success with notifications`() =
        runTest {
            every { mockRepository.getNotificationsFlow() } returns flowOf(testNotifications)
            every { mockRepository.getUnreadCountFlow() } returns flowOf(1)

            viewModel = NotificationViewModel(mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                // stateIn emits Loading first, then Success
                val loadingState = awaitItem()
                assertTrue(loadingState is UiState.Loading)

                val successState = awaitItem()
                assertTrue(successState is UiState.Success)
                assertEquals(2, (successState as UiState.Success).data.size)
                assertEquals("notif1", successState.data[0].id)
                assertEquals("New item added", successState.data[0].title)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState emits Error when repository flow fails`() =
        runTest {
            val errorMessage = "Network error"
            every { mockRepository.getNotificationsFlow() } returns
                kotlinx.coroutines.flow.flow {
                    throw Exception(errorMessage)
                }
            every { mockRepository.getUnreadCountFlow() } returns flowOf(0)

            viewModel = NotificationViewModel(mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                // stateIn emits Loading first, then Error
                val loadingState = awaitItem()
                assertTrue(loadingState is UiState.Loading)

                val errorState = awaitItem()
                assertTrue(errorState is UiState.Error)
                assertEquals(errorMessage, (errorState as UiState.Error).message)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unreadCount flows from repository`() =
        runTest {
            every { mockRepository.getNotificationsFlow() } returns flowOf(testNotifications)
            every { mockRepository.getUnreadCountFlow() } returns flowOf(3)

            viewModel = NotificationViewModel(mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.unreadCount.test {
                // Initial value is 0, then flows to 3
                val initial = awaitItem()
                assertEquals(0, initial)

                val count = awaitItem()
                assertEquals(3, count)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unreadCount emits 0 when repository flow fails`() =
        runTest {
            every { mockRepository.getNotificationsFlow() } returns flowOf(emptyList())
            every { mockRepository.getUnreadCountFlow() } returns
                kotlinx.coroutines.flow.flow {
                    throw Exception("Error")
                }

            viewModel = NotificationViewModel(mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.unreadCount.test {
                // When error occurs, catch{} emits 0, which matches initialValue = 0
                // So we only get the initial value (0) since the emitted value is also 0
                val count = awaitItem()
                assertEquals(0, count)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `markAsRead succeeds and updates operationState`() =
        runTest {
            every { mockRepository.getNotificationsFlow() } returns flowOf(testNotifications)
            every { mockRepository.getUnreadCountFlow() } returns flowOf(1)
            coEvery { mockRepository.markAsRead("notif1") } returns Result.success(Unit)

            viewModel = NotificationViewModel(mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.operationState.test {
                // Initial state is Idle
                val idle = awaitItem()
                assertTrue(idle is UiState.Idle)

                viewModel.markAsRead("notif1")
                testDispatcher.scheduler.advanceUntilIdle()

                // Loading state
                val loading = awaitItem()
                assertTrue(loading is UiState.Loading)

                // Success state
                val success = awaitItem()
                assertTrue(success is UiState.Success)
                assertEquals("Marked as read", (success as UiState.Success).data)

                cancelAndIgnoreRemainingEvents()
            }

            coVerify { mockRepository.markAsRead("notif1") }
        }

    @Test
    fun `markAsRead fails and updates operationState to Error`() =
        runTest {
            every { mockRepository.getNotificationsFlow() } returns flowOf(testNotifications)
            every { mockRepository.getUnreadCountFlow() } returns flowOf(1)
            coEvery { mockRepository.markAsRead("notif1") } returns
                Result.failure(Exception("Network error"))

            viewModel = NotificationViewModel(mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.operationState.test {
                // Initial state is Idle
                val idle = awaitItem()
                assertTrue(idle is UiState.Idle)

                viewModel.markAsRead("notif1")
                testDispatcher.scheduler.advanceUntilIdle()

                // Loading state
                val loading = awaitItem()
                assertTrue(loading is UiState.Loading)

                // Error state
                val error = awaitItem()
                assertTrue(error is UiState.Error)
                assertEquals("Failed to mark as read", (error as UiState.Error).message)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `markAsRead shows Loading state during operation`() =
        runTest {
            every { mockRepository.getNotificationsFlow() } returns flowOf(testNotifications)
            every { mockRepository.getUnreadCountFlow() } returns flowOf(1)
            coEvery { mockRepository.markAsRead(any()) } returns Result.success(Unit)

            viewModel = NotificationViewModel(mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.operationState.test {
                val initialState = awaitItem()
                assertTrue(initialState is UiState.Idle)

                viewModel.markAsRead("notif1")

                val loadingState = awaitItem()
                assertTrue(loadingState is UiState.Loading)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `markAllAsRead succeeds and updates operationState`() =
        runTest {
            every { mockRepository.getNotificationsFlow() } returns flowOf(testNotifications)
            every { mockRepository.getUnreadCountFlow() } returns flowOf(2)
            coEvery { mockRepository.markAllAsRead() } returns Result.success(Unit)

            viewModel = NotificationViewModel(mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.operationState.test {
                val idle = awaitItem()
                assertTrue(idle is UiState.Idle)

                viewModel.markAllAsRead()
                testDispatcher.scheduler.advanceUntilIdle()

                val loading = awaitItem()
                assertTrue(loading is UiState.Loading)

                val success = awaitItem()
                assertTrue(success is UiState.Success)
                assertEquals("All marked as read", (success as UiState.Success).data)

                cancelAndIgnoreRemainingEvents()
            }

            coVerify { mockRepository.markAllAsRead() }
        }

    @Test
    fun `markAllAsRead fails and updates operationState to Error`() =
        runTest {
            every { mockRepository.getNotificationsFlow() } returns flowOf(testNotifications)
            every { mockRepository.getUnreadCountFlow() } returns flowOf(2)
            coEvery { mockRepository.markAllAsRead() } returns
                Result.failure(Exception("Network error"))

            viewModel = NotificationViewModel(mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.operationState.test {
                val idle = awaitItem()
                assertTrue(idle is UiState.Idle)

                viewModel.markAllAsRead()
                testDispatcher.scheduler.advanceUntilIdle()

                val loading = awaitItem()
                assertTrue(loading is UiState.Loading)

                val error = awaitItem()
                assertTrue(error is UiState.Error)
                assertEquals("Failed to mark all as read", (error as UiState.Error).message)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteNotification succeeds and updates operationState`() =
        runTest {
            every { mockRepository.getNotificationsFlow() } returns flowOf(testNotifications)
            every { mockRepository.getUnreadCountFlow() } returns flowOf(1)
            coEvery { mockRepository.deleteNotification("notif1") } returns Result.success(Unit)

            viewModel = NotificationViewModel(mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.operationState.test {
                val idle = awaitItem()
                assertTrue(idle is UiState.Idle)

                viewModel.deleteNotification("notif1")
                testDispatcher.scheduler.advanceUntilIdle()

                val loading = awaitItem()
                assertTrue(loading is UiState.Loading)

                val success = awaitItem()
                assertTrue(success is UiState.Success)
                assertEquals("Notification deleted", (success as UiState.Success).data)

                cancelAndIgnoreRemainingEvents()
            }

            coVerify { mockRepository.deleteNotification("notif1") }
        }

    @Test
    fun `deleteNotification fails and updates operationState to Error`() =
        runTest {
            every { mockRepository.getNotificationsFlow() } returns flowOf(testNotifications)
            every { mockRepository.getUnreadCountFlow() } returns flowOf(1)
            coEvery { mockRepository.deleteNotification("notif1") } returns
                Result.failure(Exception("Network error"))

            viewModel = NotificationViewModel(mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.operationState.test {
                val idle = awaitItem()
                assertTrue(idle is UiState.Idle)

                viewModel.deleteNotification("notif1")
                testDispatcher.scheduler.advanceUntilIdle()

                val loading = awaitItem()
                assertTrue(loading is UiState.Loading)

                val error = awaitItem()
                assertTrue(error is UiState.Error)
                assertEquals("Failed to delete notification", (error as UiState.Error).message)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `resetOperationState resets state to Idle`() =
        runTest {
            every { mockRepository.getNotificationsFlow() } returns flowOf(testNotifications)
            every { mockRepository.getUnreadCountFlow() } returns flowOf(1)
            coEvery { mockRepository.deleteNotification(any()) } returns
                Result.failure(Exception("Error"))

            viewModel = NotificationViewModel(mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.operationState.test {
                val idle = awaitItem()
                assertTrue(idle is UiState.Idle)

                // Set state to Error first
                viewModel.deleteNotification("notif1")
                testDispatcher.scheduler.advanceUntilIdle()

                val loading = awaitItem()
                assertTrue(loading is UiState.Loading)

                val error = awaitItem()
                assertTrue(error is UiState.Error)

                // Reset it
                viewModel.resetOperationState()

                val resetToIdle = awaitItem()
                assertTrue(resetToIdle is UiState.Idle)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `empty notifications list emits Success state`() =
        runTest {
            every { mockRepository.getNotificationsFlow() } returns flowOf(emptyList())
            every { mockRepository.getUnreadCountFlow() } returns flowOf(0)

            viewModel = NotificationViewModel(mockRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val loading = awaitItem()
                assertTrue(loading is UiState.Loading)

                val state = awaitItem()
                assertTrue(state is UiState.Success)
                assertEquals(0, (state as UiState.Success).data.size)

                cancelAndIgnoreRemainingEvents()
            }
        }
}

