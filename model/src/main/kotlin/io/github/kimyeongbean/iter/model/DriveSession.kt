package io.github.kimyeongbean.iter.model

/**
 * One drive from start to end (GLOSSARY: 주행 세션).
 * Its day record date is the device-local date of [startEpochMs].
 */
data class DriveSession(
    val startEpochMs: Long,
    /** Null while the session is still in progress. */
    val endEpochMs: Long?,
    val source: DriveSessionSource,
    val samples: List<Sample>,
)

/** Where a drive session came from. The record store treats both the same way (ADR 0001). */
enum class DriveSessionSource {
    VEHICLE,
    FAKE_DRIVE_DATA,
}
