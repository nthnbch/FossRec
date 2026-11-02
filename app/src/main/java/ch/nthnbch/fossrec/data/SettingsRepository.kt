package ch.nthnbch.fossrec.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SharedPreferences-backed settings. Cheap to instantiate; all instances in the process share one
 * observable state, so a change made in the settings screen is seen immediately by the activity.
 */
class SettingsRepository(context: Context) {

    private val prefs = (context.applicationContext ?: context)
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val state: MutableStateFlow<RecorderSettings> = synchronized(lock) {
        shared ?: MutableStateFlow(load()).also { shared = it }
    }
    val settings: StateFlow<RecorderSettings> = state.asStateFlow()

    /** Reads the current values straight from disk. */
    fun load(): RecorderSettings {
        val d = RecorderSettings()
        return RecorderSettings(
            splitBySize = prefs.getBoolean(KEY_SPLIT_SIZE, d.splitBySize),
            maxSizeMb = prefs.getInt(KEY_MAX_SIZE_MB, d.maxSizeMb),
            splitByDuration = prefs.getBoolean(KEY_SPLIT_DURATION, d.splitByDuration),
            maxDurationMinutes = prefs.getInt(KEY_MAX_DURATION_MIN, d.maxDurationMinutes),
            lockEnabled = prefs.getBoolean(KEY_LOCK, d.lockEnabled),
            themeMode = enumOrDefault(prefs.getString(KEY_THEME, null), d.themeMode),
            language = enumOrDefault(prefs.getString(KEY_LANGUAGE, null), d.language)
        )
    }

    fun update(transform: (RecorderSettings) -> RecorderSettings) {
        val new = transform(state.value)
        prefs.edit()
            .putBoolean(KEY_SPLIT_SIZE, new.splitBySize)
            .putInt(KEY_MAX_SIZE_MB, new.maxSizeMb)
            .putBoolean(KEY_SPLIT_DURATION, new.splitByDuration)
            .putInt(KEY_MAX_DURATION_MIN, new.maxDurationMinutes)
            .putBoolean(KEY_LOCK, new.lockEnabled)
            .putString(KEY_THEME, new.themeMode.name)
            .putString(KEY_LANGUAGE, new.language.name)
            .apply()
        state.value = new
    }

    fun isDisclaimerAccepted(): Boolean = prefs.getBoolean(KEY_DISCLAIMER, false)

    fun setDisclaimerAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_DISCLAIMER, accepted).apply()
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        val lock = Any()
        @Volatile var shared: MutableStateFlow<RecorderSettings>? = null

        const val PREFS_NAME = "fossrec_settings"
        const val KEY_SPLIT_SIZE = "split_by_size"
        const val KEY_MAX_SIZE_MB = "max_size_mb"
        const val KEY_SPLIT_DURATION = "split_by_duration"
        const val KEY_MAX_DURATION_MIN = "max_duration_min"
        const val KEY_LOCK = "lock_enabled"
        const val KEY_THEME = "theme_mode"
        const val KEY_LANGUAGE = "language"
        const val KEY_DISCLAIMER = "disclaimer_accepted"
    }
}
