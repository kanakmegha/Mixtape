package com.example.privatemusiclibrary.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface MixtapeApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<Unit>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    @POST("invitations")
    suspend fun createInvitation(
        @Body request: CreateInvitationRequest
    ): Response<InvitationResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<TokenResponse>

    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Response<Unit>

    @GET("sync")
    suspend fun getSyncState(@Query("since") since: Long? = null): Response<SyncResponse>

    @POST("sync")
    suspend fun sync(@Body request: SyncRequest): Response<SyncResponse>

    @POST("sync/tracks")
    suspend fun syncTracks(@Body tracks: List<TrackSyncPayload>): Response<List<TrackSyncPayload>>

    @POST("sync/playlists")
    suspend fun syncPlaylists(@Body playlists: List<PlaylistSyncPayload>): Response<Unit>
}
