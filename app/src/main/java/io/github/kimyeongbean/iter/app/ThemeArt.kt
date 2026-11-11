package io.github.kimyeongbean.iter.app

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import io.github.kimyeongbean.iter.art.DayArtData
import io.github.kimyeongbean.iter.art.DayArtShape

/** Vehicle signals an advanced theme shows in its own way; the legends draw a sample of each. */
internal enum class LegendSignal(
    @StringRes val sectionLabel: Int,
) {
    TURN_SIGNAL(R.string.legend_turn_signal_section),
    WIPER(R.string.legend_wiper_section),
    LOW_BEAM(R.string.legend_low_beam_section),
}

/**
 * How an advanced theme (GLOSSARY: 고급 테마) looks: its app colours for the light and the dark
 * system theme, its still backdrop pattern (GLOSSARY: 바탕 무늬) and its own way of drawing the day
 * art and the map graph. Implementations only draw: every position comes from the drawing data, the
 * route or a fixed seed, so the same day record always gives the same picture.
 *
 * `progress` is the draw-in (0 to 1, drive time). `loopSeconds` drives the repeating effect
 * (GLOSSARY: 반복 연출): seconds since it started, 0 when it does not play.
 */
internal interface ThemeArt {
    val lightColors: IterColors
    val darkColors: IterColors

    /** What the art panels draw with; its [ThemePanel.art] is this. */
    fun panel(dark: Boolean): ThemePanel

    /** Every colour of the backdrop pattern, so tests can check that text reads on each. */
    fun backdropArgbs(dark: Boolean): List<Int>

    /** The ring on today's grass cell, when the theme has its own; null uses the line colour. Not for text. */
    fun todayMarkArgb(dark: Boolean): Int? = null

    /** The theme's own soft light at the screen edges under the glass plates; null makes one from its colours. */
    fun glow(dark: Boolean): GlassGlow? = null

    /** The backdrop pattern over the whole app background. Never moves. */
    fun DrawScope.drawBackdrop(dark: Boolean)

    /** Whether the theme has a backdrop motion (GLOSSARY: 바탕 연출) over its backdrop pattern. */
    val backdropMoves: Boolean get() = false

    /**
     * The backdrop motion at [seconds] since it started: slow, sparse movement over the backdrop
     * pattern, such as falling snow. Never on the "기록 중" screen, nor with system animations off.
     */
    fun DrawScope.drawBackdropMotion(
        dark: Boolean,
        seconds: Float,
    ) {}

    fun DrawScope.drawDayArt(
        data: DayArtData,
        dark: Boolean,
        progress: Float,
        loopSeconds: Float,
    )

    fun DrawScope.drawMapGraph(
        route: MapRoute,
        dark: Boolean,
        progress: Float,
        loopSeconds: Float,
    )

    /**
     * The theme's splash mark (GLOSSARY: 스플래시), filling the draw scope. It draws in as
     * [progress] goes 0 to 1 and is whole at 1.
     */
    fun DrawScope.drawSplash(
        dark: Boolean,
        progress: Float,
    )

    /** A small sample of how [signal] shows, filling the draw scope. */
    fun DrawScope.drawLegendSample(
        signal: LegendSignal,
        dark: Boolean,
    )
}

/** Day art narrower than this is a thumbnail: themes leave out fine detail there. */
private const val THUMBNAIL_MAX_DP = 200f

/** Whether the draw scope is a thumbnail (a grass cell), not the big day art. */
internal val DrawScope.isThumbnail: Boolean get() = size.minDimension < THUMBNAIL_MAX_DP * density

/** How fast the stretch was, 0 at standstill to 1 at the speed colour's strongest speed. */
internal val DayArtShape.speedIntensity: Float
    get() {
        val span = DayArtData.SPEED_BAR_MAX_RADIUS - DayArtData.SPEED_BAR_INNER_RADIUS
        return ((outerRadius - DayArtData.SPEED_BAR_INNER_RADIUS) / span).toFloat().coerceIn(0f, 1f)
    }

/**
 * How far a part that starts at [timePosition] (0 to 1, drive time) and takes [span] of the draw-in
 * has come at [progress], 0 to 1. Whatever its start, it is complete once the draw-in is.
 */
internal fun grownFraction(
    progress: Float,
    timePosition: Float,
    span: Float,
): Float = if (progress >= 1f) 1f else ((progress - timePosition) / span).coerceIn(0f, 1f)

/** Halvings [easeInOut] makes to find its place on the curve: far finer than a pixel. */
private const val EASE_HALVINGS = 20

/** One coordinate of a cubic Bezier curve from 0 to 1 with control values [c1] and [c2], [t] along it. */
private fun bezier(
    c1: Float,
    c2: Float,
    t: Float,
): Float {
    val u = 1 - t
    return 3 * u * u * t * c1 + 3 * u * t * t * c2 + t * t * t
}

/**
 * [fraction] (0 to 1) eased along the Material standard curve (0.4, 0, 0.2, 1), the one
 * `FastOutSlowInEasing` follows: a quick start and a long soft stop, 0 at 0 and 1 at 1. Found by
 * halving, so it is safe to call while drawing at any fraction: Compose's cubic easings can throw
 * near the ends (#115).
 */
internal fun easeInOut(fraction: Float): Float {
    if (!(fraction > 0f)) return 0f
    if (fraction >= 1f) return 1f
    var low = 0f
    var high = 1f
    repeat(EASE_HALVINGS) {
        val middle = (low + high) / 2
        if (bezier(0.4f, 0.2f, middle) < fraction) low = middle else high = middle
    }
    return bezier(0f, 1f, (low + high) / 2)
}

/** The back easing's constant: this one makes [easeOutBack] run about a tenth past 1. */
private const val BACK_OVERSHOOT = 1.70158f

/**
 * [fraction] (0 to 1) eased so the value runs a little past 1 and settles back: 0 at 0, 1 at 1.
 * Plain arithmetic, so it too is safe while drawing.
 */
internal fun easeOutBack(fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f) - 1
    return 1 + (BACK_OVERSHOOT + 1) * t * t * t + BACK_OVERSHOOT * t * t
}

/**
 * What [compute] made of the input last asked for, kept so a repeating effect only draws: the same
 * day art or route (the same object) is computed once, not every frame. Used on the main thread only.
 */
internal class LastComputed<K : Any, V : Any>(
    private val compute: (K) -> V,
) {
    private var key: K? = null
    private var value: V? = null

    operator fun get(input: K): V {
        val kept = value
        if (kept != null && key === input) return kept
        return compute(input).also {
            key = input
            value = it
        }
    }
}

/** The colour [fraction] of the way from [fromArgb] to [toArgb]. */
internal fun mixArgb(
    fromArgb: Int,
    toArgb: Int,
    fraction: Float,
): Color = lerp(Color(fromArgb), Color(toArgb), fraction.coerceIn(0f, 1f))

/**
 * A fixed number from 0 to 1 for [index] and [salt]: scatters backdrop marks and offsets without
 * a random source, so every frame and every run draws the same.
 */
internal fun scatter(
    index: Int,
    salt: Int,
): Float {
    var h = index * 374_761_393 + salt * 668_265_263
    h = (h xor (h ushr 13)) * 1_274_126_177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16_777_216f
}

/**
 * Calls [mark] for each spot of a backdrop pattern: one per square of [cellDp] a side on average,
 * scattered evenly and always at the same places.
 */
internal inline fun DrawScope.forEachBackdropSpot(
    cellDp: Float,
    mark: (index: Int, at: Offset) -> Unit,
) {
    val cell = cellDp * density
    val count = (size.width * size.height / (cell * cell)).toInt()
    repeat(count) { i -> mark(i, Offset(scatter(i, 1) * size.width, scatter(i, 2) * size.height)) }
}

/**
 * Which of three sizes backdrop mark [index] is: 0 (far, most), 1 or 2 (near, fewest). Fixed per
 * mark, so every frame draws the same.
 */
internal fun backdropSizeStep(index: Int): Int {
    val s = scatter(index, 5)
    return when {
        s < 0.55f -> 0
        s < 0.85f -> 1
        else -> 2
    }
}

/** Calls [action] for each point of the stroke where the turn signal comes on. */
internal inline fun List<RoutePoint>.forEachTurnSignalStart(action: (index: Int, point: RoutePoint) -> Unit) {
    forEachIndexed { i, point ->
        if (point.turnSignalOn && (i == 0 || !this[i - 1].turnSignalOn)) action(i, point)
    }
}

/**
 * Seconds since the repeating effect started, advancing each frame while [active]; 0 when it is not.
 * It stops when the caller leaves the composition (another screen, or "기록 중" on gear D) and
 * while the app is in the background (Compose pauses its frame clock when the window stops).
 */
@Composable
internal fun rememberLoopSeconds(active: Boolean): FloatState {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) {
            seconds.floatValue = 0f
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nanos -> seconds.floatValue = (nanos - startNanos) / 1_000_000_000f }
        }
    }
    return seconds
}
