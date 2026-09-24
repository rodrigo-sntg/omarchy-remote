package com.sandevsystems.omarchyremote.input

/** Input only. A future network connection can implement this without changing gestures. */
interface RemoteInput {
    fun movePointer(dx: Int, dy: Int)
    fun scroll(steps: Int)
    fun setMouseButtons(buttons: Int)
    suspend fun tapKey(key: KeyStroke): Boolean
    /** Types keys in order without interleaving other text; returns how many were delivered. */
    suspend fun type(keys: List<KeyStroke>): Int
    fun releaseAll()
}

/** Physical key identifiers use the standard USB HID keyboard usage table. */
data class KeyStroke(val usage: Int, val modifiers: Int = 0)

object ModifierKeys {
    const val CTRL = 0x01
    const val SHIFT = 0x02
    const val ALT = 0x04
    const val SUPER = 0x08
    const val RIGHT_ALT = 0x40 // AltGr on ABNT2
}

object MouseButtons {
    const val LEFT = 0x01
    const val RIGHT = 0x02
    const val MIDDLE = 0x04
}
