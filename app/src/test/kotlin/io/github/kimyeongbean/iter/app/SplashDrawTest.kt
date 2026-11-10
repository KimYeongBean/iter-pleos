package io.github.kimyeongbean.iter.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Side of the square the splash marks are drawn into, in px. */
private const val SIDE_PX = 120

/**
 * The splash (GLOSSARY: 스플래시) draws every frame at whatever progress the frame time gives, so
 * drawing must not throw at any progress (#115: a library easing threw at a fraction just under 1).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SplashDrawTest {
    /** Every tenth of a millisecond of the draw-in, and the floats next to both ends. */
    private val progresses =
        (0..SPLASH_DRAW_MS * 10).map { it / (SPLASH_DRAW_MS * 10f) } + listOf(Math.nextUp(0f), Math.nextDown(1f))

    private fun drawAtEveryProgress(draw: DrawScope.(progress: Float) -> Unit) {
        val scope = CanvasDrawScope()
        val canvas = Canvas(ImageBitmap(SIDE_PX, SIDE_PX))
        val size = Size(SIDE_PX.toFloat(), SIDE_PX.toFloat())
        progresses.forEach { progress ->
            scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { draw(progress) }
        }
    }

    @Test
    fun `every advanced theme draws its splash at any progress`() {
        ArtTheme.entries.mapNotNull { it.art }.forEach { art ->
            listOf(false, true).forEach { dark ->
                drawAtEveryProgress { progress -> with(art) { drawSplash(dark, progress) } }
            }
        }
    }

    @Test
    fun `the route splash draws at any progress`() {
        drawAtEveryProgress { progress -> drawRouteSplash(Color.White, progress) }
    }

    @Test
    fun `easings start at 0, end at 1 and are whole outside 0 to 1`() {
        listOf(::easeInOut, ::easeOutBack).forEach { ease ->
            assertEquals(0f, ease(0f), 0f)
            assertEquals(1f, ease(1f), 0f)
            assertEquals(0f, ease(-0.5f), 0f)
            assertEquals(1f, ease(1.5f), 0f)
        }
    }

    @Test
    fun `ease out back runs a little past 1 on its way`() {
        val highest = progresses.maxOf { easeOutBack(it) }
        assertTrue("highest $highest", highest > 1.05f && highest < 1.15f)
    }
}
