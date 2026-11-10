package io.github.kimyeongbean.iter.vehicle

import android.content.Context
import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.DriveSessionSource
import io.github.kimyeongbean.iter.model.Sample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/** Parked at 0 km/h this long ends a drive session (docs/interfaces.md common constant, 제안값). */
internal const val DRIVE_SESSION_END_WAIT_MS: Long = 60_000L

/** An in-progress drive session is saved every this many new samples, so a crash loses little. */
internal const val IN_PROGRESS_SAVE_EVERY_SAMPLES = 10

/**
 * Records drive sessions from the vehicle signal flow into [store].
 * A drive session starts when the gear is D and ends once the gear is P at 0 km/h for
 * [DRIVE_SESSION_END_WAIT_MS]; its end time is when that stop began, and samples after it are dropped.
 * Manual start and end requests ([requestStart], [requestEnd]) win over this detection.
 * All timing comes from the sample times, not the wall clock.
 */
public class DriveSessionRecorder(
    private val store: RecordStore,
) {
    private val inProgressState = MutableStateFlow(false)

    /** Only one [record] at a time: a second one would close the session the first is writing. */
    private val recordLock = Mutex()

    /**
     * The latest manual request not yet handled; the next vehicle signal handles it. One slot: a
     * newer request replaces an unhandled one (the screen offers start and end at different times).
     */
    private val manualRequest = AtomicReference<ManualRequest?>(null)

    /** True while a drive session is being recorded (for the "기록 중" display). */
    public val inProgress: StateFlow<Boolean> = inProgressState.asStateFlow()

    /**
     * Starts a drive session at the next vehicle signal, whatever the gear (for cars without a gear
     * signal). Only [requestEnd] ends it; parking does not. Ignored while a drive session is in progress.
     */
    public fun requestStart() {
        manualRequest.set(ManualRequest.START)
    }

    /**
     * Ends the drive session in progress at its last sample, without the parked wait. Honoured only
     * when the gear is P or unknown (no gear signal): while the car may be moving the record screens
     * must stay closed, so the request is dropped. Ignored when no drive session is in progress.
     */
    public fun requestEnd() {
        manualRequest.set(ManualRequest.END)
    }

    /**
     * Records from the real vehicle signals (Pleos Vehicle SDK, vehicle speed, location) until the
     * caller is cancelled; see [record]. Signals without a granted permission are recorded as missing,
     * so ask for [CAR_SPEED_PERMISSION] and fine location before calling.
     */
    public suspend fun recordFromVehicle(context: Context) {
        record(pleosVehicleSignals(context.applicationContext))
    }

    /**
     * First closes drive sessions left unfinished by an earlier run (end = last saved sample), then
     * records from [signals] until the flow ends or the caller is cancelled. Calls run one at a time.
     * If [signals] ends mid-drive, the samples so far are saved and the session stays unfinished.
     * Manual requests made while no [record] was running are dropped.
     */
    internal suspend fun record(signals: Flow<VehicleSignals>) = recordLock.withLock {
        manualRequest.set(null)
        store.closeUnfinishedSessions()
        var current: Recording? = null

        suspend fun finish(
            recording: Recording,
            endEpochMs: Long,
        ) {
            store.save(recording.finishedAt(endEpochMs))
            current = null
            inProgressState.value = false
        }
        try {
            signals.collect { signal ->
                val request = manualRequest.getAndSet(null)
                val recording = current
                if (recording == null) {
                    val manualStart = request == ManualRequest.START
                    if (manualStart || signal.gear == Gear.DRIVE) {
                        // Save the first sample at once, so even a very short run leaves the session.
                        current = Recording(signal.sample, manualStart).also { it.saveNewSamples() }
                        inProgressState.value = true
                    }
                    return@collect
                }
                if (request == ManualRequest.END && (signal.gear == null || signal.gear == Gear.PARK)) {
                    finish(recording, recording.lastSampleEpochMs)
                    return@collect
                }
                val endEpochMs = recording.add(signal)
                if (endEpochMs != null) {
                    finish(recording, endEpochMs)
                } else if (recording.unsavedCount >= IN_PROGRESS_SAVE_EVERY_SAMPLES) {
                    recording.saveNewSamples()
                }
            }
            current?.saveNewSamples()
        } finally {
            inProgressState.value = false
        }
    }

    private enum class ManualRequest { START, END }

    /** A drive session being recorded. A [manual] one ends only by [requestEnd]. */
    private inner class Recording(
        first: Sample,
        private val manual: Boolean,
    ) {
        private val startEpochMs = first.timeEpochMs
        private val samples = mutableListOf(first)
        private var savedCount = 0
        private var stoppedSinceMs: Long? = null

        val unsavedCount get() = samples.size - savedCount

        val lastSampleEpochMs get() = samples.last().timeEpochMs

        /**
         * Adds [signal]'s sample. Returns the session's end time (when the stop began) once the
         * gear has been P at 0 km/h for [DRIVE_SESSION_END_WAIT_MS], otherwise null. Always null
         * for a [manual] drive session.
         */
        fun add(signal: VehicleSignals): Long? {
            samples += signal.sample
            if (manual) return null
            val stopped = signal.gear == Gear.PARK && signal.sample.speedKmh <= 0.0
            val since = if (stopped) stoppedSinceMs ?: signal.sample.timeEpochMs else null
            stoppedSinceMs = since
            return since?.takeIf { signal.sample.timeEpochMs - it >= DRIVE_SESSION_END_WAIT_MS }
        }

        suspend fun saveNewSamples() {
            store.appendInProgress(startEpochMs, DriveSessionSource.VEHICLE, samples.subList(savedCount, samples.size).toList())
            savedCount = samples.size
        }

        fun finishedAt(endEpochMs: Long) = DriveSession(
            startEpochMs = startEpochMs,
            endEpochMs = endEpochMs,
            source = DriveSessionSource.VEHICLE,
            samples = samples.filter { it.timeEpochMs <= endEpochMs },
        )
    }
}
