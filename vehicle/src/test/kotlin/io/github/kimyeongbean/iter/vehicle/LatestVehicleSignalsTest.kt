package io.github.kimyeongbean.iter.vehicle

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import io.github.kimyeongbean.iter.model.GeoPoint
import io.github.kimyeongbean.iter.model.SAMPLE_INTERVAL_MS
import io.github.kimyeongbean.iter.model.Sample
import io.github.kimyeongbean.iter.model.TurnSignal
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Pleos SDK adapter's pure part: SDK values → latest signals → one vehicle signal per second. */
class LatestVehicleSignalsTest {
    @Test
    fun `no signal received yet gives 0 kmh and null for every other signal`() {
        assertEquals(
            VehicleSignals(Sample(5_000L, 0.0, null, null, null, null, null, null), null),
            LatestVehicleSignals().at(5_000L),
        )
    }

    @Test
    fun `latest received values make the vehicle signals`() {
        val latest = LatestVehicleSignals().apply {
            speedKmh = 42.0
            gear = Gear.DRIVE
            turnSignal = TurnSignal.LEFT
            wiperOn = true
            lowBeamOn = false
            steeringAngleDeg = -12.5
            brakePedalPressed = true
            location = GeoPoint(37.5, 127.0)
        }

        assertEquals(
            VehicleSignals(Sample(1_000L, 42.0, TurnSignal.LEFT, true, false, -12.5, true, GeoPoint(37.5, 127.0)), Gear.DRIVE),
            latest.at(1_000L),
        )
    }

    @Test
    fun `ticks emit the latest signals once per sample interval, a change shows up at the next tick`() = runTest {
        val latest = LatestVehicleSignals()
        val emitted = mutableListOf<VehicleSignals>()
        backgroundScope.launch { latest.ticks { currentTime }.toList(emitted) }

        runCurrent()
        latest.speedKmh = 30.0
        advanceTimeBy(2 * SAMPLE_INTERVAL_MS)
        runCurrent()

        assertEquals(listOf(0L, SAMPLE_INTERVAL_MS, 2 * SAMPLE_INTERVAL_MS), emitted.map { it.sample.timeEpochMs })
        assertEquals(listOf(0.0, 30.0, 30.0), emitted.map { it.sample.speedKmh })
    }

    @Test
    fun `vehicle speed in m per s becomes km per h, reverse counts as positive`() {
        assertEquals(36.0, speedKmhFromMetersPerSecond(10f), 1e-9)
        assertEquals(18.0, speedKmhFromMetersPerSecond(-5f), 1e-9)
    }

    @Test
    fun `Pleos gear maps to P R N D, forward gears count as D, unknown is no signal`() {
        assertEquals(Gear.PARK, VehicleGear.GEAR_PARK.toGear())
        assertEquals(Gear.REVERSE, VehicleGear.GEAR_REVERSE.toGear())
        assertEquals(Gear.NEUTRAL, VehicleGear.GEAR_NEUTRAL.toGear())
        assertEquals(Gear.DRIVE, VehicleGear.GEAR_DRIVE.toGear())
        assertEquals(Gear.DRIVE, VehicleGear.GEAR_3.toGear())
        assertNull(VehicleGear.GEAR_UNKNOWN.toGear())
    }

    @Test
    fun `Pleos turn signal maps to off left right`() {
        assertEquals(TurnSignal.OFF, TurnSignalValue.OFF.toTurnSignal())
        assertEquals(TurnSignal.LEFT, TurnSignalValue.LEFT.toTurnSignal())
        assertEquals(TurnSignal.RIGHT, TurnSignalValue.RIGHT.toTurnSignal())
    }

    @Test
    fun `wiper on and off map to true and false, other states are no signal`() {
        assertEquals(true, WiperState.ON.toWiperOn())
        assertEquals(false, WiperState.OFF.toWiperOn())
        assertNull(WiperState.OTHER.toWiperOn())
        assertNull(WiperState.SERVICE.toWiperOn())
    }

    @Test
    fun `low beam is on only in the on state, daytime running lights are not low beam`() {
        assertEquals(true, LightState.ON.toLowBeamOn())
        assertEquals(false, LightState.OFF.toLowBeamOn())
        assertEquals(false, LightState.DAYTIME_RUNNING.toLowBeamOn())
    }
}
