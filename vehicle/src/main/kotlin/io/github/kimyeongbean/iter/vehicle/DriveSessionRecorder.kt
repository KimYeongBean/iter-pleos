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
 * Manual start and end requests ([requestStart], [requestEnd]) win over this detection. A manual end
 * in gear D blocks the next automatic start until the gear leaves D.
 * All timing comes from the sample times, not the wall clock.
 */
public class DriveSessionRecorder(
    private val store: RecordStore,
) {
    private val inProgressState = MutableStateFlow(false)
    private val drivingState = MutableStateFlow(false)

    /** Only one [record] at a time: a second one would close the session the first is writing. */
    private val recordLock = Mutex()

    /**
     * The latest manual request not yet handled; the next vehicle signal handles it. One slot: a
     * newer request replaces an unhandled one (the screen offers start and end at different times).
     */
    private val manualRequest = AtomicReference<ManualRequest?>(null)

    /** True while a drive session is being recorded (the manual end applies only then). */
    public val inProgress: StateFlow<Boolean> = inProgressState.asStateFlow()

    /**
     * True while the car may be moving: a drive session is in progress or the gear is not P. No gear
     * signal (null) is not driving. The record screens stay closed while this is true.
     */
    public val driving: StateFlow<Boolean> = drivingState.asStateFlow()

    /**
     * Starts a drive session at the next vehicle signal, whatever the gear (for cars without a gear
     * signal). Only [requestEnd] ends it; parking does not. Ignored while a drive session is in progress.
     */
    public fun requestStart() {
        manualRequest.set(ManualRequest.START)
    }

    /**
     * Ends the drive session in progress at its last sample, without the parked wait, in any gear.
     * Ended in gear D, no drive session starts automatically until the gear leaves D. Ignored when no
     * drive session is in progress.
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
        var autoStartBlocked = false

        suspend fun finish(
            recording: Recording,
            endEpochMs: Long,
        ) {
            store.save(recording.finishedAt(endEpochMs))
            current = null
            inProgressState.value = false
        }
        suspend fun recordSignal(signal: VehicleSignals) {
            val request = manualRequest.getAndSet(null)
            // No gear signal (null) is not leaving D, so it keeps the block.
            if (autoStartBlocked && signal.gear != null && signal.gear != Gear.DRIVE) autoStartBlocked = false
            val recording = current
            if (recording == null) {
                val manualStart = request == ManualRequest.START
                if (manualStart || (signal.gear == Gear.DRIVE && !autoStartBlocked)) {
                    // Save the first sample at once, so even a very short run leaves the session.
                    current = Recording(signal.sample, manualStart).also { it.saveNewSamples() }
                    inProgressState.value = true
                }
                return
            }
            if (request == ManualRequest.END) {
                finish(recording, recording.lastSampleEpochMs)
                autoStartBlocked = signal.gear == Gear.DRIVE
                return
            }
            val endEpochMs = recording.add(signal)
            if (endEpochMs != null) {
                finish(recording, endEpochMs)
            } else if (recording.unsavedCount >= IN_PROGRESS_SAVE_EVERY_SAMPLES) {
                recording.saveNewSamples()
            }
        }
        try {
            signals.collect { signal ->
                recordSignal(signal)
                drivingState.value = inProgressState.value || (signal.gear != null && signal.gear != Gear.PARK)
            }
            current?.saveNewSamples()
        } finally {
            inProgressState.value = false
            drivingState.value = false
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
