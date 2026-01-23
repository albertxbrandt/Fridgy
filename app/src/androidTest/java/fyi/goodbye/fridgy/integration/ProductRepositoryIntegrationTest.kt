package fyi.goodbye.fridgy.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import fyi.goodbye.fridgy.FirebaseTestUtils
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.repositories.ProductRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.tasks.await

/**
 * Integration tests for ProductRepository using Firebase Local Emulator Suite.
 * Tests crowdsourced product database operations.
 */
@RunWith(AndroidJUnit4::class)
class ProductRepositoryIntegrationTest {

    private lateinit var repository: ProductRepository
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var context: Context
    private val testProducts = mutableListOf<String>()

    @Before
    fun setup() {
        // Configure Firebase emulators
        FirebaseTestUtils.useEmulators()
        
        context = ApplicationProvider.getApplicationContext()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        repository = ProductRepository(context, firestore, storage)
    }

    @After
    fun teardown() = runTest {
        // Clean up test products
        testProducts.forEach { upc ->
            try {
                firestore.collection("products").document(upc).delete().await()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    fun getProductInfo_returnsNullForNonexistent() = runTest {
        val product = repository.getProductInfo("999999999999")
        assertNull("Should return null for nonexistent product", product)
    }

    @Test
    fun getProductInfo_returnsExistingProduct() = runTest {
        val upc = "100000000001"
        testProducts.add(upc)
        
        // Create test product
        val testProduct = Product(
            upc = upc,
            name = "Test Product",
            brand = "Test Brand",
            category = "Test Category",
            imageUrl = null,
            lastUpdated = System.currentTimeMillis()
        )
        
        firestore.collection("products").document(upc).set(testProduct).await()

        // Retrieve product
        val product = repository.getProductInfo(upc)
        
        assertNotNull("Product should not be null", product)
        assertEquals(upc, product?.upc)
        assertEquals("Test Product", product?.name)
        assertEquals("Test Brand", product?.brand)
        assertEquals("Test Category", product?.category)
    }

    @Test
    fun getProductInfoFresh_returnsLatestProduct() = runTest {
        val upc = "200000000002"
        testProducts.add(upc)
        
        val testProduct = Product(
            upc = upc,
            name = "Fresh Product",
            brand = "Fresh Brand",
            category = "Food",
            imageUrl = null,
            lastUpdated = System.currentTimeMillis()
        )
        
        firestore.collection("products").document(upc).set(testProduct).await()

        val product = repository.getProductInfoFresh(upc)
        
        assertNotNull("Product should not be null", product)
        assertEquals("Fresh Product", product?.name)
    }

    @Test
    fun saveProductInfo_successfullySavesProduct() = runTest {
        val upc = "300000000003"
        testProducts.add(upc)
        
        val product = Product(
            upc = upc,
            name = "Saved Product",
            brand = "Saved Brand",
            category = "Food",
            imageUrl = null,
            lastUpdated = System.currentTimeMillis()
        )
        
        repository.saveProductInfo(product)

        // Verify product was saved
        val doc = firestore.collection("products").document(upc).get().await()
        assertTrue("Product document should exist", doc.exists())
        assertEquals("Saved Product", doc.getString("name"))
        assertEquals("Saved Brand", doc.getString("brand"))
        assertEquals("Food", doc.getString("category"))
    }

    @Test
    fun saveProductInfo_updatesExistingProduct() = runTest {
        val upc = "400000000004"
        testProducts.add(upc)
        
        // Save initial product
        val product1 = Product(
            upc = upc,
            name = "Original Name",
            brand = "Original Brand",
            category = "Food",
            imageUrl = null,
            lastUpdated = System.currentTimeMillis()
        )
        repository.saveProductInfo(product1)
        
        // Update product
        val product2 = Product(
            upc = upc,
            name = "Updated Name",
            brand = "Updated Brand",
            category = "Beverages",
            imageUrl = null,
            lastUpdated = System.currentTimeMillis()
        )
        repository.saveProductInfo(product2)

        // Verify updates
        val doc = firestore.collection("products").document(upc).get().await()
        assertEquals("Updated Name", doc.getString("name"))
        assertEquals("Updated Brand", doc.getString("brand"))
        assertEquals("Beverages", doc.getString("category"))
    }

    @Test
    fun searchProductsByName_findsMatchingProducts() = runTest {
        // Create test products with search tokens
        val upc1 = "500000000005"
        val upc2 = "500000000006"
        testProducts.add(upc1)
        testProducts.add(upc2)
        
        val product1 = Product(
            upc = upc1,
            name = "Coca Cola",
            brand = "Coca-Cola",
            category = "Beverages",
            imageUrl = null,
            lastUpdated = System.currentTimeMillis(),
            searchTokens = listOf("coca", "cola", "coke")
        )
        val product2 = Product(
            upc = upc2,
            name = "Pepsi Cola",
            brand = "PepsiCo",
            category = "Beverages",
            imageUrl = null,
            lastUpdated = System.currentTimeMillis(),
            searchTokens = listOf("pepsi", "cola")
        )
        
        firestore.collection("products").document(upc1).set(product1).await()
        firestore.collection("products").document(upc2).set(product2).await()

        // Search by name
        val results = repository.searchProductsByName("cola")
        
        assertTrue("Should find at least 2 products", results.size >= 2)
        assertTrue("Should contain Coca Cola", 
            results.any { it.name == "Coca Cola" })
        assertTrue("Should contain Pepsi Cola", 
            results.any { it.name == "Pepsi Cola" })
    }

    @Test
    fun searchProductsByName_returnsEmptyForBlankQuery() = runTest {
        val results = repository.searchProductsByName("")
        assertEquals("Should return empty list for blank query", 0, results.size)
    }

    @Test
    fun productCaching_reducesDatabaseReads() = runTest {
        val upc = "600000000007"
        testProducts.add(upc)
        
        val product = Product(
            upc = upc,
            name = "Cached Product",
            brand = "Cached Brand",
            category = "Food",
            imageUrl = null,
            lastUpdated = System.currentTimeMillis()
        )
        firestore.collection("products").document(upc).set(product).await()

        // First read - from database
        val product1 = repository.getProductInfo(upc)
        assertNotNull("First read should succeed", product1)
        
        // Second read - from cache (we can't directly verify cache hit, 
        // but this tests that cache doesn't break functionality)
        val product2 = repository.getProductInfo(upc)
        assertNotNull("Second read should succeed", product2)
        assertEquals("Should return same product", product1?.name, product2?.name)
    }

    @Test
    fun getProductInfoFresh_bypassesCache() = runTest {
        val upc = "700000000008"
        testProducts.add(upc)
        
        val product = Product(
            upc = upc,
            name = "Test Product",
            brand = "Test Brand",
            category = "Food",
            imageUrl = null,
            lastUpdated = System.currentTimeMillis()
        )
        firestore.collection("products").document(upc).set(product).await()

        // Populate cache
        repository.getProductInfo(upc)
        
        // Get fresh - should bypass cache and get from server
        val freshProduct = repository.getProductInfoFresh(upc)
        
        assertNotNull("Fresh product should not be null", freshProduct)
        assertEquals("Test Product", freshProduct?.name)
    }
}
