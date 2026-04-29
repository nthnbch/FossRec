package ch.nthnbch.fossrec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ch.nthnbch.fossrec.audio.AudioRecorder
import ch.nthnbch.fossrec.data.AudioRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class RecordingService : Service() {

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var audioRepository: AudioRepository
    private var currentRecordingFile: File? = null

    private var recordingJob: Job? = null
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

        private val _recordingDurationSeconds = MutableStateFlow(0L)
        val recordingDurationSeconds = _recordingDurationSeconds.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioRecorder(this)
        audioRepository = AudioRepository(this)
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

    private fun startRecording() {
        if (_isRecording.value) return

        currentRecordingFile = audioRepository.createNewRecordingFile()
        currentRecordingFile?.let { file ->
            audioRecorder.start(file)
            _isRecording.value = true
            _isPaused.value = false
            _recordingDurationSeconds.value = 0L

            startForeground(NOTIFICATION_ID, buildNotification(0L, false))

            recordingJob = scope.launch {
                while (isActive) {
                    delay(1000)
                    if (!_isPaused.value) {
                        _recordingDurationSeconds.value += 1
                        updateNotification()
                    }
                }
            }
        }
    }

    private fun stopRecording() {
        if (!_isRecording.value) return

        recordingJob?.cancel()
        recordingJob = null
        audioRecorder.stop()
        _isRecording.value = false
        _isPaused.value = false
        _recordingDurationSeconds.value = 0L
        currentRecordingFile = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        audioRecorder.pause()
        _isPaused.value = true
        updateNotification()
    }

    private fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        audioRecorder.resume()
        _isPaused.value = false
        updateNotification()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(_recordingDurationSeconds.value, _isPaused.value))
    }

    private fun buildNotification(durationSeconds: Long, isPaused: Boolean): Notification {
        val formatTime = String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60)

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        val pauseResumeActionName = if (isPaused) "Resume" else "Pause"
        val pauseResumeIntent = Intent(this, RecordingService::class.java).apply {
            action = if (isPaused) ACTION_RESUME_RECORDING else ACTION_PAUSE_RECORDING
        }
        val pauseResumePendingIntent = PendingIntent.getService(this, 1, pauseResumeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val stateText = if (isPaused) "Paused" else "Recording"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_service_notification_title) + " ($stateText)")
            .setContentText(formatTime + " - " + getString(R.string.tap_to_stop))
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, pauseResumeActionName, pauseResumePendingIntent)
            .addAction(0, getString(R.string.stop), stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.recording_service_channel_name)
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (_isRecording.value) {
            stopRecording()
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
