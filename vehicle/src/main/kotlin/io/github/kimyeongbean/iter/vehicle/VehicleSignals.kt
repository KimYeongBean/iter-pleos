package io.github.kimyeongbean.iter.vehicle

import io.github.kimyeongbean.iter.model.Sample

/**
 * Vehicle signal values at one moment (GLOSSARY: 차량 신호 흐름).
 * The vehicle signal flow is a `Flow<VehicleSignals>` with one item per sample interval: an adapter
 * (Pleos SDK in the app, a fake sequence in tests) keeps the latest signal values and emits them.
 * [gear] is null when the gear signal is not available.
 */
internal data class VehicleSignals(
    val sample: Sample,
    val gear: Gear?,
)

/** Gear selector position (GLOSSARY: 기어). */
internal enum class Gear {
    PARK,
    REVERSE,
    NEUTRAL,
    DRIVE,
}
