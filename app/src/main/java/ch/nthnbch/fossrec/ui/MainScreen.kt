package ch.nthnbch.fossrec.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.nthnbch.fossrec.MainViewModel
import ch.nthnbch.fossrec.R
import ch.nthnbch.fossrec.RecordingService
import ch.nthnbch.fossrec.SortMode
import ch.nthnbch.fossrec.data.AudioRecording
import ch.nthnbch.fossrec.data.ListRow
import ch.nthnbch.fossrec.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val playingRecording by viewModel.playingRecording.collectAsStateWithLifecycle()
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val isRecording by RecordingService.isRecording.collectAsStateWithLifecycle()
    val isPaused by RecordingService.isPaused.collectAsStateWithLifecycle()
    val recordingDuration by RecordingService.recordingDurationSeconds.collectAsStateWithLifecycle()
    val currentPart by RecordingService.currentPart.collectAsStateWithLifecycle()
    val isSplit by RecordingService.isSplit.collectAsStateWithLifecycle()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    var recordingToRename by remember { mutableStateOf<AudioRecording?>(null) }
    var recordingsToDelete by remember { mutableStateOf<List<AudioRecording>?>(null) }

    BackHandler(enabled = showSettings) { showSettings = false }

    if (showSettings) {
        val versionName = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
        }
        SettingsScreen(
            settings = settings,
            versionName = versionName,
            onChange = viewModel::updateSettings,
            onBack = { showSettings = false }
        )
        return
    }

    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.RECORD_AUDIO] == true) toggleRecording(context, isRecording)
        else showPermissionRationale = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(Dimens.iconTarget)) {
                            Icon(
                                Icons.Default.Sort,
                                contentDescription = stringResource(R.string.sort_by),
                                modifier = Modifier.size(Dimens.icon)
                            )
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(sortModeLabel(mode), style = MaterialTheme.typography.bodyLarge) },
                                    onClick = {
                                        viewModel.setSortMode(mode)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortMode == mode) Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showSettings = true }, modifier = Modifier.size(Dimens.iconTarget)) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            modifier = Modifier.size(Dimens.icon)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if (isRecording) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s5), verticalAlignment = Alignment.CenterVertically) {
                    FloatingActionButton(
                        onClick = { togglePause(context, isPaused) },
                        modifier = Modifier.size(Dimens.iconTarget + Dimens.s3),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = stringResource(if (isPaused) R.string.resume else R.string.pause),
                            modifier = Modifier.size(Dimens.icon)
                        )
                    }
                    FloatingActionButton(
                        onClick = { toggleRecording(context, true) },
                        modifier = Modifier.size(Dimens.fabMain),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = stringResource(R.string.stop),
                            modifier = Modifier.size(Dimens.fabMainIcon)
                        )
                    }
                }
            } else {
                FloatingActionButton(
                    onClick = {
                        val hasAudioPerm = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasAudioPerm) toggleRecording(context, false)
                        else launcher.launch(permissions.toTypedArray())
                    },
                    modifier = Modifier.size(Dimens.fabMain),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = stringResource(R.string.record),
                        modifier = Modifier.size(Dimens.fabMainIcon)
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(visible = isRecording) {
                RecordingIndicator(
                    durationSeconds = recordingDuration,
                    isPaused = isPaused,
                    part = if (isSplit) currentPart else null
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text(stringResource(R.string.search_recordings)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.s5, vertical = Dimens.s3),
                singleLine = true,
                shape = CircleShape
            )

            if (showPermissionRationale) {
                PermissionRationale { showPermissionRationale = false }
            }

            if (rows.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Dimens.s8 + Dimens.s6, top = Dimens.s2)
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is ListRow.GroupHeader -> GroupHeaderItem(
                                header = row,
                                onDeleteAll = { recordingsToDelete = row.parts }
                            )
                            is ListRow.Item -> {
                                val rec = row.recording
                                val isItemPlaying = playingRecording == rec
                                RecordingRow(
                                    item = row,
                                    isPlaying = isItemPlaying,
                                    playbackPositionMs = if (isItemPlaying) playbackPositionMs else 0L,
                                    onPlayPause = { viewModel.playRecording(rec) },
                                    onSeek = viewModel::seekTo,
                                    onRename = { recordingToRename = rec },
                                    onDelete = { recordingsToDelete = listOf(rec) },
                                    onShare = { shareRecording(context, rec) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    recordingToRename?.let { rec ->
        RenameDialog(
            currentName = rec.name,
            onDismiss = { recordingToRename = null },
            onConfirm = { newName ->
                viewModel.renameRecording(rec, newName)
                recordingToRename = null
            }
        )
    }

    recordingsToDelete?.let { recs ->
        DeleteDialog(
            message = if (recs.size > 1) stringResource(R.string.group_delete_confirmation, recs.size)
            else stringResource(R.string.delete_confirmation),
            onDismiss = { recordingsToDelete = null },
            onConfirm = {
                viewModel.deleteRecordings(recs)
                recordingsToDelete = null
            }
        )
    }
}

// === Pieces =====================================================================

@Composable
private fun sortModeLabel(mode: SortMode): String = when (mode) {
    SortMode.DATE_DESC -> stringResource(R.string.sort_date_desc)
    SortMode.DATE_ASC -> stringResource(R.string.sort_date_asc)
    SortMode.DURATION_DESC -> stringResource(R.string.sort_duration_desc)
    SortMode.DURATION_ASC -> stringResource(R.string.sort_duration_asc)
    SortMode.NAME_ASC -> stringResource(R.string.sort_name_asc)
    SortMode.NAME_DESC -> stringResource(R.string.sort_name_desc)
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.s3),
            modifier = Modifier.padding(Dimens.s6)
        ) {
            Icon(
                Icons.Default.MicOff,
                contentDescription = null,
                modifier = Modifier.size(Dimens.s8),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
            Text(
                text = stringResource(R.string.no_recordings_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.no_recordings_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RecordingIndicator(durationSeconds: Long, isPaused: Boolean, part: Int?) {
    val infiniteTransition = rememberInfiniteTransition(label = "recPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Surface(
        color = if (isPaused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.s5, vertical = Dimens.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.s3)) {
                if (!isPaused) {
                    Icon(
                        Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.s5),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = dotAlpha)
                    )
                } else {
                    Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(Dimens.s5))
                }
                val state = stringResource(if (isPaused) R.string.recording_paused else R.string.recording_in_progress)
                Text(
                    text = if (part != null) state + " · " + stringResource(R.string.part_label, part) else state,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(text = formatClock(durationSeconds), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun PermissionRationale(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(Dimens.s5),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(Dimens.s5),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.permission_required),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_recording)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.new_name)) },
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun DeleteDialog(message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// === Helpers ====================================================================

private fun toggleRecording(context: Context, isRecording: Boolean) {
    val intent = Intent(context, RecordingService::class.java).apply {
        action = if (isRecording) RecordingService.ACTION_STOP_RECORDING else RecordingService.ACTION_START_RECORDING
    }
    context.startForegroundService(intent)
}

private fun togglePause(context: Context, isPaused: Boolean) {
    val intent = Intent(context, RecordingService::class.java).apply {
        action = if (isPaused) RecordingService.ACTION_RESUME_RECORDING else RecordingService.ACTION_PAUSE_RECORDING
    }
    context.startForegroundService(intent)
}

private fun shareRecording(context: Context, recording: AudioRecording) {
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", recording.file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
}
