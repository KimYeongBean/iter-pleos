package io.github.kimyeongbean.iter.vehicle

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import io.github.kimyeongbean.iter.model.GeoPoint
import io.github.kimyeongbean.iter.model.SAMPLE_INTERVAL_MS
import io.github.kimyeongbean.iter.model.Sample
import io.github.kimyeongbean.iter.model.TurnSignal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs

/**
 * The latest value of each vehicle signal, written by SDK listeners as values arrive. Null means not
 * received (yet). [speedKmh] has no null in a sample, so no speed received counts as 0 km/h.
 */
internal class LatestVehicleSignals {
    @Volatile var speedKmh: Double? = null

    @Volatile var gear: Gear? = null

    @Volatile var turnSignal: TurnSignal? = null

    @Volatile var wiperOn: Boolean? = null

    @Volatile var lowBeamOn: Boolean? = null

    @Volatile var steeringAngleDeg: Double? = null

    @Volatile var brakePedalPressed: Boolean? = null

    @Volatile var location: GeoPoint? = null

    /** The vehicle signals at [timeEpochMs] from the latest values. */
    fun at(timeEpochMs: Long): VehicleSignals = VehicleSignals(
        sample = Sample(
            timeEpochMs = timeEpochMs,
            speedKmh = speedKmh ?: 0.0,
            turnSignal = turnSignal,
            wiperOn = wiperOn,
            lowBeamOn = lowBeamOn,
            steeringAngleDeg = steeringAngleDeg,
            brakePedalPressed = brakePedalPressed,
            location = location,
        ),
        gear = gear,
    )

    /** The vehicle signal flow: the latest values once per [SAMPLE_INTERVAL_MS], timed by [nowEpochMs]. */
    fun ticks(nowEpochMs: () -> Long): Flow<VehicleSignals> = flow {
        while (true) {
            emit(at(nowEpochMs()))
            delay(SAMPLE_INTERVAL_MS)
        }
    }
}

/** AAOS vehicle speed (m/s, negative in reverse) as a sample speed (km/h, never negative). */
internal fun speedKmhFromMetersPerSecond(metersPerSecond: Float): Double = abs(metersPerSecond.toDouble()) * 3.6

/** Forward gears of a manual or numbered gearbox count as D; unknown is no gear signal. */
internal fun VehicleGear.toGear(): Gear? = when (this) {
    VehicleGear.GEAR_PARK -> Gear.PARK
    VehicleGear.GEAR_REVERSE -> Gear.REVERSE
    VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
    VehicleGear.GEAR_UNKNOWN -> null
    else -> Gear.DRIVE
}

/** The Pleos SDK turn signal has no hazard state, so hazard is never recorded from it. */
internal fun TurnSignalValue.toTurnSignal(): TurnSignal = when (this) {
    TurnSignalValue.OFF -> TurnSignal.OFF
    TurnSignalValue.LEFT -> TurnSignal.LEFT
    TurnSignalValue.RIGHT -> TurnSignal.RIGHT
}

/** Other and service positions are neither on nor off, so they are recorded as no signal. */
internal fun WiperState.toWiperOn(): Boolean? = when (this) {
    WiperState.ON -> true
    WiperState.OFF -> false
    WiperState.OTHER, WiperState.SERVICE -> null
}

/** Daytime running lights are not the low beam. */
internal fun LightState.toLowBeamOn(): Boolean = this == LightState.ON
