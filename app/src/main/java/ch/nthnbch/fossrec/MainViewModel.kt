package ch.nthnbch.fossrec

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.nthnbch.fossrec.audio.AudioPlayer
import ch.nthnbch.fossrec.data.AudioRecording
import ch.nthnbch.fossrec.data.AudioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AudioRepository(application)
    private val audioPlayer = AudioPlayer(application).apply {
        onCompletionListener = {
            _playingRecording.value = null
        }
    }

    private val _recordings = MutableStateFlow<List<AudioRecording>>(emptyList())
    val recordings = _recordings.asStateFlow()

    private val _playingRecording = MutableStateFlow<AudioRecording?>(null)
    val playingRecording = _playingRecording.asStateFlow()

    init {
        loadRecordings()
    }

    fun loadRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.getRecordings()
            _recordings.value = list
        }
    }

    fun playRecording(recording: AudioRecording) {
        if (_playingRecording.value?.file == recording.file) {
            audioPlayer.stop()
            _playingRecording.value = null
        } else {
            audioPlayer.playFile(recording.file)
            _playingRecording.value = recording
        }
    }

    fun stopPlaying() {
        audioPlayer.stop()
        _playingRecording.value = null
    }

    fun deleteRecording(recording: AudioRecording) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_playingRecording.value == recording) {
                stopPlaying()
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
                stopPlaying()
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
