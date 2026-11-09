package io.github.kimyeongbean.iter.app

import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.DriveSessionSource
import io.github.kimyeongbean.iter.model.GeoPoint
import io.github.kimyeongbean.iter.model.SAMPLE_INTERVAL_MS
import io.github.kimyeongbean.iter.model.Sample
import io.github.kimyeongbean.iter.model.TurnSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRouteTest {
    private val base = 0xFF336699.toInt()

    private fun sample(
        second: Int,
        location: GeoPoint?,
        speedKmh: Double = 50.0,
        startEpochMs: Long = 0L,
    ) = Sample(
        timeEpochMs = startEpochMs + second * SAMPLE_INTERVAL_MS,
        speedKmh = speedKmh,
        turnSignal = null,
        wiperOn = null,
        lowBeamOn = null,
        steeringAngleDeg = null,
        brakePedalPressed = null,
        location = location,
    )

    private fun session(
        startEpochMs: Long,
        samples: List<Sample>,
    ) = DriveSession(startEpochMs, samples.lastOrNull()?.timeEpochMs, DriveSessionSource.VEHICLE, samples)

    @Test
    fun `a day record without location has no route`() {
        assertTrue(mapRoute(emptyList(), base).strokes.isEmpty())
        assertTrue(mapRoute(listOf(session(0L, List(5) { sample(it, location = null) })), base).strokes.isEmpty())
    }

    @Test
    fun `route fits the unit square with north up, keeping its shape, in speed colour and time order`() {
        // East 0.001 deg, then north 0.0005 deg, on the equator: twice as wide as tall.
        val day =
            listOf(
                session(
                    0L,
                    listOf(
                        sample(0, GeoPoint(0.0, 0.0), speedKmh = 0.0),
                        sample(1, GeoPoint(0.0, 0.001), speedKmh = 60.0),
                        sample(2, GeoPoint(0.0005, 0.001), speedKmh = 120.0),
                    ),
                ),
            )

        val points = mapRoute(day, base).strokes.single()

        assertEquals(listOf(0f, 1f, 1f), points.map { it.x })
        // Half as tall, centred: from 0.25 to 0.75, north at the top.
        assertEquals(listOf(0.75f, 0.75f, 0.25f), points.map { it.y })
        assertEquals(listOf(0x40336699, 0x9F336699.toInt(), base), points.map { it.colorArgb })
        assertEquals(listOf(0f, 0.5f, 1f), points.map { it.timePosition })
    }

    @Test
    fun `east-west distance shrinks with latitude`() {
        // At 60 degrees north, 0.002 deg of longitude is as long as 0.001 deg of latitude.
        val day =
            listOf(
                session(
                    0L,
                    listOf(
                        sample(0, GeoPoint(60.0, 0.0)),
                        sample(1, GeoPoint(60.0, 0.002)),
                        sample(2, GeoPoint(60.001, 0.002)),
                    ),
                ),
            )

        val points = mapRoute(day, base).strokes.single()

        points.zip(listOf(0f to 1f, 1f to 1f, 1f to 0f)).forEach { (p, xy) ->
            assertEquals(xy.first, p.x, 1e-3f)
            assertEquals(xy.second, p.y, 1e-3f)
        }
    }

    @Test
    fun `sessions and gaps in location start new strokes, in start order`() {
        val eveningStart = 10 * 3_600_000L
        val morning =
            session(
                0L,
                listOf(
                    sample(0, GeoPoint(0.0, 0.0)),
                    sample(1, GeoPoint(0.0, 0.001)),
                    sample(2, location = null),
                    sample(3, GeoPoint(0.001, 0.001)),
                ),
            )
        val evening = session(eveningStart, listOf(sample(0, GeoPoint(0.001, 0.0), startEpochMs = eveningStart)))

        val strokes = mapRoute(listOf(evening, morning), base).strokes

        assertEquals(listOf(2, 1, 1), strokes.map { it.size })
        // Time position counts located samples only: four of them.
        assertEquals(listOf(0f, 1 / 3f, 2 / 3f, 1f), strokes.flatten().map { it.timePosition })
        // The evening point is the north-west corner.
        assertEquals(0f, strokes.last().single().x, 1e-6f)
        assertEquals(0f, strokes.last().single().y, 1e-6f)
    }

    @Test
    fun `turn signal on marks the point and a missing signal does not`() {
        val signals = listOf(TurnSignal.LEFT, TurnSignal.RIGHT, TurnSignal.HAZARD, TurnSignal.OFF, null)
        val day = listOf(session(0L, signals.mapIndexed { i, s -> sample(i, GeoPoint(0.0, i * 0.001)).copy(turnSignal = s) }))

        val points = mapRoute(day, base).strokes.single()

        assertEquals(listOf(true, true, true, false, false), points.map { it.turnSignalOn })
    }

    @Test
    fun `the draw-in draws a segment while it passes the segment's stretch of time`() {
        val from = RoutePoint(0f, 0f, 0, timePosition = 0.25f, turnSignalOn = false)
        val to = from.copy(timePosition = 0.5f)

        assertEquals(0f, segmentDrawnFraction(from, to, progress = 0.2f), 1e-6f)
        assertEquals(0.5f, segmentDrawnFraction(from, to, progress = 0.375f), 1e-6f)
        assertEquals(1f, segmentDrawnFraction(from, to, progress = 0.5f), 1e-6f)
        assertEquals(1f, segmentDrawnFraction(from, to, progress = 1f), 1e-6f)
    }

    @Test
    fun `a route at one place sits in the middle`() {
        val day = listOf(session(0L, List(3) { sample(it, GeoPoint(37.5, 127.0), speedKmh = 0.0) }))

        val points = mapRoute(day, base).strokes.single()

        points.forEach { assertEquals(0.5f to 0.5f, it.x to it.y) }
    }
}
