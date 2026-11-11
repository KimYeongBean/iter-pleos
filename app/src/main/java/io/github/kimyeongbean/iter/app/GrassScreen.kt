package io.github.kimyeongbean.iter.app

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.kimyeongbean.iter.art.DayArtData
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.tanh

/** Grass intro: cells appear in date order, whole intro within 600 ms (Notion 디자인 '애니메이션·전환 기준'). */
private const val INTRO_TOTAL_MS = 600
private const val CELL_FADE_MS = 200

/** From this width the today card sits bottom left and the calendar right (Landscape); narrower, they stack (Portrait). */
private val WIDE_MIN_WIDTH = 1000.dp

/** Share of the width the calendar plate takes in the wide layout (872 of 1680 dp in 시안 B). */
private const val CALENDAR_WIDTH_SHARE = 0.52f

/** The today card leaves this much of the left side free on its right, so today's big art shows. */
private val TODAY_CARD_RIGHT_ROOM = 160.dp
private val TODAY_CARD_MAX_WIDTH = 600.dp

/** The screen width the narrow 시안 B mockup was drawn at (Portrait). */
private const val NARROW_MOCKUP_WIDTH_DP = 628f

/** Weeks the calendar shows at once; older weeks are a scroll up. */
private const val VISIBLE_WEEKS = 5

/** The intro spreads over this many cells, the weeks on screen at first open; older cells are scrolled away. */
private const val INTRO_CELLS = VISIBLE_WEEKS * 7

/**
 * Grass on glass (시안 B): today's day art large behind everything, the app name pill, the today
 * card and a calendar plate with one cell per day. A day with a day record shows its day art
 * thumbnail; tapping it calls [onOpenDay]. Today's cell is ringed in [accent], the theme's line
 * colour for the app background. [recordedDates] is null while loading; [todaySummary] is null
 * when today has no day record. [onOpenDebugMenu] is null when the debug menu is hidden.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun GrassScreen(
    today: LocalDate,
    recordedDates: List<LocalDate>?,
    dayArt: (LocalDate) -> DayArtData?,
    loadDayArt: suspend (LocalDate) -> Unit,
    todaySummary: DriveSummary?,
    panel: ThemePanel,
    accent: Color,
    gridState: LazyGridState,
    playIntro: Boolean,
    onIntroFinished: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebugMenu: (() -> Unit)?,
    animateBackdrop: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val loading = recordedDates == null
    val todayRecorded = recordedDates != null && today in recordedDates
    LaunchedEffect(loadDayArt, today, todayRecorded) { if (todayRecorded) loadDayArt(today) }
    // Opened from the today card, the day screen just moves in: today's cell must not grow
    // into it, since the tap was not on the cell.
    var openedFromTodayCard by remember { mutableStateOf(false) }
    val openToday = {
        openedFromTodayCard = true
        onOpenDay(today)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_MIN_WIDTH
        val screenWidth = maxWidth
        val heroArt = dayArt(today)?.takeIf { todayRecorded }
        val heroSide: Dp
        val heroAt: DpOffset
        if (wide) {
            val scale = maxHeight.value / MOCKUP_HEIGHT_DP
            heroSide = 1120.dp * scale
            // Centred on the open space left of the calendar, above the today card.
            val leftWidth = maxWidth * (1 - CALENDAR_WIDTH_SHARE) - SCREEN_MARGIN_WIDE
            heroAt = DpOffset(leftWidth / 2 - heroSide / 2, maxHeight * HERO_CENTRE_Y - heroSide / 2)
        } else {
            val scale = maxWidth.value / NARROW_MOCKUP_WIDTH_DP
            heroSide = 560.dp * scale
            heroAt = DpOffset(190.dp * scale, (-150).dp * scale)
        }
        GlassScene(
            accent = accent,
            art = panel.art,
            moving = animateBackdrop,
            backdrop = {
                // Today's day art is the screen's backdrop; with no record today only the bed shows.
                // Decoration only: the today card says the same in words.
                if (heroArt != null) {
                    val nudge = rememberDragNudge()
                    Box(Modifier.fillMaxSize().then(nudge.dragToNudge))
                    DayArtDisc(
                        heroArt,
                        panel,
                        Modifier
                            .offset(heroAt.x, heroAt.y)
                            .size(heroSide)
                            .graphicsLayer {
                                translationX = nudge.offset.value.x
                                translationY = nudge.offset.value.y
                            }.clearAndSetSemantics {},
                    )
                }
            },
        ) {
            val calendar: @Composable (Modifier) -> Unit = { modifier ->
                CalendarPlate(
                    wide = wide,
                    recordedDates = recordedDates,
                    today = today,
                    todayCellGrows = !openedFromTodayCard,
                    dayArt = dayArt,
                    loadDayArt = loadDayArt,
                    panel = panel,
                    accent = accent,
                    gridState = gridState,
                    playIntro = playIntro,
                    onIntroFinished = onIntroFinished,
                    onOpenDay = onOpenDay,
                    onOpenSettings = onOpenSettings,
                    onOpenDebugMenu = onOpenDebugMenu,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = modifier,
                )
            }
            if (wide) {
                val calendarWidth = screenWidth * CALENDAR_WIDTH_SHARE
                val leftWidth = screenWidth - calendarWidth - SCREEN_MARGIN_WIDE
                AppNamePill(wide = true, Modifier.padding(SCREEN_MARGIN_WIDE))
                TodayCard(
                    today = today,
                    loading = loading,
                    recorded = todayRecorded,
                    summary = todaySummary,
                    accent = accent,
                    onOpen = openToday,
                    modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(SCREEN_MARGIN_WIDE)
                        .width(minOf(TODAY_CARD_MAX_WIDTH, leftWidth - TODAY_CARD_RIGHT_ROOM)),
                )
                calendar(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(SCREEN_MARGIN_WIDE)
                        .width(calendarWidth)
                        .fillMaxHeight(),
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(SCREEN_MARGIN_NARROW),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppNamePill(wide = false)
                        Spacer(Modifier.weight(1f))
                        if (onOpenDebugMenu != null) {
                            TextButton(onClick = onOpenDebugMenu) { Text(stringResource(R.string.debug_menu_open)) }
                        }
                        SettingsButton(IterButtonSize.SMALL.height, onOpenSettings)
                    }
                    TodayRow(
                        today = today,
                        loading = loading,
                        recorded = todayRecorded,
                        summary = todaySummary,
                        accent = accent,
                        onOpen = openToday,
                    )
                    calendar(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

/** Where today's big art is centred in the wide layout, as a share of the screen height. */
private const val HERO_CENTRE_Y = 0.4f

/**
 * Dragging the backdrop pulls today's art like rubber: about this share of a short drag, then less
 * and less, never quite [NUDGE_MAX] away.
 */
private const val NUDGE_FOLLOW = 0.45f
private val NUDGE_MAX = 90.dp

/** The art trails the finger a little (soft, quick spring) and wobbles back when let go. */
private const val NUDGE_FOLLOW_STIFFNESS = 900f
private const val NUDGE_RELEASE_STIFFNESS = 260f
private const val NUDGE_RELEASE_DAMPING = 0.38f

/** How far today's art is nudged by a drag on the backdrop, and the modifier that takes the drag. */
private class DragNudge(
    val offset: Animatable<Offset, AnimationVector2D>,
    val dragToNudge: Modifier,
)

/**
 * A springy answer to a drag on the backdrop: the art trails the finger, harder to pull the further
 * it goes (a longer drag still moves it more), and wobbles back when let go.
 */
@Composable
private fun rememberDragNudge(): DragNudge {
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val maxPx = with(LocalDensity.current) { NUDGE_MAX.toPx() }
    val dragToNudge =
        Modifier.pointerInput(Unit) {
            var dragged = Offset.Zero
            val release = {
                dragged = Offset.Zero
                scope.launch { offset.animateTo(Offset.Zero, spring(NUDGE_RELEASE_DAMPING, NUDGE_RELEASE_STIFFNESS)) }
            }
            detectDragGestures(onDragEnd = { release() }, onDragCancel = { release() }) { change, amount ->
                change.consume()
                dragged += amount
                val wanted = dragged * NUDGE_FOLLOW
                val length = wanted.getDistance()
                // Rubber band: tanh bends the pull towards maxPx without a hard stop.
                val nudged = if (length > 0f) wanted * (maxPx * tanh(length / maxPx) / length) else wanted
                scope.launch { offset.animateTo(nudged, spring(stiffness = NUDGE_FOLLOW_STIFFNESS)) }
            }
        }
    return remember(offset, dragToNudge) { DragNudge(offset, dragToNudge) }
}

/** The app's name on a small glass pill. */
@Composable
private fun AppNamePill(
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val height = if (wide) IterButtonSize.MEDIUM.height else IterButtonSize.SMALL.height
    Box(
        modifier.height(height).glass(RoundedCornerShape(height / 2)).padding(horizontal = height * 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = if (wide) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A round glass button with a sliders icon; "설정" is its accessibility label. */
@Composable
private fun SettingsButton(
    diameter: Dp,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.settings_open)
    val iconColor = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier.size(diameter).glass(CircleShape, GlassLook.Control).semantics { contentDescription = description },
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

/** The today card's summary line: none while loading, the empty state when today has no day record. */
@Composable
private fun todaySummaryText(
    loading: Boolean,
    recorded: Boolean,
    summary: DriveSummary?,
): String? = when {
    loading -> null

    !recorded -> stringResource(R.string.today_empty)

    // Recorded, but the summary is still loading.
    summary == null -> null

    else -> stringResource(R.string.drive_summary, summary.sessionCount, summary.timeSpanText)
}

/**
 * The today card (GLOSSARY: 오늘 카드), wide: today's day number large, the drive summary line and
 * the "오늘 기록 보기" button, which opens today's day screen. With no day record today it says so
 * and has no button; while loading it shows the date only.
 */
@Composable
private fun TodayCard(
    today: LocalDate,
    loading: Boolean,
    recorded: Boolean,
    summary: DriveSummary?,
    accent: Color,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthWeekday = remember(today) { monthWeekdayText(today) }
    Column(
        modifier.glass(PlateShape).padding(36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
            Text("${today.dayOfMonth}", style = MaterialTheme.typography.displayLarge)
            Column(Modifier.padding(bottom = 20.dp)) {
                Text(
                    stringResource(R.string.today),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(monthWeekday, style = MaterialTheme.typography.titleMedium)
            }
        }
        todaySummaryText(loading, recorded, summary)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (recorded) {
            IterButton(stringResource(R.string.today_open), onOpen, Modifier.fillMaxWidth(), primary = true)
        }
    }
}

/** The today card, narrow: one glass row with the date and the summary; tapping it opens today's day screen. */
@Composable
private fun TodayRow(
    today: LocalDate,
    loading: Boolean,
    recorded: Boolean,
    summary: DriveSummary?,
    accent: Color,
    onOpen: () -> Unit,
) {
    val dateText = remember(today) { monthDayWeekdayText(today) }
    val openLabel = stringResource(R.string.today_card_open)
    val shape = RoundedCornerShape(36.dp)
    val open = if (recorded) Modifier.clip(shape).clickable(onClickLabel = openLabel, onClick = onOpen) else Modifier
    Column(Modifier.fillMaxWidth().glass(shape).then(open).padding(horizontal = 28.dp, vertical = 18.dp)) {
        Text(
            stringResource(R.string.today),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
        Text(dateText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        todaySummaryText(loading, recorded, summary)?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The calendar plate: in the wide layout the settings button on top, then the weekdays and the
 * grass cells. Only the plate shows while [recordedDates] loads.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CalendarPlate(
    wide: Boolean,
    recordedDates: List<LocalDate>?,
    today: LocalDate,
    todayCellGrows: Boolean,
    dayArt: (LocalDate) -> DayArtData?,
    loadDayArt: suspend (LocalDate) -> Unit,
    panel: ThemePanel,
    accent: Color,
    gridState: LazyGridState,
    playIntro: Boolean,
    onIntroFinished: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebugMenu: (() -> Unit)?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.glass(if (wide) PlateShape else RoundedCornerShape(36.dp)).padding(if (wide) 40.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (wide) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onOpenDebugMenu != null) {
                    TextButton(onClick = onOpenDebugMenu) { Text(stringResource(R.string.debug_menu_open)) }
                }
                SettingsButton(IterButtonSize.MEDIUM.height, onOpenSettings)
            }
        }
        if (recordedDates == null) return@Column
        GrassGrid(
            cells = grassCells(today, recordedDates),
            today = today,
            todayCellGrows = todayCellGrows,
            dayArt = dayArt,
            loadDayArt = loadDayArt,
            panel = panel,
            accent = accent,
            wide = wide,
            gridState = gridState,
            playIntro = playIntro,
            onIntroFinished = onIntroFinished,
            onOpenDay = onOpenDay,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = Modifier.weight(1f),
        )
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
    panel: ThemePanel,
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
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val cellShape = RoundedCornerShape(if (wide) 28.dp else 20.dp)
    val cellGap = if (wide) 12.dp else 6.dp
    val todayMark = panel.art?.todayMarkArgb(panel.dark)?.let { Color(it) } ?: accent

    Column(modifier.then(skipIntro), verticalArrangement = Arrangement.spacedBy(cellGap)) {
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
        BoxWithConstraints(Modifier.weight(1f)) {
            // The plate fits five weeks; a cell is as tall as a fifth of it.
            val rowHeight = ((maxHeight - cellGap * (VISIBLE_WEEKS - 1)) / VISIBLE_WEEKS).coerceAtLeast(0.dp)
            val cellWidth = (maxWidth - cellGap * 6) / 7
            // Room the date takes above the thumbnail.
            val labelRoom = if (wide) 52.dp else 32.dp
            val thumbnailSide = minOf(cellWidth - 10.dp, rowHeight - labelRoom).coerceAtLeast(0.dp)
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(cellGap),
                verticalArrangement = Arrangement.spacedBy(cellGap),
            ) {
                itemsIndexed(cells) { index, cell ->
                    if (cell == null) {
                        Box(Modifier.height(rowHeight))
                        return@itemsIndexed
                    }
                    val description =
                        stringResource(
                            if (cell.recorded) R.string.grass_cell_recorded else R.string.grass_cell_empty,
                            cell.date.format(dateFormat),
                        )
                    val isToday = cell.date == today
                    val intro =
                        Modifier
                            .height(rowHeight)
                            .graphicsLayer {
                                // Read the clock here so the intro redraws without recomposing.
                                val fraction = ((introMs.value - (index - introFirstIndex) * stepMs) / CELL_FADE_MS).coerceIn(0f, 1f)
                                val eased = LinearOutSlowInEasing.transform(fraction)
                                alpha = eased
                                scaleX = 0.8f + 0.2f * eased
                                scaleY = scaleX
                            }
                    val todayRing = if (isToday) Modifier.border(3.dp, todayMark, cellShape) else Modifier
                    val open = if (cell.recorded) Modifier.clip(cellShape).clickable { onOpenDay(cell.date) } else Modifier
                    Column(
                        intro
                            .glass(cellShape, if (cell.recorded) GlassLook.Cell else GlassLook.EmptyCell)
                            .then(todayRing)
                            .then(open)
                            .semantics { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CellLabel(
                            label = grassCellLabel(cell.date, firstCell = index == 0),
                            color = if (isToday) accent else secondary,
                            bold = isToday,
                            modifier =
                            if (wide) {
                                Modifier.align(Alignment.Start).padding(start = 12.dp, top = 10.dp)
                            } else {
                                Modifier.padding(top = 4.dp)
                            },
                        )
                        if (!cell.recorded) return@Column
                        LaunchedEffect(loadDayArt, cell.date) { loadDayArt(cell.date) }
                        val grows =
                            if (!isToday || todayCellGrows) {
                                Modifier.dayArtSharedElement(cell.date, sharedTransitionScope, animatedVisibilityScope)
                            } else {
                                Modifier
                            }
                        Box(Modifier.padding(top = if (wide) 6.dp else 2.dp).size(thumbnailSide).then(grows)) {
                            dayArt(cell.date)?.let { DayArtDisc(it, panel, Modifier.fillMaxSize()) }
                        }
                    }
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
    bold: Boolean,
    modifier: Modifier,
) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = if (bold) FontWeight.Bold else null,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.clearAndSetSemantics {},
    )
}
