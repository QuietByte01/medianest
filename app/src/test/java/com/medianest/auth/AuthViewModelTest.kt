package com.medianest.auth

import com.medianest.data.auth.AuthRepository
import com.medianest.data.auth.AuthResult
import com.medianest.data.auth.AuthState
import com.medianest.data.auth.User
import com.medianest.data.settings.SettingsManager
import com.medianest.data.sync.CloudSyncManager
import com.medianest.ui.auth.AuthMode
import com.medianest.ui.auth.AuthViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepo = mockk<AuthRepository>(relaxed = true)
    private val syncManager = mockk<CloudSyncManager>(relaxed = true)
    private val settingsManager = mockk<SettingsManager>(relaxed = true)

    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.Unauthenticated)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { authRepo.authState } returns authStateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `invalid email shows error message`() = runTest {
        val viewModel = AuthViewModel(authRepo, syncManager, settingsManager)
        viewModel.email = "invalid-email"
        viewModel.password = "123456"

        viewModel.submitEmailAuth()

        assertNotNull(viewModel.errorMessage)
        assertEquals("Please enter a valid email address", viewModel.errorMessage)
    }

    @Test
    fun `short password on registration shows error message`() = runTest {
        val viewModel = AuthViewModel(authRepo, syncManager, settingsManager)
        viewModel.authMode = AuthMode.REGISTER
        viewModel.email = "user@medianest.app"
        viewModel.password = "123"

        viewModel.submitEmailAuth()

        assertNotNull(viewModel.errorMessage)
        assertEquals("Password must be at least 6 characters", viewModel.errorMessage)
    }

    @Test
    fun `valid login calls repository and triggers cloud sync`() = runTest {
        val mockUser = User(uid = "user123", email = "test@medianest.app")
        coEvery { authRepo.loginWithEmail(any(), any()) } returns AuthResult.Success(mockUser)

        val viewModel = AuthViewModel(authRepo, syncManager, settingsManager)
        viewModel.authMode = AuthMode.LOGIN
        viewModel.email = "test@medianest.app"
        viewModel.password = "password123"

        viewModel.submitEmailAuth()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { authRepo.loginWithEmail("test@medianest.app", "password123") }
        coVerify { syncManager.syncUserData(mockUser) }
    }

    @Test
    fun `continue as guest sets guest mode in settings`() = runTest {
        val viewModel = AuthViewModel(authRepo, syncManager, settingsManager)

        viewModel.continueAsGuest()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { authRepo.continueAsGuest() }
        coVerify { settingsManager.setGuestMode(true) }
    }
}
