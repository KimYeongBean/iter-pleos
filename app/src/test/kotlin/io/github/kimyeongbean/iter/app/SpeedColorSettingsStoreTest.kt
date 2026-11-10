package io.github.kimyeongbean.iter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpeedColorSettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `defaults before anything is chosen`() {
        assertEquals(SpeedColorSettings(), SpeedColorSettingsStore(context).load())
    }

    @Test
    fun `chosen base colour and background survive a new store instance`() {
        val chosen = SpeedColorSettings(SpeedColorBase.CYAN, MapBackground.LIGHT)

        SpeedColorSettingsStore(context).save(chosen)

        // A new instance reads from disk, like the app after a restart.
        assertEquals(chosen, SpeedColorSettingsStore(context).load())
    }

    @Test
    fun `settings saved before themes existed open in the default theme with their colours kept`() {
        context
            .getSharedPreferences("speed_color_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("base", SpeedColorBase.PINK.name)
            .putString("background", MapBackground.LIGHT.name)
            .commit()

        assertEquals(
            SpeedColorSettings(SpeedColorBase.PINK, MapBackground.LIGHT, ArtTheme.DEFAULT),
            SpeedColorSettingsStore(context).load(),
        )
    }

    @Test
    fun `chosen theme survives a new store instance`() {
        val chosen = SpeedColorSettings(theme = ArtTheme.PAPER)

        SpeedColorSettingsStore(context).save(chosen)

        assertEquals(chosen, SpeedColorSettingsStore(context).load())
    }

    @Test
    fun `chosen screen mode survives a new store instance`() {
        val chosen = SpeedColorSettings(screenMode = ScreenMode.DARK)

        SpeedColorSettingsStore(context).save(chosen)

        assertEquals(chosen, SpeedColorSettingsStore(context).load())
    }

    @Test
    fun `screen mode follows the system unless light or dark is chosen`() {
        assertEquals(true, ScreenMode.SYSTEM.isDark(systemDark = true))
        assertEquals(false, ScreenMode.SYSTEM.isDark(systemDark = false))
        assertEquals(false, ScreenMode.LIGHT.isDark(systemDark = true))
        assertEquals(true, ScreenMode.DARK.isDark(systemDark = false))
    }
}
