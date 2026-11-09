package io.github.kimyeongbean.iter.model

/** Interval between two samples in a drive session (docs/interfaces.md common constant). */
const val SAMPLE_INTERVAL_MS: Long = 1_000L

/**
 * Vehicle signal values at one moment of a drive session (GLOSSARY: 샘플).
 * Every nullable field is null when the signal is not available.
 */
data class Sample(
    val timeEpochMs: Long,
    val speedKmh: Double,
    val turnSignal: TurnSignal?,
    val wiperOn: Boolean?,
    val lowBeamOn: Boolean?,
    val steeringAngleDeg: Double?,
    val brakePedalPressed: Boolean?,
    val location: GeoPoint?,
)

/** Turn signal state (GLOSSARY: 깜빡이). */
enum class TurnSignal {
    OFF,
    LEFT,
    RIGHT,
    HAZARD,
}

data class GeoPoint(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
)
