package io.github.kimyeongbean.iter.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * The only screen while a drive session is being recorded: a still "기록 중" with a manual end
 * button. Nothing on it moves, so it does not draw the driver's eye.
 */
@Composable
internal fun RecordingScreen(onEnd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.recording), style = MaterialTheme.typography.displayMedium)
        // Large target: pressed while driving.
        Button(onClick = onEnd, modifier = Modifier.widthIn(min = 200.dp).heightIn(min = 72.dp)) {
            Text(stringResource(R.string.end_recording), style = MaterialTheme.typography.titleLarge)
        }
    }
}
