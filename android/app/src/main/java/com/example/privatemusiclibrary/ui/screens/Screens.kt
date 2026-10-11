package com.example.privatemusiclibrary.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.privatemusiclibrary.domain.model.ImportJobStatus
import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.theme.Dimens
import com.example.privatemusiclibrary.ui.components.EmptyStateView
import com.example.privatemusiclibrary.ui.components.MixtapeTrackCard
import com.example.privatemusiclibrary.ui.components.MixtapeTrackRow
import com.example.privatemusiclibrary.ui.components.SectionHeader
import com.example.privatemusiclibrary.ui.screens.auth.AuthViewModel
import com.example.privatemusiclibrary.BuildConfig
import com.example.privatemusiclibrary.update.AppUpdateCheckResult
import com.example.privatemusiclibrary.update.AppUpdateChecker
import com.example.privatemusiclibrary.update.AppUpdateDownloader
import com.example.privatemusiclibrary.update.AvailableAppUpdate
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Login Screen")
    }
}

@Composable
fun RegisterScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Register Screen")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToLibrary: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToPlayer: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(Dimens.spacingSm))
                        Text(
                            text = "Mixtape",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXl)
        ) {
            item {
                Column {
                    Text(
                        text = "Welcome back",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Your personal library at a glance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.recentlyPlayed.isEmpty() && state.favorites.isEmpty() && !state.isLoading) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.MusicNote,
                        title = "No music in your library yet",
                        description = "Extract audio from online links or scan local storage to start listening.",
                        actionText = "Import Music",
                        onActionClick = onNavigateToImport
                    )
                }
            } else {
                if (state.recentlyPlayed.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Recently Played",
                            actionText = "See All",
                            onActionClick = onNavigateToLibrary
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
                            contentPadding = PaddingValues(vertical = Dimens.spacingSm)
                        ) {
                            items(state.recentlyPlayed) { track ->
                                var showAddToPlaylist by remember { mutableStateOf(false) }
                                MixtapeTrackCard(
                                    track = track,
                                    onClick = {
                                        playerViewModel.playQueue(listOf(track), 0)
                                        onNavigateToPlayer()
                                    },
                                    onAddToPlaylist = { showAddToPlaylist = true },
                                    onRename = { playerViewModel.renameTrack(track, it) },
                                    onDelete = { playerViewModel.deleteTrack(track) }
                                )
                                if (showAddToPlaylist) {
                                    AddToPlaylistBottomSheet(
                                        trackId = track.id,
                                        onDismiss = { showAddToPlaylist = false }
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.favorites.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Favorites",
                            actionText = "Library",
                            onActionClick = onNavigateToLibrary
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
                            contentPadding = PaddingValues(vertical = Dimens.spacingSm)
                        ) {
                            items(state.favorites) { track ->
                                var showAddToPlaylist by remember { mutableStateOf(false) }
                                MixtapeTrackCard(
                                    track = track,
                                    onClick = {
                                        playerViewModel.playQueue(listOf(track), 0)
                                        onNavigateToPlayer()
                                    },
                                    onAddToPlaylist = { showAddToPlaylist = true },
                                    onRename = { playerViewModel.renameTrack(track, it) },
                                    onDelete = { playerViewModel.deleteTrack(track) }
                                )
                                if (showAddToPlaylist) {
                                    AddToPlaylistBottomSheet(
                                        trackId = track.id,
                                        onDismiss = { showAddToPlaylist = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToPlayer: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    Scaffold(
        topBar = {
            val searchQuery by viewModel.searchQuery.collectAsState()
            var isSearchActive by remember { mutableStateOf(false) }

            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = Dimens.spacingSm),
                            placeholder = { Text("Search title or artist...") },
                            singleLine = true,
                            shape = RoundedCornerShape(Dimens.radiusMd),
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.updateSearchQuery("")
                                    isSearchActive = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Library",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = Dimens.spacingLg)
                                    .size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            IconButton(onClick = { viewModel.scanLibrary() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Scan Library")
                            }
                        }
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search Library")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val currentState = state) {
                is LibraryUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is LibraryUiState.Empty -> {
                    EmptyStateView(
                        icon = Icons.Default.LibraryMusic,
                        title = "No music in your library",
                        description = "Add music files to your configured storage location or import tracks using online links.",
                        actionText = "Scan Storage",
                        onActionClick = { viewModel.scanLibrary() }
                    )
                }
                is LibraryUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Error loading library: ${currentState.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(Dimens.spacingXl)
                        )
                    }
                }
                is LibraryUiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = Dimens.spacingSm, vertical = Dimens.spacingSm),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                    ) {
                        item {
                            Text(
                                text = "${currentState.tracks.size} tracks",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
                            )
                        }
                        itemsIndexed(currentState.tracks) { index, track ->
                            var showAddToPlaylist by remember { mutableStateOf(false) }

                            MixtapeTrackRow(
                                track = track,
                                onClick = {
                                    playerViewModel.playQueue(currentState.tracks, index)
                                    onNavigateToPlayer()
                                },
                                onAddToPlaylist = { showAddToPlaylist = true },
                                onRename = { newName -> playerViewModel.renameTrack(track, newName) },
                                onDelete = { playerViewModel.deleteTrack(track) },
                                onToggleFavorite = {
                                    playerViewModel.playQueue(currentState.tracks, index)
                                    playerViewModel.toggleFavorite()
                                }
                            )

                            if (showAddToPlaylist) {
                                AddToPlaylistBottomSheet(
                                    trackId = track.id,
                                    onDismiss = { showAddToPlaylist = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    sharedUrl: String? = null,
    onSharedUrlHandled: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: ImportViewModel = hiltViewModel()
) {
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            viewModel.onSharedUrlReceived(sharedUrl)
            onSharedUrlHandled()
        }
    }
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Import Music",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Dimens.spacingXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXl)
        ) {
            if (!state.isStorageLoading && !state.isStorageConfigured) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(Dimens.radiusMd),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(Dimens.spacingLg),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(Dimens.spacingMd))
                            Text(
                                text = "Storage Directory Required",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (!state.pendingSharedUrl.isNullOrBlank()) {
                                "Your shared link is ready to import, but Mixtape needs a music folder first. Tap below to select a folder in Settings."
                            } else {
                                "Storage directory is not configured. Please select a folder in Settings to save your downloaded tracks."
                            },
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = onNavigateToSettings,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(Dimens.radiusSm),
                            modifier = Modifier.padding(top = Dimens.spacingXs)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Dimens.spacingSm))
                            Text("Configure Storage in Settings")
                        }
                    }
                }
            }

            if (state.currentJob == null && !state.isImporting) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(Dimens.radiusXl),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(Dimens.spacingXl),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(Dimens.spacingMd))
                            Text(
                                text = "Paste Link to Extract Audio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Supports public video and audio links. Audio will be extracted and saved to your Mixtape library.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = state.urlInput,
                            onValueChange = { viewModel.onUrlInputChanged(it) },
                            placeholder = { Text("https://example.com/watch?v=...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = state.error != null,
                            enabled = !state.isImporting,
                            shape = RoundedCornerShape(Dimens.radiusMd),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        if (state.error != null) {
                            Text(
                                text = state.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = { viewModel.startImport() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(Dimens.radiusMd),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            enabled = state.urlInput.isNotBlank() && state.isStorageConfigured && !state.isImporting
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(Dimens.spacingSm))
                            Text(
                                text = "Extract & Import",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else if (state.currentJob == null && state.isImporting) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.radiusLg),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                        Text(
                            text = "Receiving media link...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(Dimens.spacingLg))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            state.currentJob?.let { job ->
                val isError = job.status == ImportJobStatus.FAILED
                val isComplete = job.status == ImportJobStatus.COMPLETED

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.radiusLg),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
                        else if (isComplete) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = job.title ?: "Extracting media...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = job.sourceUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isComplete) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Completed",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else if (isError) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Failed",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimens.spacingLg))

                        if (!isComplete && !isError && job.status != ImportJobStatus.CANCELLED) {
                            LinearProgressIndicator(
                                progress = { if (job.progress > 0) job.progress / 100f else 0f },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Dimens.spacingSm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val statusText = when (job.status) {
                                    ImportJobStatus.QUEUED, ImportJobStatus.VALIDATING -> "Receiving link..."
                                    ImportJobStatus.RESOLVING -> "Preparing media source..."
                                    ImportJobStatus.DOWNLOADING -> "Downloading audio stream..."
                                    ImportJobStatus.CONVERTING -> "Converting audio format..."
                                    ImportJobStatus.SAVING -> "Saving to library..."
                                    ImportJobStatus.COMPLETED -> "✓ Added to Mixtape"
                                    else -> job.status.name
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { viewModel.cancelImport(job.id) }) {
                                    Text("Cancel")
                                }
                            }
                        }

                        if (isError) {
                            job.errorMessage?.let { msg ->
                                Text(
                                    text = msg,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = Dimens.spacingSm)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Dimens.spacingSm),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { viewModel.clearCurrentJob() }) {
                                    Text("Dismiss")
                                }
                                Spacer(modifier = Modifier.width(Dimens.spacingSm))
                                TextButton(onClick = {
                                    viewModel.clearCurrentJob()
                                    viewModel.onUrlInputChanged(job.sourceUrl)
                                    viewModel.startImport()
                                }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel? = null
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var checkingForUpdates by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AvailableAppUpdate?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    fun checkForUpdates() {
        if (checkingForUpdates) return
        checkingForUpdates = true
        updateMessage = null
        coroutineScope.launch {
            when (val result = AppUpdateChecker.checkForUpdate()) {
                is AppUpdateCheckResult.UpdateAvailable -> {
                    availableUpdate = result.update
                }
                is AppUpdateCheckResult.UpToDate -> {
                    updateMessage = "You're using the latest version (${result.currentVersion})."
                }
                is AppUpdateCheckResult.Failed -> {
                    updateMessage = result.message
                }
            }
            checkingForUpdates = false
        }
    }

    val documentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                viewModel.onStorageLocationSelected(uri)
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = Dimens.spacingSm)
        ) {
            item {
                SettingsSectionHeader("Storage")

                if (state.storageUri == null) {
                    SettingsItem(
                        icon = Icons.Default.FolderOff,
                        title = "Storage not configured",
                        subtitle = "Tap to select a local folder for downloaded audio",
                        onClick = { documentTreeLauncher.launch(null) }
                    )
                } else {
                    SettingsItem(
                        icon = Icons.Default.Folder,
                        title = "Storage Location",
                        subtitle = state.storageUri,
                        onClick = { documentTreeLauncher.launch(null) }
                    )
                    SettingsItem(
                        icon = Icons.Default.Storage,
                        title = "Storage Used",
                        subtitle = "${state.storageUsageBytes / (1024 * 1024)} MB (${state.trackCount} tracks)",
                        onClick = null
                    )
                }
            }

            item {
                SettingsSectionHeader("Playback & Audio")
                SettingsItem(
                    icon = Icons.Default.HighQuality,
                    title = "Audio Quality",
                    subtitle = "High quality lossy / lossless (Default)",
                    onClick = null,
                    enabled = true
                )
            }

            item {
                SettingsSectionHeader("Invite Friends")

                SettingsItem(
                    icon = Icons.Default.PersonAdd,
                    title = "Create Invitation",
                    subtitle = "Generate an invite link for a friend to join Mixtape",
                    onClick = { viewModel.createInvitation() },
                    enabled = !state.isCreatingInvitation
                )

                if (state.isCreatingInvitation) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(Dimens.spacingMd))
                        Text(
                            text = "Creating invitation link...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                state.invitationError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
                    )
                }

                state.invitationToken?.let { token ->
                    SettingsItem(
                        icon = Icons.Default.Share,
                        title = "Share Invitation Link",
                        subtitle = "Tap to copy or share invitation",
                        onClick = {
                            val invitationUrl = "https://mixtape-web-eight.vercel.app/?token=${Uri.encode(token)}"
                            val shareMessage = """
                                You're invited to join Mixtape! 🎵

                                Open this link to get started:
                                $invitationUrl

                                Or enter code when registering: $token
                            """.trimIndent()

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareMessage)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Mixtape Invitation"))
                        }
                    )
                }
            }

            item {
                SettingsSectionHeader("Account")

                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "Sign Out",
                    subtitle = "Disconnect account session from Mixtape server",
                    onClick = { authViewModel?.logout() }
                )
            }

            item {
                SettingsSectionHeader("About")
                SettingsItem(
                    icon = Icons.Default.SystemUpdate,
                    title = if (checkingForUpdates) "Checking for updates…" else "Check for updates",
                    subtitle = if (checkingForUpdates) {
                        "Checking the latest GitHub release"
                    } else {
                        "Installed version ${BuildConfig.VERSION_NAME} · Tap to check"
                    },
                    onClick = { checkForUpdates() },
                    enabled = !checkingForUpdates
                )
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Mixtape Version",
                    subtitle = "${BuildConfig.VERSION_NAME} (Installed)",
                    onClick = null
                )
            }
        }
    }

    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { availableUpdate = null },
            title = { Text("Mixtape update available") },
            text = {
                Text(
                    "Version ${update.versionName} is available. Download the APK now? " +
                        "After it finishes, open the download notification and approve the Android installer. " +
                        "Your existing data is retained when Android accepts this as an update."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            AppUpdateDownloader.enqueue(context, update)
                            updateMessage = "Download started for version ${update.versionName}. When it finishes, tap the download notification to install the update."
                            availableUpdate = null
                        } catch (_: Exception) {
                            updateMessage = "Couldn't start the APK download. Please try again."
                            availableUpdate = null
                        }
                    }
                ) { Text("Download APK") }
            },
            dismissButton = {
                TextButton(onClick = { availableUpdate = null }) { Text("Later") }
            }
        )
    }

    updateMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { updateMessage = null },
            title = { Text("Mixtape updates") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { updateMessage = null }) { Text("OK") }
            },
            dismissButton = {
                if (message.contains("Download started")) {
                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/kanakmegha/Mixtape/releases")
                                )
                                context.startActivity(intent)
                                updateMessage = null
                            } catch (_: Exception) {
                                updateMessage = "Open github.com/kanakmegha/Mixtape/releases in your browser."
                            }
                        }
                    ) { Text("Releases page") }
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
    )
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null && enabled,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(Dimens.spacingLg))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
