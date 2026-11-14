package io.github.kimyeongbean.iter.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** What the settings' privacy section shows and does (#127). */
internal class PrivacyActions(
    val consent: RecordingConsent,
    val onShowNotice: () -> Unit,
    val onAgree: () -> Unit,
    val onWithdraw: () -> Unit,
)

/**
 * The settings' privacy section: what the user agreed to, the notice again, and withdrawing
 * (after a confirmation, since it deletes every record) or agreeing after declining.
 */
@Composable
internal fun PrivacyChoice(privacy: PrivacyActions) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val consent = privacy.consent
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(
                when {
                    consent.recordsLocation -> R.string.privacy_agreed_with_location
                    consent.agreed -> R.string.privacy_agreed
                    else -> R.string.privacy_not_agreed
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IterButton(stringResource(R.string.privacy_show_notice), privacy.onShowNotice, size = IterButtonSize.SMALL)
            if (consent.agreed) {
                IterButton(stringResource(R.string.privacy_withdraw), { confirming = true }, size = IterButtonSize.SMALL)
            } else {
                IterButton(stringResource(R.string.privacy_agree), privacy.onAgree, size = IterButtonSize.SMALL, primary = true)
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.privacy_withdraw_title)) },
            text = { Text(stringResource(R.string.privacy_withdraw_message)) },
            confirmButton = {
                TextButton({
                    confirming = false
                    privacy.onWithdraw()
                }) { Text(stringResource(R.string.privacy_withdraw_confirm)) }
            },
            dismissButton = {
                TextButton({ confirming = false }) { Text(stringResource(R.string.privacy_withdraw_cancel)) }
            },
        )
    }
}
