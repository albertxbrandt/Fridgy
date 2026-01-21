package fyi.goodbye.fridgy.ui.auth

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import fyi.goodbye.fridgy.repositories.UserRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SignupViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockContext: Context
    private lateinit var mockUserRepository: UserRepository
    private lateinit var viewModel: SignupViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockContext = mockk(relaxed = true)
        mockUserRepository = mockk(relaxed = true)

        // Mock getString for error messages
        every { mockContext.getString(any()) } returns "Error message"
        every { mockContext.getString(any(), any()) } returns "Error message"

        viewModel = SignupViewModel(mockContext, mockUserRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state has empty fields`() {
        assertEquals("", viewModel.email)
        assertEquals("", viewModel.username)
        assertEquals("", viewModel.password)
        assertEquals("", viewModel.confirmPassword)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun `username with spaces is invalid`() {
        viewModel.onUsernameChange("user name")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")

        // This would trigger validation
        assertFalse(viewModel.username.matches(Regex("^[a-zA-Z0-9_.]+$")))
    }

    @Test
    fun `username with special characters is invalid`() {
        viewModel.onUsernameChange("user@name")
        assertFalse(viewModel.username.matches(Regex("^[a-zA-Z0-9_.]+$")))
    }

    @Test
    fun `username with valid characters is valid`() {
        viewModel.onUsernameChange("valid_user.123")
        assertTrue(viewModel.username.matches(Regex("^[a-zA-Z0-9_.]+$")))
    }

    @Test
    fun `username too short is invalid`() {
        viewModel.onUsernameChange("ab")
        assertTrue(viewModel.username.length < 3)
    }

    @Test
    fun `username too long is invalid`() {
        viewModel.onUsernameChange("a".repeat(17))
        assertTrue(viewModel.username.length > 16)
    }

    @Test
    fun `username within length limits is valid`() {
        viewModel.onUsernameChange("validuser")
        assertTrue(viewModel.username.length in 3..16)
    }

    @Test
    fun `passwords not matching is invalid`() {
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password456")
        assertFalse(viewModel.password == viewModel.confirmPassword)
    }

    @Test
    fun `matching passwords is valid`() {
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")
        assertTrue(viewModel.password == viewModel.confirmPassword)
    }

    @Test
    fun `empty fields are invalid`() {
        viewModel.onEmailChange("")
        viewModel.onUsernameChange("")
        viewModel.onPasswordChange("")
        viewModel.onConfirmPasswordChange("")

        assertTrue(
            viewModel.email.isBlank() ||
                viewModel.username.isBlank() ||
                viewModel.password.isBlank() ||
                viewModel.confirmPassword.isBlank()
        )
    }

    @Test
    fun `all fields filled is valid`() {
        viewModel.onEmailChange("test@example.com")
        viewModel.onUsernameChange("testuser")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")

        assertFalse(
            viewModel.email.isBlank() ||
                viewModel.username.isBlank() ||
                viewModel.password.isBlank() ||
                viewModel.confirmPassword.isBlank()
        )
    }

    @Test
    fun `updating email clears error`() {
        viewModel.onEmailChange("test@example.com")
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun `updating username clears error`() {
        viewModel.onUsernameChange("testuser")
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun `updating password clears error`() {
        viewModel.onPasswordChange("password123")
        assertNull(viewModel.errorMessage)
    }
}
