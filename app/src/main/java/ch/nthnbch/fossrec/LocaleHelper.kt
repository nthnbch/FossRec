package ch.nthnbch.fossrec

import android.content.Context
import android.content.res.Configuration
import ch.nthnbch.fossrec.data.AppLanguage
import ch.nthnbch.fossrec.data.SettingsRepository
import java.util.Locale

/** Applies the language chosen in Settings (or the phone's language) to a Context. */
object LocaleHelper {

    fun wrap(base: Context): Context {
        val language = SettingsRepository(base).load().language
        if (language == AppLanguage.SYSTEM) {
            // Context handed in by the system already carries the phone's locale.
            Locale.setDefault(base.resources.configuration.locales[0])
            return base
        }
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
