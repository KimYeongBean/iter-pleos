package io.github.kimyeongbean.iter.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The plate is at most this wide, so the lines stay readable on a wide screen. */
private val PLATE_MAX_WIDTH = 1100.dp

/** What the notice says, item by item (Pleos 심사: 항목, 목적, 보유기간, 거부할 권리; #127). */
private val NOTICE_ITEMS =
    listOf(
        R.string.consent_items_title to R.string.consent_items,
        R.string.consent_purpose_title to R.string.consent_purpose,
        R.string.consent_storage_title to R.string.consent_storage,
        R.string.consent_retention_title to R.string.consent_retention,
        R.string.consent_refuse_title to R.string.consent_refuse,
    )

/**
 * The collection notice (GLOSSARY: 수집 동의, #127) on glass. When [onAnswer] is given it asks:
 * nothing counts as agreeing but the agree button, and location is an extra choice that starts
 * off. Without [onAnswer] it only shows the notice again, with [onClose].
 */
@Composable
internal fun ConsentScreen(
    accent: Color,
    art: ThemeArt?,
    onAnswer: ((RecordingConsent) -> Unit)?,
    onClose: () -> Unit = {},
) {
    var location by rememberSaveable { mutableStateOf(false) }
    GlassScene(accent = accent, art = art) {
        Box(Modifier.fillMaxSize().padding(SCREEN_MARGIN_WIDE), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = PLATE_MAX_WIDTH)
                    .fillMaxWidth()
                    .glass(PlateShape)
                    .verticalScroll(rememberScrollState())
                    .padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Text(stringResource(R.string.consent_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.consent_lead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NOTICE_ITEMS.forEach { (title, body) -> NoticeItem(title, body) }
                if (onAnswer != null) {
                    LocationChoice(location) { location = it }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                    ) {
                        IterButton(
                            stringResource(R.string.consent_decline),
                            { onAnswer(RecordingConsent(RecordingConsent.Answer.DECLINED)) },
                        )
                        IterButton(
                            stringResource(R.string.consent_agree),
                            { onAnswer(RecordingConsent(RecordingConsent.Answer.AGREED, location)) },
                            primary = true,
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IterButton(stringResource(R.string.consent_close), onClose, primary = true)
                    }
                }
            }
        }
    }
}

/** One item of the notice: its name, then what it says. */
@Composable
private fun NoticeItem(
    @StringRes title: Int,
    @StringRes body: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(body), style = MaterialTheme.typography.bodySmall)
    }
}

/** The optional location choice: a whole-row switch that starts off. */
@Composable
private fun LocationChoice(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp))
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.consent_location_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.consent_location_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The row is the toggle; the switch only shows its state.
        Switch(checked = checked, onCheckedChange = null)
    }
}
