package ch.nthnbch.fossrec

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.nthnbch.fossrec.audio.AudioPlayer
import ch.nthnbch.fossrec.data.AudioRecording
import ch.nthnbch.fossrec.data.AudioRepository
import ch.nthnbch.fossrec.data.ListRow
import ch.nthnbch.fossrec.data.RecorderSettings
import ch.nthnbch.fossrec.data.SettingsRepository
import ch.nthnbch.fossrec.data.UpdateChecker
import ch.nthnbch.fossrec.data.buildRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortMode {
    DATE_DESC, DATE_ASC, DURATION_DESC, DURATION_ASC, NAME_ASC, NAME_DESC
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AudioRepository(application)
    private val settingsRepository = SettingsRepository(application)

    val settings: StateFlow<RecorderSettings> = settingsRepository.settings

    fun updateSettings(transform: (RecorderSettings) -> RecorderSettings) =
        settingsRepository.update(transform)

    private val _update = MutableStateFlow<UpdateChecker.Release?>(null)
    /** A newer release than the installed one, or null (also when checks are off). */
    val update: StateFlow<UpdateChecker.Release?> = _update.asStateFlow()

    /** Result of a manual check: null = not run, true = found a newer one, false = none / unreachable. */
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()
    private val _checkResult = MutableStateFlow<UpdateResult?>(null)
    val checkResult: StateFlow<UpdateResult?> = _checkResult.asStateFlow()

    enum class UpdateResult { UP_TO_DATE, AVAILABLE, FAILED }

    private val currentVersion: String = runCatching {
        application.packageManager.getPackageInfo(application.packageName, 0).versionName
    }.getOrNull().orEmpty()

    fun checkForUpdate() {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            val release = UpdateChecker.latest()
            _checking.value = false
            if (release == null) {
                _checkResult.value = UpdateResult.FAILED
            } else if (UpdateChecker.isNewer(release.version, currentVersion)) {
                _update.value = release
                _checkResult.value = UpdateResult.AVAILABLE
            } else {
                _update.value = null
                _checkResult.value = UpdateResult.UP_TO_DATE
            }
        }
    }

    fun dismissUpdate() { _update.value = null }

    init {
        // Automatic check at launch, only when the user opted in.
        if (settingsRepository.load().updateCheckEnabled) checkForUpdate()
    }

    private val audioPlayer = AudioPlayer(application).apply {
        onCompletionListener = {
            _playingRecording.value = null
            _playbackPositionMs.value = 0L
            progressJob?.cancel()
        }
    }

    private val _recordings = MutableStateFlow<List<AudioRecording>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DATE_DESC)
    val sortMode = _sortMode.asStateFlow()

    /** Recordings after search and sort, with the parts of a split session gathered under one header. */
    val rows: StateFlow<List<ListRow>> = combine(
        _recordings, _searchQuery, _sortMode
    ) { recordings, query, sort ->
        recordings
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .let { filtered ->
                when (sort) {
                    SortMode.DATE_DESC -> filtered.sortedByDescending { it.lastModified }
                    SortMode.DATE_ASC -> filtered.sortedBy { it.lastModified }
                    SortMode.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
                    SortMode.DURATION_ASC -> filtered.sortedBy { it.durationMs }
                    SortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
                    SortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
                }
            }
            .let(::buildRows)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _playingRecording = MutableStateFlow<AudioRecording?>(null)
    val playingRecording = _playingRecording.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs = _playbackPositionMs.asStateFlow()

    private var progressJob: Job? = null

    init {
        loadRecordings()
        // Refresh the list when a recording starts a new part or finishes, so new files show up at once.
        viewModelScope.launch {
            RecordingService.isRecording
                .drop(1) // skip initial emission
                .collect { isRecording ->
                    if (!isRecording) {
                        delay(300) // let the recorder flush & close the file
                        loadRecordings()
                    }
                }
        }
        viewModelScope.launch {
            RecordingService.currentPart
                .drop(1)
                .collect {
                    delay(300)
                    loadRecordings()
                }
        }
    }

    fun loadRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.getRecordings()
            _recordings.value = list
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun playRecording(recording: AudioRecording) {
        if (_playingRecording.value?.file == recording.file) {
            audioPlayer.stop()
            _playingRecording.value = null
            progressJob?.cancel()
            _playbackPositionMs.value = 0L
        } else {
            audioPlayer.playFile(recording.file)
            _playingRecording.value = recording
            startProgressTracking()
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                _playbackPositionMs.value = audioPlayer.getCurrentPosition().toLong()
                delay(200)
            }
        }
    }

    fun seekTo(fraction: Float) {
        val recording = _playingRecording.value ?: return
        val targetMs = (fraction * recording.durationMs).toInt()
        audioPlayer.seekTo(targetMs)
        _playbackPositionMs.value = targetMs.toLong()
    }

    fun stopPlaying() {
        audioPlayer.stop()
        _playingRecording.value = null
        progressJob?.cancel()
        _playbackPositionMs.value = 0L
    }

    fun deleteRecording(recording: AudioRecording) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_playingRecording.value == recording) {
                withContext(Dispatchers.Main) { stopPlaying() }
            }
            val success = repository.deleteRecording(recording.file)
            if (success) {
                loadRecordings()
            }
        }
    }

    fun deleteRecordings(recordings: List<AudioRecording>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_playingRecording.value in recordings) {
                withContext(Dispatchers.Main) { stopPlaying() }
            }
            recordings.forEach { repository.deleteRecording(it.file) }
            loadRecordings()
        }
    }

    fun renameRecording(recording: AudioRecording, newName: String) {
        if (newName.isBlank() || newName == recording.name) return
        viewModelScope.launch(Dispatchers.IO) {
            if (_playingRecording.value == recording) {
                withContext(Dispatchers.Main) { stopPlaying() }
            }
            val success = repository.renameRecording(recording.file, newName)
            if (success) {
                loadRecordings()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
    }
}
