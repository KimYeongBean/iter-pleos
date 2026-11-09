package io.github.kimyeongbean.iter.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.kimyeongbean.iter.art.SPEED_COLOR_MAX_KMH
import io.github.kimyeongbean.iter.art.SpeedColor

/** Background and speed colour changes crossfade over 200 ms (Notion 디자인 '애니메이션·전환 기준'). */
private const val COLOR_CHANGE_MS = 200

/** Speeds sampled for the preview strip's gradient stops. */
private const val PREVIEW_STEP_KMH = 10

/** Settings: speed colour base (five fixed colours) and map graph background, with a 0~120 km/h preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    settings: SpeedColorSettings,
    onChange: (SpeedColorSettings) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        Text(stringResource(R.string.speed_color_base_title), style = MaterialTheme.typography.titleMedium)
        // Swatches sit on the chosen background: each colour has a variant made for that background.
        val swatchBackground by animateColorAsState(Color(settings.background.argb), tween(COLOR_CHANGE_MS), label = "swatchBackground")
        val ring by animateColorAsState(Color(settings.background.textArgb), tween(COLOR_CHANGE_MS), label = "ring")
        Row(
            Modifier
                .selectableGroup()
                .background(swatchBackground, RoundedCornerShape(12.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpeedColorBase.entries.forEach { base ->
                SpeedColorSwatch(
                    color = Color(base.argbOn(settings.background)),
                    ringColor = ring,
                    label = stringResource(base.label),
                    selected = base == settings.base,
                    onClick = { onChange(settings.copy(base = base)) },
                )
            }
        }

        Text(stringResource(R.string.map_background_title), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow {
            MapBackground.entries.forEachIndexed { index, background ->
                SegmentedButton(
                    selected = background == settings.background,
                    onClick = { onChange(settings.copy(background = background)) },
                    shape = SegmentedButtonDefaults.itemShape(index, MapBackground.entries.size),
                ) { Text(stringResource(background.label)) }
            }
        }

        Text(stringResource(R.string.speed_color_preview_title), style = MaterialTheme.typography.titleMedium)
        SpeedColorPreview(settings)
    }
}

@Composable
private fun SpeedColorSwatch(
    color: Color,
    ringColor: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val animated by animateColorAsState(color, tween(COLOR_CHANGE_MS), label = "swatch")
    Box(
        Modifier
            // Whole 64 dp is the touch target (Pleos Radio size); the colour dot sits inside the ring.
            .size(64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .then(if (selected) Modifier.border(4.dp, ringColor, CircleShape) else Modifier)
            .padding(8.dp)
            .background(animated, CircleShape),
    )
}

/** Strip of the speed colour from 0 to 120 km/h on the chosen background. Crossfades on change. */
@Composable
private fun SpeedColorPreview(settings: SpeedColorSettings) {
    Crossfade(targetState = settings, animationSpec = tween(COLOR_CHANGE_MS), label = "preview") { shown ->
        val baseArgb = shown.base.argbOn(shown.background)
        val maxKmh = SPEED_COLOR_MAX_KMH.toInt()
        val stops =
            (0..maxKmh step PREVIEW_STEP_KMH)
                .map { kmh -> kmh.toFloat() / maxKmh to Color(SpeedColor.argb(baseArgb, kmh.toDouble())) }
                .toTypedArray()
        val textColor = Color(shown.background.textArgb)
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(shown.background.argb), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                drawRoundRect(Brush.horizontalGradient(*stops), cornerRadius = CornerRadius(size.height / 2))
            }
            Row(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.speed_kmh, 0),
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.speed_kmh, maxKmh), color = textColor, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
