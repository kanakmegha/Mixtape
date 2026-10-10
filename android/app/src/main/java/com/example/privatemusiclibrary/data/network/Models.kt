package com.example.privatemusiclibrary.data.network

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("invitation_token") val invitationToken: String
)

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("device") val device: String,
    @SerializedName("platform") val platform: String
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
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String?,
    @SerializedName("album") val album: String?,
    @SerializedName("duration") val duration: Int?,
    @SerializedName("source") val source: String?,
    @SerializedName("source_identifier") val sourceIdentifier: String?,
    @SerializedName("deleted") val deleted: Boolean
)

data class PlaylistSyncPayload(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("deleted") val deleted: Boolean,
    @SerializedName("tracks") val tracks: List<PlaylistTrackSyncPayload>
)

data class PlaylistTrackSyncPayload(
    @SerializedName("track_id") val trackId: String,
    @SerializedName("position") val position: Int
)

data class CreateInvitationRequest(
    @SerializedName("expires_in_days")
    val expiresInDays: Int = 7
)

data class InvitationResponse(
    @SerializedName("id") val id: String,
    @SerializedName("token") val token: String
)