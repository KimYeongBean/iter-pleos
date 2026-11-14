package io.github.kimyeongbean.iter.app

import android.app.Application
import android.content.Intent
import io.github.kimyeongbean.iter.vehicle.DriveSessionRecorder
import io.github.kimyeongbean.iter.vehicle.RecordStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

class IterApplication : Application() {
    /** One record store per process, so every screen reads the same database. */
    val recordStore: RecordStore by lazy { RecordStore.open(this) }

    /** One drive session recorder per process: its in-progress state gates the screens. */
    val driveSessionRecorder: DriveSessionRecorder by lazy { DriveSessionRecorder(recordStore) }

    /** Speed colour base and map graph background the user picked. */
    internal val speedColorSettingsStore by lazy { SpeedColorSettingsStore(this) }

    /** The user's answer to the collection notice (#127). */
    internal val consentStore by lazy { RecordingConsentStore(this) }

    /** Work that must finish even if the activity is recreated (e.g. inserting fake drive data). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Whether the splash (GLOSSARY: 스플래시) already played in this process: it plays once (#110). */
    internal var splashPlayed = false

    private var recording: Job? = null

    /**
     * Starts recording drive sessions from the vehicle for the rest of the process (#18), if the
     * user agreed to collection (#127), with location only if they agreed to that too. The
     * recording service calls it (#125); signals without permission are recorded as missing.
     * Later calls do nothing. Returns whether recording runs.
     */
    fun startRecording(): Boolean {
        if (recording != null) return true
        val consent = consentStore.load()
        if (!consent.agreed) return false
        recording =
            appScope.launch {
                driveSessionRecorder.recordFromVehicle(this@IterApplication, withLocation = consent.recordsLocation)
            }
        return true
    }

    /**
     * Withdraws the collection consent (#127): stops the recording service and recording at once,
     * then deletes every saved record, real and fake alike. Recording starts again only after
     * the user agrees again.
     */
    internal suspend fun withdrawConsent() {
        consentStore.save(RecordingConsent(RecordingConsent.Answer.DECLINED))
        stopService(Intent(this, RecordingService::class.java))
        recording?.cancelAndJoin()
        recording = null
        recordStore.deleteAll()
    }
}
