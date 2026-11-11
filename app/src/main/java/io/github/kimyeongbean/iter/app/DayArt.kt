package io.github.kimyeongbean.iter.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.kimyeongbean.iter.art.DayArtData
import io.github.kimyeongbean.iter.art.DayArtPattern
import io.github.kimyeongbean.iter.art.DayArtShape

/** Not yet drawn shapes show this faintly during the draw-in, so the grown grass cell never goes blank. */
internal const val DRAW_IN_GHOST_ALPHA = 0.15f

/** The drawing radius leaves this share of half the box empty round the art. */
internal const val DRAWING_RADIUS_SCALE = 0.92f

/** Pattern rings, as fractions of the drawing radius. Speed bars end at [DayArtData.SPEED_BAR_MAX_RADIUS]. */
private const val LOW_BEAM_RING_RADIUS = 0.19f
private const val LOW_BEAM_RING_WIDTH = 0.06f
private const val TURN_SIGNAL_RING_RADIUS = 0.9f
private const val TURN_SIGNAL_RING_WIDTH = 0.05f
private const val WIPER_RING_RADIUS = 0.97f
private const val WIPER_RING_WIDTH = 0.03f

/** 0 before the draw-in reaches this shape, 1 once it passed it, in between while drawing it. */
internal fun DayArtShape.drawnFraction(progress: Float): Float {
    val span = sweepAngleDeg / 360.0
    return ((progress - timePosition) / span).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Day art (GLOSSARY: 하루 그림) drawn from [data] on [panel]; an advanced theme draws it its own
 * way ([ThemePanel.art]). The grass thumbnail and the big day art use this same drawing.
 * [drawInProgress] (0 to 1, drive time) is read only while drawing, so animating it redraws without
 * recomposing. When [animateLoop], an advanced theme's repeating effect plays.
 */
@Composable
internal fun DayArt(
    data: DayArtData,
    panel: ThemePanel,
    modifier: Modifier = Modifier,
    animateLoop: Boolean = false,
    drawInProgress: () -> Float = { 1f },
) {
    val patternColor = Color(panel.textArgb)
    val art = panel.art
    val loopSeconds = rememberLoopSeconds(animateLoop && art != null)
    Canvas(modifier) {
        val progress = drawInProgress()
        if (art != null) {
            with(art) { drawDayArt(data, panel.dark, progress, loopSeconds.floatValue) }
            return@Canvas
        }
        data.shapes.forEach { shape ->
            val drawn = shape.drawnFraction(progress)
            if (drawn < 1f) drawShape(shape, patternColor, sweepFraction = 1f, alpha = DRAW_IN_GHOST_ALPHA)
            if (drawn > 0f) drawShape(shape, patternColor, sweepFraction = drawn, alpha = 1f)
        }
    }
}

/**
 * [DayArt] standing on its own on a glass screen. A plain theme's colours are made for its panel,
 * so its art sits on a disc of the panel colour; an advanced theme's art draws straight on the scene.
 */
@Composable
internal fun DayArtDisc(
    data: DayArtData,
    panel: ThemePanel,
    modifier: Modifier = Modifier,
    animateLoop: Boolean = false,
    drawInProgress: () -> Float = { 1f },
) {
    val ground = if (panel.art == null) Modifier.clip(CircleShape).background(Color(panel.argb)) else Modifier
    Box(modifier.then(ground)) { DayArt(data, panel, Modifier.fillMaxSize(), animateLoop, drawInProgress) }
}

private fun DrawScope.drawShape(
    shape: DayArtShape,
    patternColor: Color,
    sweepFraction: Float,
    alpha: Float,
) {
    val startAngle = shape.startAngleDeg.toFloat() - 90f // DrawScope's 0 degrees is 3 o'clock.
    val sweep = shape.sweepAngleDeg.toFloat() * sweepFraction
    val inner = shape.innerRadius.toFloat()
    val outer = shape.outerRadius.toFloat()
    ring(Color(shape.colorArgb), startAngle, sweep, (inner + outer) / 2, outer - inner, alpha)
    if (DayArtPattern.LOW_BEAM in shape.patterns) {
        ring(patternColor, startAngle, sweep, LOW_BEAM_RING_RADIUS, LOW_BEAM_RING_WIDTH, alpha)
    }
    if (DayArtPattern.TURN_SIGNAL in shape.patterns) {
        ring(patternColor, startAngle, sweep, TURN_SIGNAL_RING_RADIUS, TURN_SIGNAL_RING_WIDTH, alpha)
    }
    if (DayArtPattern.WIPER in shape.patterns) {
        val dash = size.minDimension * WIPER_RING_WIDTH
        ring(patternColor, startAngle, sweep, WIPER_RING_RADIUS, WIPER_RING_WIDTH, alpha, PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
    }
}

/** Arc band centred on [radius] with [width], both fractions of the drawing radius. */
private fun DrawScope.ring(
    color: Color,
    startAngle: Float,
    sweep: Float,
    radius: Float,
    width: Float,
    alpha: Float,
    pathEffect: PathEffect? = null,
) {
    val drawingRadius = size.minDimension / 2 * DRAWING_RADIUS_SCALE
    val r = radius * drawingRadius
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = center - Offset(r, r),
        size = Size(r * 2, r * 2),
        alpha = alpha,
        style = Stroke(width = width * drawingRadius, pathEffect = pathEffect),
    )
}
