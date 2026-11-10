package io.github.kimyeongbean.iter.vehicle

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kimyeongbean.iter.model.DriveSession
import io.github.kimyeongbean.iter.model.DriveSessionSource
import io.github.kimyeongbean.iter.model.GeoPoint
import io.github.kimyeongbean.iter.model.SAMPLE_INTERVAL_MS
import io.github.kimyeongbean.iter.model.Sample
import io.github.kimyeongbean.iter.model.TurnSignal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class RecordStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Seoul")
    private val openStores = mutableListOf<RecordStore>()

    private fun openStore() = RecordStore.open(context) { zone }.also { openStores += it }

    @After
    fun closeStores() = openStores.forEach { it.close() }

    /** Epoch ms of a local time in [zone]. */
    private fun at(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
    ) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun session(
        startEpochMs: Long,
        seconds: Int = 3,
        source: DriveSessionSource = DriveSessionSource.VEHICLE,
    ) = DriveSession(
        startEpochMs = startEpochMs,
        endEpochMs = startEpochMs + (seconds - 1) * SAMPLE_INTERVAL_MS,
        source = source,
        samples =
        List(seconds) { i ->
            Sample(
                timeEpochMs = startEpochMs + i * SAMPLE_INTERVAL_MS,
                speedKmh = 10.5 * i,
                turnSignal = TurnSignal.entries[i % TurnSignal.entries.size],
                wiperOn = true,
                lowBeamOn = false,
                steeringAngleDeg = -12.5,
                brakePedalPressed = i == 0,
                location = GeoPoint(37.5 + i * 0.001, 127.0),
            )
        },
    )

    private val nov9 = LocalDate.of(2026, 11, 9)

    @Test
    fun `a time zone change after opening puts new sessions on the new zone's date, saved ones stay`() = runBlocking {
        var current = ZoneId.of("America/Los_Angeles")
        val store = RecordStore.open(context) { current }.also { openStores += it }
        // 2026-11-10 12:14 in Seoul is still 11-09 in Los Angeles.
        val before = session(at(nov9.plusDays(1), 12, 14))
        store.save(before)

        current = zone
        val after = session(at(nov9.plusDays(1), 12, 30))
        store.save(after)

        assertEquals(listOf(before), store.dayRecord(nov9))
        assertEquals(listOf(after), store.dayRecord(nov9.plusDays(1)))
    }

    @Test
    fun `saved session comes back as the day record of its start date`() = runBlocking {
        val store = openStore()
        val saved = session(at(nov9, 8))

        store.save(saved)

        assertEquals(listOf(saved), store.dayRecord(nov9))
        assertEquals(emptyList<DriveSession>(), store.dayRecord(nov9.plusDays(1)))
    }

    @Test
    fun `sessions of the same day form one day record in start order`() = runBlocking {
        val store = openStore()
        val evening = session(at(nov9, 18))
        val morning = session(at(nov9, 8))
        val nextDay = session(at(nov9.plusDays(2), 9))

        listOf(evening, nextDay, morning).forEach { store.save(it) }

        assertEquals(listOf(morning, evening), store.dayRecord(nov9))
        assertEquals(listOf(nov9, nov9.plusDays(2)), store.recordedDates().first())
    }

    @Test
    fun `session crossing midnight belongs to the date it started`() = runBlocking {
        val store = openStore()
        val lateNight = session(at(nov9, 23, 59), seconds = 120)

        store.save(lateNight)

        assertEquals(listOf(lateNight), store.dayRecord(nov9))
        assertEquals(emptyList<DriveSession>(), store.dayRecord(nov9.plusDays(1)))
    }

    @Test
    fun `missing signals stay null`() = runBlocking {
        val store = openStore()
        val start = at(nov9, 8)
        val sparse =
            DriveSession(
                startEpochMs = start,
                endEpochMs = null,
                source = DriveSessionSource.VEHICLE,
                samples = listOf(Sample(start, 42.0, null, null, null, null, null, null)),
            )

        store.save(sparse)

        assertEquals(listOf(sparse), store.dayRecord(nov9))
    }

    @Test
    fun `saving the same session again replaces it`() = runBlocking {
        val store = openStore()
        val start = at(nov9, 8)
        store.save(session(start, seconds = 2))
        val longer = session(start, seconds = 5)

        store.save(longer)

        assertEquals(listOf(longer), store.dayRecord(nov9))
    }

    @Test
    fun `real and fake sessions are stored and read the same way`() = runBlocking {
        val store = openStore()
        val start = at(nov9, 8)
        val real = session(start, source = DriveSessionSource.VEHICLE)
        val fake = session(start, source = DriveSessionSource.FAKE_DRIVE_DATA)

        store.save(real)
        store.save(fake)

        assertEquals(setOf(real, fake), store.dayRecord(nov9).toSet())
    }

    @Test
    fun `deleting all leaves no dates and empty day records`() = runBlocking {
        val store = openStore()
        val day = LocalDate.of(2026, 11, 9)
        store.save(session(at(day, 9)))
        store.save(session(at(day.plusDays(1), 9), source = DriveSessionSource.FAKE_DRIVE_DATA))

        store.deleteAll()

        assertEquals(emptyList<LocalDate>(), store.recordedDates().first())
        assertEquals(emptyList<DriveSession>(), store.dayRecord(day))
    }

    @Test
    fun `records survive closing and reopening the store`() = runBlocking {
        val saved = session(at(nov9, 8))
        openStore().apply {
            save(saved)
            close()
        }

        val reopened = openStore()

        assertEquals(listOf(nov9), reopened.recordedDates().first())
        assertEquals(listOf(saved), reopened.dayRecord(nov9))
    }
}
