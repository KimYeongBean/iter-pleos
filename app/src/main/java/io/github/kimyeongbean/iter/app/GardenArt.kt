package io.github.kimyeongbean.iter.app

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import io.github.kimyeongbean.iter.art.DayArtData
import io.github.kimyeongbean.iter.art.DayArtPattern
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Day art radii, as shares of the drawing radius: where petals start, and the flower's centre. */
private const val PETAL_INNER_RADIUS = 0.13f
private const val CENTRE_RADIUS = 0.12f

/** A petal unfolds over this share of the draw-in after the draw-in reaches it. */
private const val UNFOLD_SPAN = 0.12f

/** Petals and leaves not yet drawn show this faintly, so a growing grass cell never goes blank. */
private const val GHOST_ALPHA = 0.15f

/** Distance along the stem between leaves, in route unit square lengths; sides alternate. */
private const val LEAF_SPACING = 0.035f

/** Backdrop marks per this many px² of screen, at 1x density. */
private const val BACKDROP_CELL_PX = 120f

/**
 * Garden (정원): the day is a flower with thin petals, the route a stem with leaves, on a flower
 * bed. Speed is petal length and colour depth, and leaf size. Turn signal: a bud. Wiper: rain.
 * Low beam: a stem of another shade. Repeating effect: petals and leaves sway slowly.
 */
internal object GardenArt : ThemeArt {
    override val lightColors =
        IterColors(
            backgroundArgb = 0xFFEEF0DF.toInt(),
            cardArgb = 0xFFFBFBF3.toInt(),
            cardHighArgb = 0xFFE4E8D2.toInt(),
            controlArgb = 0xFFD5DCC0.toInt(),
            textArgb = 0xFF1E2A1A.toInt(),
            textSecondaryArgb = 0xFF4A5741.toInt(),
            outlineArgb = 0xFF8C9A7C.toInt(),
            primaryArgb = 0xFF2F5229.toInt(),
            onPrimaryArgb = 0xFFFFFFFF.toInt(),
        )

    override val darkColors =
        IterColors(
            backgroundArgb = 0xFF10160F.toInt(),
            cardArgb = 0xFF1A2318.toInt(),
            cardHighArgb = 0xFF263322.toInt(),
            controlArgb = 0xFF36462F.toInt(),
            textArgb = 0xFFEEF3E6.toInt(),
            textSecondaryArgb = 0xFFBFCBB3.toInt(),
            outlineArgb = 0xFF6B7A5F.toInt(),
            primaryArgb = 0xFFEEF3E6.toInt(),
            onPrimaryArgb = 0xFF10160F.toInt(),
        )

    private class Palette(
        val colors: IterColors,
        val stemArgb: Int,
        val stemLowBeamArgb: Int,
        val leafSlowArgb: Int,
        val leafFastArgb: Int,
        val petalSlowArgb: Int,
        val petalFastArgb: Int,
        val centreArgb: Int,
        val budArgb: Int,
        val rainArgb: Int,
        val backdropTuftArgb: Int,
        val backdropSoilArgb: Int,
    )

    private val light =
        Palette(
            colors = lightColors,
            stemArgb = 0xFF4C7F3F.toInt(),
            stemLowBeamArgb = 0xFF274A2B.toInt(),
            leafSlowArgb = 0xFFA9CC84.toInt(),
            leafFastArgb = 0xFF3F7D3A.toInt(),
            petalSlowArgb = 0xFFF4B9CB.toInt(),
            petalFastArgb = 0xFFC2255C.toInt(),
            centreArgb = 0xFFE0A21B.toInt(),
            budArgb = 0xFFE58BA8.toInt(),
            rainArgb = 0xFF3F7FB5.toInt(),
            backdropTuftArgb = 0xFFDDE3C8.toInt(),
            backdropSoilArgb = 0xFFE3DFCB.toInt(),
        )

    // On the dark ground a darker stem cannot stand out, so the low beam stem is a paler one.
    private val dark =
        Palette(
            colors = darkColors,
            stemArgb = 0xFF7FB56B.toInt(),
            stemLowBeamArgb = 0xFFD7EDC9.toInt(),
            leafSlowArgb = 0xFF3E5A34.toInt(),
            leafFastArgb = 0xFF9AD07E.toInt(),
            petalSlowArgb = 0xFF6B3A4A.toInt(),
            petalFastArgb = 0xFFFF8FB3.toInt(),
            centreArgb = 0xFFF2C24B.toInt(),
            budArgb = 0xFFE58BA8.toInt(),
            rainArgb = 0xFF8FC3EA.toInt(),
            backdropTuftArgb = 0xFF1A2417.toInt(),
            backdropSoilArgb = 0xFF1C2219.toInt(),
        )

    private fun palette(dark: Boolean) = if (dark) this.dark else light

    override fun panel(dark: Boolean): ThemePanel {
        val p = palette(dark)
        return ThemePanel(
            argb = p.colors.cardArgb,
            textArgb = p.colors.textSecondaryArgb,
            speedColorBaseArgb = p.petalFastArgb,
            effects = MapEffectColors(turnSignalBandArgb = p.budArgb, lowBeamArgb = p.stemLowBeamArgb, rainArgb = p.rainArgb),
            art = this,
            dark = dark,
        )
    }

    override fun backdropArgbs(dark: Boolean): List<Int> = palette(dark).let { listOf(it.backdropTuftArgb, it.backdropSoilArgb) }

    /** Grass tufts and soil specks, scattered evenly. */
    override fun DrawScope.drawBackdrop(dark: Boolean) {
        val p = palette(dark)
        val tuft = Color(p.backdropTuftArgb)
        val soil = Color(p.backdropSoilArgb)
        val cell = BACKDROP_CELL_PX * density
        val count = (size.width * size.height / (cell * cell)).toInt()
        repeat(count) { i ->
            val at = Offset(scatter(i, 1) * size.width, scatter(i, 2) * size.height)
            if (i % 3 == 0) {
                drawCircle(soil, radius = (2f + 3f * scatter(i, 3)) * density, center = at)
            } else {
                drawTuft(at, height = (8f + 6f * scatter(i, 3)) * density, color = tuft)
            }
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
        val radius = size.minDimension / 2 * 0.92f
        val thumbnail = size.minDimension < THUMBNAIL_MAX_PX
        // A petal per two shapes; a thumbnail keeps about fourteen so they stay apart.
        val perPetal = if (thumbnail) (shapes.size / 14).coerceAtLeast(1) else 2
        val halfWidth = radius * if (thumbnail) 0.11f else 0.05f
        val inner = radius * PETAL_INNER_RADIUS
        val path = Path()
        // The whole flower leans a little to and fro; each petal flutters on its own beat.
        rotate(degrees = sin(loopSeconds * 0.9f) * 1.6f) {
            var from = 0
            while (from < shapes.size) {
                val to = minOf(from + perPetal, shapes.size)
                val group = shapes.subList(from, to)
                val first = group.first()
                val last = group.last()
                val intensity = group.sumOf { it.speedIntensity.toDouble() }.toFloat() / group.size
                val midDeg = (first.startAngleDeg + (last.startAngleDeg + last.sweepAngleDeg)) / 2 - 90
                val angle = Math.toRadians(midDeg).toFloat() + sin(loopSeconds * 1.3f + from * 0.7f) * 0.012f
                val outer = radius * (0.25f + 0.6f * intensity.coerceAtLeast(0.08f))
                val color = mixArgb(p.petalSlowArgb, p.petalFastArgb, intensity)
                val unfolded = ((progress - first.timePosition.toFloat()) / UNFOLD_SPAN).coerceIn(0f, 1f)
                if (unfolded < 1f) drawPetal(path, angle, inner, outer, halfWidth, color, GHOST_ALPHA)
                if (unfolded > 0f) drawPetal(path, angle, inner, inner + (outer - inner) * unfolded, halfWidth, color, 0.92f)
                if (!thumbnail && unfolded >= 1f) {
                    val direction = Offset(cos(angle), sin(angle))
                    val patterns = group.flatMapTo(mutableSetOf()) { it.patterns }
                    if (DayArtPattern.TURN_SIGNAL in patterns) {
                        drawCircle(Color(p.budArgb), radius * 0.018f, center + direction * (outer + radius * 0.04f))
                    }
                    if (DayArtPattern.WIPER in patterns) {
                        drawCircle(Color(p.rainArgb), radius * 0.014f, center + direction * (radius * 0.97f))
                    }
                    if (DayArtPattern.LOW_BEAM in patterns) {
                        val r = radius * 0.19f
                        drawArc(
                            color = Color(p.stemLowBeamArgb),
                            startAngle = midDeg.toFloat() - 3.5f,
                            sweepAngle = 7f,
                            useCenter = false,
                            topLeft = center - Offset(r, r),
                            size = Size(r * 2, r * 2),
                            style = Stroke(radius * 0.04f),
                        )
                    }
                }
                from = to
            }
            drawCircle(Color(p.centreArgb), radius * CENTRE_RADIUS)
        }
    }

    override fun DrawScope.drawMapGraph(
        route: MapRoute,
        dark: Boolean,
        progress: Float,
        loopSeconds: Float,
    ) {
        val p = palette(dark)
        val frame = mapFrame()
        val stem = Color(p.stemArgb)
        val stemLowBeam = Color(p.stemLowBeamArgb)
        val stemWidth = size.minDimension * 0.011f
        val path = Path()

        // The bed: tufts in the panel's own soft shade, under everything.
        val tuft = Color(p.colors.cardHighArgb)
        repeat(48) { i -> drawTuft(Offset(scatter(i, 21) * size.width, scatter(i, 22) * size.height), size.minDimension * 0.016f, tuft) }

        drawRain(route, frame, progress, loopSeconds, Color(p.rainArgb))

        var leafIndex = 0
        route.strokes.forEach { stroke ->
            // Leaves first, so the stem runs over their feet: one every LEAF_SPACING along the stem.
            var sinceLeaf = 0f
            stroke.forEachIndexed { i, point ->
                if (i == 0 || i == stroke.lastIndex) return@forEachIndexed
                sinceLeaf += hypot(point.x - stroke[i - 1].x, point.y - stroke[i - 1].y)
                if (sinceLeaf < LEAF_SPACING) return@forEachIndexed
                sinceLeaf = 0f
                leafIndex++
                val grown = ((progress - point.timePosition) / 0.05f).coerceIn(0f, 1f)
                if (grown <= 0f) return@forEachIndexed
                val before = frame.at(stroke[i - 1])
                val after = frame.at(stroke[i + 1])
                val side = if (leafIndex % 2 == 0) 1f else -1f
                val sway = sin(loopSeconds * 1.1f + leafIndex) * 0.14f
                val angle = atan2(after.y - before.y, after.x - before.x) + side * 1.05f + sway
                val length = size.minDimension * (0.016f + 0.026f * point.speedIntensity) * grown
                drawLeaf(path, frame.at(point), angle, length, mixArgb(p.leafSlowArgb, p.leafFastArgb, point.speedIntensity))
            }
            stroke.zipWithNext().forEach { (a, b) ->
                drawSegment(a, b, progress, frame, if (a.lowBeamOn || b.lowBeamOn) stemLowBeam else stem, stemWidth)
            }
            // A bud where the turn signal comes on.
            stroke.forEachIndexed { i, point ->
                val comesOn = point.turnSignalOn && (i == 0 || !stroke[i - 1].turnSignalOn)
                if (!comesOn || progress < point.timePosition) return@forEachIndexed
                val at = frame.at(point)
                val next = frame.at(stroke[minOf(i + 1, stroke.lastIndex)])
                val heading = atan2(next.y - at.y, next.x - at.x) + (PI / 2).toFloat()
                val bud = at + Offset(cos(heading), sin(heading)) * (size.minDimension * 0.022f)
                drawLine(stem, at, bud, stemWidth * 0.4f, StrokeCap.Round)
                drawCircle(Color(p.budArgb), size.minDimension * 0.009f, bud)
            }
        }

        // The seed mound at the start and, once the stem is grown, a flower at the end.
        val first = route.strokes.firstOrNull()?.firstOrNull() ?: return
        val last = route.strokes.last().last()
        val mound = Size(size.minDimension * 0.04f, size.minDimension * 0.018f)
        drawOval(Color(p.colors.outlineArgb), frame.at(first) - Offset(mound.width / 2, mound.height / 4), mound)
        val bloom = ((progress - 0.94f) / 0.06f).coerceIn(0f, 1f)
        if (bloom > 0f) {
            val at = frame.at(last)
            val petal = size.minDimension * 0.028f * bloom
            val turn = sin(loopSeconds * 0.9f) * 0.08f
            repeat(8) { i ->
                drawPetal(path, i * (PI / 4).toFloat() + turn, petal * 0.2f, petal, petal * 0.3f, Color(p.petalFastArgb), 0.95f, at)
            }
            drawCircle(Color(p.centreArgb), petal * 0.25f, at)
        }
    }

    override fun DrawScope.drawLegendSample(
        signal: LegendSignal,
        dark: Boolean,
    ) {
        val p = palette(dark)
        when (signal) {
            LegendSignal.TURN_SIGNAL -> drawCircle(Color(p.budArgb), size.minDimension * 0.32f)

            LegendSignal.WIPER ->
                listOf(0.25f, 0.5f, 0.75f).forEach { x ->
                    val top = Offset(size.width * x + size.height * 0.12f, size.height * 0.1f)
                    drawLine(Color(p.rainArgb), top, top + Offset(-size.height * 0.24f, size.height * 0.8f), size.height * 0.13f, StrokeCap.Round)
                }

            LegendSignal.LOW_BEAM ->
                drawRoundRect(
                    Color(p.stemLowBeamArgb),
                    topLeft = Offset(0f, size.height * 0.36f),
                    size = Size(size.width, size.height * 0.28f),
                    cornerRadius = CornerRadius(size.height * 0.14f),
                )
        }
    }

    /** A thin petal from [inner] to [outer] along [angle] (radians), at most twice [halfWidth] wide. */
    private fun DrawScope.drawPetal(
        path: Path,
        angle: Float,
        inner: Float,
        outer: Float,
        halfWidth: Float,
        color: Color,
        alpha: Float,
        at: Offset = center,
    ) {
        val along = Offset(cos(angle), sin(angle))
        val across = Offset(-along.y, along.x) * halfWidth
        val foot = at + along * inner
        val tip = at + along * outer
        val belly = at + along * ((inner + outer) * 0.55f)
        path.reset()
        path.moveTo(foot.x, foot.y)
        path.quadraticTo(belly.x + across.x, belly.y + across.y, tip.x, tip.y)
        path.quadraticTo(belly.x - across.x, belly.y - across.y, foot.x, foot.y)
        drawPath(path, color, alpha)
    }

    /** A leaf from [foot] along [angle] (radians), [length] long. */
    private fun DrawScope.drawLeaf(
        path: Path,
        foot: Offset,
        angle: Float,
        length: Float,
        color: Color,
    ) {
        val along = Offset(cos(angle), sin(angle))
        val across = Offset(-along.y, along.x) * (length * 0.34f)
        val tip = foot + along * length
        val belly = foot + along * (length * 0.5f)
        path.reset()
        path.moveTo(foot.x, foot.y)
        path.quadraticTo(belly.x + across.x, belly.y + across.y, tip.x, tip.y)
        path.quadraticTo(belly.x - across.x, belly.y - across.y, foot.x, foot.y)
        drawPath(path, color)
    }

    /** Three short blades of grass fanning up from [foot]. */
    private fun DrawScope.drawTuft(
        foot: Offset,
        height: Float,
        color: Color,
    ) {
        val width = height * 0.14f
        drawLine(color, foot, foot + Offset(-height * 0.35f, -height * 0.8f), width, StrokeCap.Round)
        drawLine(color, foot, foot + Offset(0f, -height), width, StrokeCap.Round)
        drawLine(color, foot, foot + Offset(height * 0.35f, -height * 0.8f), width, StrokeCap.Round)
    }
}
