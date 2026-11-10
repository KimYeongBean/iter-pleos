package io.github.kimyeongbean.iter.app

import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.DriveSessionSource
import io.github.kimyeongbean.iter.model.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class DriveSummaryTest {
    private val zone = ZoneId.of("Asia/Seoul")

    private fun epochMs(
        hour: Int,
        minute: Int,
    ) = LocalDateTime.of(2026, 11, 10, hour, minute, 30).atZone(zone).toInstant().toEpochMilli()

    private fun session(
        startEpochMs: Long,
        endEpochMs: Long?,
        samples: List<Sample> = emptyList(),
    ) = DriveSession(startEpochMs, endEpochMs, DriveSessionSource.VEHICLE, samples)

    @Test
    fun `a day with no drive session has no summary`() {
        assertNull(driveSummary(emptyList(), zone))
    }

    @Test
    fun `summary counts the sessions and spans the first start to the last end`() {
        val dayRecord =
            listOf(
                session(epochMs(18, 5), epochMs(18, 47)),
                session(epochMs(8, 12), epochMs(8, 47)),
            )

        assertEquals(DriveSummary(2, LocalTime.of(8, 12), LocalTime.of(18, 47)), driveSummary(dayRecord, zone))
    }

    @Test
    fun `the time span reads as two 24 hour times`() {
        assertEquals("08:12 – 18:47", DriveSummary(2, LocalTime.of(8, 12), LocalTime.of(18, 47)).timeSpanText)
    }

    @Test
    fun `a session still in progress ends at its last sample, or at its start with no sample`() {
        val lastSample = Sample(epochMs(9, 20), 30.0, null, null, null, null, null, null)
        val withSample = session(epochMs(9, 0), null, listOf(lastSample))
        val withoutSample = session(epochMs(9, 0), null)

        assertEquals(LocalTime.of(9, 20), driveSummary(listOf(withSample), zone)?.lastEnd)
        assertEquals(LocalTime.of(9, 0), driveSummary(listOf(withoutSample), zone)?.lastEnd)
    }
}
