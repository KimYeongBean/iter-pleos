package io.github.kimyeongbean.iter.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.kimyeongbean.iter.vehicle.RecordStore
import kotlinx.coroutines.CoroutineScope
import java.time.LocalDate

// Declared from shallow to deep: the screen move slides by this order.
private enum class Screen { GRASS, SETTINGS, DEBUG_MENU }

/** Screen move: fade + small slide, 300 ms (Notion 디자인 '애니메이션·전환 기준'). */
private const val SCREEN_MOVE_MS = 300

/** [appScope] runs work that must outlive screens and the activity. */
@Composable
internal fun IterApp(
    recordStore: RecordStore,
    speedColorSettingsStore: SpeedColorSettingsStore,
    appScope: CoroutineScope,
    showDebugMenu: Boolean,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.GRASS) }
    var speedColorSettings by remember(speedColorSettingsStore) { mutableStateOf(speedColorSettingsStore.load()) }
    var grassIntroPlayed by rememberSaveable { mutableStateOf(false) }
    val recordedDates by remember(recordStore) { recordStore.recordedDates() }.collectAsState(initial = null)

    BackHandler(enabled = screen != Screen.GRASS) { screen = Screen.GRASS }

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
                    playIntro = !grassIntroPlayed,
                    onIntroFinished = { grassIntroPlayed = true },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenDebugMenu = if (showDebugMenu) ({ screen = Screen.DEBUG_MENU }) else null,
                )

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
