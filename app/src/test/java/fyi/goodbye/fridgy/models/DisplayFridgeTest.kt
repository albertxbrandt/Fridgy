package fyi.goodbye.fridgy.models

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DisplayFridgeTest {
    @Test
    fun `displayFridge properties are correctly assigned`() {
        val users =
            listOf(
                UserProfile(uid = "user1", username = "user1")
            )
        val displayFridge =
            DisplayFridge(
                id = "fridge-1",
                name = "Kitchen Fridge",
                createdByUid = "user-123",
                creatorDisplayName = "John Doe",
                memberUsers = users,
                pendingInviteUsers = emptyList(),
                createdAt = 1234567890L
            )

        assertEquals("fridge-1", displayFridge.id)
        assertEquals("Kitchen Fridge", displayFridge.name)
        assertEquals("user-123", displayFridge.createdByUid)
        assertEquals("John Doe", displayFridge.creatorDisplayName)
        assertEquals(1, displayFridge.memberUsers.size)
        assertEquals(0, displayFridge.pendingInviteUsers.size)
        assertEquals(1234567890L, displayFridge.createdAt)
    }

    @Test
    fun `displayFridge with multiple members`() {
        val users =
            listOf(
                UserProfile(uid = "user1", username = "user1"),
                UserProfile(uid = "user2", username = "user2"),
                UserProfile(uid = "user3", username = "user3")
            )
        val displayFridge =
            DisplayFridge(
                id = "fridge-1",
                name = "Kitchen Fridge",
                createdByUid = "user1",
                creatorDisplayName = "User One",
                memberUsers = users,
                pendingInviteUsers = emptyList(),
                createdAt = 0L
            )

        assertEquals(3, displayFridge.memberUsers.size)
    }

    @Test
    fun `displayFridge with pending invites`() {
        val invites =
            listOf(
                UserProfile(uid = "user4", username = "user4")
            )
        val displayFridge =
            DisplayFridge(
                id = "fridge-1",
                name = "Kitchen Fridge",
                createdByUid = "user1",
                creatorDisplayName = "User One",
                memberUsers = emptyList(),
                pendingInviteUsers = invites,
                createdAt = 0L
            )

        assertEquals(1, displayFridge.pendingInviteUsers.size)
        assertEquals("user4", displayFridge.pendingInviteUsers[0].uid)
    }

    @Test
    fun `displayFridge with empty members list`() {
        val displayFridge =
            DisplayFridge(
                id = "fridge-1",
                name = "Empty Fridge",
                createdByUid = "user1",
                creatorDisplayName = "User One",
                memberUsers = emptyList(),
                pendingInviteUsers = emptyList(),
                createdAt = 0L
            )

        assertTrue(displayFridge.memberUsers.isEmpty())
        assertTrue(displayFridge.pendingInviteUsers.isEmpty())
    }
}
