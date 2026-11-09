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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.kimyeongbean.iter.vehicle.RecordStore
import java.time.LocalDate

private enum class Screen { GRASS, DEBUG_MENU }

/** Screen move: fade + small slide, 300 ms (Notion 디자인 '애니메이션·전환 기준'). */
private const val SCREEN_MOVE_MS = 300

@Composable
fun IterApp(
    recordStore: RecordStore,
    showDebugMenu: Boolean,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.GRASS) }
    var grassIntroPlayed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val recordedDates by remember(recordStore) { recordStore.recordedDates() }.collectAsState(initial = null)

    BackHandler(enabled = screen != Screen.GRASS) { screen = Screen.GRASS }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            // Going deeper slides in from the right; coming back slides in from the left.
            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
            val enter = tween<Float>(SCREEN_MOVE_MS, easing = LinearOutSlowInEasing)
            val exit = tween<Float>(SCREEN_MOVE_MS, easing = FastOutLinearInEasing)
            (
                fadeIn(enter) +
                    slideInHorizontally(tween(SCREEN_MOVE_MS, easing = LinearOutSlowInEasing)) { direction * it / 10 }
                ) togetherWith (
                fadeOut(exit) +
                    slideOutHorizontally(tween(SCREEN_MOVE_MS, easing = FastOutLinearInEasing)) { -direction * it / 10 }
                )
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
                    onOpenDebugMenu = if (showDebugMenu) ({ screen = Screen.DEBUG_MENU }) else null,
                )

            Screen.DEBUG_MENU ->
                DebugMenuScreen(
                    recordStore = recordStore,
                    scope = scope,
                    onBack = { screen = Screen.GRASS },
                )
        }
    }
}
