package io.github.kimyeongbean.iter.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Empty space round the route, as a share of the box side. */
private const val ROUTE_MARGIN = 0.08f

/** Line and turn signal band widths, as shares of the box side. */
private const val LINE_WIDTH = 0.012f
private const val TURN_SIGNAL_BAND_WIDTH = 0.05f
private const val TURN_SIGNAL_BAND_ALPHA = 0.25f

/**
 * Map graph (GLOSSARY: 맵 그래프): [route] as a speed-coloured line on [background], no map.
 * Stretches with the turn signal on get a band of another colour behind the line.
 * [drawInProgress] (0 to 1, drive time) is read only while drawing, so animating it redraws
 * without recomposing.
 */
@Composable
internal fun MapGraph(
    route: MapRoute,
    background: MapBackground,
    modifier: Modifier = Modifier,
    drawInProgress: () -> Float = { 1f },
) {
    val backgroundColor = Color(background.argb)
    // Opaque colours: round caps of see-through segments would otherwise overlap into beads.
    val bandColor = Color(background.textArgb).copy(alpha = TURN_SIGNAL_BAND_ALPHA).compositeOver(backgroundColor)
    Canvas(modifier) {
        val progress = drawInProgress()
        val side = size.minDimension * (1 - 2 * ROUTE_MARGIN)
        val origin = Offset((size.width - side) / 2, (size.height - side) / 2)
        val at = { p: RoutePoint -> origin + Offset(p.x * side, p.y * side) }
        val band = size.minDimension * TURN_SIGNAL_BAND_WIDTH
        val line = size.minDimension * LINE_WIDTH

        // Bands first, so the line stays on top.
        route.strokes.forEach { stroke ->
            stroke.zipWithNext().forEach { (a, b) ->
                if (a.turnSignalOn && b.turnSignalOn) segment(a, b, progress, at, bandColor, band)
            }
        }
        route.strokes.forEach { stroke ->
            if (stroke.size == 1) {
                val p = stroke.single()
                if (progress >= p.timePosition) drawCircle(Color(p.colorArgb).compositeOver(backgroundColor), line / 2, at(p))
            }
            stroke.zipWithNext().forEach { (a, b) ->
                segment(a, b, progress, at, Color(b.colorArgb).compositeOver(backgroundColor), line)
            }
        }
    }
}

private fun DrawScope.segment(
    from: RoutePoint,
    to: RoutePoint,
    progress: Float,
    at: (RoutePoint) -> Offset,
    color: Color,
    width: Float,
) {
    val drawn = segmentDrawnFraction(from, to, progress)
    if (drawn <= 0f) return
    val start = at(from)
    drawLine(color, start, lerp(start, at(to), drawn), strokeWidth = width, cap = StrokeCap.Round)
}
