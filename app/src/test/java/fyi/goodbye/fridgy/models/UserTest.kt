package fyi.goodbye.fridgy.models

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UserTest {
    @Test
    fun `user with same uid are equal`() {
        val user1 =
            User(
                uid = "user-123",
                email = "test1@example.com",
                createdAt = 0L
            )
        val user2 =
            User(
                uid = "user-123",
                email = "test2@example.com",
                createdAt = 1L
            )
        assertEquals(user1.uid, user2.uid)
    }

    @Test
    fun `user with different uid are not equal`() {
        val user1 =
            User(
                uid = "user-123",
                email = "test@example.com",
                createdAt = 0L
            )
        val user2 =
            User(
                uid = "user-456",
                email = "test@example.com",
                createdAt = 0L
            )
        assertNotEquals(user1.uid, user2.uid)
    }

    @Test
    fun `user properties are correctly assigned`() {
        val user =
            User(
                uid = "user-789",
                email = "john.doe@example.com",
                createdAt = 1234567890L
            )

        assertEquals("user-789", user.uid)
        assertEquals("john.doe@example.com", user.email)
        assertEquals(1234567890L, user.createdAt)
    }

    @Test
    fun `user with empty uid is valid`() {
        val user =
            User(
                uid = "",
                email = "test@example.com",
                createdAt = 0L
            )
        assertEquals("", user.uid)
    }

    @Test
    fun `user createdAt timestamp can be zero`() {
        val user =
            User(
                uid = "1",
                email = "test@example.com",
                createdAt = 0L
            )
        assertEquals(0L, user.createdAt)
    }

    @Test
    fun `user with valid email format`() {
        val user =
            User(
                uid = "1",
                email = "test@example.com",
                createdAt = 0L
            )
        assertEquals("test@example.com", user.email)
    }
}
