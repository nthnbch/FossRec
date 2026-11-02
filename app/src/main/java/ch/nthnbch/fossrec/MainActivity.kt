package ch.nthnbch.fossrec

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import ch.nthnbch.fossrec.data.SettingsRepository
import ch.nthnbch.fossrec.data.ThemeMode
import ch.nthnbch.fossrec.widget.RecordingWidgets
import ch.nthnbch.fossrec.ui.DisclaimerScreen
import ch.nthnbch.fossrec.ui.LockScreen
import ch.nthnbch.fossrec.ui.MainScreen
import ch.nthnbch.fossrec.ui.theme.FossRecTheme

/**
 * Gate order: legal notice (first launch only) → optional biometric / device-credential lock → app.
 * The lock re-arms every time the app leaves the foreground.
 */
class MainActivity : FragmentActivity() {

    private val isAuthenticated = mutableStateOf(false)
    private val authError = mutableStateOf<String?>(null)
    private val disclaimerAccepted = mutableStateOf(false)
    private var authInProgress = false

    private lateinit var settingsRepository: SettingsRepository

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(this)
        disclaimerAccepted.value = settingsRepository.isDisclaimerAccepted()
        val startLanguage = settingsRepository.load().language

        setContent {
            val settings by settingsRepository.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.AUTO -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Keep status / navigation bar icons readable when the theme differs from the phone's.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xe6, 0xFF, 0xFF, 0xFF), Color.argb(0x80, 0x1b, 0x1b, 0x1b)
                    ) { darkTheme }
                )
                onDispose {}
            }

            // A new language needs the activity to reload its resources.
            LaunchedEffect(settings.language) {
                if (settings.language != startLanguage) {
                    RecordingWidgets.refresh(applicationContext)
                    recreate()
                }
            }

            FossRecTheme(darkTheme = darkTheme) {
                val accepted by disclaimerAccepted
                val authenticated by isAuthenticated
                val error by authError
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        !accepted -> DisclaimerScreen(
                            onAccept = {
                                settingsRepository.setDisclaimerAccepted(true)
                                disclaimerAccepted.value = true
                                authenticate()
                            },
                            onDecline = { finishAffinity() }
                        )
                        authenticated -> MainScreen()
                        else -> LockScreen(error = error, onUnlock = { authenticate() })
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        isAuthenticated.value = false
        authError.value = null
    }

    override fun onResume() {
        super.onResume()
        if (!disclaimerAccepted.value) return
        ViewModelProvider(this)[MainViewModel::class.java].loadRecordings()
        if (!isAuthenticated.value) authenticate()
    }

    private fun authenticate() {
        if (authInProgress) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        // Lock is opt-in (Settings). Also skip it if the phone has no biometric / screen lock to unlock with.
        if (!settingsRepository.load().lockEnabled ||
            BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            isAuthenticated.value = true
            return
        }

        authInProgress = true
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authInProgress = false
                isAuthenticated.value = true
                authError.value = null
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authInProgress = false
                authError.value = errString.toString()
            }
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(promptInfo)
    }
}
