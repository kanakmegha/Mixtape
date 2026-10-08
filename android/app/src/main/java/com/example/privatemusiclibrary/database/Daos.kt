package com.example.privatemusiclibrary.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: TrackEntity): Long

    @Update
    suspend fun update(track: TrackEntity): Int

    @Delete
    suspend fun delete(track: TrackEntity): Int

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE sourceId = :sourceId LIMIT 1")
    suspend fun getBySourceId(sourceId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE localUri = :localUri LIMIT 1")
    suspend fun getByLocalUri(localUri: String): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun observeFavorites(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<TrackEntity>

    @Query("SELECT * FROM tracks")
    suspend fun getAll(): List<TrackEntity>
}

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity): Int

    @Delete
    suspend fun delete(folder: FolderEntity): Int

    @Query("SELECT * FROM folders")
    fun observeAll(): Flow<List<FolderEntity>>
}

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity): Int

    @Delete
    suspend fun delete(playlist: PlaylistEntity): Int

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>
}

@Dao
interface PlaylistTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlistTrack: PlaylistTrackEntity): Long

    @Delete
    suspend fun delete(playlistTrack: PlaylistTrackEntity): Int

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: String, trackId: String): Int

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updatePosition(playlistId: String, trackId: String, position: Int): Int

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getTrackCount(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun observeTrackCount(playlistId: String): Flow<Int>

    @Query("SELECT playlistId FROM playlist_tracks WHERE trackId = :trackId")
    fun observePlaylistsForTrack(trackId: String): Flow<List<String>>

    @Query("SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON t.id = pt.trackId WHERE pt.playlistId = :playlistId ORDER BY pt.position ASC")
    fun observeTracksForPlaylist(playlistId: String): Flow<List<TrackEntity>>
}

@Dao
interface PlaybackStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: PlaybackStateEntity): Long

    @Query("SELECT * FROM playback_state WHERE id = 1")
    fun observeState(): Flow<List<PlaybackStateEntity>>

    @Query("SELECT * FROM playback_state WHERE id = 1")
    suspend fun getState(): PlaybackStateEntity?
}

@Dao
interface ImportJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: ImportJobEntity): Long

    @Update
    suspend fun update(job: ImportJobEntity): Int

    @Query("SELECT * FROM import_jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ImportJobEntity>>

    @Query("SELECT * FROM import_jobs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ImportJobEntity?
}

@Dao
interface AppSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: AppSettingsEntity): Long

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observeSettings(): Flow<List<AppSettingsEntity>>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettingsEntity?
}
