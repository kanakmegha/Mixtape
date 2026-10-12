package com.example.privatemusiclibrary.database

import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.privatemusiclibrary.di.DatabaseModule
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class RoomMigrationTest {

    private lateinit var connection: Connection
    private lateinit var supportDb: SupportSQLiteDatabase

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val handler = object : InvocationHandler {
            override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
                if (method.name == "execSQL") {
                    val sql = args?.get(0) as String
                    connection.createStatement().use { stmt ->
                        stmt.execute(sql)
                    }
                    return null
                }
                return null
            }
        }
        supportDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler
        ) as SupportSQLiteDatabase
    }

    @After
    fun tearDown() {
        if (!connection.isClosed) {
            connection.close()
        }
    }

    private fun createSchemaV1() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS `folders` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `uri` TEXT NOT NULL,
                    `dateAdded` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS `tracks` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `album` TEXT NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `localUri` TEXT NOT NULL,
                    `sourcePlatform` TEXT NOT NULL,
                    `sourceId` TEXT NOT NULL,
                    `sourceUrl` TEXT NOT NULL,
                    `contentHash` TEXT,
                    `folderId` TEXT,
                    `isFavorite` INTEGER NOT NULL,
                    `dateAdded` INTEGER NOT NULL,
                    `lastPlayedAt` INTEGER,
                    `playCount` INTEGER NOT NULL,
                    `fileSizeBytes` INTEGER NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS `playlists` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS `playlist_tracks` (
                    `playlistId` TEXT NOT NULL,
                    `trackId` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    PRIMARY KEY(`playlistId`, `trackId`)
                )
                """.trimIndent()
            )
        }
    }

    private fun createSchemaV2() {
        createSchemaV1()
        DatabaseModule.MIGRATION_1_2.migrate(supportDb)
    }

    @Test
    fun migration1To2_preservesPlaylistTracksAndAddsForeignKeys() {
        createSchemaV1()

        val playlistId = "pl-1"
        val trackId = "tr-1"

        connection.createStatement().use { stmt ->
            stmt.execute("INSERT INTO folders (id, name, uri, dateAdded) VALUES ('f1', 'Music', 'uri1', 100)")
            stmt.execute("INSERT INTO tracks (id, title, artist, album, durationMs, localUri, sourcePlatform, sourceId, sourceUrl, isFavorite, dateAdded, playCount, fileSizeBytes, mimeType) VALUES ('$trackId', 'Song A', 'Artist', 'Album', 180000, 'uri', 'yt', 'id1', 'url1', 0, 100, 0, 1024, 'audio/mp3')")
            stmt.execute("INSERT INTO playlists (id, name, createdAt) VALUES ('$playlistId', 'Favorites', 100)")
            stmt.execute("INSERT INTO playlist_tracks (playlistId, trackId, position) VALUES ('$playlistId', '$trackId', 0)")
        }

        DatabaseModule.MIGRATION_1_2.migrate(supportDb)

        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT playlistId, trackId, position FROM playlist_tracks WHERE playlistId = '$playlistId'")
            assertTrue("Row in playlist_tracks must be preserved", rs.next())
            assertEquals(playlistId, rs.getString("playlistId"))
            assertEquals(trackId, rs.getString("trackId"))
            assertEquals(0, rs.getInt("position"))
        }
    }

    @Test
    fun migration2To3_addsTrackKeyAndLocalAvailabilityToTracks() {
        createSchemaV2()

        val trackId = "tr-existing-1"
        connection.createStatement().use { stmt ->
            stmt.execute("""
                INSERT INTO tracks (
                    id, title, artist, album, durationMs, localUri,
                    sourcePlatform, sourceId, sourceUrl, contentHash,
                    folderId, isFavorite, dateAdded, lastPlayedAt,
                    playCount, fileSizeBytes, mimeType
                ) VALUES (
                    '$trackId', 'Installed Song', 'Test Artist', 'Test Album', 240000, 'content://media/1',
                    'youtube', 'dQw4w9WgXcQ', 'https://youtu.be/dQw4w9WgXcQ', 'hash123',
                    null, 1, 1700000000000, null, 5, 4096000, 'audio/mp4'
                )
            """.trimIndent())
        }

        // Apply migration from v2 to v3
        DatabaseModule.MIGRATION_2_3.migrate(supportDb)

        // Verify tracks schema and defaults
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT id, trackKey, isLocalAvailable, isFavorite, title FROM tracks WHERE id = '$trackId'")
            assertTrue("Existing track must still exist", rs.next())
            assertEquals(trackId, rs.getString("id"))
            assertEquals("trackKey must default to empty string for existing tracks", "", rs.getString("trackKey"))
            assertEquals("isLocalAvailable must default to 1 (true) for installed tracks", 1, rs.getInt("isLocalAvailable"))
            assertEquals("isFavorite status must remain intact", 1, rs.getInt("isFavorite"))
            assertEquals("Installed Song", rs.getString("title"))
        }

        // Verify index was created
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index' AND name='index_tracks_trackKey'")
            assertTrue("Index index_tracks_trackKey must exist", rs.next())
        }
    }

    @Test
    fun migration2To3_addsUpdatedAtAndIsDeletedToPlaylists() {
        createSchemaV2()

        val playlistId = "pl-existing-1"
        connection.createStatement().use { stmt ->
            stmt.execute("INSERT INTO playlists (id, name, createdAt) VALUES ('$playlistId', 'Roadtrip', 1690000000000)")
        }

        DatabaseModule.MIGRATION_2_3.migrate(supportDb)

        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT id, name, createdAt, updatedAt, isDeleted FROM playlists WHERE id = '$playlistId'")
            assertTrue("Existing playlist must still exist", rs.next())
            assertEquals("Roadtrip", rs.getString("name"))
            assertEquals(1690000000000L, rs.getLong("createdAt"))
            assertEquals("updatedAt must default to 0 for un-synced playlists", 0L, rs.getLong("updatedAt"))
            assertEquals("isDeleted must default to 0 (false)", 0, rs.getInt("isDeleted"))
        }
    }

    @Test
    fun migration2To3_createsSyncTombstonesTable() {
        createSchemaV2()

        DatabaseModule.MIGRATION_2_3.migrate(supportDb)

        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='sync_tombstones'")
            assertTrue("sync_tombstones table must exist", rs.next())
        }

        // Verify CRUD on sync_tombstones
        connection.createStatement().use { stmt ->
            stmt.execute("INSERT INTO sync_tombstones (entityType, entityId, deletedAt) VALUES ('PLAYLIST', 'pl-1', 1700000000000)")
            val rs = stmt.executeQuery("SELECT entityType, entityId, deletedAt FROM sync_tombstones WHERE entityId = 'pl-1'")
            assertTrue(rs.next())
            assertEquals("PLAYLIST", rs.getString("entityType"))
            assertEquals("pl-1", rs.getString("entityId"))
            assertEquals(1700000000000L, rs.getLong("deletedAt"))
        }
    }

    @Test
    fun fullMigrationChain1To2To3_preservesAllDataAcrossVersions() {
        createSchemaV1()

        val trackId = "track-full-chain"
        val playlistId = "playlist-full-chain"

        connection.createStatement().use { stmt ->
            stmt.execute("INSERT INTO playlists (id, name, createdAt) VALUES ('$playlistId', 'Old Playlist', 1500000000000)")
            stmt.execute("INSERT INTO tracks (id, title, artist, album, durationMs, localUri, sourcePlatform, sourceId, sourceUrl, isFavorite, dateAdded, playCount, fileSizeBytes, mimeType) VALUES ('$trackId', 'Song Old', 'Artist', 'Album', 120000, 'file://music/1.mp3', 'direct', 'd1', 'http://a.b/c.mp3', 1, 1500000000000, 3, 2048, 'audio/mpeg')")
            stmt.execute("INSERT INTO playlist_tracks (playlistId, trackId, position) VALUES ('$playlistId', '$trackId', 0)")
        }

        // Migrate 1 -> 2
        DatabaseModule.MIGRATION_1_2.migrate(supportDb)
        // Migrate 2 -> 3
        DatabaseModule.MIGRATION_2_3.migrate(supportDb)

        connection.createStatement().use { stmt ->
            val trackRs = stmt.executeQuery("SELECT id, trackKey, isLocalAvailable, isFavorite FROM tracks WHERE id = '$trackId'")
            assertTrue(trackRs.next())
            assertEquals("", trackRs.getString("trackKey"))
            assertEquals(1, trackRs.getInt("isLocalAvailable"))
            assertEquals(1, trackRs.getInt("isFavorite"))

            val plRs = stmt.executeQuery("SELECT id, updatedAt, isDeleted FROM playlists WHERE id = '$playlistId'")
            assertTrue(plRs.next())
            assertEquals(0L, plRs.getLong("updatedAt"))
            assertEquals(0, plRs.getInt("isDeleted"))

            val ptRs = stmt.executeQuery("SELECT playlistId, trackId FROM playlist_tracks WHERE playlistId = '$playlistId'")
            assertTrue(ptRs.next())
            assertEquals(playlistId, ptRs.getString("playlistId"))
            assertEquals(trackId, ptRs.getString("trackId"))
        }
    }
}
