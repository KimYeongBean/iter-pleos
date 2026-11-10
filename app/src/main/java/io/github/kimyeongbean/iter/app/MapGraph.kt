package io.github.kimyeongbean.iter.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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

/** Low beam band: a wide night band, as a share of the box side, behind the other bands. */
private const val LOW_BEAM_SHADE_WIDTH = 0.09f

/** Rain effect: one slow fall per cycle (Notion 디자인 '애니메이션·전환 기준': 천천히 반복). */
private const val RAIN_FALL_MS = 1_600L

/** Rain streaks, as shares of the box side: fall window height, streak length and width. */
private const val RAIN_FALL_HEIGHT = 0.07f
private const val RAIN_STREAK_LENGTH = 0.022f
private const val RAIN_STREAK_WIDTH = 0.004f

/** Rain streaks lean a little, like rain in the wind: sideways shift per unit of fall. */
private const val RAIN_SLANT = 0.25f

/**
 * Map graph (GLOSSARY: 맵 그래프): [route] as a speed-coloured line on [background], no map.
 * Stretches with the turn signal on get a band of another colour behind the line, stretches with
 * the low beam on a wider night band ([mapEffectColors]), and stretches with the wiper on falling rain. Missing
 * signals (null) get no effect.
 * [drawInProgress] (0 to 1, drive time) is read only while drawing, so animating it redraws
 * without recomposing.
 * When [animateRain], the rain falls slowly over and over while the map graph is on screen; it
 * stops when the map graph leaves the composition (another screen, or "기록 중" on gear D) and
 * while the app is in the background (Compose pauses its frame clock when the window stops).
 * Otherwise (system animations off) the rain shows still.
 */
@Composable
internal fun MapGraph(
    route: MapRoute,
    background: MapBackground,
    modifier: Modifier = Modifier,
    animateRain: Boolean = false,
    drawInProgress: () -> Float = { 1f },
) {
    val rainPhase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animateRain, route.rainDrops.isNotEmpty()) {
        if (!animateRain || route.rainDrops.isEmpty()) return@LaunchedEffect
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nanos ->
                rainPhase.floatValue = (nanos - startNanos) / 1_000_000 % RAIN_FALL_MS / RAIN_FALL_MS.toFloat()
            }
        }
    }
    val backgroundColor = Color(background.argb)
    // Opaque colours: round caps of see-through segments would otherwise overlap into beads.
    val effectColors = background.mapEffectColors
    val bandColor = Color(effectColors.turnSignalBandArgb)
    val shadeColor = Color(effectColors.lowBeamArgb)
    val rainColor = Color(effectColors.rainArgb)
    Canvas(modifier) {
        val progress = drawInProgress()
        val side = size.minDimension * (1 - 2 * ROUTE_MARGIN)
        val origin = Offset((size.width - side) / 2, (size.height - side) / 2)
        val toCanvasXY = { x: Float, y: Float -> origin + Offset(x * side, y * side) }
        val toCanvas = { p: RoutePoint -> toCanvasXY(p.x, p.y) }
        val shade = size.minDimension * LOW_BEAM_SHADE_WIDTH
        val band = size.minDimension * TURN_SIGNAL_BAND_WIDTH
        val line = size.minDimension * LINE_WIDTH

        // Bands first, so the line stays on top. A segment touching a signal sample gets one, so
        // a single-sample signal still shows. The wide low beam shade goes under the turn band.
        route.forEachSegmentTouching({ it.lowBeamOn }) { a, b -> drawSegment(a, b, progress, toCanvas, shadeColor, shade) }
        route.forEachSegmentTouching({ it.turnSignalOn }) { a, b -> drawSegment(a, b, progress, toCanvas, bandColor, band) }
        // Rain under the line, so the line stays readable; drops show once the draw-in passed them.
        val phase = rainPhase.floatValue
        val fallHeight = size.minDimension * RAIN_FALL_HEIGHT
        val streak = Offset(-RAIN_SLANT, 1f) * (size.minDimension * RAIN_STREAK_LENGTH)
        val streakWidth = size.minDimension * RAIN_STREAK_WIDTH
        route.rainDrops.forEach { drop ->
            if (progress < drop.timePosition) return@forEach
            val fall = rainFall(drop, phase)
            val top = toCanvasXY(drop.x, drop.y) + Offset(0f, (fall - 0.5f) * fallHeight)
            drawLine(rainColor.copy(alpha = rainAlpha(fall)), top, top + streak, streakWidth, StrokeCap.Round)
        }
        route.strokes.forEach { stroke ->
            if (stroke.size == 1) {
                val p = stroke.single()
                if (progress >= p.timePosition) drawCircle(Color(p.colorArgb).compositeOver(backgroundColor), line / 2, toCanvas(p))
            }
            stroke.zipWithNext().forEach { (a, b) ->
                drawSegment(a, b, progress, toCanvas, Color(b.colorArgb).compositeOver(backgroundColor), line)
            }
        }
    }
}

/** Calls [action] for each segment with at least one end where [signalOn]. */
private inline fun MapRoute.forEachSegmentTouching(
    signalOn: (RoutePoint) -> Boolean,
    action: (RoutePoint, RoutePoint) -> Unit,
) {
    strokes.forEach { stroke ->
        stroke.zipWithNext().forEach { (a, b) -> if (signalOn(a) || signalOn(b)) action(a, b) }
    }
}

private fun DrawScope.drawSegment(
    from: RoutePoint,
    to: RoutePoint,
    progress: Float,
    toCanvas: (RoutePoint) -> Offset,
    color: Color,
    width: Float,
) {
    val drawn = segmentDrawnFraction(from, to, progress)
    if (drawn <= 0f) return
    val start = toCanvas(from)
    drawLine(color, start, lerp(start, toCanvas(to), drawn), strokeWidth = width, cap = StrokeCap.Round)
}
