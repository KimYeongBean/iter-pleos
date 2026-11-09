package io.github.kimyeongbean.iter.vehicle

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.DriveSessionSource
import io.github.kimyeongbean.iter.model.GeoPoint
import io.github.kimyeongbean.iter.model.SAMPLE_INTERVAL_MS
import io.github.kimyeongbean.iter.model.Sample
import io.github.kimyeongbean.iter.model.TurnSignal
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/** Seam 1: fake vehicle signal sequences in, day records out of the record store. No Pleos SDK. */
@RunWith(RobolectricTestRunner::class)
class DriveSessionRecorderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Seoul")
    private val nov9 = LocalDate.of(2026, 11, 9)
    private val openStores = mutableListOf<RecordStore>()

    private fun openStore() = RecordStore.open(context, zone).also { openStores += it }

    @After
    fun closeStores() = openStores.forEach { it.close() }

    private fun at(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
    ) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** One stretch of identical signals, one per second. */
    private data class Stretch(
        val gear: Gear?,
        val speedKmh: Double,
        val seconds: Int,
        val allSignals: Boolean = true,
    )

    /** One-second vehicle signals for [stretches] played back to back from [startEpochMs]. */
    private fun signals(
        startEpochMs: Long,
        vararg stretches: Stretch,
    ): List<VehicleSignals> {
        var timeEpochMs = startEpochMs
        return stretches.flatMap { stretch ->
            List(stretch.seconds) {
                val sample =
                    if (stretch.allSignals) {
                        Sample(timeEpochMs, stretch.speedKmh, TurnSignal.OFF, false, true, 1.5, false, GeoPoint(37.5, 127.0))
                    } else {
                        Sample(timeEpochMs, stretch.speedKmh, null, null, null, null, null, null)
                    }
                timeEpochMs += SAMPLE_INTERVAL_MS
                VehicleSignals(sample, stretch.gear)
            }
        }
    }

    private val endWaitS = (DRIVE_SESSION_END_WAIT_MS / SAMPLE_INTERVAL_MS).toInt()

    private fun drive(seconds: Int) = Stretch(Gear.DRIVE, 30.0, seconds)

    private fun parked(seconds: Int) = Stretch(Gear.PARK, 0.0, seconds)

    /** The session the recorder should save: samples up to the stop start, which is also the end. */
    private fun expectedSession(
        played: List<VehicleSignals>,
        stopStartEpochMs: Long,
    ): DriveSession {
        val driveStartEpochMs = played.first { it.gear == Gear.DRIVE }.sample.timeEpochMs
        return DriveSession(
            startEpochMs = driveStartEpochMs,
            endEpochMs = stopStartEpochMs,
            source = DriveSessionSource.VEHICLE,
            samples = played.map { it.sample }.filter { it.timeEpochMs in driveStartEpochMs..stopStartEpochMs },
        )
    }

    @Test
    fun `drive starts on gear D and ends after parked at 0 kmh for the end wait, ending at the stop start`() = runBlocking {
        val store = openStore()
        val start = at(nov9, 8)
        val parkedBeforeS = 5
        val driveS = 100
        val played = signals(start, parked(parkedBeforeS), drive(driveS), parked(endWaitS + 1))

        DriveSessionRecorder(store).record(played.asFlow())

        val stopStart = start + (parkedBeforeS + driveS) * SAMPLE_INTERVAL_MS
        val sessions = store.dayRecord(nov9)
        assertEquals(listOf(expectedSession(played, stopStart)), sessions)
        // One sample per second from D to the stop start, both ends included.
        assertEquals(driveS + 1, sessions.single().samples.size)
    }

    @Test
    fun `parked shorter than the end wait keeps the same drive session going`() = runBlocking {
        val store = openStore()
        val start = at(nov9, 8)
        val played = signals(start, drive(10), parked(endWaitS), drive(10), parked(endWaitS + 1))

        DriveSessionRecorder(store).record(played.asFlow())

        val secondStopStart = start + (10 + endWaitS + 10) * SAMPLE_INTERVAL_MS
        assertEquals(listOf(expectedSession(played, secondStopStart)), store.dayRecord(nov9))
    }

    @Test
    fun `standing still in D does not end the drive session`() = runBlocking {
        val store = openStore()
        val start = at(nov9, 8)
        val played = signals(start, drive(10), Stretch(Gear.DRIVE, 0.0, endWaitS * 3), parked(endWaitS + 1))

        DriveSessionRecorder(store).record(played.asFlow())

        val stopStart = start + (10 + endWaitS * 3) * SAMPLE_INTERVAL_MS
        assertEquals(listOf(expectedSession(played, stopStart)), store.dayRecord(nov9))
    }

    @Test
    fun `no drive session without gear D`() = runBlocking {
        val store = openStore()
        val played = signals(at(nov9, 8), parked(100), Stretch(Gear.NEUTRAL, 0.0, 10), Stretch(null, 20.0, 30))

        DriveSessionRecorder(store).record(played.asFlow())

        assertEquals(emptyList<LocalDate>(), store.recordedDates().first())
    }

    @Test
    fun `missing signals are recorded as null and the drive session still records`() = runBlocking {
        val store = openStore()
        val start = at(nov9, 8)
        val played =
            signals(
                start,
                Stretch(Gear.DRIVE, 40.0, 30, allSignals = false),
                Stretch(Gear.PARK, 0.0, endWaitS + 1, allSignals = false),
            )

        DriveSessionRecorder(store).record(played.asFlow())

        val session = store.dayRecord(nov9).single()
        assertEquals(expectedSession(played, start + 30 * SAMPLE_INTERVAL_MS), session)
        assertTrue(session.samples.all { it.turnSignal == null && it.wiperOn == null && it.location == null })
    }

    @Test
    fun `drive sessions of the same day form one day record`() = runBlocking {
        val store = openStore()
        val morning = signals(at(nov9, 8), drive(20), parked(endWaitS + 1))
        val evening = signals(at(nov9, 18), drive(30), parked(endWaitS + 1))

        DriveSessionRecorder(store).record((morning + evening).asFlow())

        assertEquals(
            listOf(
                expectedSession(morning, at(nov9, 8) + 20 * SAMPLE_INTERVAL_MS),
                expectedSession(evening, at(nov9, 18) + 30 * SAMPLE_INTERVAL_MS),
            ),
            store.dayRecord(nov9),
        )
        assertEquals(listOf(nov9), store.recordedDates().first())
    }

    @Test
    fun `drive session crossing midnight belongs to the date it started`() = runBlocking {
        val store = openStore()
        val start = at(nov9, 23, 59)
        val played = signals(start, drive(120), parked(endWaitS + 1))

        DriveSessionRecorder(store).record(played.asFlow())

        assertEquals(listOf(expectedSession(played, start + 120 * SAMPLE_INTERVAL_MS)), store.dayRecord(nov9))
        assertEquals(emptyList<DriveSession>(), store.dayRecord(nov9.plusDays(1)))
    }

    @Test
    fun `samples are saved while driving and a restart closes the session at its last saved sample`() = runBlocking {
        val store = openStore()
        val start = at(nov9, 8)
        // A drive that is still going when the app dies: the flow never ends on its own.
        val played = signals(start, drive(IN_PROGRESS_SAVE_EVERY_SAMPLES * 2 + 5))
        val killed = launch {
            DriveSessionRecorder(store).record(
                flow {
                    emitAll(played.asFlow())
                    awaitCancellation()
                },
            )
        }

        // The first sample is saved at gear D, then every IN_PROGRESS_SAVE_EVERY_SAMPLES new ones.
        val savedCount = 1 + IN_PROGRESS_SAVE_EVERY_SAMPLES * 2
        withTimeout(10_000) {
            while (store.dayRecord(nov9).singleOrNull()?.samples?.size != savedCount) delay(20)
        }
        killed.cancelAndJoin()
        val inProgress = store.dayRecord(nov9).single()
        assertNull(inProgress.endEpochMs)
        assertEquals(played.take(savedCount).map { it.sample }, inProgress.samples)

        // Restart: the recorder closes the unfinished session before it reads new signals.
        DriveSessionRecorder(store).record(emptyFlow())

        val lastSaved = played[savedCount - 1].sample.timeEpochMs
        assertEquals(inProgress.copy(endEpochMs = lastSaved), store.dayRecord(nov9).single())
    }

    @Test
    fun `in progress is true from gear D until the drive session ends`() = runBlocking {
        val store = openStore()
        val recorder = DriveSessionRecorder(store)
        val start = at(nov9, 8)
        val feed = Channel<VehicleSignals>()

        recorder.inProgress.test {
            assertFalse(awaitItem())
            val recording = launch { recorder.record(feed.consumeAsFlow()) }

            signals(start, parked(3)).forEach { feed.send(it) }
            signals(start + 3 * SAMPLE_INTERVAL_MS, drive(5)).forEach { feed.send(it) }
            assertTrue(awaitItem())

            signals(start + 8 * SAMPLE_INTERVAL_MS, parked(endWaitS + 1)).forEach { feed.send(it) }
            assertFalse(awaitItem())

            recording.cancelAndJoin()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
