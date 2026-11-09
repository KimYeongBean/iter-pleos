package io.github.kimyeongbean.iter.art

import kotlin.math.roundToInt

/** Speed at and above which the speed colour is strongest (docs/interfaces.md common constant). */
const val SPEED_COLOR_MAX_KMH: Double = 120.0

/**
 * Speed colour (GLOSSARY: 속도색): one colour that gets continuously stronger with speed, no bands.
 * The strongest point is fixed at [SPEED_COLOR_MAX_KMH] whatever the date, so days compare.
 */
object SpeedColor {
    /** Alpha at 0 km/h, so standing still stays faintly visible. */
    private const val MIN_ALPHA = 0.25

    /** How strong the colour is at [speedKmh]: 0 at standstill, 1 at [SPEED_COLOR_MAX_KMH] and above. */
    fun intensity(speedKmh: Double): Double = (speedKmh / SPEED_COLOR_MAX_KMH).coerceIn(0.0, 1.0)

    /**
     * [baseColorArgb] (the user's speed colour base, ARGB) at [speedKmh]: same hue, alpha from 25%
     * at standstill to full at [SPEED_COLOR_MAX_KMH]. Draw it over the background to see it.
     */
    fun argb(
        baseColorArgb: Int,
        speedKmh: Double,
    ): Int {
        val alpha = ((MIN_ALPHA + (1 - MIN_ALPHA) * intensity(speedKmh)) * 255).roundToInt()
        return (alpha shl 24) or (baseColorArgb and 0xFFFFFF)
    }
}
