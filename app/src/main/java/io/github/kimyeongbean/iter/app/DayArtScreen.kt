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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.kimyeongbean.iter.art.DayArtData
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Day art draw-in on first open: drive time order, within 1.2 s (Notion 디자인 '애니메이션·전환 기준'). */
private const val DRAW_IN_MS = 1_200

/** Grass cell → day art: the thumbnail grows into the big day art, 350 ms, decelerating. */
private const val GROW_MS = 350

@OptIn(ExperimentalSharedTransitionApi::class)
internal val DayArtGrowTransform = BoundsTransform { _, _ -> tween(GROW_MS, easing = LinearOutSlowInEasing) }

/** Shared element key linking a grass cell's thumbnail to that date's big day art. */
internal fun dayArtSharedKey(date: LocalDate) = "day-art-$date"

/**
 * The day screen: the big day art of [date]. [data] is null while loading.
 * When [playDrawIn] the art draws in drive time order once data is ready; a tap skips to the end.
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
    val progress = remember { Animatable(if (playDrawIn) 0f else 1f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(data != null) {
        if (data != null && progress.value < 1f) {
            onDrawInStarted()
            progress.animateTo(1f, tween(DRAW_IN_MS, easing = LinearOutSlowInEasing))
        }
    }
    val dateText = remember(date) { date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
    val description = stringResource(R.string.day_art_description, dateText)

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(dateText, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp))
        }
        val art =
            with(sharedTransitionScope) {
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .sharedElement(
                        sharedTransitionScope.rememberSharedContentState(dayArtSharedKey(date)),
                        animatedVisibilityScope,
                        boundsTransform = DayArtGrowTransform,
                    )
            }
        Box(
            art
                .background(Color(background.argb), RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTapGestures { if (progress.value < 1f) scope.launch { progress.snapTo(1f) } }
                }.semantics { contentDescription = description },
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
