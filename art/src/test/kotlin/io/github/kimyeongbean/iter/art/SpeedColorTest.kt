package io.github.kimyeongbean.iter.art

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedColorTest {
    // Opaque base colour 0xFF336699; only the alpha changes with speed.
    private val base = 0xFF336699.toInt()

    private fun alphaOf(argb: Int) = argb ushr 24

    private fun rgbOf(argb: Int) = argb and 0xFFFFFF

    @Test
    fun `intensity rises continuously from 0 to 120 kmh and stays full above`() {
        assertEquals(0.0, SpeedColor.intensity(0.0), 1e-9)
        assertEquals(0.5, SpeedColor.intensity(60.0), 1e-9)
        assertEquals(1.0, SpeedColor.intensity(120.0), 1e-9)
        assertEquals(1.0, SpeedColor.intensity(150.0), 1e-9)
    }

    @Test
    fun `intensity has no bands`() {
        // Every 1 km/h step from 0 to 120 makes the colour a little stronger.
        val steps = (0..120).map { SpeedColor.intensity(it.toDouble()) }
        steps.zipWithNext().forEach { (a, b) -> assertEquals(1.0 / 120, b - a, 1e-9) }
    }

    @Test
    fun `negative speed counts as standing still`() {
        assertEquals(0.0, SpeedColor.intensity(-5.0), 1e-9)
    }

    @Test
    fun `colour keeps the base hue and gets more opaque with speed`() {
        // Alpha 25% at 0 km/h, 62.5% at 60, full at 120 and above.
        assertEquals(64, alphaOf(SpeedColor.argb(base, 0.0)))
        assertEquals(159, alphaOf(SpeedColor.argb(base, 60.0)))
        assertEquals(255, alphaOf(SpeedColor.argb(base, 120.0)))
        assertEquals(255, alphaOf(SpeedColor.argb(base, 150.0)))
        listOf(0.0, 60.0, 120.0, 150.0).forEach { assertEquals(0x336699, rgbOf(SpeedColor.argb(base, it))) }
    }
}
