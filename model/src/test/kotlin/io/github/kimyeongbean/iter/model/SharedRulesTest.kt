package io.github.kimyeongbean.iter.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedRulesTest {
    private fun sample(timeEpochMs: Long) = Sample(timeEpochMs, 0.0, null, null, null, null, null, null)

    private fun session(
        startEpochMs: Long,
        source: DriveSessionSource,
        sampleTimes: List<Long>,
    ) = DriveSession(startEpochMs, sampleTimes.maxOrNull(), source, sampleTimes.map(::sample))

    @Test
    fun `drive order is start time then source, with samples in time order, whatever the input order`() {
        val evening = session(5_000L, DriveSessionSource.VEHICLE, listOf(7_000L, 5_000L, 6_000L))
        val morningFake = session(1_000L, DriveSessionSource.FAKE_DRIVE_DATA, listOf(2_000L, 1_000L))
        val morningReal = session(1_000L, DriveSessionSource.VEHICLE, listOf(1_000L))

        val ordered = listOf(evening, morningFake, morningReal).inDriveOrder()

        // VEHICLE is declared before FAKE_DRIVE_DATA, so it wins a start time tie.
        assertEquals(
            listOf(1_000L to DriveSessionSource.VEHICLE, 1_000L to DriveSessionSource.FAKE_DRIVE_DATA, 5_000L to DriveSessionSource.VEHICLE),
            ordered.map { it.startEpochMs to it.source },
        )
        assertEquals(listOf(listOf(1_000L), listOf(1_000L, 2_000L), listOf(5_000L, 6_000L, 7_000L)), ordered.map { s -> s.samples.map { it.timeEpochMs } })
        assertEquals(ordered, listOf(morningReal, evening, morningFake).inDriveOrder())
    }

    @Test
    fun `turn signal is on when left, right or hazard, and off when off or missing`() {
        assertEquals(
            listOf(true, true, true, false, false),
            listOf(TurnSignal.LEFT, TurnSignal.RIGHT, TurnSignal.HAZARD, TurnSignal.OFF, null).map { it.isOn },
        )
    }
}
