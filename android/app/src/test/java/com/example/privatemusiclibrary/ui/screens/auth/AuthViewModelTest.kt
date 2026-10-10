package com.example.privatemusiclibrary.ui.screens.auth

import com.example.privatemusiclibrary.data.auth.AuthTokenStore
import com.example.privatemusiclibrary.data.network.CreateInvitationRequest
import com.example.privatemusiclibrary.data.network.InvitationResponse
import com.example.privatemusiclibrary.data.network.LoginRequest
import com.example.privatemusiclibrary.data.network.MixtapeApi
import com.example.privatemusiclibrary.data.network.PlaylistSyncPayload
import com.example.privatemusiclibrary.data.network.RefreshRequest
import com.example.privatemusiclibrary.data.network.RegisterRequest
import com.example.privatemusiclibrary.data.network.TokenResponse
import com.example.privatemusiclibrary.data.network.TrackSyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeApi: FakeMixtapeApi
    private lateinit var fakeTokenStore: FakeAuthTokenStore
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeApi = FakeMixtapeApi()
        fakeTokenStore = FakeAuthTokenStore()
        viewModel = AuthViewModel(fakeApi, fakeTokenStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun login_normalizesEmail_andPreservesPasswordExactly() = runTest {
        fakeApi.loginResponse = Response.success(TokenResponse("access_123", "refresh_123"))

        // Given email with leading/trailing whitespace and mixed case
        val rawEmail = "  User.Name@Example.COM  "
        val exactPassword = "  P@ssw0rd!#$  " // whitespace in password must NOT be trimmed

        viewModel.login(rawEmail, exactPassword)
        testDispatcher.scheduler.advanceUntilIdle()

        val capturedRequest = fakeApi.lastLoginRequest
        assertEquals("user.name@example.com", capturedRequest?.email)
        assertEquals("  P@ssw0rd!#$  ", capturedRequest?.password)
        assertEquals("Android", capturedRequest?.device)
        assertEquals("Android", capturedRequest?.platform)

        assertEquals(AuthState.Authenticated, viewModel.authState.value)
        assertEquals("access_123", fakeTokenStore.getAccessToken())
        assertEquals("refresh_123", fakeTokenStore.getRefreshToken())
        assertNull(viewModel.uiError.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun login_handles401_withInvalidCredentialsMessage() = runTest {
        val errorBody = "{\"error\":\"invalid credentials\"}".toResponseBody("application/json".toMediaTypeOrNull())
        fakeApi.loginResponse = Response.error(401, errorBody)

        viewModel.login("user@test.com", "wrong_pwd")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Invalid email or password", viewModel.uiError.value)
        assertEquals(AuthState.Unauthenticated, viewModel.authState.value)
        assertNull(fakeTokenStore.getAccessToken())
    }

    @Test
    fun login_handles429_withRateLimitMessage() = runTest {
        val errorBody = "{\"error\":\"rate limit exceeded\"}".toResponseBody("application/json".toMediaTypeOrNull())
        fakeApi.loginResponse = Response.error(429, errorBody)

        viewModel.login("user@test.com", "pwd")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Too many login attempts. Please try again in a few minutes.", viewModel.uiError.value)
        assertEquals(AuthState.Unauthenticated, viewModel.authState.value)
    }

    @Test
    fun login_handles400_withBadRequestMessage() = runTest {
        val errorBody = "{\"error\":\"bad request\"}".toResponseBody("application/json".toMediaTypeOrNull())
        fakeApi.loginResponse = Response.error(400, errorBody)

        viewModel.login("user@test.com", "pwd")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Invalid request format. Please check your credentials.", viewModel.uiError.value)
        assertEquals(AuthState.Unauthenticated, viewModel.authState.value)
    }

    @Test
    fun login_handles500_withServerUnavailableMessage() = runTest {
        val errorBody = "{\"error\":\"internal server error\"}".toResponseBody("application/json".toMediaTypeOrNull())
        fakeApi.loginResponse = Response.error(500, errorBody)

        viewModel.login("user@test.com", "pwd")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Mixtape server is temporarily unavailable. Your local library still works.", viewModel.uiError.value)
        assertEquals(AuthState.Unauthenticated, viewModel.authState.value)
    }

    @Test
    fun login_handlesNetworkException_gracefully() = runTest {
        fakeApi.networkException = java.net.ConnectException("Connection refused")

        viewModel.login("user@test.com", "pwd")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Couldn't reach the Mixtape server. Please try again.", viewModel.uiError.value)
        assertEquals(AuthState.Unauthenticated, viewModel.authState.value)
    }

    @Test
    fun checkSession_authenticatesWhenTokenExists() {
        fakeTokenStore.save("existing_token", "existing_refresh")
        viewModel.checkSession()
        assertEquals(AuthState.Authenticated, viewModel.authState.value)
    }

    @Test
    fun checkSession_unauthenticatedWhenTokenMissing() {
        fakeTokenStore.clear()
        viewModel.checkSession()
        assertEquals(AuthState.Unauthenticated, viewModel.authState.value)
    }
}

class FakeAuthTokenStore : AuthTokenStore(null) {
    private var accessToken: String? = null
    private var refreshToken: String? = null

    override fun save(accessToken: String, refreshToken: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
    }

    override fun getAccessToken(): String? = accessToken

    override fun getRefreshToken(): String? = refreshToken

    override fun clear() {
        accessToken = null
        refreshToken = null
    }
}

class FakeMixtapeApi : MixtapeApi {
    var lastLoginRequest: LoginRequest? = null
    var loginResponse: Response<TokenResponse>? = null
    var networkException: Exception? = null

    override suspend fun login(request: LoginRequest): Response<TokenResponse> {
        lastLoginRequest = request
        networkException?.let { throw it }
        return loginResponse ?: Response.error(
            500,
            "{}".toResponseBody("application/json".toMediaTypeOrNull())
        )
    }

    override suspend fun register(request: RegisterRequest): Response<Unit> {
        networkException?.let { throw it }
        return Response.success(Unit)
    }

    override suspend fun createInvitation(request: CreateInvitationRequest): Response<InvitationResponse> {
        return Response.success(InvitationResponse("id", "token"))
    }

    override suspend fun refresh(request: RefreshRequest): Response<TokenResponse> {
        return Response.success(TokenResponse("access", "refresh"))
    }

    override suspend fun logout(request: RefreshRequest): Response<Unit> {
        return Response.success(Unit)
    }

    override suspend fun syncTracks(tracks: List<TrackSyncPayload>): Response<List<TrackSyncPayload>> {
        return Response.success(emptyList())
    }

    override suspend fun syncPlaylists(playlists: List<PlaylistSyncPayload>): Response<Unit> {
        return Response.success(Unit)
    }
}
