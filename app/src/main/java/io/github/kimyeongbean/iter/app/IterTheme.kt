package io.github.kimyeongbean.iter.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * App theme colours as ARGB with no Compose types, so tests can check their contrast (#69).
 * Values are the Pleos Connect basic scale tokens (Notion 'Pleos 디자인 가이드·에셋 (Figma 215)').
 * The card colours equal the [MapBackground] panels, so a day art panel sits flush in a card.
 */
internal class IterColors(
    val backgroundArgb: Int,
    val cardArgb: Int,
    val cardHighArgb: Int,
    val controlArgb: Int,
    val textArgb: Int,
    val textSecondaryArgb: Int,
    val outlineArgb: Int,
    val primaryArgb: Int,
    val onPrimaryArgb: Int,
)

internal val IterLightColors =
    IterColors(
        backgroundArgb = 0xFFF7F8FA.toInt(),
        cardArgb = MapBackground.LIGHT.argb,
        cardHighArgb = 0xFFEDEEF2.toInt(),
        controlArgb = 0xFFE0E1E6.toInt(),
        textArgb = 0xFF131417.toInt(),
        textSecondaryArgb = 0xFF686A72.toInt(),
        outlineArgb = 0xFFA4A8AE.toInt(),
        primaryArgb = 0xFF131417.toInt(),
        onPrimaryArgb = 0xFFFFFFFF.toInt(),
    )

internal val IterDarkColors =
    IterColors(
        backgroundArgb = 0xFF131417.toInt(),
        cardArgb = MapBackground.DARK.argb,
        cardHighArgb = 0xFF313236.toInt(),
        controlArgb = 0xFF44464E.toInt(),
        textArgb = 0xFFF7F8FA.toInt(),
        textSecondaryArgb = 0xFFC7C9CE.toInt(),
        outlineArgb = 0xFF686A72.toInt(),
        primaryArgb = 0xFFF7F8FA.toInt(),
        onPrimaryArgb = 0xFF131417.toInt(),
    )

private fun IterColors.toColorScheme(dark: Boolean): ColorScheme {
    val background = Color(backgroundArgb)
    val card = Color(cardArgb)
    val cardHigh = Color(cardHighArgb)
    val text = Color(textArgb)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        onSurfaceVariant = Color(textSecondaryArgb),
        surfaceVariant = cardHigh,
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = cardHigh,
        surfaceContainerHighest = cardHigh,
        primary = Color(primaryArgb),
        onPrimary = Color(onPrimaryArgb),
        secondaryContainer = Color(controlArgb),
        onSecondaryContainer = text,
        outline = Color(outlineArgb),
        outlineVariant = cardHigh,
    )
}

/** Asta Sans, the Pleos system font, bundled so the emulator and the car look the same. One variable file. */
@OptIn(ExperimentalTextApi::class)
private val AstaSans =
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
            Font(
                R.font.asta_sans,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )

private fun style(
    sizeSp: Int,
    lineHeightSp: Int,
    weight: FontWeight,
) = TextStyle(fontFamily = AstaSans, fontWeight = weight, fontSize = sizeSp.sp, lineHeight = lineHeightSp.sp)

/** Pleos type scale: Feature Speed and Headline as display, then Headline / Title / Body / Label. */
private val IterTypography =
    Typography(
        displayLarge = style(112, 134, FontWeight.Bold),
        displayMedium = style(60, 80, FontWeight.Bold),
        displaySmall = style(60, 80, FontWeight.Bold),
        headlineLarge = style(60, 80, FontWeight.Bold),
        headlineMedium = style(56, 72, FontWeight.Bold),
        headlineSmall = style(48, 64, FontWeight.Bold),
        titleLarge = style(40, 52, FontWeight.Bold),
        titleMedium = style(36, 44, FontWeight.Bold),
        titleSmall = style(32, 40, FontWeight.SemiBold),
        bodyLarge = style(30, 38, FontWeight.Normal),
        bodyMedium = style(28, 36, FontWeight.Normal),
        bodySmall = style(26, 34, FontWeight.Normal),
        labelLarge = style(24, 28, FontWeight.Normal),
        labelMedium = style(20, 24, FontWeight.Normal),
        labelSmall = style(20, 24, FontWeight.Normal),
    )

private val IterShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

/** The app theme: Pleos colours, type scale and corners on Material 3. [dark] follows the system. */
@Composable
internal fun IterTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) IterDarkColors else IterLightColors
    MaterialTheme(
        colorScheme = colors.toColorScheme(dark),
        typography = IterTypography,
        shapes = IterShapes,
        content = content,
    )
}

/** Pleos button heights (Figma 215 Basic Button: Small / Medium / Large). */
internal enum class IterButtonSize(
    val height: Dp,
) {
    SMALL(64.dp),
    MEDIUM(80.dp),
    LARGE(100.dp),
}

/** A pill button at a Pleos height. [primary] is the one main action of a screen; the rest are quiet. */
@Composable
internal fun IterButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: IterButtonSize = IterButtonSize.MEDIUM,
    primary: Boolean = false,
) {
    val colors =
        if (primary) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    val textStyle =
        when (size) {
            IterButtonSize.SMALL -> MaterialTheme.typography.labelLarge
            IterButtonSize.MEDIUM -> MaterialTheme.typography.bodyMedium
            IterButtonSize.LARGE -> MaterialTheme.typography.bodyLarge
        }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = size.height),
        colors = colors,
        contentPadding = PaddingValues(horizontal = size.height / 2),
    ) {
        Text(text, style = textStyle, fontWeight = FontWeight.SemiBold)
    }
}

/** A card: the raised ground that groups one thing on a screen. */
@Composable
internal fun IterCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        content = content,
    )
}
