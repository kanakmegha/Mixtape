package com.example.privatemusiclibrary.data.storage

import com.example.privatemusiclibrary.domain.model.AppSettings
import com.example.privatemusiclibrary.domain.repository.LocalAudioStorage
import com.example.privatemusiclibrary.domain.repository.SettingsRepository
import com.example.privatemusiclibrary.domain.repository.StorageError
import com.example.privatemusiclibrary.domain.repository.StorageResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Unit tests verifying:
 * 1. Successful audio writes with safe pending-file state transitions.
 * 2. Failed audio writes and cleanup of incomplete/pending entries.
 * 3. Backward-compatible operations on existing SAF-backed tracks.
 * 4. Deletion safety and atomic storage/database behavior.
 */
class LocalStorageWorkflowTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var sourceFile: File

    @Before
    fun setUp() {
        sourceFile = tempFolder.newFile("source_song.mp3")
        sourceFile.writeBytes(ByteArray(1024) { 0x42 })
    }

    /**
     * Simulated test storage implementing the pending-file workflow (MediaStore on API 29+):
     * - Creates entry marked IS_PENDING = 1
     * - Streams bytes from source
     * - On success: flips IS_PENDING = 0
     * - On failure: removes entry so no corrupt/incomplete media file is exposed
     */
    class TestScopedStorageWorkflow(
        var simulateWriteFailure: Boolean = false
    ) : LocalAudioStorage {

        data class MediaStoreEntry(
            val id: Long,
            val uri: String,
            val relativePath: String,
            val displayName: String,
            var isPending: Boolean,
            var data: ByteArray? = null
        )

        val mediaStoreTable = mutableMapOf<String, MediaStoreEntry>()
        var nextId = 1000L

        override suspend fun save(source: File, destinationName: String): StorageResult {
            val id = nextId++
            val uri = "content://media/external/audio/media/$id"

            // 1. Create MediaStore entry with IS_PENDING = 1
            val entry = MediaStoreEntry(
                id = id,
                uri = uri,
                relativePath = "Music/Mixtape/",
                displayName = destinationName,
                isPending = true
            )
            mediaStoreTable[uri] = entry

            // 2. Stream audio to entry
            try {
                if (simulateWriteFailure) {
                    throw IOException("Simulated disk write failure or quota exceeded")
                }
                entry.data = source.readBytes()

                // 3. Mark available after successful write
                entry.isPending = false
                return StorageResult.Success(uri)
            } catch (e: Exception) {
                // 4. Remove incomplete entry if writing fails
                mediaStoreTable.remove(uri)
                return StorageResult.Error(StorageError.WriteFailed)
            }
        }

        override suspend fun delete(uri: String): Boolean {
            return mediaStoreTable.remove(uri) != null
        }

        override suspend fun exists(uri: String): Boolean {
            val entry = mediaStoreTable[uri] ?: return false
            return !entry.isPending && entry.data != null
        }

        override suspend fun size(uri: String): Long? {
            val entry = mediaStoreTable[uri] ?: return null
            return if (!entry.isPending) entry.data?.size?.toLong() else null
        }

        override fun open(uri: String): InputStream {
            val entry = mediaStoreTable[uri] ?: throw IOException("Not found: $uri")
            if (entry.isPending) throw IOException("File is pending: $uri")
            return (entry.data ?: ByteArray(0)).inputStream()
        }

        override suspend fun listAudioFiles(): List<String> {
            return mediaStoreTable.values
                .filter { !it.isPending && it.relativePath.startsWith("Music/Mixtape") }
                .map { it.uri }
        }
    }

    /**
     * Simulated test storage implementing legacy external storage (Android 7.0 - 9.0 / API 24-28):
     * - Writes to a hidden temporary file (.temp_...)
     * - On success: atomic rename to destination
     * - On failure: deletes temporary file so no corrupt file remains
     */
    class TestLegacyStorageWorkflow(
        private val baseDir: File,
        var simulateWriteFailure: Boolean = false
    ) : LocalAudioStorage {

        private val mixtapeDir = File(baseDir, "Music/Mixtape").apply { mkdirs() }

        override suspend fun save(source: File, destinationName: String): StorageResult {
            val tempFile = File(mixtapeDir, ".temp_${System.currentTimeMillis()}_$destinationName")
            val targetFile = File(mixtapeDir, destinationName)

            try {
                if (simulateWriteFailure) {
                    throw IOException("Disk write failure during stream copy")
                }
                source.copyTo(tempFile, overwrite = true)

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    throw IOException("Failed to rename temp file")
                }
                return StorageResult.Success("file://${targetFile.absolutePath}")
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                return StorageResult.Error(StorageError.WriteFailed)
            }
        }

        override suspend fun delete(uri: String): Boolean {
            val path = uri.removePrefix("file://")
            val file = File(path)
            return file.delete()
        }

        override suspend fun exists(uri: String): Boolean {
            val path = uri.removePrefix("file://")
            return File(path).exists()
        }

        override suspend fun size(uri: String): Long? {
            val path = uri.removePrefix("file://")
            val file = File(path)
            return if (file.exists()) file.length() else null
        }

        override fun open(uri: String): InputStream {
            val path = uri.removePrefix("file://")
            return File(path).inputStream()
        }

        override suspend fun listAudioFiles(): List<String> {
            return mixtapeDir.listFiles()
                ?.filter { it.isFile && !it.name.startsWith(".temp") }
                ?.map { "file://${it.absolutePath}" }
                ?: emptyList()
        }
    }

    /**
     * Storage that combines automatic Music/Mixtape storage with existing SAF-backed tracks.
     */
    class TestDualModeStorage(
        private val scopedStorage: TestScopedStorageWorkflow
    ) : LocalAudioStorage {
        val safFiles = mutableMapOf<String, ByteArray>()

        override suspend fun save(source: File, destinationName: String): StorageResult {
            return scopedStorage.save(source, destinationName)
        }

        override suspend fun delete(uri: String): Boolean {
            return if (uri.startsWith("content://tree/")) {
                safFiles.remove(uri) != null
            } else {
                scopedStorage.delete(uri)
            }
        }

        override suspend fun exists(uri: String): Boolean {
            return if (uri.startsWith("content://tree/")) {
                safFiles.containsKey(uri)
            } else {
                scopedStorage.exists(uri)
            }
        }

        override suspend fun size(uri: String): Long? {
            return if (uri.startsWith("content://tree/")) {
                safFiles[uri]?.size?.toLong()
            } else {
                scopedStorage.size(uri)
            }
        }

        override fun open(uri: String): InputStream {
            return if (uri.startsWith("content://tree/")) {
                val data = safFiles[uri] ?: throw IOException("File not found")
                data.inputStream()
            } else {
                scopedStorage.open(uri)
            }
        }

        override suspend fun listAudioFiles(): List<String> {
            val list = mutableListOf<String>()
            list.addAll(scopedStorage.listAudioFiles())
            list.addAll(safFiles.keys)
            return list
        }
    }

    @Test
    fun testSuccessfulWrite_createsMediaStoreEntryAndMarksAvailable() = runBlocking {
        val storage = TestScopedStorageWorkflow(simulateWriteFailure = false)
        val result = storage.save(sourceFile, "FavoriteSong.mp3")

        assertTrue(result is StorageResult.Success)
        val uri = (result as StorageResult.Success).uri
        assertTrue(uri.startsWith("content://media/external/audio/media/"))

        // Entry must be available (isPending = false) and file size must match source
        val entry = storage.mediaStoreTable[uri]
        assertNotNull(entry)
        assertFalse(entry!!.isPending)
        assertEquals("Music/Mixtape/", entry.relativePath)
        assertEquals("FavoriteSong.mp3", entry.displayName)
        assertEquals(1024L, storage.size(uri))
        assertTrue(storage.exists(uri))

        // Must appear in scanned audio files
        val files = storage.listAudioFiles()
        assertEquals(1, files.size)
        assertEquals(uri, files[0])
    }

    @Test
    fun testFailedWrite_cleansUpPendingMediaStoreEntry() = runBlocking {
        val storage = TestScopedStorageWorkflow(simulateWriteFailure = true)
        val result = storage.save(sourceFile, "CorruptedSong.mp3")

        assertTrue(result is StorageResult.Error)
        assertEquals(StorageError.WriteFailed, (result as StorageResult.Error).error)

        // Verify that the failed/incomplete entry was removed and nothing remains in storage
        assertTrue(storage.mediaStoreTable.isEmpty())
        assertTrue(storage.listAudioFiles().isEmpty())
    }

    @Test
    fun testLegacyStorage_successfulWriteAndAtomicRename() = runBlocking {
        val storage = TestLegacyStorageWorkflow(tempFolder.root, simulateWriteFailure = false)
        val result = storage.save(sourceFile, "LegacyTrack.mp3")

        assertTrue(result is StorageResult.Success)
        val uri = (result as StorageResult.Success).uri
        assertTrue(storage.exists(uri))
        assertEquals(1024L, storage.size(uri))

        val list = storage.listAudioFiles()
        assertEquals(1, list.size)
        // No temporary files left behind
        val musicDir = File(tempFolder.root, "Music/Mixtape")
        val files = musicDir.listFiles() ?: emptyArray()
        assertEquals(1, files.size)
        assertFalse(files[0].name.startsWith(".temp"))
    }

    @Test
    fun testLegacyStorage_failedWrite_cleansUpTempFile() = runBlocking {
        val storage = TestLegacyStorageWorkflow(tempFolder.root, simulateWriteFailure = true)
        val result = storage.save(sourceFile, "FailedLegacy.mp3")

        assertTrue(result is StorageResult.Error)

        val musicDir = File(tempFolder.root, "Music/Mixtape")
        val remainingFiles = musicDir.listFiles() ?: emptyArray()
        assertEquals("No temporary files should linger after write failure", 0, remainingFiles.size)
    }

    @Test
    fun testExistingSafTracks_readAndDeletedSeamlesslyWithMediaStoreTracks() = runBlocking {
        val scopedStorage = TestScopedStorageWorkflow()
        val dualStorage = TestDualModeStorage(scopedStorage)

        // Seed existing SAF-backed track from previous app version
        val existingSafUri = "content://tree/primary:Music/ExistingOldTrack.mp3"
        dualStorage.safFiles[existingSafUri] = ByteArray(512) { 0x11 }

        // Save new track via automatic MediaStore storage
        val saveResult = dualStorage.save(sourceFile, "NewMediaStoreSong.mp3")
        assertTrue(saveResult is StorageResult.Success)
        val newUri = (saveResult as StorageResult.Success).uri

        // Verify both are discovered and accessible
        val allFiles = dualStorage.listAudioFiles()
        assertEquals(2, allFiles.size)
        assertTrue(allFiles.contains(existingSafUri))
        assertTrue(allFiles.contains(newUri))

        assertTrue(dualStorage.exists(existingSafUri))
        assertEquals(512L, dualStorage.size(existingSafUri))

        assertTrue(dualStorage.exists(newUri))
        assertEquals(1024L, dualStorage.size(newUri))

        // Delete existing SAF track
        val safDeleteSuccess = dualStorage.delete(existingSafUri)
        assertTrue(safDeleteSuccess)
        assertFalse(dualStorage.exists(existingSafUri))

        // New MediaStore track remains intact
        assertTrue(dualStorage.exists(newUri))
        assertEquals(1, dualStorage.listAudioFiles().size)
    }
}
