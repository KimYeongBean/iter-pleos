package io.github.kimyeongbean.iter.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The "기록 중" plate's size in 시안 B; it narrows to fit a narrow screen. */
private val PLATE_WIDTH = 804.dp
private val PLATE_HEIGHT_WITH_END = 560.dp
private val PlateCorner = RoundedCornerShape(56.dp)

/** The still mark's centre, as a share of the screen width, and its rings in dp at the mockup's height. */
private const val MARK_CENTRE_X = 600f / 1680f
private const val MARK_OUTER_RADIUS_DP = 720f
private const val MARK_MIDDLE_RADIUS_DP = 480f
private const val MARK_INNER_RADIUS_DP = 230f
private const val MARK_RING_WIDTH_DP = 18f

/** Width of the manual end button: large, pressed at a glance from the driver's seat. */
private val END_BUTTON_WIDTH = 420.dp

/**
 * The only screen while driving (a drive session in progress or the gear not P): a large still
 * mark in the theme's line colour [markArgb] running off the left edge, and one glass plate with
 * "기록 중" large enough to read at a glance, one line saying when the records open again, and a
 * manual end button when [onEnd] is given (a drive session is in progress). [art] gives the
 * backdrop pattern. Nothing on it moves, so it does not draw the driver's eye.
 */
@Composable
internal fun RecordingScreen(
    markArgb: Int,
    art: ThemeArt?,
    onEnd: (() -> Unit)?,
) {
    val markColor = Color(markArgb)
    GlassScene(
        accent = markColor,
        art = art,
        backdrop = {
            // Decoration only: the text says the same thing.
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                val scale = size.height / (MOCKUP_HEIGHT_DP * density)
                val centre = Offset(size.width * MARK_CENTRE_X, size.height / 2)
                val ring = MARK_RING_WIDTH_DP * density * scale
                fun radius(dp: Float) = dp * density * scale
                drawCircle(markColor, radius(MARK_OUTER_RADIUS_DP) - ring / 2, centre, alpha = 0.3f, style = Stroke(ring))
                drawCircle(markColor, radius(MARK_MIDDLE_RADIUS_DP) - ring / 2, centre, alpha = 0.6f, style = Stroke(ring))
                drawCircle(markColor, radius(MARK_INNER_RADIUS_DP), centre)
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(SCREEN_MARGIN_NARROW), contentAlignment = Alignment.Center) {
            val plate =
                Modifier
                    .width(minOf(PLATE_WIDTH, maxWidth))
                    .then(if (onEnd != null) Modifier.height(PLATE_HEIGHT_WITH_END) else Modifier)
                    .glass(PlateCorner, GlassLook.Strong)
                    .padding(64.dp)
            Column(
                plate,
                verticalArrangement = if (onEnd != null) Arrangement.SpaceBetween else Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.recording), style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
                    Text(
                        stringResource(R.string.recording_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (onEnd != null) EndButton(onEnd)
            }
        }
    }
}

/** The manual end button: a nearly solid glass pill at the Pleos large button height. */
@Composable
private fun EndButton(onEnd: () -> Unit) {
    val shape = RoundedCornerShape(IterButtonSize.LARGE.height / 2)
    Surface(
        onClick = onEnd,
        shape = shape,
        color = Color.Transparent,
        modifier = Modifier.widthIn(min = END_BUTTON_WIDTH).height(IterButtonSize.LARGE.height).glass(shape, GlassLook.Button),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.end_recording), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
