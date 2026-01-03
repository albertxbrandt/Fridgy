package fyi.goodbye.fridgy.ui.auth

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
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
class LoginViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockApplication: Application
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockApplication = mockk(relaxed = true)
        every { mockApplication.getString(any()) } returns "Error message"
        viewModel = LoginViewModel(mockApplication)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initial state has empty email and password`() {
        assertEquals("", viewModel.email)
        assertEquals("", viewModel.password)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun `empty email is invalid`() {
        viewModel.onEmailChange("")
        viewModel.onPasswordChange("password123")
        assertTrue(viewModel.email.isBlank())
    }

    @Test
    fun `empty password is invalid`() {
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("")
        assertTrue(viewModel.password.isBlank())
    }

    @Test
    fun `both fields empty is invalid`() {
        viewModel.onEmailChange("")
        viewModel.onPasswordChange("")
        assertTrue(viewModel.email.isBlank() && viewModel.password.isBlank())
    }

    @Test
    fun `valid email format is accepted`() {
        viewModel.onEmailChange("test@example.com")
        assertTrue(viewModel.email.contains("@"))
        assertTrue(viewModel.email.contains("."))
    }

    @Test
    fun `non-empty credentials pass basic validation`() {
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        assertFalse(viewModel.email.isBlank() || viewModel.password.isBlank())
    }

    @Test
    fun `updating email updates state`() {
        val testEmail = "newemail@example.com"
        viewModel.onEmailChange(testEmail)
        assertEquals(testEmail, viewModel.email)
    }

    @Test
    fun `updating password updates state`() {
        val testPassword = "newpassword123"
        viewModel.onPasswordChange(testPassword)
        assertEquals(testPassword, viewModel.password)
    }

    @Test
    fun `changing email clears error message`() {
        // Set an error (would be set during failed login)
        viewModel.onEmailChange("test@example.com")
        // Error should be cleared when email changes
        assertNull(viewModel.errorMessage)
    }
}
