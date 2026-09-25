package com.sandevsystems.omarchyremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sandevsystems.omarchyremote.KeypadViewModel
import com.sandevsystems.omarchyremote.Transport
import com.sandevsystems.omarchyremote.input.KeyboardLayout
import com.sandevsystems.omarchyremote.input.Shortcuts

// Layers of design v2 (DESIGN §3 KeysLayer/TextLayer, §4.3, §4.7): they rise over the base with a
// scrim and give it back untouched.

/** HID usages of the keys on screen. */
object Usage {
    const val ESC = 0x29
    const val TAB = 0x2b
    const val BACKSPACE = 0x2a
    const val DELETE = 0x4c
    const val ENTER = 0x28
    const val RIGHT = 0x4f
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52
}

/** A layer's name and a visible way out (the grabber and the scrim are not enough to find). */
@Composable
private fun LayerHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = KeypadType.HostName, color = KeypadColors.Text)
        ActionKey(tr("Fechar", "Close"), onClose)
    }
}

/** Scrim behind a layer: the base does not take touches; tapping it closes the layer. */
@Composable
fun Scrim(onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(KeypadColors.Scrim).clickable(onClickLabel = tr("Fechar", "Close"), indication = null,
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onDismiss))
}

/** Portrait keys layer: modifier strip, modifiers, edit keys, arrows, clipboard, compact click bar. */
@Composable
fun KeysLayer(vm: KeypadViewModel, enabled: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(KeypadShapes.Layer).background(KeypadColors.Surface2)
            .border(1.dp, KeypadColors.Line, KeypadShapes.Layer).navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Grabber(onClose, Modifier.align(Alignment.CenterHorizontally))
        LayerHeader(tr("Teclado", "Keyboard"), onClose)
        if (vm.modifiers != 0) ModifierStrip(vm, enabled)
        ModifierKeys(vm, enabled, KeypadDimens.KeyHeight)
        KeyRow {
            Key("Esc", { vm.pressKey(Usage.ESC) }, Modifier.weight(1f), style = KeypadType.KeyCompact, enabled = enabled)
            Key("Tab", { vm.pressKey(Usage.TAB) }, Modifier.weight(1f), style = KeypadType.KeyCompact, enabled = enabled)
            Key("", { vm.pressKey(Usage.BACKSPACE) }, Modifier.weight(1f), icon = Glyph.Backspace, enabled = enabled, description = tr("Apagar", "Backspace"))
            Key("Del", { vm.pressKey(Usage.DELETE) }, Modifier.weight(1f), style = KeypadType.KeyCompact, enabled = enabled, description = tr("Apagar à frente", "Delete"))
            Key("Enter", { vm.pressKey(Usage.ENTER) }, Modifier.weight(1f), style = KeypadType.KeyCompact, enabled = enabled)
        }
        ArrowRow(vm, enabled)
        KeyRow {
            for ((label, usage) in listOf("Home" to 0x4a, "End" to 0x4d, "PgUp" to 0x4b, "PgDn" to 0x4e)) {
                Key(label, { vm.pressKey(usage) }, Modifier.weight(1f), height = KeypadDimens.KeyHeightSmall, style = KeypadType.KeySmall, enabled = enabled)
            }
        }
        KeyRow {
            for (s in Shortcuts.edit) {
                Key(s.caption, { vm.pressShortcut(s.key) }, Modifier.weight(1f), height = KeypadDimens.KeyHeightSmall, style = KeypadType.KeySmall, enabled = enabled)
            }
        }
        ClickBar(vm.dragging, enabled, { vm.click(com.sandevsystems.omarchyremote.input.MouseButtons.LEFT) }, vm::toggleDrag,
            { vm.click(com.sandevsystems.omarchyremote.input.MouseButtons.RIGHT) }, compact = true)
    }
}

/** "CTRL VALE PARA A PRÓXIMA TECLA" + Soltar, and the letters that usually go with it. */
@Composable
private fun ModifierStrip(vm: KeypadViewModel, enabled: Boolean) {
    val names = modifierNames.filter { vm.modifiers and it.first != 0 }.joinToString("+") { it.second }
    Column(
        Modifier.fillMaxWidth().clip(KeypadShapes.SegmentTrack).background(KeypadColors.Accent.copy(alpha = 0.07f))
            .border(1.dp, KeypadColors.AccentBorder35, KeypadShapes.SegmentTrack).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(KeypadShapes.Segment).background(KeypadColors.Accent))
            Overline(tr("$names vale para a próxima tecla", "$names applies to the next key"), Modifier.padding(start = 7.dp).weight(1f), color = KeypadColors.Accent)
            Box(Modifier.heightIn(min = KeypadDimens.MinTouch).clickable(onClickLabel = tr("Soltar modificadores", "Release modifiers")) { vm.releaseModifiers() },
                contentAlignment = Alignment.Center) {
                Text(tr("Soltar", "Release"), Modifier.border(1.dp, KeypadColors.Accent.copy(alpha = 0.4f), KeypadShapes.Segment).padding(horizontal = 9.dp, vertical = 4.dp),
                    style = KeypadType.Caption, color = KeypadColors.Accent)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((label, usage) in Shortcuts.letters) {
                Key(label, { vm.pressKey(usage) }, Modifier.weight(1f), height = KeypadDimens.ModifierStripKey,
                    style = KeypadType.MonoValue, enabled = enabled, description = "$names+$label")
            }
        }
    }
}

@Composable
private fun ModifierKeys(vm: KeypadViewModel, enabled: Boolean, height: androidx.compose.ui.unit.Dp, columns: Int = 4) {
    val view = LocalView.current
    val keys = modifierNames
    val rows = keys.chunked(columns)
    Column(verticalArrangement = Arrangement.spacedBy(if (columns == 4) 7.dp else 6.dp)) {
        for (row in rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (columns == 4) 7.dp else 6.dp)) {
                for ((mask, name) in row) {
                    val on = vm.modifiers and mask != 0
                    Key(name, { if (on) Haptic.off(view) else Haptic.on(view); vm.toggleModifier(mask) }, Modifier.weight(1f),
                        height = height, on = on, enabled = enabled)
                }
            }
        }
    }
}

@Composable
private fun ArrowRow(vm: KeypadViewModel, enabled: Boolean) {
    KeyRow {
        Key("", { vm.pressKey(Usage.LEFT) }, Modifier.weight(1f), icon = Glyph.ArrowLeft, enabled = enabled, description = tr("Seta esquerda", "Left arrow"))
        Key("", { vm.pressKey(Usage.UP) }, Modifier.weight(1f), icon = Glyph.ArrowUp, enabled = enabled, description = tr("Seta acima", "Up arrow"))
        Key("", { vm.pressKey(Usage.DOWN) }, Modifier.weight(1f), icon = Glyph.ArrowDown, enabled = enabled, description = tr("Seta abaixo", "Down arrow"))
        Key("", { vm.pressKey(Usage.RIGHT) }, Modifier.weight(1f), icon = Glyph.ArrowRight, enabled = enabled, description = tr("Seta direita", "Right arrow"))
    }
}

/** Text layer above the Android keyboard: sunken field, PC layout, Limpar · Enter · Enviar. */
@Composable
fun TextLayer(vm: KeypadViewModel, capsLock: Boolean, enabled: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    HideKeyboardOnLeave()
    Column(
        modifier.fillMaxWidth().imePadding().clip(KeypadShapes.Layer).background(KeypadColors.Surface2)
            .border(1.dp, KeypadColors.Line, KeypadShapes.Layer).padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Grabber(onClose, Modifier.align(Alignment.CenterHorizontally))
        LayerHeader(tr("Escrever no PC", "Type on the PC"), onClose)
        Column(
            Modifier.fillMaxWidth().clip(KeypadShapes.Card).background(KeypadColors.Surface1)
                .border(1.dp, KeypadColors.Line2, KeypadShapes.Card).sunken().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            BasicTextField(
                value = vm.draft,
                onValueChange = { vm.draft = it },
                readOnly = vm.typing, // keeps focus and the keyboard while sending
                textStyle = KeypadType.Body.copy(fontSize = KeypadType.Key.fontSize * 16 / 15, color = KeypadColors.Text),
                cursorBrush = SolidColor(KeypadColors.Accent),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).focusRequester(focus)
                    .semantics { contentDescription = tr("Texto para o computador", "Text for the computer") },
            )
        }
        if (capsLock) Text(tr("Caps Lock está ligado no computador", "The computer has Caps Lock on"), style = KeypadType.Body, color = KeypadColors.Danger)
        if (vm.transport == Transport.NETWORK) {
            // The PC's clipboard (network only): long text goes there at once, with no layout to get wrong.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Área de transferência do PC", "PC clipboard"), Modifier.weight(1f), style = KeypadType.Caption, color = KeypadColors.TextDim)
                ActionKey(tr("Copiar do PC", "Copy from PC"), vm::copyPcClipboard, enabled = enabled,
                    description = tr("Copiar a área de transferência do PC para o celular", "Copy the PC clipboard to the phone"))
                ActionKey(tr("Colar no PC", "Paste on PC"), { vm.sendClipboardToPc(vm.draft) },
                    enabled = enabled && vm.draft.isNotBlank(), description = tr("Pôr este texto na área de transferência do PC", "Put this text on the PC clipboard"))
            }
        }
        val dictate = rememberDictation({ vm.showMessage(tr("Este celular não tem reconhecimento de voz.", "This phone doesn't have speech recognition.")) }) { spoken ->
            vm.draft = if (vm.draft.isBlank()) spoken else vm.draft.trimEnd() + " " + spoken
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(tr("Ditar", "Dictate"), dictate, Modifier.width(52.dp), height = 52.dp, icon = Glyph.Mic, enabled = !vm.typing, description = tr("Ditar o texto", "Dictate the text"))
            Key(tr("Limpar", "Clear"), { vm.draft = "" }, Modifier.weight(1f), height = 52.dp, enabled = !vm.typing)
            Key("Enter", { vm.pressKey(Usage.ENTER) }, Modifier.weight(1f), height = 52.dp, enabled = enabled && !vm.typing)
            if (vm.typing) {
                ActionButton(tr("Cancelar", "Cancel"), vm::cancelTyping, Modifier.weight(2f), primary = false, danger = true, height = 52.dp)
            } else {
                ActionButton(tr("Enviar", "Send"), vm::sendText, Modifier.weight(2f), icon = Glyph.Send, height = 52.dp,
                    enabled = enabled && !capsLock && vm.draft.isNotEmpty())
            }
        }
    }
}

/**
 * Landscape keys layer (two thumbs): arrows and modifiers on the left, edit keys on the right,
 * trackpad in the middle, and a field that opens the text layer.
 */
@Composable
fun LandscapeKeysLayer(
    vm: KeypadViewModel,
    enabled: Boolean,
    status: @Composable () -> Unit,
    trackpad: @Composable (Modifier) -> Unit,
    onText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize().background(KeypadColors.Bg).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.width(216.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.width(68.dp))
                Key("", { vm.pressKey(Usage.UP) }, Modifier.width(68.dp), height = 64.dp, icon = Glyph.ArrowUp, enabled = enabled, description = tr("Seta acima", "Up arrow"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key("", { vm.pressKey(Usage.LEFT) }, Modifier.width(68.dp), height = 64.dp, icon = Glyph.ArrowLeft, enabled = enabled, description = tr("Seta esquerda", "Left arrow"))
                Key("", { vm.pressKey(Usage.DOWN) }, Modifier.width(68.dp), height = 64.dp, icon = Glyph.ArrowDown, enabled = enabled, description = tr("Seta abaixo", "Down arrow"))
                Key("", { vm.pressKey(Usage.RIGHT) }, Modifier.width(68.dp), height = 64.dp, icon = Glyph.ArrowRight, enabled = enabled, description = tr("Seta direita", "Right arrow"))
            }
            ModifierKeys(vm, enabled, 62.dp, columns = 2)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            status()
            trackpad(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().height(KeypadDimens.MinTouch).clip(KeypadShapes.Macro).background(KeypadColors.Surface1)
                    .border(1.dp, KeypadColors.Line, KeypadShapes.Macro).clickable(onClickLabel = tr("Escrever texto", "Type text"), onClick = onText)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(Glyph.Lines, null, Modifier.size(17.dp), tint = KeypadColors.TextMute)
                Text(tr("Escrever e enviar para o PC", "Type and send to the PC"), style = KeypadType.Body.copy(fontSize = KeypadType.KeySmall.fontSize), color = KeypadColors.TextMute)
            }
        }
        Column(Modifier.width(216.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            KeyRow {
                Key("Esc", { vm.pressKey(Usage.ESC) }, Modifier.weight(1f), height = 64.dp, style = KeypadType.KeyCompact, enabled = enabled)
                Key("Tab", { vm.pressKey(Usage.TAB) }, Modifier.weight(1f), height = 64.dp, style = KeypadType.KeyCompact, enabled = enabled)
                Key("", { vm.pressKey(Usage.BACKSPACE) }, Modifier.weight(1f), height = 64.dp, icon = Glyph.Backspace, enabled = enabled, description = "Backspace")
            }
            KeyRow {
                Key("Del", { vm.pressKey(Usage.DELETE) }, Modifier.weight(1f), height = 64.dp, style = KeypadType.KeyCompact, enabled = enabled, description = "Delete")
                Key("Enter", { vm.pressKey(Usage.ENTER) }, Modifier.weight(1f), height = 64.dp, style = KeypadType.KeyCompact, enabled = enabled)
            }
            KeyRow {
                for (s in Shortcuts.edit) {
                    Key(s.glyph, { vm.pressShortcut(s.key) }, Modifier.weight(1f), height = 48.dp, style = KeypadType.MonoValue,
                        caption = s.caption.lowercase().take(6), enabled = enabled, description = s.caption)
                }
            }
        }
    }
}
