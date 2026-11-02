package ch.nthnbch.fossrec.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import ch.nthnbch.fossrec.R
import ch.nthnbch.fossrec.audio.AudioRecorder
import ch.nthnbch.fossrec.data.RecorderSettings
import ch.nthnbch.fossrec.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: RecorderSettings,
    versionName: String,
    onChange: ((RecorderSettings) -> RecorderSettings) -> Unit,
    onBack: () -> Unit
) {
    var showLegal by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(Dimens.iconTarget)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.size(Dimens.icon)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.s5),
            verticalArrangement = Arrangement.spacedBy(Dimens.s5)
        ) {
            Section(stringResource(R.string.settings_section_recording)) {
                InfoRow(Icons.Default.GraphicEq, R.string.settings_format_title, R.string.settings_format_body)
                Text(
                    stringResource(R.string.settings_split_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SplitOption(
                    icon = Icons.Default.Storage,
                    title = R.string.settings_split_size_title,
                    body = R.string.settings_split_size_body,
                    enabled = settings.splitBySize,
                    onEnabledChange = { v -> onChange { it.copy(splitBySize = v) } },
                    presets = RecorderSettings.SIZE_PRESETS_MB,
                    selected = settings.maxSizeMb,
                    unit = R.string.settings_unit_mb,
                    onSelect = { v -> onChange { it.copy(maxSizeMb = v) } },
                    footnote = stringResource(
                        R.string.settings_size_estimate, AudioRecorder.minutesForMegabytes(settings.maxSizeMb)
                    )
                )
                SplitOption(
                    icon = Icons.Default.Timer,
                    title = R.string.settings_split_duration_title,
                    body = R.string.settings_split_duration_body,
                    enabled = settings.splitByDuration,
                    onEnabledChange = { v -> onChange { it.copy(splitByDuration = v) } },
                    presets = RecorderSettings.DURATION_PRESETS_MIN,
                    selected = settings.maxDurationMinutes,
                    unit = R.string.settings_unit_min,
                    onSelect = { v -> onChange { it.copy(maxDurationMinutes = v) } }
                )
                Text(
                    stringResource(R.string.settings_split_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Section(stringResource(R.string.settings_section_security)) {
                InfoRow(Icons.Default.Fingerprint, R.string.settings_lock_title, R.string.settings_lock_body)
                InfoRow(Icons.Default.WifiOff, R.string.settings_local_title, R.string.settings_local_body)
            }

            Section(stringResource(R.string.settings_section_appearance)) {
                InfoRow(Icons.Default.DarkMode, R.string.settings_dark_title, R.string.settings_dark_body)
            }

            Section(stringResource(R.string.settings_section_tips)) {
                InfoRow(Icons.Default.Refresh, R.string.settings_refresh_title, R.string.settings_refresh_body)
                InfoRow(Icons.Default.SwipeLeft, R.string.settings_swipe_title, R.string.settings_swipe_body)
                InfoRow(Icons.Default.Widgets, R.string.settings_widget_title, R.string.settings_widget_body)
            }

            Section(stringResource(R.string.settings_section_legal)) {
                InfoRow(
                    Icons.Default.Gavel, R.string.settings_legal_title, R.string.settings_legal_body,
                    modifier = Modifier.clickable { showLegal = true }
                )
            }

            Section(stringResource(R.string.settings_section_about)) {
                Text(
                    stringResource(R.string.app_name) + " · " + stringResource(R.string.settings_version, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(Dimens.s6))
        }
    }

    if (showLegal) {
        AlertDialog(
            onDismissRequest = { showLegal = false },
            title = { Text(stringResource(R.string.disclaimer_heading)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.disclaimer_body), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLegal = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.s4)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(Dimens.s5),
                verticalArrangement = Arrangement.spacedBy(Dimens.s5),
                content = content
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: Int,
    body: Int,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.s4)) {
        SettingIcon(icon)
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.s1)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(Dimens.icon),
        tint = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitOption(
    icon: ImageVector,
    title: Int,
    body: Int,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    presets: List<Int>,
    selected: Int,
    unit: Int,
    onSelect: (Int) -> Unit,
    footnote: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.s4)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s4), verticalAlignment = Alignment.CenterVertically) {
            SettingIcon(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.s1)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.s3),
                verticalArrangement = Arrangement.spacedBy(Dimens.s3),
                modifier = Modifier.padding(start = Dimens.icon + Dimens.s4)
            ) {
                presets.forEach { value ->
                    FilterChip(
                        selected = value == selected,
                        onClick = { onSelect(value) },
                        label = { Text(stringResource(unit, value), style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.heightIn(min = Dimens.s6 + Dimens.s2)
                    )
                }
            }
            if (footnote != null) {
                Text(
                    footnote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Dimens.icon + Dimens.s4)
                )
            }
        }
    }
}
