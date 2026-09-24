package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.input.Encoding
import com.sandevsystems.omarchyremote.input.KeyStroke
import com.sandevsystems.omarchyremote.input.KeyboardLayout
import com.sandevsystems.omarchyremote.input.KeyboardLayout.ABNT2
import com.sandevsystems.omarchyremote.input.KeyboardLayout.US
import com.sandevsystems.omarchyremote.input.ModifierKeys.RIGHT_ALT
import com.sandevsystems.omarchyremote.input.ModifierKeys.SHIFT
import org.junit.Assert.assertEquals
import org.junit.Test

/** Expected values follow /usr/share/X11/xkb/symbols/br (abnt2) and the USB HID usage table. */
class KeyboardLayoutTest {
    private fun k(usage: Int, modifiers: Int = 0) = KeyStroke(usage, modifiers)
    private fun keys(layout: KeyboardLayout, text: String) = (layout.encode(text) as Encoding.Keys).keys

    private val a = k(0x04)
    private val o = k(0x12)
    private val space = k(0x2c)
    private val tilde = k(0x34)
    private val acute = k(0x2f)

    @Test
    fun lettersAndDigits() {
        assertEquals(listOf(a, k(0x05), k(0x06), k(0x1e), k(0x1f), k(0x20)), keys(ABNT2, "abc123"))
        assertEquals(listOf(k(0x04, SHIFT), k(0x1d, SHIFT), k(0x27)), keys(ABNT2, "AZ0"))
    }

    @Test
    fun portugueseWordsUseDeadKeys() {
        assertEquals(listOf(a, k(0x33), tilde, a, o), keys(ABNT2, "ação"))
        assertEquals(listOf(acute, k(0x12, SHIFT), k(0x15), k(0x0a), tilde, a, o), keys(ABNT2, "Órgão"))
        assertEquals(
            listOf(k(0x16, SHIFT), tilde, a, o, space, k(0x13, SHIFT), a, k(0x18), k(0x0f), o),
            keys(ABNT2, "São Paulo"),
        )
        assertEquals(listOf(k(0x34, SHIFT), k(0x08), k(0x2f, SHIFT), a, k(0x23, SHIFT), k(0x18)), keys(ABNT2, "êàü"))
    }

    @Test
    fun cedilla() {
        assertEquals(listOf(k(0x33), k(0x33, SHIFT)), keys(ABNT2, "çÇ"))
    }

    @Test
    fun decomposedInputIsNormalized() {
        assertEquals(keys(ABNT2, "ã"), keys(ABNT2, "ã"))
    }

    @Test
    fun literalAccentsAreFollowedBySpace() {
        assertEquals(listOf(tilde, space), keys(ABNT2, "~"))
        assertEquals(listOf(k(0x34, SHIFT), space), keys(ABNT2, "^"))
        assertEquals(listOf(acute, space), keys(ABNT2, "´"))
        assertEquals(listOf(k(0x2f, SHIFT), space), keys(ABNT2, "`"))
        assertEquals(listOf(k(0x23, SHIFT), space), keys(ABNT2, "¨"))
    }

    @Test
    fun abnt2ShellPunctuation() {
        val expected = mapOf(
            "'" to k(0x35), "\"" to k(0x35, SHIFT),
            "/" to k(0x14, RIGHT_ALT), "?" to k(0x1a, RIGHT_ALT),
            "\\" to k(0x64), "|" to k(0x64, SHIFT),
            ";" to k(0x38), ":" to k(0x38, SHIFT),
            "[" to k(0x30), "{" to k(0x30, SHIFT), "]" to k(0x31), "}" to k(0x31, SHIFT),
            "," to k(0x36), "<" to k(0x36, SHIFT), "." to k(0x37), ">" to k(0x37, SHIFT),
            "-" to k(0x2d), "_" to k(0x2d, SHIFT), "=" to k(0x2e), "+" to k(0x2e, SHIFT),
            "!" to k(0x1e, SHIFT), "@" to k(0x1f, SHIFT), "#" to k(0x20, SHIFT), "$" to k(0x21, SHIFT),
            "%" to k(0x22, SHIFT), "&" to k(0x24, SHIFT), "*" to k(0x25, SHIFT),
            "(" to k(0x26, SHIFT), ")" to k(0x27, SHIFT),
            "\n" to k(0x28), "\t" to k(0x2b),
        )
        for ((text, stroke) in expected) assertEquals(text, listOf(stroke), keys(ABNT2, text))
    }

    @Test
    fun usPunctuationDiffersFromAbnt2() {
        val expected = mapOf(
            "/" to k(0x38), "?" to k(0x38, SHIFT), "\\" to k(0x31), "|" to k(0x31, SHIFT),
            ";" to k(0x33), ":" to k(0x33, SHIFT), "'" to k(0x34), "\"" to k(0x34, SHIFT),
            "[" to k(0x2f), "]" to k(0x30), "`" to k(0x35), "~" to k(0x35, SHIFT), "^" to k(0x23, SHIFT),
        )
        for ((text, stroke) in expected) assertEquals(text, listOf(stroke), keys(US, text))
    }

    @Test
    fun unsupportedCharacterIsReported() {
        assertEquals(Encoding.Unsupported("ç"), US.encode("ação"))
        assertEquals(Encoding.Unsupported("😀"), ABNT2.encode("ok 😀"))
        assertEquals(Encoding.Unsupported("€"), ABNT2.encode("5€"))
        assertEquals(Encoding.Unsupported("ẽ"), ABNT2.encode("ẽ"))
    }
}
