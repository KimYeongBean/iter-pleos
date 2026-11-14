package io.github.kimyeongbean.iter.app

import androidx.test.core.app.ApplicationProvider
import io.github.kimyeongbean.iter.art.FakeDriveData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class RecordingConsentTest {
    private val app: IterApplication = ApplicationProvider.getApplicationContext()

    @Test
    fun `nothing is asked or agreed at first, so nothing records`() {
        val consent = RecordingConsentStore(app).load()

        assertEquals(RecordingConsent.Answer.NOT_ASKED, consent.answer)
        assertFalse(consent.agreed)
        assertFalse(app.startRecording())
    }

    @Test
    fun `the answer survives a new store instance`() {
        RecordingConsentStore(app).save(RecordingConsent(RecordingConsent.Answer.AGREED, location = true))

        // A new instance reads from disk, like the recording service after a boot.
        val loaded = RecordingConsentStore(app).load()
        assertTrue(loaded.agreed)
        assertTrue(loaded.recordsLocation)
    }

    @Test
    fun `location is recorded only when collection is agreed too`() {
        assertFalse(RecordingConsent(RecordingConsent.Answer.DECLINED, location = true).recordsLocation)
        assertFalse(RecordingConsent(RecordingConsent.Answer.AGREED, location = false).recordsLocation)
        assertTrue(RecordingConsent(RecordingConsent.Answer.AGREED, location = true).recordsLocation)
    }

    @Test
    fun `withdrawing deletes every record and stops recording from starting again`() = runBlocking {
        app.consentStore.save(RecordingConsent(RecordingConsent.Answer.AGREED))
        FakeDriveData.allKinds(LocalDate.of(2026, 11, 9), 3, ZoneId.of("Asia/Seoul")).forEach { app.recordStore.save(it) }

        app.withdrawConsent()

        assertEquals(RecordingConsent.Answer.DECLINED, app.consentStore.load().answer)
        assertTrue((7..9).all { app.recordStore.dayRecord(LocalDate.of(2026, 11, it)).isEmpty() })
        assertFalse(app.startRecording())
    }
}
