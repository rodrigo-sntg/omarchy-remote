package com.sandevsystems.omarchyremote.input

/**
 * A keyboard plugged into (or paired with) the phone types on the PC (docs/PLANO-V2.md §6): Android
 * key codes become HID usages by key position, with the modifiers from the meta state. Codes and
 * meta bits are android.view.KeyEvent's, as numbers, so the mapping is testable on the JVM.
 */
object HardwareKeys {
    private const val META_SHIFT_ON = 0x1
    private const val META_ALT_ON = 0x2
    private const val META_CTRL_ON = 0x1000
    private const val META_META_ON = 0x10000

    private val usages: Map<Int, Int> = buildMap {
        for (i in 0..25) put(29 + i, 0x04 + i)          // KEYCODE_A..Z
        for (i in 1..9) put(7 + i, 0x1E + i - 1)        // KEYCODE_1..9
        put(7, 0x27)                                    // KEYCODE_0
        put(66, 0x28); put(160, 0x58)                   // ENTER, NUMPAD_ENTER
        put(111, 0x29); put(67, 0x2A); put(61, 0x2B); put(62, 0x2C) // ESCAPE, DEL (backspace), TAB, SPACE
        put(69, 0x2D); put(70, 0x2E); put(71, 0x2F); put(72, 0x30); put(73, 0x31) // - = [ ] \
        put(74, 0x33); put(75, 0x34); put(68, 0x35); put(55, 0x36); put(56, 0x37); put(76, 0x38) // ; ' ` , . /
        for (i in 0..11) put(131 + i, 0x3A + i)         // F1..F12
        put(120, 0x46); put(116, 0x47); put(121, 0x48)  // SYSRQ, SCROLL_LOCK, BREAK
        put(124, 0x49); put(122, 0x4A); put(92, 0x4B); put(112, 0x4C); put(123, 0x4D); put(93, 0x4E) // Insert Home PgUp Del End PgDn
        put(22, 0x4F); put(21, 0x50); put(20, 0x51); put(19, 0x52) // arrows
    }

    /** The stroke for a key press, or null for a modifier alone or a key the PC has no use for. */
    fun stroke(keyCode: Int, metaState: Int): KeyStroke? {
        val usage = usages[keyCode] ?: return null
        var modifiers = 0
        if (metaState and META_CTRL_ON != 0) modifiers = modifiers or ModifierKeys.CTRL
        if (metaState and META_SHIFT_ON != 0) modifiers = modifiers or ModifierKeys.SHIFT
        if (metaState and META_ALT_ON != 0) modifiers = modifiers or ModifierKeys.ALT
        if (metaState and META_META_ON != 0) modifiers = modifiers or ModifierKeys.SUPER
        return KeyStroke(usage, modifiers)
    }
}
