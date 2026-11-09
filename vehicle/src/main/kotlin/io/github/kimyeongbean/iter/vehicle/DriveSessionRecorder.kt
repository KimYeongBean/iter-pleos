package io.github.kimyeongbean.iter.vehicle

import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.DriveSessionSource
import io.github.kimyeongbean.iter.model.Sample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Parked at 0 km/h this long ends a drive session (docs/interfaces.md common constant, 제안값). */
internal const val DRIVE_SESSION_END_WAIT_MS: Long = 60_000L

/** An in-progress drive session is saved every this many new samples, so a crash loses little. */
internal const val IN_PROGRESS_SAVE_EVERY_SAMPLES = 10

/**
 * Records drive sessions from the vehicle signal flow into [store].
 * A drive session starts when the gear is D and ends once the gear is P at 0 km/h for
 * [DRIVE_SESSION_END_WAIT_MS]; its end time is when that stop began, and samples after it are dropped.
 * All timing comes from the sample times, not the wall clock.
 */
public class DriveSessionRecorder(
    private val store: RecordStore,
) {
    private val inProgressState = MutableStateFlow(false)

    /** Only one [record] at a time: a second one would close the session the first is writing. */
    private val recordLock = Mutex()

    /** True while a drive session is being recorded (for the "기록 중" display). */
    public val inProgress: StateFlow<Boolean> = inProgressState.asStateFlow()

    /**
     * First closes drive sessions left unfinished by an earlier run (end = last saved sample), then
     * records from [signals] until the flow ends or the caller is cancelled. Calls run one at a time.
     * If [signals] ends mid-drive, the samples so far are saved and the session stays unfinished.
     */
    internal suspend fun record(signals: Flow<VehicleSignals>) = recordLock.withLock {
        store.closeUnfinishedSessions()
        var current: Recording? = null
        try {
            signals.collect { signal ->
                val recording = current
                if (recording == null) {
                    if (signal.gear == Gear.DRIVE) {
                        // Save the first sample at once, so even a very short run leaves the session.
                        current = Recording(signal.sample).also { it.saveNewSamples() }
                        inProgressState.value = true
                    }
                    return@collect
                }
                val endEpochMs = recording.add(signal)
                if (endEpochMs != null) {
                    store.save(recording.finishedAt(endEpochMs))
                    current = null
                    inProgressState.value = false
                } else if (recording.unsavedCount >= IN_PROGRESS_SAVE_EVERY_SAMPLES) {
                    recording.saveNewSamples()
                }
            }
            current?.saveNewSamples()
        } finally {
            inProgressState.value = false
        }
    }

    /** A drive session being recorded. */
    private inner class Recording(
        first: Sample,
    ) {
        private val startEpochMs = first.timeEpochMs
        private val samples = mutableListOf(first)
        private var savedCount = 0
        private var stoppedSinceMs: Long? = null

        val unsavedCount get() = samples.size - savedCount

        /**
         * Adds [signal]'s sample. Returns the session's end time (when the stop began) once the
         * gear has been P at 0 km/h for [DRIVE_SESSION_END_WAIT_MS], otherwise null.
         */
        fun add(signal: VehicleSignals): Long? {
            samples += signal.sample
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
