package io.github.kimyeongbean.iter.app

import kotlin.math.pow

/** WCAG 2.x relative luminance of an opaque ARGB colour. */
internal fun luminance(argb: Int): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

/** WCAG 2.x contrast ratio of two opaque ARGB colours (1 to 21). */
internal fun contrast(
    a: Int,
    b: Int,
): Double {
    val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
    return (light + 0.05) / (dark + 0.05)
}
