package fyi.goodbye.fridgy.models

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CategoryTest {
    @Test
    fun `category with same id are equal`() {
        val category1 = Category(id = "1", name = "Dairy", order = 1)
        val category2 = Category(id = "1", name = "Different Name", order = 2)
        assertEquals(category1.id, category2.id)
    }

    @Test
    fun `category with different id are not equal`() {
        val category1 = Category(id = "1", name = "Dairy", order = 1)
        val category2 = Category(id = "2", name = "Dairy", order = 1)
        assertNotEquals(category1.id, category2.id)
    }

    @Test
    fun `category properties are correctly assigned`() {
        val category = Category(id = "test-id", name = "Produce", order = 5)
        assertEquals("test-id", category.id)
        assertEquals("Produce", category.name)
        assertEquals(5, category.order)
    }

    @Test
    fun `category with empty id is valid`() {
        val category = Category(id = "", name = "Test", order = 1)
        assertEquals("", category.id)
    }

    @Test
    fun `category order can be negative`() {
        val category = Category(id = "1", name = "Test", order = -1)
        assertEquals(-1, category.order)
    }

    @Test
    fun `category order can be zero`() {
        val category = Category(id = "1", name = "Test", order = 0)
        assertEquals(0, category.order)
    }
}
