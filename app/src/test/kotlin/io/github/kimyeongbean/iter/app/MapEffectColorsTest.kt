package io.github.kimyeongbean.iter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Map graph effect colours must show on their background (#56). Decorative, so 1.5:1, not 4.5:1. */
class MapEffectColorsTest {
    @Test
    fun `every effect colour has at least 1_5 to 1 contrast on its background`() {
        MapBackground.entries.forEach { background ->
            val colors = background.mapEffectColors
            mapOf(
                "turn signal band" to colors.turnSignalBandArgb,
                "low beam band" to colors.lowBeamArgb,
                "rain at its most opaque" to compositeArgb(colors.rainArgb, RAIN_ALPHA, background.argb),
            ).forEach { (effect, argb) ->
                val ratio = contrast(argb, background.argb)
                assertTrue("$effect on $background: $ratio", ratio >= 1.5)
            }
        }
    }

    @Test
    fun `on the dark background the low beam band is lighter than the background, on the light one darker`() {
        assertTrue(luminance(MapBackground.DARK.mapEffectColors.lowBeamArgb) > luminance(MapBackground.DARK.argb))
        assertTrue(luminance(MapBackground.LIGHT.mapEffectColors.lowBeamArgb) < luminance(MapBackground.LIGHT.argb))
    }

    @Test
    fun `compositing mixes each channel by alpha and stays opaque`() {
        assertEquals(0xFF808080.toInt(), compositeArgb(0xFFFFFFFF.toInt(), 0.5f, 0xFF000000.toInt()))
        assertEquals(0xFF000000.toInt(), compositeArgb(0xFFFFFFFF.toInt(), 0f, 0xFF000000.toInt()))
    }
}
