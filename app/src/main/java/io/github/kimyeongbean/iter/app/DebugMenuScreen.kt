package io.github.kimyeongbean.iter.app

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
private const val FAKE_RECORD_DAYS = 28

/** Debug menu (development only, hidden in release builds). */
@Composable
internal fun DebugMenuScreen(
    recordStore: RecordStore,
    // Outlives this screen, so leaving mid-insert does not cancel the save.
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var inserting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.debug_menu_title), style = MaterialTheme.typography.headlineMedium)
        Button(
            enabled = !inserting,
            onClick = {
                inserting = true
                scope.launch {
                    val sessions =
                        withContext(Dispatchers.Default) {
                            FakeDriveData.normal(LocalDate.now(), FAKE_RECORD_DAYS, ZoneId.systemDefault())
                        }
                    sessions.forEach { recordStore.save(it) }
                    inserting = false
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.fake_records_inserted, sessions.size),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            },
        ) {
            Text(stringResource(if (inserting) R.string.inserting_fake_records else R.string.insert_fake_records))
        }
    }
}
