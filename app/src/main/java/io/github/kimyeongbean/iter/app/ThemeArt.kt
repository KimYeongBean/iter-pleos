package io.github.kimyeongbean.iter.app

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import io.github.kimyeongbean.iter.art.DayArtData
import io.github.kimyeongbean.iter.art.DayArtShape

/** Vehicle signals an advanced theme shows in its own way; the legends draw a sample of each. */
internal enum class LegendSignal(
    @StringRes val label: Int,
    @StringRes val sectionLabel: Int,
) {
    TURN_SIGNAL(R.string.legend_turn_signal, R.string.legend_turn_signal_section),
    WIPER(R.string.legend_wiper, R.string.legend_wiper_section),
    LOW_BEAM(R.string.legend_low_beam, R.string.legend_low_beam_section),
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

    /** The backdrop pattern over the whole app background. Never moves. */
    fun DrawScope.drawBackdrop(dark: Boolean)

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
