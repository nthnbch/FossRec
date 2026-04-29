package ch.nthnbch.fossrec

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.nthnbch.fossrec.audio.AudioPlayer
import ch.nthnbch.fossrec.data.AudioRecording
import ch.nthnbch.fossrec.data.AudioRepository
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

    val filteredAndSortedRecordings: StateFlow<List<AudioRecording>> = combine(
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
