package com.sandevsystems.omarchyremote.input

import com.sandevsystems.omarchyremote.input.ModifierKeys.ALT
import com.sandevsystems.omarchyremote.input.ModifierKeys.CTRL
import com.sandevsystems.omarchyremote.input.ModifierKeys.SHIFT
import com.sandevsystems.omarchyremote.input.ModifierKeys.SUPER
import com.sandevsystems.omarchyremote.ui.tr

/**
 * A one-tap shortcut: [glyph] big on the key, [caption] what it does (in the language in effect;
 * [captionPt] and [captionEn] are its two versions). Sent as ordinary keys.
 */
data class Shortcut(val id: String, val glyph: String, val captionPt: String, val captionEn: String, val key: KeyStroke) {
    val caption: String get() = tr(captionPt, captionEn)
}

/** Shortcuts of design v2: the editable 5-key row, the modifier strip and the edit row. */
object Shortcuts {
    private fun s(id: String, glyph: String, pt: String, en: String, usage: Int, mods: Int = 0) = Shortcut(id, glyph, pt, en, KeyStroke(usage, mods))

    /** "Editar atalhos": fixed list, grouped (free combinations come later); titles in the language in effect. */
    val groups: List<Pair<String, List<Shortcut>>> get() = catalog.map { (pt, en, items) -> tr(pt, en) to items }

    private val catalog: List<Triple<String, String, List<Shortcut>>> = listOf(
        Triple("Edição", "Editing", listOf(
            s("copy", "C", "copiar", "copy", 0x06, CTRL), s("paste", "V", "colar", "paste", 0x19, CTRL),
            s("cut", "X", "recortar", "cut", 0x1b, CTRL), s("undo", "Z", "desfazer", "undo", 0x1d, CTRL),
            s("redo", "⇧Z", "refazer", "redo", 0x1d, CTRL or SHIFT), s("select_all", "A", "tudo", "all", 0x04, CTRL),
            s("save", "S", "salvar", "save", 0x16, CTRL), s("find", "F", "buscar", "find", 0x09, CTRL),
        )),
        Triple("Navegador", "Browser", listOf(
            s("close_tab", "W", "fechar aba", "close tab", 0x1a, CTRL), s("new_tab", "T", "nova aba", "new tab", 0x17, CTRL),
            s("reopen_tab", "⇧T", "reabrir aba", "reopen tab", 0x17, CTRL or SHIFT), s("next_tab", "⇥", "próx. aba", "next tab", 0x2b, CTRL),
            s("reload", "R", "recarregar", "reload", 0x15, CTRL), s("address", "L", "endereço", "address", 0x0f, CTRL),
            s("back", "←", "voltar", "back", 0x50, ALT),
        )),
        // Omarchy defaults (default/hypr/bindings): Super+W closes, Super+Space menu, Super+Return terminal.
        Triple("Janela · Hyprland", "Window · Hyprland", listOf(
            s("super", "⌘", "Super", "Super", 0, SUPER), s("close_window", "⌘W", "fechar janela", "close window", 0x1a, SUPER),
            s("menu", "⌘␣", "menu", "menu", 0x2c, SUPER), s("terminal", "⌘↵", "terminal", "terminal", 0x28, SUPER),
            s("fullscreen", "⌘F", "tela cheia", "fullscreen", 0x09, SUPER), s("alt_tab", "⇥", "Alt+Tab", "Alt+Tab", 0x2b, ALT),
        )),
        Triple("Teclas soltas", "Single keys", listOf(
            s("esc", "Esc", "cancelar", "cancel", 0x29), s("enter", "↵", "Enter", "Enter", 0x28), s("tab", "Tab", "Tab", "Tab", 0x2b),
            s("delete", "Del", "apagar", "delete", 0x4c), s("home", "Home", "início", "home", 0x4a), s("end", "End", "fim", "end", 0x4d),
            s("page_up", "PgUp", "página ↑", "page ↑", 0x4b), s("page_down", "PgDn", "página ↓", "page ↓", 0x4e), s("f5", "F5", "F5", "F5", 0x3e),
        )),
    )

    private val all = catalog.flatMap { it.third }.associateBy { it.id }

    fun byId(id: String): Shortcut = all.getValue(id)

    /**
     * Copy, paste and cut over the network go through Omarchy's universal clipboard (Super+C/V/X:
     * terminals too, and the copy comes to the phone); null for any other key, or over Bluetooth,
     * where the PC may not be Omarchy.
     */
    fun clipboardAction(id: String, network: Boolean): String? = id.takeIf { network && it in setOf("copy", "paste", "cut") }

    val defaultSlots = listOf("copy", "paste", "undo", "close_tab", "esc")

    /** Saved slots ("a,b,c,d,e"); an unknown or missing id falls back to that slot's default. */
    fun parseSlots(saved: String?): List<String> {
        val ids = saved?.split(",").orEmpty()
        return defaultSlots.mapIndexed { i, default -> ids.getOrNull(i)?.takeIf { it in all } ?: default }
    }

    /** Letters of the modifier strip in the Teclas layer (label to HID usage). */
    val letters = listOf("C" to 0x06, "V" to 0x19, "X" to 0x1b, "A" to 0x04, "Z" to 0x1d, "T" to 0x17, "1" to 0x1e)

    /** Editing row of the Teclas layer. */
    val edit = listOf(
        s("copy", "C", "Copiar", "Copy", 0x06, CTRL), s("paste", "V", "Colar", "Paste", 0x19, CTRL),
        s("cut", "X", "Recortar", "Cut", 0x1b, CTRL), s("undo", "Z", "Desfazer", "Undo", 0x1d, CTRL),
    )
}
