package io.github.kimyeongbean.iter.app

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.kimyeongbean.iter.art.DayArtData
import io.github.kimyeongbean.iter.art.DayArtPattern
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Day art: radius of the brushed circle, as a share of the drawing radius. */
private const val CIRCLE_RADIUS = 0.6f

/** Brush width as a share of the drawing radius (day art) or the box side (map graph): slow is wide. */
private const val CIRCLE_WIDTH_SLOW = 0.2f
private const val CIRCLE_WIDTH_FAST = 0.07f
private const val LINE_WIDTH_SLOW = 0.022f
private const val LINE_WIDTH_FAST = 0.008f

/** The brush follows the mean speed of this many shapes on each side, so width and ink change smoothly. */
private const val WIDTH_SMOOTHING = 4

/** The seal is stamped over this last share of the draw-in. */
private const val STAMP_SPAN = 0.06f

/** Paper fibres scattered over the map graph panel. */
private const val PANEL_FIBRES = 70

/** One backdrop paper fibre per square of this side, in dp. */
private const val BACKDROP_CELL_DP = 70f

/** The repeating effect: how deep the wash is (0 to 1) and how wet the ink, [loopSeconds] in. */
private fun washDepth(loopSeconds: Float): Float = 0.75f + 0.25f * sin(loopSeconds * 0.8f)

/** Wet ink spreads a little and draws back: the brush width, as a share of its own, [loopSeconds] in. */
private fun inkSpread(loopSeconds: Float): Float = 1f + 0.05f * sin(loopSeconds * 0.8f)

/**
 * Ink wash (수묵): the day is a circle brushed in one go, the route a brush line, on mulberry
 * paper. The faster, the darker and thinner the ink; slow ink pools wide and pale. Turn signal: an
 * ink dot. Wiper: spattered ink. Low beam: a pale wash. A vermilion seal is stamped at the end.
 * Repeating effect: the ink slowly spreads and draws back, and the wash deepens and fades.
 */
internal object InkWashArt : ThemeArt {
    override val lightColors =
        IterColors(
            backgroundArgb = 0xFFEEE7D6.toInt(),
            cardArgb = 0xFFF7F2E6.toInt(),
            cardHighArgb = 0xFFE3DAC6.toInt(),
            controlArgb = 0xFFD4CAB3.toInt(),
            textArgb = 0xFF16130F.toInt(),
            textSecondaryArgb = 0xFF4F4838.toInt(),
            outlineArgb = 0xFF94896F.toInt(),
            primaryArgb = 0xFF16130F.toInt(),
            onPrimaryArgb = 0xFFF7F2E6.toInt(),
        )

    override val darkColors =
        IterColors(
            backgroundArgb = 0xFF12110F.toInt(),
            cardArgb = 0xFF1C1A17.toInt(),
            cardHighArgb = 0xFF2A2723.toInt(),
            controlArgb = 0xFF3A3630.toInt(),
            textArgb = 0xFFF1ECE0.toInt(),
            textSecondaryArgb = 0xFFC4BCAA.toInt(),
            outlineArgb = 0xFF7A7364.toInt(),
            primaryArgb = 0xFFF1ECE0.toInt(),
            onPrimaryArgb = 0xFF12110F.toInt(),
        )

    private class Palette(
        val colors: IterColors,
        /** Ink at standstill (thinned) and at full speed. On dark paper the ink is light. */
        val inkPaleArgb: Int,
        val inkArgb: Int,
        val washArgb: Int,
        /** Orange-leaning, not red: red is the Pleos warning colour. */
        val sealArgb: Int,
        val backdropFibreArgbs: List<Int>,
    )

    private val light =
        Palette(
            colors = lightColors,
            inkPaleArgb = 0xFFB4AC9C.toInt(),
            inkArgb = 0xFF16130F.toInt(),
            washArgb = 0xFFC9BFAA.toInt(),
            sealArgb = 0xFFC2410C.toInt(),
            backdropFibreArgbs = listOf(0xFFE4DCC9.toInt(), 0xFFDFD6C2.toInt()),
        )

    private val dark =
        Palette(
            colors = darkColors,
            inkPaleArgb = 0xFF5C574E.toInt(),
            inkArgb = 0xFFF1ECE0.toInt(),
            washArgb = 0xFF423E37.toInt(),
            sealArgb = 0xFFF0763A.toInt(),
            backdropFibreArgbs = listOf(0xFF1B1916.toInt(), 0xFF1F1C19.toInt()),
        )

    private fun palette(dark: Boolean) = if (dark) this.dark else light

    override fun panel(dark: Boolean): ThemePanel {
        val p = palette(dark)
        return ThemePanel(
            argb = p.colors.cardArgb,
            textArgb = p.colors.textSecondaryArgb,
            speedColorBaseArgb = p.inkArgb,
            effects = MapEffectColors(turnSignalBandArgb = p.inkArgb, lowBeamArgb = p.washArgb, rainArgb = p.inkArgb),
            art = this,
            dark = dark,
        )
    }

    override fun backdropArgbs(dark: Boolean): List<Int> = palette(dark).backdropFibreArgbs

    /** Short paper fibres lying every way. */
    override fun DrawScope.drawBackdrop(dark: Boolean) {
        val fibres = palette(dark).backdropFibreArgbs.map { Color(it) }
        forEachBackdropSpot(BACKDROP_CELL_DP) { i, at ->
            drawFibre(i, at, (8f + 16f * scatter(i, 4)) * density, 1.4f * density, fibres[i % fibres.size])
        }
    }

    override fun DrawScope.drawDayArt(
        data: DayArtData,
        dark: Boolean,
        progress: Float,
        loopSeconds: Float,
    ) {
        val shapes = data.shapes
        if (shapes.isEmpty()) return
        val p = palette(dark)
        val radius = size.minDimension / 2 * DRAWING_RADIUS_SCALE
        val thumbnail = isThumbnail
        val r = radius * CIRCLE_RADIUS
        val topLeft = center - Offset(r, r)
        val arcSize = Size(r * 2, r * 2)
        // Opaque, so the strokes that overlap do not show as darker seams.
        val wash = mixArgb(p.colors.cardArgb, p.washArgb, washDepth(loopSeconds))
        val spread = inkSpread(loopSeconds)
        val intensities = smoothedIntensities[data]

        // The wash first, under the ink: wider than any stroke, and breathing.
        shapes.forEach { shape ->
            if (DayArtPattern.LOW_BEAM !in shape.patterns || shape.drawnFraction(progress) <= 0f) return@forEach
            drawArc(
                color = wash,
                startAngle = shape.startAngleDeg.toFloat() - 90f,
                sweepAngle = shape.sweepAngleDeg.toFloat() + 0.6f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(radius * 0.36f),
            )
        }
        shapes.forEachIndexed { i, shape ->
            val intensity = intensities[i]
            val width = radius * (CIRCLE_WIDTH_SLOW + (CIRCLE_WIDTH_FAST - CIRCLE_WIDTH_SLOW) * intensity) * spread
            val color = mixArgb(p.inkPaleArgb, p.inkArgb, intensity)
            val start = shape.startAngleDeg.toFloat() - 90f
            // A little overlap hides the seams between strokes.
            val sweep = shape.sweepAngleDeg.toFloat() + 0.6f
            val brushed = shape.drawnFraction(progress)
            if (brushed < 1f) drawArc(color, start, sweep, false, topLeft, arcSize, DRAW_IN_GHOST_ALPHA, Stroke(width))
            if (brushed > 0f) drawArc(color, start, sweep * brushed, false, topLeft, arcSize, style = Stroke(width))
            if (thumbnail || brushed < 1f) return@forEachIndexed
            val angleRad = Math.toRadians(shape.startAngleDeg + shape.sweepAngleDeg / 2 - 90).toFloat()
            val direction = Offset(cos(angleRad), sin(angleRad))
            if (DayArtPattern.TURN_SIGNAL in shape.patterns) {
                drawCircle(Color(p.inkArgb), radius * 0.016f, center + direction * (radius * 0.9f))
            }
            if (DayArtPattern.WIPER in shape.patterns && i % 2 == 0) {
                val at = center + direction * (radius * (0.93f + 0.07f * scatter(i, 5)))
                drawCircle(Color(p.inkArgb), radius * (0.006f + 0.012f * scatter(i, 6)), at, alpha = 0.7f)
            }
        }
        drawSeal(center + Offset(radius * 0.74f, radius * 0.8f), radius * if (thumbnail) 0.22f else 0.16f, Color(p.sealArgb), progress)
    }

    override fun DrawScope.drawMapGraph(
        route: MapRoute,
        dark: Boolean,
        progress: Float,
        loopSeconds: Float,
    ) {
        val p = palette(dark)
        val frame = mapFrame()
        val side = size.minDimension
        val ink = Color(p.inkArgb)

        // Paper fibres in the panel's own soft shade, under everything.
        val fibre = Color(p.colors.cardHighArgb)
        repeat(PANEL_FIBRES) { i ->
            val at = Offset(scatter(i, 21) * size.width, scatter(i, 22) * size.height)
            drawFibre(i, at, side * (0.012f + 0.02f * scatter(i, 24)), side * 0.0018f, fibre)
        }

        // The wash under stretches with the low beam on, slowly deepening and fading.
        // Opaque, so the round ends of the pieces do not show as beads.
        val wash = mixArgb(p.colors.cardArgb, p.washArgb, washDepth(loopSeconds))
        val spread = inkSpread(loopSeconds)
        route.forEachSegmentTouching({ it.lowBeamOn }) { a, b -> drawSegment(a, b, progress, frame, wash, side * 0.075f) }

        // Spattered ink where the wiper was on: two specks by each drop anchor, each slowly soaking in
        // and showing again on its own beat.
        route.rainDrops.forEachIndexed { i, drop ->
            if (progress < drop.timePosition) return@forEachIndexed
            repeat(2) { j ->
                val offset = Offset(scatter(i, 7 + j) - 0.5f, scatter(i, 9 + j) - 0.5f) * (side * 0.07f)
                drawCircle(ink, side * (0.0015f + 0.004f * scatter(i, 11 + j)), frame.at(drop.x, drop.y) + offset, alpha = 0.45f + 0.25f * sin(loopSeconds * 0.8f + i + j))
            }
        }

        route.strokes.forEach { stroke ->
            // A stretch of one sample has no line to draw: it is a dab of ink.
            if (stroke.size == 1 && progress >= stroke[0].timePosition) {
                val intensity = stroke[0].speedIntensity
                drawCircle(mixArgb(p.inkPaleArgb, p.inkArgb, intensity), side * lineWidth(intensity) * spread / 2, frame.at(stroke[0]))
            }
            for (i in 1..stroke.lastIndex) {
                val intensity = stroke[i].speedIntensity
                drawSegment(stroke[i - 1], stroke[i], progress, frame, mixArgb(p.inkPaleArgb, p.inkArgb, intensity), side * lineWidth(intensity) * spread)
            }
            // An ink dot beside the line where the turn signal comes on.
            stroke.forEachTurnSignalStart { i, point ->
                if (progress < point.timePosition) return@forEachTurnSignalStart
                val at = frame.at(point)
                val next = frame.at(stroke[minOf(i + 1, stroke.lastIndex)])
                val besideRad = atan2(next.y - at.y, next.x - at.x) + (PI / 2).toFloat()
                drawCircle(ink, side * 0.008f, at + Offset(cos(besideRad), sin(besideRad)) * (side * 0.03f))
            }
        }

        // Where the brush first touched the paper, and the seal by the end.
        val first = route.strokes.firstOrNull()?.firstOrNull() ?: return
        val last = route.strokes.lastOrNull()?.lastOrNull() ?: return
        drawCircle(ink, side * 0.016f, frame.at(first))
        drawSeal(frame.at(last) + Offset(side * 0.035f, side * 0.03f), side * 0.045f, Color(p.sealArgb), progress)
    }

    override fun DrawScope.drawLegendSample(
        signal: LegendSignal,
        dark: Boolean,
    ) {
        val p = palette(dark)
        val ink = Color(p.inkArgb)
        when (signal) {
            LegendSignal.TURN_SIGNAL -> drawCircle(ink, size.minDimension * 0.28f)

            LegendSignal.WIPER ->
                listOf(Offset(0.2f, 0.65f) to 0.1f, Offset(0.5f, 0.3f) to 0.16f, Offset(0.8f, 0.7f) to 0.07f).forEach { (at, r) ->
                    drawCircle(ink, size.height * r, Offset(size.width * at.x, size.height * at.y), alpha = 0.7f)
                }

            LegendSignal.LOW_BEAM ->
                drawRoundRect(
                    Color(p.washArgb),
                    topLeft = Offset(0f, size.height * 0.2f),
                    size = Size(size.width, size.height * 0.6f),
                    cornerRadius = CornerRadius(size.height * 0.3f),
                )
        }
    }

    /** The brush line's width for [intensity], as a share of the map graph's box side. */
    private fun lineWidth(intensity: Float): Float = LINE_WIDTH_SLOW + (LINE_WIDTH_FAST - LINE_WIDTH_SLOW) * intensity

    /**
     * For each shape, the mean speed intensity of the shapes within [WIDTH_SMOOTHING] of it, found
     * once per day art. The day's first and last shapes are not neighbours: midnight does not wrap.
     */
    private val smoothedIntensities =
        LastComputed<DayArtData, FloatArray> { data ->
            val shapes = data.shapes
            FloatArray(shapes.size) { i ->
                val from = (i - WIDTH_SMOOTHING).coerceAtLeast(0)
                val to = (i + WIDTH_SMOOTHING).coerceAtMost(shapes.lastIndex)
                var sum = 0f
                for (n in from..to) sum += shapes[n].speedIntensity
                sum / (to - from + 1)
            }
        }

    /** The [index]th paper fibre: a short line from [at], at a fixed angle. */
    private fun DrawScope.drawFibre(
        index: Int,
        at: Offset,
        length: Float,
        width: Float,
        color: Color,
    ) {
        val angleRad = scatter(index, 3) * PI.toFloat()
        drawLine(color, at, at + Offset(cos(angleRad), sin(angleRad)) * length, width, StrokeCap.Round)
    }

    /**
     * The seal, a vermilion square of [side] centred on [at]. It is stamped over the last [STAMP_SPAN] of
     * the draw-in: it comes down from a little larger and see-through.
     */
    private fun DrawScope.drawSeal(
        at: Offset,
        side: Float,
        color: Color,
        progress: Float,
    ) {
        val stamped = grownFraction(progress, 1 - STAMP_SPAN, STAMP_SPAN)
        if (stamped <= 0f) return
        val shown = side * (1.5f - 0.5f * stamped)
        drawRoundRect(
            color,
            topLeft = at - Offset(shown / 2, shown / 2),
            size = Size(shown, shown),
            cornerRadius = CornerRadius(shown * 0.14f),
            alpha = stamped,
        )
    }
}
