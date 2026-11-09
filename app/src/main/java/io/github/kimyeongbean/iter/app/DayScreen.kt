package io.github.kimyeongbean.iter.app

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.kimyeongbean.iter.art.DayArtData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Day art draw-in on first open: drive time order, within 1.2 s (Notion 디자인 '애니메이션·전환 기준'). */
private const val DAY_ART_DRAW_IN_MS = 1_200

/** Map graph draw-in on first open: the line from the start point, within 1.5 s. */
private const val MAP_GRAPH_DRAW_IN_MS = 1_500

/** Grass cell → day art: the thumbnail grows into the big day art, 350 ms, decelerating. */
private const val GROW_MS = 350

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
 * The day screen (그날 화면) of [date]: the big day art above, the map graph below.
 * [dayArt] and [route] are null while loading.
 * When [playDrawIn], the full day art first grows out of the grass cell, then both draw in drive
 * time order once their data is ready. A tap anywhere skips both to the end. [onDrawInStarted] is
 * called on open, so the draw-in counts as played even when skipped or left early.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun DayScreen(
    date: LocalDate,
    dayArt: DayArtData?,
    route: MapRoute?,
    background: MapBackground,
    playDrawIn: Boolean,
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
    val dateText = remember(date) { date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
    val artDescription = stringResource(R.string.day_art_description, dateText)
    val mapDescription = stringResource(R.string.map_graph_description, dateText)
    val panelColor = Color(background.argb)
    val messageColor = Color(background.textArgb)

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    artDrawIn.skip(scope)
                    routeDrawIn.skip(scope)
                }
            }.padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(dateText, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp))
        }
        // Day art and map graph share the height as two squares, so both fit without scrolling.
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val square = Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true)
            Box(
                square
                    .dayArtSharedElement(date, sharedTransitionScope, animatedVisibilityScope)
                    .background(panelColor, RoundedCornerShape(16.dp))
                    .semantics { contentDescription = artDescription },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    dayArt == null -> Unit
                    dayArt.shapes.isEmpty() -> Text(stringResource(R.string.day_art_empty), color = messageColor)
                    else -> DayArt(dayArt, background, Modifier.fillMaxSize()) { artDrawIn.progress.value }
                }
            }
            Box(
                square
                    .background(panelColor, RoundedCornerShape(16.dp))
                    .semantics { contentDescription = mapDescription },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    route == null -> Unit
                    route.strokes.isEmpty() -> Text(stringResource(R.string.map_graph_no_route), color = messageColor)
                    else -> MapGraph(route, background, Modifier.fillMaxSize()) { routeDrawIn.progress.value }
                }
            }
        }
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
 * Plays the draw-in once [ready], after the grass cell finished growing, unless skipped.
 * Until then the drawing shows complete when [startComplete], else empty.
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
        delay(GROW_MS.toLong())
        if (!drawIn.pending) return@LaunchedEffect
        drawIn.progress.snapTo(0f)
        drawIn.progress.animateTo(1f, tween(durationMs, easing = LinearOutSlowInEasing))
        drawIn.pending = false
    }
    return drawIn
}
