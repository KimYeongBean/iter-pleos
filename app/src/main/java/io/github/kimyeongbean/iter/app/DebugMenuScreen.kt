package io.github.kimyeongbean.iter.app

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kimyeongbean.iter.art.FakeDriveData
import io.github.kimyeongbean.iter.vehicle.RecordStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Days of fake drive data that "가짜 기록 넣기" fills, ending today. */
private const val FAKE_DRIVE_DATA_DAYS = 28

/** Debug menu (development only, hidden in release builds). */
@Composable
internal fun DebugMenuScreen(
    recordStore: RecordStore,
    // Outlives this screen and the activity, so leaving mid-insert does not cancel the save.
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    // Application context: the toast may show after the activity is gone.
    val context = LocalContext.current.applicationContext
    var inserting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text(stringResource(R.string.debug_menu_title), style = MaterialTheme.typography.headlineSmall)
        }
        DebugMenuItem(
            title = stringResource(R.string.insert_fake_records),
            hint = stringResource(R.string.insert_fake_records_hint, FAKE_DRIVE_DATA_DAYS),
            action = stringResource(if (inserting) R.string.inserting_fake_records else R.string.insert_fake_records_action),
            enabled = !inserting,
            onClick = {
                inserting = true
                scope.launch {
                    val sessions =
                        try {
                            withContext(Dispatchers.Default) {
                                FakeDriveData.allKinds(LocalDate.now(), FAKE_DRIVE_DATA_DAYS, ZoneId.systemDefault())
                            }.onEach { recordStore.save(it) }
                        } finally {
                            inserting = false
                        }
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.fake_records_inserted, sessions.size),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            },
        )
    }
}

/** One row of the debug menu list: what it does on the left, the button that does it on the right. */
@Composable
private fun DebugMenuItem(
    title: String,
    hint: String,
    action: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IterCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IterButton(action, onClick, primary = true, enabled = enabled)
        }
    }
}
