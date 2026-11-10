package io.github.kimyeongbean.iter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every theme must read on its own panel and as a mark on the app background (#89). */
class ArtThemeTest {
    private val panels =
        ArtTheme.entries.flatMap { theme ->
            // Only the default theme takes the user's choices; one pair is enough for the others.
            val choices =
                if (theme == ArtTheme.DEFAULT) {
                    SpeedColorBase.entries.flatMap { base -> MapBackground.entries.map { base to it } }
                } else {
                    listOf(SpeedColorBase.AMBER to MapBackground.DARK)
                }
            choices.flatMap { (base, background) ->
                listOf(false, true).map { dark -> "$theme $base $background dark=$dark" to theme.panel(base, background, dark) }
            }
        }

    @Test
    fun `the default theme draws with the chosen base colour and background`() {
        val panel = ArtTheme.DEFAULT.panel(SpeedColorBase.CYAN, MapBackground.LIGHT, dark = true)

        assertEquals(MapBackground.LIGHT.argb, panel.argb)
        assertEquals(MapBackground.LIGHT.textArgb, panel.textArgb)
        assertEquals(SpeedColorBase.CYAN.argbOn(MapBackground.LIGHT), panel.speedColorBaseArgb)
    }

    @Test
    fun `the other themes ignore the chosen base colour and background`() {
        ArtTheme.entries.filter { it != ArtTheme.DEFAULT }.forEach { theme ->
            val one = theme.panel(SpeedColorBase.AMBER, MapBackground.DARK, dark = false)
            val other = theme.panel(SpeedColorBase.CYAN, MapBackground.LIGHT, dark = false)

            assertEquals("$theme ground", one.argb, other.argb)
            assertEquals("$theme line", one.speedColorBaseArgb, other.speedColorBaseArgb)
        }
    }

    @Test
    fun `the line at full speed and the text have at least 4_5 to 1 contrast on every panel`() {
        panels.forEach { (name, panel) ->
            val line = contrast(panel.speedColorBaseArgb, panel.argb)
            val text = contrast(panel.textArgb, panel.argb)
            assertTrue("$name line: $line", line >= 4.5)
            assertTrue("$name text: $text", text >= 4.5)
        }
    }

    @Test
    fun `every map graph effect colour has at least 1_5 to 1 contrast on every panel`() {
        panels.forEach { (name, panel) ->
            mapOf(
                "turn signal band" to panel.effects.turnSignalBandArgb,
                "low beam band" to panel.effects.lowBeamArgb,
                "rain at its most opaque" to compositeArgb(panel.effects.rainArgb, RAIN_ALPHA, panel.argb),
            ).forEach { (effect, argb) ->
                val ratio = contrast(argb, panel.argb)
                assertTrue("$name $effect: $ratio", ratio >= 1.5)
            }
        }
    }

    @Test
    fun `an advanced theme's panel sits flush in its card and follows the system theme`() {
        ArtTheme.entries.filter { it.art != null }.forEach { theme ->
            listOf(true, false).forEach { dark ->
                val panel = theme.panel(SpeedColorBase.AMBER, MapBackground.DARK, dark)
                assertEquals("$theme dark=$dark", theme.appColors(dark).cardArgb, panel.argb)
                assertEquals("$theme dark=$dark", dark, panel.dark)
            }
        }
    }

    @Test
    fun `text on the app background has at least 4_5 to 1 contrast on every backdrop pattern colour`() {
        ArtTheme.entries.forEach { theme ->
            val art = theme.art ?: return@forEach
            listOf(true, false).forEach { dark ->
                val colors = theme.appColors(dark)
                art.backdropArgbs(dark).forEach { backdrop ->
                    listOf("text" to colors.textArgb, "secondary text" to colors.textSecondaryArgb).forEach { (name, text) ->
                        val ratio = contrast(text, backdrop)
                        assertTrue("$theme dark=$dark $name: $ratio", ratio >= 4.5)
                    }
                }
            }
        }
    }

    @Test
    fun `the mark colour has at least 4_5 to 1 contrast on the app background in both system themes`() {
        ArtTheme.entries.forEach { theme ->
            listOf(true, false).forEach { dark ->
                val ratio = contrast(theme.accentArgb(SpeedColorBase.AMBER, dark), theme.appColors(dark).backgroundArgb)
                assertTrue("$theme dark=$dark: $ratio", ratio >= 4.5)
            }
        }
    }
}
