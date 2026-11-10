package io.github.kimyeongbean.iter.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Runtime permissions the drive session recorder needs: vehicle speed and location (#18). */
private val RECORDING_PERMISSIONS = arrayOf(
    "android.car.permission.CAR_SPEED",
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

class MainActivity : ComponentActivity() {
    /** Records whatever was granted: a denied signal is recorded as missing. */
    private val askRecordingPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            (application as IterApplication).startRecording()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as IterApplication
        val missing = RECORDING_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) app.startRecording() else askRecordingPermissions.launch(missing.toTypedArray())
        setContent {
            // Held above the app theme: an advanced theme sets the app colours and the backdrop.
            var settings by remember { mutableStateOf(app.speedColorSettingsStore.load()) }
            val dark = isSystemInDarkTheme()
            val art = settings.theme.art
            IterTheme(dark, settings.theme.appColors(dark)) {
                Surface {
                    // Decoration only, and still: it reads no state that changes while a theme is on.
                    if (art != null) Canvas(Modifier.fillMaxSize()) { with(art) { drawBackdrop(dark) } }
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
                    )
                }
            }
        }
    }
}
