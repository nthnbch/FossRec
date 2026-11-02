package ch.nthnbch.fossrec

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import ch.nthnbch.fossrec.data.SettingsRepository
import ch.nthnbch.fossrec.ui.DisclaimerScreen
import ch.nthnbch.fossrec.ui.LockScreen
import ch.nthnbch.fossrec.ui.MainScreen
import ch.nthnbch.fossrec.ui.theme.FossRecTheme

/**
 * Gate order: legal notice (first launch only) → biometric / device-credential lock → app.
 * The lock re-arms every time the app leaves the foreground.
 */
class MainActivity : FragmentActivity() {

    private val isAuthenticated = mutableStateOf(false)
    private val authError = mutableStateOf<String?>(null)
    private val disclaimerAccepted = mutableStateOf(false)
    private var authInProgress = false

    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsRepository = SettingsRepository(this)
        disclaimerAccepted.value = settingsRepository.isDisclaimerAccepted()

        setContent {
            FossRecTheme {
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

        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No biometric or screen lock configured on this phone: nothing to unlock with.
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
