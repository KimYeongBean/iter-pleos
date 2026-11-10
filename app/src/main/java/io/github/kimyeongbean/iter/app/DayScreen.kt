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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** Day art draw-in on first open: drive time order, within 1.2 s (Notion 디자인 '애니메이션·전환 기준'). */
private const val DAY_ART_DRAW_IN_MS = 1_200

/** Map graph draw-in on first open: the line from the start point, within 1.5 s (same Notion page). */
private const val MAP_GRAPH_DRAW_IN_MS = 1_500

/** Grass cell → day art: the thumbnail grows into the big day art, 350 ms, decelerating. */
private const val GROW_MS = 350

/** From this width the day art sits left of the map graph (Landscape); narrower, they stack and scroll (Portrait). */
private val WIDE_MIN_WIDTH = 1000.dp

/** Share of the width the day art column takes in the wide layout; the map graph gets the rest. */
private const val DAY_ART_COLUMN_WEIGHT = 0.38f

/** Height the summary and legends under the day art keep in the wide layout, however short the window. */
private val DAY_INFO_MIN_HEIGHT = 220.dp

/** Legend sample sizes: a wide one for bands and rain, a square one for the day art rings. */
private val BAND_SAMPLE_SIZE = DpSize(36.dp, 20.dp)
private val RING_SAMPLE_SIZE = DpSize(28.dp, 28.dp)

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
 * The day screen (그날 화면) of [date]: the day art small, the map graph large, each on [panel],
 * with the drive summary line and the legends. [dayArt] and [route] are null while loading;
 * [summary] is null while loading or when the date has no day record.
 * When [playDrawIn], the full day art first grows out of the grass cell, then both draw in drive
 * time order once their data is ready. A tap anywhere skips both to the end. [onDrawInStarted] is
 * called on open, so the draw-in counts as played even when skipped or left early.
 * When [animateRain], the map graph's rain effect falls over and over; a tap does not stop it.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun DayScreen(
    date: LocalDate,
    dayArt: DayArtData?,
    route: MapRoute?,
    summary: DriveSummary?,
    panel: ThemePanel,
    playDrawIn: Boolean,
    animateRain: Boolean,
    onDrawInStarted: () -> Unit,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    LaunchedEffect(Unit) { if (playDrawIn) onDrawInStarted() }
    // The day art shows complete while it grows out of the grass cell; the map graph starts empty.
    val artDrawIn = rememberDrawIn(playDrawIn, startComplete = true, ready = dayArt != null, DAY_ART_DRAW_IN_MS)
    val routeDrawIn = rememberDrawIn(playDrawIn, startComplete = false, ready = route != null, MAP_GRAPH_DRAW_IN_MS)
    val scope = rememberCoroutineScope()
    val dateText = remember(date) { monthDayWeekdayText(date) }
    val describedDate = remember(date) { date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
    val artDescription = stringResource(R.string.day_art_description, describedDate)
    val mapDescription = stringResource(R.string.map_graph_description, describedDate)
    val panelColor = Color(panel.argb)
    val panelText = Color(panel.textArgb)
    val panelShape = MaterialTheme.shapes.extraLarge

    val artPanel: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier
                .aspectRatio(1f)
                .dayArtSharedElement(date, sharedTransitionScope, animatedVisibilityScope)
                .clip(panelShape)
                .background(panelColor)
                .semantics { contentDescription = artDescription },
            contentAlignment = Alignment.Center,
        ) {
            when {
                dayArt == null -> Unit
                dayArt.shapes.isEmpty() -> Text(stringResource(R.string.day_art_empty), color = panelText)
                else -> DayArt(dayArt, panel, Modifier.fillMaxSize()) { artDrawIn.progress.value }
            }
        }
    }
    val mapPanel: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.clip(panelShape).background(panelColor)) {
            Box(
                Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = mapDescription },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    route == null -> Unit
                    route.strokes.isEmpty() -> Text(stringResource(R.string.map_graph_no_route), color = panelText)
                    else -> MapGraph(route, panel, Modifier.fillMaxSize(), animateRain) { routeDrawIn.progress.value }
                }
            }
            MapGraphLegend(panel, Modifier.padding(start = 28.dp, end = 28.dp, bottom = 24.dp))
        }
    }
    val infoPanel: @Composable (Modifier) -> Unit = { modifier ->
        DayInfo(summary, panel, modifier.clip(panelShape).background(panelColor).padding(28.dp))
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures {
                artDrawIn.skip(scope)
                routeDrawIn.skip(scope)
            }
        },
    ) {
        val wide = maxWidth >= WIDE_MIN_WIDTH
        val gap = if (wide) 32.dp else 16.dp
        Column(
            Modifier.fillMaxSize().padding(if (wide) 48.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBack, if (wide) IterButtonSize.MEDIUM.height else IterButtonSize.SMALL.height)
                Text(
                    dateText,
                    style = if (wide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                )
            }
            if (wide) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    BoxWithConstraints(Modifier.weight(DAY_ART_COLUMN_WEIGHT).fillMaxHeight()) {
                        // A square as wide as its column, but in a short window it shrinks so the
                        // summary and legends below keep their height.
                        val artSide = minOf(maxWidth, maxHeight - DAY_INFO_MIN_HEIGHT - gap).coerceAtLeast(0.dp)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(gap),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            artPanel(Modifier.size(artSide))
                            infoPanel(Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                    mapPanel(Modifier.weight(1f - DAY_ART_COLUMN_WEIGHT).fillMaxHeight())
                }
            } else {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    artPanel(Modifier.fillMaxWidth())
                    mapPanel(Modifier.fillMaxWidth().aspectRatio(1f))
                    infoPanel(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Under the day art: the drive summary line, the speed colour legend and the day art pattern legend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayInfo(
    summary: DriveSummary?,
    panel: ThemePanel,
    modifier: Modifier = Modifier,
) {
    val text = Color(panel.textArgb)
    val label = MaterialTheme.typography.labelMedium
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
        if (summary != null) {
            Text(
                stringResource(R.string.drive_summary, summary.sessionCount, summary.timeSpanText),
                style = MaterialTheme.typography.labelLarge,
                color = text,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.speed_kmh, 0), style = label, color = text)
            SpeedColorStrip(panel.speedColorBaseArgb, Modifier.weight(1f).height(16.dp))
            Text(stringResource(R.string.speed_kmh, SPEED_COLOR_MAX_KMH.toInt()), style = label, color = text)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Each sample is a small day art: the ring sits where the day art draws it. Turn signal is
            // the solid outer ring, wiper the dashed outermost one, low beam the ring at the centre.
            LegendItem(stringResource(R.string.legend_turn_signal), text, RING_SAMPLE_SIZE) { ring(text, radiusFraction = 0.8f) }
            LegendItem(stringResource(R.string.legend_wiper), text, RING_SAMPLE_SIZE) {
                ring(text, radiusFraction = 0.9f, widthFraction = 0.12f, dashed = true)
            }
            LegendItem(stringResource(R.string.legend_low_beam), text, RING_SAMPLE_SIZE) {
                ring(text, radiusFraction = 0.3f, edgeGuide = true)
            }
        }
    }
}

/** Under the map graph: what its turn signal band, low beam band and rain effect look like. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapGraphLegend(
    panel: ThemePanel,
    modifier: Modifier = Modifier,
) {
    val text = Color(panel.textArgb)
    val effects = panel.effects
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LegendItem(stringResource(R.string.legend_turn_signal_section), text) { band(Color(effects.turnSignalBandArgb)) }
        LegendItem(stringResource(R.string.legend_low_beam_section), text) { band(Color(effects.lowBeamArgb)) }
        LegendItem(stringResource(R.string.legend_wiper_section), text) { rain(Color(effects.rainArgb).copy(alpha = RAIN_ALPHA)) }
    }
}

/** One legend entry: a small drawn sample, then what it stands for. */
@Composable
private fun LegendItem(
    label: String,
    textColor: Color,
    sampleSize: DpSize = BAND_SAMPLE_SIZE,
    drawSample: DrawScope.() -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(sampleSize)) { drawSample() }
        Text(label, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

/**
 * A day art pattern ring at [radiusFraction] of the sample's radius, [widthFraction] of it wide.
 * [edgeGuide] adds a faint circle for the day art's edge, so a ring near the centre reads as inner.
 */
private fun DrawScope.ring(
    color: Color,
    radiusFraction: Float,
    widthFraction: Float = 0.2f,
    dashed: Boolean = false,
    edgeGuide: Boolean = false,
) {
    val sampleRadius = size.minDimension / 2
    val strokeWidth = sampleRadius * widthFraction
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 2, strokeWidth * 2)) else null
    if (edgeGuide) drawCircle(color, sampleRadius * 0.95f, alpha = 0.3f, style = Stroke(sampleRadius * 0.05f))
    drawCircle(color, sampleRadius * radiusFraction - strokeWidth / 2, style = Stroke(strokeWidth, pathEffect = effect))
}

private fun DrawScope.band(color: Color) {
    drawRoundRect(color, cornerRadius = CornerRadius(size.height / 2))
}

private fun DrawScope.rain(color: Color) {
    val strokeWidth = size.height * 0.15f
    listOf(0.2f, 0.5f, 0.8f).forEach { x ->
        val top = Offset(size.width * x + size.height * 0.15f, 0f)
        val bottom = Offset(size.width * x - size.height * 0.15f, size.height)
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
