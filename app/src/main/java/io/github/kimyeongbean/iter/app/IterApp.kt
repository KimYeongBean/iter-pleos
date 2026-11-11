package io.github.kimyeongbean.iter.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.kimyeongbean.iter.art.DayArtData
import io.github.kimyeongbean.iter.vehicle.DriveSessionRecorder
import io.github.kimyeongbean.iter.vehicle.RecordStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

// Declared from shallow to deep: the screen move slides by this order.
private enum class Screen { GRASS, DAY, SETTINGS, DEBUG_MENU }

/** Screen move: fade + small slide, 300 ms (Notion 디자인 '애니메이션·전환 기준'). */
private const val SCREEN_MOVE_MS = 300

/**
 * While [recorder] has a drive session in progress, only the still "기록 중" screen shows; the record
 * screens open again once it ends. [settings] is what the user picked; [onSettingsChange] keeps a
 * new pick. [appScope] runs work that must outlive screens and the activity. [animationsOff] is
 * whether the user turned system animations off.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun IterApp(
    recordStore: RecordStore,
    recorder: DriveSessionRecorder,
    settings: SpeedColorSettings,
    onSettingsChange: (SpeedColorSettings) -> Unit,
    appScope: CoroutineScope,
    showDebugMenu: Boolean,
    animationsOff: Boolean,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.GRASS) }
    var openDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var grassIntroPlayed by rememberSaveable { mutableStateOf(false) }
    // Dates whose day art already drew in; it draws in once, on the first open.
    var drawnInDates by rememberSaveable { mutableStateOf(emptySet<LocalDate>()) }
    val grassGridState = rememberLazyGridState()

    // An emission can mean a day's sessions changed even when the date list stays equal
    // (e.g. a session saved on an already recorded date), so count emissions.
    var recordedDates by remember { mutableStateOf<List<LocalDate>?>(null) }
    var recordsVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(recordStore) {
        recordStore.recordedDates().collect {
            recordedDates = it
            recordsVersion++
        }
    }
    val dark = isSystemInDarkTheme()
    val panel = settings.panel(dark)
    val speedColorBaseArgb = panel.speedColorBaseArgb
    val dayArts = remember(recordStore) { DayArtCache() }
    // A new lambda whenever records or colours change, so screens keyed on it load again.
    val loadDayArt: suspend (LocalDate) -> Unit =
        remember(dayArts, recordsVersion, speedColorBaseArgb) {
            { date ->
                dayArts.load(date, inputs = recordsVersion to speedColorBaseArgb) {
                    val dayRecord = recordStore.dayRecord(date)
                    withContext(Dispatchers.Default) { DayArtData.of(dayRecord, speedColorBaseArgb) }
                }
            }
        }

    val driving by recorder.driving.collectAsState()
    val inProgress by recorder.inProgress.collectAsState()
    if (driving) {
        // Swapped in without a transition: leaving the record screens stops their animations at once.
        RecordingScreen(settings.accentArgb(dark), settings.theme.art, onEnd = if (inProgress) recorder::requestEnd else null)
        return
    }

    BackHandler(enabled = screen != Screen.GRASS) { screen = Screen.GRASS }

    SharedTransitionLayout {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                // Going deeper slides in from the right; coming back slides in from the left.
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1

                // Appearing decelerates, disappearing accelerates.
                fun <T> enter() = tween<T>(SCREEN_MOVE_MS, easing = LinearOutSlowInEasing)

                fun <T> exit() = tween<T>(SCREEN_MOVE_MS, easing = FastOutLinearInEasing)
                (fadeIn(enter()) + slideInHorizontally(enter()) { direction * it / 10 }) togetherWith
                    (fadeOut(exit()) + slideOutHorizontally(exit()) { -direction * it / 10 })
            },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.GRASS -> {
                    val today = LocalDate.now()
                    val todaySummary by produceState<DriveSummary?>(null, today, recordsVersion) {
                        value = driveSummary(recordStore.dayRecord(today), ZoneId.systemDefault())
                    }
                    GrassScreen(
                        today = today,
                        recordedDates = recordedDates,
                        dayArt = { dayArts[it] },
                        loadDayArt = loadDayArt,
                        todaySummary = todaySummary,
                        panel = panel,
                        accent = Color(settings.accentArgb(dark)),
                        gridState = grassGridState,
                        playIntro = !grassIntroPlayed,
                        onIntroFinished = { grassIntroPlayed = true },
                        onOpenDay = {
                            // Leaving mid-intro would otherwise replay the intro on the way back.
                            grassIntroPlayed = true
                            openDate = it
                            screen = Screen.DAY
                        },
                        onOpenSettings = { screen = Screen.SETTINGS },
                        animateBackdrop = !animationsOff,
                        onOpenDebugMenu = if (showDebugMenu) ({ screen = Screen.DEBUG_MENU }) else null,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedContent,
                    )
                }

                Screen.DAY -> {
                    val date = openDate ?: return@AnimatedContent
                    LaunchedEffect(loadDayArt, date) { loadDayArt(date) }
                    // Computed before drawing, so the draw-in only draws. Keeps the previous
                    // route while recomputing after records or colours change.
                    val route by produceState<MapRoute?>(null, date, recordsVersion, speedColorBaseArgb) {
                        val dayRecord = recordStore.dayRecord(date)
                        value = withContext(Dispatchers.Default) { mapRoute(dayRecord, speedColorBaseArgb) }
                    }
                    val summary by produceState<DriveSummary?>(null, date, recordsVersion) {
                        value = driveSummary(recordStore.dayRecord(date), ZoneId.systemDefault())
                    }
                    DayScreen(
                        date = date,
                        dayArt = dayArts[date],
                        route = route,
                        summary = summary,
                        panel = panel,
                        accent = Color(settings.accentArgb(dark)),
                        playDrawIn = date !in drawnInDates && !animationsOff,
                        animateLoop = !animationsOff,
                        onDrawInStarted = { drawnInDates = drawnInDates + date },
                        onBack = { screen = Screen.GRASS },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedContent,
                    )
                }

                Screen.SETTINGS ->
                    SettingsScreen(
                        settings = settings,
                        onChange = onSettingsChange,
                        onBack = { screen = Screen.GRASS },
                        animateBackdrop = !animationsOff,
                    )

                Screen.DEBUG_MENU ->
                    DebugMenuScreen(
                        recordStore = recordStore,
                        onStartRecording = recorder::requestStart,
                        scope = appScope,
                        onBack = { screen = Screen.GRASS },
                    )
            }
        }
    }
}
