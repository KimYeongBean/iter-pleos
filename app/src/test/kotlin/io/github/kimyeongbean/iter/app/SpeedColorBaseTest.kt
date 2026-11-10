package io.github.kimyeongbean.iter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedColorBaseTest {
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
