package io.github.kimyeongbean.iter.app

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kimyeongbean.iter.art.DayArtData
import io.github.kimyeongbean.iter.art.DayArtShape
import io.github.kimyeongbean.iter.art.SPEED_COLOR_MAX_KMH
import io.github.kimyeongbean.iter.art.SpeedColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Background and speed colour changes crossfade over 200 ms (Notion 디자인 '애니메이션·전환 기준'). */
private const val COLOR_CHANGE_MS = 200

/** Side of the sample day art on an advanced theme tile: larger than a plain tile's swatch. */
private val ADVANCED_TILE_ART_SIZE = 132.dp

/** From this width the preview sits right of the choices (Landscape); narrower, it goes below them (Portrait). */
private val WIDE_MIN_WIDTH = 1000.dp

/** Share of the width the choices take in the wide layout; the preview gets the rest. */
private const val CHOICES_WEIGHT = 0.48f

/** Segments of the preview's sample line; its speed rises and falls twice along the way. */
private const val SAMPLE_LINE_SEGMENTS = 48

/**
 * Settings: the theme, in a card per kind (default, plain, advanced), with a live preview. The
 * default theme's card holds the speed colour base (five fixed colours) and the map graph background.
 */
@Composable
internal fun SettingsScreen(
    settings: SpeedColorSettings,
    onChange: (SpeedColorSettings) -> Unit,
    onBack: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_MIN_WIDTH
        val gap = if (wide) 32.dp else 16.dp
        val choices: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
                // The default theme has no tile: picking a colour or a background picks it.
                SettingCard(R.string.theme_default_title) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SpeedColorChoice(settings, onChange)
                        MapBackgroundChoice(settings, onChange, cellHeight = if (wide) IterButtonSize.MEDIUM.height else IterButtonSize.SMALL.height)
                    }
                }
                SettingCard(R.string.theme_plain_title) {
                    PlainThemeChoice(settings, onChange)
                }
                SettingCard(R.string.theme_advanced_title) {
                    AdvancedThemeChoice(settings, onChange)
                }
            }
        }
        Column(
            Modifier.fillMaxSize().padding(if (wide) 48.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBack, if (wide) IterButtonSize.MEDIUM.height else IterButtonSize.SMALL.height)
                Text(
                    stringResource(R.string.settings_title),
                    style = if (wide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                )
            }
            if (wide) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    // Scrolls when the window is too short for both cards.
                    choices(Modifier.weight(CHOICES_WEIGHT).verticalScroll(rememberScrollState()))
                    SpeedColorPreview(settings, Modifier.weight(1f - CHOICES_WEIGHT).fillMaxHeight())
                }
            } else {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    choices(Modifier.fillMaxWidth())
                    SpeedColorPreview(settings, Modifier.fillMaxWidth().height(320.dp))
                }
            }
        }
    }
}

/** A card for one setting: its name, then the choice itself. */
@Composable
private fun SettingCard(
    @StringRes title: Int,
    choice: @Composable () -> Unit,
) {
    IterCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            choice()
        }
    }
}

/**
 * The plain themes (GLOSSARY: 일반 테마): one tile each, on its own panel ground with its line colour
 * as the swatch.
 */
@Composable
private fun PlainThemeChoice(
    settings: SpeedColorSettings,
    onChange: (SpeedColorSettings) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ArtTheme.entries.filter { it.art == null && it != ArtTheme.DEFAULT }.forEach { theme ->
            val panel = theme.panel(settings.base, settings.background, dark)
            val tileColor by animateColorAsState(Color(panel.argb), tween(COLOR_CHANGE_MS), label = "themeTile")
            val textColor by animateColorAsState(Color(panel.textArgb), tween(COLOR_CHANGE_MS), label = "themeTileText")
            val swatch by animateColorAsState(Color(panel.speedColorBaseArgb), tween(COLOR_CHANGE_MS), label = "themeSwatch")
            ThemeTile(theme, settings, onChange, tileColor, textColor, Modifier.weight(1f)) {
                Box(Modifier.size(64.dp).background(swatch, CircleShape))
            }
        }
    }
}

/**
 * The advanced themes (GLOSSARY: 고급 테마): one tile each, told from the plain ones at a glance.
 * A tile is a small piece of the theme itself: its backdrop pattern with a sample day art drawn the
 * theme's way.
 */
@Composable
private fun AdvancedThemeChoice(
    settings: SpeedColorSettings,
    onChange: (SpeedColorSettings) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val sample = remember { sampleDayArt() }
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ArtTheme.entries.forEach { theme ->
            val art = theme.art ?: return@forEach
            val colors = theme.appColors(dark)
            val backdrop =
                Modifier.drawBehind {
                    with(art) { drawBackdrop(dark) }
                }
            ThemeTile(theme, settings, onChange, Color(colors.backgroundArgb), Color(colors.textArgb), Modifier.weight(1f).then(backdrop)) {
                // Decoration only: the tile's label names the theme.
                DayArt(sample, art.panel(dark), Modifier.size(ADVANCED_TILE_ART_SIZE).clearAndSetSemantics {})
            }
        }
    }
}

/** A theme tile: [picture] over the theme's name on [ground]; the chosen one has a strong outline. */
@Composable
private fun ThemeTile(
    theme: ArtTheme,
    settings: SpeedColorSettings,
    onChange: (SpeedColorSettings) -> Unit,
    ground: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    picture: @Composable () -> Unit,
) {
    val selected = theme == settings.theme
    val shape = MaterialTheme.shapes.large
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        Modifier
            .clip(shape)
            .background(ground)
            .then(modifier)
            // The outline keeps a tile apart from a card of nearly the same colour.
            .border(if (selected) 3.dp else 1.dp, outline, shape)
            .selectable(selected = selected, role = Role.RadioButton) { onChange(settings.copy(theme = theme)) }
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        picture()
        Text(
            stringResource(theme.label),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shapes of the sample day art on the advanced theme tiles. */
private const val SAMPLE_SHAPES = 60

/**
 * A made-up day's drawing data for the advanced theme tiles: speed rising and falling a few times,
 * no vehicle signals. Always the same, never saved.
 */
private fun sampleDayArt(): DayArtData {
    val barSpan = DayArtData.SPEED_BAR_MAX_RADIUS - DayArtData.SPEED_BAR_INNER_RADIUS
    return DayArtData(
        List(SAMPLE_SHAPES) { i ->
            val intensity = (0.6 + 0.4 * sin(i * 0.55)).coerceIn(0.2, 1.0)
            DayArtShape(
                timePosition = i.toDouble() / SAMPLE_SHAPES,
                startAngleDeg = i * 360.0 / SAMPLE_SHAPES,
                sweepAngleDeg = 360.0 / SAMPLE_SHAPES,
                innerRadius = DayArtData.SPEED_BAR_INNER_RADIUS,
                outerRadius = DayArtData.SPEED_BAR_INNER_RADIUS + barSpan * intensity,
                // An advanced theme reads speed from the shape's length, not its colour.
                colorArgb = 0,
                patterns = emptySet(),
            )
        },
    )
}

/** Five swatches on the chosen background: each colour has a variant made for that background. */
@Composable
private fun SpeedColorChoice(
    settings: SpeedColorSettings,
    onChange: (SpeedColorSettings) -> Unit,
) {
    val tileColor by animateColorAsState(Color(settings.background.argb), tween(COLOR_CHANGE_MS), label = "tile")
    val textColor by animateColorAsState(Color(settings.background.textArgb), tween(COLOR_CHANGE_MS), label = "tileText")
    val tileShape = MaterialTheme.shapes.large
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SpeedColorBase.entries.forEach { base ->
            val selected = settings.theme == ArtTheme.DEFAULT && base == settings.base
            val swatch by animateColorAsState(Color(base.argbOn(settings.background)), tween(COLOR_CHANGE_MS), label = "swatch")
            Column(
                Modifier
                    .weight(1f)
                    .clip(tileShape)
                    .background(tileColor)
                    .then(if (selected) Modifier.border(3.dp, textColor, tileShape) else Modifier)
                    .selectable(selected = selected, role = Role.RadioButton) { onChange(settings.copy(base = base, theme = ArtTheme.DEFAULT)) }
                    .padding(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(64.dp).background(swatch, CircleShape))
                Text(
                    stringResource(base.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Dark or light, as two cells side by side (Pleos segmented control, 2 cells). */
@Composable
private fun MapBackgroundChoice(
    settings: SpeedColorSettings,
    onChange: (SpeedColorSettings) -> Unit,
    cellHeight: Dp,
) {
    val cellShape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .selectableGroup()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MapBackground.entries.forEach { background ->
            val selected = settings.theme == ArtTheme.DEFAULT && background == settings.background
            // The chosen cell is the strong one: filled in the primary colour, not just a shade lighter.
            val cellColor by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                tween(COLOR_CHANGE_MS),
                label = "cell",
            )
            val cellTextColor by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                tween(COLOR_CHANGE_MS),
                label = "cellText",
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = cellHeight)
                    .clip(cellShape)
                    .background(cellColor)
                    .selectable(selected = selected, role = Role.RadioButton) { onChange(settings.copy(background = background, theme = ArtTheme.DEFAULT)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(background.label), style = MaterialTheme.typography.bodyMedium, color = cellTextColor)
            }
        }
    }
}

/**
 * What the choices look like together: a sample line in the speed colour on the theme's panel,
 * over the 0 to 120 km/h strip. Crossfades on change.
 */
@Composable
private fun SpeedColorPreview(
    settings: SpeedColorSettings,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    Crossfade(
        targetState = settings,
        animationSpec = tween(COLOR_CHANGE_MS),
        label = "preview",
        modifier = modifier.clip(MaterialTheme.shapes.extraLarge),
    ) { shown ->
        val panel = shown.panel(dark)
        val baseArgb = panel.speedColorBaseArgb
        val textColor = Color(panel.textArgb)
        Column(
            Modifier.fillMaxSize().background(Color(panel.argb)).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(stringResource(R.string.speed_color_preview_title), style = MaterialTheme.typography.labelLarge, color = textColor)
            // Decoration only: the strip below carries the same colours with their speeds.
            Canvas(Modifier.weight(1f).fillMaxWidth().clearAndSetSemantics {}) {
                val strokeWidth = size.minDimension * 0.035f

                fun point(step: Int): Offset {
                    val t = step.toFloat() / SAMPLE_LINE_SEGMENTS
                    return Offset(size.width * (0.08f + 0.84f * t), size.height * (0.5f - 0.3f * sin(2 * PI * t).toFloat()))
                }
                repeat(SAMPLE_LINE_SEGMENTS) { step ->
                    val speedKmh = SPEED_COLOR_MAX_KMH * (0.5 - 0.5 * cos(4 * PI * step / SAMPLE_LINE_SEGMENTS))
                    // Opaque, so the round caps of neighbouring segments do not darken where they overlap.
                    val alpha = (SpeedColor.argb(baseArgb, speedKmh) ushr 24) / 255f
                    val color = Color(compositeArgb(baseArgb, alpha, panel.argb))
                    drawLine(color, point(step), point(step + 1), strokeWidth, StrokeCap.Round)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.speed_kmh, 0), style = MaterialTheme.typography.labelMedium, color = textColor)
                SpeedColorStrip(baseArgb, Modifier.weight(1f).height(20.dp))
                Text(
                    stringResource(R.string.speed_kmh, SPEED_COLOR_MAX_KMH.toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                )
            }
        }
    }
}
