package io.github.kimyeongbean.iter.app

import android.content.Context
import android.provider.Settings
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.kimyeongbean.iter.art.DayArtData
import io.github.kimyeongbean.iter.vehicle.RecordStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

// Declared from shallow to deep: the screen move slides by this order.
private enum class Screen { GRASS, DAY, SETTINGS, DEBUG_MENU }

/** Screen move: fade + small slide, 300 ms (Notion 디자인 '애니메이션·전환 기준'). */
private const val SCREEN_MOVE_MS = 300

/** [appScope] runs work that must outlive screens and the activity. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun IterApp(
    recordStore: RecordStore,
    speedColorSettingsStore: SpeedColorSettingsStore,
    appScope: CoroutineScope,
    showDebugMenu: Boolean,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.GRASS) }
    var openDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var speedColorSettings by remember(speedColorSettingsStore) { mutableStateOf(speedColorSettingsStore.load()) }
    var grassIntroPlayed by rememberSaveable { mutableStateOf(false) }
    // Dates whose day art already drew in; it draws in once, on the first open.
    var drawnInDates by rememberSaveable { mutableStateOf(emptySet<LocalDate>()) }
    val grassGridState = rememberLazyGridState()
    val context = LocalContext.current
    val animationsOff = remember(context) { animatorDurationScale(context) == 0f }

    // Every emission means records changed, even when the date list stays equal,
    // so count emissions to know when drawing data must be computed again.
    var recordedDates by remember { mutableStateOf<List<LocalDate>?>(null) }
    var recordsVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(recordStore) {
        recordStore.recordedDates().collect {
            recordedDates = it
            recordsVersion++
        }
    }
    val background = speedColorSettings.background
    val speedColorBaseArgb = speedColorSettings.base.argbOn(background)
    val dayArts =
        remember(recordStore, recordsVersion, speedColorBaseArgb) {
            DayArtCache { date ->
                val dayRecord = recordStore.dayRecord(date)
                withContext(Dispatchers.Default) { DayArtData.of(dayRecord, speedColorBaseArgb) }
            }
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
                Screen.GRASS ->
                    GrassScreen(
                        today = LocalDate.now(),
                        recordedDates = recordedDates,
                        dayArts = dayArts,
                        background = background,
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
                        onOpenDebugMenu = if (showDebugMenu) ({ screen = Screen.DEBUG_MENU }) else null,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedContent,
                    )

                Screen.DAY -> {
                    val date = openDate ?: return@AnimatedContent
                    LaunchedEffect(dayArts, date) { dayArts.load(date) }
                    DayArtScreen(
                        date = date,
                        data = dayArts[date],
                        background = background,
                        playDrawIn = date !in drawnInDates && !animationsOff,
                        onDrawInStarted = { drawnInDates = drawnInDates + date },
                        onBack = { screen = Screen.GRASS },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedContent,
                    )
                }

                Screen.SETTINGS ->
                    SettingsScreen(
                        settings = speedColorSettings,
                        onChange = {
                            speedColorSettings = it
                            speedColorSettingsStore.save(it)
                        },
                        onBack = { screen = Screen.GRASS },
                    )

                Screen.DEBUG_MENU ->
                    DebugMenuScreen(
                        recordStore = recordStore,
                        scope = appScope,
                        onBack = { screen = Screen.GRASS },
                    )
            }
        }
    }
}

/** System animator duration scale; 0 means the user turned animations off. */
private fun animatorDurationScale(context: Context): Float = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
