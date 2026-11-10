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

/** Low beam band: a wide night band, as a share of the box side, behind the other bands. */
private const val LOW_BEAM_SHADE_WIDTH = 0.09f

/** Rain effect: one slow fall per cycle (Notion 디자인 '애니메이션·전환 기준': 천천히 반복). */
private const val RAIN_FALL_SECONDS = 1.6f

/** Rain streaks, as shares of the box side: fall window height, streak length and width. */
private const val RAIN_FALL_HEIGHT = 0.07f
private const val RAIN_STREAK_LENGTH = 0.022f
private const val RAIN_STREAK_WIDTH = 0.004f

/** Rain streaks lean a little, like rain in the wind: sideways shift per unit of fall. */
private const val RAIN_SLANT = 0.25f

/**
 * Map graph (GLOSSARY: 맵 그래프): [route] on [panel], no map. An advanced theme draws it its own
 * way ([ThemePanel.art]). Otherwise it is a speed-coloured line: stretches with the turn signal on
 * get a band of another colour behind the line, stretches with the low beam on a wider night band
 * ([ThemePanel.effects]), and stretches with the wiper on falling rain. Missing signals (null) get
 * no effect.
 * [drawInProgress] (0 to 1, drive time) is read only while drawing, so animating it redraws
 * without recomposing.
 * When [animateLoop], the repeating effects (the rain, an advanced theme's own) play over and over
 * while the map graph is on screen; see [rememberLoopSeconds] for when they stop. Otherwise (system
 * animations off) they show still.
 */
@Composable
internal fun MapGraph(
    route: MapRoute,
    panel: ThemePanel,
    modifier: Modifier = Modifier,
    animateLoop: Boolean = false,
    drawInProgress: () -> Float = { 1f },
) {
    val art = panel.art
    // The plain drawing has nothing to repeat without rain.
    val loopSeconds = rememberLoopSeconds(animateLoop && (art != null || route.rainDrops.isNotEmpty()))
    val backgroundColor = Color(panel.argb)
    // Opaque colours: round caps of see-through segments would otherwise overlap into beads.
    val effectColors = panel.effects
    val bandColor = Color(effectColors.turnSignalBandArgb)
    val shadeColor = Color(effectColors.lowBeamArgb)
    val rainColor = Color(effectColors.rainArgb)
    Canvas(modifier) {
        val progress = drawInProgress()
        if (art != null) {
            with(art) { drawMapGraph(route, panel.dark, progress, loopSeconds.floatValue) }
            return@Canvas
        }
        val frame = mapFrame()
        val shade = size.minDimension * LOW_BEAM_SHADE_WIDTH
        val band = size.minDimension * TURN_SIGNAL_BAND_WIDTH
        val line = size.minDimension * LINE_WIDTH

        // Bands first, so the line stays on top. A segment touching a signal sample gets one, so
        // a single-sample signal still shows. The wide low beam shade goes under the turn band.
        route.forEachSegmentTouching({ it.lowBeamOn }) { a, b -> drawSegment(a, b, progress, frame, shadeColor, shade) }
        route.forEachSegmentTouching({ it.turnSignalOn }) { a, b -> drawSegment(a, b, progress, frame, bandColor, band) }
        // Rain under the line, so the line stays readable.
        drawRain(route, frame, progress, loopSeconds.floatValue, rainColor)
        route.strokes.forEach { stroke ->
            if (stroke.size == 1) {
                val p = stroke.single()
                if (progress >= p.timePosition) drawCircle(Color(p.colorArgb).compositeOver(backgroundColor), line / 2, frame.at(p))
            }
            stroke.zipWithNext().forEach { (a, b) ->
                drawSegment(a, b, progress, frame, Color(b.colorArgb).compositeOver(backgroundColor), line)
            }
        }
    }
}

/** Where the route's unit square sits in the draw scope: a centred square with a margin. */
internal class MapFrame(
    private val origin: Offset,
    val side: Float,
) {
    fun at(
        x: Float,
        y: Float,
    ): Offset = origin + Offset(x * side, y * side)

    fun at(point: RoutePoint): Offset = at(point.x, point.y)
}

internal fun DrawScope.mapFrame(): MapFrame {
    val side = size.minDimension * (1 - 2 * ROUTE_MARGIN)
    return MapFrame(Offset((size.width - side) / 2, (size.height - side) / 2), side)
}

/**
 * The rain effect (GLOSSARY: 비 효과) of [route] in [color]: slanted streaks falling over and over
 * as [loopSeconds] advances. Drops show once the draw-in at [progress] passed them.
 */
internal fun DrawScope.drawRain(
    route: MapRoute,
    frame: MapFrame,
    progress: Float,
    loopSeconds: Float,
    color: Color,
) {
    val phase = loopSeconds % RAIN_FALL_SECONDS / RAIN_FALL_SECONDS
    val fallHeight = size.minDimension * RAIN_FALL_HEIGHT
    val streak = Offset(-RAIN_SLANT, 1f) * (size.minDimension * RAIN_STREAK_LENGTH)
    val streakWidth = size.minDimension * RAIN_STREAK_WIDTH
    route.rainDrops.forEach { drop ->
        if (progress < drop.timePosition) return@forEach
        val fall = rainFall(drop, phase)
        val top = frame.at(drop.x, drop.y) + Offset(0f, (fall - 0.5f) * fallHeight)
        drawLine(color.copy(alpha = RAIN_ALPHA * rainAlpha(fall)), top, top + streak, streakWidth, StrokeCap.Round)
    }
}

/** Calls [action] for each segment with at least one end where [signalOn]. */
internal inline fun MapRoute.forEachSegmentTouching(
    signalOn: (RoutePoint) -> Boolean,
    action: (RoutePoint, RoutePoint) -> Unit,
) {
    strokes.forEach { stroke ->
        stroke.zipWithNext().forEach { (a, b) -> if (signalOn(a) || signalOn(b)) action(a, b) }
    }
}

/** The part of the segment [from] → [to] the draw-in at [progress] has drawn, as a line. */
internal fun DrawScope.drawSegment(
    from: RoutePoint,
    to: RoutePoint,
    progress: Float,
    frame: MapFrame,
    color: Color,
    width: Float,
) {
    val drawn = segmentDrawnFraction(from, to, progress)
    if (drawn <= 0f) return
    val start = frame.at(from)
    drawLine(color, start, lerp(start, frame.at(to), drawn), strokeWidth = width, cap = StrokeCap.Round)
}
