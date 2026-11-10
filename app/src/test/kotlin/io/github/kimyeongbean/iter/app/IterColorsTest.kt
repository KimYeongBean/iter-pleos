package io.github.kimyeongbean.iter.app

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

/** App theme text must read on what it sits on: 4.5:1, the Pleos design guide minimum (#69). */
class IterColorsTest {
    @Test
    fun `every text colour has at least 4_5 to 1 contrast on its ground`() {
        mapOf("light" to IterLightColors, "dark" to IterDarkColors).forEach { (theme, colors) ->
            with(colors) {
                mapOf(
                    "text on background" to (textArgb to backgroundArgb),
                    "text on card" to (textArgb to cardArgb),
                    "text on raised card" to (textArgb to cardHighArgb),
                    "text on control" to (textArgb to controlArgb),
                    "secondary text on background" to (textSecondaryArgb to backgroundArgb),
                    "secondary text on card" to (textSecondaryArgb to cardArgb),
                    "secondary text on raised card" to (textSecondaryArgb to cardHighArgb),
                    "primary button text" to (onPrimaryArgb to primaryArgb),
                ).forEach { (label, colours) ->
                    val (text, ground) = colours
                    val ratio = contrast(text, ground)
                    assertTrue("$theme, $label: $ratio", ratio >= 4.5)
                }
            }
        }
    }

    /** Also covers the Material defaults the theme leaves as they are, such as error (#79). */
    @Test
    fun `every colour scheme content colour has at least 4_5 to 1 contrast on its ground`() {
        mapOf(
            "light" to IterLightColors.toColorScheme(dark = false),
            "dark" to IterDarkColors.toColorScheme(dark = true),
        ).forEach { (theme, scheme) ->
            with(scheme) {
                val surfaces =
                    mapOf(
                        "surface" to surface,
                        "surfaceDim" to surfaceDim,
                        "surfaceBright" to surfaceBright,
                        "surfaceVariant" to surfaceVariant,
                        "surfaceContainerLowest" to surfaceContainerLowest,
                        "surfaceContainerLow" to surfaceContainerLow,
                        "surfaceContainer" to surfaceContainer,
                        "surfaceContainerHigh" to surfaceContainerHigh,
                        "surfaceContainerHighest" to surfaceContainerHighest,
                    )
                val pairs =
                    mapOf(
                        "onBackground on background" to (onBackground to background),
                        "onPrimary on primary" to (onPrimary to primary),
                        "onPrimaryContainer on primaryContainer" to (onPrimaryContainer to primaryContainer),
                        "onSecondary on secondary" to (onSecondary to secondary),
                        "onSecondaryContainer on secondaryContainer" to (onSecondaryContainer to secondaryContainer),
                        "onTertiary on tertiary" to (onTertiary to tertiary),
                        "onTertiaryContainer on tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
                        "onError on error" to (onError to error),
                        "onErrorContainer on errorContainer" to (onErrorContainer to errorContainer),
                        "inverseOnSurface on inverseSurface" to (inverseOnSurface to inverseSurface),
                        "inversePrimary on inverseSurface" to (inversePrimary to inverseSurface),
                    ) +
                        surfaces.flatMap { (name, ground) ->
                            listOf(
                                "onSurface on $name" to (onSurface to ground),
                                "onSurfaceVariant on $name" to (onSurfaceVariant to ground),
                            )
                        }
                pairs.forEach { (label, colours) ->
                    val (content, ground) = colours
                    val ratio = contrast(content.toArgb(), ground.toArgb())
                    assertTrue("$theme, $label: $ratio", ratio >= 4.5)
                }
            }
        }
    }

    @Test
    fun `the card stands apart from the background in both themes`() {
        listOf(IterLightColors, IterDarkColors).forEach { colors ->
            assertTrue(colors.cardArgb != colors.backgroundArgb)
        }
    }
}
