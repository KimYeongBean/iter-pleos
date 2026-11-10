package io.github.kimyeongbean.iter.app

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

    @Test
    fun `the card stands apart from the background in both themes`() {
        listOf(IterLightColors, IterDarkColors).forEach { colors ->
            assertTrue(colors.cardArgb != colors.backgroundArgb)
        }
    }
}
