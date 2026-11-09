package io.github.kimyeongbean.iter.art

import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.DriveSessionSource
import io.github.kimyeongbean.iter.model.SAMPLE_INTERVAL_MS
import io.github.kimyeongbean.iter.model.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FakeDriveDataTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val lastDate = LocalDate.of(2026, 11, 9)
    private val dayCount = 28

    private fun startDate(startEpochMs: Long) = Instant.ofEpochMilli(startEpochMs).atZone(zone).toLocalDate()

    /** Speed change between consecutive samples, km/h per sample (one sample per second). */
    private fun List<Sample>.speedStepsKmh() = zipWithNext { a, b -> b.speedKmh - a.speedKmh }

    /** Tells a session's kind from what it contains. */
    private fun kindOf(session: DriveSession): String {
        val steps = session.samples.speedStepsKmh()
        return when {
            session.samples.any { it.wiperOn == null } -> "missing"
            steps.any { it >= HARSH_ACCELERATION_KMH_PER_S } && steps.any { it <= -HARSH_BRAKING_KMH_PER_S } -> "harsh"
            else -> "normal"
        }
    }

    /** Contract every fake drive session keeps: required fields, location, one sample per second. */
    private fun assertContract(session: DriveSession) {
        assertEquals(DriveSessionSource.FAKE_DRIVE_DATA, session.source)
        assertTrue(session.samples.isNotEmpty())
        assertEquals(session.startEpochMs, session.samples.first().timeEpochMs)
        assertEquals(session.samples.last().timeEpochMs, session.endEpochMs)
        session.samples.zipWithNext().forEach { (a, b) -> assertEquals(SAMPLE_INTERVAL_MS, b.timeEpochMs - a.timeEpochMs) }
        session.samples.forEach { sample ->
            assertTrue("speed ${sample.speedKmh}", sample.speedKmh in 0.0..SPEED_LIMIT_KMH)
            assertNotNull(sample.location)
        }
    }

    private companion object {
        // Rough harsh-driving thresholds: about 2.8 m/s² up and 3.9 m/s² down.
        const val HARSH_ACCELERATION_KMH_PER_S = 10.0
        const val HARSH_BRAKING_KMH_PER_S = 14.0
        const val SPEED_LIMIT_KMH = 120.0
    }

    @Test
    fun `normal drive data spans several dates in the range and leaves some days empty`() {
        val sessions = FakeDriveData.normal(lastDate, dayCount, zone)

        val dates = sessions.map { startDate(it.startEpochMs) }.toSet()
        val firstDate = lastDate.minusDays(dayCount - 1L)
        assertTrue("dates out of range: $dates", dates.all { it in firstDate..lastDate })
        assertTrue("expected many recorded days, got ${dates.size}", dates.size >= dayCount / 2)
        assertTrue("expected some empty days, got ${dates.size}", dates.size < dayCount)
    }

    @Test
    fun `every session is a complete fake drive session sampled once per second`() {
        val sessions = FakeDriveData.normal(lastDate, dayCount, zone)

        assertTrue(sessions.isNotEmpty())
        sessions.forEach { session ->
            assertEquals(DriveSessionSource.FAKE_DRIVE_DATA, session.source)
            assertTrue(session.samples.isNotEmpty())
            assertEquals(session.startEpochMs, session.samples.first().timeEpochMs)
            assertEquals(session.samples.last().timeEpochMs, session.endEpochMs)
            session.samples.zipWithNext().forEach { (a, b) -> assertEquals(SAMPLE_INTERVAL_MS, b.timeEpochMs - a.timeEpochMs) }
        }
    }

    @Test
    fun `normal samples carry location and every signal with a sane speed`() {
        val samples = FakeDriveData.normal(lastDate, dayCount, zone).flatMap { it.samples }

        samples.forEach { sample ->
            assertTrue("speed ${sample.speedKmh}", sample.speedKmh in 0.0..120.0)
            assertTrue(sample.location != null)
            assertTrue(sample.turnSignal != null)
            assertTrue(sample.wiperOn != null)
            assertTrue(sample.lowBeamOn != null)
            assertTrue(sample.steeringAngleDeg != null)
            assertTrue(sample.brakePedalPressed != null)
        }
        assertTrue("car never moved", samples.any { it.speedKmh > 30.0 })
    }

    @Test
    fun `harsh drive data has sudden acceleration and sudden braking in every session`() {
        val sessions = FakeDriveData.harsh(lastDate, dayCount, zone)

        assertTrue(sessions.isNotEmpty())
        sessions.forEach { session ->
            assertContract(session)
            val steps = session.samples.speedStepsKmh()
            assertTrue("no sudden acceleration: max ${steps.max()}", steps.any { it >= HARSH_ACCELERATION_KMH_PER_S })
            assertTrue("no sudden braking: min ${steps.min()}", steps.any { it <= -HARSH_BRAKING_KMH_PER_S })
        }
    }

    @Test
    fun `normal drive data has no sudden acceleration or braking`() {
        val steps = FakeDriveData.normal(lastDate, dayCount, zone).flatMap { it.samples.speedStepsKmh() }

        assertTrue(steps.none { it >= HARSH_ACCELERATION_KMH_PER_S || it <= -HARSH_BRAKING_KMH_PER_S })
    }

    @Test
    fun `missing signals drive data has null wiper and low beam and dropouts but keeps time speed and location`() {
        val sessions = FakeDriveData.missingSignals(lastDate, dayCount, zone)

        assertTrue(sessions.isNotEmpty())
        sessions.forEach { session ->
            assertContract(session)
            session.samples.forEach { sample ->
                assertNull(sample.wiperOn)
                assertNull(sample.lowBeamOn)
            }
            assertTrue("turn signal never drops out", session.samples.any { it.turnSignal == null })
            assertTrue("turn signal always missing", session.samples.any { it.turnSignal != null })
        }
    }

    @Test
    fun `all kinds puts each kind on its own dates and covers all three`() {
        val sessions = FakeDriveData.allKinds(lastDate, dayCount, zone)

        sessions.forEach { assertContract(it) }
        val kindsByDate =
            sessions
                .groupBy { startDate(it.startEpochMs) }
                .mapValues { (_, daySessions) -> daySessions.map { kindOf(it) }.toSet() }
        kindsByDate.forEach { (date, kinds) -> assertEquals("mixed kinds on $date: $kinds", 1, kinds.size) }
        assertEquals(setOf("normal", "harsh", "missing"), kindsByDate.values.flatten().toSet())
    }

    @Test
    fun `all kinds gives a date the same sessions whatever range it is generated in`() {
        val today = FakeDriveData.allKinds(lastDate, dayCount, zone)
        val tomorrow = FakeDriveData.allKinds(lastDate.plusDays(1), dayCount, zone)

        val shared = lastDate.minusDays(dayCount - 2L)..lastDate
        assertEquals(
            today.filter { startDate(it.startEpochMs) in shared },
            tomorrow.filter { startDate(it.startEpochMs) in shared },
        )
    }

    @Test
    fun `same arguments give the same sessions and another seed gives different ones`() {
        val first = FakeDriveData.normal(lastDate, dayCount, zone, seed = 7L)

        assertEquals(first, FakeDriveData.normal(lastDate, dayCount, zone, seed = 7L))
        assertNotEquals(first, FakeDriveData.normal(lastDate, dayCount, zone, seed = 8L))
    }

    @Test
    fun `a date gets the same sessions whatever range it is generated in`() {
        // Inserting again on a later day must replace, not stack, the shared dates.
        val today = FakeDriveData.normal(lastDate, dayCount, zone)
        val tomorrow = FakeDriveData.normal(lastDate.plusDays(1), dayCount, zone)

        val shared = lastDate.minusDays(dayCount - 2L)..lastDate
        assertEquals(
            today.filter { startDate(it.startEpochMs) in shared },
            tomorrow.filter { startDate(it.startEpochMs) in shared },
        )
    }
}
