package com.sandevsystems.omarchyremote.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import androidx.compose.ui.window.Dialog
import com.sandevsystems.omarchyremote.ConnectionState
import com.sandevsystems.omarchyremote.Host
import com.sandevsystems.omarchyremote.KeypadViewModel
import com.sandevsystems.omarchyremote.display.VideoQuality
import com.sandevsystems.omarchyremote.Transport
import com.sandevsystems.omarchyremote.input.KeyboardLayout
import com.sandevsystems.omarchyremote.input.Shortcuts

// Sheets and dialogs of design v2 (DESIGN §3 Sheet, §4.4–4.6, §5 Editar atalhos).

/** Base sheet: surface2, radius 28 on top, grabber, title, 18 dp sides, 16 dp gap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
/** [tall]: content that arrives later (menus, agents) gets the full height now, since a sheet does not grow. */
fun AppSheet(title: String, onDismiss: () -> Unit, subtitle: String? = null, tall: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = KeypadShapes.Sheet,
        containerColor = KeypadColors.Surface2,
        scrimColor = KeypadColors.Scrim,
        dragHandle = { Grabber(onDismiss, Modifier.padding(top = 8.dp)) },
    ) {
        Column(
            // Never taller than the screen below the status bar: long sheets scroll instead of hiding under it.
            Modifier.fillMaxWidth().let { m ->
                val max = LocalConfiguration.current.screenHeightDp.dp * 0.88f
                if (tall) m.height(max) else m.heightIn(max = max)
            }.navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(title, style = KeypadType.SheetTitle, color = KeypadColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, Modifier.padding(top = 4.dp), style = KeypadType.Caption.copy(fontSize = KeypadType.KeySmall.fontSize), color = KeypadColors.TextDim)
            }
            content()
        }
    }
}

@Composable
private fun SectionDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(KeypadColors.Line))

/** Computador: Bluetooth (paired computers first) or network (Tailscale name + pairing code). */
@Composable
fun ComputerSheet(
    vm: KeypadViewModel,
    state: ConnectionState,
    discoverableSeconds: Int?,
    onMakeDiscoverable: () -> Unit,
    onDismiss: () -> Unit,
) {
    // The tabs only choose what to look at; connecting to something is what switches the link.
    var view by rememberSaveable { mutableStateOf(vm.transport) }
    AppSheet("PC", onDismiss) {
        Segmented(listOf(Transport.NETWORK to tr("Rede", "Network"), Transport.BLUETOOTH to "Bluetooth"), view, { view = it }, Modifier.fillMaxWidth())
        Text(
            if (view == Transport.NETWORK) tr("Tudo: a tela do PC, os agentes e o menu, de qualquer lugar (pelo Tailscale).",
                "Everything: the PC's screen, agents and menu, from anywhere (over Tailscale).")
            else tr("Só teclado e mouse, perto do PC, sem internet.", "Keyboard and mouse only, near the PC, no internet."),
            style = KeypadType.Caption, color = KeypadColors.TextDim,
        )
        val connected = (state as? ConnectionState.Connected)?.host?.takeIf { vm.transport == view }
        if (connected != null) {
            HostRow(connected, current = true, trailing = {
                ActionButton(tr("Desconectar", "Disconnect"), { vm.disconnect(); onDismiss() }, primary = false, danger = true, height = KeypadDimens.MinTouch)
            })
        }
        if (view == Transport.BLUETOOTH) {
            LaunchedEffect(Unit) { vm.loadHosts() }
            BluetoothPart(vm, connected, discoverableSeconds, onMakeDiscoverable, onDismiss)
        } else {
            NetworkPart(vm, connected != null, onDismiss)
        }
    }
}

@Composable
private fun BluetoothPart(vm: KeypadViewModel, connected: Host?, seconds: Int?, onMakeDiscoverable: () -> Unit, onDismiss: () -> Unit) {
    var showOthers by remember { mutableStateOf(false) }
    val others = vm.hosts.filter { it.address != connected?.address }
    val computers = others.filter { it.isComputer }
    val rest = others.filterNot { it.isComputer }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Overline(tr("PCs pareados", "Paired PCs"))
        if (computers.isEmpty()) Text(tr("Nenhum PC pareado ainda.", "No paired PCs yet."), style = KeypadType.Body, color = KeypadColors.TextDim)
        for (host in computers) HostRow(host, onClick = { vm.connect(host); onDismiss() })
        if (rest.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { showOthers = !showOthers },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Glyph.ChevronDown, null, Modifier.size(16.dp), tint = KeypadColors.TextDim)
                Text(if (showOthers) tr("Esconder outros aparelhos", "Hide other devices") else tr("Mostrar outros ${rest.size} aparelhos (fones, relógios…)", "Show ${rest.size} other devices (headphones, watches…)"),
                    style = KeypadType.Body.copy(fontSize = KeypadType.Key.fontSize), color = KeypadColors.TextDim)
            }
            if (showOthers) for (host in rest) HostRow(host, onClick = { vm.connect(host); onDismiss() })
        }
    }
    val minutes = seconds?.let { "%d:%02d".format(it / 60, it % 60) } ?: "2:00"
    Key(if (seconds != null) tr("Visível para o PC · $minutes", "Visible to the PC · $minutes") else tr("Parear um PC novo", "Pair a new PC"), onMakeDiscoverable,
        Modifier.fillMaxWidth(), height = 52.dp, enabled = seconds == null)
    Text(
        if (seconds != null) tr("No PC, abra o Bluetooth e escolha este celular.", "On the PC, open Bluetooth and choose this phone.")
        else tr("O celular fica visível por 2 minutos para o PC encontrá-lo.", "The phone stays visible for 2 minutes so the PC can find it."),
        style = KeypadType.Caption, color = KeypadColors.TextMute,
    )
}

@Composable
private fun HostRow(host: Host, current: Boolean = false, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val border = if (current) KeypadColors.Ok.copy(alpha = 0.6f) else KeypadColors.Line
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(KeypadShapes.Macro).background(KeypadColors.Surface2)
            .border(1.dp, border, KeypadShapes.Macro)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = tr("Conectar a ${host.name}", "Connect to ${host.name}"), onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).clip(KeypadShapes.Key).background(KeypadColors.Surface3), contentAlignment = Alignment.Center) {
            Icon(Glyph.ViewPc, null, Modifier.size(18.dp), tint = if (current) KeypadColors.Ok else KeypadColors.TextDim)
        }
        Column(Modifier.weight(1f)) {
            Text(host.name, style = KeypadType.Key, color = KeypadColors.Text, maxLines = 1)
            Text(if (current) tr("conectado", "connected") else host.address, style = KeypadType.Caption,
                color = if (current) KeypadColors.Ok else KeypadColors.TextMute, maxLines = 1)
        }
        if (trailing != null) trailing() else if (onClick != null) Icon(Glyph.ArrowRight, null, Modifier.size(16.dp), tint = KeypadColors.TextMute)
    }
}

/** Pairing over the network: the QR code is the way; typing the address and code is the fallback. */
@Composable
private fun NetworkPart(vm: KeypadViewModel, connected: Boolean, onDismiss: () -> Unit) {
    var showCode by remember { mutableStateOf(false) }
    var typing by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scan = {
        // Google's scanner runs its own camera UI; the app only receives the text.
        GmsBarcodeScanning.getClient(context, GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
            .startScan()
            .addOnSuccessListener { barcode ->
                if (vm.pairFromQr(barcode.rawValue.orEmpty())) onDismiss()
                else vm.showMessage(tr("Esse QR não é do Omarchy Remote. No PC: Super + Espaço › Phone › Pair phone.", "That QR code isn't from Omarchy Remote. On the PC: Super + Space › Phone › Pair phone."))
            }
            .addOnFailureListener { vm.showMessage(tr("Não foi possível abrir a câmera para o QR.", "Couldn't open the camera for the QR code.")) }
        Unit
    }
    if (!connected) {
        ActionButton(tr("Ler o QR do PC", "Scan the PC's QR code"), scan, Modifier.fillMaxWidth(), icon = Glyph.Grid)
        Text(tr("No PC: Super + Espaço › Phone › Pair phone mostra o QR.", "On the PC: Super + Space › Phone › Pair phone shows the QR code."),
            style = KeypadType.Caption, color = KeypadColors.TextDim)
    }
    if (!typing) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { typing = true }, contentAlignment = Alignment.CenterStart) {
            Text(if (connected) tr("Trocar de PC ›", "Switch PC ›") else tr("Digitar endereço e código ›", "Type the address and code ›"),
                style = KeypadType.Body, color = KeypadColors.TextDim)
        }
        if (connected) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { scan() }, contentAlignment = Alignment.CenterStart) {
                Text(tr("Ler outro QR ›", "Scan another QR code ›"), style = KeypadType.Body, color = KeypadColors.TextDim)
            }
        }
        return
    }
    Field(tr("Endereço do PC", "PC address"), vm.networkAddress, { vm.networkAddress = it }, KeyboardType.Uri)
    Field(
        tr("Código de pareamento", "Pairing code"), vm.pairingCode, { vm.pairingCode = it }, KeyboardType.Password,
        // A secret: masked unless the eye is pressed (screenshots, someone looking over the shoulder).
        visual = if (showCode) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            Box(
                Modifier.size(40.dp).clip(KeypadShapes.Key).border(1.dp, KeypadColors.Line, KeypadShapes.Key).clickable { showCode = !showCode }
                    .semantics { contentDescription = if (showCode) tr("Esconder código", "Hide code") else tr("Mostrar código", "Show code") },
                contentAlignment = Alignment.Center,
            ) { Icon(Glyph.Eye, null, Modifier.size(18.dp), tint = KeypadColors.TextDim) }
        },
    )
    ActionButton(tr("Conectar", "Connect"), { vm.connectNetwork(); onDismiss() }, Modifier.fillMaxWidth(), icon = Glyph.Wifi,
        enabled = vm.networkAddress.isNotBlank() && vm.pairingCode.isNotBlank())
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    type: KeyboardType,
    focusedLabel: Boolean = false,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Overline(label, color = if (focusedLabel) KeypadColors.Accent else KeypadColors.TextMute)
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(KeypadShapes.Field).background(KeypadColors.Surface1)
                .border(1.dp, if (focusedLabel) KeypadColors.Accent else KeypadColors.Line, KeypadShapes.Field).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicTextField(
                value, onChange, Modifier.weight(1f).semantics { contentDescription = label }, singleLine = true,
                textStyle = KeypadType.MonoValue.copy(fontWeight = FontWeight.Normal, color = KeypadColors.Text),
                keyboardOptions = KeyboardOptions(keyboardType = type), visualTransformation = visual,
                cursorBrush = SolidColor(KeypadColors.Accent),
            )
            trailing?.invoke()
        }
    }
}

/** Ajustes: trackpad, mode on the PC screen, active PC layout, shortcuts and gesture tips. */
@Composable
fun SettingsSheet(vm: KeypadViewModel, onUnlockSetup: () -> Unit = {}, onEditShortcuts: () -> Unit, onGestures: () -> Unit, onDismiss: () -> Unit) {
    AppSheet(tr("Ajustes", "Settings"), onDismiss) {
        Overline("Trackpad")
        SettingSlider(tr("Sensibilidade", "Sensitivity"), vm.sensitivity, "%.1f×".format(vm.sensitivity), 0.5f..3f, 24, vm::changeSensitivity)
        SettingToggle(tr("Rolagem natural", "Natural scrolling"), vm.naturalScroll, vm::changeNaturalScroll, tr("A página acompanha os dedos", "The page follows your fingers"))
        SettingToggle(tr("Tocar para clicar", "Tap to click"), vm.tapToClick, vm::changeTapToClick, tr("Um toque leve é um clique", "A light tap is a click"))
        SectionDivider()
        Overline(tr("Tela do PC", "PC screen"))
        Segmented(listOf(false to tr("Toque direto", "Direct touch"), true to "Trackpad"), vm.videoTouchpad, vm::changeVideoTouchpad, Modifier.fillMaxWidth())
        Text(if (vm.videoTouchpad) tr("O dedo move o cursor, como no notebook.", "Your finger moves the cursor, like a laptop.")
            else tr("Toque onde quer clicar; arrastar rola a página.", "Tap where you want to click; drag to scroll."), style = KeypadType.Caption, color = KeypadColors.TextMute)
        Segmented(VideoQuality.entries.map { it to it.label }, vm.videoQuality, vm::changeVideoQuality, Modifier.fillMaxWidth())
        Text(tr("Leve gasta bem menos dados no 4G; Nítido é o mais fluido.", "Light uses far less mobile data; Sharp is the smoothest."), style = KeypadType.Caption, color = KeypadColors.TextMute)
        SectionDivider()
        Overline(tr("Teclado do PC", "PC keyboard"))
        Segmented(listOf(KeyboardLayout.ABNT2 to tr("Português (ABNT2)", "Portuguese (ABNT2)"), KeyboardLayout.US to tr("Inglês (US)", "English (US)")), vm.layout, vm::changeLayout, Modifier.fillMaxWidth())
        Text(tr("O mesmo layout que está ativo no PC, para os acentos saírem certos.", "The same layout the PC uses, so accents come out right."), style = KeypadType.Caption, color = KeypadColors.TextMute)
        SettingToggle(tr("Volume do celular controla o PC", "Phone volume controls the PC"), vm.volumeKeysToPc, vm::changeVolumeKeysToPc,
            tr("Conectado, os botões de volume mudam o som do PC", "While connected, the volume buttons change the PC's sound"))
        SectionDivider()
        Overline(tr("Avisos", "Notifications"))
        SettingToggle(
            tr("Avisar quando um agente precisar de mim", "Tell me when an agent needs me"), vm.followAgents, vm::changeFollowAgents,
            tr("Mantém a conexão mesmo com o app fechado", "Keeps the connection even with the app closed"),
        )
        SettingToggle(tr("Notificações do PC no celular", "PC notifications on the phone"), vm.pcNotifications, vm::changePcNotifications,
            tr("O que aparece no PC aparece aqui também", "What shows up on the PC shows up here too"))
        Row(Modifier.fillMaxWidth().heightIn(min = KeypadDimens.MinTouch).clickable(onClick = onUnlockSetup), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Desbloquear o PC pela digital", "Unlock the PC with your fingerprint"), style = KeypadType.Body.copy(fontSize = KeypadType.Key.fontSize), color = KeypadColors.Text)
                Text(tr("Sem digitar a senha no bloqueio do Omarchy", "Without typing the password on Omarchy's lock"), style = KeypadType.Caption, color = KeypadColors.TextMute)
            }
            Icon(Glyph.ChevronRight, null, Modifier.size(16.dp), tint = KeypadColors.TextMute)
        }
        var mirrorAccess by remember { mutableStateOf(false) }
        var mirrorApps by remember { mutableStateOf(false) }
        if (mirrorAccess) MirrorAccessSheet { mirrorAccess = false }
        if (mirrorApps) MirrorAppsSheet(vm) { mirrorApps = false }
        SettingToggle(tr("Notificações do celular no PC", "Phone notifications on the PC"), vm.phoneNotifications,
            { if (!vm.changePhoneNotifications(it)) mirrorAccess = true },
            tr("Mensagens do celular aparecem no PC, e dá para responder de lá", "Phone messages show on the PC, and you can reply from there"))
        if (vm.phoneNotifications) {
            Row(Modifier.fillMaxWidth().heightIn(min = KeypadDimens.MinTouch).clickable { mirrorApps = true }, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Quais apps", "Which apps"), Modifier.weight(1f), style = KeypadType.Body.copy(fontSize = KeypadType.Key.fontSize), color = KeypadColors.Text)
                val off = vm.mirrorExcluded.size
                Text(if (off == 0) tr("Todos", "All") else tr("$off desligado${if (off > 1) "s" else ""}", "$off off"), style = KeypadType.Caption, color = KeypadColors.TextMute)
                Icon(Glyph.ChevronRight, null, Modifier.padding(start = 6.dp).size(16.dp), tint = KeypadColors.TextMute)
            }
        }
        SettingToggle(tr("Música do PC na tela de bloqueio", "PC music on the lock screen"), vm.pcMedia, vm::changePcMedia,
            tr("Pausar e pular faixa sem abrir o app", "Pause and skip without opening the app"))
        SettingToggle(tr("Miniaturas das telas do PC", "PC screen thumbnails"), vm.liveThumbs, vm::changeLiveThumbs,
            tr("Mostra o que está em cada monitor no mapa, só no Wi-Fi (no 4G/5G fica o desenho, sem gastar dados)",
                "Shows what's on each monitor in the map, on Wi-Fi only (on mobile data the drawing stays, no data used)"))
        SettingToggle(tr("Área de transferência compartilhada", "Shared clipboard"), vm.clipSync, vm::changeClipSync,
            tr("Copie num, cole no outro. Do celular, vai ao abrir o app ou pelo atalho rápido \"Copiar pro PC\". Senhas não passam.",
                "Copy on one, paste on the other. From the phone it goes when you open the app, or with the \"Copy to PC\" quick setting. Passwords stay out."))
        SectionDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Key(tr("Editar atalhos", "Edit shortcuts"), onEditShortcuts, Modifier.weight(1f), height = 50.dp)
            Key(tr("Dicas de gestos", "Gesture tips"), onGestures, Modifier.weight(1f), height = 50.dp)
        }
        SectionDivider()
        Overline(tr("Idioma", "Language"))
        Segmented(I18n.Choice.entries.map { it to if (it == I18n.Choice.SYSTEM) tr("O do celular", "Phone's") else it.label },
            I18n.choice, vm::changeLanguage, Modifier.fillMaxWidth())
        val context = LocalContext.current
        val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
        val hostInfo by vm.hostInfo.collectAsStateWithLifecycle()
        val view = androidx.compose.ui.platform.LocalView.current
        // Versions: the app's, and the PC service's with a comfortable Restart pill.
        Row(
            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(KeypadColors.Surface1).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Omarchy Remote" + (version?.let { " $it" } ?: ""), style = KeypadType.Body, color = KeypadColors.Text)
                hostInfo?.let { h ->
                    Text(tr("Serviço do PC ${h.version}", "PC service ${h.version}"), style = KeypadType.Caption, color = KeypadColors.TextMute)
                    if (h.outdated) Text(tr("Versão nova esperando", "New version waiting"), style = KeypadType.Caption, color = KeypadColors.Attention)
                }
            }
            if (hostInfo != null) {
                Box(
                    Modifier.heightIn(min = 40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .background(if (hostInfo?.outdated == true) KeypadColors.Attention else KeypadColors.Surface3)
                        .clickable(onClickLabel = tr("Reiniciar o serviço do PC", "Restart the PC service")) { Haptic.tap(view); vm.restartHost() }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tr("Reiniciar", "Restart"), style = KeypadType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = if (hostInfo?.outdated == true) androidx.compose.ui.graphics.Color.Black else KeypadColors.Text, maxLines = 1)
                }
            }
        }
    }
}

/** Editar atalhos: pick a slot of the row, then one of the grouped shortcuts for it. */
@Composable
fun ShortcutEditorSheet(vm: KeypadViewModel, initialSlot: Int?, onDismiss: () -> Unit) {
    var slot by remember { mutableStateOf(initialSlot ?: 0) }
    AppSheet(tr("Editar atalhos", "Edit shortcuts"), onDismiss, subtitle = tr("Escolha uma posição e depois o atalho dela.", "Pick a slot, then its shortcut.")) {
        KeyRow {
            vm.shortcutSlots.forEachIndexed { i, s ->
                Pressable(
                    { slot = i }, Modifier.weight(1f).height(KeypadDimens.MacroHeight), shape = KeypadShapes.Macro,
                    background = if (i == slot) KeypadColors.AccentTint12 else KeypadColors.Surface3,
                    border = if (i == slot) KeypadColors.Accent else KeypadColors.Line, description = tr("Posição ${i + 1}: ${s.caption}", "Slot ${i + 1}: ${s.caption}"),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.glyph, style = KeypadType.MacroGlyph, color = if (i == slot) KeypadColors.Accent else KeypadColors.Text, maxLines = 1)
                        Text(s.caption, style = KeypadType.MacroCaption, color = KeypadColors.TextMute, maxLines = 1)
                    }
                }
            }
        }
        for ((group, items) in Shortcuts.groups) {
            Overline(group)
            for (row in items.chunked(4)) {
                KeyRow {
                    for (s in row) {
                        val chosen = vm.shortcutSlots.getOrNull(slot)?.id == s.id
                        Pressable(
                            { vm.setShortcutSlot(slot, s.id) }, Modifier.weight(1f).height(KeypadDimens.MacroHeight), shape = KeypadShapes.Macro,
                            background = KeypadColors.Surface3, border = if (chosen) KeypadColors.Accent else KeypadColors.Line, description = s.caption,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(s.glyph, style = KeypadType.MacroGlyph, color = KeypadColors.Text, maxLines = 1)
                                Text(s.caption, style = KeypadType.MacroCaption, color = KeypadColors.TextMute, maxLines = 1)
                            }
                        }
                    }
                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Dicas de gestos: one line per gesture, the number of fingers in a square. */
@Composable
fun GesturesDialog(touchpad: Boolean, onDone: () -> Unit) {
    Dialog(onDismissRequest = onDone) {
        Column(
            Modifier.widthIn(max = 420.dp).clip(KeypadShapes.Layer.copy(bottomStart = KeypadShapes.Layer.topStart, bottomEnd = KeypadShapes.Layer.topEnd))
                .background(KeypadColors.Surface2).border(1.dp, KeypadColors.Line, KeypadShapes.Card).padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column {
                Text(if (touchpad) tr("Modo trackpad", "Trackpad mode") else tr("Modo toque direto", "Direct touch mode"), style = KeypadType.SheetTitle, color = KeypadColors.Text)
                Text(if (touchpad) tr("Vale para o pad e para a tela do PC", "Works on the pad and on the PC screen") else tr("Na tela do PC", "On the PC screen"), Modifier.padding(top = 4.dp), style = KeypadType.Caption.copy(fontSize = KeypadType.KeySmall.fontSize), color = KeypadColors.TextDim)
            }
            val rows = if (touchpad) listOf(
                Triple("1", tr("Um dedo", "One finger"), tr("move o cursor · toque clica", "moves the cursor · tap to click")),
                Triple("2", tr("Dois toques e segure", "Double-tap and hold"), tr("arrasta ou seleciona", "to drag or select")),
                Triple("2", tr("Dois dedos", "Two fingers"), tr("rolam · toque com dois é botão direito", "scroll · a two-finger tap is a right-click")),
                Triple("3", tr("Três dedos para os lados", "Three fingers sideways"), tr("trocam de workspace · toque com três é botão do meio", "switch workspaces · a three-finger tap is a middle-click")),
                Triple("⇲", tr("Pinça", "Pinch"), tr("dá zoom e a vista segue o cursor", "to zoom; the view follows the cursor")),
            ) else listOf(
                Triple("1", tr("Toque", "Tap"), tr("clica no ponto · arrastar rola a página (solte rápido e ela continua)", "clicks the spot · dragging scrolls the page (flick and it keeps going)")),
                Triple("1", tr("Segure", "Hold"), tr("mostra o ponto ampliado · deslize para arrastar ou selecionar · solte para o botão direito", "magnifies the spot · slide to drag or select · release for a right-click")),
                Triple("2", tr("Pinça", "Pinch"), tr("dá zoom · dois dedos arrastam a vista · toque com dois: ver tudo", "to zoom · two fingers pan the view · two-finger tap: see everything")),
                Triple("⌨", tr("Digitar", "Type"), tr("no trilho à direita: o teclado do celular escreve direto no PC", "on the right rail: the phone's keyboard types straight into the PC")),
            )
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for ((badge, gesture, effect) in rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(38.dp).clip(KeypadShapes.Key).background(KeypadColors.Surface3).border(1.dp, KeypadColors.Line, KeypadShapes.Key),
                            contentAlignment = Alignment.Center) { Text(badge, style = KeypadType.MonoValue, color = KeypadColors.Accent) }
                        Text(gestureText(gesture, effect), Modifier.padding(top = 9.dp), style = KeypadType.Body.copy(fontSize = KeypadType.Key.fontSize * 14.5f / 15), color = KeypadColors.TextDim)
                    }
                }
            }
            SectionDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Troque o modo e a velocidade em Ajustes.", "Change the mode and speed in Settings."), Modifier.weight(1f), style = KeypadType.Caption.copy(fontSize = KeypadType.KeySmall.fontSize), color = KeypadColors.TextMute)
                ActionButton(tr("Entendi", "Got it"), onDone, height = 50.dp)
            }
        }
    }
}

private fun gestureText(gesture: String, effect: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = KeypadColors.Text, fontWeight = FontWeight.SemiBold)) { append(gesture) }
    append(" ")
    append(effect)
}
