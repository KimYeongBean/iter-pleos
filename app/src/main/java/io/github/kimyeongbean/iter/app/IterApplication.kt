package io.github.kimyeongbean.iter.app

import android.app.Application
import io.github.kimyeongbean.iter.vehicle.DriveSessionRecorder
import io.github.kimyeongbean.iter.vehicle.RecordStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IterApplication : Application() {
    /** One record store per process, so every screen reads the same database. */
    val recordStore: RecordStore by lazy { RecordStore.open(this) }

    /** One drive session recorder per process: its in-progress state gates the screens. */
    val driveSessionRecorder: DriveSessionRecorder by lazy { DriveSessionRecorder(recordStore) }

    /** Speed colour base and map graph background the user picked. */
    internal val speedColorSettingsStore by lazy { SpeedColorSettingsStore(this) }

    /** Work that must finish even if the activity is recreated (e.g. inserting fake drive data). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Whether the splash (GLOSSARY: 스플래시) already played in this process: it plays once (#110). */
    internal var splashPlayed = false

    private var recording: Job? = null

    /**
     * Starts recording drive sessions from the vehicle for the rest of the process (#18). The
     * recording service calls it (#125); signals without permission are recorded as missing.
     * Later calls do nothing.
     */
    fun startRecording() {
        if (recording != null) return
        recording = appScope.launch { driveSessionRecorder.recordFromVehicle(this@IterApplication) }
    }
}
