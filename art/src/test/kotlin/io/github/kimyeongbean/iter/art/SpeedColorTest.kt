package io.github.kimyeongbean.iter.art

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedColorTest {
    // Opaque base colour 0xFF336699; only the alpha changes with speed.
    private val base = 0xFF336699.toInt()

    private val halfKmh = SPEED_COLOR_MAX_KMH / 2
    private val overKmh = SPEED_COLOR_MAX_KMH + 30

    private fun alphaOf(argb: Int) = argb ushr 24

    private fun rgbOf(argb: Int) = argb and 0xFFFFFF

    @Test
    fun `intensity rises continuously to the max speed and stays full above`() {
        assertEquals(0.0, SpeedColor.intensity(0.0), 1e-9)
        assertEquals(0.5, SpeedColor.intensity(halfKmh), 1e-9)
        assertEquals(1.0, SpeedColor.intensity(SPEED_COLOR_MAX_KMH), 1e-9)
        assertEquals(1.0, SpeedColor.intensity(overKmh), 1e-9)
    }

    @Test
    fun `max speed is the 120 kmh common constant`() {
        assertEquals(120.0, SPEED_COLOR_MAX_KMH, 0.0)
    }

    @Test
    fun `intensity has no bands`() {
        // Every 1 km/h step up to the max speed makes the colour equally a little stronger.
        val maxKmh = SPEED_COLOR_MAX_KMH.toInt()
        val steps = (0..maxKmh).map { SpeedColor.intensity(it.toDouble()) }
        steps.zipWithNext().forEach { (a, b) -> assertEquals(1.0 / maxKmh, b - a, 1e-9) }
    }

    @Test
    fun `negative speed counts as standing still`() {
        assertEquals(0.0, SpeedColor.intensity(-5.0), 1e-9)
    }

    @Test
    fun `colour keeps the base hue and gets more opaque with speed`() {
        // Alpha 25% at 0 km/h, 62.5% at half the max speed, full at the max speed and above.
        assertEquals(64, alphaOf(SpeedColor.argb(base, 0.0)))
        assertEquals(159, alphaOf(SpeedColor.argb(base, halfKmh)))
        assertEquals(255, alphaOf(SpeedColor.argb(base, SPEED_COLOR_MAX_KMH)))
        assertEquals(255, alphaOf(SpeedColor.argb(base, overKmh)))
        listOf(0.0, halfKmh, SPEED_COLOR_MAX_KMH, overKmh).forEach { assertEquals(0x336699, rgbOf(SpeedColor.argb(base, it))) }
    }
}
