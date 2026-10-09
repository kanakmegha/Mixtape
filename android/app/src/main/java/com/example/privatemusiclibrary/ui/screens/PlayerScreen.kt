package com.example.privatemusiclibrary.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.privatemusiclibrary.domain.player.RepeatMode
import com.example.privatemusiclibrary.theme.Dimens
import com.example.privatemusiclibrary.theme.MixtapeLimeAccent
import com.example.privatemusiclibrary.theme.MixtapeVioletPrimary

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

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF161926), // Deep rich violet-black
            Color(0xFF0B0D14)  // Obsidian black
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
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Close Player",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = Color.White
                            )
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
                                    Icon(
                                        imageVector = if (track?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null
                                    )
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
                    .padding(horizontal = Dimens.spacingXl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.5f))

                // Large Artwork View
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(Dimens.radiusXl)),
                    shape = RoundedCornerShape(Dimens.radiusXl),
                    tonalElevation = 12.dp,
                    shadowElevation = 16.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF2E1B5B),
                                        Color(0xFF1E2235)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(110.dp),
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.5f))

                // Track Title & Artist with Favorite Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track?.title ?: "Not Playing",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track?.artist ?: "Unknown Artist",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val isFav = track?.isFavorite == true
                    val scale = remember { Animatable(1f) }

                    LaunchedEffect(isFav) {
                        if (isFav) {
                            scale.animateTo(1.3f, animationSpec = tween(100))
                            scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        } else {
                            scale.animateTo(0.8f, animationSpec = tween(100))
                            scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    }

                    IconButton(
                        onClick = { viewModel.toggleFavorite() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFav) MixtapeVioletPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(28.dp)
                                .scale(scale.value)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.spacingLg))

                // Seek Progress Slider
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
                        thumbColor = MixtapeVioletPrimary,
                        activeTrackColor = MixtapeVioletPrimary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )

                // Time Labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spacingXs),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(if (isSeeking) (sliderValue * playerState.durationMs).toLong() else playerState.positionMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTime(playerState.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.spacingXl))

                // Playback Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState.shuffleEnabled) MixtapeLimeAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(52.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(36.dp),
                            tint = Color.White
                        )
                    }

                    FloatingActionButton(
                        onClick = { if (playerState.isPlaying) viewModel.pause() else viewModel.resume() },
                        shape = CircleShape,
                        containerColor = MixtapeVioletPrimary,
                        contentColor = Color.White,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(52.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(36.dp),
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                        Icon(
                            imageVector = when (playerState.repeatMode) {
                                RepeatMode.REPEAT_ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (playerState.repeatMode != RepeatMode.OFF) MixtapeLimeAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm && track != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete track?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("\"${track.title}\"", fontWeight = FontWeight.SemiBold)
                    Text(track.artist ?: "Unknown Artist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(Dimens.spacingLg))
                    Text("This will permanently remove the audio file from your library and storage.")
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
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
            title = { Text("Rename track", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.radiusMd)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTitle.isNotBlank()) {
                        viewModel.renameTrack(track, newTitle.trim())
                    }
                    showRenameDialog = false
                }) { Text("Save", fontWeight = FontWeight.Bold) }
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
            title = { Text("Track Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
                    DetailRow("Title", track.title)
                    DetailRow("Artist", track.artist ?: "Unknown")
                    DetailRow("Source Platform", track.sourcePlatform ?: "Local Storage")
                    if (track.sourceId != null) DetailRow("Source ID", track.sourceId)
                    DetailRow("Duration", formatTime(track.durationMs ?: 0))
                    DetailRow("MIME Type", track.mimeType)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
