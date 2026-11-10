package io.github.kimyeongbean.iter.app

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

/** From this width the today card sits left of the grid (Landscape); narrower, it is a row on top (Portrait). */
private val WIDE_MIN_WIDTH = 1000.dp
private val TODAY_CARD_WIDTH = 520.dp

/** The intro spreads over this many cells, the weeks on screen at first open; older cells are scrolled away. */
private const val INTRO_CELLS = 5 * 7

/**
 * Grass: the today card and one cell per day. A day with a day record shows its day art thumbnail
 * on [background]; tapping it calls [onOpenDay]. Today's cell is ringed in [accent], the speed
 * colour. [recordedDates] is null while loading; [todaySummary] is null when today has no day
 * record. [onOpenDebugMenu] is null when the debug menu is hidden.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun GrassScreen(
    today: LocalDate,
    recordedDates: List<LocalDate>?,
    dayArt: (LocalDate) -> DayArtData?,
    loadDayArt: suspend (LocalDate) -> Unit,
    todaySummary: DriveSummary?,
    background: MapBackground,
    accent: Color,
    gridState: LazyGridState,
    playIntro: Boolean,
    onIntroFinished: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebugMenu: (() -> Unit)?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_MIN_WIDTH
        val gap = if (wide) 32.dp else 16.dp
        Column(
            Modifier.fillMaxSize().padding(if (wide) 48.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            GrassHeader(wide, onOpenSettings, onOpenDebugMenu)
            if (recordedDates == null) return@Column
            val todayRecorded = today in recordedDates
            LaunchedEffect(loadDayArt, today, todayRecorded) { if (todayRecorded) loadDayArt(today) }
            // Opened from the today card, the day screen just moves in: today's cell must not grow
            // into it, since the tap was not on the cell.
            var openedFromTodayCard by remember { mutableStateOf(false) }
            val todayCard: @Composable (Modifier) -> Unit = { modifier ->
                TodayCard(
                    today = today,
                    recorded = todayRecorded,
                    dayArt = dayArt(today),
                    summary = todaySummary,
                    background = background,
                    wide = wide,
                    onOpen = {
                        openedFromTodayCard = true
                        onOpenDay(today)
                    },
                    modifier = modifier,
                )
            }
            val grid: @Composable (Modifier) -> Unit = { modifier ->
                GrassGrid(
                    cells = grassCells(today, recordedDates),
                    today = today,
                    todayCellGrows = !openedFromTodayCard,
                    dayArt = dayArt,
                    loadDayArt = loadDayArt,
                    background = background,
                    accent = accent,
                    wide = wide,
                    gridState = gridState,
                    playIntro = playIntro,
                    onIntroFinished = onIntroFinished,
                    onOpenDay = onOpenDay,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = modifier,
                )
            }
            if (wide) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    todayCard(Modifier.width(TODAY_CARD_WIDTH).fillMaxHeight())
                    grid(Modifier.weight(1f))
                }
            } else {
                todayCard(Modifier.fillMaxWidth())
                grid(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GrassHeader(
    wide: Boolean,
    onOpenSettings: () -> Unit,
    onOpenDebugMenu: (() -> Unit)?,
) {
    val buttonSize = if (wide) IterButtonSize.MEDIUM else IterButtonSize.SMALL
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        if (onOpenDebugMenu != null) {
            TextButton(onClick = onOpenDebugMenu) { Text(stringResource(R.string.debug_menu_open)) }
        }
        SettingsButton(buttonSize.height, onOpenSettings)
    }
}

/** A round button with a sliders icon; "설정" is its accessibility label. */
@Composable
private fun SettingsButton(
    diameter: Dp,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.settings_open)
    val iconColor = MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(diameter).semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxSize().padding(diameter * 0.28f)) {
            val stroke = Stroke(width = size.minDimension * 0.09f, cap = StrokeCap.Round)
            val knobRadius = size.minDimension * 0.12f
            // Two sliders: a line with a knob towards the right, then one with a knob towards the left.
            listOf(0.25f to 0.68f, 0.75f to 0.32f).forEach { (yFraction, knobFraction) ->
                val y = size.height * yFraction
                val knobX = size.width * knobFraction
                drawLine(iconColor, Offset(0f, y), Offset(knobX - knobRadius, y), stroke.width, stroke.cap)
                drawLine(iconColor, Offset(knobX + knobRadius, y), Offset(size.width, y), stroke.width, stroke.cap)
                drawCircle(iconColor, knobRadius, Offset(knobX, y), style = stroke)
            }
        }
    }
}

/**
 * The today card (GLOSSARY: 오늘 카드): today's day art, large, with the drive summary line.
 * Tapping it opens today's day screen. With no day record today it only says so and does nothing.
 */
@Composable
private fun TodayCard(
    today: LocalDate,
    recorded: Boolean,
    dayArt: DayArtData?,
    summary: DriveSummary?,
    background: MapBackground,
    wide: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateText = remember(today) { monthDayWeekdayText(today) }
    val openLabel = stringResource(R.string.today_card_open)
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val summaryText =
        when {
            !recorded -> stringResource(R.string.today_empty)

            // Recorded, but the summary is still loading.
            summary == null -> ""

            else -> stringResource(R.string.drive_summary, summary.sessionCount, summary.timeSpanText)
        }
    val art: @Composable (Modifier) -> Unit = { artModifier ->
        Box(artModifier.aspectRatio(1f).clip(MaterialTheme.shapes.large).background(Color(background.argb))) {
            if (dayArt != null) DayArt(dayArt, background, Modifier.fillMaxSize())
        }
    }
    val open = if (recorded) Modifier.clickable(onClickLabel = openLabel, onClick = onOpen) else Modifier
    IterCard(modifier.clip(MaterialTheme.shapes.extraLarge).then(open)) {
        if (wide) {
            Column(Modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column {
                    Text(stringResource(R.string.today), style = MaterialTheme.typography.labelLarge, color = secondary)
                    Text(dateText, style = MaterialTheme.typography.titleMedium)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (recorded) art(Modifier)
                }
                Text(summaryText, style = MaterialTheme.typography.bodyMedium, color = secondary)
            }
        } else {
            Row(
                Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (recorded) art(Modifier.size(140.dp))
                Column {
                    Text(stringResource(R.string.today), style = MaterialTheme.typography.labelMedium, color = secondary)
                    Text(dateText, style = MaterialTheme.typography.titleSmall)
                    Text(summaryText, style = MaterialTheme.typography.labelLarge, color = secondary)
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GrassGrid(
    cells: List<GrassCell?>,
    today: LocalDate,
    todayCellGrows: Boolean,
    dayArt: (LocalDate) -> DayArtData?,
    loadDayArt: suspend (LocalDate) -> Unit,
    background: MapBackground,
    accent: Color,
    wide: Boolean,
    gridState: LazyGridState,
    playIntro: Boolean,
    onIntroFinished: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    // Intro clock in ms. Starts finished when the intro already played.
    val introMs = remember { Animatable(if (playIntro) 0f else INTRO_TOTAL_MS.toFloat()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (playIntro) {
            // First open: start at this week, the last row. Older weeks are a scroll up.
            gridState.scrollToItem(cells.lastIndex)
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
    // The grid opens on its last weeks, so the intro runs over those cells; older ones are already shown.
    val introFirstIndex = (cells.size - INTRO_CELLS).coerceAtLeast(0)
    val stepMs = (INTRO_TOTAL_MS - CELL_FADE_MS).toFloat() / (cells.size - introFirstIndex - 1).coerceAtLeast(1)
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val recordedBackground = Color(background.argb)
    val recordedText = Color(background.textArgb)
    val emptyOutline = MaterialTheme.colorScheme.secondaryContainer
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val cellShape = if (wide) MaterialTheme.shapes.large else MaterialTheme.shapes.medium
    val cellGap = if (wide) 16.dp else 8.dp
    val labelPadding = if (wide) Modifier.padding(start = 12.dp, top = 8.dp) else Modifier.padding(start = 8.dp, top = 4.dp)

    Column(modifier.then(skipIntro), verticalArrangement = Arrangement.spacedBy(cellGap)) {
        Text(
            stringResource(R.string.grass_title),
            style = if (wide) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cellGap)) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = if (wide) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                    color = secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(cellGap),
            verticalArrangement = Arrangement.spacedBy(cellGap),
        ) {
            itemsIndexed(cells) { index, cell ->
                if (cell == null) {
                    Box(Modifier.aspectRatio(1f))
                    return@itemsIndexed
                }
                val description =
                    stringResource(
                        if (cell.recorded) R.string.grass_cell_recorded else R.string.grass_cell_empty,
                        cell.date.format(dateFormat),
                    )
                val label = grassCellLabel(cell.date, firstCell = index == 0)
                val intro =
                    Modifier
                        .aspectRatio(1f)
                        .graphicsLayer {
                            // Read the clock here so the intro redraws without recomposing.
                            val fraction = ((introMs.value - (index - introFirstIndex) * stepMs) / CELL_FADE_MS).coerceIn(0f, 1f)
                            val eased = LinearOutSlowInEasing.transform(fraction)
                            alpha = eased
                            scaleX = 0.8f + 0.2f * eased
                            scaleY = scaleX
                        }
                val todayRing = if (cell.date == today) Modifier.border(3.dp, accent, cellShape) else Modifier
                if (!cell.recorded) {
                    val outline = if (cell.date == today) todayRing else Modifier.border(2.dp, emptyOutline, cellShape)
                    Box(intro.then(outline).semantics { contentDescription = description }) {
                        CellLabel(label, secondary, labelPadding)
                    }
                    return@itemsIndexed
                }
                LaunchedEffect(loadDayArt, cell.date) { loadDayArt(cell.date) }
                val grows =
                    if (cell.date != today || todayCellGrows) {
                        Modifier.dayArtSharedElement(cell.date, sharedTransitionScope, animatedVisibilityScope)
                    } else {
                        Modifier
                    }
                Box(
                    intro
                        .then(grows)
                        .clip(cellShape)
                        .background(recordedBackground)
                        .then(todayRing)
                        .clickable { onOpenDay(cell.date) }
                        .semantics { contentDescription = description },
                ) {
                    dayArt(cell.date)?.let { DayArt(it, background, Modifier.fillMaxSize()) }
                    CellLabel(label, recordedText, labelPadding)
                }
            }
        }
    }
}

/** The date in a cell's corner. Screen readers get the cell's description instead, so this is hidden from them. */
@Composable
private fun CellLabel(
    label: String,
    color: Color,
    modifier: Modifier,
) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.clearAndSetSemantics {},
    )
}
