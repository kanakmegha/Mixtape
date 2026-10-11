package com.example.privatemusiclibrary.data.extraction

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.yausername.youtubedl_android.YoutubeDL.UpdateChannel
import com.yausername.youtubedl_android.YoutubeDL.UpdateStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class YtDlpUpdateManagerTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var dummyContext: Context

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        dummyContext = object : ContextWrapper(null) {}
    }

    @Test
    fun updateSuccess_transitionsToSuccessStateAndPersistsTimestamp() = runTest {
        var callCount = 0
        val manager = YtDlpUpdateManager(
            context = dummyContext,
            updateInvoker = { _, channel ->
                assertEquals(UpdateChannel.STABLE, channel)
                callCount++
                UpdateStatus.DONE
            },
            versionProvider = { "2026.08.19" },
            prefs = fakePrefs
        )

        val result = manager.updateIfNecessary(force = true)

        assertTrue(result)
        assertEquals(1, callCount)
        val state = manager.updateState.value
        assertTrue("Expected Success state, but was $state", state is YtDlpUpdateState.Success)
        val successState = state as YtDlpUpdateState.Success
        assertEquals("2026.08.19", successState.version)
        assertEquals(UpdateStatus.DONE, successState.status)

        val savedTs = fakePrefs.getLong(YtDlpUpdateManager.KEY_LAST_SUCCESSFUL_UPDATE, 0L)
        assertTrue("Timestamp should have been persisted", savedTs > 0L)
    }

    @Test
    fun updateSuccess_withAlreadyUpToDate_transitionsToSuccessState() = runTest {
        val manager = YtDlpUpdateManager(
            context = dummyContext,
            updateInvoker = { _, _ -> UpdateStatus.ALREADY_UP_TO_DATE },
            versionProvider = { "2026.08.19" },
            prefs = fakePrefs
        )

        val result = manager.updateIfNecessary(force = true)

        assertTrue(result)
        val state = manager.updateState.value
        assertTrue("Expected Success state, but was $state", state is YtDlpUpdateState.Success)
        val successState = state as YtDlpUpdateState.Success
        assertEquals(UpdateStatus.ALREADY_UP_TO_DATE, successState.status)
        assertEquals("2026.08.19", successState.version)
        assertTrue(fakePrefs.getLong(YtDlpUpdateManager.KEY_LAST_SUCCESSFUL_UPDATE, 0L) > 0L)
    }

    @Test
    fun updateFailure_doesNotPersistTimestampAndPreservesFailureState() = runTest {
        val manager = YtDlpUpdateManager(
            context = dummyContext,
            updateInvoker = { _, _ -> throw IOException("Unable to resolve host: github.com") },
            versionProvider = { "2025.11.12" },
            prefs = fakePrefs
        )

        val result = manager.updateIfNecessary(force = false)

        assertFalse(result)
        val state = manager.updateState.value
        assertTrue("Expected Failed state, but was $state", state is YtDlpUpdateState.Failed)
        val failedState = state as YtDlpUpdateState.Failed
        assertTrue(failedState.canRetry)
        assertTrue(failedState.error.contains("github.com", ignoreCase = true) || failedState.error.contains("Network error", ignoreCase = true) || failedState.error.contains("resolve host", ignoreCase = true))

        // Timestamp must NEVER be persisted on failure
        val savedTs = fakePrefs.getLong(YtDlpUpdateManager.KEY_LAST_SUCCESSFUL_UPDATE, 0L)
        assertEquals(0L, savedTs)
    }

    @Test
    fun updateFailure_permitsLaterRetry() = runTest {
        var attempts = 0
        val manager = YtDlpUpdateManager(
            context = dummyContext,
            updateInvoker = { _, _ ->
                attempts++
                if (attempts == 1) {
                    throw IOException("Network timeout connecting to server")
                } else {
                    UpdateStatus.DONE
                }
            },
            versionProvider = {
                if (attempts == 1) "2025.11.12" else "2026.08.19"
            },
            prefs = fakePrefs
        )

        // Attempt 1: Fails
        val firstResult = manager.updateIfNecessary(force = false)
        assertFalse(firstResult)
        assertEquals(1, attempts)
        assertEquals(0L, fakePrefs.getLong(YtDlpUpdateManager.KEY_LAST_SUCCESSFUL_UPDATE, 0L))
        assertTrue(manager.updateState.value is YtDlpUpdateState.Failed)

        // Attempt 2: Retries because no timestamp was saved and engine was still outdated
        val secondResult = manager.updateIfNecessary(force = false)
        assertTrue(secondResult)
        assertEquals(2, attempts)
        assertTrue(fakePrefs.getLong(YtDlpUpdateManager.KEY_LAST_SUCCESSFUL_UPDATE, 0L) > 0L)
        assertTrue(manager.updateState.value is YtDlpUpdateState.Success)
    }

    @Test
    fun concurrentCallers_shareSingleFlightExecution() = runTest {
        val invokerCalls = AtomicInteger(0)
        val manager = YtDlpUpdateManager(
            context = dummyContext,
            updateInvoker = { _, _ ->
                invokerCalls.incrementAndGet()
                Thread.sleep(50) // Simulate in-flight network activity
                UpdateStatus.DONE
            },
            versionProvider = { "2026.08.19" },
            prefs = fakePrefs
        )

        // Launch 5 concurrent callers
        val deferredList = (1..5).map {
            async {
                manager.updateIfNecessary(force = false)
            }
        }

        val results = deferredList.awaitAll()

        // All 5 should have succeeded
        assertTrue(results.all { it })
        // The updater must have run EXACTLY ONCE due to single-flight synchronization
        assertEquals(1, invokerCalls.get())
        assertTrue(manager.updateState.value is YtDlpUpdateState.Success)
    }

    @Test
    fun ensureReadyForExtraction_doesNotUpdateAgainWhenRecentlyUpdated() = runTest {
        var callCount = 0
        val manager = YtDlpUpdateManager(
            context = dummyContext,
            updateInvoker = { _, _ ->
                callCount++
                UpdateStatus.DONE
            },
            versionProvider = { "2026.08.19" },
            prefs = fakePrefs
        )

        // First extraction check: runs update
        val firstReady = manager.ensureReadyForExtraction(isYouTube = true)
        assertTrue(firstReady)
        assertEquals(1, callCount)

        // Subsequent extractions: already up-to-date, should skip update
        val secondReady = manager.ensureReadyForExtraction(isYouTube = true)
        val thirdReady = manager.ensureReadyForExtraction(isYouTube = true)

        assertTrue(secondReady)
        assertTrue(thirdReady)
        // Count remains 1: did not update for every track
        assertEquals(1, callCount)
    }

    @Test
    fun updateFinishingWithOutdatedVersion_reportsFailureAndDoesNotPersistTimestamp() = runTest {
        val manager = YtDlpUpdateManager(
            context = dummyContext,
            updateInvoker = { _, _ ->
                // Updater reported done, but binary remains 2025.11.12
                UpdateStatus.DONE
            },
            versionProvider = { "2025.11.12" }, // Stale engine version
            prefs = fakePrefs
        )

        val result = manager.updateIfNecessary(force = true)

        // Must reject update as failed and not silently claim it was updated
        assertFalse(result)
        val state = manager.updateState.value
        assertTrue("Expected Failed state, but was $state", state is YtDlpUpdateState.Failed)
        assertEquals(0L, fakePrefs.getLong(YtDlpUpdateManager.KEY_LAST_SUCCESSFUL_UPDATE, 0L))
    }

    @Test
    fun isOutdatedVersion_identifiesBundledAndOldVersionsCorrectly() {
        assertTrue(YtDlpUpdateManager.isOutdatedVersion("2025.11.12"))
        assertTrue(YtDlpUpdateManager.isOutdatedVersion("2024.08.01"))
        assertTrue(YtDlpUpdateManager.isOutdatedVersion("unknown"))
        assertTrue(YtDlpUpdateManager.isOutdatedVersion(null))
        assertTrue(YtDlpUpdateManager.isOutdatedVersion(""))

        assertFalse(YtDlpUpdateManager.isOutdatedVersion("2026.08.19"))
        assertFalse(YtDlpUpdateManager.isOutdatedVersion("2026.09.27.232945"))
    }

    @Test
    fun parseVersionString_extractsVersionFromPythonFile() {
        val sampleContent = """
            # Autogenerated by devscripts/update-version.py
            __version__ = '2026.08.19'
            RELEASE_GIT_HEAD = 'abc'
        """.trimIndent()

        val parsed = YtDlpUpdateManager.parseVersionString(sampleContent)
        assertEquals("2026.08.19", parsed)
    }
}

class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = values

    override fun getString(key: String?, defValue: String?): String? =
        values[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        (values[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(values)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class FakeEditor(private val target: MutableMap<String, Any>) : SharedPreferences.Editor {
        private val staging = mutableMapOf<String, Any>()
        private val toRemove = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            key?.let { if (value != null) staging[it] = value else toRemove.add(it) }
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            key?.let { if (values != null) staging[it] = values else toRemove.add(it) }
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            key?.let { staging[it] = value }
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            key?.let { staging[it] = value }
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            key?.let { staging[it] = value }
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            key?.let { staging[it] = value }
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            key?.let { toRemove.add(it) }
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) target.clear()
            toRemove.forEach { target.remove(it) }
            target.putAll(staging)
        }
    }
}
