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

data class PlaylistTrackSyncPayload(
    @SerializedName("id") val id: String? = null,
    @SerializedName("track_key") val trackKey: String = "",
    @SerializedName("track_id") val trackId: String = "",
    @SerializedName("position") val position: Int = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("artist") val artist: String? = null,
    @SerializedName("album") val album: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("deleted") val deleted: Boolean = false,
    @SerializedName("updated_at") val updatedAt: Long = 0L
) {
    fun getLogicalKey(): String = if (trackKey.isNotBlank()) trackKey else trackId
}

data class PlaylistSyncPayload(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("deleted") val deleted: Boolean = false,
    @SerializedName("updated_at") val updatedAt: Long = 0L,
    @SerializedName("tracks") val tracks: List<PlaylistTrackSyncPayload> = emptyList()
)

data class FavoriteSyncPayload(
    @SerializedName("track_key") val trackKey: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String? = null,
    @SerializedName("album") val album: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("is_favorite") val isFavorite: Boolean = true,
    @SerializedName("updated_at") val updatedAt: Long = 0L,
    @SerializedName("deleted") val deleted: Boolean = false
)

data class SyncRequest(
    @SerializedName("playlists") val playlists: List<PlaylistSyncPayload> = emptyList(),
    @SerializedName("favorites") val favorites: List<FavoriteSyncPayload> = emptyList(),
    @SerializedName("since") val since: Long = 0L
)

data class SyncResponse(
    @SerializedName("playlists") val playlists: List<PlaylistSyncPayload> = emptyList(),
    @SerializedName("favorites") val favorites: List<FavoriteSyncPayload> = emptyList(),
    @SerializedName("server_time") val serverTime: Long = 0L
)

data class CreateInvitationRequest(
    @SerializedName("expires_in_days")
    val expiresInDays: Int = 7
)

data class InvitationResponse(
    @SerializedName("id") val id: String,
    @SerializedName("token") val token: String
)