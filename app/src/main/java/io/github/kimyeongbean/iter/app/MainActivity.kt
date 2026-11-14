package io.github.kimyeongbean.iter.app

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.launch

/**
 * Runtime permissions the drive session recorder needs: vehicle speed (#18), from Android 13 the
 * recording service's notification (#125), and location only if the user agreed to it (#127).
 */
private fun recordingPermissions(withLocation: Boolean) = buildList {
    add("android.car.permission.CAR_SPEED")
    if (withLocation) {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

/** Whether the collection notice shows, and whether it asks or is only read again (#127). */
private enum class Notice { NONE, ASK, READ }

private const val PREFS = "recording"
private const val PREF_BACKGROUND_LOCATION_ASKED = "background_location_asked"

class MainActivity : ComponentActivity() {
    /** Records whatever was granted: a denied signal is recorded as missing. */
    private val askRecordingPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            RecordingService.start(this)
            offerBackgroundLocation()
        }

    private val askBackgroundLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val app get() = application as IterApplication

    /** The user's answer to the collection notice; nothing records before they agree (#127). */
    private var consent by mutableStateOf(RecordingConsent())
    private var notice by mutableStateOf(Notice.NONE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = app
        consent = app.consentStore.load()
        if (consent.agreed) startRecording()
        if (consent.answer == RecordingConsent.Answer.NOT_ASKED) notice = Notice.ASK
        setContent {
            // Held above the app theme: an advanced theme sets the app colours and the backdrop.
            var settings by remember { mutableStateOf(app.speedColorSettingsStore.load()) }
            val dark = settings.screenMode.isDark(isSystemInDarkTheme())
            val art = settings.theme.art
            // The screens ask the system whether it is dark: give them the chosen screen mode's answer.
            val systemConfiguration = LocalConfiguration.current
            val shownConfiguration =
                remember(systemConfiguration, dark) {
                    Configuration(systemConfiguration).apply {
                        val night = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                    }
                }
            CompositionLocalProvider(LocalConfiguration provides shownConfiguration) {
                IterTheme(dark, settings.theme.appColors(dark)) {
                    Surface {
                        // Decoration only, and still: it reads no state that changes while a theme is on.
                        if (art != null) Canvas(Modifier.fillMaxSize()) { with(art) { drawBackdrop(dark) } }
                        if (notice != Notice.NONE) {
                            ConsentScreen(
                                accent = Color(settings.accentArgb(dark)),
                                art = art,
                                onAnswer = if (notice == Notice.ASK) ::answer else null,
                                onClose = { notice = Notice.NONE },
                            )
                            return@Surface
                        }
                        val driving by app.driveSessionRecorder.driving.collectAsState()
                        val animationsOff = remember { animatorDurationScale() == 0f }
                        // Asked once per activity; the answer is for the whole process.
                        val splashPlayed = remember { app.splashPlayed.also { app.splashPlayed = true } }
                        SplashThenApp(settings.theme, settings.accentArgb(dark), dark, skip = splashPlayed || driving || animationsOff) {
                            IterApp(
                                recordStore = app.recordStore,
                                recorder = app.driveSessionRecorder,
                                settings = settings,
                                onSettingsChange = {
                                    settings = it
                                    app.speedColorSettingsStore.save(it)
                                },
                                appScope = app.appScope,
                                showDebugMenu = BuildConfig.DEBUG,
                                animationsOff = animationsOff,
                                privacy =
                                PrivacyActions(
                                    consent = consent,
                                    onShowNotice = { notice = Notice.READ },
                                    onAgree = { notice = Notice.ASK },
                                    onWithdraw = {
                                        app.appScope.launch {
                                            app.withdrawConsent()
                                            consent = app.consentStore.load()
                                        }
                                    },
                                ),
                                onStartRecording = {
                                    app.requestManualStart()
                                    // Not agreed yet: the notice comes first, and agreeing sends the start.
                                    if (!consent.agreed) notice = Notice.ASK
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    /** Keeps the user's answer to the notice and, if they agreed, starts recording. */
    private fun answer(answer: RecordingConsent) {
        app.consentStore.save(answer)
        consent = answer
        notice = Notice.NONE
        if (answer.agreed) startRecording() else app.cancelManualStart()
    }

    /** Asks for what is still missing, then starts the recording service with whatever was granted. */
    private fun startRecording() {
        val missing = recordingPermissions(consent.recordsLocation).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            RecordingService.start(this)
            offerBackgroundLocation()
        } else {
            askRecordingPermissions.launch(missing.toTypedArray())
        }
    }

    /**
     * Once, after location was allowed while in use: explains why recording without the screen
     * wants location always, then asks. Declining keeps recording, without location (#125).
     */
    private fun offerBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !consent.recordsLocation) return
        val background = Manifest.permission.ACCESS_BACKGROUND_LOCATION
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        if (checkSelfPermission(background) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(PREF_BACKGROUND_LOCATION_ASKED, false)) return
        prefs.edit().putBoolean(PREF_BACKGROUND_LOCATION_ASKED, true).apply()
        AlertDialog
            .Builder(this)
            .setTitle(R.string.background_location_title)
            .setMessage(R.string.background_location_message)
            .setPositiveButton(R.string.background_location_allow) { _, _ -> askBackgroundLocation.launch(background) }
            .setNegativeButton(R.string.background_location_later, null)
            .show()
    }

    /** System animator duration scale; 0 means the user turned animations off. */
    private fun animatorDurationScale(): Float = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}
