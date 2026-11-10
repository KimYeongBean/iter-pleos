package io.github.kimyeongbean.iter.app

import androidx.annotation.StringRes

/**
 * Theme (GLOSSARY: 테마): the colours and the way the map graph, the day art and the grass cells
 * are drawn, chosen by the user. In the default theme (GLOSSARY: 기본 테마) and the plain themes (GLOSSARY: 일반 테마) the drawing is the same,
 * only the panel ground and the line colour differ. An advanced theme (GLOSSARY: 고급 테마) has
 * its own drawing, app colours and backdrop pattern ([art]). Only [DEFAULT] lets the user pick the
 * speed colour base and the background; the others fix both.
 */
internal enum class ArtTheme(
    @StringRes val label: Int,
    private val fixed: FixedThemeColors?,
    val art: ThemeArt? = null,
) {
    DEFAULT(R.string.theme_default, null),

    /** Pure black with one vermilion line. Not red: Pleos informative red would read as a warning. */
    INK(
        R.string.theme_ink,
        FixedThemeColors(
            panelArgb = 0xFF000000.toInt(),
            textArgb = 0xFFB8B8B8.toInt(),
            lineOnDarkArgb = 0xFFFF5A36.toInt(),
            lineOnLightArgb = 0xFFC2410C.toInt(),
            panelIsDark = true,
            turnSignalBandAlpha = 0.28f,
            lowBeamArgb = 0xFF30406A.toInt(),
        ),
    ),

    /** Off-white paper with an ink-black line, like a map drawn by hand. */
    PAPER(
        R.string.theme_paper,
        FixedThemeColors(
            panelArgb = 0xFFF4EEDF.toInt(),
            textArgb = 0xFF6B6457.toInt(),
            lineOnDarkArgb = 0xFFEDE6D6.toInt(),
            lineOnLightArgb = 0xFF2B2A28.toInt(),
            panelIsDark = false,
            turnSignalBandAlpha = 0.32f,
            lowBeamArgb = 0xFFBDB5A2.toInt(),
        ),
    ),

    /** Asphalt grey with a white line, like a race track plan. */
    CIRCUIT(
        R.string.theme_circuit,
        FixedThemeColors(
            panelArgb = 0xFF3A3D42.toInt(),
            textArgb = 0xFFD5D8DD.toInt(),
            lineOnDarkArgb = 0xFFFFFFFF.toInt(),
            lineOnLightArgb = 0xFF44464E.toInt(),
            panelIsDark = true,
            turnSignalBandAlpha = 0.25f,
            lowBeamArgb = 0xFF5F73A8.toInt(),
        ),
    ),

    GARDEN(R.string.theme_garden, null, GardenArt),
    ;

    /** App colours for the system theme: an advanced theme's own, else the Pleos ones. */
    fun appColors(dark: Boolean): IterColors {
        val art = art ?: return if (dark) IterDarkColors else IterLightColors
        return if (dark) art.darkColors else art.lightColors
    }

    /**
     * What the art panels draw with. [base] and [background] count only for [DEFAULT]; [dark] (the
     * system theme) only for an advanced theme, whose panels follow the app colours.
     */
    fun panel(
        base: SpeedColorBase,
        background: MapBackground,
        dark: Boolean,
    ): ThemePanel {
        if (art != null) return art.panel(dark)
        val fixed = fixed ?: return ThemePanel(background.argb, background.textArgb, base.argbOn(background), background.mapEffectColors)
        return ThemePanel(
            argb = fixed.panelArgb,
            textArgb = fixed.textArgb,
            speedColorBaseArgb = if (fixed.panelIsDark) fixed.lineOnDarkArgb else fixed.lineOnLightArgb,
            effects =
            MapEffectColors(
                turnSignalBandArgb = compositeArgb(fixed.textArgb, fixed.turnSignalBandAlpha, fixed.panelArgb),
                lowBeamArgb = fixed.lowBeamArgb,
                rainArgb = fixed.textArgb,
            ),
        )
    }

    /**
     * The theme's line colour for a mark on the app background, which follows the system: the
     * variant made for a dark ground when [dark], else the one for a light ground.
     */
    fun accentArgb(
        base: SpeedColorBase,
        dark: Boolean,
    ): Int {
        if (art != null) return art.panel(dark).speedColorBaseArgb
        val fixed = fixed ?: return base.argbOn(if (dark) MapBackground.DARK else MapBackground.LIGHT)
        return if (dark) fixed.lineOnDarkArgb else fixed.lineOnLightArgb
    }
}

/**
 * Colours a theme fixes, as ARGB. The line has a variant per ground brightness, like
 * [SpeedColorBase], so it keeps 4.5:1 both on the panel and as a mark on the app background.
 */
internal class FixedThemeColors(
    val panelArgb: Int,
    val textArgb: Int,
    val lineOnDarkArgb: Int,
    val lineOnLightArgb: Int,
    val panelIsDark: Boolean,
    /** Opacity of the text colour over the panel that makes the turn signal band. */
    val turnSignalBandAlpha: Float,
    val lowBeamArgb: Int,
)

/**
 * What a map graph panel, a day art panel or a grass cell is drawn with: the ground, text on it,
 * the speed colour base and the map graph effect colours. ARGB with no Compose types, so tests can
 * check the contrast. A data class, so screens taking an equal one are not drawn again.
 */
internal data class ThemePanel(
    val argb: Int,
    val textArgb: Int,
    val speedColorBaseArgb: Int,
    val effects: MapEffectColors,
    /** An advanced theme's own drawing; null draws the plain way. */
    val art: ThemeArt? = null,
    /** Which of the advanced theme's colour sets this is. */
    val dark: Boolean = false,
)
