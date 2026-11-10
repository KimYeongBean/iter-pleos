package io.github.kimyeongbean.iter.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.kimyeongbean.iter.art.SPEED_COLOR_MAX_KMH
import io.github.kimyeongbean.iter.art.SpeedColor

/** Speeds sampled for the strip's gradient stops. */
private const val STRIP_STEP_KMH = 10

/**
 * A rounded strip of the speed colour (GLOSSARY: 속도색) from standstill to [SPEED_COLOR_MAX_KMH],
 * left to right, for [baseArgb]. Draw it on the background that base colour was made for.
 */
@Composable
internal fun SpeedColorStrip(
    baseArgb: Int,
    modifier: Modifier = Modifier,
) {
    val stops =
        remember(baseArgb) {
            val maxKmh = SPEED_COLOR_MAX_KMH.toInt()
            (0..maxKmh step STRIP_STEP_KMH)
                .map { kmh -> kmh.toFloat() / maxKmh to Color(SpeedColor.argb(baseArgb, kmh.toDouble())) }
                .toTypedArray()
        }
    Canvas(modifier) {
        drawRoundRect(Brush.horizontalGradient(*stops), cornerRadius = CornerRadius(size.height / 2))
    }
}
