package ch.nthnbch.fossrec

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.nthnbch.fossrec.data.AudioRecording
import ch.nthnbch.fossrec.ui.theme.FossRecTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FossRecTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload list when returning to the app
        val viewModel = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.loadRecordings()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val playingRecording by viewModel.playingRecording.collectAsStateWithLifecycle()
    
    val isRecording by RecordingService.isRecording.collectAsStateWithLifecycle()
    val isPaused by RecordingService.isPaused.collectAsStateWithLifecycle()
    val recordingDuration by RecordingService.recordingDurationSeconds.collectAsStateWithLifecycle()

    var showPermissionRationale by remember { mutableStateOf(false) }

    val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val audioGranted = perms[Manifest.permission.RECORD_AUDIO] ?: false
        if (audioGranted) {
           toggleRecording(context, isRecording)
        } else {
            showPermissionRationale = true
        }
    }

    var selectedRecordingForRename by remember { mutableStateOf<AudioRecording?>(null) }
    var selectedRecordingForDelete by remember { mutableStateOf<AudioRecording?>(null) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.app_name), 
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    ) 
                }
            )
        },
        floatingActionButton = {
            if (isRecording) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FloatingActionButton(
                        onClick = { togglePause(context, isPaused) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    LargeFloatingActionButton(
                        onClick = { toggleRecording(context, true) },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.stop), modifier = Modifier.size(36.dp))
                    }
                }
            } else {
                LargeFloatingActionButton(
                    onClick = {
                        val hasAudioPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        
                        if (hasAudioPerm) {
                            toggleRecording(context, false)
                        } else {
                            launcher.launch(permissions.toTypedArray())
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.record), modifier = Modifier.size(36.dp))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(visible = isRecording) {
                RecordingIndicator(durationSeconds = recordingDuration, isPaused = isPaused)
            }

            if (showPermissionRationale) {
                PermissionRationale { showPermissionRationale = false }
            }

            if (recordings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_recordings),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp) // space for FAB
                ) {
                    items(recordings, key = { it.file.absolutePath }) { recording ->
                        val isItemPlaying = playingRecording == recording
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                    selectedRecordingForDelete = recording
                                    false // Don't dismiss immediately, let the dialog handle it
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color = MaterialTheme.colorScheme.errorContainer
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .background(color, MaterialTheme.shapes.medium),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            },
                            content = {
                                RecordingItem(
                                    recording = recording,
                                    isPlaying = isItemPlaying,
                                    onPlayPause = { viewModel.playRecording(recording) },
                                    onRename = { selectedRecordingForRename = recording },
                                    onDelete = { selectedRecordingForDelete = recording },
                                    onShare = { shareRecording(context, recording) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    selectedRecordingForRename?.let { rec ->
        RenameDialog(
            currentName = rec.name,
            onDismiss = { selectedRecordingForRename = null },
            onConfirm = { newName ->
                viewModel.renameRecording(rec, newName)
                selectedRecordingForRename = null
            }
        )
    }

    selectedRecordingForDelete?.let { rec ->
        DeleteDialog(
            onDismiss = { selectedRecordingForDelete = null },
            onConfirm = {
                viewModel.deleteRecording(rec)
                selectedRecordingForDelete = null
            }
        )
    }
}

@Composable
fun RecordingIndicator(durationSeconds: Long, isPaused: Boolean) {
    val formatTime = String.format(Locale.getDefault(), "%02d:%02d", durationSeconds / 60, durationSeconds % 60)
    Surface(
        color = if (isPaused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isPaused) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                } else {
                    Icon(Icons.Default.PauseCircle, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                val text = if (isPaused) "Enregistrement en pause" else stringResource(R.string.recording_in_progress)
                Text(text, fontWeight = FontWeight.Bold)
            }
            Text(formatTime, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun RecordingItem(
    recording: AudioRecording,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val df = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val dateStr = df.format(Date(recording.lastModified))
    val sizeStr = Formatter.formatShortFileSize(context, recording.sizeBytes)
    val durationSeconds = recording.durationMs / 1000
    val durationStr = String.format(Locale.getDefault(), "%02d:%02d", durationSeconds / 60, durationSeconds % 60)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onPlayPause),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = recording.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                    tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$durationStr • $sizeStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isPlaying) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalIconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(onClick = onRename, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename))
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRationale(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.permission_required),
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
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
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun DeleteDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.delete_confirmation)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

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
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        recording.file
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
}
