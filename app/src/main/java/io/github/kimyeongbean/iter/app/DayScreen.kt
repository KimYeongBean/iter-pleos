package io.github.kimyeongbean.iter.app

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.kimyeongbean.iter.art.DayArtData
import io.github.kimyeongbean.iter.art.SPEED_COLOR_MAX_KMH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** Day art draw-in on first open: drive time order, within 1.2 s (Notion 디자인 '애니메이션·전환 기준'). */
private const val DAY_ART_DRAW_IN_MS = 1_200

/** Map graph draw-in on first open: the line from the start point, within 1.5 s (same Notion page). */
private const val MAP_GRAPH_DRAW_IN_MS = 1_500

/** Grass cell → day art: the thumbnail grows into the big day art, 350 ms, decelerating. */
private const val GROW_MS = 350

/** From this width the pillar sits left of the map graph (Landscape); narrower, they stack and scroll (Portrait). */
private val WIDE_MIN_WIDTH = 1000.dp

/** Width of the glass pillar in the wide layout (시안 B). */
private val PILLAR_WIDTH = 560.dp

/** Height the speed strip and the legend under the day art keep in the pillar, however short the window. */
private val DAY_INFO_MIN_HEIGHT = 200.dp

/** Space between the parts of the pillar. */
private val PILLAR_GAP = 32.dp

/** Legend sample size: wide enough for a band or rain. */
private val SAMPLE_SIZE = DpSize(40.dp, 24.dp)

/** The start and end time chips sit this far from their point on the route. */
private val CHIP_GAP = 20.dp
private val CHIP_HEIGHT = 52.dp

@OptIn(ExperimentalSharedTransitionApi::class)
private val DayArtGrowTransform = BoundsTransform { _, _ -> tween(GROW_MS, easing = LinearOutSlowInEasing) }

/** This date's grass thumbnail and big day art are one shared element. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.dayArtSharedElement(
    date: LocalDate,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier = with(sharedTransitionScope) {
    sharedElement(
        rememberSharedContentState("day-art-$date"),
        animatedVisibilityScope,
        boundsTransform = DayArtGrowTransform,
    )
}

/**
 * The day screen (그날 화면) of [date] on glass (시안 B): the map graph over the whole screen on
 * [panel], and a glass pillar with the date, the drive summary line, the day art in a round window,
 * the speed strip and one legend. The route's first start and last end get a small glass chip with
 * the time once the map graph is drawn. [accent] is the theme's line colour for the app background.
 * [dayArt] and [route] are null while loading; [summary] is null while loading or when the date has
 * no day record.
 * When [playDrawIn], the full day art first grows out of the grass cell, then both draw in drive
 * time order once their data is ready. A tap anywhere skips both to the end. [onDrawInStarted] is
 * called on open, so the draw-in counts as played even when skipped or left early.
 * When [animateLoop], the repeating effects (the rain, an advanced theme's own) play over and over;
 * a tap does not stop them.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun DayScreen(
    date: LocalDate,
    dayArt: DayArtData?,
    route: MapRoute?,
    summary: DriveSummary?,
    panel: ThemePanel,
    accent: Color,
    playDrawIn: Boolean,
    animateLoop: Boolean,
    onDrawInStarted: () -> Unit,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    LaunchedEffect(Unit) { if (playDrawIn) onDrawInStarted() }
    // The day art shows complete while it grows out of the grass cell; the map graph starts empty.
    val artDrawIn = rememberDrawIn(playDrawIn, startComplete = true, ready = dayArt != null, DAY_ART_DRAW_IN_MS)
    val routeDrawIn = rememberDrawIn(playDrawIn, startComplete = false, ready = route != null, MAP_GRAPH_DRAW_IN_MS)
    val routeDrawn by remember { derivedStateOf { routeDrawIn.progress.value >= 1f } }
    val scope = rememberCoroutineScope()
    val dateText = remember(date) { monthDayWeekdayText(date) }
    val describedDate = remember(date) { date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
    val artDescription = stringResource(R.string.day_art_description, describedDate)
    val mapDescription = stringResource(R.string.map_graph_description, describedDate)
    val plain = panel.art == null
    // A plain theme's map graph and day art sit on its panel colour; an advanced theme's on the scene.
    val groundText = if (plain) Color(panel.textArgb) else MaterialTheme.colorScheme.onSurface

    val map: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.semantics { contentDescription = mapDescription }, contentAlignment = Alignment.Center) {
            when {
                route == null -> Unit
                route.strokes.isEmpty() -> Text(stringResource(R.string.map_graph_no_route), color = groundText)
                else -> MapGraph(route, panel, Modifier.fillMaxSize(), animateLoop) { routeDrawIn.progress.value }
            }
        }
    }
    val window: @Composable (Modifier) -> Unit = { modifier ->
        val ground = if (plain) Modifier.clip(CircleShape).background(Color(panel.argb)) else Modifier.glass(CircleShape, GlassLook.Window)
        Box(
            modifier
                .dayArtSharedElement(date, sharedTransitionScope, animatedVisibilityScope)
                .then(ground)
                .semantics { contentDescription = artDescription },
            contentAlignment = Alignment.Center,
        ) {
            when {
                dayArt == null -> Unit
                dayArt.shapes.isEmpty() -> Text(stringResource(R.string.day_art_empty), color = groundText)
                else -> DayArt(dayArt, panel, Modifier.fillMaxSize(), animateLoop) { artDrawIn.progress.value }
            }
        }
    }
    val header: @Composable () -> Unit = {
        DayHeader(dateText, summary, onBack)
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures {
                artDrawIn.skip(scope)
                routeDrawIn.skip(scope)
            }
        },
    ) {
        if (maxWidth >= WIDE_MIN_WIDTH) {
            val mapStart = SCREEN_MARGIN_WIDE + PILLAR_WIDTH + SCREEN_MARGIN_WIDE
            val mapArea =
                DpArea(
                    left = mapStart,
                    top = SCREEN_MARGIN_WIDE,
                    width = maxWidth - mapStart - SCREEN_MARGIN_WIDE,
                    height = maxHeight - SCREEN_MARGIN_WIDE * 2,
                )
            GlassScene(
                accent = accent,
                art = panel.art,
                moving = animateLoop,
                backdrop = {
                    if (plain) Box(Modifier.fillMaxSize().background(Color(panel.argb)))
                    map(Modifier.padding(start = mapArea.left, top = mapArea.top).size(mapArea.width, mapArea.height))
                },
            ) {
                Column(
                    Modifier
                        .padding(SCREEN_MARGIN_WIDE)
                        .width(PILLAR_WIDTH)
                        .fillMaxHeight()
                        .glass(PlateShape)
                        .padding(36.dp),
                ) {
                    header()
                    Spacer(Modifier.height(PILLAR_GAP))
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        // A circle as wide as the pillar, but in a short window it shrinks so the
                        // strip and the legend below keep their height.
                        val side = minOf(maxWidth, maxHeight - DAY_INFO_MIN_HEIGHT - PILLAR_GAP).coerceAtLeast(0.dp)
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            window(Modifier.size(side))
                            DayLegend(panel)
                        }
                    }
                }
                if (route != null && route.strokes.isNotEmpty() && summary != null && routeDrawn) {
                    RouteTimeChips(route, summary, mapArea)
                }
            }
        } else {
            GlassScene(accent = accent, art = panel.art, moving = animateLoop) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SCREEN_MARGIN_NARROW),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val shape = RoundedCornerShape(36.dp)
                    Column(
                        Modifier.fillMaxWidth().glass(shape).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        header()
                        window(Modifier.fillMaxWidth().aspectRatio(1f))
                        DayLegend(panel)
                    }
                    val mapGround = if (plain) Modifier.clip(shape).background(Color(panel.argb)) else Modifier.glass(shape, GlassLook.Window)
                    map(Modifier.fillMaxWidth().aspectRatio(1f).then(mapGround))
                }
            }
        }
    }
}

/** A rectangle in dp: where the map graph is drawn on the screen. */
private class DpArea(
    val left: Dp,
    val top: Dp,
    val width: Dp,
    val height: Dp,
)

/** Back button, the date and the drive summary line. */
@Composable
private fun DayHeader(
    dateText: String,
    summary: DriveSummary?,
    onBack: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        BackButton(onBack, IterButtonSize.MEDIUM.height)
        Column {
            Text(dateText, style = MaterialTheme.typography.titleLarge)
            if (summary != null) {
                Text(
                    stringResource(R.string.drive_summary, summary.sessionCount, summary.timeSpanText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The one legend of the day screen: the speed strip from 0 to 120 km/h, then a sample of how each
 * vehicle signal shows. A plain theme's colours are made for its panel, so its samples sit on it.
 */
@Composable
private fun DayLegend(panel: ThemePanel) {
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    val label = MaterialTheme.typography.labelMedium
    val art = panel.art
    val sampleGround = if (art == null) Modifier.background(Color(panel.argb), RoundedCornerShape(8.dp)) else Modifier
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.speed_kmh, 0), style = label, color = text)
            SpeedColorStrip(panel.speedColorBaseArgb, Modifier.weight(1f).height(16.dp).then(sampleGround))
            Text(stringResource(R.string.speed_kmh, SPEED_COLOR_MAX_KMH.toInt()), style = label, color = text)
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val effects = panel.effects
            LegendSignal.entries.forEach { signal ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(SAMPLE_SIZE).then(sampleGround)) {
                        when {
                            art != null -> with(art) { drawLegendSample(signal, panel.dark) }
                            signal == LegendSignal.TURN_SIGNAL -> band(Color(effects.turnSignalBandArgb))
                            signal == LegendSignal.WIPER -> rain(Color(effects.rainArgb).copy(alpha = RAIN_ALPHA))
                            else -> band(Color(effects.lowBeamArgb))
                        }
                    }
                    Text(stringResource(signal.sectionLabel), style = MaterialTheme.typography.labelLarge, color = text)
                }
            }
        }
    }
}

/**
 * The route's first start and last end time, each on a small glass chip just above its point on the
 * map graph drawn in [area] (below the point when there is no room above, kept inside [area]).
 */
@Composable
private fun RouteTimeChips(
    route: MapRoute,
    summary: DriveSummary,
    area: DpArea,
) {
    val start = route.strokes.first().first()
    val end = route.strokes.last().last()
    Layout(
        content = {
            TimeChip(stringResource(R.string.route_start_time, summary.firstStart.format(TimeFormat)))
            TimeChip(stringResource(R.string.route_end_time, summary.lastEnd.format(TimeFormat)))
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val chips = measurables.map { it.measure(Constraints()) }
        val origin = Offset(area.left.toPx(), area.top.toPx())
        val areaSize = Size(area.width.toPx(), area.height.toPx())
        val frame = mapFrame(areaSize)
        val gap = CHIP_GAP.toPx()
        val placed = mutableListOf<Rect>()
        layout(constraints.maxWidth, constraints.maxHeight) {
            listOf(start, end).zip(chips).forEach { (point, chip) ->
                val at = origin + frame.at(point)
                val x = (at.x - chip.width / 2).coerceIn(origin.x, origin.x + areaSize.width - chip.width)
                val above = Rect(Offset(x, at.y - gap - chip.height), Size(chip.width.toFloat(), chip.height.toFloat()))
                val below = above.translate(0f, gap * 2 + chip.height)
                // Above unless that runs off the top or onto the other chip.
                val rect = if (above.top >= origin.y && placed.none { it.overlaps(above) }) above else below
                placed += rect
                chip.place(rect.left.roundToInt(), rect.top.roundToInt())
            }
        }
    }
}

@Composable
private fun TimeChip(text: String) {
    Box(
        Modifier.height(CHIP_HEIGHT).glass(RoundedCornerShape(CHIP_HEIGHT / 2), GlassLook.Chip).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun DrawScope.band(color: Color) {
    val height = size.height * 0.5f
    drawRoundRect(
        color,
        topLeft = Offset(size.width * 0.1f, (size.height - height) / 2),
        size = Size(size.width * 0.8f, height),
        cornerRadius = CornerRadius(height / 2),
    )
}

private fun DrawScope.rain(color: Color) {
    val strokeWidth = size.height * 0.12f
    listOf(0.3f, 0.5f, 0.7f).forEach { x ->
        val top = Offset(size.width * x + size.height * 0.1f, size.height * 0.15f)
        val bottom = Offset(size.width * x - size.height * 0.1f, size.height * 0.85f)
        drawLine(color, top, bottom, strokeWidth, StrokeCap.Round)
    }
}

/** A one-time draw-in: progress 0 to 1 over drive time, or 1 when it does not play. */
@Stable
private class DrawIn(
    play: Boolean,
    startComplete: Boolean,
) {
    val progress = Animatable(if (play && !startComplete) 0f else 1f)
    var pending by mutableStateOf(play)

    fun skip(scope: CoroutineScope) {
        pending = false
        scope.launch { progress.snapTo(1f) }
    }
}

/**
 * Plays the draw-in once [ready], unless skipped. When [startComplete] (the day art growing out of
 * the grass cell) the drawing shows complete until the grow finished, then replays from the start;
 * otherwise it starts empty and draws right away.
 */
@Composable
private fun rememberDrawIn(
    play: Boolean,
    startComplete: Boolean,
    ready: Boolean,
    durationMs: Int,
): DrawIn {
    val drawIn = remember { DrawIn(play, startComplete) }
    LaunchedEffect(ready) {
        if (!ready || !drawIn.pending) return@LaunchedEffect
        if (startComplete) {
            delay(GROW_MS.toLong())
            if (!drawIn.pending) return@LaunchedEffect
        }
        drawIn.progress.snapTo(0f)
        drawIn.progress.animateTo(1f, tween(durationMs, easing = LinearOutSlowInEasing))
        drawIn.pending = false
    }
    return drawIn
}
