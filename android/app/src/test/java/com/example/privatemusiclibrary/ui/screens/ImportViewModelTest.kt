package com.example.privatemusiclibrary.ui.screens

import com.example.privatemusiclibrary.data.importing.PendingImportManager
import com.example.privatemusiclibrary.domain.model.AppSettings
import com.example.privatemusiclibrary.domain.model.ImportJob
import com.example.privatemusiclibrary.domain.model.ImportJobStatus
import com.example.privatemusiclibrary.domain.repository.ImportJobRepository
import com.example.privatemusiclibrary.domain.repository.SettingsRepository
import com.example.privatemusiclibrary.domain.usecase.GetStorageInfoUseCase
import com.example.privatemusiclibrary.domain.usecase.ImportUrlUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeImportUseCase: FakeImportUrlUseCase
    private lateinit var fakeJobRepo: FakeImportJobRepository
    private lateinit var fakeSettingsRepo: FakeSettingsRepository
    private lateinit var pendingImportManager: PendingImportManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeImportUseCase = FakeImportUrlUseCase()
        fakeJobRepo = FakeImportJobRepository()
        fakeSettingsRepo = FakeSettingsRepository()
        pendingImportManager = PendingImportManager()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ImportViewModel {
        return ImportViewModel(
            settingsRepository = fakeSettingsRepo,
            pendingImportManager = pendingImportManager,
            importUrlUseCase = fakeImportUseCase,
            cancelImportJobUseCase = com.example.privatemusiclibrary.domain.usecase.CancelImportJobUseCase(),
            importJobRepository = fakeJobRepo
        )
    }

    @Test
    fun unconfiguredStorage_retainsPendingUrl_showsNotice_doesNotDiscard() = runTest {
        // Storage is unconfigured (storageUri = null)
        fakeSettingsRepo.settingsFlow.value = AppSettings(storageUri = null)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isStorageConfigured)
        assertFalse(viewModel.uiState.value.isStorageLoading)

        // Incoming shared URL received
        viewModel.onSharedUrlReceived("https://youtu.be/test12345")
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify URL is retained and not discarded
        assertEquals("https://youtu.be/test12345", viewModel.uiState.value.urlInput)
        assertEquals("https://youtu.be/test12345", viewModel.uiState.value.pendingSharedUrl)
        assertEquals("https://youtu.be/test12345", pendingImportManager.pendingUrl.value)
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error!!.contains("Storage directory is not configured"))

        // Verify import was NOT started prematurely
        assertEquals(0, fakeImportUseCase.invocations.size)
    }

    @Test
    fun storageConfiguredLater_resumesPendingImportAutomatically() = runTest {
        // Storage initially unconfigured
        fakeSettingsRepo.settingsFlow.value = AppSettings(storageUri = null)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Shared URL received while storage is unconfigured
        viewModel.onSharedUrlReceived("https://youtu.be/test12345")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, fakeImportUseCase.invocations.size)

        // User configures storage in Settings
        fakeSettingsRepo.settingsFlow.value = AppSettings(storageUri = "content://com.android.externalstorage.documents/tree/music")
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify import automatically started
        assertEquals(1, fakeImportUseCase.invocations.size)
        assertEquals("https://youtu.be/test12345", fakeImportUseCase.invocations[0])

        // Verify pending URL was consumed atomically
        assertNull(pendingImportManager.pendingUrl.value)
        assertNull(viewModel.uiState.value.pendingSharedUrl)
    }

    @Test
    fun configuredStorage_startsImportImmediately_andConsumesPendingUrl() = runTest {
        fakeSettingsRepo.settingsFlow.value = AppSettings(storageUri = "content://music")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isStorageConfigured)

        viewModel.onSharedUrlReceived("https://youtu.be/test12345")
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify exactly one invocation
        assertEquals(1, fakeImportUseCase.invocations.size)
        assertEquals("https://youtu.be/test12345", fakeImportUseCase.invocations[0])
        assertNull(pendingImportManager.pendingUrl.value)
        assertNull(viewModel.uiState.value.pendingSharedUrl)
    }

    @Test
    fun duplicateRecomposition_doesNotTriggerDuplicateImports() = runTest {
        fakeSettingsRepo.settingsFlow.value = AppSettings(storageUri = "content://music")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Simulate first composition with sharedUrl
        viewModel.onSharedUrlReceived("https://youtu.be/test12345")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, fakeImportUseCase.invocations.size)

        // Simulate subsequent recompositions passing same sharedUrl
        viewModel.onSharedUrlReceived("https://youtu.be/test12345")
        viewModel.onSharedUrlReceived("https://youtu.be/test12345")
        testDispatcher.scheduler.advanceUntilIdle()

        // Still exactly one invocation because the pending URL was consumed
        assertEquals(1, fakeImportUseCase.invocations.size)
    }

    @Test
    fun manualUrlEntry_worksNormallyWhenStorageConfigured() = runTest {
        fakeSettingsRepo.settingsFlow.value = AppSettings(storageUri = "content://music")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onUrlInputChanged("https://example.com/direct.mp3")
        assertEquals("https://example.com/direct.mp3", viewModel.uiState.value.urlInput)

        viewModel.startImport()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeImportUseCase.invocations.size)
        assertEquals("https://example.com/direct.mp3", fakeImportUseCase.invocations[0])
        assertEquals("", viewModel.uiState.value.urlInput)
    }
}

class FakeImportUrlUseCase : ImportUrlUseCase() {
    val invocations = mutableListOf<String>()

    override suspend fun invoke(url: String): Result<String> {
        invocations.add(url)
        return Result.success("job-" + invocations.size)
    }
}

class FakeImportJobRepository : ImportJobRepository {
    private val jobs = mutableListOf<ImportJob>()
    private val jobsFlow = MutableSharedFlow<List<ImportJob>>(replay = 1)

    init {
        jobsFlow.tryEmit(emptyList())
    }

    override fun observeAll(): Flow<List<ImportJob>> = jobsFlow

    override suspend fun getById(id: String): ImportJob? = jobs.find { it.id == id }

    override suspend fun insert(job: ImportJob) {
        jobs.add(job)
        jobsFlow.emit(jobs.toList())
    }

    override suspend fun update(job: ImportJob) {
        val index = jobs.indexOfFirst { it.id == job.id }
        if (index != -1) {
            jobs[index] = job
            jobsFlow.emit(jobs.toList())
        }
    }
}

class FakeSettingsRepository : SettingsRepository {
    val settingsFlow = MutableStateFlow(AppSettings())

    override fun observeSettings(): Flow<AppSettings> = settingsFlow

    override suspend fun getSettings(): AppSettings = settingsFlow.value

    override suspend fun updateSettings(settings: AppSettings) {
        settingsFlow.value = settings
    }
}
