package io.github.kimyeongbean.iter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as IterApplication
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
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
