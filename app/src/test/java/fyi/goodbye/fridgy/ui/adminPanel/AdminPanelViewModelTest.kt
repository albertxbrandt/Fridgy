package fyi.goodbye.fridgy.ui.adminPanel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.AdminUserDisplay
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.repositories.AdminRepository
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AdminPanelViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockContext: Context
    private lateinit var mockAdminRepository: AdminRepository
    private lateinit var viewModel: AdminPanelViewModel

    private val testUsers =
        listOf(
            AdminUserDisplay(
                uid = "user-1",
                username = "testuser1",
                email = "test1@example.com",
                createdAt = System.currentTimeMillis()
            ),
            AdminUserDisplay(
                uid = "user-2",
                username = "testuser2",
                email = "test2@example.com",
                createdAt = System.currentTimeMillis()
            )
        )

    private val testProducts =
        listOf(
            Product(
                upc = "123456",
                name = "Test Product",
                brand = "Test Brand",
                category = "Test Category",
                imageUrl = null,
                lastUpdated = System.currentTimeMillis()
            )
        )

    private val testFridges =
        listOf(
            Fridge(
                id = "fridge-1",
                name = "Main Fridge",
                type = "fridge",
                householdId = "household-1",
                createdBy = "user-1",
                createdAt = System.currentTimeMillis()
            )
        )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)        mockContext = mockk(relaxed = true)
        mockAdminRepository = mockk(relaxed = true)

        every { mockContext.getString(R.string.error_failed_to_load_admin_data, any()) } returns "Failed to load admin data"
        every { mockContext.getString(R.string.error_failed_to_delete_user, any()) } returns "Failed to delete user"
        every { mockContext.getString(R.string.error_failed_to_update_user, any()) } returns "Failed to update user"
        every { mockContext.getString(R.string.error_failed_to_delete_product, any()) } returns "Failed to delete product"
        every { mockContext.getString(R.string.error_failed_to_update_product, any()) } returns "Failed to update product"

        // Default repository behaviors
        coEvery { mockAdminRepository.isCurrentUserAdmin() } returns true
        coEvery { mockAdminRepository.getAllUsers() } returns testUsers
        coEvery { mockAdminRepository.getAllProducts() } returns testProducts
        coEvery { mockAdminRepository.getAllFridges() } returns testFridges
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state is Loading`() =
        runTest {
            viewModel = AdminPanelViewModel(mockContext, mockAdminRepository)

            assertTrue(viewModel.uiState.value is UiState.Loading)
        }

    @Test
    fun `loadAdminData when user is admin loads data successfully`() =
        runTest {
            viewModel = AdminPanelViewModel(mockContext, mockAdminRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Success)
                val data = (state as UiState.Success).data as AdminData
                assertEquals(2, data.totalUsers)
                assertEquals(1, data.totalProducts)
                assertEquals(1, data.totalFridges)
            }
        }

    @Test
    fun `loadAdminData when user is not admin sets Unauthorized state`() =
        runTest {
            coEvery { mockAdminRepository.isCurrentUserAdmin() } returns false

            viewModel = AdminPanelViewModel(mockContext, mockAdminRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Success)
                assertTrue((state as UiState.Success).data is UnauthorizedState)
            }
        }

    @Test
    fun `loadAdminData when all collections empty sets Error`() =
        runTest {
            coEvery { mockAdminRepository.getAllUsers() } returns emptyList()
            coEvery { mockAdminRepository.getAllProducts() } returns emptyList()
            coEvery { mockAdminRepository.getAllFridges() } returns emptyList()

            viewModel = AdminPanelViewModel(mockContext, mockAdminRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
            }
        }

    @Test
    fun `loadAdminData handles repository exception`() =
        runTest {
            coEvery { mockAdminRepository.getAllUsers() } throws Exception("Network error")

            viewModel = AdminPanelViewModel(mockContext, mockAdminRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
            }
        }

    @Test
    fun `refresh reloads admin data`() =
        runTest {
            viewModel = AdminPanelViewModel(mockContext, mockAdminRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.refresh()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 2) { mockAdminRepository.getAllUsers() }
        }

    @Test
    fun `deleteUser calls repository and refreshes on success`() =
        runTest {
            coEvery { mockAdminRepository.deleteUser(any()) } returns true

            viewModel = AdminPanelViewModel(mockContext, mockAdminRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.deleteUser("user-1")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockAdminRepository.deleteUser("user-1") }
            coVerify(exactly = 2) { mockAdminRepository.getAllUsers() }
        }

    @Test
    fun `deleteUser does not refresh on failure`() =
        runTest {
            coEvery { mockAdminRepository.deleteUser(any()) } returns false

            viewModel = AdminPanelViewModel(mockContext, mockAdminRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.deleteUser("user-1")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { mockAdminRepository.getAllUsers() }
        }

    @Test
    fun `deleteUser handles repository exception`() =
        runTest {
            coEvery { mockAdminRepository.deleteUser(any()) } throws Exception("Permission denied")

            viewModel = AdminPanelViewModel(mockContext, mockAdminRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.deleteUser("user-1")
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is UiState.Error)
            }
        }

    @Test
    fun `updateUser calls repository and refreshes on success`() =
        runTest {
            coEvery { mockAdminRepository.updateUser(any(), any(), any()) } returns true

            viewModel = AdminPanelViewModel(mockContext, mockAdminRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateUser("user-1", "newname", "new@email.com")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockAdminRepository.updateUser("user-1", "newname", "new@email.com") }
            coVerify(exactly = 2) { mockAdminRepository.getAllUsers() }
        }

    @Test
    fun `deleteProduct calls repository and refreshes on success`() =
        runTest {
            coEvery { mockAdminRepository.deleteProduct(any()) } returns true

            viewModel = AdminPanelViewModel(mockContext, mockAdminRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.deleteProduct("123456")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockAdminRepository.deleteProduct("123456") }
            coVerify(exactly = 2) { mockAdminRepository.getAllProducts() }
        }

    @Test
    fun `updateProduct calls repository and refreshes on success`() =
        runTest {
            coEvery { mockAdminRepository.updateProduct(any(), any(), any(), any()) } returns true

            viewModel = AdminPanelViewModel(mockContext, mockAdminRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateProduct("123456", "New Name", "New Brand", "New Category")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockAdminRepository.updateProduct("123456", "New Name", "New Brand", "New Category") }
            coVerify(exactly = 2) { mockAdminRepository.getAllProducts() }
        }
}

