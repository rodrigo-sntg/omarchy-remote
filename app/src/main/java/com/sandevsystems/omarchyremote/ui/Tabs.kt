package com.sandevsystems.omarchyremote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandevsystems.omarchyremote.ConnectionState
import com.sandevsystems.omarchyremote.KeypadViewModel
import com.sandevsystems.omarchyremote.input.ModifierKeys
import com.sandevsystems.omarchyremote.input.Shortcut
import com.sandevsystems.omarchyremote.network.Agent
import com.sandevsystems.omarchyremote.network.AgentScreen
import com.sandevsystems.omarchyremote.network.AgentsText
import com.sandevsystems.omarchyremote.network.CursorAt
import com.sandevsystems.omarchyremote.network.MonitorInfo
import com.sandevsystems.omarchyremote.network.UsageText
import com.sandevsystems.omarchyremote.network.Workspace
import kotlinx.coroutines.delay

/** The app's four places, one purpose each (design: the "Novo app" canvas page). */
enum class MainTab { CONTROL, AGENTS, PC }

private val CardShape = RoundedCornerShape(22.dp)

/** A screen's big title, like iOS: the name of the place, then one line of context. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = GroupType.LargeTitle, color = KeypadColors.Text, maxLines = 1)
            if (subtitle != null) Text(subtitle, Modifier.padding(top = 4.dp), style = GroupType.Lead, color = KeypadColors.TextDim)
        }
        trailing?.invoke()
    }
}

/** Bottom tab bar (a rail at the side in landscape): icon and name, the current one tinted. */
@Composable
fun TabBar(current: MainTab, waiting: Int, vertical: Boolean, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val items = listOf(
        Triple(MainTab.CONTROL, Glyph.Mouse, tr("Controle", "Control")),
        Triple(MainTab.AGENTS, Glyph.Terminal, tr("Agentes", "Agents")),
        Triple(MainTab.PC, RemoteMark, "Omarchy"),
    )
    val item: @Composable (MainTab, ImageVector, String, Modifier) -> Unit = { tab, icon, label, m ->
        val on = tab == current
        val tint = if (on) KeypadColors.Text else KeypadColors.TextMute
        Box(
            m.clickable(onClickLabel = label) { if (!on) { Haptic.tap(view); onSelect(tab) } }
                .semantics { contentDescription = label; if (on) stateDescription = tr("atual", "current") },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, null, Modifier.size(26.dp), tint = tint)
                Text(label, style = KeypadType.Caption.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = tint)
            }
            if (tab == MainTab.AGENTS && waiting > 0) {
                Box(
                    Modifier.align(Alignment.TopCenter).offset(x = 15.dp, y = 4.dp).heightIn(min = 18.dp).widthIn(min = 18.dp)
                        .clip(CircleShape).background(KeypadColors.Attention).padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("$waiting", style = KeypadType.Caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = KeypadColors.Bg) }
            }
        }
    }
    if (vertical) {
        Column(modifier.fillMaxHeight().width(84.dp).background(KeypadColors.Bg), verticalArrangement = Arrangement.Center) {
            for ((tab, icon, label) in items) item(tab, icon, label, Modifier.fillMaxWidth().height(72.dp))
        }
    } else {
        Column(modifier.fillMaxWidth().background(KeypadColors.Bg)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(KeypadColors.Line))
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
                for ((tab, icon, label) in items) item(tab, icon, label, Modifier.weight(1f).height(60.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Controle

/** The PC's name as a pill: the connection at a glance, a tap chooses or reconnects. */
@Composable
fun PcPill(state: ConnectionState, network: Boolean, onClick: () -> Unit) {
    val (title, detail) = statusTexts(state, network)
    Row(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(KeypadColors.Surface2)
            .clickable(onClickLabel = tr("Escolher PC", "Choose PC"), onClick = onClick).padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(state)
        Text(title, style = KeypadType.HostName, color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp))
        if (detail != null) Text(detail, style = KeypadType.Caption, color = KeypadColors.TextMute, maxLines = 1)
        Icon(Glyph.ChevronDown, null, Modifier.size(15.dp), tint = KeypadColors.TextMute)
    }
}

/** Compact workspace buttons for the top bar: the current one filled. */
@Composable
fun WorkspacePills(workspaces: List<Workspace>, onGo: (Int) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (w in workspaces) {
            val on = w.focused
            Box(
                Modifier.size(36.dp, 34.dp).clip(RoundedCornerShape(10.dp)).background(if (on) KeypadColors.Text else KeypadColors.Surface2)
                    .clickable { Haptic.tap(view); onGo(w.id) }
                    .semantics { contentDescription = "Workspace ${w.id}"; if (on) stateDescription = tr("atual", "current") },
                contentAlignment = Alignment.Center,
            ) { Text("${w.id}", style = KeypadType.MonoValue, color = if (on) KeypadColors.Bg else KeypadColors.TextDim) }
        }
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(KeypadDimens.MinTouch).clip(CircleShape).background(KeypadColors.Surface2)
            .clickable(onClickLabel = description, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = KeypadColors.TextDim) }
}

/**
 * Keeps the monitors' thumbnails fresh while a map shows them: every few seconds, on Wi-Fi only
 * (mobile data keeps the drawing), and only with the option on.
 */
@Composable
fun LiveThumbs(vm: KeypadViewModel, monitors: List<MonitorInfo>) {
    val names = monitors.filter { !it.extra }.map { it.name }
    LaunchedEffect(names, vm.liveThumbs) {
        if (!vm.liveThumbs) return@LaunchedEffect
        while (true) {
            if (vm.unmetered()) for (name in names) { vm.requestThumb(name, 480); delay(300) }
            delay(THUMB_EVERY_MS)
        }
    }
}

private const val THUMB_EVERY_MS = 3_000L

/**
 * The monitors in proportion, where the cursor is, and the way into Ver PC — one slim strip
 * (design 1A). A monitor opens Ver PC on it; the rest of the strip opens the cursor's.
 */
@Composable
fun MonitorStrip(
    monitors: List<MonitorInfo>, cursor: CursorAt?, thumbs: Map<String, androidx.compose.ui.graphics.ImageBitmap> = emptyMap(),
    /** The other things to look at (the focused window, the phone as a screen), behind "⋯". */
    more: List<Pair<String, () -> Unit>> = emptyList(),
    onOpen: (String?) -> Unit,
) {
    val real = monitors.filter { !it.extra }
    if (real.isEmpty()) return
    val names = monitorNames(real)
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth().height(100.dp).clip(CardShape).background(KeypadColors.Surface1)
            .clickable(onClickLabel = tr("Ver PC", "View PC")) { Haptic.tap(view); onOpen(cursor?.monitor) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val order = real.sortedBy { it.x }
            val left = order.minOf { it.x }
            val top = order.minOf { it.y }
            val spanW = (order.maxOf { it.x + it.width } - left).toFloat()
            val spanH = (order.maxOf { it.y + it.height } - top).toFloat()
            val gap = 8f
            val labelH = 18f
            val scale = minOf((maxWidth.value - gap * (order.size - 1)) / spanW, (maxHeight.value - labelH) / spanH)
            for ((i, m) in order.withIndex()) {
                val here = cursor?.monitor == m.name
                val x = (m.x - left) * scale + gap * i
                val h = m.height * scale
                val y = (maxHeight.value - labelH) - h  // bottoms aligned, like monitors on a desk
                val w = m.width * scale
                val shape = RoundedCornerShape(9.dp)
                Box(
                    Modifier.offset(x.dp, y.dp).size(w.dp, h.dp).clip(shape).background(KeypadColors.Surface2)
                        .then(if (here) Modifier.background(KeypadColors.Text.copy(alpha = 0.06f)) else Modifier)
                        .clickable(onClickLabel = tr("Ver ${names[m.name]}", "View ${names[m.name]}")) { Haptic.tap(view); onOpen(m.name) }
                        .semantics { contentDescription = "${names[m.name]} (${m.name})" + if (here) tr(", cursor aqui", ", cursor here") else "" },
                ) {
                    thumbs[m.name]?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(if (here) KeypadColors.Text else KeypadColors.Line2, style = Stroke(1.5.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(9.dp.toPx()))
                        if (here && cursor != null) {
                            val r = 5.dp.toPx()
                            val c = Offset((cursor.x * size.width).coerceIn(r * 2, size.width - r * 2), (cursor.y * size.height).coerceIn(r * 2, size.height - r * 2))
                            drawCircle(KeypadColors.Text.copy(alpha = 0.16f), r * 2.2f, c)
                            drawCircle(KeypadColors.Text, r, c)
                        }
                    }
                }
                Text(names[m.name] ?: m.name, Modifier.offset(x.dp, (maxHeight.value - labelH + 3).dp).width(maxOf(w, 60f).dp),
                    style = KeypadType.Caption.copy(fontWeight = if (here) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (here) KeypadColors.Text else KeypadColors.TextMute, maxLines = 1)
            }
        }
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Ver PC", "View PC"), style = KeypadType.KeySmall.copy(fontWeight = FontWeight.Bold), color = KeypadColors.Text)
                Icon(Glyph.ChevronRight, null, Modifier.size(18.dp), tint = KeypadColors.TextDim)
            }
            if (more.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(KeypadColors.Surface3)
                            .clickable(onClickLabel = tr("Outras fontes", "Other sources")) { Haptic.tap(view); open = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Glyph.More, tr("Outras fontes", "Other sources"), Modifier.size(18.dp), tint = KeypadColors.Text) }
                    androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = KeypadColors.Surface2) {
                        for ((label, action) in more) {
                            androidx.compose.material3.DropdownMenuItem(
                                { Text(label, style = GroupType.Lead, color = KeypadColors.Text) },
                                { open = false; action() },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The shortcut row as chips: the keys and what they do; hold one to change it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutChips(shortcuts: List<Shortcut>, onShortcut: (Shortcut) -> Unit, onEdit: (Int) -> Unit, onEnter: (() -> Unit)? = null) {
    val view = LocalView.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Enter first and always there: confirming on the PC is the most common tap after the pad.
        if (onEnter != null) {
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(13.dp)).background(KeypadColors.Text)
                    .clickable(onClickLabel = "Enter") { Haptic.tap(view); onEnter() }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text("⏎", style = KeypadType.MonoValue, color = KeypadColors.Bg)
                Text("Enter", style = KeypadType.Caption.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Bg)
            }
        }
        shortcuts.forEachIndexed { i, s ->
            val glyph = if (s.key.modifiers and ModifierKeys.CTRL != 0 && s.glyph.length == 1) "⌃${s.glyph}" else s.glyph
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(13.dp)).background(KeypadColors.Surface2)
                    .combinedClickable(
                        onClickLabel = s.caption, onLongClickLabel = tr("Trocar atalho", "Change shortcut"),
                        onLongClick = { Haptic.tap(view); onEdit(i) }, onClick = { Haptic.tap(view); onShortcut(s) },
                    ).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(glyph, style = KeypadType.MonoValue, color = KeypadColors.Text)
                Text(s.caption.replaceFirstChar { it.uppercase() }, style = KeypadType.Caption, color = KeypadColors.TextDim)
            }
        }
    }
}

/** The one big button of Controle: typing and keys live behind it. */
@Composable
fun KeyboardButton(onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp), background = KeypadColors.Text, border = null,
        raised = false, description = tr("Teclado", "Keyboard")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Glyph.Keyboard, null, Modifier.size(22.dp), tint = KeypadColors.Bg)
            Text(tr("Teclado", "Keyboard"), style = KeypadType.Key.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = KeypadColors.Bg)
        }
    }
}

/**
 * The keyboard, one place for everything that types: what the phone keyboard writes goes to the
 * PC live, letter by letter (corrections included); the keys the phone lacks are right above it;
 * "Mais teclas" has the rest and the PC's clipboard.
 */
@Composable
fun KeyboardPanel(vm: KeypadViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var ctrl by remember { mutableStateOf(false) }
    // The phone keyboard goes away with the panel (it would stay up over the tabs otherwise).
    HideKeyboardOnLeave()
    val hideKeyboard = rememberHideKeyboard()
    var more by rememberSaveable { mutableStateOf(false) }
    val dictate = rememberDictation({ vm.showMessage(tr("Este celular não tem reconhecimento de voz.", "This phone has no speech recognition.")) }) { spoken ->
        vm.typeLive(com.sandevsystems.omarchyremote.input.TypingDiff.Change(0, spoken))
    }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(KeypadColors.Surface2)
            .imePadding().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Teclado", "Keyboard"), Modifier.weight(1f), style = KeypadType.SheetTitle.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
                color = KeypadColors.Text)
            ActionKey(tr("Fechar", "Close"), { hideKeyboard(); onClose() })
        }
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(KeypadColors.Bg).padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(tr("AO VIVO", "LIVE"), style = KeypadType.Mono.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Accent)
            AndroidView(
                factory = { ctx ->
                    LiveTypingField(ctx) { change ->
                        if (ctrl && change.backspaces == 0 && change.insert.codePointCount(0, change.insert.length) == 1) {
                            vm.typeShortcut(change.insert, ModifierKeys.CTRL)
                            ctrl = false
                            true
                        } else {
                            vm.typeLive(change)
                            false
                        }
                    }.also { it.post { it.requestFocus(); it.showKeyboard() } }
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Text(tr("O que você digita vai direto para o PC, letra por letra.", "What you type goes straight to the PC, letter by letter."),
            style = KeypadType.Caption, color = KeypadColors.TextMute)
        val h = 44.dp
        KeyRow {
            Key("Ctrl", { ctrl = !ctrl }, Modifier.weight(1.1f), height = h, style = KeypadType.KeySmall, on = ctrl,
                description = tr("Ctrl para a próxima tecla", "Ctrl for the next key"))
            for ((label, usage) in listOf("Esc" to 0x29, "Tab" to 0x2B, "←" to 0x50, "↑" to 0x52, "↓" to 0x51, "→" to 0x4F)) {
                Key(label, { vm.typeKey(usage, if (ctrl) ModifierKeys.CTRL else 0); ctrl = false }, Modifier.weight(1f), height = h, style = KeypadType.KeySmall)
            }
        }
        KeyRow {
            Key(tr("Copiar", "Copy"), vm::copyOnPc, Modifier.weight(1f), height = h, style = KeypadType.KeySmall,
                description = tr("Copia o que está selecionado no PC, e traz para o celular", "Copies what is selected on the PC, and brings it to the phone"))
            Key(tr("Colar", "Paste"), vm::pasteOnPc, Modifier.weight(1f), height = h, style = KeypadType.KeySmall,
                description = tr("Cola no PC o que você copiou no celular", "Pastes on the PC what you copied on the phone"))
        }
        KeyRow {
            Key("", { vm.typeKey(0x2A) }, Modifier.weight(1f), height = h, icon = Glyph.Backspace, description = tr("Apagar", "Backspace"))
            Key(if (more) tr("Menos teclas", "Fewer keys") else tr("Mais teclas", "More keys"), { more = !more }, Modifier.weight(1.4f), height = h,
                style = KeypadType.KeySmall, on = more)
            Key("", dictate, Modifier.weight(1f), height = h, icon = Glyph.Mic, description = tr("Ditar", "Dictate"))
            Pressable({ vm.typeKey(0x28) }, Modifier.weight(1.4f).height(h), background = KeypadColors.Text, border = null, raised = false,
                description = "Enter") {
                Text("Enter", style = KeypadType.KeySmall.copy(fontWeight = FontWeight.Bold), color = KeypadColors.Bg)
            }
        }
        if (more) {
            KeyRow {
                for ((label, usage) in listOf("Home" to 0x4a, "End" to 0x4d, "PgUp" to 0x4b, "PgDn" to 0x4e, "Del" to 0x4c)) {
                    Key(label, { vm.typeKey(usage) }, Modifier.weight(1f), height = h, style = KeypadType.KeySmall)
                }
            }
            KeyRow {
                for ((label, mods) in listOf("Alt" to ModifierKeys.ALT, "Super" to ModifierKeys.SUPER, "Shift" to ModifierKeys.SHIFT)) {
                    Key(label, { vm.toggleModifier(mods) }, Modifier.weight(1f), height = h, style = KeypadType.KeySmall, on = vm.modifiers and mods != 0,
                        description = tr("$label para a próxima tecla", "$label for the next key"))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Área de transferência do PC", "PC clipboard"), Modifier.weight(1f), style = KeypadType.Caption, color = KeypadColors.TextDim)
                ActionKey(tr("Copiar do PC", "Copy from PC"), vm::copyPcClipboard)
            }
        }
    }
}

// ---------------------------------------------------------------- Tela


// ---------------------------------------------------------------- PC (PcTab.kt)

/** The PC's folders, to bring a file to the phone: the usual folders, then wherever the person goes. */
@Composable
internal fun PcFilesSheet(vm: KeypadViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { vm.listPcFiles(null) }
    val listing by vm.pcFiles.collectAsStateWithLifecycle()
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    // Files asked in this sheet (full path → the PC took the request, or null while asking).
    val asked = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean?>() }
    val view = LocalView.current
    AppSheet(tr("Pegar do PC", "Get from the PC"), onDismiss, subtitle = listing?.path?.replace(Regex("^/home/[^/]+"), "~"), tall = true) {
        val l = listing
        if (l == null) {
            Text(tr("Lendo as pastas…", "Reading folders…"), style = KeypadType.Body, color = KeypadColors.TextMute)
            return@AppSheet
        }
        // What was asked here, newest first: how far it is, where it landed, Abrir.
        val mine = transfers.filter { t -> t.incoming && asked.keys.any { it.substringAfterLast('/') == t.name } }.asReversed()
        for (t in mine.take(3)) TransferCard(t, { vm.openTransfer(t) })
        for ((path, ok) in asked) if (mine.none { it.name == path.substringAfterLast('/') }) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KeypadColors.Surface3).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(path.substringAfterLast('/'), Modifier.weight(1f), style = KeypadType.Key, color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (ok == false) tr("O PC não entregou", "The PC didn't send it") else tr("Pedindo ao PC…", "Asking the PC…"),
                    style = KeypadType.Caption, color = if (ok == false) KeypadColors.Danger else KeypadColors.TextDim)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((path, name) in l.roots) {
                val on = path == l.path
                Box(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) KeypadColors.Text else KeypadColors.Surface1)
                    .clickable { Haptic.tap(view); vm.listPcFiles(path) }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(name, style = KeypadType.Caption.copy(fontWeight = FontWeight.SemiBold), color = if (on) KeypadColors.Bg else KeypadColors.Text)
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(CardShape).background(KeypadColors.Surface1)) {
            l.parent?.let { parent ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { Haptic.tap(view); vm.listPcFiles(parent) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Glyph.ChevronLeft, null, Modifier.size(18.dp), tint = KeypadColors.TextDim)
                    Text(tr("Voltar", "Back"), style = KeypadType.Key, color = KeypadColors.TextDim)
                }
            }
            if (l.items.isEmpty()) Text(tr("Pasta vazia", "Empty folder"), Modifier.padding(14.dp), style = KeypadType.Body, color = KeypadColors.TextMute)
            for (f in l.items) {
                Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(KeypadColors.Line))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(onClickLabel = if (f.dir) tr("Abrir", "Open") else tr("Trazer para o celular", "Bring to the phone")) {
                        Haptic.tap(view)
                        val path = l.path.trimEnd('/') + "/" + f.name
                        if (f.dir) vm.listPcFiles(path)
                        else if (transfers.none { it.incoming && it.name == f.name && it.state == com.sandevsystems.omarchyremote.network.Transfer.State.RUNNING }) {
                            asked[path] = null
                            vm.fetchFromPc(path) { ok -> asked[path] = ok }
                        }
                    }.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(if (f.dir) Glyph.Folder else Glyph.File, null, Modifier.size(20.dp), tint = if (f.dir) KeypadColors.Text else KeypadColors.TextDim)
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = KeypadType.Key, color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!f.dir) Text(sizeText(f.size) + " · " + ago((System.currentTimeMillis() / 1000 - f.mtime).toInt()), style = KeypadType.Caption, color = KeypadColors.TextMute)
                    }
                    val t = if (f.dir) null else transfers.lastOrNull { it.incoming && it.name == f.name && asked.containsKey(l.path.trimEnd('/') + "/" + f.name) }
                    when {
                        t?.state == com.sandevsystems.omarchyremote.network.Transfer.State.RUNNING ->
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = KeypadColors.Text, trackColor = KeypadColors.Line2, strokeWidth = 2.dp)
                        t?.state == com.sandevsystems.omarchyremote.network.Transfer.State.DONE -> Icon(Glyph.Check, tr("No celular", "On the phone"), Modifier.size(18.dp), tint = KeypadColors.Ok)
                        else -> Icon(if (f.dir) Glyph.ChevronRight else Glyph.Download, null, Modifier.size(16.dp), tint = KeypadColors.TextMute)
                    }
                }
            }
        }
    }
}

private fun sizeText(bytes: Long) = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1048576.0)
    else -> String.format("%.1f GB", bytes / 1073741824.0)
}

@Composable
internal fun SectionHeader(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Overline(title, Modifier.weight(1f))
        if (action != null) Box(Modifier.heightIn(min = 40.dp).clickable(onClick = onAction).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text("$action ›", style = KeypadType.Caption, color = KeypadColors.TextDim)
        }
    }
}

@Composable
internal fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClickLabel = title, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = KeypadType.Key.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text)
            Text(subtitle, style = KeypadType.Caption, color = KeypadColors.TextMute)
        }
        Icon(Glyph.ChevronRight, null, Modifier.size(16.dp), tint = KeypadColors.TextMute)
    }
}
