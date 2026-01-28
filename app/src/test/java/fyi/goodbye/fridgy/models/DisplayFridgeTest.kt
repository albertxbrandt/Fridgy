package fyi.goodbye.fridgy.models

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [DisplayFridge] model.
 */
class DisplayFridgeTest {
    @Test
    fun `displayFridge properties are correctly assigned`() {
        val displayFridge =
            DisplayFridge(
                id = "fridge-1",
                name = "Kitchen Fridge",
                type = "fridge",
                householdId = "household-1",
                createdByUid = "user-123",
                creatorDisplayName = "John Doe",
                createdAt = 1234567890L
            )

        assertEquals("fridge-1", displayFridge.id)
        assertEquals("Kitchen Fridge", displayFridge.name)
        assertEquals("fridge", displayFridge.type)
        assertEquals("household-1", displayFridge.householdId)
        assertEquals("user-123", displayFridge.createdByUid)
        assertEquals("John Doe", displayFridge.creatorDisplayName)
        assertEquals(1234567890L, displayFridge.createdAt)
    }

    @Test
    fun `displayFridge default values are correct`() {
        val displayFridge = DisplayFridge()

        assertEquals("", displayFridge.id)
        assertEquals("", displayFridge.name)
        assertEquals("fridge", displayFridge.type)
        assertEquals("", displayFridge.householdId)
        assertEquals("", displayFridge.createdByUid)
        assertEquals("Unknown", displayFridge.creatorDisplayName)
    }

    @Test
    fun `displayFridge with freezer type`() {
        val displayFridge =
            DisplayFridge(
                id = "fridge-2",
                name = "Basement Freezer",
                type = "freezer",
                householdId = "household-1",
                createdByUid = "user-123",
                creatorDisplayName = "John Doe",
                createdAt = 0L
            )

        assertEquals("freezer", displayFridge.type)
        assertEquals("Basement Freezer", displayFridge.name)
    }

    @Test
    fun `displayFridge with pantry type`() {
        val displayFridge =
            DisplayFridge(
                id = "fridge-3",
                name = "Kitchen Pantry",
                type = "pantry",
                householdId = "household-2",
                createdByUid = "user-456",
                creatorDisplayName = "Jane Doe",
                createdAt = 0L
            )

        assertEquals("pantry", displayFridge.type)
        assertEquals("Kitchen Pantry", displayFridge.name)
        assertEquals("household-2", displayFridge.householdId)
    }
}
