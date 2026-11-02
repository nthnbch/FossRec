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
import ch.nthnbch.fossrec.MainActivity
import ch.nthnbch.fossrec.R
import ch.nthnbch.fossrec.RecordingService
import ch.nthnbch.fossrec.data.SettingsRepository

/** Home-screen widget: one big button to start / stop a recording, and a live timer. */
class RecordingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, buildViews(context))
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RecordingWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, buildViews(context))
        }

        private fun buildViews(context: Context): RemoteViews {
            val recording = RecordingService.isRecording.value
            val paused = RecordingService.isPaused.value
            val seconds = RecordingService.recordingDurationSeconds.value

            val views = RemoteViews(context.packageName, R.layout.widget_recording)

            views.setImageViewResource(
                R.id.widget_button_icon, if (recording) R.drawable.ic_stop else R.drawable.ic_mic
            )
            views.setInt(
                R.id.widget_button, "setBackgroundResource",
                if (recording) R.drawable.widget_button_recording else R.drawable.widget_button_idle
            )
            views.setContentDescription(
                R.id.widget_button,
                context.getString(if (recording) R.string.stop else R.string.record)
            )

            if (recording) {
                views.setTextViewText(
                    R.id.widget_status,
                    context.getString(if (paused) R.string.notification_state_paused else R.string.notification_state_recording)
                )
                views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
                views.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime() - seconds * 1000, null, !paused)
            } else {
                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_idle))
                views.setViewVisibility(R.id.widget_timer, View.GONE)
                views.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime(), null, false)
            }

            views.setOnClickPendingIntent(R.id.widget_button, buttonIntent(context, recording))
            views.setOnClickPendingIntent(R.id.widget_info, openAppIntent(context))
            return views
        }

        private fun buttonIntent(context: Context, recording: Boolean): PendingIntent {
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            if (recording) {
                val stop = Intent(context, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_STOP_RECORDING
                }
                return PendingIntent.getService(context, 10, stop, flags)
            }
            val canRecord = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED && SettingsRepository(context).isDisclaimerAccepted()
            // Without microphone permission (or before the legal notice was accepted) open the app instead.
            if (!canRecord) return openAppIntent(context)
            val start = Intent(context, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START_RECORDING
            }
            return PendingIntent.getForegroundService(context, 11, start, flags)
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(context, 12, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
