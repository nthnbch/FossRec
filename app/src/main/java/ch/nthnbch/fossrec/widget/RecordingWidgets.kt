package ch.nthnbch.fossrec.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import ch.nthnbch.fossrec.LocaleHelper
import ch.nthnbch.fossrec.MainActivity
import ch.nthnbch.fossrec.R
import ch.nthnbch.fossrec.RecordingService
import ch.nthnbch.fossrec.data.SettingsRepository

/** Mini widget (1×1): a single record / stop button, with a small timer while recording. */
class MiniWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) =
        RecordingWidgets.refresh(context)
}

/** Large widget (4×2): status, big timer, pause / resume and record / stop. */
class LargeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) =
        RecordingWidgets.refresh(context)
}

object RecordingWidgets {

    /** Redraws every FossRec widget on the home screen. Call whenever the recording state changes. */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val localized = LocaleHelper.wrap(context)
        update(manager, context, MiniWidgetProvider::class.java) { mini(localized) }
        update(manager, context, LargeWidgetProvider::class.java) { large(localized) }
    }

    private fun update(manager: AppWidgetManager, context: Context, provider: Class<*>, views: () -> RemoteViews) {
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isNotEmpty()) manager.updateAppWidget(ids, views())
    }

    private class State(val recording: Boolean, val paused: Boolean, val seconds: Long)

    private fun state() = State(
        RecordingService.isRecording.value,
        RecordingService.isPaused.value,
        RecordingService.recordingDurationSeconds.value
    )

    private fun mini(context: Context): RemoteViews {
        val s = state()
        val views = RemoteViews(context.packageName, R.layout.widget_mini)
        bindMainButton(context, views, s)
        bindTimer(views, s, showWhenIdle = false)
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        return views
    }

    private fun large(context: Context): RemoteViews {
        val s = state()
        val views = RemoteViews(context.packageName, R.layout.widget_large)
        bindMainButton(context, views, s)
        bindTimer(views, s, showWhenIdle = true)

        views.setTextViewText(
            R.id.widget_status,
            context.getString(
                when {
                    !s.recording -> R.string.widget_idle
                    s.paused -> R.string.notification_state_paused
                    else -> R.string.notification_state_recording
                }
            )
        )

        // Pause / resume only exists while recording.
        views.setViewVisibility(R.id.widget_pause, if (s.recording) View.VISIBLE else View.INVISIBLE)
        views.setImageViewResource(R.id.widget_pause_icon, if (s.paused) R.drawable.ic_mic else R.drawable.ic_pause)
        views.setContentDescription(R.id.widget_pause, context.getString(if (s.paused) R.string.resume else R.string.pause))
        views.setOnClickPendingIntent(
            R.id.widget_pause,
            servicePending(context, 12, if (s.paused) RecordingService.ACTION_RESUME_RECORDING else RecordingService.ACTION_PAUSE_RECORDING)
        )
        views.setOnClickPendingIntent(R.id.widget_info, openApp(context))
        return views
    }

    private fun bindMainButton(context: Context, views: RemoteViews, s: State) {
        views.setImageViewResource(R.id.widget_button_icon, if (s.recording) R.drawable.ic_stop else R.drawable.ic_mic)
        views.setInt(
            R.id.widget_button, "setBackgroundResource",
            if (s.recording) R.drawable.widget_button_recording else R.drawable.widget_button_idle
        )
        views.setContentDescription(R.id.widget_button, context.getString(if (s.recording) R.string.stop else R.string.record))
        views.setOnClickPendingIntent(R.id.widget_button, mainButtonIntent(context, s.recording))
    }

    private fun bindTimer(views: RemoteViews, s: State, showWhenIdle: Boolean) {
        val visible = s.recording || showWhenIdle
        views.setViewVisibility(R.id.widget_timer, if (visible) View.VISIBLE else View.GONE)
        // The chronometer counts from `base`; stopped, it shows the time frozen at the last update.
        val base = SystemClock.elapsedRealtime() - (if (s.recording) s.seconds * 1000 else 0L)
        views.setChronometer(R.id.widget_timer, base, null, s.recording && !s.paused)
    }

    private fun mainButtonIntent(context: Context, recording: Boolean): PendingIntent {
        if (recording) return servicePending(context, 10, RecordingService.ACTION_STOP_RECORDING)

        val canRecord = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED && SettingsRepository(context).isDisclaimerAccepted()
        // Without microphone permission (or before the legal notice was accepted) open the app instead.
        if (!canRecord) return openApp(context)

        val start = Intent(context, RecordingService::class.java).apply { action = RecordingService.ACTION_START_RECORDING }
        return PendingIntent.getForegroundService(context, 11, start, FLAGS)
    }

    private fun servicePending(context: Context, requestCode: Int, action: String): PendingIntent {
        val intent = Intent(context, RecordingService::class.java).apply { this.action = action }
        return PendingIntent.getService(context, requestCode, intent, FLAGS)
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 13, intent, FLAGS)
    }

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
}
