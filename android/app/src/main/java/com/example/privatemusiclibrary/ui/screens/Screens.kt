package com.example.privatemusiclibrary.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.privatemusiclibrary.domain.model.Track

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
                title = { Text("Mixtape") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    text = "Welcome back",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            if (state.recentlyPlayed.isEmpty() && state.favorites.isEmpty() && !state.isLoading) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "No music yet",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Import your first track to start building your library.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(onClick = onNavigateToImport) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import Music")
                        }
                    }
                }
            } else {
                if (state.recentlyPlayed.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recently Played",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(state.recentlyPlayed) { track ->
                                var showAddToPlaylist by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                                TrackCard(
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
                                    com.example.privatemusiclibrary.ui.screens.AddToPlaylistBottomSheet(
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
                        Text(
                            text = "Favorites",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(state.favorites) { track ->
                                var showAddToPlaylist by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                                TrackCard(
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
                                    com.example.privatemusiclibrary.ui.screens.AddToPlaylistBottomSheet(
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
fun TrackCard(
    track: Track, 
    onClick: () -> Unit = {},
    onAddToPlaylist: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.size(120.dp, 160.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    com.example.privatemusiclibrary.ui.components.TrackActionMenu(
                        track = track,
                        onPlay = onClick,
                        onAddToPlaylist = onAddToPlaylist,
                        onRename = onRename,
                        onDelete = onDelete
                    )
                }
            }
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            var isSearchActive by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            placeholder = { Text("Search title or artist...") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { 
                                    viewModel.updateSearchQuery("")
                                    isSearchActive = false 
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        )
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Library") },
                    actions = {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { viewModel.scanLibrary() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Scan Library")
                            }
                        }
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
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
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is LibraryUiState.Empty -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No music yet",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Add music to your configured storage location and it will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                is LibraryUiState.Error -> {
                    Text(
                        text = "Error: ${currentState.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is LibraryUiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(currentState.tracks) { index, track ->
                            Card(
                                onClick = { 
                                    playerViewModel.playQueue(listOf(track), 0)
                                    onNavigateToPlayer()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 16.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.artist ?: "Unknown Artist",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (track.isFavorite) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = "Favorite",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                    var showAddToPlaylist by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                                    
                                    com.example.privatemusiclibrary.ui.components.TrackActionMenu(
                                        track = track,
                                        onPlay = {
                                            playerViewModel.playQueue(listOf(track), 0)
                                            onNavigateToPlayer()
                                        },
                                        onAddToPlaylist = { showAddToPlaylist = true },
                                        onRename = { newName -> playerViewModel.renameTrack(track, newName) },
                                        onDelete = { playerViewModel.deleteTrack(track) }
                                    )
                                    
                                    if (showAddToPlaylist) {
                                        com.example.privatemusiclibrary.ui.screens.AddToPlaylistBottomSheet(
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    sharedUrl: String? = null,
    onSharedUrlHandled: () -> Unit = {},
    viewModel: ImportViewModel = hiltViewModel()
) {
    androidx.compose.runtime.LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            viewModel.onUrlInputChanged(sharedUrl)
            viewModel.startImport()
            onSharedUrlHandled()
        }
    }
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Import Music") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            if (!state.isStorageConfigured) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Storage not configured. Please configure it in Settings.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (state.currentJob == null && !state.isImporting) {
                // Input section
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Paste a media URL to extract audio",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = state.urlInput,
                            onValueChange = { viewModel.onUrlInputChanged(it) },
                            placeholder = { Text("https://...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = state.error != null,
                            enabled = !state.isImporting,
                            shape = MaterialTheme.shapes.medium
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
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = state.urlInput.isNotBlank() && state.isStorageConfigured && !state.isImporting
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extract & Import")
                        }
                    }
                }
            } else if (state.currentJob == null && state.isImporting) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Receiving link...",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            
            state.currentJob?.let { job ->
                val isError = job.status == com.example.privatemusiclibrary.domain.model.ImportJobStatus.FAILED
                val isComplete = job.status == com.example.privatemusiclibrary.domain.model.ImportJobStatus.COMPLETED
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isError) MaterialTheme.colorScheme.errorContainer 
                        else if (isComplete) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = job.title ?: "Extracting media...",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = job.sourceUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            if (isComplete) {
                                Icon(
                                    Icons.Default.CheckCircle, 
                                    contentDescription = "Completed",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else if (isError) {
                                Icon(
                                    Icons.Default.Error, 
                                    contentDescription = "Failed",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (!isComplete && !isError && job.status != com.example.privatemusiclibrary.domain.model.ImportJobStatus.CANCELLED) {
                            LinearProgressIndicator(
                                progress = if (job.progress > 0) job.progress / 100f else 0f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val statusText = when (job.status) {
                                    com.example.privatemusiclibrary.domain.model.ImportJobStatus.QUEUED,
                                    com.example.privatemusiclibrary.domain.model.ImportJobStatus.VALIDATING -> "Receiving link..."
                                    com.example.privatemusiclibrary.domain.model.ImportJobStatus.RESOLVING -> "Preparing media..."
                                    com.example.privatemusiclibrary.domain.model.ImportJobStatus.DOWNLOADING -> "Downloading audio..."
                                    com.example.privatemusiclibrary.domain.model.ImportJobStatus.CONVERTING -> "Converting audio..."
                                    com.example.privatemusiclibrary.domain.model.ImportJobStatus.SAVING -> "Saving to library..."
                                    com.example.privatemusiclibrary.domain.model.ImportJobStatus.COMPLETED -> "✓ Added to Mixtape"
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
                            if (job.errorMessage != null) {
                                Text(
                                    text = job.errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { viewModel.clearCurrentJob() }) {
                                    Text("Dismiss")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
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
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel(), authViewModel: com.example.privatemusiclibrary.ui.screens.auth.AuthViewModel? = null) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
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
                title = { Text("Settings") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                SettingsSectionHeader("Storage")
                
                if (state.storageUri == null) {
                    SettingsItem(
                        icon = Icons.Default.FolderOff,
                        title = "Storage not configured",
                        subtitle = "Tap to choose a directory for your music",
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
                SettingsSectionHeader("Playback")
                SettingsItem(
                    icon = Icons.Default.HighQuality,
                    title = "Audio Quality",
                    subtitle = "Coming soon",
                    onClick = null,
                    enabled = false
                )
            }
            
            item {
                SettingsSectionHeader("Account")
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "Sign Out",
                    subtitle = "Disconnect from Mixtape API",
                    onClick = { authViewModel?.logout() }
                )
            }
            
            item {
                SettingsSectionHeader("About")
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "1.0 (Pre-release)",
                    onClick = null
                )
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.padding(end = 16.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}
