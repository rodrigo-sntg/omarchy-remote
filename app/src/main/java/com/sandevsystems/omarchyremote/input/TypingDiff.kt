package com.sandevsystems.omarchyremote.input

/**
 * Typing straight into the PC from the Android keyboard: the field's text changes in any way
 * (a letter, backspace, autocorrect replacing a word, a suggestion) and the PC gets the same change
 * as backspaces followed by the new text. Counted in characters (code points), not UTF-16 units.
 */
object TypingDiff {
    data class Change(val backspaces: Int, val insert: String)

    fun between(before: String, after: String): Change {
        val a = before.codePoints().toArray()
        val b = after.codePoints().toArray()
        var same = 0
        while (same < a.size && same < b.size && a[same] == b[same]) same++
        return Change(a.size - same, String(b, same, b.size - same))
    }
}
