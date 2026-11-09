package io.github.kimyeongbean.iter.art

import io.github.kimyeongbean.iter.model.DriveSessionSource
import io.github.kimyeongbean.iter.model.SAMPLE_INTERVAL_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
