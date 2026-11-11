package io.github.kimyeongbean.iter.app

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * How a glass plate (GLOSSARY: 유리판) looks. Fill opacities are for the light screen mode, a
 * gradient from the top left to the bottom right; the dark mode makes them stronger.
 * [blur] is how much of what is behind shows blurred through it: 0 for a plate on another plate,
 * where blurring the scene behind both would be wrong.
 */
@Immutable
internal class GlassLook(
    val fillFrom: Float,
    val fillTo: Float,
    val edgeAlpha: Float,
    val blur: Dp,
    val elevation: Dp,
    val sheen: Boolean,
) {
    companion object {
        /** A plate on the scene: cards, the calendar, the day screen pillar. */
        val Panel = GlassLook(0.32f, 0.12f, 0.85f, blur = 32.dp, elevation = 24.dp, sheen = true)

        /**
         * The "기록 중" plate: whiter than the others, so its text reads at a glance and keeps 4.5:1
         * over the blurred mark behind it.
         */
        val Strong = GlassLook(0.58f, 0.42f, 0.9f, blur = 32.dp, elevation = 24.dp, sheen = true)

        /** A small label on the scene, such as the start and end time on the map graph. */
        val Chip = GlassLook(0.7f, 0.7f, 1f, blur = 20.dp, elevation = 8.dp, sheen = false)

        /** A round button or a segmented control's track on a plate. */
        val Control = GlassLook(0.75f, 0.75f, 1f, blur = 0.dp, elevation = 0.dp, sheen = false)
        val Track = GlassLook(0.55f, 0.55f, 0.9f, blur = 0.dp, elevation = 0.dp, sheen = false)

        /** The round window the day art sits in on the day screen. */
        val Window = GlassLook(0.6f, 0.6f, 1f, blur = 0.dp, elevation = 0.dp, sheen = false)

        /** Grass cells: a recorded day stands out, a day with no record is nearly clear. */
        val Cell = GlassLook(0.55f, 0.2f, 0.95f, blur = 0.dp, elevation = 0.dp, sheen = false)
        val EmptyCell = GlassLook(0.08f, 0.08f, 0.55f, blur = 0.dp, elevation = 0.dp, sheen = false)

        /** The "기록 끝" button: nearly solid, a large target. */
        val Button = GlassLook(0.85f, 0.85f, 1f, blur = 0.dp, elevation = 0.dp, sheen = false)
    }
}

/** Screen margin round the plates, wide (Landscape) and narrow (Portrait). Shared by the glass screens. */
internal val SCREEN_MARGIN_WIDE = 48.dp
internal val SCREEN_MARGIN_NARROW = 24.dp

/** Corners of the large plates. */
internal val PlateShape = RoundedCornerShape(48.dp)

/** The screen height the 시안 B mockup was drawn at; the big art behind the plates scales from it. */
internal const val MOCKUP_HEIGHT_DP = 1210f

/** In the dark mode the glass is the theme's card colour, a little more solid, with a faint edge. */
private const val DARK_FILL_EXTRA = 0.2f
private const val DARK_EDGE_SHARE = 0.3f

/** Dark glass is the card colour lifted this much towards white, so a plate reads apart from the dark scene. */
private const val DARK_TINT_LIFT = 0.1f

/** CSS-like `saturate(1.9)` on what shows through, so the colours behind stay lively when blurred. */
private const val BACKDROP_SATURATION = 1.9f

/** The scene the glass plates of one screen blur: drawn once, then drawn again blurred under each plate. */
internal class GlassBackdrop(
    val layer: GraphicsLayer,
    val shadowScale: Float,
) {
    var coordinates: LayoutCoordinates? = null
}

private val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Background blur needs RenderEffect (Android 12). Before it, the plates are see-through fill only. */
private val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The light of a glass scene, as ARGB. It comes from one place, the top left: [litArgb] there,
 * settling into [groundArgb] towards the bottom right, with a faint ground shade [shadeArgb] along
 * the bottom for depth. A theme may add one very faint [tintArgb] glow at [tintAt] (shares of the
 * screen).
 */
@Immutable
internal class GlassGlow(
    val litArgb: Int,
    val groundArgb: Int,
    val shadeArgb: Int,
    val tintArgb: Int? = null,
    val tintAt: Offset = Offset.Zero,
    /** Plate shadows, as a share of the usual: deeper lifts the plates off a pale scene. */
    val shadowScale: Float = 1f,
)

/** The theme's own light, or one made from its background and line colour. */
internal fun glassGlow(
    backgroundArgb: Int,
    accentArgb: Int,
    art: ThemeArt?,
    dark: Boolean,
): GlassGlow {
    art?.glow(dark)?.let { return it }
    val background = Color(backgroundArgb)
    val accent = Color(accentArgb)
    return if (dark) {
        GlassGlow(
            litArgb = lerp(lerp(background, Color.White, 0.07f), accent, 0.05f).toArgb(),
            groundArgb = backgroundArgb,
            shadeArgb = lerp(background, Color.Black, 0.35f).toArgb(),
        )
    } else {
        GlassGlow(
            litArgb = lerp(lerp(background, Color.White, 0.55f), accent, 0.04f).toArgb(),
            groundArgb = backgroundArgb,
            shadeArgb = lerp(background, Color.Black, 0.05f).toArgb(),
        )
    }
}

/** Where the light comes from (shares of the screen), and how far its pool reaches (share of the long side). */
private val LIGHT_SOURCE = Offset(0.08f, 0.05f)
private const val LIGHT_POOL_REACH = 0.55f

/** The ground shade starts at this share of the screen height and is full at the bottom. */
private const val GROUND_SHADE_FROM = 0.7f

/** The theme's faint tint glow reaches this share of the screen's short side. */
private const val TINT_REACH = 0.45f

private fun DrawScope.drawGlow(glow: GlassGlow) {
    val lit = Color(glow.litArgb)
    drawRect(Brush.linearGradient(listOf(lit, Color(glow.groundArgb)), start = Offset.Zero, end = Offset(size.width, size.height)))
    val source = Offset(size.width * LIGHT_SOURCE.x, size.height * LIGHT_SOURCE.y)
    val pool = size.maxDimension * LIGHT_POOL_REACH
    drawCircle(Brush.radialGradient(listOf(lit, lit.copy(alpha = 0f)), source, pool), pool, source)
    glow.tintArgb?.let { argb ->
        val tint = Color(argb)
        val at = Offset(size.width * glow.tintAt.x, size.height * glow.tintAt.y)
        val reach = size.minDimension * TINT_REACH
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.6f), tint.copy(alpha = 0f)), at, reach), reach, at)
    }
    val shade = Color(glow.shadeArgb)
    drawRect(
        Brush.verticalGradient(GROUND_SHADE_FROM to shade.copy(alpha = 0f), 1f to shade, startY = 0f, endY = size.height),
    )
}

/**
 * One screen's glass scene: the glow and the theme's backdrop pattern, then [backdrop] (the big
 * art the screen shows behind its plates), then [content] with the plates that blur all of it.
 * [accent] is the theme's line colour for the app background. When [moving], the theme's backdrop
 * motion plays (never on the "기록 중" screen, nor with system animations off).
 */
@Composable
internal fun GlassScene(
    accent: Color,
    art: ThemeArt?,
    modifier: Modifier = Modifier,
    moving: Boolean = false,
    backdrop: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val background = MaterialTheme.colorScheme.background
    val glow = remember(background, accent, art, dark) { glassGlow(background.toArgb(), accent.toArgb(), art, dark) }
    val layer = rememberGraphicsLayer()
    val holder = remember(layer, glow.shadowScale) { GlassBackdrop(layer, glow.shadowScale) }
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { holder.coordinates = it }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        ) {
            // Decoration only, and still.
            Canvas(Modifier.fillMaxSize()) {
                drawGlow(glow)
                if (art != null) with(art) { drawBackdrop(dark) }
            }
            backdrop()
        }
        // Over the backdrop but under the plates: the plates show the still scene blurred.
        if (art != null && art.backdropMoves) {
            val seconds = rememberLoopSeconds(moving)
            if (moving) {
                // Decoration only.
                Canvas(Modifier.fillMaxSize()) { with(art) { drawBackdropMotion(dark, seconds.floatValue) } }
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides holder) {
            content()
        }
    }
}

/**
 * Makes this a glass plate (GLOSSARY: 유리판) of [shape]: the scene behind shows blurred through
 * it (Android 12 and later; before, it is see-through fill only), under a soft white fill with a
 * light band at the top edge, a white edge and a shadow. Light mode glass is white; dark mode glass
 * is the theme's card colour.
 */
@Composable
internal fun Modifier.glass(
    shape: Shape,
    look: GlassLook = GlassLook.Panel,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val tint = if (dark) lerp(scheme.surfaceContainer, Color.White, DARK_TINT_LIFT) else Color.White
    val extra = if (dark) DARK_FILL_EXTRA else 0f
    val fillFrom = tint.copy(alpha = (look.fillFrom + extra).coerceAtMost(1f))
    val fillTo = tint.copy(alpha = (look.fillTo + extra).coerceAtMost(1f))
    val edge = Color.White.copy(alpha = if (dark) look.edgeAlpha * DARK_EDGE_SHARE else look.edgeAlpha)
    val highlight = Color.White.copy(alpha = if (dark) 0.28f else 0.95f)
    val lowlight = Color.White.copy(alpha = if (dark) 0.08f else 0.45f)
    val sheen = Color.White.copy(alpha = if (dark) 0.09f else 0.3f)
    val shadowColor = if (dark) Color.Black else scheme.primary
    val blurLayer = rememberGraphicsLayer()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }
    val blurs = blurSupported && look.blur > 0.dp && backdrop != null
    val shadow =
        if (look.elevation > 0.dp) {
            Modifier.shadow(look.elevation * (backdrop?.shadowScale ?: 1f), shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
        } else {
            Modifier
        }
    return this
        .then(shadow)
        // Any move redraws the plate, so what shows through stays lined up with the scene.
        .then(if (blurs) Modifier.onGloballyPositioned { coordinates = it } else Modifier)
        .drawWithCache {
            val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val fill = Brush.linearGradient(listOf(fillFrom, fillTo), start = Offset.Zero, end = Offset(size.width, size.height))
            val band = Brush.verticalGradient(0f to sheen, 0.12f to sheen.copy(alpha = 0f))
            val top = Path().apply { addPath(outline, Offset(0f, 2.dp.toPx())) }
            val bottom = Path().apply { addPath(outline, Offset(0f, -1.5.dp.toPx())) }
            val blurPx = look.blur.toPx()
            onDrawBehind {
                clipPath(outline) {
                    if (blurs) drawBackdropBlurred(backdrop!!, coordinates, blurLayer, blurPx)
                    drawRect(fill)
                    if (look.sheen) drawRect(band)
                    if (look.elevation > 0.dp) {
                        // Inner light along the top edge, fainter along the bottom.
                        clipPath(top, ClipOp.Difference) { drawRect(highlight) }
                        clipPath(bottom, ClipOp.Difference) { drawRect(lowlight) }
                    }
                }
            }
        }.border(1.5.dp, edge, shape)
}

private val SaturateFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(BACKDROP_SATURATION) })

/** The scene behind this plate, blurred, at the plate's place in it. Does nothing until both are placed. */
private fun DrawScope.drawBackdropBlurred(
    backdrop: GlassBackdrop,
    coordinates: LayoutCoordinates?,
    blurLayer: GraphicsLayer,
    blurPx: Float,
) {
    val scene = backdrop.coordinates ?: return
    val plate = coordinates ?: return
    if (!scene.isAttached || !plate.isAttached) return
    val at = scene.localPositionOf(plate, Offset.Zero)
    // Record a margin round the plate, so the blur near its edges takes in what is just outside.
    val margin = ceil(blurPx * 2).toInt()
    blurLayer.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
    blurLayer.colorFilter = SaturateFilter
    blurLayer.topLeft = IntOffset(-margin, -margin)
    blurLayer.record(IntSize(size.width.toInt() + margin * 2, size.height.toInt() + margin * 2)) {
        translate(margin - at.x, margin - at.y) { drawLayer(backdrop.layer) }
    }
    drawLayer(blurLayer)
}
