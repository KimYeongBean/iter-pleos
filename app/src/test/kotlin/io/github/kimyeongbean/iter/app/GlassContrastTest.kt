package io.github.kimyeongbean.iter.app

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text on a glass plate must keep 4.5:1 over whatever large area lies behind the plate (#122).
 * The plate's thinnest fill is laid over each colour behind it; blur is left out, so this is
 * stricter than the screen. The "기록 중" plate over its stop mark is not covered: there the blur
 * and where the text sits decide, so only a screenshot can tell.
 */
class GlassContrastTest {
    /** Which text colours each plate carries. */
    private enum class Ink { TEXT, SECONDARY }

    private class Plate(
        val glass: GlassLook,
        val inks: List<Ink>,
    )

    private val plates =
        mapOf(
            "Panel" to Plate(GlassLook.Panel, Ink.entries),
            "Strong" to Plate(GlassLook.Strong, Ink.entries),
            "Chip" to Plate(GlassLook.Chip, listOf(Ink.TEXT)),
            "Control" to Plate(GlassLook.Control, listOf(Ink.TEXT)),
            "Track" to Plate(GlassLook.Track, listOf(Ink.TEXT)),
            "Button" to Plate(GlassLook.Button, listOf(Ink.TEXT)),
            "Cell" to Plate(GlassLook.Cell, listOf(Ink.SECONDARY)),
            "EmptyCell" to Plate(GlassLook.EmptyCell, listOf(Ink.SECONDARY)),
        )

    private class Case(
        val name: String,
        val dark: Boolean,
        val colors: IterColors,
        val panel: ThemePanel,
        val accentArgb: Int,
        val art: ThemeArt?,
    )

    private val cases =
        ArtTheme.entries.flatMap { theme ->
            // Only the default theme takes the user's choices; one pair is enough for the others.
            val choices =
                if (theme == ArtTheme.DEFAULT) {
                    SpeedColorBase.entries.flatMap { base -> MapBackground.entries.map { base to it } }
                } else {
                    listOf(SpeedColorBase.AMBER to MapBackground.DARK)
                }
            choices.flatMap { (base, background) ->
                listOf(false, true).map { dark ->
                    Case(
                        "$theme $base $background dark=$dark",
                        dark,
                        theme.appColors(dark),
                        theme.panel(base, background, dark),
                        theme.accentArgb(base, dark),
                        theme.art,
                    )
                }
            }
        }

    /** The colours of the screen scene behind every plate: light, backdrop pattern, and a big art's panel. */
    private fun Case.scene(): Map<String, Int> {
        val glow = glassGlow(colors.backgroundArgb, accentArgb, art, dark)
        return buildMap {
            put("background", colors.backgroundArgb)
            put("light", glow.litArgb)
            put("ground", glow.groundArgb)
            put("ground shade", glow.shadeArgb)
            glow.tintArgb?.let { put("tint glow", compositeArgb(it, TINT_GLOW_ALPHA, glow.groundArgb)) }
            art?.backdropArgbs(dark)?.forEachIndexed { i, argb -> put("backdrop $i", argb) }
            if (art != null || panelShowsBehindPlates(panel.argb, dark)) put("art panel", panel.argb)
        }
    }

    @Test
    fun `text on every glass plate has at least 4_5 to 1 contrast over the scene behind it`() {
        assertAllRead { cases.forEach { case -> plates.forEach { (look, plate) -> case.check(look, plate, case.scene()) } } }
    }

    @Test
    fun `the start and end time chips read over the map graph panel`() {
        assertAllRead { cases.forEach { case -> case.check("Chip", plates.getValue("Chip"), mapOf("map panel" to case.panel.argb)) } }
    }

    private val failures = mutableListOf<String>()

    private fun assertAllRead(checks: () -> Unit) {
        checks()
        assertTrue(failures.joinToString("\n", "${failures.size} below 4.5:1:\n"), failures.isEmpty())
    }

    private fun Case.check(
        look: String,
        plate: Plate,
        behind: Map<String, Int>,
    ) {
        val tint = glassTint(colors.toColorScheme(dark).surfaceContainer, dark).toArgb()
        val thinnest = minOf(plate.glass.fillFromAlpha(dark), plate.glass.fillToAlpha(dark))
        behind.forEach { (ground, argb) ->
            val fill = compositeArgb(tint, thinnest, argb)
            plate.inks.forEach { ink ->
                val ratio = contrast(if (ink == Ink.TEXT) colors.textArgb else colors.textSecondaryArgb, fill)
                if (ratio < 4.5) failures += "$name $look over $ground, $ink: ${"%.2f".format(ratio)}"
            }
        }
    }
}
