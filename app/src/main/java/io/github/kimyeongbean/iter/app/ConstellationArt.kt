package io.github.kimyeongbean.iter.app

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/** Day art radius where the rays start, as a share of the drawing radius. */
private const val RAY_INNER_RADIUS = 0.16f

/** A ray or a star lights up over this share of the draw-in after the draw-in reaches it. */
private const val LIGHT_UP_SPAN = 0.06f

/** Distance along the route between stars, in route unit square lengths. */
private const val STAR_SPACING = 0.03f

/** A meteor crosses its stretch of sky once per this many seconds. */
private const val METEOR_SECONDS = 2.4f

/** A meteor's trail is drawn in this many pieces, each fainter than the one before. */
private const val METEOR_PIECES = 3

/** Far stars scattered over the map graph panel. */
private const val PANEL_STARS = 90

/** The Milky Way: its width as a share of the screen's short side, its glows and its dust spacing in dp. */
private const val MILKY_WAY_WIDTH = 0.16f
private const val MILKY_WAY_DUST_DP = 13f

/** Bright stars on the backdrop. */
private const val BRIGHT_STARS = 7

/** A shooting star: one every so often, each a short flight; the first a few seconds in. */
private const val SHOOTING_STAR_EVERY_SECONDS = 7f
private const val SHOOTING_STAR_FLIGHT_SECONDS = 0.9f
private const val SHOOTING_STAR_FIRST_SECONDS = 3f

/**
 * Rays of the big day art and of a thumbnail. The big art keeps one ray per shape (the drawing data
 * has at most this many); a thumbnail keeps as many as the garden's petals, so they stay apart.
 */
private const val BIG_RAYS = 120
private const val THUMBNAIL_RAYS = 14

/** One backdrop star per square of this side, in dp. */
private const val BACKDROP_CELL_DP = 105f

/** Today's grass cell ring in the light theme: chart gold, deep enough for 3:1 on the glass. */
private val TODAY_GOLD = 0xFFA07419.toInt()

/** The star chart arcs' radii, as shares of the screen height. */
private val CHART_ARCS = listOf(1.55f, 1.8f, 2.05f)

/** Splash: the constellation's stars in a unit square, in the order they light up. */
private val SPLASH_STARS =
    listOf(
        Offset(0.10f, 0.36f),
        Offset(0.30f, 0.68f),
        Offset(0.50f, 0.44f),
        Offset(0.70f, 0.70f),
        Offset(0.88f, 0.30f),
    )

/** Splash: a star lights up over this share of the draw-in. */
private const val SPLASH_LIGHT_UP_SPAN = 0.2f

/**
 * Constellation (별자리): the day is a star with thin rays, the route a constellation, on a night
 * sky (a dawn sky in the light system theme). Speed is ray length and brightness, and star size.
 * Turn signal: a gold sparkle. Wiper: meteors. Low beam: a nebula band. Repeating effect: the stars
 * twinkle slowly.
 */
internal object ConstellationArt : ThemeArt {
    override val lightColors =
        IterColors(
            backgroundArgb = 0xFFE9ECF8.toInt(),
            cardArgb = 0xFFF8F9FE.toInt(),
            cardHighArgb = 0xFFDDE2F3.toInt(),
            controlArgb = 0xFFCDD4EA.toInt(),
            textArgb = 0xFF141A33.toInt(),
            textSecondaryArgb = 0xFF454E73.toInt(),
            outlineArgb = 0xFF8A93B8.toInt(),
            primaryArgb = 0xFF1F2A5C.toInt(),
            onPrimaryArgb = 0xFFFFFFFF.toInt(),
        )

    override val darkColors =
        IterColors(
            backgroundArgb = 0xFF070B1E.toInt(),
            cardArgb = 0xFF10162E.toInt(),
            cardHighArgb = 0xFF1B2342.toInt(),
            controlArgb = 0xFF2A3358.toInt(),
            textArgb = 0xFFEEF1FF.toInt(),
            textSecondaryArgb = 0xFFB9C1E3.toInt(),
            outlineArgb = 0xFF5D678F.toInt(),
            primaryArgb = 0xFFEEF1FF.toInt(),
            onPrimaryArgb = 0xFF070B1E.toInt(),
        )

    private class Palette(
        val colors: IterColors,
        /** A star at standstill and at full speed: it stands out more the faster. */
        val starSlowArgb: Int,
        val starFastArgb: Int,
        val lineArgb: Int,
        val sparkleArgb: Int,
        val meteorArgb: Int,
        val nebulaArgb: Int,
        val backdropStarArgbs: List<Int>,
        /** The Milky Way: its soft glow, its dust of tiny stars, and the few bright backdrop stars. */
        val milkyGlowArgb: Int,
        val milkyDustArgb: Int,
        val brightStarArgb: Int,
        /** Faint arcs like the latitude lines of a printed star chart, or none. */
        val chartArcArgb: Int? = null,
        /** Day art ray width, as a share of the usual. */
        val rayWidthScale: Float = 1f,
        /** Shooting star tail length, as a share of the screen's short side. */
        val meteorTail: Float = 0.14f,
        /** The Milky Way's wide veil and its core, as opacities. */
        val milkyVeilAlpha: Float = 0.45f,
        val milkyCoreAlpha: Float = 0.7f,
        /** How strongly the day art's core glows. */
        val coreGlowAlpha: Float = 0.85f,
        /** The map graph's low beam nebula band width, as a share of the short side. */
        val nebulaWidth: Float = 0.07f,
    )

    private val light =
        Palette(
            colors = lightColors,
            starSlowArgb = 0xFF7C86C7.toInt(),
            starFastArgb = 0xFF1F2A5C.toInt(),
            lineArgb = 0xFF1F2A5C.toInt(),
            sparkleArgb = 0xFFB7791F.toInt(),
            meteorArgb = 0xFF1F2A5C.toInt(),
            nebulaArgb = 0xFFC0C7ED.toInt(),
            backdropStarArgbs = listOf(0xFFB9C1E4.toInt(), 0xFFC8CEEA.toInt()),
            milkyGlowArgb = 0xFFC9D0EF.toInt(),
            milkyDustArgb = 0xFFBAC3E6.toInt(),
            brightStarArgb = 0xFFE3CF9C.toInt(),
            chartArcArgb = 0xFFCDD3EE.toInt(),
            rayWidthScale = 1.5f,
            meteorTail = 0.07f,
            milkyVeilAlpha = 0.16f,
            milkyCoreAlpha = 0.26f,
            coreGlowAlpha = 0.3f,
            nebulaWidth = 0.035f,
        )

    private val dark =
        Palette(
            colors = darkColors,
            starSlowArgb = 0xFF5F6B99.toInt(),
            starFastArgb = 0xFFFFFFFF.toInt(),
            lineArgb = 0xFF4E5A86.toInt(),
            sparkleArgb = 0xFFFFE29A.toInt(),
            meteorArgb = 0xFFFFFFFF.toInt(),
            nebulaArgb = 0xFF33407A.toInt(),
            backdropStarArgbs = listOf(0xFF141A33.toInt(), 0xFF1A2140.toInt()),
            milkyGlowArgb = 0xFF1A2245.toInt(),
            milkyDustArgb = 0xFF2B3563.toInt(),
            brightStarArgb = 0xFF3A4675.toInt(),
        )

    private fun palette(dark: Boolean) = if (dark) this.dark else light

    override fun panel(dark: Boolean): ThemePanel {
        val p = palette(dark)
        return ThemePanel(
            argb = p.colors.cardArgb,
            textArgb = p.colors.textSecondaryArgb,
            speedColorBaseArgb = p.starFastArgb,
            effects = MapEffectColors(turnSignalBandArgb = p.sparkleArgb, lowBeamArgb = p.nebulaArgb, rainArgb = p.meteorArgb),
            art = this,
            dark = dark,
        )
    }

    /** Light: a printed star chart, paper with an indigo cast lit from the top left. */
    private val lightGlow =
        GlassGlow(
            litArgb = 0xFFF7F7FC.toInt(),
            groundArgb = 0xFFE9EBF6.toInt(),
            shadeArgb = 0xFFDFE2F0.toInt(),
            shadowScale = 1.4f,
        )

    override fun todayMarkArgb(dark: Boolean): Int? = if (dark) null else TODAY_GOLD

    override fun glow(dark: Boolean): GlassGlow? = if (dark) null else lightGlow

    override fun backdropArgbs(dark: Boolean): List<Int> =
        palette(dark).let { it.backdropStarArgbs + listOfNotNull(it.milkyGlowArgb, it.milkyDustArgb, it.brightStarArgb, it.chartArcArgb) }

    /** The Milky Way across the sky, small faint stars scattered evenly and a few bright ones. */
    override fun DrawScope.drawBackdrop(dark: Boolean) {
        val p = palette(dark)
        p.chartArcArgb?.let { argb ->
            // Arcs round a centre far below the screen, like a chart's latitude lines.
            val centre = Offset(size.width * 0.5f, size.height * 2.2f)
            CHART_ARCS.forEach { r -> drawCircle(Color(argb), size.height * r, centre, style = Stroke(1.2f * density)) }
        }
        drawMilkyWay(p)
        val stars = p.backdropStarArgbs.map { Color(it) }
        forEachBackdropSpot(BACKDROP_CELL_DP) { i, at ->
            // Two sizes, near and far.
            val near = if (backdropSizeStep(i) == 2) 2.4f else 1.2f
            drawCircle(stars[i % stars.size], radius = near * density, center = at)
        }
        val bright = Color(p.brightStarArgb)
        repeat(BRIGHT_STARS) { i ->
            val at = Offset(scatter(i, 51) * size.width, scatter(i, 52) * size.height)
            val r = (2.5f + 2f * scatter(i, 53)) * density
            drawCircle(bright, r, at)
            // A soft cross of light.
            drawLine(bright, at - Offset(r * 3.5f, 0f), at + Offset(r * 3.5f, 0f), r * 0.35f, StrokeCap.Round)
            drawLine(bright, at - Offset(0f, r * 3.5f), at + Offset(0f, r * 3.5f), r * 0.35f, StrokeCap.Round)
        }
    }

    /** A soft band from the bottom left to the top right, dusted with tiny stars thickest in its middle. */
    private fun DrawScope.drawMilkyWay(p: Palette) {
        val from = Offset(-0.05f * size.width, 0.95f * size.height)
        val to = Offset(1.05f * size.width, 0.08f * size.height)
        val along = to - from
        val length = along.getDistance()
        val normal = Offset(-along.y / length, along.x / length)
        val middle = (from + to) / 2f
        val band = size.minDimension * MILKY_WAY_WIDTH
        val glow = Color(p.milkyGlowArgb)
        val angleDeg = atan2(along.y, along.x) * 180f / PI.toFloat()
        // One smooth band, softest at its edges: a wide faint veil and a narrower, brighter core.
        rotate(angleDeg, middle) {
            listOf(band to p.milkyVeilAlpha, band * 0.45f to p.milkyCoreAlpha).forEach { (half, alpha) ->
                drawRect(
                    Brush.verticalGradient(
                        0f to glow.copy(alpha = 0f),
                        0.5f to glow.copy(alpha = alpha),
                        1f to glow.copy(alpha = 0f),
                        startY = middle.y - half,
                        endY = middle.y + half,
                    ),
                    topLeft = Offset(middle.x - length / 2, middle.y - half),
                    size = Size(length, half * 2),
                )
            }
        }
        val dust = Color(p.milkyDustArgb)
        val dustCell = MILKY_WAY_DUST_DP * density
        val count = (length * band / (dustCell * dustCell)).toInt()
        repeat(count) { i ->
            val t = scatter(i, 41)
            // Two scatters summed: most dust gathers near the band's middle.
            val across = (scatter(i, 42) + scatter(i, 43) - 1f) * band * 0.8f
            drawCircle(dust, (0.5f + 0.9f * scatter(i, 44)) * density, from + along * t + normal * across)
        }
    }

    override val backdropMoves: Boolean get() = true

    /** Now and then a shooting star crosses the sky, somewhere new each time. */
    override fun DrawScope.drawBackdropMotion(
        dark: Boolean,
        seconds: Float,
    ) {
        if (seconds < SHOOTING_STAR_FIRST_SECONDS) return
        // Shifted, so the first one comes a few seconds in rather than at once.
        val time = seconds - SHOOTING_STAR_FIRST_SECONDS
        val n = (time / SHOOTING_STAR_EVERY_SECONDS).toInt()
        val flown = (time - n * SHOOTING_STAR_EVERY_SECONDS) / SHOOTING_STAR_FLIGHT_SECONDS
        if (flown > 1f) return
        val start = Offset((0.35f + 0.6f * scatter(n, 61)) * size.width, (0.04f + 0.3f * scatter(n, 62)) * size.height)
        val direction = Offset(-0.85f, 0.53f)
        val head = start + direction * (size.minDimension * 0.4f * flown)
        val glow = sin(PI.toFloat() * flown)
        val p = palette(dark)
        val tail = head - direction * (size.minDimension * p.meteorTail * glow.coerceAtLeast(0.2f))
        val color = Color(p.meteorArgb)
        drawLine(
            Brush.linearGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = glow)), tail, head),
            tail,
            head,
            2f * density,
            StrokeCap.Round,
        )
        drawCircle(color.copy(alpha = glow), 1.8f * density, head)
    }

    /**
     * A ray: a run of the day art's shapes, summed up once so drawing only draws. Its speed is the
     * run's mean; a vehicle signal shows when any shape of the run has it, so no stretch drops out.
     */
    private class Ray(
        /** Drawing angle of the ray's middle, 0 at 3 o'clock. */
        val midDeg: Float,
        /** Where the run starts and how far it reaches, in day art degrees (0 at 12 o'clock). */
        val startDeg: Float,
        val sweepDeg: Float,
        val timePosition: Float,
        val speedIntensity: Float,
        val turnSignal: Boolean,
        val wiper: Boolean,
        val lowBeam: Boolean,
    )

    private fun rays(
        data: DayArtData,
        count: Int,
    ): List<Ray> {
        val shapes = data.shapes
        val perRay = ((shapes.size + count - 1) / count).coerceAtLeast(1)
        return shapes.chunked(perRay).map { group ->
            val first = group.first()
            val last = group.last()
            val sweep = last.startAngleDeg + last.sweepAngleDeg - first.startAngleDeg
            Ray(
                midDeg = (first.startAngleDeg + sweep / 2).toFloat() - 90f,
                startDeg = first.startAngleDeg.toFloat(),
                sweepDeg = sweep.toFloat(),
                timePosition = first.timePosition.toFloat(),
                speedIntensity = group.sumOf { it.speedIntensity.toDouble() }.toFloat() / group.size,
                turnSignal = group.any { DayArtPattern.TURN_SIGNAL in it.patterns },
                wiper = group.any { DayArtPattern.WIPER in it.patterns },
                lowBeam = group.any { DayArtPattern.LOW_BEAM in it.patterns },
            )
        }
    }

    private val bigRays = LastComputed<DayArtData, List<Ray>> { rays(it, BIG_RAYS) }
    private val thumbnailRays = LastComputed<DayArtData, List<Ray>> { rays(it, THUMBNAIL_RAYS) }

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
        // Runs of shapes summed into rays, the same number in the light and the dark theme.
        val rays = if (thumbnail) thumbnailRays[data] else bigRays[data]
        val rayWidth = radius * (if (thumbnail) 0.035f else 0.009f) * p.rayWidthScale
        val inner = radius * RAY_INNER_RADIUS
        rays.forEachIndexed { i, ray ->
            val intensity = ray.speedIntensity
            val midDeg = ray.midDeg
            val angleRad = Math.toRadians(midDeg.toDouble()).toFloat()
            val direction = Offset(cos(angleRad), sin(angleRad))
            val outer = radius * (0.25f + 0.6f * intensity.coerceAtLeast(0.06f))
            val color = mixArgb(p.starSlowArgb, p.starFastArgb, intensity)
            val lit = grownFraction(progress, ray.timePosition, LIGHT_UP_SPAN)
            val alpha = DRAW_IN_GHOST_ALPHA + (1 - DRAW_IN_GHOST_ALPHA) * lit
            drawLine(color, center + direction * inner, center + direction * outer, rayWidth, StrokeCap.Round, alpha = alpha)
            if (thumbnail || lit < 1f) return@forEachIndexed
            // The tip twinkles, each ray on its own beat.
            val twinkle = 0.7f + 0.3f * sin(loopSeconds * 2f + i * 1.7f)
            drawCircle(color, radius * (0.008f + 0.012f * intensity) * twinkle, center + direction * outer)
            // One sparkle where the turn signal comes on, not one per ray: they would run together.
            val turnSignalBefore = i > 0 && rays[i - 1].turnSignal
            if (ray.turnSignal && !turnSignalBefore) {
                drawSparkle(center + direction * (radius * 0.93f), radius * 0.028f, Color(p.sparkleArgb))
            }
            if (ray.wiper && i % 2 == 0) {
                val r = radius * 0.99f
                drawArc(
                    color = Color(p.meteorArgb),
                    startAngle = midDeg - 2f,
                    sweepAngle = 2.4f,
                    useCenter = false,
                    topLeft = center - Offset(r, r),
                    size = Size(r * 2, r * 2),
                    alpha = 0.8f,
                    style = Stroke(radius * 0.008f, cap = StrokeCap.Round),
                )
            }
            if (ray.lowBeam) {
                val r = radius * 0.12f
                drawArc(
                    color = Color(p.nebulaArgb),
                    startAngle = ray.startDeg - 90f,
                    sweepAngle = ray.sweepDeg + 0.5f,
                    useCenter = false,
                    topLeft = center - Offset(r, r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(radius * 0.035f),
                )
            }
        }
        // The core glows and breathes slowly.
        val core = Color(p.starFastArgb)
        val glow = radius * (0.26f + 0.03f * sin(loopSeconds * 1.2f))
        drawCircle(Brush.radialGradient(listOf(core.copy(alpha = p.coreGlowAlpha), core.copy(alpha = 0f)), center, glow), glow)
        drawCircle(core, radius * 0.04f)
    }

    /** A star every [STAR_SPACING] along each stroke and one at each of its ends, found once per route. */
    private val stars =
        LastComputed<MapRoute, List<RoutePoint>> { route ->
            val found = mutableListOf<RoutePoint>()
            route.strokes.forEach { stroke ->
                var sinceStar = STAR_SPACING
                stroke.forEachIndexed { i, point ->
                    if (i > 0) sinceStar += hypot(point.x - stroke[i - 1].x, point.y - stroke[i - 1].y)
                    if (sinceStar < STAR_SPACING && i != stroke.lastIndex) return@forEachIndexed
                    sinceStar = 0f
                    found += point
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

        // Far stars in the panel's own soft shade, under everything.
        val far = Color(p.colors.cardHighArgb)
        repeat(PANEL_STARS) { i -> drawCircle(far, side * (0.002f + 0.003f * scatter(i, 23)), Offset(scatter(i, 21) * size.width, scatter(i, 22) * size.height)) }

        // The nebula band under stretches with the low beam on.
        val nebula = Color(p.nebulaArgb)
        route.forEachSegmentTouching({ it.lowBeamOn }) { a, b -> drawSegment(a, b, progress, frame, nebula, side * p.nebulaWidth) }

        // Meteors where the wiper was on: every third drop anchor, each crossing on its own beat.
        val meteor = Color(p.meteorArgb)
        route.rainDrops.forEachIndexed { i, drop ->
            if (i % 3 != 0 || progress < drop.timePosition) return@forEachIndexed
            val flight = (loopSeconds / METEOR_SECONDS + drop.fallOffset) % 1f
            val head = frame.at(drop.x, drop.y) + Offset(-1f, 0.55f) * (side * 0.1f * (flight - 0.5f))
            val piece = Offset(1f, -0.55f) * (side * 0.045f / METEOR_PIECES)
            val alpha = RAIN_ALPHA * rainAlpha(flight)
            repeat(METEOR_PIECES) { n ->
                val from = head + piece * n.toFloat()
                drawLine(meteor, from, from + piece, side * 0.004f, alpha = alpha * (1 - n.toFloat() / METEOR_PIECES))
            }
        }

        val line = Color(p.lineArgb)
        route.strokes.forEach { stroke ->
            for (i in 1..stroke.lastIndex) drawSegment(stroke[i - 1], stroke[i], progress, frame, line, side * 0.004f)
        }
        stars[route].forEachIndexed { i, point ->
            val lit = grownFraction(progress, point.timePosition, LIGHT_UP_SPAN)
            if (lit <= 0f) return@forEachIndexed
            val twinkle = 0.75f + 0.25f * sin(loopSeconds * 2f + i * 1.7f)
            val intensity = point.speedIntensity
            drawCircle(mixArgb(p.starSlowArgb, p.starFastArgb, intensity), side * (0.004f + 0.009f * intensity) * lit * twinkle, frame.at(point))
        }
        // A gold sparkle where the turn signal comes on.
        route.strokes.forEach { stroke ->
            stroke.forEachTurnSignalStart { _, point ->
                if (progress >= point.timePosition) drawSparkle(frame.at(point), side * 0.018f, Color(p.sparkleArgb))
            }
        }

        // A ring at the start and, once the line is drawn, the brightest star at the end.
        val first = route.strokes.firstOrNull()?.firstOrNull() ?: return
        val last = route.strokes.lastOrNull()?.lastOrNull() ?: return
        drawCircle(Color(p.starFastArgb), side * 0.014f, frame.at(first), style = Stroke(side * 0.003f))
        if (progress >= 1f) {
            drawSparkle(frame.at(last), side * (0.028f + 0.004f * sin(loopSeconds * 2f)), Color(p.starFastArgb))
        }
    }

    /** Stars light up one after another, each joined to the next by a line; the last is a gold sparkle. */
    override fun DrawScope.drawSplash(
        dark: Boolean,
        progress: Float,
    ) {
        val p = palette(dark)
        val side = size.minDimension
        val corner = center - Offset(side / 2, side / 2)
        val stars = SPLASH_STARS.map { corner + it * side }
        // Each star starts this long after the one before; the last finishes with the draw-in.
        val every = (1f - SPLASH_LIGHT_UP_SPAN) / stars.lastIndex
        for (i in 0 until stars.lastIndex) {
            val joined = easeInOut(grownFraction(progress, every * i + SPLASH_LIGHT_UP_SPAN / 2, every))
            if (joined > 0f) drawLine(Color(p.lineArgb), stars[i], stars[i] + (stars[i + 1] - stars[i]) * joined, side * 0.011f, StrokeCap.Round)
        }
        stars.forEachIndexed { i, at ->
            val lit = easeOutBack(grownFraction(progress, every * i, SPLASH_LIGHT_UP_SPAN))
            if (lit <= 0f) return@forEachIndexed
            val last = i == stars.lastIndex
            drawSparkle(at, side * (if (last) 0.095f else 0.055f) * lit, Color(if (last) p.sparkleArgb else p.starFastArgb))
        }
    }

    override fun DrawScope.drawLegendSample(
        signal: LegendSignal,
        dark: Boolean,
    ) {
        val p = palette(dark)
        when (signal) {
            LegendSignal.TURN_SIGNAL -> drawSparkle(center, size.minDimension * 0.45f, Color(p.sparkleArgb))

            LegendSignal.WIPER -> {
                val meteor = Color(p.meteorArgb)
                val head = Offset(size.width * 0.15f, size.height * 0.8f)
                val tail = Offset(size.width * 0.85f, size.height * 0.2f)
                drawLine(Brush.linearGradient(listOf(meteor, meteor.copy(alpha = 0.1f)), head, tail), head, tail, size.height * 0.14f, StrokeCap.Round)
            }

            LegendSignal.LOW_BEAM ->
                drawRoundRect(
                    Color(p.nebulaArgb),
                    topLeft = Offset(0f, size.height * 0.2f),
                    size = Size(size.width, size.height * 0.6f),
                    cornerRadius = CornerRadius(size.height * 0.3f),
                )
        }
    }

    /** A four-point sparkle: a cross of [reach] with a dot in the middle. */
    private fun DrawScope.drawSparkle(
        at: Offset,
        reach: Float,
        color: Color,
    ) {
        val width = reach * 0.22f
        repeat(2) { i ->
            val armRad = (i * PI / 2).toFloat()
            val arm = Offset(cos(armRad), sin(armRad)) * reach
            drawLine(color, at - arm, at + arm, width, StrokeCap.Round)
        }
        drawCircle(color, reach * 0.3f, at)
    }
}
