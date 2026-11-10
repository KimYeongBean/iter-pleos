package io.github.kimyeongbean.iter.app

import kotlin.math.roundToInt

/** Turn signal band: the background's text colour at this opacity. */
private const val TURN_SIGNAL_BAND_ALPHA = 0.32f

/** Rain streaks at their most opaque: the background's text colour at this opacity. */
private const val RAIN_ALPHA = 0.7f

/**
 * Map graph effect colours on one background, as opaque ARGB with no Compose types, so tests can
 * check their contrast (#56). [rainArgb] is the rain at its most opaque; it fades from there.
 */
internal class MapEffectColors(
    val turnSignalBandArgb: Int,
    val lowBeamArgb: Int,
    val rainArgb: Int,
)

/**
 * Low beam on the light background is a darker shade. On the dark background a darker colour
 * cannot stand out (even black is under 1.5:1), so it is a night blue band lighter than it.
 */
internal val MapBackground.mapEffectColors: MapEffectColors
    get() =
        MapEffectColors(
            turnSignalBandArgb = compositeArgb(textArgb, TURN_SIGNAL_BAND_ALPHA, argb),
            lowBeamArgb =
            when (this) {
                MapBackground.DARK -> 0xFF30406A.toInt()
                MapBackground.LIGHT -> 0xFFB8BECB.toInt()
            },
            rainArgb = compositeArgb(textArgb, RAIN_ALPHA, argb),
        )

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
