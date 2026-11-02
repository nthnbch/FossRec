package ch.nthnbch.fossrec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import ch.nthnbch.fossrec.audio.AudioRecorder
import ch.nthnbch.fossrec.data.AudioRepository
import ch.nthnbch.fossrec.data.RecorderSettings
import ch.nthnbch.fossrec.data.SettingsRepository
import ch.nthnbch.fossrec.widget.RecordingWidgetProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

class RecordingService : Service() {

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var audioRepository: AudioRepository
    private lateinit var settingsRepository: SettingsRepository

    private var settings = RecorderSettings()
    private var sessionName = ""
    private var currentFile: File? = null
    private var segmentSeconds = 0L

    private var tickJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val ACTION_START_RECORDING = "START_RECORDING"
        const val ACTION_STOP_RECORDING = "STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "RESUME_RECORDING"
        const val NOTIFICATION_ID = 1

        private const val CHANNEL_ID = "recording_channel"

        private val _isRecording = MutableStateFlow(false)
        val isRecording = _isRecording.asStateFlow()

        private val _isPaused = MutableStateFlow(false)
        val isPaused = _isPaused.asStateFlow()

        /** Total recorded time of the session (all parts), pauses excluded. */
        private val _recordingDurationSeconds = MutableStateFlow(0L)
        val recordingDurationSeconds = _recordingDurationSeconds.asStateFlow()

        /** 1-based number of the part currently being written. */
        private val _currentPart = MutableStateFlow(1)
        val currentPart = _currentPart.asStateFlow()

        /** True only if the session has been split at least once. */
        private val _isSplit = MutableStateFlow(false)
        val isSplit = _isSplit.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioRecorder(this)
        audioRepository = AudioRepository(this)
        settingsRepository = SettingsRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_PAUSE_RECORDING -> pauseRecording()
            ACTION_RESUME_RECORDING -> resumeRecording()
        }
        return START_NOT_STICKY
    }

    // --- Session lifecycle -------------------------------------------------------

    private fun startRecording() {
        if (_isRecording.value) return

        settings = settingsRepository.load()
        sessionName = audioRepository.newSessionName()
        _currentPart.value = 1
        _isSplit.value = false
        _recordingDurationSeconds.value = 0L

        // Must be called quickly after startForegroundService().
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!beginSegment()) {
            Toast.makeText(this, R.string.recording_failed, Toast.LENGTH_LONG).show()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        _isRecording.value = true
        _isPaused.value = false
        RecordingWidgetProvider.refresh(this)

        tickJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (_isPaused.value) continue
                _recordingDurationSeconds.value += 1
                segmentSeconds += 1
                if (shouldSplit() && !rotateSegment()) {
                    stopRecording()
                    return@launch
                }
                updateNotification()
            }
        }
    }

    private fun stopRecording() {
        if (!_isRecording.value) return

        tickJob?.cancel()
        tickJob = null
        finishSegment()

        _isRecording.value = false
        _isPaused.value = false
        _recordingDurationSeconds.value = 0L
        currentFile = null
        RecordingWidgetProvider.refresh(this)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        audioRecorder.pause()
        _isPaused.value = true
        updateNotification()
        RecordingWidgetProvider.refresh(this)
    }

    private fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        audioRecorder.resume()
        _isPaused.value = false
        updateNotification()
        RecordingWidgetProvider.refresh(this)
    }

    // --- Segments (split by size / duration) -------------------------------------

    private fun beginSegment(): Boolean {
        val part = _currentPart.value
        val file = audioRepository.fileFor(sessionName, if (settings.splitEnabled) part else null)
        segmentSeconds = 0L
        currentFile = file
        return audioRecorder.start(file)
    }

    /** Closes the current file; drops it if the recorder produced nothing usable. */
    private fun finishSegment() {
        val file = currentFile ?: return
        val valid = audioRecorder.stop()
        if (!valid || !file.exists() || file.length() == 0L) {
            file.delete()
        } else if (settings.splitEnabled && !_isSplit.value) {
            // Never actually split: keep a plain file name.
            audioRepository.unsplit(file)
        }
    }

    private fun shouldSplit(): Boolean {
        if (!settings.splitEnabled) return false
        val byDuration = settings.maxDurationSeconds > 0 && segmentSeconds >= settings.maxDurationSeconds
        val bySize = settings.maxSizeBytes > 0 && (currentFile?.length() ?: 0L) >= settings.maxSizeBytes
        return byDuration || bySize
    }

    /** Saves the current part and immediately starts the next one. */
    private fun rotateSegment(): Boolean {
        _isSplit.value = true
        finishSegment()
        _currentPart.value += 1
        return beginSegment()
    }

    // --- Notification ------------------------------------------------------------

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val seconds = _recordingDurationSeconds.value
        val isPaused = _isPaused.value
        val clock = if (seconds >= 3600) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
        }
        val partPrefix = if (_isSplit.value) getString(R.string.part_label, _currentPart.value) + " · " else ""

        val stopPendingIntent = servicePendingIntent(0, ACTION_STOP_RECORDING)
        val pauseResumePendingIntent = servicePendingIntent(
            1, if (isPaused) ACTION_RESUME_RECORDING else ACTION_PAUSE_RECORDING
        )
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        val stateText = getString(if (isPaused) R.string.notification_state_paused else R.string.notification_state_recording)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_service_notification_title) + " ($stateText)")
            .setContentText("$partPrefix$clock - ${getString(R.string.tap_to_stop)}")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, getString(if (isPaused) R.string.resume else R.string.pause), pauseResumePendingIntent)
            .addAction(0, getString(R.string.stop), stopPendingIntent)
            .build()
    }

    private fun servicePendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.recording_service_channel_name), NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        if (_isRecording.value) stopRecording()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
