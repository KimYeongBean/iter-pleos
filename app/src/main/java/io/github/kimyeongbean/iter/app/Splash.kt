package io.github.kimyeongbean.iter.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/** The splash mark draws in over this long; the app name comes in with the end of it. */
internal const val SPLASH_DRAW_MS = 700

/** The finished mark stands this long, then the splash fades away over the first screen. */
private const val SPLASH_HOLD_MS = 150
private const val SPLASH_LEAVE_MS = 200

/** The app name comes in over this last share of the draw-in. */
private const val NAME_SPAN = 0.45f

/** The mark's box, as a share of the screen height. */
private const val MARK_HEIGHT_SHARE = 0.42f

/** While the splash fades away it also grows by this share. */
private const val LEAVE_GROWTH = 0.06f

/**
 * Shows the splash (GLOSSARY: 스플래시) over [app], then fades it away. [app] is composed from the
 * start, so it loads under the splash and nothing waits for it. Nothing shows when [skip]: the splash
 * already played in this process, animations are off, or the car is driving and only the still
 * "기록 중" screen may show. A tap ends the splash at once.
 */
@Composable
internal fun SplashThenApp(
    theme: ArtTheme,
    accentArgb: Int,
    dark: Boolean,
    skip: Boolean,
    app: @Composable () -> Unit,
) {
    var over by remember { mutableStateOf(skip) }
    val elapsedMs = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (over) return@LaunchedEffect
        val totalMs = SPLASH_DRAW_MS + SPLASH_HOLD_MS + SPLASH_LEAVE_MS
        elapsedMs.animateTo(totalMs.toFloat(), tween(totalMs, easing = LinearEasing))
        over = true
    }
    // Once skipped it stays over: the splash must not start when the drive ends.
    LaunchedEffect(skip) { if (skip) over = true }
    Box(Modifier.fillMaxSize()) {
        app()
        if (!over && !skip) Splash(theme, accentArgb, dark, elapsedMs = { elapsedMs.value }, onSkip = { over = true })
    }
}

/**
 * The splash [elapsedMs] in: the theme's mark draws in and the app name comes in under it, on the
 * app background with the theme's backdrop pattern. [elapsedMs] is read only while drawing.
 */
@Composable
internal fun Splash(
    theme: ArtTheme,
    accentArgb: Int,
    dark: Boolean,
    elapsedMs: () -> Float,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val art = theme.art
    val accent = Color(accentArgb)

    fun drawn() = (elapsedMs() / SPLASH_DRAW_MS).coerceIn(0f, 1f)

    fun left() = ((elapsedMs() - SPLASH_DRAW_MS - SPLASH_HOLD_MS) / SPLASH_LEAVE_MS).coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - left() }
            .background(MaterialTheme.colorScheme.background)
            // Also keeps taps from the first screen under the fade.
            .pointerInput(onSkip) { detectTapGestures { onSkip() } },
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) Canvas(Modifier.fillMaxSize()) { with(art) { drawBackdrop(dark) } }
        Column(
            modifier =
            Modifier.graphicsLayer {
                scaleX = 1f + LEAVE_GROWTH * left()
                scaleY = scaleX
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.fillMaxHeight(MARK_HEIGHT_SHARE).aspectRatio(1f, matchHeightConstraintsFirst = true)) {
                if (art != null) with(art) { drawSplash(dark, drawn()) } else drawRouteSplash(accent, drawn())
            }
            Text(
                text = stringResource(R.string.app_name),
                modifier =
                Modifier.graphicsLayer {
                    val shown = FastOutSlowInEasing.transform(grownFraction(drawn(), 1 - NAME_SPAN, NAME_SPAN))
                    alpha = shown
                    translationY = (1 - shown) * 16.dp.toPx()
                },
                style = MaterialTheme.typography.headlineLarge,
                letterSpacing = 0.12.em,
            )
        }
    }
}

/** The splash track's corners in a unit square, in driving order from the start line. */
private val SPLASH_TRACK_CORNERS =
    listOf(
        Offset(0.50f, 0.80f),
        Offset(0.26f, 0.80f),
        Offset(0.12f, 0.66f),
        Offset(0.20f, 0.48f),
        Offset(0.38f, 0.50f),
        Offset(0.48f, 0.38f),
        Offset(0.40f, 0.22f),
        Offset(0.58f, 0.14f),
        Offset(0.80f, 0.20f),
        Offset(0.90f, 0.40f),
        Offset(0.78f, 0.54f),
        Offset(0.86f, 0.70f),
        Offset(0.72f, 0.80f),
    )

/** A closed smooth line through [SPLASH_TRACK_CORNERS] (a Catmull-Rom spline), made once. */
private val splashTrack: Path by lazy {
    val corners = SPLASH_TRACK_CORNERS
    Path().apply {
        moveTo(corners[0].x, corners[0].y)
        corners.indices.forEach { i ->
            val before = corners[(i - 1 + corners.size) % corners.size]
            val from = corners[i]
            val to = corners[(i + 1) % corners.size]
            val after = corners[(i + 2) % corners.size]
            val leave = from + (to - before) / 6f
            val arrive = to - (after - from) / 6f
            cubicTo(leave.x, leave.y, arrive.x, arrive.y, to.x, to.y)
        }
    }
}
private val splashTrackMeasure: PathMeasure by lazy { PathMeasure().apply { setPath(splashTrack, forceClosed = false) } }

/** Line width and the moving head's radius, as shares of the box side. */
private const val TRACK_WIDTH = 0.036f
private const val TRACK_HEAD_RADIUS = 0.04f

/**
 * The splash mark of the default and the plain themes: a route line in [color] runs one lap of a
 * track over the faint whole of it, a dot at its head, as [progress] goes 0 to 1.
 */
internal fun DrawScope.drawRouteSplash(
    color: Color,
    progress: Float,
) {
    val side = size.minDimension
    val lapLength = splashTrackMeasure.length * FastOutSlowInEasing.transform(progress)
    val stroke = Stroke(TRACK_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val driven = Path()
    splashTrackMeasure.getSegment(0f, lapLength, driven, startWithMoveTo = true)
    withTransform({
        translate((size.width - side) / 2, (size.height - side) / 2)
        scale(side, side, pivot = Offset.Zero)
    }) {
        drawPath(splashTrack, color, alpha = DRAW_IN_GHOST_ALPHA, style = stroke)
        drawPath(driven, color, style = stroke)
        drawCircle(color, TRACK_HEAD_RADIUS, splashTrackMeasure.getPosition(lapLength))
    }
}
