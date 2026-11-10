package io.github.kimyeongbean.iter.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface

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
            IterTheme(dark = isSystemInDarkTheme()) {
                Surface {
                    IterApp(
                        recordStore = app.recordStore,
                        recorder = app.driveSessionRecorder,
                        speedColorSettingsStore = app.speedColorSettingsStore,
                        appScope = app.appScope,
                        showDebugMenu = BuildConfig.DEBUG,
                    )
                }
            }
        }
    }
}
