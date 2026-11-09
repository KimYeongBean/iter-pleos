package io.github.kimyeongbean.iter.app

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kimyeongbean.iter.art.DayArtData
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Grass intro: cells appear in date order, whole intro within 600 ms (Notion 디자인 '애니메이션·전환 기준'). */
private const val INTRO_TOTAL_MS = 600
private const val CELL_FADE_MS = 200

/**
 * Grass: one cell per day. A day with a day record shows its day art thumbnail on [background];
 * tapping it calls [onOpenDay]. [recordedDates] is null while loading.
 * [onOpenDebugMenu] is null when the debug menu is hidden.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun GrassScreen(
    today: LocalDate,
    recordedDates: List<LocalDate>?,
    dayArt: (LocalDate) -> DayArtData?,
    loadDayArt: suspend (LocalDate) -> Unit,
    background: MapBackground,
    gridState: LazyGridState,
    playIntro: Boolean,
    onIntroFinished: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebugMenu: (() -> Unit)?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.grass_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings_open)) }
            if (onOpenDebugMenu != null) {
                TextButton(onClick = onOpenDebugMenu) { Text(stringResource(R.string.debug_menu_open)) }
            }
        }
        if (recordedDates != null) {
            GrassGrid(
                cells = grassCells(today, recordedDates),
                dayArt = dayArt,
                loadDayArt = loadDayArt,
                background = background,
                gridState = gridState,
                playIntro = playIntro,
                onIntroFinished = onIntroFinished,
                onOpenDay = onOpenDay,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GrassGrid(
    cells: List<GrassCell?>,
    dayArt: (LocalDate) -> DayArtData?,
    loadDayArt: suspend (LocalDate) -> Unit,
    background: MapBackground,
    gridState: LazyGridState,
    playIntro: Boolean,
    onIntroFinished: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // Intro clock in ms. Starts finished when the intro already played.
    val introMs = remember { Animatable(if (playIntro) 0f else INTRO_TOTAL_MS.toFloat()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (playIntro) {
            introMs.animateTo(INTRO_TOTAL_MS.toFloat(), tween(INTRO_TOTAL_MS, easing = LinearEasing))
            onIntroFinished()
        }
    }
    // A tap skips the intro to its final frame (snapTo cancels the running animation).
    val skipIntro =
        Modifier.pointerInput(Unit) {
            detectTapGestures {
                if (introMs.value < INTRO_TOTAL_MS) {
                    scope.launch { introMs.snapTo(INTRO_TOTAL_MS.toFloat()) }
                    onIntroFinished()
                }
            }
        }
    val stepMs = (INTRO_TOTAL_MS - CELL_FADE_MS).toFloat() / (cells.size - 1).coerceAtLeast(1)
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val recordedBackground = Color(background.argb)
    val empty = MaterialTheme.colorScheme.surfaceVariant

    Column(Modifier.padding(top = 16.dp).then(skipIntro)) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            itemsIndexed(cells) { index, cell ->
                if (cell == null) {
                    Box(Modifier.aspectRatio(1f))
                    return@itemsIndexed
                }
                val label =
                    stringResource(
                        if (cell.recorded) R.string.grass_cell_recorded else R.string.grass_cell_empty,
                        cell.date.format(dateFormat),
                    )
                val intro =
                    Modifier
                        .aspectRatio(1f)
                        .graphicsLayer {
                            // Read the clock here so the intro redraws without recomposing.
                            val fraction = ((introMs.value - index * stepMs) / CELL_FADE_MS).coerceIn(0f, 1f)
                            val eased = LinearOutSlowInEasing.transform(fraction)
                            alpha = eased
                            scaleX = 0.8f + 0.2f * eased
                            scaleY = scaleX
                        }
                if (!cell.recorded) {
                    Box(intro.background(empty, RoundedCornerShape(6.dp)).semantics { contentDescription = label })
                    return@itemsIndexed
                }
                LaunchedEffect(loadDayArt, cell.date) { loadDayArt(cell.date) }
                Box(
                    intro
                        .dayArtSharedElement(cell.date, sharedTransitionScope, animatedVisibilityScope)
                        .clip(RoundedCornerShape(6.dp))
                        .background(recordedBackground)
                        .clickable { onOpenDay(cell.date) }
                        .semantics { contentDescription = label },
                ) {
                    dayArt(cell.date)?.let { DayArt(it, background, Modifier.fillMaxSize()) }
                }
            }
        }
    }
}
