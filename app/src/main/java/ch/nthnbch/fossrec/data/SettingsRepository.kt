package ch.nthnbch.fossrec.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** SharedPreferences-backed settings. Cheap to instantiate; every instance reads the same store. */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<RecorderSettings> = _settings.asStateFlow()

    /** Reads the current values straight from disk (used by the recording service at each start). */
    fun load(): RecorderSettings {
        val d = RecorderSettings()
        return RecorderSettings(
            splitBySize = prefs.getBoolean(KEY_SPLIT_SIZE, d.splitBySize),
            maxSizeMb = prefs.getInt(KEY_MAX_SIZE_MB, d.maxSizeMb),
            splitByDuration = prefs.getBoolean(KEY_SPLIT_DURATION, d.splitByDuration),
            maxDurationMinutes = prefs.getInt(KEY_MAX_DURATION_MIN, d.maxDurationMinutes)
        )
    }

    fun update(transform: (RecorderSettings) -> RecorderSettings) {
        val new = transform(_settings.value)
        prefs.edit()
            .putBoolean(KEY_SPLIT_SIZE, new.splitBySize)
            .putInt(KEY_MAX_SIZE_MB, new.maxSizeMb)
            .putBoolean(KEY_SPLIT_DURATION, new.splitByDuration)
            .putInt(KEY_MAX_DURATION_MIN, new.maxDurationMinutes)
            .apply()
        _settings.value = new
    }

    fun isDisclaimerAccepted(): Boolean = prefs.getBoolean(KEY_DISCLAIMER, false)

    fun setDisclaimerAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_DISCLAIMER, accepted).apply()
    }

    private companion object {
        const val PREFS_NAME = "fossrec_settings"
        const val KEY_SPLIT_SIZE = "split_by_size"
        const val KEY_MAX_SIZE_MB = "max_size_mb"
        const val KEY_SPLIT_DURATION = "split_by_duration"
        const val KEY_MAX_DURATION_MIN = "max_duration_min"
        const val KEY_DISCLAIMER = "disclaimer_accepted"
    }
}
