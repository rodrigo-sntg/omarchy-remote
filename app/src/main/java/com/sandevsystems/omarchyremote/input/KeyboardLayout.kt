package com.sandevsystems.omarchyremote.input

import com.sandevsystems.omarchyremote.ui.tr
import com.sandevsystems.omarchyremote.input.ModifierKeys.RIGHT_ALT
import com.sandevsystems.omarchyremote.input.ModifierKeys.SHIFT
import java.text.Normalizer

sealed interface Encoding {
    data class Keys(val keys: List<KeyStroke>) : Encoding
    /** The first character the layout cannot produce; the text must not be sent partially. */
    data class Unsupported(val character: String) : Encoding
}

/**
 * Text to physical keys, for the layout active on the host. Tables follow the xkb symbols
 * `br(abnt2)` and `us` and the USB HID usage table.
 */
enum class KeyboardLayout(private val pt: String, private val en: String, private val keys: Map<Char, KeyStroke>, private val accents: List<Accent>) {
    ABNT2("Português · ABNT2", "Portuguese · ABNT2", abnt2Keys, abnt2Accents),
    US("Inglês · US (sem acentos)", "English · US (no accents)", usKeys, emptyList());

    val label: String get() = tr(pt, en)

    fun encode(text: String): Encoding {
        val result = mutableListOf<KeyStroke>()
        for (codePoint in Normalizer.normalize(text, Normalizer.Form.NFC).codePoints().toArray()) {
            val character = String(Character.toChars(codePoint))
            result += strokesFor(character) ?: return Encoding.Unsupported(character)
        }
        return Encoding.Keys(result)
    }

    private fun strokesFor(character: String): List<KeyStroke>? {
        if (character.length != 1) return null
        keys[character[0]]?.let { return listOf(it) }
        accents.firstOrNull { it.spacing == character[0] }?.let { return listOf(it.deadKey, keys.getValue(' ')) }
        // Accented letter: dead key followed by the base letter.
        val parts = Normalizer.normalize(character, Normalizer.Form.NFD)
        if (parts.length != 2) return null
        val accent = accents.firstOrNull { it.combining == parts[1] && parts[0].lowercaseChar() in it.letters }
        val letter = keys[parts[0]]
        return if (accent == null || letter == null) null else listOf(accent.deadKey, letter)
    }
}

class Accent(val combining: Char, val spacing: Char, val deadKey: KeyStroke, val letters: String)

private fun key(usage: Int) = KeyStroke(usage)
private fun shift(usage: Int) = KeyStroke(usage, SHIFT)

private val commonKeys: Map<Char, KeyStroke> = buildMap {
    for (letter in 'a'..'z') {
        put(letter, key(0x04 + (letter - 'a')))
        put(letter.uppercaseChar(), shift(0x04 + (letter - 'a')))
    }
    "1234567890".forEachIndexed { index, digit -> put(digit, key(0x1e + index)) }
    put(' ', key(0x2c)); put('\n', key(0x28)); put('\t', key(0x2b))
    put('!', shift(0x1e)); put('@', shift(0x1f)); put('#', shift(0x20)); put('$', shift(0x21))
    put('%', shift(0x22)); put('&', shift(0x24)); put('*', shift(0x25)); put('(', shift(0x26)); put(')', shift(0x27))
    put('-', key(0x2d)); put('_', shift(0x2d)); put('=', key(0x2e)); put('+', shift(0x2e))
    put(',', key(0x36)); put('<', shift(0x36)); put('.', key(0x37)); put('>', shift(0x37))
}

private val abnt2Keys = commonKeys + mapOf(
    '\'' to key(0x35), '"' to shift(0x35),
    '[' to key(0x30), '{' to shift(0x30),
    ']' to key(0x31), '}' to shift(0x31),
    'ç' to key(0x33), 'Ç' to shift(0x33),
    ';' to key(0x38), ':' to shift(0x38),
    '\\' to key(0x64), '|' to shift(0x64),
    // The dedicated /? key (HID 0x87) is outside the descriptor range; AltGr+Q/W is equivalent in br(abnt2).
    '/' to KeyStroke(0x14, RIGHT_ALT), '?' to KeyStroke(0x1a, RIGHT_ALT),
)

private const val VOWELS = "aeiou"

private val abnt2Accents = listOf(
    Accent('́', '´', key(0x2f), VOWELS),
    Accent('̀', '`', shift(0x2f), VOWELS),
    Accent('̃', '~', key(0x34), "aon"),
    Accent('̂', '^', shift(0x34), VOWELS),
    Accent('̈', '¨', shift(0x23), VOWELS),
)

private val usKeys = commonKeys + mapOf(
    '`' to key(0x35), '~' to shift(0x35), '^' to shift(0x23),
    '[' to key(0x2f), '{' to shift(0x2f), ']' to key(0x30), '}' to shift(0x30),
    '\\' to key(0x31), '|' to shift(0x31),
    ';' to key(0x33), ':' to shift(0x33), '\'' to key(0x34), '"' to shift(0x34),
    '/' to key(0x38), '?' to shift(0x38),
)
