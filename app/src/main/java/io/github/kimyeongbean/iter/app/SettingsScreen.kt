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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.kimyeongbean.iter.art.DayArtData
import io.github.kimyeongbean.iter.art.DayArtShape
import io.github.kimyeongbean.iter.art.SPEED_COLOR_MAX_KMH
import io.github.kimyeongbean.iter.art.SpeedColor
import kotlin.math.sin

/** Background and speed colour changes crossfade over 200 ms (Notion 디자인 '애니메이션·전환 기준'). */
private const val COLOR_CHANGE_MS = 200

/** From this width the preview sits left of the choices (Landscape); narrower, the choices come first (Portrait). */
private val WIDE_MIN_WIDTH = 1000.dp

/** Share of the width the choices plate takes in the wide layout (932 of 1680 dp in 시안 B). */
private const val CHOICES_WIDTH_SHARE = 0.555f

/** The preview card leaves this much of the left side free on its right, so the big art shows. */
private val PREVIEW_CARD_RIGHT_ROOM = 108.dp

/** A theme tile: its height, and the side of an advanced theme's sample day art on it. */
private val THEME_TILE_HEIGHT = 148.dp
private val TILE_ART_SIZE = 84.dp

/** The plain line sample on a default or plain theme tile, in its 96 x 72 box. */
private val WAVE_SIZE = 96.dp
private const val WAVE_ASPECT = 72f / 96f

/**
 * Settings on glass (시안 B): the chosen theme's day art large behind everything with a small
 * preview card, and one glass plate with the screen mode, the theme tiles (default, plain and
 * advanced themes together) and the default theme's colour and background.
 */
@Composable
internal fun SettingsScreen(
    settings: SpeedColorSettings,
    onChange: (SpeedColorSettings) -> Unit,
    onBack: () -> Unit,
    animateBackdrop: Boolean,
    privacy: PrivacyActions,
) {
    val dark = isSystemInDarkTheme()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_MIN_WIDTH
        val screenWidth = maxWidth
        val artSide: Dp
        val artAt: DpOffset
        if (wide) {
            val scale = maxHeight.value / MOCKUP_HEIGHT_DP
            artSide = 1000.dp * scale
            artAt = DpOffset((-110).dp * scale, 75.dp * scale)
        } else {
            artSide = maxWidth * 0.9f
            artAt = DpOffset(maxWidth * 0.3f, -maxWidth * 0.2f)
        }
        GlassScene(
            accent = Color(settings.accentArgb(dark)),
            art = settings.theme.art,
            moving = animateBackdrop,
            backdrop = {
                // The preview: a made-up day drawn the chosen theme's way. Decoration only.
                Crossfade(settings, animationSpec = tween(COLOR_CHANGE_MS), label = "preview") { shown ->
                    val panel = shown.panel(dark)
                    val sample = remember(panel.speedColorBaseArgb) { sampleDayArt(panel.speedColorBaseArgb) }
                    DayArtDisc(sample, panel, Modifier.offset(artAt.x, artAt.y).size(artSide).clearAndSetSemantics {}, behindPlates = true)
                }
            },
        ) {
            if (wide) {
                val choicesWidth = screenWidth * CHOICES_WIDTH_SHARE
                val leftWidth = screenWidth - choicesWidth - SCREEN_MARGIN_WIDE
                PreviewCard(
                    settings,
                    dark,
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(SCREEN_MARGIN_WIDE)
                        .width(leftWidth - PREVIEW_CARD_RIGHT_ROOM),
                )
                Choices(
                    settings,
                    onChange,
                    onBack,
                    privacy,
                    dark,
                    // Scrolls when the window is too short for all the choices.
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(SCREEN_MARGIN_WIDE)
                        .width(choicesWidth)
                        .fillMaxHeight()
                        .glass(PlateShape)
                        .verticalScroll(rememberScrollState())
                        .padding(40.dp),
                )
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SCREEN_MARGIN_NARROW),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Choices(settings, onChange, onBack, privacy, dark, Modifier.fillMaxWidth().glass(RoundedCornerShape(36.dp)).padding(24.dp))
                    PreviewCard(settings, dark, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Back button and title, then each setting under its name. */
@Composable
private fun Choices(
    settings: SpeedColorSettings,
    onChange: (SpeedColorSettings) -> Unit,
    onBack: () -> Unit,
    privacy: PrivacyActions,
    dark: Boolean,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack, IterButtonSize.MEDIUM.height)
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        }
        Section(R.string.screen_mode_title) {
            SegmentedChoice(
                choices = ScreenMode.entries,
                chosen = settings.screenMode,
                label = { it.label },
            ) { onChange(settings.copy(screenMode = it)) }
        }
        Section(R.string.theme_title) {
            ThemeChoice(settings, onChange, dark)
        }
        Section(R.string.theme_default_title, hint = R.string.theme_default_hint) {
            SpeedColorChoice(settings, onChange)
            SegmentedChoice(
                choices = MapBackground.entries,
                chosen = settings.background.takeIf { settings.theme == ArtTheme.DEFAULT },
                label = { it.label },
            ) { onChange(settings.copy(background = it, theme = ArtTheme.DEFAULT)) }
        }
        Section(R.string.privacy_title) {
            PrivacyChoice(privacy)
        }
    }
}

/** One setting: its name (and a hint beside it), then the choice. */
@Composable
private fun Section(
    @StringRes title: Int,
    @StringRes hint: Int? = null,
    choice: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.alignByBaseline())
            if (hint != null) {
                Text(
                    stringResource(hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        choice()
    }
}

/**
 * All themes (GLOSSARY: 테마) as tiles, four to a row: the default and plain themes on their panel
 * ground with their line as a sample, the advanced themes as a small piece of the theme itself (its
 * backdrop pattern with a sample day art drawn its way). Picking the default tile keeps the chosen
 * colour and background.
 */
@Composable
private fun ThemeChoice(
    settings: SpeedColorSettings,
    onChange: (SpeedColorSettings) -> Unit,
    dark: Boolean,
) {
    val sample = remember { sampleDayArt(baseArgb = 0) }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ArtTheme.entries.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { theme -> ThemeTile(theme, settings, onChange, dark, sample, Modifier.weight(1f)) }
            }
        }
    }
}

/** A theme tile: a picture of the theme over its name; the chosen one has a strong outline. */
@Composable
private fun ThemeTile(
    theme: ArtTheme,
    settings: SpeedColorSettings,
    onChange: (SpeedColorSettings) -> Unit,
    dark: Boolean,
    sample: DayArtData,
    modifier: Modifier,
) {
    val selected = theme == settings.theme
    val shape = MaterialTheme.shapes.large
    val art = theme.art
    val panel = theme.panel(settings.base, settings.background, dark)
    val appColors = theme.appColors(dark)
    val ground by animateColorAsState(
        Color(if (art != null) appColors.backgroundArgb else panel.argb),
        tween(COLOR_CHANGE_MS),
        label = "themeTile",
    )
    val textColor by animateColorAsState(
        Color(if (art != null) appColors.textArgb else panel.textArgb),
        tween(COLOR_CHANGE_MS),
        label = "themeTileText",
    )
    val line by animateColorAsState(Color(panel.speedColorBaseArgb), tween(COLOR_CHANGE_MS), label = "themeLine")
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val backdrop = if (art != null) Modifier.drawBehind { with(art) { drawBackdrop(dark) } } else Modifier
    Column(
        modifier
            .height(THEME_TILE_HEIGHT)
            .clip(shape)
            .background(ground)
            .then(backdrop)
            // The outline keeps a tile apart from glass of nearly the same colour.
            .border(if (selected) 3.dp else 1.dp, outline, shape)
            .selectable(selected = selected, role = Role.RadioButton) { onChange(settings.copy(theme = theme)) },
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Decoration only: the tile's label names the theme.
        if (art != null) {
            DayArt(sample, art.panel(dark), Modifier.size(TILE_ART_SIZE).clearAndSetSemantics {})
        } else {
            Wave(line, Modifier.width(WAVE_SIZE).height(WAVE_SIZE * WAVE_ASPECT).clearAndSetSemantics {})
        }
        Text(
            stringResource(theme.label),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = if (selected) FontWeight.Bold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A short curvy line in [color], the sample on a default or plain theme tile. */
@Composable
private fun Wave(
    color: Color,
    modifier: Modifier,
) {
    Canvas(modifier) {
        // Drawn on a 96 x 72 grid.
        val unit = size.width / 96f
        val path =
            Path().apply {
                moveTo(8 * unit, 52 * unit)
                cubicTo(24 * unit, 8 * unit, 40 * unit, 8 * unit, 48 * unit, 36 * unit)
                cubicTo(56 * unit, 64 * unit, 72 * unit, 64 * unit, 88 * unit, 20 * unit)
            }
        drawPath(path, color, style = Stroke(7 * unit, cap = StrokeCap.Round))
    }
}

/** Shapes of the sample day art. */
private const val SAMPLE_SHAPES = 60

/**
 * A made-up day's drawing data: speed rising and falling a few times, no vehicle signals, in the
 * speed colour of [baseArgb] (an advanced theme reads speed from the shape's length, not its
 * colour). Always the same, never saved.
 */
private fun sampleDayArt(baseArgb: Int): DayArtData {
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
                colorArgb = SpeedColor.argb(baseArgb, SPEED_COLOR_MAX_KMH * intensity),
                patterns = emptySet(),
            )
        },
    )
}

/**
 * Under the big art: the chosen theme's name, "미리보기" and the speed strip from 0 to 120 km/h.
 * A plain theme's strip sits on its panel colour, which its colours are made for.
 */
@Composable
private fun PreviewCard(
    settings: SpeedColorSettings,
    dark: Boolean,
    modifier: Modifier,
) {
    val panel = settings.panel(dark)
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val label = MaterialTheme.typography.labelMedium
    val stripGround = if (panel.art == null) Modifier.background(Color(panel.argb), RoundedCornerShape(10.dp)) else Modifier
    Column(
        modifier.glass(RoundedCornerShape(40.dp)).padding(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(settings.theme.label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.alignByBaseline())
            Text(
                stringResource(R.string.speed_color_preview_title),
                style = MaterialTheme.typography.labelLarge,
                color = secondary,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.speed_kmh, 0), style = label, color = secondary)
            SpeedColorStrip(panel.speedColorBaseArgb, Modifier.weight(1f).height(20.dp).then(stripGround))
            Text(stringResource(R.string.speed_kmh, SPEED_COLOR_MAX_KMH.toInt()), style = label, color = secondary)
        }
    }
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
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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

/** One of a few [choices], as cells side by side on a glass track (Pleos segmented control). [chosen] may be none. */
@Composable
private fun <T> SegmentedChoice(
    choices: List<T>,
    chosen: T?,
    label: (T) -> Int,
    onChoose: (T) -> Unit,
) {
    val cellShape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup()
            .glass(MaterialTheme.shapes.large, GlassLook.Track)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { choice ->
            val selected = choice == chosen
            // The chosen cell is the strong one: filled in the primary colour, not just a shade lighter.
            val cellColor by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                tween(COLOR_CHANGE_MS),
                label = "cell",
            )
            val cellTextColor by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                tween(COLOR_CHANGE_MS),
                label = "cellText",
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = IterButtonSize.MEDIUM.height)
                    .clip(cellShape)
                    .background(cellColor)
                    .selectable(selected = selected, role = Role.RadioButton) { onChoose(choice) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(label(choice)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cellTextColor,
                    fontWeight = if (selected) FontWeight.SemiBold else null,
                )
            }
        }
    }
}
