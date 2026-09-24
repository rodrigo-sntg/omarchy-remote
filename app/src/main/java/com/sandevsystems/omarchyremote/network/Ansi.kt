package com.sandevsystems.omarchyremote.network

/**
 * A terminal screen with its colors (herdr's --format ansi) as runs of text and style: what a TUI
 * shows only in color — the selected item of a picker, a panel's current tab — survives. Colors are
 * 0xRRGGBB; null is the terminal's own.
 */
data class AnsiStyle(
    val fg: Int? = null, val bg: Int? = null,
    val bold: Boolean = false, val dim: Boolean = false, val italic: Boolean = false, val underline: Boolean = false, val inverse: Boolean = false,
)

data class AnsiRun(val text: String, val style: AnsiStyle)

private val basic = intArrayOf(
    0x000000, 0xCC0000, 0x4E9A06, 0xC4A000, 0x3465A4, 0x75507B, 0x06989A, 0xD3D7CF,
    0x555753, 0xEF2929, 0x8AE234, 0xFCE94F, 0x729FCF, 0xAD7FA8, 0x34E2E2, 0xEEEEEC,
)

/** xterm's 256 colors: the 16 basic, a 6×6×6 cube, 24 grays. */
fun xterm256(n: Int): Int = when {
    n < 16 -> basic[n.coerceAtLeast(0)]
    n < 232 -> {
        val i = n - 16
        val level = { v: Int -> if (v == 0) 0 else 55 + v * 40 }
        (level(i / 36) shl 16) or (level(i / 6 % 6) shl 8) or level(i % 6)
    }
    else -> (8 + (n.coerceAtMost(255) - 232) * 10).let { (it shl 16) or (it shl 8) or it }
}

private fun sgr(style: AnsiStyle, params: List<Int>): AnsiStyle {
    var s = style
    var i = 0
    val p = params.ifEmpty { listOf(0) }
    fun color(): Int? {
        return when (p.getOrNull(i + 1)) {
            5 -> p.getOrNull(i + 2)?.let { xterm256(it) }.also { i += 2 }
            2 -> if (i + 4 < p.size) ((p[i + 2] and 255) shl 16 or ((p[i + 3] and 255) shl 8) or (p[i + 4] and 255)).also { i += 4 } else null
            else -> null
        }
    }
    while (i < p.size) {
        when (val c = p[i]) {
            0 -> s = AnsiStyle()
            1 -> s = s.copy(bold = true)
            2 -> s = s.copy(dim = true)
            3 -> s = s.copy(italic = true)
            4 -> s = s.copy(underline = true)
            7 -> s = s.copy(inverse = true)
            22 -> s = s.copy(bold = false, dim = false)
            23 -> s = s.copy(italic = false)
            24 -> s = s.copy(underline = false)
            27 -> s = s.copy(inverse = false)
            in 30..37 -> s = s.copy(fg = basic[c - 30])
            38 -> s = s.copy(fg = color() ?: s.fg)
            39 -> s = s.copy(fg = null)
            in 40..47 -> s = s.copy(bg = basic[c - 40])
            48 -> s = s.copy(bg = color() ?: s.bg)
            49 -> s = s.copy(bg = null)
            in 90..97 -> s = s.copy(fg = basic[c - 90 + 8])
            in 100..107 -> s = s.copy(bg = basic[c - 100 + 8])
        }
        i++
    }
    return s
}

fun ansiRuns(raw: String): List<AnsiRun> {
    val runs = mutableListOf<AnsiRun>()
    val text = StringBuilder()
    var style = AnsiStyle()
    fun flush() {
        if (text.isEmpty()) return
        val last = runs.lastOrNull()
        if (last != null && last.style == style) runs[runs.size - 1] = last.copy(text = last.text + text) else runs += AnsiRun(text.toString(), style)
        text.clear()
    }
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        when {
            c == '\u001b' && raw.getOrNull(i + 1) == '[' -> {
                var j = i + 2
                while (j < raw.length && raw[j] !in '@'..'~') j++
                if (j < raw.length && raw[j] == 'm') {
                    val body = raw.substring(i + 2, j)
                    if (!body.startsWith("?")) {
                        val next = sgr(style, body.split(';').mapNotNull { it.toIntOrNull() ?: if (it.isEmpty()) 0 else null })
                        if (next != style) {
                            flush()
                            style = next
                        }
                    }
                }
                i = j + 1
            }
            c == '\u001b' && raw.getOrNull(i + 1) == ']' -> {
                // An OSC (window title…): up to BEL or ESC \.
                var j = i + 2
                while (j < raw.length && raw[j] != '\u0007' && !(raw[j] == '\u001b' && raw.getOrNull(j + 1) == '\\')) j++
                i = if (j < raw.length && raw[j] == '\u001b') j + 2 else j + 1
            }
            c == '\u001b' -> i += 2
            c == '\r' -> i++
            else -> {
                text.append(c)
                i++
            }
        }
    }
    flush()
    return runs
}
