package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.bluetooth.HidReports
import com.sandevsystems.omarchyremote.input.KeyStroke
import com.sandevsystems.omarchyremote.input.ModifierKeys.CTRL
import com.sandevsystems.omarchyremote.input.ModifierKeys.SUPER
import com.sandevsystems.omarchyremote.input.Shortcuts
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shortcuts of design v2 (design/DESIGN.md §3 ShortcutRow, KeysLayer; §5 Editar atalhos). */
class ShortcutsTest {
    @Test
    fun defaultRowIsCopyPasteUndoCloseEscape() {
        val row = Shortcuts.defaultSlots.map(Shortcuts::byId)
        assertEquals(listOf("C", "V", "Z", "W", "Esc"), row.map { it.glyph })
        assertEquals(listOf("copiar", "colar", "desfazer", "fechar aba", "cancelar"), row.map { it.caption })
        assertEquals(
            listOf(KeyStroke(0x06, CTRL), KeyStroke(0x19, CTRL), KeyStroke(0x1d, CTRL), KeyStroke(0x1a, CTRL), KeyStroke(0x29)),
            row.map { it.key },
        )
    }

    @Test
    fun modifierStripLetters() {
        assertEquals(listOf("C", "V", "X", "A", "Z", "T", "1"), Shortcuts.letters.map { it.first })
        assertEquals(listOf(0x06, 0x19, 0x1b, 0x04, 0x1d, 0x17, 0x1e), Shortcuts.letters.map { it.second })
    }

    @Test
    fun editRowIsCopyPasteCutUndo() {
        assertEquals(listOf("Copiar", "Colar", "Recortar", "Desfazer"), Shortcuts.edit.map { it.caption })
        assertEquals(listOf(0x06, 0x19, 0x1b, 0x1d), Shortcuts.edit.map { it.key.usage })
    }

    @Test
    fun catalogIsGroupedWithUniqueIdsAndIncludesTheDefaults() {
        val all = Shortcuts.groups.flatMap { it.second }
        assertEquals(listOf("Edição", "Navegador", "Janela · Hyprland", "Teclas soltas"), Shortcuts.groups.map { it.first })
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(Shortcuts.defaultSlots.all { id -> all.any { it.id == id } })
    }

    @Test
    fun savedSlotsSurviveUnknownIds() {
        assertEquals(Shortcuts.defaultSlots, Shortcuts.parseSlots(null))
        assertEquals(Shortcuts.defaultSlots, Shortcuts.parseSlots("lixo"))
        val custom = listOf("super", "copy", "paste", "undo", "esc")
        assertEquals(custom, Shortcuts.parseSlots(custom.joinToString(",")))
        // an unknown id falls back to that slot's default ("paste" in slot 2)
        assertEquals(listOf("super", "paste", "undo", "close_tab", "esc"), Shortcuts.parseSlots("super,sumiu,undo,close_tab,esc"))
    }

    @Test
    fun superAloneIsAModifierOnlyBluetoothReport() {
        assertArrayEquals(byteArrayOf(0x08, 0, 0, 0, 0, 0, 0, 0), HidReports.keyboard(KeyStroke(0, SUPER)))
    }

    @Test
    fun copyPasteAndCutUseOmarchysUniversalClipboardOverTheNetwork() {
        assertEquals("copy", Shortcuts.clipboardAction("copy", network = true))
        assertEquals("paste", Shortcuts.clipboardAction("paste", network = true))
        assertEquals("cut", Shortcuts.clipboardAction("cut", network = true))
        assertEquals(null, Shortcuts.clipboardAction("undo", network = true))
        // Over Bluetooth the PC may not be Omarchy: plain Ctrl+C / Ctrl+V.
        assertEquals(null, Shortcuts.clipboardAction("copy", network = false))
    }
}
