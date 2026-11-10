package io.github.kimyeongbean.iter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kimyeongbean.iter.art.FakeDriveData
import io.github.kimyeongbean.iter.vehicle.RecordStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Seam 1: fake drive data saved through the record store reads back like real records. */
@RunWith(RobolectricTestRunner::class)
class FakeDriveDataInRecordStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun `saved fake drive data comes back as day records of each start date`() = runBlocking {
        val fake = FakeDriveData.allKinds(LocalDate.of(2026, 11, 9), dayCount = 28, zone = zone)
        val expectedByDate =
            fake.groupBy { Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() }

        RecordStore.open(context) { zone }.use { store ->
            fake.forEach { store.save(it) }

            assertEquals(expectedByDate.keys.sorted(), store.recordedDates().first())
            expectedByDate.forEach { (date, sessions) ->
                assertEquals(sessions.sortedBy { it.startEpochMs }, store.dayRecord(date))
            }
        }
    }

    @Test
    fun `saving fake drive data twice does not duplicate sessions`() = runBlocking {
        val fake = FakeDriveData.allKinds(LocalDate.of(2026, 11, 9), dayCount = 7, zone = zone)

        RecordStore.open(context) { zone }.use { store ->
            repeat(2) { fake.forEach { store.save(it) } }

            val stored = store.recordedDates().first().flatMap { store.dayRecord(it) }
            assertEquals(fake.size, stored.size)
        }
    }
}
