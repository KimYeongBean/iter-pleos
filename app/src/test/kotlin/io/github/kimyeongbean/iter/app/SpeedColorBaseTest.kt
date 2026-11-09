package io.github.kimyeongbean.iter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class SpeedColorBaseTest {
    /** WCAG 2.x relative luminance of an opaque ARGB colour. */
    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(
        a: Int,
        b: Int,
    ): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    @Test
    fun `contrast helper matches known WCAG values`() {
        assertEquals(21.0, contrast(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01)
        assertEquals(1.0, contrast(0xFF777777.toInt(), 0xFF777777.toInt()), 0.01)
    }

    @Test
    fun `palette has five base colours`() {
        assertEquals(5, SpeedColorBase.entries.size)
    }

    @Test
    fun `every base colour at full speed has at least 4_5 to 1 contrast on both backgrounds`() {
        SpeedColorBase.entries.forEach { base ->
            MapBackground.entries.forEach { background ->
                val ratio = contrast(base.argbOn(background), background.argb)
                assertTrue("$base on $background: $ratio", ratio >= 4.5)
            }
        }
    }

    @Test
    fun `preview labels are readable on both backgrounds`() {
        MapBackground.entries.forEach { background ->
            val ratio = contrast(background.textArgb, background.argb)
            assertTrue("$background text: $ratio", ratio >= 4.5)
        }
    }
}
