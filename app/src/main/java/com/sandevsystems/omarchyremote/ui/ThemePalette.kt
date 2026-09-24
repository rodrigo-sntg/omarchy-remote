package com.sandevsystems.omarchyremote.ui

import kotlin.math.pow

/**
 * The app's palette as ARGB ints, from the PC's Omarchy theme (docs/PLANO-V2.md §5.3). Pure, so the
 * mapping and the contrast rules are tested; KeypadColors applies it.
 */
data class Palette(
    val bg: Int, val surface1: Int, val surface2: Int, val surface3: Int, val line: Int, val line2: Int,
    val text: Int, val textDim: Int, val textMute: Int, val accent: Int, val onAccent: Int, val danger: Int,
    /** A light theme: the system bars switch to dark icons. */
    val light: Boolean = false,
    /** State that must mean something on any theme: connected / working (green), needs you (amber). */
    val ok: Int = 0xFF4CC38A.toInt(),
    val attention: Int = 0xFFE3B341.toInt(),
)

object ThemePalette {
    /** Design v2's own look: graphite with a lime accent (design/DESIGN.md). */
    val DEFAULT = Palette(
        bg = 0xFF0B0C0D.toInt(), surface1 = 0xFF121517.toInt(), surface2 = 0xFF16191B.toInt(), surface3 = 0xFF1D2124.toInt(),
        line = 0xFF2B3034.toInt(), line2 = 0xFF3A4045.toInt(), text = 0xFFECEEF0.toInt(), textDim = 0xFF99A1A8.toInt(),
        textMute = 0xFF808890.toInt(), accent = 0xFFC5F24A.toInt(), onAccent = 0xFF0B0C0D.toInt(), danger = 0xFFE8674B.toInt(),
    )
    private const val MIN_CONTRAST = 3.0
    private const val GREY_SATURATION = 0.22
    private val hex = Regex("#[0-9a-fA-F]{6}")

    /**
     * The palette for a theme's resolved colors, or null to keep the app's own. It follows Omarchy's
     * shell (default/themed/shell.toml.tpl): every surface is the theme's background, layers are the
     * foreground over it (controls 4 %, hover 8 %, selected 18 %, borders 40 %), the accent marks what
     * is selected or active, red what is urgent. Light themes work the same way (the layers are the
     * dark foreground over the light background).
     */
    fun from(mode: String, colors: Map<String, String>): Palette? {
        fun color(key: String): Int? = colors[key]?.takeIf { hex.matches(it) }?.let { (0xFF000000 or it.drop(1).toLong(16)).toInt() }
        val accent = color("accent") ?: return null
        val bg = color("background") ?: return null
        val fg = color("foreground") ?: return null
        val light = mode == "light" || luminance(bg) > 0.5
        // A grey accent (monochrome themes like Solitude) cannot mark what is on: "on" becomes a
        // strong fill of the text color instead, like a dark monochrome iOS screen.
        val readableAccent = if (saturation(accent) < GREY_SATURATION) readable(fg, bg, 9.0) else readable(accent, bg)
        // "red" is urgent in Omarchy, but some themes make it a muted tone: then the brighter red.
        val urgent = listOfNotNull(color("red"), color("bright_red")).firstOrNull { saturation(it) > 0.35 && contrast(it, bg) >= MIN_CONTRAST }
            ?: readable(color("bright_red") ?: DEFAULT.danger, bg)
        return Palette(
            // Omarchy layers its shell as the foreground over the background; cards and sheets get a
            // little of it too, so they stand off the background instead of reading as outlines.
            bg = bg, surface1 = over(fg, bg, 0.05), surface2 = over(fg, bg, 0.035), surface3 = over(fg, bg, 0.10),
            line = over(fg, bg, 0.20), line2 = over(fg, bg, 0.40),
            text = fg, textDim = readable(over(fg, bg, 0.78), bg, 4.5), textMute = readable(over(fg, bg, 0.62), bg, 4.5),
            accent = readableAccent, onAccent = onColor(readableAccent, if (light) fg else bg), danger = urgent,
            light = light,
            ok = readable(if (light) 0xFF1F8A5B.toInt() else DEFAULT.ok, bg),
            attention = readable(if (light) 0xFFA86A12.toInt() else DEFAULT.attention, bg),
        )
    }

    /** [fg] at [alpha] over [bg]: how Omarchy's shell layers its surfaces. */
    fun over(fg: Int, bg: Int, alpha: Double): Int {
        fun ch(shift: Int) = ((fg shr shift and 0xFF) * alpha + (bg shr shift and 0xFF) * (1 - alpha) + 0.5).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun saturation(c: Int): Double {
        val r = (c shr 16 and 0xFF) / 255.0; val g = (c shr 8 and 0xFF) / 255.0; val b = (c and 0xFF) / 255.0
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        return if (max == 0.0) 0.0 else (max - min) / max
    }

    /** WCAG contrast ratio between two opaque colors. */
    fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(c: Int): Double {
        fun channel(v: Int): Double = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel(c shr 16 and 0xFF) + 0.7152 * channel(c shr 8 and 0xFF) + 0.0722 * channel(c and 0xFF)
    }

    /** Moves [c] away from [bg] (lighter on dark, darker on light) until it reads on it. */
    private fun readable(c: Int, bg: Int, min: Double = MIN_CONTRAST): Int {
        val target = if (luminance(bg) > 0.5) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        var color = c
        var step = 0
        while (contrast(color, bg) < min && step < 20) {
            color = mix(color, target, 0.12)
            step++
        }
        return color
    }

    /** Dark text on light colors, light text on dark ones. */
    private fun onColor(c: Int, dark: Int = DEFAULT.bg): Int = if (luminance(c) > 0.18) dark else 0xFFFFFFFF.toInt()

    private fun mix(a: Int, b: Int, t: Double): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
