package io.github.kimyeongbean.iter.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.kimyeongbean.iter.art.DayArtData
import io.github.kimyeongbean.iter.art.FakeDriveData
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/** Where the pictures go, under the module directory. CI uploads this folder. */
private const val OUTPUT_DIR = "build/outputs/roborazzi"

/** Half way through the draw-in, and the finished drawing. */
private val DRAW_IN_STEPS = listOf(0.5f, 1f)

/**
 * Draws every theme's day art, map graph and splash, light and dark, half drawn and finished, into
 * picture files. Nothing is compared: the test fails only when drawing throws. The pictures are for looking
 * at what an emulator check would otherwise need (the dark schemes, a drawing half way).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h360dp-mdpi")
class ThemeScreenshotTest {
    private val drive = FakeDriveData.normal(lastDate = LocalDate.of(2026, 1, 7), dayCount = 7, zone = ZoneOffset.UTC).takeLast(1)

    @Test
    fun `every theme draws its day art and map graph`() {
        ArtTheme.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                DRAW_IN_STEPS.forEach { progress ->
                    val name = "${theme.name.lowercase()}_${if (dark) "dark" else "light"}_${(progress * 100).toInt()}"
                    val file = File(OUTPUT_DIR, "$name.png")
                    captureRoboImage(file.path) { ThemeSample(theme, dark, progress) }
                    assertTrue("$name was not drawn", file.length() > 0)
                }
            }
        }
    }

    @Test
    fun `every theme draws its splash`() {
        ArtTheme.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                DRAW_IN_STEPS.forEach { progress ->
                    val name = "splash_${theme.name.lowercase()}_${if (dark) "dark" else "light"}_${(progress * 100).toInt()}"
                    val file = File(OUTPUT_DIR, "$name.png")
                    captureRoboImage(file.path) {
                        IterTheme(dark, theme.appColors(dark)) {
                            Surface {
                                Splash(theme, SpeedColorSettings(theme = theme).accentArgb(dark), dark, elapsedMs = { SPLASH_DRAW_MS * progress }, onSkip = {})
                            }
                        }
                    }
                    assertTrue("$name was not drawn", file.length() > 0)
                }
            }
        }
    }

    @Composable
    private fun ThemeSample(
        theme: ArtTheme,
        dark: Boolean,
        progress: Float,
    ) {
        val settings = SpeedColorSettings(theme = theme)
        val panel = settings.panel(dark)
        val dayArt = DayArtData.of(drive, panel.speedColorBaseArgb)
        val route = mapRoute(drive, panel.speedColorBaseArgb).withSingleSampleStroke()
        val art = theme.art
        IterTheme(dark, theme.appColors(dark)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (art != null) Canvas(Modifier.fillMaxSize()) { with(art) { drawBackdrop(dark) } }
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    val panelModifier = Modifier.size(300.dp).clip(MaterialTheme.shapes.large).background(Color(panel.argb))
                    DayArt(dayArt, panel, panelModifier, drawInProgress = { progress })
                    MapGraph(route, panel, panelModifier, drawInProgress = { progress })
                }
            }
        }
    }

    /** Adds a stretch of one sample at the start: a theme must show it as a dot, not skip it. */
    private fun MapRoute.withSingleSampleStroke(): MapRoute {
        val first = strokes.first().first()
        return copy(strokes = listOf(listOf(first.copy(x = 0.1f, y = 0.1f, timePosition = 0f))) + strokes)
    }
}
