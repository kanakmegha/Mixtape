package com.example.privatemusiclibrary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.privatemusiclibrary.domain.model.Playlist
import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.theme.Dimens
import com.example.privatemusiclibrary.ui.components.EmptyStateView
import com.example.privatemusiclibrary.ui.components.MixtapeTrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Playlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.radiusMd)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName) {
                                showCreateDialog = false
                                newPlaylistName = ""
                            }
                        }
                    }
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newPlaylistName = ""
                    showCreateDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Playlist")
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyStateView(
                    icon = Icons.Default.QueueMusic,
                    title = "No playlists created",
                    description = "Organize your favorite music into custom playlists.",
                    actionText = "Create Playlist",
                    onActionClick = {
                        newPlaylistName = ""
                        showCreateDialog = true
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                items(uiState.playlists) { playlist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToPlaylist(playlist.id) },
                        shape = RoundedCornerShape(Dimens.radiusLg),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.spacingLg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(Dimens.radiusMd))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FeaturedPlayList,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(Dimens.spacingLg))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Playlist", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            viewModel.deletePlaylist(playlist.id)
                                            showMenu = false
                                        }
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
fun PlaylistDetailScreen(
    playlistId: String,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    val uiState by viewModel.uiState.collectAsState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameName by remember { mutableStateOf("") }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.radiusMd)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameName.isNotBlank()) {
                        viewModel.renamePlaylist(renameName)
                        showRenameDialog = false
                    }
                }) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.playlist?.name ?: "Playlist", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    renameName = uiState.playlist?.name ?: ""
                                    showRenameDialog = true
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    viewModel.deletePlaylist(onNavigateBack)
                                    showMenu = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            if (uiState.tracks.isNotEmpty()) {
                                playerViewModel.playQueue(uiState.tracks, 0)
                                onNavigateToPlayer()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .padding(end = Dimens.spacingSm),
                        shape = RoundedCornerShape(Dimens.radiusMd),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(Dimens.spacingSm))
                        Text("Play", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            if (uiState.tracks.isNotEmpty()) {
                                playerViewModel.playQueue(uiState.tracks, 0)
                                playerViewModel.setShuffleModeEnabled(true)
                                onNavigateToPlayer()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .padding(start = Dimens.spacingSm),
                        shape = RoundedCornerShape(Dimens.radiusMd),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(Dimens.spacingSm))
                        Text("Shuffle", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                    }
                }

                if (uiState.tracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "This playlist is empty.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.tracks.size) { index ->
                            val track = uiState.tracks[index]
                            TrackRow(
                                track = track,
                                onClick = {
                                    playerViewModel.playQueue(uiState.tracks, index)
                                    onNavigateToPlayer()
                                },
                                onRemove = { viewModel.removeTrack(track.id) },
                                onMoveUp = if (index > 0) {
                                    { viewModel.reorderTracks(index, index - 1) }
                                } else null,
                                onMoveDown = if (index < uiState.tracks.size - 1) {
                                    { viewModel.reorderTracks(index, index + 1) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = 2.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (onMoveUp != null) {
                        DropdownMenuItem(
                            text = { Text("Move Up") },
                            onClick = {
                                onMoveUp()
                                showMenu = false
                            }
                        )
                    }
                    if (onMoveDown != null) {
                        DropdownMenuItem(
                            text = { Text("Move Down") },
                            onClick = {
                                onMoveDown()
                                showMenu = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove from playlist", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onRemove()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}
