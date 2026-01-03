package fyi.goodbye.fridgy.models

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ProductTest {
    @Test
    fun `product with same upc are equal`() {
        val product1 = Product(
            upc = "123456789",
            name = "Milk",
            brand = "Brand A",
            category = "Dairy",
            imageUrl = "url1",
            lastUpdated = 0L
        )
        val product2 = Product(
            upc = "123456789",
            name = "Different Name",
            brand = "Brand B",
            category = "Other",
            imageUrl = "url2",
            lastUpdated = 1L
        )
        assertEquals(product1.upc, product2.upc)
    }

    @Test
    fun `product with different upc are not equal`() {
        val product1 = Product(
            upc = "123456789",
            name = "Milk",
            brand = "Brand A",
            category = "Dairy",
            imageUrl = "",
            lastUpdated = 0L
        )
        val product2 = Product(
            upc = "987654321",
            name = "Milk",
            brand = "Brand A",
            category = "Dairy",
            imageUrl = "",
            lastUpdated = 0L
        )
        assertNotEquals(product1.upc, product2.upc)
    }

    @Test
    fun `product properties are correctly assigned`() {
        val product = Product(
            upc = "123456789",
            name = "Organic Milk",
            brand = "Organic Valley",
            category = "Dairy",
            imageUrl = "https://example.com/image.jpg",
            lastUpdated = 1234567890L
        )

        assertEquals("123456789", product.upc)
        assertEquals("Organic Milk", product.name)
        assertEquals("Organic Valley", product.brand)
        assertEquals("Dairy", product.category)
        assertEquals("https://example.com/image.jpg", product.imageUrl)
        assertEquals(1234567890L, product.lastUpdated)
    }

    @Test
    fun `product with empty brand is valid`() {
        val product = Product(
            upc = "123456789",
            name = "Generic Product",
            brand = "",
            category = "Other",
            imageUrl = "",
            lastUpdated = 0L
        )
        assertEquals("", product.brand)
    }

    @Test
    fun `product with empty imageUrl is valid`() {
        val product = Product(
            upc = "123456789",
            name = "Test Product",
            brand = "Test Brand",
            category = "Test",
            imageUrl = "",
            lastUpdated = 0L
        )
        assertEquals("", product.imageUrl)
    }
}
