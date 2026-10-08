package com.example.privatemusiclibrary.data.network

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    val email: String,
    val password: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("invitation_token") val invitationToken: String
)

data class LoginRequest(
    val email: String,
    val password: String,
    val device: String,
    val platform: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class TrackSyncPayload(
    @SerializedName("track_id") val trackId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Int?,
    val source: String?,
    @SerializedName("source_identifier") val sourceIdentifier: String?,
    val deleted: Boolean
)

data class PlaylistSyncPayload(
    val id: String,
    val name: String,
    val deleted: Boolean,
    val tracks: List<PlaylistTrackSyncPayload>
)

data class PlaylistTrackSyncPayload(
    @SerializedName("track_id") val trackId: String,
    val position: Int
)
