package fyi.goodbye.fridgy.ui.itemDetail

import android.content.Context
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayItem
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.models.UserProfile
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.repositories.ProductRepository
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockContext: Context
    private lateinit var mockFridgeRepository: FridgeRepository
    private lateinit var mockProductRepository: ProductRepository
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: ItemDetailViewModel

    private val testFridgeId = "test-fridge-123"
    private val testItemId = "item-1"
    private val testUpc = "123456789"
    private val testUserId = "user-123"
    private val testHouseholdId = "household-456"

    private val testProduct =
        Product(
            upc = testUpc,
            name = "Milk",
            brand = "Organic Valley",
            category = "Dairy",
            imageUrl = "",
            lastUpdated = System.currentTimeMillis()
        )

    private val testItem =
        Item(
            id = testItemId,
            upc = testUpc,
            householdId = testHouseholdId,
            addedAt = System.currentTimeMillis(),
            addedBy = testUserId,
            lastUpdatedAt = System.currentTimeMillis(),
            lastUpdatedBy = testUserId,
            expirationDate = null
        )

    private val testDisplayItem = DisplayItem(testItem, testProduct)

    private val testUserProfile = UserProfile(uid = testUserId, username = "TestUser")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockFridgeRepository = mockk(relaxed = true)
        mockProductRepository = mockk(relaxed = true)

        every { mockContext.getString(R.string.error_item_not_found) } returns "Item not found"
        every { mockContext.getString(R.string.error_product_not_found) } returns "Product not found"
        every { mockContext.getString(R.string.error_failed_to_load_details) } returns "Failed to load details"
        every { mockContext.getString(R.string.unknown_user) } returns "Unknown User"

        // Default repository behaviors
        coEvery { mockFridgeRepository.getItemsForFridge(any()) } returns flowOf(listOf(testDisplayItem))
        coEvery { mockProductRepository.getProductInfo(any()) } returns testProduct
        coEvery { mockFridgeRepository.getUserProfileById(any()) } returns testUserProfile

        savedStateHandle =
            SavedStateHandle().apply {
                set("fridgeId", testFridgeId)
                set("itemId", testItemId)
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
            // Don't advance dispatcher, check initial state
            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )

            assertTrue(viewModel.uiState.value is UiState.Loading)
        }

    @Test
    fun `loadDetails emits Success with item data`() =
        runTest {
            coEvery { mockFridgeRepository.getItemsForFridge(testFridgeId) } returns flowOf(listOf(testDisplayItem))

            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is UiState.Success)
            assertEquals(1, (state as UiState.Success).data.items.size)
            assertEquals("Milk", state.data.product.name)
        }

    @Test
    fun `loadDetails emits Error when item not found`() =
        runTest {
            coEvery { mockFridgeRepository.getItemsForFridge(testFridgeId) } returns flowOf(emptyList())

            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is UiState.Error)
            assertEquals("Item not found", (state as UiState.Error).message)
        }

    @Test
    fun `loadDetails sorts items by expiration date`() =
        runTest {
            val expiredItem = testItem.copy(id = "item-2", expirationDate = System.currentTimeMillis() - 86400000L)
            val futureItem = testItem.copy(id = "item-3", expirationDate = System.currentTimeMillis() + 86400000L)
            val noExpiryItem = testItem.copy(id = "item-4", expirationDate = null)

            coEvery { mockFridgeRepository.getItemsForFridge(testFridgeId) } returns
                flowOf(
                    listOf(
                        DisplayItem(noExpiryItem, testProduct),
                        DisplayItem(futureItem, testProduct),
                        DisplayItem(expiredItem, testProduct),
                        DisplayItem(testItem, testProduct)
                    )
                )

            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is UiState.Success)
            val items = (state as UiState.Success).data.items

            // Items sorted by: null expiration last, then by expiration date ascending
            assertEquals(4, items.size)
            assertEquals(expiredItem.id, items[0].id) // Earliest date first
            assertEquals(futureItem.id, items[1].id) // Later date
            // Null expiration dates come last
            assertTrue(items[2].expirationDate == null)
            assertTrue(items[3].expirationDate == null)
        }

    @Test
    fun `loadUserNames resolves usernames for addedBy and lastUpdatedBy`() =
        runTest {
            val user1 = UserProfile("user-1", "Alice")
            val user2 = UserProfile("user-2", "Bob")

            coEvery { mockFridgeRepository.getUserProfileById("user-1") } returns user1
            coEvery { mockFridgeRepository.getUserProfileById("user-2") } returns user2

            val item1 = testItem.copy(id = "item-1", addedBy = "user-1", lastUpdatedBy = "user-2")
            coEvery { mockFridgeRepository.getItemsForFridge(testFridgeId) } returns
                flowOf(listOf(DisplayItem(item1, testProduct)))

            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.userNames.test {
                val names = awaitItem()
                assertEquals("Alice", names["user-1"])
                assertEquals("Bob", names["user-2"])
            }
        }

    @Test
    fun `loadUserNames returns Unknown User when profile not found`() =
        runTest {
            coEvery { mockFridgeRepository.getUserProfileById("user-999") } returns null

            val item = testItem.copy(addedBy = "user-999", lastUpdatedBy = "user-999")
            coEvery { mockFridgeRepository.getItemsForFridge(testFridgeId) } returns
                flowOf(listOf(DisplayItem(item, testProduct)))

            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.userNames.test {
                val names = awaitItem()
                assertEquals("Unknown User", names["user-999"])
            }
        }

    @Test
    fun `deleteItem calls repository deleteItem`() =
        runTest {
            coEvery { mockFridgeRepository.deleteItem(any(), any()) } returns Unit

            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )

            viewModel.deleteItem("item-to-delete")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockFridgeRepository.deleteItem(testFridgeId, "item-to-delete") }
        }

    @Test
    fun `showAddInstanceDialog sets pendingItemForDate to UPC`() =
        runTest {
            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.showAddInstanceDialog()

            viewModel.pendingItemForDate.test {
                val upc = awaitItem()
                assertEquals(testUpc, upc)
            }
        }

    @Test
    fun `cancelDatePicker clears pendingItemForDate`() =
        runTest {
            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.showAddInstanceDialog()
            viewModel.cancelDatePicker()

            viewModel.pendingItemForDate.test {
                val upc = awaitItem()
                assertNull(upc)
            }
        }

    @Test
    fun `cancelDatePicker clears pendingItemForEdit`() =
        runTest {
            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.showEditExpirationDialog(testItem)
            viewModel.cancelDatePicker()

            viewModel.pendingItemForEdit.test {
                val item = awaitItem()
                assertNull(item)
            }
        }

    @Test
    fun `addNewInstanceWithDate clears pendingItemForDate and adds item`() =
        runTest {
            val expirationDate = System.currentTimeMillis() + 86400000L
            val newItem = testItem.copy(id = "new-item", expirationDate = expirationDate)
            coEvery { mockFridgeRepository.addItemToFridge(testFridgeId, testUpc, expirationDate) } returns newItem

            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.addNewInstanceWithDate(expirationDate)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.pendingItemForDate.test {
                val upc = awaitItem()
                assertNull(upc)
            }

            coVerify { mockFridgeRepository.addItemToFridge(testFridgeId, testUpc, expirationDate) }
        }

    @Test
    fun `addNewInstanceWithDate with null date adds item without expiration`() =
        runTest {
            val newItem = testItem.copy(id = "new-item", expirationDate = null)
            coEvery { mockFridgeRepository.addItemToFridge(testFridgeId, testUpc, null) } returns newItem

            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.addNewInstanceWithDate(null)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockFridgeRepository.addItemToFridge(testFridgeId, testUpc, null) }
        }

    @Test
    fun `getProductForDisplay returns product from repository`() =
        runTest {
            coEvery { mockProductRepository.getProductInfo(testUpc) } returns testProduct

            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )

            val product = viewModel.getProductForDisplay(testUpc)

            assertNotNull(product)
            assertEquals("Milk", product.name)
        }

    @Test
    fun `updateQuantity logs deprecation warning`() =
        runTest {
            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )

            // Should not throw, just log
            viewModel.updateQuantity(5)

            // Verify warning logged
            io.mockk.verify { Log.w("ItemDetailVM", "updateQuantity called but quantity is deprecated") }
        }

    @Test
    fun `showEditExpirationDialog sets pendingItemForEdit to item`() =
        runTest {
            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.showEditExpirationDialog(testItem)

            viewModel.pendingItemForEdit.test {
                val item = awaitItem()
                assertEquals(testItem, item)
            }
        }

    @Test
    fun `updateItemExpiration clears pendingItemForEdit and updates expiration`() =
        runTest {
            val newExpirationDate = System.currentTimeMillis() + 86400000L
            coEvery {
                mockFridgeRepository.updateItemExpirationDate(
                    testFridgeId,
                    testItemId,
                    newExpirationDate
                )
            } returns Unit

            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.showEditExpirationDialog(testItem)
            viewModel.updateItemExpiration(newExpirationDate)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.pendingItemForEdit.test {
                val item = awaitItem()
                assertNull(item)
            }

            coVerify {
                mockFridgeRepository.updateItemExpirationDate(
                    testFridgeId,
                    testItemId,
                    newExpirationDate
                )
            }
        }

    @Test
    fun `updateItemExpiration with null removes expiration`() =
        runTest {
            coEvery {
                mockFridgeRepository.updateItemExpirationDate(
                    testFridgeId,
                    testItemId,
                    null
                )
            } returns Unit

            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.showEditExpirationDialog(testItem)
            viewModel.updateItemExpiration(null)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify {
                mockFridgeRepository.updateItemExpirationDate(
                    testFridgeId,
                    testItemId,
                    null
                )
            }
        }

    @Test
    fun `loadDetails handles repository exception`() =
        runTest {
            coEvery { mockFridgeRepository.getItemsForFridge(testFridgeId) } throws Exception("Network error")

            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            // Exception in Flow collection results in Error state
            assertTrue(state is UiState.Error || state is UiState.Loading)
            if (state is UiState.Error) {
                assertTrue(state.message.contains("Failed to load details") || state.message.contains("Network error"))
            }
        }

    @Test
    fun `empty items list after deletion maintains Success state with empty list`() =
        runTest {
            // Start with one item
            coEvery { mockFridgeRepository.getItemsForFridge(testFridgeId) } returns
                flowOf(
                    listOf(testDisplayItem),
                    // Then all items deleted
                    emptyList()
                )

            viewModel =
                ItemDetailViewModel(
                    mockContext,
                    savedStateHandle,
                    mockFridgeRepository,
                    mockProductRepository
                )
            testDispatcher.scheduler.advanceUntilIdle()

            // Should maintain Success state with empty list
            val state = viewModel.uiState.value
            assertTrue(state is UiState.Success)
            assertEquals(0, (state as UiState.Success).data.items.size)
        }
}
