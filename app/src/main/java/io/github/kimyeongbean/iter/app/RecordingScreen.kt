package io.github.kimyeongbean.iter.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The only screen while driving (a drive session in progress or the gear not P): a still mark in
 * the theme's line colour [markArgb], "기록 중" large enough to read at a glance, one line saying
 * when the records open again, and a manual end button when [onEnd] is given (a drive session is
 * in progress). Nothing on it moves, so it does not draw the driver's eye.
 */
@Composable
internal fun RecordingScreen(
    markArgb: Int,
    onEnd: (() -> Unit)?,
) {
    val markColor = Color(markArgb)
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Decoration only: the text below says the same thing.
        Canvas(Modifier.size(180.dp).clearAndSetSemantics {}) {
            val ringWidth = size.minDimension * 0.03f
            drawCircle(markColor, radius = size.minDimension * 0.46f, alpha = 0.3f, style = Stroke(ringWidth))
            drawCircle(markColor, radius = size.minDimension * 0.32f, alpha = 0.6f, style = Stroke(ringWidth))
            drawCircle(markColor, radius = size.minDimension * 0.17f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.recording), style = MaterialTheme.typography.displayLarge)
            Text(
                stringResource(R.string.recording_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (onEnd != null) {
            // Large target: pressed at a glance from the driver's seat.
            IterButton(
                stringResource(R.string.end_recording),
                onEnd,
                modifier = Modifier.widthIn(min = 420.dp),
                size = IterButtonSize.LARGE,
            )
        }
    }
}
