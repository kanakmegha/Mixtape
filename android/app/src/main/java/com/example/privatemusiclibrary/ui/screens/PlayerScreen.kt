package com.example.privatemusiclibrary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.privatemusiclibrary.domain.player.RepeatMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    val track = playerState.currentTrack
    
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    // Premium background gradient
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1E213A), // Deep indigo/charcoal
            Color(0xFF0F111A)  // Near black
        )
    )

    if (showAddToPlaylist && track != null) {
        AddToPlaylistBottomSheet(
            trackId = track.id,
            onDismiss = { showAddToPlaylist = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = { 
                                    showRenameDialog = true
                                    showMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to playlist") },
                                onClick = { 
                                    showAddToPlaylist = true
                                    showMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (track?.isFavorite == true) "Remove favorite" else "Favorite") },
                                onClick = { 
                                    viewModel.toggleFavorite()
                                    showMenu = false 
                                },
                                leadingIcon = { 
                                    Icon(if (track?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Track details") },
                                onClick = { 
                                    showDetails = true
                                    showMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.Info, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { 
                                    showDeleteConfirm = true
                                    showMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // Artwork Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2C3258), Color(0xFF1E213A))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Title and Artist
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track?.title ?: "Not Playing",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track?.artist ?: "Unknown Artist",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFA0A5C0), // Soft lavender/gray
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    val isFav = track?.isFavorite == true
                    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
                    
                    LaunchedEffect(isFav) {
                        if (isFav) {
                            scale.animateTo(1.3f, animationSpec = androidx.compose.animation.core.tween(100))
                            scale.animateTo(1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy))
                        } else {
                            scale.animateTo(0.8f, animationSpec = androidx.compose.animation.core.tween(100))
                            scale.animateTo(1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy))
                        }
                    }

                    IconButton(
                        onClick = { viewModel.toggleFavorite() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFav) Color(0xFF6C63FF) else Color(0xFFA0A5C0),
                            modifier = Modifier
                                .size(32.dp)
                                .scale(scale.value)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Progress Slider
                var isSeeking by remember { mutableStateOf(false) }
                var sliderValue by remember { mutableStateOf(0f) }

                LaunchedEffect(playerState.positionMs, playerState.durationMs) {
                    if (!isSeeking && playerState.durationMs > 0) {
                        sliderValue = playerState.positionMs.toFloat() / playerState.durationMs.toFloat()
                    }
                }

                Slider(
                    value = if (playerState.durationMs > 0) sliderValue else 0f,
                    onValueChange = { value ->
                        isSeeking = true
                        sliderValue = value
                    },
                    onValueChangeFinished = {
                        isSeeking = false
                        viewModel.seekTo((sliderValue * playerState.durationMs).toLong())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF6C63FF),
                        activeTrackColor = Color(0xFF6C63FF),
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )

                // Time Texts
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(if (isSeeking) (sliderValue * playerState.durationMs).toLong() else playerState.positionMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA0A5C0)
                    )
                    Text(
                        text = formatTime(playerState.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA0A5C0)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState.shuffleEnabled) Color(0xFF6C63FF) else Color(0xFFA0A5C0)
                        )
                    }
                    
                    IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(40.dp), tint = Color.White)
                    }
                    
                    FloatingActionButton(
                        onClick = { if (playerState.isPlaying) viewModel.pause() else viewModel.resume() },
                        shape = CircleShape,
                        containerColor = Color(0xFF6C63FF),
                        contentColor = Color.White,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    
                    IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(40.dp), tint = Color.White)
                    }
                    
                    IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                        Icon(
                            imageVector = when (playerState.repeatMode) {
                                RepeatMode.REPEAT_ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (playerState.repeatMode != RepeatMode.OFF) Color(0xFF6C63FF) else Color(0xFFA0A5C0)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1.5f))
            }
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteConfirm && track != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete track?") },
            text = { 
                Column {
                    Text("\"${track.title}\"")
                    Text(track.artist ?: "Unknown Artist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("This will remove the audio from your library and storage.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTrack(track)
                        showDeleteConfirm = false
                        onNavigateBack()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Rename Dialog
    if (showRenameDialog && track != null) {
        var newTitle by remember(track.title) { mutableStateOf(track.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename track") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    if (newTitle.isNotBlank()) {
                        viewModel.renameTrack(track, newTitle.trim())
                    }
                    showRenameDialog = false 
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
    
    // Track Details Dialog
    if (showDetails && track != null) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("Track Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("Title", track.title)
                    DetailRow("Artist", track.artist ?: "Unknown")
                    DetailRow("Source", track.sourcePlatform ?: "Local")
                    if (track.sourceId != null) DetailRow("Source ID", track.sourceId)
                    DetailRow("Duration", formatTime(track.durationMs ?: 0))
                    DetailRow("File Type", track.mimeType)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
