package fyi.goodbye.fridgy.ui.shared

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import fyi.goodbye.fridgy.models.Category
import fyi.goodbye.fridgy.repositories.CategoryRepository
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockApplication: Application
    private lateinit var mockRepository: CategoryRepository
    private lateinit var viewModel: CategoryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockApplication = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)

        every { mockApplication.getString(any()) } returns "Error message"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state is Loading`() = runTest {
        coEvery { mockRepository.getCategories() } returns flowOf(emptyList())
        viewModel = CategoryViewModel(mockApplication, mockRepository)

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is CategoryViewModel.CategoryUiState.Loading)
        }
    }

    @Test
    fun `loading categories updates state to Success`() = runTest {
        val testCategories = listOf(
            Category(id = "1", name = "Dairy", order = 1),
            Category(id = "2", name = "Meat", order = 2)
        )
        coEvery { mockRepository.getCategories() } returns flowOf(testCategories)

        viewModel = CategoryViewModel(mockApplication, mockRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is CategoryViewModel.CategoryUiState.Success)
            assertEquals(2, (state as CategoryViewModel.CategoryUiState.Success).categories.size)
            assertEquals("Dairy", state.categories[0].name)
            assertEquals("Meat", state.categories[1].name)
        }
    }

    @Test
    fun `loading categories failure updates state to Error`() = runTest {
        coEvery { mockRepository.getCategories() } throws Exception("Network error")

        viewModel = CategoryViewModel(mockApplication, mockRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is CategoryViewModel.CategoryUiState.Error)
        }
    }

    @Test
    fun `createCategory calls repository`() = runTest {
        coEvery { mockRepository.getCategories() } returns flowOf(emptyList())
        coEvery { mockRepository.createCategory(any(), any()) } returns "new-cat-id"

        viewModel = CategoryViewModel(mockApplication, mockRepository)
        viewModel.createCategory("New Category", 5)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.createCategory("New Category", 5) }
    }

    @Test
    fun `updateCategory calls repository`() = runTest {
        coEvery { mockRepository.getCategories() } returns flowOf(emptyList())
        coEvery { mockRepository.updateCategory(any(), any(), any()) } just Runs

        viewModel = CategoryViewModel(mockApplication, mockRepository)
        viewModel.updateCategory("cat-id", "Updated Name", 10)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.updateCategory("cat-id", "Updated Name", 10) }
    }

    @Test
    fun `deleteCategory calls repository`() = runTest {
        coEvery { mockRepository.getCategories() } returns flowOf(emptyList())
        coEvery { mockRepository.deleteCategory(any()) } just Runs

        viewModel = CategoryViewModel(mockApplication, mockRepository)
        viewModel.deleteCategory("cat-id")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.deleteCategory("cat-id") }
    }
}
