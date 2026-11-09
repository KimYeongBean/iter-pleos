package io.github.kimyeongbean.iter.art

import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.DriveSessionSource
import io.github.kimyeongbean.iter.model.SAMPLE_INTERVAL_MS
import io.github.kimyeongbean.iter.model.Sample
import io.github.kimyeongbean.iter.model.TurnSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayArtDataTest {
    private val base = 0xFF336699.toInt()

    private fun sample(
        second: Int,
        speedKmh: Double,
        startEpochMs: Long = 0L,
    ) = Sample(
        timeEpochMs = startEpochMs + second * SAMPLE_INTERVAL_MS,
        speedKmh = speedKmh,
        turnSignal = null,
        wiperOn = null,
        lowBeamOn = null,
        steeringAngleDeg = null,
        brakePedalPressed = null,
        location = null,
    )

    private fun session(
        startEpochMs: Long,
        samples: List<Sample>,
    ) = DriveSession(startEpochMs, samples.lastOrNull()?.timeEpochMs, DriveSessionSource.VEHICLE, samples)

    @Test
    fun `empty day record gives no shapes`() {
        assertTrue(DayArtData.of(emptyList(), base).shapes.isEmpty())
        assertTrue(DayArtData.of(listOf(session(0L, emptyList())), base).shapes.isEmpty())
    }

    @Test
    fun `drive time goes round the circle in time order and speed sets radius and colour`() {
        val speedsKmh = listOf(0.0, 60.0, 120.0, 150.0)
        val day = listOf(session(0L, speedsKmh.mapIndexed { i, kmh -> sample(i, kmh) }))

        val shapes = DayArtData.of(day, base).shapes

        // Four samples, four quarter-circle shapes clockwise from 12 o'clock.
        assertEquals(listOf(0.0, 0.25, 0.5, 0.75), shapes.map { it.timePosition })
        assertEquals(listOf(0.0, 90.0, 180.0, 270.0), shapes.map { it.startAngleDeg })
        shapes.forEach { assertEquals(90.0, it.sweepAngleDeg, 1e-9) }
        shapes.forEach { assertEquals(0.25, it.innerRadius, 1e-9) }
        // Standing still keeps a thin bar; half the speed colour max reaches halfway; the max and above reach 0.85.
        assertEquals(listOf(0.28, 0.55, 0.85, 0.85), shapes.map { it.outerRadius }.map { Math.round(it * 100) / 100.0 })
        // Speed colour alpha: 25% at standstill, 62.5% at half, full at the max (same hue).
        assertEquals(listOf(0x40336699, 0x9F336699.toInt(), base, base), shapes.map { it.colorArgb })
    }

    @Test
    fun `a long day is grouped into at most 120 shapes with sessions in start order`() {
        // Two 300-sample sessions, the later one listed first: 600 samples, 5 per shape.
        val morning = session(0L, List(300) { sample(it, 30.0) })
        val eveningStart = 10 * 3_600_000L
        val evening = session(eveningStart, List(300) { sample(it, 90.0, eveningStart) })

        val shapes = DayArtData.of(listOf(evening, morning), base).shapes

        assertEquals(120, shapes.size)
        shapes.forEach { assertEquals(3.0, it.sweepAngleDeg, 1e-9) }
        assertEquals(357.0, shapes.last().startAngleDeg, 1e-9)
        assertEquals(0.4, shapes.first().outerRadius, 1e-9)
        assertTrue(shapes.take(60).all { it.colorArgb == SpeedColor.argb(base, 30.0) })
        assertTrue(shapes.drop(60).all { it.colorArgb == SpeedColor.argb(base, 90.0) })
        assertEquals(shapes.sortedBy { it.timePosition }, shapes)
    }

    @Test
    fun `turn signal, wiper and low beam become patterns and missing signals leave none`() {
        val samples =
            listOf(
                sample(0, 40.0).copy(turnSignal = TurnSignal.LEFT, wiperOn = true, lowBeamOn = true),
                sample(1, 40.0).copy(turnSignal = TurnSignal.HAZARD, wiperOn = false, lowBeamOn = false),
                sample(2, 40.0).copy(turnSignal = TurnSignal.OFF, wiperOn = false, lowBeamOn = true),
                // Every signal missing.
                sample(3, 40.0),
            )

        val patterns = DayArtData.of(listOf(session(0L, samples)), base).shapes.map { it.patterns }

        assertEquals(
            listOf(
                setOf(DayArtPattern.TURN_SIGNAL, DayArtPattern.WIPER, DayArtPattern.LOW_BEAM),
                setOf(DayArtPattern.TURN_SIGNAL),
                setOf(DayArtPattern.LOW_BEAM),
                emptySet(),
            ),
            patterns,
        )
    }

    @Test
    fun `a grouped shape has a pattern when any of its samples has the signal on`() {
        // 240 samples, 2 per shape: only sample 1 has the turn signal on.
        val samples = List(240) { sample(it, 40.0).copy(turnSignal = if (it == 1) TurnSignal.RIGHT else null) }

        val shapes = DayArtData.of(listOf(session(0L, samples)), base).shapes

        assertEquals(setOf(DayArtPattern.TURN_SIGNAL), shapes[0].patterns)
        assertTrue(shapes.drop(1).all { it.patterns.isEmpty() })
    }

    @Test
    fun `same input gives the same data`() {
        val day = FakeDriveData.allKinds(java.time.LocalDate.of(2026, 11, 9), 7, java.time.ZoneId.of("Asia/Seoul"))

        assertEquals(DayArtData.of(day, base), DayArtData.of(day.reversed(), base))
    }

    @Test
    fun `a day record without location still makes the same art`() {
        val day = FakeDriveData.normal(java.time.LocalDate.of(2026, 11, 9), 3, java.time.ZoneId.of("Asia/Seoul"))
        val withoutLocation = day.map { s -> s.copy(samples = s.samples.map { it.copy(location = null) }) }

        val art = DayArtData.of(withoutLocation, base)

        assertTrue(art.shapes.isNotEmpty())
        assertEquals(DayArtData.of(day, base), art)
    }
}
