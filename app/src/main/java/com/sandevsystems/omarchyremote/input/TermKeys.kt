package com.sandevsystems.omarchyremote.input

/** Bytes for the terminal toolbar (docs/PLANO-V2.md §4.2): named keys, control codes, sticky modifiers. */
object TermKeys {
    val ESC = byteArrayOf(0x1b)

    private val named = mapOf(
        "esc" to ESC,
        "tab" to byteArrayOf(0x09),
        "enter" to byteArrayOf(0x0d),
        "backspace" to byteArrayOf(0x7f),
        "up" to "\u001b[A".toByteArray(),
        "down" to "\u001b[B".toByteArray(),
        "right" to "\u001b[C".toByteArray(),
        "left" to "\u001b[D".toByteArray(),
        // Ctrl+Space: herdr's prefix in Omarchy's config (config/herdr/config.toml).
        "prefix" to byteArrayOf(0x00),
    )

    fun bytes(name: String): ByteArray? = named[name]

    /** Ctrl + one printable character: its control code, or null when there is none (digits, accents…). */
    fun control(text: String): ByteArray? {
        if (text.length != 1) return null
        return when (val c = text[0].uppercaseChar()) {
            in 'A'..'Z' -> byteArrayOf((c.code - 64).toByte())
            '@', ' ' -> byteArrayOf(0x00)
            '[' -> byteArrayOf(0x1b)
            '\\' -> byteArrayOf(0x1c)
            ']' -> byteArrayOf(0x1d)
            '^' -> byteArrayOf(0x1e)
            '_' -> byteArrayOf(0x1f)
            else -> null
        }
    }
}

/** The toolbar's Ctrl and Alt: armed for the next thing typed, then released (like the keys layer). */
class TermModifiers {
    var ctrl = false
    var alt = false

    fun apply(text: String): ByteArray {
        var out = text.toByteArray()
        if (ctrl) {
            out = TermKeys.control(text) ?: out
            ctrl = false
        }
        if (alt) {
            out = TermKeys.ESC + out
            alt = false
        }
        return out
    }
}
