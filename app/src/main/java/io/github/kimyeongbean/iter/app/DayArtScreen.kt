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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Day art draw-in on first open: drive time order, within 1.2 s (Notion 디자인 '애니메이션·전환 기준'). */
private const val DRAW_IN_MS = 1_200

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
 * The day screen: the big day art of [date]. [data] is null while loading.
 * When [playDrawIn], the full art first grows out of the grass cell, then draws in drive time
 * order once data is ready. A tap skips to the end. [onDrawInStarted] is called on open, so the
 * draw-in counts as played even when skipped or left early.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun DayArtScreen(
    date: LocalDate,
    data: DayArtData?,
    background: MapBackground,
    playDrawIn: Boolean,
    onDrawInStarted: () -> Unit,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val progress = remember { Animatable(1f) }
    var drawInPending by remember { mutableStateOf(playDrawIn) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (playDrawIn) onDrawInStarted() }
    LaunchedEffect(data != null) {
        if (data == null || !drawInPending) return@LaunchedEffect
        // Let the full thumbnail finish growing before the day replays from its start.
        delay(GROW_MS.toLong())
        if (!drawInPending) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(DRAW_IN_MS, easing = LinearOutSlowInEasing))
        drawInPending = false
    }
    val skipDrawIn = {
        drawInPending = false
        scope.launch { progress.snapTo(1f) }
    }
    val dateText = remember(date) { date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
    val description = stringResource(R.string.day_art_description, dateText)

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(dateText, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp))
        }
        Box(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .dayArtSharedElement(date, sharedTransitionScope, animatedVisibilityScope)
                .background(Color(background.argb), RoundedCornerShape(16.dp))
                .pointerInput(Unit) { detectTapGestures { skipDrawIn() } }
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            when {
                data == null -> Unit
                data.shapes.isEmpty() -> Text(stringResource(R.string.day_art_empty), color = Color(background.textArgb))
                else -> DayArt(data, background, Modifier.fillMaxSize()) { progress.value }
            }
        }
    }
}
