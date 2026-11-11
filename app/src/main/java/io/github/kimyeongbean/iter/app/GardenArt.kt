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

/** A leaf grows over this share of the draw-in; the flower at the end blooms over the last of it. */
private const val LEAF_GROW_SPAN = 0.05f
private const val BLOOM_SPAN = 0.06f

/** Petals let a little of what is under them through. */
private const val PETAL_ALPHA = 0.92f

/** Petals of the big day art (two shapes each at most) and of a thumbnail, where fewer stay apart. */
private const val BIG_PETALS = 60
private const val THUMBNAIL_PETALS = 14

/** How far a leaf leans away from the stem, in radians. */
private const val LEAF_ANGLE_RAD = 1.05f

/** Grass tufts scattered over the map graph panel. */
private const val PANEL_TUFTS = 48

/** Distance along the stem between leaves, in route unit square lengths; sides alternate. */
private const val LEAF_SPACING = 0.035f

/** Leaves on a backdrop stem. */
private const val STEM_LEAVES = 6

/** One backdrop mark per square of this side, in dp. */
private const val BACKDROP_CELL_DP = 140f

/** Still fireflies on the dark bed, and how strongly they glow over it. */
private const val FIREFLIES = 9
private const val FIREFLY_ALPHA = 0.3f

/** Splash: the stem grows over this first share of the draw-in, drawn in this many pieces. */
private const val SPLASH_STEM_SPAN = 0.55f
private const val SPLASH_STEM_PIECES = 20

/** Splash: how far up the stem each leaf sits (0 foot to 1 head), and the share of the draw-in a leaf takes. */
private val SPLASH_LEAVES = listOf(0.35f, 0.6f)
private const val SPLASH_LEAF_SPAN = 0.25f

/** Splash: petals of the flower, and the share of the draw-in one takes to open. */
private const val SPLASH_PETALS = 12
private const val SPLASH_PETAL_SPAN = 0.22f

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
            textSecondaryArgb = 0xFF36422F.toInt(),
            outlineArgb = 0xFF8C9A7C.toInt(),
            primaryArgb = 0xFF2F5229.toInt(),
            onPrimaryArgb = 0xFFFFFFFF.toInt(),
        )

    override val darkColors =
        IterColors(
            backgroundArgb = 0xFF10160F.toInt(),
            cardArgb = 0xFF24362C.toInt(),
            cardHighArgb = 0xFF263322.toInt(),
            controlArgb = 0xFF36462F.toInt(),
            textArgb = 0xFFEEF3E6.toInt(),
            textSecondaryArgb = 0xFFBFCBB3.toInt(),
            outlineArgb = 0xFF6B7A5F.toInt(),
            primaryArgb = 0xFFEEF3E6.toInt(),
            onPrimaryArgb = 0xFF10160F.toInt(),
        )

    /** Light: pale morning sun from the top left over a pale green bed, a faint pink only round the big flower. */
    private val lightGlow =
        GlassGlow(
            litArgb = 0xFFF9FAEE.toInt(),
            groundArgb = 0xFFE5EEDC.toInt(),
            shadeArgb = 0xFFD9E3CF.toInt(),
            tintArgb = 0xFFF8DCE5.toInt(),
            tintAt = Offset(0.23f, 0.4f),
        )

    /** Dark: a moonlit night garden, deep night teal with a cool mint light from the top left. */
    private val darkGlow =
        GlassGlow(
            litArgb = 0xFF2B4D43.toInt(),
            groundArgb = 0xFF0D1713.toInt(),
            shadeArgb = 0xFF050A08.toInt(),
        )

    override fun glow(dark: Boolean): GlassGlow = if (dark) darkGlow else lightGlow

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
        /** One or two climbing stems with leaves on the bed. */
        val backdropStemArgb: Int,
        val backdropLeafArgb: Int,
        /** A few still fireflies on the night bed, or none. */
        val fireflyArgb: Int? = null,
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
            backdropStemArgb = 0xFFD2DBBB.toInt(),
            backdropLeafArgb = 0xFFD8E0C2.toInt(),
        )

    // On the dark ground a darker stem cannot stand out, so the low beam stem is a paler one.
    private val dark =
        Palette(
            colors = darkColors,
            stemArgb = 0xFF7FB56B.toInt(),
            stemLowBeamArgb = 0xFFD7EDC9.toInt(),
            leafSlowArgb = 0xFF4C6E40.toInt(),
            leafFastArgb = 0xFF9AD07E.toInt(),
            petalSlowArgb = 0xFFC84E80.toInt(),
            petalFastArgb = 0xFFFF8FB3.toInt(),
            centreArgb = 0xFFF2C24B.toInt(),
            budArgb = 0xFFE58BA8.toInt(),
            rainArgb = 0xFF8FC3EA.toInt(),
            backdropTuftArgb = 0xFF27402F.toInt(),
            backdropSoilArgb = 0xFF1C2E23.toInt(),
            backdropStemArgb = 0xFF2C4A38.toInt(),
            backdropLeafArgb = 0xFF31533E.toInt(),
            fireflyArgb = 0xFFF0CC5A.toInt(),
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

    override fun backdropArgbs(dark: Boolean): List<Int> =
        palette(dark).let { p ->
            listOf(p.backdropTuftArgb, p.backdropSoilArgb, p.backdropStemArgb, p.backdropLeafArgb) +
                listOfNotNull(p.fireflyArgb?.let { compositeArgb(it, FIREFLY_ALPHA, p.colors.backgroundArgb) })
        }

    /** Grass tufts and soil specks, scattered evenly. */
    override fun DrawScope.drawBackdrop(dark: Boolean) {
        val p = palette(dark)
        val tuft = Color(p.backdropTuftArgb)
        val soil = Color(p.backdropSoilArgb)
        forEachBackdropSpot(BACKDROP_CELL_DP) { i, at ->
            // Three sizes, so near and far marks read apart.
            val near = backdropSizeStep(i)
            if (i % 3 == 0) {
                drawCircle(soil, radius = (1.5f + 1.2f * near) * density, center = at)
            } else {
                drawTuft(at, height = (6f + 4.5f * near) * density, color = tuft)
            }
        }
        BackdropStems.forEach { drawBackdropStem(it, Color(p.backdropStemArgb), Color(p.backdropLeafArgb)) }
        p.fireflyArgb?.let { argb ->
            val firefly = Color(argb)
            repeat(FIREFLIES) { i ->
                val at = Offset(scatter(i, 95) * size.width, (0.1f + 0.65f * scatter(i, 96)) * size.height)
                drawCircle(firefly.copy(alpha = FIREFLY_ALPHA * 0.35f), 9f * density, at)
                drawCircle(firefly.copy(alpha = FIREFLY_ALPHA), 2.4f * density, at)
            }
        }
    }

    /** Backdrop stems: each a curve from the bottom edge up (four points, shares of the screen). */
    private val BackdropStems =
        listOf(
            listOf(Offset(0.015f, 1f), Offset(0.06f, 0.76f), Offset(-0.01f, 0.56f), Offset(0.07f, 0.38f)),
            listOf(Offset(0.43f, 1f), Offset(0.39f, 0.83f), Offset(0.48f, 0.7f), Offset(0.44f, 0.5f)),
        )

    /** A stem climbing from the ground with leaves on alternate sides and a bud at its tip. */
    private fun DrawScope.drawBackdropStem(
        points: List<Offset>,
        stem: Color,
        leaf: Color,
    ) {
        val (a, b, c, d) = points.map { Offset(it.x * size.width, it.y * size.height) }

        fun at(t: Float): Offset {
            val u = 1 - t
            return a * (u * u * u) + b * (3 * u * u * t) + c * (3 * u * t * t) + d * (t * t * t)
        }
        val side = size.minDimension
        val path =
            Path().apply {
                moveTo(a.x, a.y)
                cubicTo(b.x, b.y, c.x, c.y, d.x, d.y)
            }
        drawPath(path, stem, style = Stroke(side * 0.004f, cap = StrokeCap.Round))
        val leafLength = side * 0.032f
        for (k in 1..STEM_LEAVES) {
            val t = k / (STEM_LEAVES + 1f)
            val foot = at(t)
            val ahead = at(t + 0.01f) - foot
            // Each leaf leans and grows a little differently, smaller towards the tip.
            val lean = 0.75f + 0.35f * scatter(k, 91)
            val angle = atan2(ahead.y, ahead.x) + (if (k % 2 == 0) 1 else -1) * lean
            val tip = foot + Offset(cos(angle), sin(angle)) * (leafLength * (1.15f - 0.5f * t) * (0.8f + 0.4f * scatter(k, 92)))
            val mid = (foot + tip) / 2f
            val bulge = Offset(-(tip.y - foot.y), tip.x - foot.x) * 0.28f
            drawPath(
                Path().apply {
                    moveTo(foot.x, foot.y)
                    quadraticTo(mid.x + bulge.x, mid.y + bulge.y, tip.x, tip.y)
                    quadraticTo(mid.x - bulge.x, mid.y - bulge.y, foot.x, foot.y)
                },
                leaf,
            )
        }
        drawCircle(leaf, side * 0.008f, d)
    }

    /** A petal: a run of the day art's shapes, summed up once so drawing only draws. */
    private class Petal(
        val angleDeg: Float,
        val timePosition: Float,
        val speedIntensity: Float,
        val turnSignal: Boolean,
        val wiper: Boolean,
        val lowBeam: Boolean,
    )

    private fun petals(
        data: DayArtData,
        count: Int,
    ): List<Petal> {
        val shapes = data.shapes
        val perPetal = ((shapes.size + count - 1) / count).coerceAtLeast(1)
        return shapes.chunked(perPetal).map { group ->
            val first = group.first()
            val last = group.last()
            Petal(
                angleDeg = ((first.startAngleDeg + last.startAngleDeg + last.sweepAngleDeg) / 2).toFloat() - 90f,
                timePosition = first.timePosition.toFloat(),
                speedIntensity = group.sumOf { it.speedIntensity.toDouble() }.toFloat() / group.size,
                turnSignal = group.any { DayArtPattern.TURN_SIGNAL in it.patterns },
                wiper = group.any { DayArtPattern.WIPER in it.patterns },
                lowBeam = group.any { DayArtPattern.LOW_BEAM in it.patterns },
            )
        }
    }

    private val bigPetals = LastComputed<DayArtData, List<Petal>> { petals(it, BIG_PETALS) }
    private val thumbnailPetals = LastComputed<DayArtData, List<Petal>> { petals(it, THUMBNAIL_PETALS) }

    override fun DrawScope.drawDayArt(
        data: DayArtData,
        dark: Boolean,
        progress: Float,
        loopSeconds: Float,
    ) {
        if (data.shapes.isEmpty()) return
        val p = palette(dark)
        val radius = size.minDimension / 2 * DRAWING_RADIUS_SCALE
        val thumbnail = isThumbnail
        val halfWidth = radius * if (thumbnail) 0.11f else 0.05f
        val inner = radius * PETAL_INNER_RADIUS
        val path = Path()
        // The whole flower leans a little to and fro; each petal flutters on its own beat.
        rotate(degrees = sin(loopSeconds * 0.9f) * 1.6f) {
            (if (thumbnail) thumbnailPetals else bigPetals)[data].forEachIndexed { i, petal ->
                val angleRad = Math.toRadians(petal.angleDeg.toDouble()).toFloat() + sin(loopSeconds * 1.3f + i * 1.4f) * 0.012f
                val outer = radius * (0.25f + 0.6f * petal.speedIntensity.coerceAtLeast(0.08f))
                val color = mixArgb(p.petalSlowArgb, p.petalFastArgb, petal.speedIntensity)
                val unfolded = grownFraction(progress, petal.timePosition, UNFOLD_SPAN)
                if (unfolded < 1f) drawBlade(path, center, angleRad, inner, outer, halfWidth, color, DRAW_IN_GHOST_ALPHA)
                if (unfolded > 0f) drawBlade(path, center, angleRad, inner, inner + (outer - inner) * unfolded, halfWidth, color, PETAL_ALPHA)
                if (thumbnail || unfolded < 1f) return@forEachIndexed
                val direction = Offset(cos(angleRad), sin(angleRad))
                if (petal.turnSignal) drawCircle(Color(p.budArgb), radius * 0.018f, center + direction * (outer + radius * 0.04f))
                if (petal.wiper) drawCircle(Color(p.rainArgb), radius * 0.014f, center + direction * (radius * 0.97f))
                if (petal.lowBeam) {
                    val r = radius * 0.19f
                    drawArc(
                        color = Color(p.stemLowBeamArgb),
                        startAngle = petal.angleDeg - 3.5f,
                        sweepAngle = 7f,
                        useCenter = false,
                        topLeft = center - Offset(r, r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(radius * 0.04f),
                    )
                }
            }
            drawCircle(Color(p.centreArgb), radius * CENTRE_RADIUS)
        }
    }

    /** A leaf on the stem: where it grows, which way the stem runs there, and which side it is on. */
    private class Leaf(
        val at: RoutePoint,
        val stemAngleRad: Float,
        val side: Float,
    )

    /** One leaf every [LEAF_SPACING] along each stroke, on alternating sides, found once per route. */
    private val leaves =
        LastComputed<MapRoute, List<Leaf>> { route ->
            val found = mutableListOf<Leaf>()
            route.strokes.forEach { stroke ->
                var sinceLeaf = 0f
                for (i in 1 until stroke.lastIndex) {
                    sinceLeaf += hypot(stroke[i].x - stroke[i - 1].x, stroke[i].y - stroke[i - 1].y)
                    if (sinceLeaf < LEAF_SPACING) continue
                    sinceLeaf = 0f
                    val stemAngleRad = atan2(stroke[i + 1].y - stroke[i - 1].y, stroke[i + 1].x - stroke[i - 1].x)
                    found += Leaf(stroke[i], stemAngleRad, side = if (found.size % 2 == 0) 1f else -1f)
                }
            }
            found
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
        val stem = Color(p.stemArgb)
        val stemLowBeam = Color(p.stemLowBeamArgb)
        val stemWidth = side * 0.011f
        val path = Path()

        // The bed: tufts in the panel's own soft shade, under everything.
        val tuft = Color(p.colors.cardHighArgb)
        repeat(PANEL_TUFTS) { i -> drawTuft(Offset(scatter(i, 21) * size.width, scatter(i, 22) * size.height), side * 0.016f, tuft) }

        drawRain(route, frame, progress, loopSeconds, Color(p.rainArgb))

        // Leaves first, so the stem runs over their feet.
        leaves[route].forEachIndexed { i, leaf ->
            val grown = grownFraction(progress, leaf.at.timePosition, LEAF_GROW_SPAN)
            if (grown <= 0f) return@forEachIndexed
            val angleRad = leaf.stemAngleRad + leaf.side * LEAF_ANGLE_RAD + sin(loopSeconds * 1.1f + i) * 0.14f
            val intensity = leaf.at.speedIntensity
            val length = side * (0.016f + 0.026f * intensity) * grown
            drawBlade(path, frame.at(leaf.at), angleRad, 0f, length, length * 0.34f, mixArgb(p.leafSlowArgb, p.leafFastArgb, intensity), 1f)
        }
        route.strokes.forEach { stroke ->
            // A stretch of one sample has no line to draw: it is a dot of stem.
            if (stroke.size == 1 && progress >= stroke[0].timePosition) drawCircle(stem, stemWidth / 2, frame.at(stroke[0]))
            for (i in 1..stroke.lastIndex) {
                val a = stroke[i - 1]
                val b = stroke[i]
                drawSegment(a, b, progress, frame, if (a.lowBeamOn || b.lowBeamOn) stemLowBeam else stem, stemWidth)
            }
            // A bud where the turn signal comes on.
            stroke.forEachTurnSignalStart { i, point ->
                if (progress < point.timePosition) return@forEachTurnSignalStart
                val at = frame.at(point)
                val next = frame.at(stroke[minOf(i + 1, stroke.lastIndex)])
                val besideRad = atan2(next.y - at.y, next.x - at.x) + (PI / 2).toFloat()
                val bud = at + Offset(cos(besideRad), sin(besideRad)) * (side * 0.022f)
                drawLine(stem, at, bud, stemWidth * 0.4f, StrokeCap.Round)
                drawCircle(Color(p.budArgb), side * 0.009f, bud)
            }
        }

        // The seed mound at the start and, as the stem finishes growing, a flower at the end.
        val first = route.strokes.firstOrNull()?.firstOrNull() ?: return
        val last = route.strokes.last().last()
        val mound = Size(side * 0.04f, side * 0.018f)
        drawOval(Color(p.colors.outlineArgb), frame.at(first) - Offset(mound.width / 2, mound.height / 4), mound)
        val bloom = grownFraction(progress, 1 - BLOOM_SPAN, BLOOM_SPAN)
        if (bloom > 0f) {
            val at = frame.at(last)
            val petal = side * 0.028f * bloom
            val turnRad = sin(loopSeconds * 0.9f) * 0.08f
            repeat(8) { i -> drawBlade(path, at, i * (PI / 4).toFloat() + turnRad, petal * 0.2f, petal, petal * 0.3f, Color(p.petalFastArgb), PETAL_ALPHA) }
            drawCircle(Color(p.centreArgb), petal * 0.25f, at)
        }
    }

    /** A stem grows up from a seed mound, two leaves come out, and a flower opens petal by petal. */
    override fun DrawScope.drawSplash(
        dark: Boolean,
        progress: Float,
    ) {
        val p = palette(dark)
        val side = size.minDimension
        val foot = center + Offset(0f, side * 0.44f)
        val head = center - Offset(0f, side * 0.14f)
        val path = Path()

        // The stem leans out and back on its way up.
        fun stemAt(t: Float) = Offset(foot.x + sin(t * PI.toFloat()) * side * 0.05f, foot.y + (head.y - foot.y) * t)

        val mound = Size(side * 0.2f, side * 0.05f)
        drawOval(Color(p.colors.outlineArgb), foot - Offset(mound.width / 2, mound.height / 3), mound)

        SPLASH_LEAVES.forEachIndexed { i, leafAt ->
            val grown = easeOutBack(grownFraction(progress, SPLASH_STEM_SPAN * leafAt, SPLASH_LEAF_SPAN))
            if (grown <= 0f) return@forEachIndexed
            val leafSide = if (i % 2 == 0) 1f else -1f
            val length = side * 0.13f * grown
            drawBlade(path, stemAt(leafAt), -(PI / 2).toFloat() + leafSide * LEAF_ANGLE_RAD, 0f, length, length * 0.34f, Color(p.leafFastArgb), 1f)
        }
        val stemGrown = easeInOut(grownFraction(progress, 0f, SPLASH_STEM_SPAN))
        repeat(SPLASH_STEM_PIECES) { i ->
            drawLine(
                Color(p.stemArgb),
                stemAt(stemGrown * i / SPLASH_STEM_PIECES),
                stemAt(stemGrown * (i + 1) / SPLASH_STEM_PIECES),
                side * 0.022f,
                StrokeCap.Round,
            )
        }

        // Petals open one after another round the flower, the last one as the draw-in ends.
        val firstOpens = SPLASH_STEM_SPAN - 0.05f
        val opensEvery = (1f - SPLASH_PETAL_SPAN - firstOpens) / (SPLASH_PETALS - 1)
        repeat(SPLASH_PETALS) { i ->
            val open = easeOutBack(grownFraction(progress, firstOpens + opensEvery * i, SPLASH_PETAL_SPAN))
            if (open <= 0f) return@repeat
            val angleRad = -(PI / 2).toFloat() + i * (2 * PI / SPLASH_PETALS).toFloat()
            val color = mixArgb(p.petalSlowArgb, p.petalFastArgb, if (i % 2 == 0) 1f else 0.55f)
            drawBlade(path, head, angleRad, side * 0.04f, side * 0.04f + side * 0.26f * open, side * 0.05f, color, PETAL_ALPHA)
        }
        val centreGrown = easeOutBack(grownFraction(progress, firstOpens, SPLASH_PETAL_SPAN))
        if (centreGrown > 0f) drawCircle(Color(p.centreArgb), side * 0.055f * centreGrown, head)
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

    /**
     * A petal or a leaf: a pointed blade from [inner] to [outer] away from [at] along [angleRad],
     * at most twice [halfWidth] wide.
     */
    private fun DrawScope.drawBlade(
        path: Path,
        at: Offset,
        angleRad: Float,
        inner: Float,
        outer: Float,
        halfWidth: Float,
        color: Color,
        alpha: Float,
    ) {
        val along = Offset(cos(angleRad), sin(angleRad))
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
