package ch.nthnbch.fossrec.ui

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.nthnbch.fossrec.R
import ch.nthnbch.fossrec.data.AudioRecording
import ch.nthnbch.fossrec.data.GroupPosition
import ch.nthnbch.fossrec.data.ListRow
import ch.nthnbch.fossrec.ui.theme.Dimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where the vertical "linked" rail sits inside a row (horizontal centre), and where its dot sits. */
private val RailWidth = Dimens.s6
private val RailDotY = Dimens.s6

/** Draws the line that visually links the parts of a split recording. */
@Composable
private fun Modifier.groupRail(fromTop: Boolean, toBottom: Boolean, dot: Boolean): Modifier {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    return drawBehind {
        val x = RailWidth.toPx() / 2 + Dimens.s3.toPx()
        val dotY = RailDotY.toPx()
        val stroke = 3.dp.toPx()
        drawLine(
            color,
            Offset(x, if (fromTop) 0f else dotY),
            Offset(x, if (toBottom) size.height else dotY),
            strokeWidth = stroke,
            cap = StrokeCap.Butt
        )
        if (dot) drawCircle(color, radius = 6.dp.toPx(), center = Offset(x, dotY))
    }
}

// === Group header ===============================================================

@Composable
fun GroupHeaderItem(header: ListRow.GroupHeader, onDeleteAll: () -> Unit) {
    val context = LocalContext.current
    val summary = stringResource(
        R.string.group_summary,
        header.parts.size,
        formatClock(header.totalDurationMs / 1000),
        Formatter.formatShortFileSize(context, header.totalSizeBytes)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .groupRail(fromTop = false, toBottom = true, dot = false)
            .padding(start = Dimens.s5 - Dimens.s3 + 0.dp, end = Dimens.s5 - Dimens.s3, top = Dimens.s4, bottom = Dimens.s2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Icon(
                Icons.Default.Link,
                contentDescription = stringResource(R.string.linked_parts),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(Dimens.s3)
                    .size(Dimens.s5 + Dimens.s2)
            )
        }
        Spacer(Modifier.width(Dimens.s4))
        Column(Modifier.weight(1f)) {
            Text(
                text = header.groupKey,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDeleteAll, modifier = Modifier.size(Dimens.iconTarget)) {
            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = stringResource(R.string.delete_all),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(Dimens.icon)
            )
        }
    }
}

// === Swipeable row ==============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingRow(
    item: ListRow.Item,
    isPlaying: Boolean,
    playbackPositionMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val grouped = item.position != GroupPosition.NONE
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onDelete()
            false // never dismiss: the confirmation dialog decides
        }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (grouped) Modifier.groupRail(
                    fromTop = true,
                    toBottom = item.position != GroupPosition.LAST,
                    dot = true
                ) else Modifier
            )
    ) {
        if (grouped) Spacer(Modifier.width(RailWidth + Dimens.s3 + Dimens.s3))
        SwipeToDismissBox(
            state = dismissState,
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = if (grouped) 0.dp else Dimens.s5 - Dimens.s3,
                    end = Dimens.s5 - Dimens.s3,
                    top = Dimens.s2,
                    bottom = Dimens.s2
                ),
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(Dimens.icon)
                    )
                }
            }
        ) {
            RecordingItem(
                recording = item.recording,
                title = if (grouped) stringResource(R.string.part_label, item.recording.partIndex)
                else item.recording.name,
                isPlaying = isPlaying,
                playbackPositionMs = playbackPositionMs,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onRename = onRename,
                onDelete = onDelete,
                onShare = onShare
            )
        }
    }
}

// === Recording card =============================================================

@Composable
private fun RecordingItem(
    recording: AudioRecording,
    title: String,
    isPlaying: Boolean,
    playbackPositionMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val dateStr = remember(recording.lastModified) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(recording.lastModified))
    }
    val sizeStr = Formatter.formatShortFileSize(context, recording.sizeBytes)
    val durationStr = formatClock(recording.durationMs / 1000)

    var sliderValue by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(playbackPositionMs, recording.durationMs) {
        if (!isDragging && recording.durationMs > 0) {
            sliderValue = playbackPositionMs.toFloat() / recording.durationMs
        }
    }

    val cardColor by animateColorAsState(
        targetValue = if (isPlaying) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "cardColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = Dimens.s5, end = Dimens.s5, top = Dimens.s4 + Dimens.s2, bottom = Dimens.s3)
        ) {
            // Title + duration badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = Dimens.s3)
                )
                Surface(
                    shape = RoundedCornerShape(Dimens.s5),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = durationStr,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = Dimens.s4, vertical = Dimens.s2),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.height(Dimens.s2))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = sizeStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Seek bar — only while playing
            AnimatedVisibility(visible = isPlaying) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s3)) {
                    Slider(
                        value = sliderValue.coerceIn(0f, 1f),
                        onValueChange = {
                            isDragging = true
                            sliderValue = it
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            onSeek(sliderValue)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s2),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatClock(playbackPositionMs / 1000),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = durationStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = Dimens.s3, bottom = Dimens.s2),
                thickness = 0.5.dp
            )

            // Actions — large glyphs on 55 dp touch targets
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(Dimens.iconTarget),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isPlaying) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                        modifier = Modifier.size(Dimens.icon)
                    )
                }

                Spacer(Modifier.weight(1f))

                ActionIcon(Icons.Default.Share, R.string.share, MaterialTheme.colorScheme.onSurfaceVariant, onShare)
                ActionIcon(Icons.Default.Edit, R.string.rename, MaterialTheme.colorScheme.onSurfaceVariant, onRename)
                ActionIcon(Icons.Default.Delete, R.string.delete, MaterialTheme.colorScheme.error, onDelete)
            }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: Int,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(Dimens.iconTarget)) {
        Icon(icon, contentDescription = stringResource(description), tint = tint, modifier = Modifier.size(Dimens.icon))
    }
}
