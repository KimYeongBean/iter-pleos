package io.github.kimyeongbean.iter.app

import kotlin.math.roundToInt

/** Rain streaks at their most opaque: [MapEffectColors.rainArgb] at this opacity. */
internal const val RAIN_ALPHA = 0.7f

/**
 * Map graph effect colours on one background, as ARGB with no Compose types, so tests can check
 * their contrast (#56). The bands are opaque; [rainArgb] is drawn see-through, at most [RAIN_ALPHA].
 */
internal data class MapEffectColors(
    val turnSignalBandArgb: Int,
    val lowBeamArgb: Int,
    val rainArgb: Int,
)

/**
 * The turn signal band is the text colour see-through over the background; the light background
 * needs it a little stronger to reach 1.5:1. Low beam on the light background is a darker shade.
 * On the dark background a darker colour cannot stand out (even black is under 1.5:1), so it is a
 * night blue band lighter than it.
 */
internal val MapBackground.mapEffectColors: MapEffectColors
    get() =
        when (this) {
            MapBackground.DARK ->
                MapEffectColors(
                    turnSignalBandArgb = compositeArgb(textArgb, 0.25f, argb),
                    lowBeamArgb = 0xFF30406A.toInt(),
                    rainArgb = textArgb,
                )

            MapBackground.LIGHT ->
                MapEffectColors(
                    turnSignalBandArgb = compositeArgb(textArgb, 0.32f, argb),
                    lowBeamArgb = 0xFFB8BECB.toInt(),
                    rainArgb = textArgb,
                )
        }

/** [foregroundArgb] at [alpha] over the opaque [backgroundArgb], as an opaque colour. */
internal fun compositeArgb(
    foregroundArgb: Int,
    alpha: Float,
    backgroundArgb: Int,
): Int {
    fun channel(shift: Int): Int {
        val fg = (foregroundArgb shr shift) and 0xFF
        val bg = (backgroundArgb shr shift) and 0xFF
        return (fg * alpha + bg * (1 - alpha)).roundToInt() shl shift
    }
    return 0xFF shl 24 or channel(16) or channel(8) or channel(0)
}
