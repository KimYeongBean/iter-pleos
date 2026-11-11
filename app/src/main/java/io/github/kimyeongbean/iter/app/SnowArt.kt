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
import io.github.kimyeongbean.iter.art.DayArtShape
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The crystal has six arms; each stands for a sixth of the day's drive time, clockwise from 12. */
private const val ARMS = 6

/** Side branch pairs per arm: each stands for an equal part of the arm's stretch of time. */
private const val BRANCH_PAIRS = 4
private const val THUMBNAIL_BRANCH_PAIRS = 2

/** A twig leaves its arm at this angle, in radians: sixty degrees, as in a snow crystal. */
private const val TWIG_ANGLE_RAD = (PI / 3).toFloat()

/** Where the middle star's twigs leave the arm, and how long they are, in drawing radii. */
private const val STAR_FOOT = 0.2f

/** Turn signal trees stand on a ring of this radius (in drawing radii), this far apart in radians. */
private const val TREE_RING_RADIUS = 0.98f
private const val TREE_GAP_RAD = 0.14f

/** Snowflakes falling over the day art and over the map graph, and snow drifts on the map graph. */
private const val DAY_ART_FLAKES = 14
private const val MAP_GRAPH_FLAKES = 26
private const val PANEL_DRIFTS = 7

/** Half the gap between the two wheel tracks, as a share of the box side. */
private const val TRACK_HALF_GAP = 0.006f

/** A snowflake falls the height of its panel once per this many seconds, give or take. */
private const val SNOWFALL_SECONDS = 9f

/** A gust crosses its stretch of the route once per this many seconds. */
private const val GUST_SECONDS = 2f

/** The far snowman: where it stands along the far hill's left curve, and its size (share of the short side). */
private const val SNOWMAN_ON_RIDGE = 0.72f
private const val SNOWMAN_SIZE = 0.01f

/** Falling snow: one flake per square of this side (dp), falling this fast (dp per second) and up to this much faster. */
private const val FALLING_SNOW_CELL_DP = 110f
private const val FALLING_SNOW_SLOWEST_DP = 18f
private const val FALLING_SNOW_SPREAD_DP = 24f

/** One backdrop speck per square of this side, in dp. */
private const val BACKDROP_CELL_DP = 95f

/** Splash: the arms grow over this first share of the draw-in; a twig takes this share to sprout. */
private const val SPLASH_ARM_SPAN = 0.6f
private const val SPLASH_TWIG_SPAN = 0.4f

/** Splash: twig pairs on each arm, as where they leave the arm to how long they are, in arm lengths. */
private val SPLASH_TWIGS = listOf(0.35f to 0.3f, 0.58f to 0.24f, 0.8f to 0.15f)

/** Splash: the crystal turns this many degrees to its resting place while it grows. */
private const val SPLASH_TURN_DEG = 30f

/**
 * Snow crystal (눈 결정): the day is a six-armed crystal, the route two wheel tracks, on a snow
 * field. Each arm is a sixth of the day, clockwise; arm and side branch length and colour depth are
 * speed. Turn signal: a tree. Wiper: a snow flurry. Low beam: a blue shade under the line. Repeating effect: snow falls slowly.
 */
internal object SnowArt : ThemeArt {
    override val lightColors =
        IterColors(
            backgroundArgb = 0xFFE6EEF6.toInt(),
            cardArgb = 0xFFF6FAFD.toInt(),
            cardHighArgb = 0xFFD8E4F0.toInt(),
            controlArgb = 0xFFC6D6E6.toInt(),
            textArgb = 0xFF10243D.toInt(),
            textSecondaryArgb = 0xFF3F566F.toInt(),
            outlineArgb = 0xFF7F96AE.toInt(),
            primaryArgb = 0xFF1B4A8A.toInt(),
            onPrimaryArgb = 0xFFFFFFFF.toInt(),
        )

    override val darkColors =
        IterColors(
            backgroundArgb = 0xFF081220.toInt(),
            cardArgb = 0xFF0F1D30.toInt(),
            cardHighArgb = 0xFF1A2C44.toInt(),
            controlArgb = 0xFF27405E.toInt(),
            textArgb = 0xFFEAF3FC.toInt(),
            textSecondaryArgb = 0xFFB4C6DA.toInt(),
            outlineArgb = 0xFF56708C.toInt(),
            primaryArgb = 0xFFEAF3FC.toInt(),
            onPrimaryArgb = 0xFF081220.toInt(),
        )

    private class Palette(
        val colors: IterColors,
        /** The crystal's outline and its inner highlight, at standstill and at full speed. */
        val outerSlowArgb: Int,
        val outerFastArgb: Int,
        val innerSlowArgb: Int,
        val innerFastArgb: Int,
        val trackSlowArgb: Int,
        val trackFastArgb: Int,
        val treeArgb: Int,
        val flurryArgb: Int,
        val shadeArgb: Int,
        val snowfallArgb: Int,
        val backdropSpeckArgbs: List<Int>,
        /** Snow hills, far and near, the line along their tops, and the far snowman. */
        val hillFarArgb: Int,
        val hillNearArgb: Int,
        val ridgeArgb: Int,
        val snowmanArgb: Int,
        val snowmanLineArgb: Int,
    )

    private val light =
        Palette(
            colors = lightColors,
            outerSlowArgb = 0xFF6FB4EC.toInt(),
            outerFastArgb = 0xFF1B4A8A.toInt(),
            innerSlowArgb = 0xFFD6F0FD.toInt(),
            innerFastArgb = 0xFF8FC4F2.toInt(),
            trackSlowArgb = 0xFFB5C8DE.toInt(),
            trackFastArgb = 0xFF1B4A8A.toInt(),
            treeArgb = 0xFF3E6B73.toInt(),
            flurryArgb = 0xFF3F6FA8.toInt(),
            shadeArgb = 0xFFB7CADF.toInt(),
            snowfallArgb = 0xFFC6D6E6.toInt(),
            backdropSpeckArgbs = listOf(0xFFDAE5F0.toInt(), 0xFFD3E0EC.toInt()),
            hillFarArgb = 0xFFF3F7FB.toInt(),
            hillNearArgb = 0xFFFAFCFE.toInt(),
            ridgeArgb = 0xFFC9D7E6.toInt(),
            snowmanArgb = 0xFFFFFFFF.toInt(),
            snowmanLineArgb = 0xFFBACBDD.toInt(),
        )

    private val dark =
        Palette(
            colors = darkColors,
            outerSlowArgb = 0xFF3F6FA8.toInt(),
            outerFastArgb = 0xFFCFE6FF.toInt(),
            innerSlowArgb = 0xFF7FAEDD.toInt(),
            innerFastArgb = 0xFFFFFFFF.toInt(),
            trackSlowArgb = 0xFF2B4664.toInt(),
            trackFastArgb = 0xFFCFE6FF.toInt(),
            treeArgb = 0xFF7FC8BD.toInt(),
            flurryArgb = 0xFFCFE6FF.toInt(),
            shadeArgb = 0xFF3A5F96.toInt(),
            snowfallArgb = 0xFFB4C6DA.toInt(),
            backdropSpeckArgbs = listOf(0xFF0F1C2D.toInt(), 0xFF122033.toInt()),
            hillFarArgb = 0xFF0E1A2B.toInt(),
            hillNearArgb = 0xFF122136.toInt(),
            ridgeArgb = 0xFF22344D.toInt(),
            snowmanArgb = 0xFF2A3F5A.toInt(),
            snowmanLineArgb = 0xFF34496A.toInt(),
        )

    private fun palette(dark: Boolean) = if (dark) this.dark else light

    override fun panel(dark: Boolean): ThemePanel {
        val p = palette(dark)
        return ThemePanel(
            argb = p.colors.cardArgb,
            textArgb = p.colors.textSecondaryArgb,
            speedColorBaseArgb = p.outerFastArgb,
            effects = MapEffectColors(turnSignalBandArgb = p.treeArgb, lowBeamArgb = p.shadeArgb, rainArgb = p.flurryArgb),
            art = this,
            dark = dark,
        )
    }

    override fun backdropArgbs(dark: Boolean): List<Int> = palette(dark).let {
        it.backdropSpeckArgbs + listOf(it.hillFarArgb, it.hillNearArgb, it.ridgeArgb, it.snowmanArgb, it.snowmanLineArgb)
    }

    /** Soft specks of snow scattered evenly, over snow hills with a snowman far away on the far one. */
    override fun DrawScope.drawBackdrop(dark: Boolean) {
        val p = palette(dark)
        val specks = p.backdropSpeckArgbs.map { Color(it) }
        forEachBackdropSpot(BACKDROP_CELL_DP) { i, at ->
            // Three sizes, so near and far specks read apart.
            drawCircle(specks[i % specks.size], radius = (1.5f + 1.5f * backdropSizeStep(i)) * density, center = at)
        }
        val ridge = Color(p.ridgeArgb).copy(alpha = 0.6f)
        val ridgeLine = Stroke(1.5f * density)
        val far = hill(FarHill)
        drawPath(far, Color(p.hillFarArgb))
        drawPath(far, ridge, style = ridgeLine)
        drawSnowman(p, onRidge(FarHill, SNOWMAN_ON_RIDGE))
        val near = hill(NearHill)
        drawPath(near, Color(p.hillNearArgb))
        drawPath(near, ridge, style = ridgeLine)
    }

    /** A hill: its top is two curves across the screen, given as heights (shares of the screen height). */
    private class Hill(
        val left: Float,
        val c1: Float,
        val c2: Float,
        val middle: Float,
        val c3: Float,
        val c4: Float,
        val right: Float,
    )

    private val FarHill = Hill(0.8f, 0.7f, 0.74f, 0.76f, 0.78f, 0.66f, 0.71f)
    private val NearHill = Hill(0.9f, 0.82f, 0.95f, 0.88f, 0.85f, 0.86f, 0.89f)

    private fun DrawScope.hill(h: Hill): Path {
        val w = size.width
        val y = size.height
        return Path().apply {
            moveTo(0f, h.left * y)
            cubicTo(w * 0.2f, h.c1 * y, w * 0.4f, h.c2 * y, w * 0.55f, h.middle * y)
            cubicTo(w * 0.7f, h.c3 * y, w * 0.85f, h.c4 * y, w, h.right * y)
            lineTo(w, y)
            lineTo(0f, y)
            close()
        }
    }

    /** The point [t] (0 to 1) along the left curve of a hill's top: open ground between the plates. */
    private fun DrawScope.onRidge(
        h: Hill,
        t: Float,
    ): Offset {
        val u = 1 - t

        fun bezier(
            a: Float,
            b: Float,
            c: Float,
            d: Float,
        ) = u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d
        return Offset(bezier(0f, 0.2f, 0.4f, 0.55f) * size.width, bezier(h.left, h.c1, h.c2, h.middle) * size.height)
    }

    /** A tiny snowman standing at [foot]: so small it reads as far away. */
    private fun DrawScope.drawSnowman(
        p: Palette,
        foot: Offset,
    ) {
        val body = Color(p.snowmanArgb)
        val line = Color(p.snowmanLineArgb)
        val r = size.minDimension * SNOWMAN_SIZE
        val outline = Stroke(r * 0.12f)
        val base = foot - Offset(0f, r * 0.85f)
        val middle = base - Offset(0f, r * 1.45f)
        val head = middle - Offset(0f, r * 1.05f)
        // Twig arms.
        drawLine(line, middle + Offset(-r * 0.6f, 0f), middle + Offset(-r * 1.5f, -r * 0.6f), r * 0.14f, StrokeCap.Round)
        drawLine(line, middle + Offset(r * 0.6f, 0f), middle + Offset(r * 1.5f, -r * 0.6f), r * 0.14f, StrokeCap.Round)
        listOf(base to r, middle to r * 0.72f, head to r * 0.5f).forEach { (at, radius) ->
            drawCircle(body, radius, at)
            drawCircle(line, radius, at, style = outline)
        }
        // A little hat.
        drawRect(line, head + Offset(-r * 0.32f, -r * 0.95f), Size(r * 0.64f, r * 0.55f))
        drawLine(line, head + Offset(-r * 0.5f, -r * 0.42f), head + Offset(r * 0.5f, -r * 0.42f), r * 0.12f)
    }

    override val backdropMoves: Boolean get() = true

    /** Snow falling slowly, each flake at its own pace and swaying a little. */
    override fun DrawScope.drawBackdropMotion(
        dark: Boolean,
        seconds: Float,
    ) {
        val flake = Color(palette(dark).snowfallArgb).copy(alpha = 0.7f)
        val margin = 20f * density
        val fall = size.height + margin * 2
        forEachBackdropSpot(FALLING_SNOW_CELL_DP) { i, at ->
            val speed = (FALLING_SNOW_SLOWEST_DP + FALLING_SNOW_SPREAD_DP * scatter(i, 71)) * density
            val y = (at.y + seconds * speed) % fall - margin
            val sway = sin(seconds * 0.7f + i) * 8f * density
            drawCircle(flake, (1f + 1.8f * scatter(i, 74)) * density, Offset(at.x + sway, y))
        }
    }

    /**
     * One line of the crystal, in drawing radii from the centre. An arm ([isArm]) grows out along
     * itself; a twig appears whole once its arm has grown [sproutAt] (0 to 1) of its length.
     */
    private class CrystalLine(
        val arm: Int,
        val isArm: Boolean,
        val sproutAt: Float,
        val from: Offset,
        val to: Offset,
        val widthScale: Float,
        val speedIntensity: Float,
        val lowBeam: Boolean,
    )

    /** A turn signal tree or a wiper speck by the crystal, in drawing radii from the centre. */
    private class CrystalMark(
        val timePosition: Float,
        val at: Offset,
        val tree: Boolean,
    )

    private class Crystal(
        val lines: List<CrystalLine>,
        val marks: List<CrystalMark>,
    )

    private val bigCrystals = LastComputed<DayArtData, Crystal> { crystal(it, thumbnail = false) }
    private val thumbnailCrystals = LastComputed<DayArtData, Crystal> { crystal(it, thumbnail = true) }

    /** The crystal of a day art with at least one shape, worked out once so drawing only draws. */
    private fun crystal(
        data: DayArtData,
        thumbnail: Boolean,
    ): Crystal {
        val shapes = data.shapes
        val pairs = if (thumbnail) THUMBNAIL_BRANCH_PAIRS else BRANCH_PAIRS
        val lines = mutableListOf<CrystalLine>()
        val marks = mutableListOf<CrystalMark>()
        for (arm in 0 until ARMS) {
            // The arm's sixth of the day; at least one shape, so a short day still has six arms.
            val first = arm * shapes.size / ARMS
            val end = maxOf(first + 1, (arm + 1) * shapes.size / ARMS)
            val armRad = (-PI / 2 + arm * 2 * PI / ARMS).toFloat()
            val along = Offset(cos(armRad), sin(armRad))
            val intensity = meanIntensity(shapes, first, end)
            val length = 0.42f + 0.5f * intensity.coerceAtLeast(0.08f)
            lines += CrystalLine(arm, isArm = true, sproutAt = 0f, Offset.Zero, along * length, 1f, intensity, lowBeam = false)
            // The star in the middle: neighbouring arms' first twigs meet between the arms.
            if (!thumbnail) lines.addTwigs(arm, armRad, STAR_FOOT, length, STAR_FOOT, 0.6f, intensity, lowBeam = false)
            for (pair in 0 until pairs) {
                val from = first + pair * (end - first) / pairs
                val to = maxOf(from + 1, first + (pair + 1) * (end - first) / pairs).coerceAtMost(end)
                val part = shapes.subList(from, to)
                val foot = length * (0.46f + 0.42f * pair / (pairs - 1).coerceAtLeast(1))
                val pairIntensity = meanIntensity(shapes, from, to)
                val reach = minOf((0.09f + 0.2f * pairIntensity) * (1 - 0.28f * pair / pairs), foot * 0.62f)
                lines.addTwigs(arm, armRad, foot, length, reach, 0.8f, pairIntensity, part.any { DayArtPattern.LOW_BEAM in it.patterns })
                if (thumbnail) continue
                val timePosition = (arm + foot / length) / ARMS
                if (part.any { DayArtPattern.TURN_SIGNAL in it.patterns }) {
                    // Trees stand round the crystal, the arm's pairs side by side.
                    val treeRad = armRad + (pair - (pairs - 1) / 2f) * TREE_GAP_RAD
                    marks += CrystalMark(timePosition, Offset(cos(treeRad), sin(treeRad)) * TREE_RING_RADIUS, tree = true)
                }
                if (part.any { DayArtPattern.WIPER in it.patterns }) {
                    for (side in -1..1 step 2) {
                        val twigRad = armRad + side * TWIG_ANGLE_RAD
                        marks += CrystalMark(timePosition, along * foot + Offset(cos(twigRad), sin(twigRad)) * (reach + 0.08f), tree = false)
                    }
                }
            }
        }
        return Crystal(lines, marks)
    }

    /** Adds the pair of twigs that leave the arm at [armRad] at [foot], each [reach] long. */
    private fun MutableList<CrystalLine>.addTwigs(
        arm: Int,
        armRad: Float,
        foot: Float,
        armLength: Float,
        reach: Float,
        widthScale: Float,
        speedIntensity: Float,
        lowBeam: Boolean,
    ) {
        val base = Offset(cos(armRad), sin(armRad)) * foot
        for (side in -1..1 step 2) {
            val twigRad = armRad + side * TWIG_ANGLE_RAD
            val tip = base + Offset(cos(twigRad), sin(twigRad)) * reach
            add(CrystalLine(arm, isArm = false, sproutAt = foot / armLength, base, tip, widthScale, speedIntensity, lowBeam))
        }
    }

    /** Mean speed intensity of [shapes] from index [from] (inclusive) to [to] (exclusive). */
    private fun meanIntensity(
        shapes: List<DayArtShape>,
        from: Int,
        to: Int,
    ): Float {
        var sum = 0f
        for (i in from until to) sum += shapes[i].speedIntensity
        return sum / (to - from)
    }

    /**
     * Calls [draw] for every line of [crystal] grown up to [grown] (0 to 1, drive time): each arm
     * grows out from the centre while the draw-in crosses its sixth of the day, and a twig sprouts
     * once the arm has reached its foot.
     */
    private inline fun DrawScope.forEachGrownLine(
        crystal: Crystal,
        radius: Float,
        grown: Float,
        draw: (from: Offset, to: Offset, line: CrystalLine) -> Unit,
    ) {
        crystal.lines.forEach { line ->
            val armGrown = grownFraction(grown, line.arm.toFloat() / ARMS, 1f / ARMS)
            if (armGrown <= 0f || armGrown < line.sproutAt) return@forEach
            draw(center + line.from * radius, center + line.to * (radius * if (line.isArm) armGrown else 1f), line)
        }
    }

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
        val crystal = (if (thumbnail) thumbnailCrystals else bigCrystals)[data]
        val armWidth = (radius * if (thumbnail) 0.12f else 0.07f).coerceAtLeast(2f)
        if (!thumbnail) drawSnowfall(Color(p.snowfallArgb), loopSeconds, DAY_ART_FLAKES)

        // Faint whole crystal first, then what has grown: shade under, outlines, highlights on top.
        if (progress < 1f) {
            forEachGrownLine(crystal, radius, grown = 1f) { from, to, line ->
                val outer = mixArgb(p.outerSlowArgb, p.outerFastArgb, line.speedIntensity)
                drawLine(outer, from, to, armWidth * line.widthScale, StrokeCap.Round, alpha = DRAW_IN_GHOST_ALPHA)
            }
        }
        val shade = Color(p.shadeArgb)
        forEachGrownLine(crystal, radius, progress) { from, to, line ->
            if (line.lowBeam) drawLine(shade, from, to, armWidth * line.widthScale * 2.4f, StrokeCap.Round)
        }
        forEachGrownLine(crystal, radius, progress) { from, to, line ->
            drawLine(mixArgb(p.outerSlowArgb, p.outerFastArgb, line.speedIntensity), from, to, armWidth * line.widthScale, StrokeCap.Round)
        }
        forEachGrownLine(crystal, radius, progress) { from, to, line ->
            drawLine(mixArgb(p.innerSlowArgb, p.innerFastArgb, line.speedIntensity), from, to, armWidth * line.widthScale * 0.42f, StrokeCap.Round)
        }
        drawCircle(Color(p.outerFastArgb), armWidth * 0.85f)
        drawCircle(Color(p.colors.cardArgb), armWidth * 0.45f)

        val path = Path()
        crystal.marks.forEach { mark ->
            if (progress < 1f && progress < mark.timePosition) return@forEach
            val at = center + mark.at * radius
            if (mark.tree) drawTree(path, at, radius * 0.07f, Color(p.treeArgb)) else drawCircle(Color(p.flurryArgb), armWidth * 0.26f, at)
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
        val side = size.minDimension

        // Snow drifts in the panel's own soft shade, under everything.
        val drift = Color(p.colors.cardHighArgb)
        repeat(PANEL_DRIFTS) { i ->
            val drifts = Size(side * (0.25f + 0.3f * scatter(i, 23)), side * (0.05f + 0.05f * scatter(i, 24)))
            drawOval(drift, Offset(scatter(i, 21) * size.width - drifts.width / 2, scatter(i, 22) * size.height), drifts, alpha = 0.5f)
        }

        // The blue shade under stretches with the low beam on.
        val shade = Color(p.shadeArgb)
        route.forEachSegmentTouching({ it.lowBeamOn }) { a, b -> drawSegment(a, b, progress, frame, shade, side * 0.07f) }

        // A flurry where the wiper was on: short streaks blown sideways, each on its own beat.
        val flurry = Color(p.flurryArgb)
        route.rainDrops.forEach { drop ->
            if (progress < drop.timePosition) return@forEach
            val gust = (loopSeconds / GUST_SECONDS + drop.fallOffset) % 1f
            val at = frame.at(drop.x, drop.y) + Offset(side * 0.09f, side * 0.025f) * (gust - 0.5f)
            drawLine(flurry, at, at + Offset(side * 0.016f, side * 0.006f), side * 0.004f, StrokeCap.Round, alpha = RAIN_ALPHA * rainAlpha(gust))
        }

        val trackWidth = side * 0.0045f
        val path = Path()
        route.strokes.forEach { stroke ->
            // Two wheel tracks, one either side of the route.
            // A stretch of one sample has no tracks to draw: it is a dot.
            if (stroke.size == 1 && progress >= stroke[0].timePosition) {
                drawCircle(mixArgb(p.trackSlowArgb, p.trackFastArgb, stroke[0].speedIntensity), trackWidth, frame.at(stroke[0]))
            }
            for (i in 1..stroke.lastIndex) {
                val a = stroke[i - 1]
                val b = stroke[i]
                val drawn = segmentDrawnFraction(a, b, progress)
                if (drawn <= 0f) continue
                val from = frame.at(a)
                val whole = frame.at(b) - from
                val length = hypot(whole.x, whole.y)
                if (length == 0f) continue
                val across = Offset(-whole.y, whole.x) / length * (side * TRACK_HALF_GAP)
                val to = from + whole * drawn
                val color = mixArgb(p.trackSlowArgb, p.trackFastArgb, b.speedIntensity)
                drawLine(color, from + across, to + across, trackWidth, StrokeCap.Round)
                drawLine(color, from - across, to - across, trackWidth, StrokeCap.Round)
            }
            // A tree beside the tracks where the turn signal comes on.
            stroke.forEachTurnSignalStart { i, point ->
                if (progress < point.timePosition) return@forEachTurnSignalStart
                val at = frame.at(point)
                val next = frame.at(stroke[minOf(i + 1, stroke.lastIndex)])
                val besideRad = atan2(next.y - at.y, next.x - at.x) + (PI / 2).toFloat()
                drawTree(path, at + Offset(cos(besideRad), sin(besideRad)) * (side * 0.035f), side * 0.028f, Color(p.treeArgb))
            }
        }

        val first = route.strokes.firstOrNull()?.firstOrNull() ?: return
        val last = route.strokes.lastOrNull()?.lastOrNull() ?: return
        drawCircle(Color(p.trackFastArgb), side * 0.012f, frame.at(first))
        if (progress >= 1f) {
            drawCircle(Color(p.trackFastArgb), side * 0.016f, frame.at(last))
            drawCircle(Color(p.colors.cardArgb), side * 0.009f, frame.at(last))
        }
        drawSnowfall(Color(p.snowfallArgb), loopSeconds, MAP_GRAPH_FLAKES)
    }

    /** A crystal grows from the middle: six arms run out, twigs sprout along them, and it turns to rest. */
    override fun DrawScope.drawSplash(
        dark: Boolean,
        progress: Float,
    ) {
        val p = palette(dark)
        val side = size.minDimension
        val armLength = side * 0.44f
        val armWidth = side * 0.026f
        val armGrown = easeInOut(grownFraction(progress, 0f, SPLASH_ARM_SPAN))
        rotate(degrees = -SPLASH_TURN_DEG * (1 - easeInOut(progress))) {
            // Outlines first, highlights on top.
            listOf(Color(p.outerFastArgb) to armWidth, Color(p.innerFastArgb) to armWidth * 0.42f).forEach { (color, width) ->
                for (arm in 0 until ARMS) {
                    val armRad = (-PI / 2 + arm * 2 * PI / ARMS).toFloat()
                    val along = Offset(cos(armRad), sin(armRad))
                    drawLine(color, center, center + along * (armLength * armGrown), width, StrokeCap.Round)
                    SPLASH_TWIGS.forEach { (foot, reach) ->
                        // A twig sprouts about when the arm reaches its foot.
                        val sprouted = easeOutBack(grownFraction(progress, SPLASH_ARM_SPAN * foot, SPLASH_TWIG_SPAN))
                        if (sprouted <= 0f) return@forEach
                        val base = center + along * (armLength * foot)
                        for (twigSide in -1..1 step 2) {
                            val twigRad = armRad + twigSide * TWIG_ANGLE_RAD
                            drawLine(color, base, base + Offset(cos(twigRad), sin(twigRad)) * (armLength * reach * sprouted), width * 0.8f, StrokeCap.Round)
                        }
                    }
                }
            }
        }
        drawCircle(Color(p.outerFastArgb), armWidth * 0.85f)
        drawCircle(Color(p.colors.backgroundArgb), armWidth * 0.45f)
    }

    override fun DrawScope.drawLegendSample(
        signal: LegendSignal,
        dark: Boolean,
    ) {
        val p = palette(dark)
        when (signal) {
            LegendSignal.TURN_SIGNAL -> drawTree(Path(), Offset(center.x, size.height * 0.95f), size.height * 0.9f, Color(p.treeArgb))

            LegendSignal.WIPER ->
                listOf(Offset(0.1f, 0.25f), Offset(0.45f, 0.5f), Offset(0.2f, 0.75f)).forEach { at ->
                    val from = Offset(size.width * at.x, size.height * at.y)
                    drawLine(Color(p.flurryArgb), from, from + Offset(size.width * 0.4f, size.height * 0.1f), size.height * 0.13f, StrokeCap.Round)
                }

            LegendSignal.LOW_BEAM ->
                drawRoundRect(
                    Color(p.shadeArgb),
                    topLeft = Offset(0f, size.height * 0.2f),
                    size = Size(size.width, size.height * 0.6f),
                    cornerRadius = CornerRadius(size.height * 0.3f),
                )
        }
    }

    /**
     * Snowflakes drifting down over the whole draw scope as [loopSeconds] advances, each at its own
     * pace; they stand still where they are when it does not.
     */
    private fun DrawScope.drawSnowfall(
        color: Color,
        loopSeconds: Float,
        flakes: Int,
    ) {
        repeat(flakes) { i ->
            val pace = 0.6f + 0.8f * scatter(i, 33)
            val fallen = (scatter(i, 32) + loopSeconds / SNOWFALL_SECONDS * pace) % 1f
            val sway = sin(loopSeconds * 0.7f + i) * size.minDimension * 0.008f
            drawCircle(color, size.minDimension * (0.003f + 0.004f * scatter(i, 34)), Offset(scatter(i, 31) * size.width + sway, fallen * size.height))
        }
    }

    /** A small fir with its foot at [foot], [height] tall. */
    private fun DrawScope.drawTree(
        path: Path,
        foot: Offset,
        height: Float,
        color: Color,
    ) {
        path.reset()
        path.moveTo(foot.x, foot.y - height)
        path.lineTo(foot.x + height * 0.42f, foot.y - height * 0.2f)
        path.lineTo(foot.x - height * 0.42f, foot.y - height * 0.2f)
        path.close()
        drawPath(path, color)
        drawLine(color, foot, foot + Offset(0f, -height * 0.25f), height * 0.14f)
    }
}
