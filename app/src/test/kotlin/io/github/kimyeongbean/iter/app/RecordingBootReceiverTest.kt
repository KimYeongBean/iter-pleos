package io.github.kimyeongbean.iter.app

import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RecordingBootReceiverTest {
    private val app: IterApplication = ApplicationProvider.getApplicationContext()

    private fun agree() = app.consentStore.save(RecordingConsent(RecordingConsent.Answer.AGREED))

    private fun receive(action: String) = RecordingBootReceiver().onReceive(app, Intent(action))

    private fun nextStartedService() = shadowOf(app).nextStartedService?.component

    @Test
    fun `a boot starts the recording service`() {
        agree()
        receive(Intent.ACTION_BOOT_COMPLETED)

        assertEquals(ComponentName(app, RecordingService::class.java), nextStartedService())
    }

    @Test
    fun `an app update starts the recording service`() {
        agree()
        receive(Intent.ACTION_MY_PACKAGE_REPLACED)

        assertEquals(ComponentName(app, RecordingService::class.java), nextStartedService())
    }

    @Test
    fun `other broadcasts start nothing`() {
        agree()
        receive(Intent.ACTION_SCREEN_ON)

        assertNull(nextStartedService())
    }

    @Test
    fun `without the collection consent a boot starts nothing`() {
        receive(Intent.ACTION_BOOT_COMPLETED)

        assertNull(nextStartedService())
    }

    @Test
    fun `after declining a boot starts nothing`() {
        app.consentStore.save(RecordingConsent(RecordingConsent.Answer.DECLINED))

        receive(Intent.ACTION_BOOT_COMPLETED)

        assertNull(nextStartedService())
    }
}
