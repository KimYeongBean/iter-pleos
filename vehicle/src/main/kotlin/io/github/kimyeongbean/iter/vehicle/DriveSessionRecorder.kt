package io.github.kimyeongbean.iter.vehicle

import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.DriveSessionSource
import io.github.kimyeongbean.iter.model.Sample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Parked at 0 km/h this long ends a drive session (docs/interfaces.md common constant, 제안값). */
const val DRIVE_SESSION_END_WAIT_MS: Long = 60_000L

/** An in-progress drive session is saved every this many new samples, so a crash loses little. */
internal const val IN_PROGRESS_SAVE_EVERY_SAMPLES = 10

/**
 * Records drive sessions from the vehicle signal flow into [store].
 * A drive session starts when the gear is D and ends once the gear is P at 0 km/h for
 * [DRIVE_SESSION_END_WAIT_MS]; its end time is when that stop began, and samples after it are dropped.
 * All timing comes from the sample times, not the wall clock.
 */
class DriveSessionRecorder(
    private val store: RecordStore,
) {
    private val inProgressState = MutableStateFlow(false)

    /** True while a drive session is being recorded (for the "기록 중" display). */
    val inProgress: StateFlow<Boolean> = inProgressState.asStateFlow()

    /**
     * First closes drive sessions left unfinished by an earlier run (end = last saved sample), then
     * records from [signals] until the flow ends or the caller is cancelled. Run one at a time.
     * If [signals] ends mid-drive, the samples so far are saved and the session stays unfinished.
     */
    suspend fun record(signals: Flow<VehicleSignals>) {
        store.closeUnfinishedSessions()
        var current: Recording? = null
        try {
            signals.collect { signal ->
                val recording = current
                if (recording == null) {
                    if (signal.gear == Gear.DRIVE) {
                        current = Recording(signal.sample)
                        inProgressState.value = true
                    }
                    return@collect
                }
                val stoppedSinceMs = recording.add(signal)
                if (stoppedSinceMs != null && signal.sample.timeEpochMs - stoppedSinceMs >= DRIVE_SESSION_END_WAIT_MS) {
                    store.save(recording.finishedAt(stoppedSinceMs))
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

        /** Adds [signal]'s sample. Returns when the current stop (P at 0 km/h) began, or null if moving. */
        fun add(signal: VehicleSignals): Long? {
            samples += signal.sample
            val stopped = signal.gear == Gear.PARK && signal.sample.speedKmh <= 0.0
            stoppedSinceMs = if (stopped) stoppedSinceMs ?: signal.sample.timeEpochMs else null
            return stoppedSinceMs
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
