package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.ui.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mapping follows Omarchy's own shell (default/themed/shell.toml.tpl): one background, layers
 * made of the foreground over it (controls 4 %, hover 8 %, selected 18 %, borders 40 %), the accent
 * for what is selected/active, red for what is urgent.
 */
class ThemePaletteTest {
    // Omarchy's "solitude", as omarchy-theme-color resolves it on the user's PC.
    private val solitude = mapOf(
        "accent" to "#798186", "background" to "#101315", "dark_background" to "#0c0e10", "darker_background" to "#080a0b",
        "foreground" to "#cacccc", "bright_foreground" to "#a5aeb4", "muted" to "#4b4e55", "selection" to "#343d41",
        "red" to "#565d60", "bright_red" to "#de6145",
    )

    private fun over(fg: Int, bg: Int, alpha: Double): Int {
        fun ch(shift: Int) = ((fg shr shift and 0xFF) * alpha + (bg shr shift and 0xFF) * (1 - alpha) + 0.5).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    @Test
    fun surfacesAreTheForegroundOverTheBackground() {
        val p = ThemePalette.from("dark", solitude)!!
        val bg = 0xFF101315.toInt()
        val fg = 0xFFCACCCC.toInt()
        assertEquals(bg, p.bg)
        // Cards and sheets stand a little off the background, or everything looks like a wireframe.
        assertEquals(over(fg, bg, 0.035), p.surface2)
        assertEquals(over(fg, bg, 0.05), p.surface1)
        assertEquals(over(fg, bg, 0.10), p.surface3)
        assertEquals(over(fg, bg, 0.40), p.line2)
        assertEquals(fg, p.text)
        assertTrue(ThemePalette.contrast(p.textMute, p.bg) >= 4.5)
        assertTrue(ThemePalette.contrast(p.textDim, p.bg) >= 4.5)
    }

    @Test
    fun aGreyAccentBecomesALightFillSoWhatIsOnStillShows() {
        // Solitude's accent (#798186) is a grey: "on" and "off" would look alike. On a monochrome
        // theme "on" is a light fill with dark text, like a dark monochrome iOS screen.
        val p = ThemePalette.from("dark", solitude)!!
        assertTrue(ThemePalette.contrast(p.accent, p.bg) >= 9.0)
        assertTrue(ThemePalette.contrast(p.onAccent, p.accent) >= 7.0)
    }

    @Test
    fun aColorfulAccentIsKept() {
        val tokyo = solitude + mapOf("accent" to "#7aa2f7", "background" to "#1a1b26", "foreground" to "#a9b1d6")
        assertEquals(0xFF7AA2F7.toInt(), ThemePalette.from("dark", tokyo)!!.accent)
    }

    @Test
    fun stateHasItsOwnColorsWhateverTheAccent() {
        val p = ThemePalette.from("dark", solitude)!!
        // Connected is green and "needs you" amber even on a grey theme: they must mean something.
        assertTrue(ThemePalette.contrast(p.ok, p.bg) >= 3.0 && p.ok != p.accent)
        assertTrue(ThemePalette.contrast(p.attention, p.bg) >= 3.0)
        assertTrue(ThemePalette.DEFAULT.ok != ThemePalette.DEFAULT.danger)
    }

    @Test
    fun urgentIsTheThemesRedMadeReadable() {
        val p = ThemePalette.from("dark", solitude)!!
        // solitude's "red" is a grey (#565d60): the brighter red is used when red does not read as a warning.
        assertEquals(0xFFDE6145.toInt(), p.danger)
    }

    @Test
    fun anAccentTooDarkForTheBackgroundIsLightened() {
        val p = ThemePalette.from("dark", solitude + ("accent" to "#202428"))!!
        assertTrue(ThemePalette.contrast(p.accent, p.bg) >= 3.0)
    }

    @Test
    fun missingEssentialsKeepTheAppsOwnLook() {
        assertNull(ThemePalette.from("dark", mapOf("accent" to "#798186")))
        assertNull(ThemePalette.from("dark", solitude + ("accent" to "red")))
    }

    @Test
    fun lightThemesAreFollowedToo() {
        // Rosé Pine Dawn–like: light background, dark text.
        val light = ThemePalette.from("light", mapOf("background" to "#faf4ed", "foreground" to "#575279", "accent" to "#56949f", "red" to "#b4637a"))!!
        assertTrue(light.light)
        assertEquals(0xFFFAF4ED.toInt(), light.bg)
        assertTrue(ThemePalette.contrast(light.text, light.bg) >= 4.5)
        assertTrue(ThemePalette.contrast(light.accent, light.bg) >= 3.0)
        assertTrue(ThemePalette.contrast(light.textMute, light.bg) >= 3.0)
        assertTrue(!ThemePalette.from("dark", solitude)!!.light)
    }
}
