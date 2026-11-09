package io.github.kimyeongbean.iter.app

import io.github.kimyeongbean.iter.art.DayArtShape
import org.junit.Assert.assertEquals
import org.junit.Test

class DayArtDrawInTest {
    // The second quarter of the day: time position 0.25 to 0.5.
    private val shape =
        DayArtShape(
            timePosition = 0.25,
            startAngleDeg = 90.0,
            sweepAngleDeg = 90.0,
            innerRadius = 0.25,
            outerRadius = 0.5,
            colorArgb = 0,
            patterns = emptySet(),
        )

    @Test
    fun `a shape draws in while the draw-in passes its stretch of drive time`() {
        assertEquals(0f, shape.drawnFraction(0f), 1e-6f)
        assertEquals(0f, shape.drawnFraction(0.25f), 1e-6f)
        assertEquals(0.5f, shape.drawnFraction(0.375f), 1e-6f)
        assertEquals(1f, shape.drawnFraction(0.5f), 1e-6f)
        assertEquals(1f, shape.drawnFraction(1f), 1e-6f)
    }
}
