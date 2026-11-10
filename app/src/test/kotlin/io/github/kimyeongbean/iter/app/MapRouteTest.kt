package io.github.kimyeongbean.iter.app

import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.DriveSessionSource
import io.github.kimyeongbean.iter.model.GeoPoint
import io.github.kimyeongbean.iter.model.Sample
import io.github.kimyeongbean.iter.model.TurnSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRouteTest {
    private val base = 0xFF336699.toInt()

    private fun sample(
        minute: Int,
        location: GeoPoint?,
        speedKmh: Double = 50.0,
        startEpochMs: Long = 0L,
    ) = Sample(
        // A minute apart, so the small test steps (about 100 m) stay at a drivable speed (#64).
        timeEpochMs = startEpochMs + minute * 60_000L,
        speedKmh = speedKmh,
        turnSignal = null,
        wiperOn = null,
        lowBeamOn = null,
        steeringAngleDeg = null,
        brakePedalPressed = null,
        location = location,
    )

    private fun point(
        x: Float,
        y: Float,
        timePosition: Float = 0f,
    ) = RoutePoint(x, y, 0, timePosition, turnSignalOn = false, wiperOn = false, lowBeamOn = false)

    private fun session(
        startEpochMs: Long,
        samples: List<Sample>,
    ) = DriveSession(startEpochMs, samples.lastOrNull()?.timeEpochMs, DriveSessionSource.VEHICLE, samples)

    /** A short drive north-east in Seoul, one sample per minute. */
    private fun seoulDrive(firstMinute: Int) = List(5) { i -> sample(firstMinute + i, GeoPoint(37.5 + i * 0.0001, 127.0 + i * 0.0001)) }

    @Test
    fun `a first location far from the rest, too far to drive in time, is left out (#64)`() {
        val stale = sample(0, GeoPoint(37.422, -122.084))
        val withStale = mapRoute(listOf(session(0L, listOf(stale) + seoulDrive(1))), base)
        val without = mapRoute(listOf(session(0L, seoulDrive(1))), base)

        assertEquals(without.strokes.map { s -> s.map { it.x to it.y } }, withStale.strokes.map { s -> s.map { it.x to it.y } })
    }

    @Test
    fun `a single location jump in the middle of a drive is left out (#64)`() {
        val drive = seoulDrive(0)
        val jump = sample(5, GeoPoint(35.1, 129.0))
        val after = List(5) { i -> sample(6 + i, GeoPoint(37.5005 + i * 0.0001, 127.0005 + i * 0.0001)) }
        val route = mapRoute(listOf(session(0L, drive + jump + after)), base)

        assertEquals(1, route.strokes.size)
        assertEquals(10, route.strokes.single().size)
    }

    @Test
    fun `two far apart locations alone are both kept, there is no telling which is wrong (#64)`() {
        val route = mapRoute(listOf(session(0L, listOf(sample(0, GeoPoint(37.5, 127.0)), sample(1, GeoPoint(35.1, 129.0))))), base)

        assertEquals(2, route.strokes.single().size)
    }

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
    fun `wiper and low beam on mark the point and a missing signal does not`() {
        val signals = listOf(true, false, null)
        val day =
            listOf(
                session(
                    0L,
                    signals.mapIndexed { i, s ->
                        sample(i, GeoPoint(0.0, i * 0.001)).copy(wiperOn = s, lowBeamOn = signals[(i + 1) % 3])
                    },
                ),
            )

        val points = mapRoute(day, base).strokes.single()

        assertEquals(listOf(true, false, false), points.map { it.wiperOn })
        assertEquals(listOf(false, false, true), points.map { it.lowBeamOn })
    }

    @Test
    fun `rain drops sit evenly along segments touching a wiper sample, each wiper stretch starting with one`() {
        // Nine samples east along the equator, 1/8 of the width apart. Wiper on at sample 0 and
        // sample 5 only, so the stretches are x 0 to 1/8 and x 4/8 to 6/8; null is off.
        val wipers = listOf(true, false, false, null, false, true, false, false, false)
        val day = listOf(session(0L, wipers.mapIndexed { i, w -> sample(i, GeoPoint(0.0, i * 0.001)).copy(wiperOn = w) }))

        val drops = mapRoute(day, base).rainDrops

        val expectedX = listOf(0f, 0.04f, 0.08f, 0.12f) + listOf(0.5f, 0.54f, 0.58f, 0.62f, 0.66f, 0.70f, 0.74f)
        assertEquals(expectedX.size, drops.size)
        drops.zip(expectedX).forEach { (drop, x) ->
            assertEquals(x, drop.x, 1e-4f)
            assertEquals(0.5f, drop.y, 1e-6f)
            // Time runs evenly from west to east here, so the time position equals x.
            assertEquals(x, drop.timePosition, 1e-4f)
        }
        // Neighbouring drops fall out of step.
        assertTrue(drops.zipWithNext().all { (a, b) -> a.fallOffset != b.fallOffset })
    }

    @Test
    fun `a day without wiper on has no rain`() {
        val day = listOf(session(0L, List(4) { sample(it, GeoPoint(0.0, it * 0.001)).copy(wiperOn = if (it % 2 == 0) false else null) }))

        assertTrue(mapRoute(day, base).rainDrops.isEmpty())
    }

    @Test
    fun `a rain drop falls through its window once per cycle, fading in and out`() {
        val drop = RainDrop(0.5f, 0.5f, timePosition = 0f, fallOffset = 0.25f)

        assertEquals(0.25f, rainFall(drop, phase = 0f), 1e-6f)
        assertEquals(0.75f, rainFall(drop, phase = 0.5f), 1e-6f)
        // Past the bottom it starts again at the top.
        assertEquals(0.05f, rainFall(drop, phase = 0.8f), 1e-5f)
        assertEquals(0f, rainAlpha(0f), 1e-6f)
        assertEquals(1f, rainAlpha(0.5f), 1e-6f)
        assertEquals(0f, rainAlpha(1f), 1e-6f)
    }

    @Test
    fun `the draw-in draws a segment while it passes the segment's stretch of time`() {
        val from = point(0f, 0f, timePosition = 0.25f)
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
