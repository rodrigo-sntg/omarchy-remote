package com.sandevsystems.omarchyremote.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sandevsystems.omarchyremote.ConnectionState
import com.sandevsystems.omarchyremote.input.ModifierKeys
import com.sandevsystems.omarchyremote.input.Shortcut
import com.sandevsystems.omarchyremote.input.TouchPoint
import com.sandevsystems.omarchyremote.input.TouchpadAction
import com.sandevsystems.omarchyremote.input.TouchpadGesture
import com.sandevsystems.omarchyremote.input.Workspaces
import com.sandevsystems.omarchyremote.network.CursorAt
import com.sandevsystems.omarchyremote.network.MonitorInfo
import com.sandevsystems.omarchyremote.network.Workspace
import kotlinx.coroutines.delay

// The base of design v2 (DESIGN.md §3, §4.1, §4.2): status → screens card → shortcuts → trackpad → dock.

val modifierNames = listOf(ModifierKeys.CTRL to "Ctrl", ModifierKeys.ALT to "Alt", ModifierKeys.SUPER to "Super", ModifierKeys.SHIFT to "Shift")

/** Status line: dot · host · transport · chevron (opens Computador) · badges · settings. */
@Composable
fun StatusLine(
    state: ConnectionState,
    network: Boolean,
    capsLock: Boolean,
    modifiers: Int,
    onComputer: () -> Unit,
    onSettings: () -> Unit,
    onReleaseModifiers: () -> Unit,
    modifier: Modifier = Modifier,
    showSettings: Boolean = true,
    // Omarchy's menu on the phone (network only).
    onOmarchy: (() -> Unit)? = null,
    // "3 agentes", or "!2 esperando" when some need the person (drawn in accent).
    agentsSummary: String? = null,
    onAgents: () -> Unit = {},
) {
    val (title, detail) = statusTexts(state, network)
    Row(modifier.fillMaxWidth().height(KeypadDimens.StatusHeight), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(state)
        Row(
            Modifier.padding(start = 10.dp).weight(1f).heightIn(min = KeypadDimens.MinTouch)
                .clickable(onClickLabel = tr("Escolher computador", "Choose computer"), onClick = onComputer),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The name wins the space; the detail is the one that gets cut.
            Text(title, style = KeypadType.HostName, color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 230.dp))
            if (detail != null) Text(detail, style = KeypadType.Mono, color = KeypadColors.TextMute, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            Icon(Glyph.ChevronDown, null, Modifier.size(15.dp), tint = KeypadColors.TextMute)
        }
        if (agentsSummary != null) {
            val waiting = agentsSummary.startsWith("!")
            Box(
                Modifier.padding(start = 6.dp).clip(KeypadShapes.Segment)
                    .background(if (waiting) KeypadColors.Attention.copy(alpha = 0.16f) else KeypadColors.Surface3)
                    .clickable(onClickLabel = tr("Agentes", "Agents"), onClick = onAgents).heightIn(min = 32.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(agentsSummary.removePrefix("!"), style = KeypadType.Caption.copy(fontWeight = KeypadType.Overline.fontWeight),
                    color = if (waiting) KeypadColors.Attention else KeypadColors.TextDim)
            }
        }
        if (capsLock) Badge("CAPS", KeypadColors.Danger)
        for ((mask, name) in modifierNames) {
            if (modifiers and mask != 0) {
                Box(
                    Modifier.padding(start = 6.dp).clip(KeypadShapes.Segment).background(KeypadColors.Accent)
                        .clickable(onClickLabel = tr("Soltar $name", "Release $name"), onClick = onReleaseModifiers).padding(horizontal = 8.dp, vertical = 4.dp),
                ) { Text(name, style = KeypadType.Mono, color = KeypadColors.OnAccent) }
            }
        }
        if (onOmarchy != null) {
            Box(
                Modifier.size(KeypadDimens.MinTouch).clip(KeypadShapes.Macro).clickable(onClickLabel = tr("O PC", "The PC"), onClick = onOmarchy)
                    .semantics { contentDescription = tr("O PC: controles, apps, atalhos e janelas", "The PC: controls, apps, shortcuts and windows") },
                contentAlignment = Alignment.Center,
            ) { Icon(Glyph.Grid, null, Modifier.size(20.dp), tint = KeypadColors.TextDim) }
        }
        if (showSettings) {
            Box(
                Modifier.size(KeypadDimens.MinTouch).clip(KeypadShapes.Macro).clickable(onClickLabel = tr("Ajustes", "Settings"), onClick = onSettings)
                    .semantics { contentDescription = tr("Ajustes", "Settings") },
                contentAlignment = Alignment.Center,
            ) { Icon(Glyph.Gear, null, Modifier.size(21.dp), tint = KeypadColors.TextDim) }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(Modifier.padding(start = 6.dp).border(1.dp, color, KeypadShapes.Segment).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(text, style = KeypadType.Mono.copy(fontSize = KeypadType.PadLabel.fontSize), color = color)
    }
}

@Composable
fun StatusDot(state: ConnectionState, size: Dp = 8.dp) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(1f, 0.3f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a")
    val color = when (state) {
        is ConnectionState.Connected -> KeypadColors.Ok
        is ConnectionState.Connecting -> KeypadColors.Attention
        is ConnectionState.Error -> KeypadColors.Danger
        else -> KeypadColors.Line2
    }
    Canvas(Modifier.size(size + 8.dp).alpha(if (state is ConnectionState.Connecting) alpha else 1f)) {
        val r = size.toPx() / 2
        if (state is ConnectionState.Connected) drawCircle(KeypadColors.Ok.copy(alpha = 0.18f), r + 4.dp.toPx())
        drawCircle(color, r)
    }
}

/** Title and detail of the status line for each connection state (DESIGN §4.2). */
fun statusTexts(state: ConnectionState, network: Boolean): Pair<String, String?> {
    // Connected over the network is the normal case: just the PC's name. Bluetooth says so.
    val transport = if (network) null else "Bluetooth"
    return when (state) {
        ConnectionState.PermissionRequired -> tr("Permissão necessária", "Permission needed") to null
        ConnectionState.BluetoothOff -> tr("Bluetooth desligado", "Bluetooth is off") to null
        ConnectionState.Starting -> tr("Preparando…", "Starting…") to null
        ConnectionState.Ready -> tr("Nenhum PC", "No PC") to tr("toque para escolher", "tap to choose")
        is ConnectionState.Connecting -> state.host.name to tr("conectando…", "connecting…")
        is ConnectionState.Connected -> state.host.name to transport
        is ConnectionState.Disconnected -> tr("Desconectado", "Disconnected") to tr("toque para conectar", "tap to connect")
        is ConnectionState.Error -> tr("Não conectou", "Couldn't connect") to tr("toque para ver", "tap for details")
    }
}

/**
 * Screens card: over the network the cursor map + real workspaces; over Bluetooth the fixed
 * 1…0 row (the PC does not say which is current); without monitor data, the real workspaces only.
 */
@Composable
fun ScreensCard(
    network: Boolean,
    enabled: Boolean,
    monitors: List<MonitorInfo>,
    cursor: CursorAt?,
    workspaces: List<Workspace>,
    onGo: (Int) -> Unit,
    onOpenMonitor: (String?) -> Unit,
    onExtra: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val real = monitors.filter { !it.extra }
    Column(
        modifier.fillMaxWidth().clip(KeypadShapes.Card).background(KeypadColors.Surface2)
            .border(1.dp, KeypadColors.Line, KeypadShapes.Card).padding(10.dp).alpha(if (enabled) 1f else 0.3f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (network && enabled && real.isNotEmpty() && !compact) {
            // The card says what it is and offers its two ways in; the map is only the map.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Overline(tr("Telas", "Screens"), Modifier.weight(1f).padding(start = 4.dp))
                HeaderLink(tr("＋ Celular como tela", "＋ Phone as a screen"), onExtra)
                HeaderLink(tr("Ver PC ›", "View PC ›"), { onOpenMonitor(cursor?.monitor) }, strong = true)
            }
        }
        if (network && enabled && real.isNotEmpty()) {
            CursorMap(real, cursor, onOpenMonitor, onExtra, compact, Modifier.fillMaxWidth().height(if (compact) 92.dp else KeypadDimens.CursorMapHeight - 24.dp))
        }
        WorkspaceStrip(network, enabled, workspaces, onGo)
    }
}

@Composable
private fun HeaderLink(text: String, onClick: () -> Unit, strong: Boolean = false) {
    val view = LocalView.current
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedShape10).clickable(role = Role.Button) { Haptic.tap(view); onClick() }.padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = KeypadType.Caption.copy(fontWeight = KeypadType.Overline.fontWeight), color = if (strong) KeypadColors.Text else KeypadColors.TextDim) }
}

/** Monitors by where they sit, not by connector: "Esquerda", "Direita"… (DP-1 stays for TalkBack). */
fun monitorNames(monitors: List<MonitorInfo>): Map<String, String> {
    val order = monitors.sortedBy { it.x }.map { it.name }
    val names = when (order.size) {
        1 -> listOf(tr("Principal", "Main"))
        2 -> listOf(tr("Esquerda", "Left"), tr("Direita", "Right"))
        3 -> listOf(tr("Esquerda", "Left"), tr("Centro", "Center"), tr("Direita", "Right"))
        else -> order
    }
    return order.zip(names).toMap()
}

@Composable
private fun WorkspaceStrip(network: Boolean, enabled: Boolean, workspaces: List<Workspace>, onGo: (Int) -> Unit) {
    val view = LocalView.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val items: List<Pair<Int, Boolean>> = if (network && workspaces.isNotEmpty()) workspaces.map { it.id to it.focused }
        else Workspaces.numbers.map { it to false }
        for ((id, current) in items) {
            val label = if (id == 10 && (!network || workspaces.isEmpty())) "0" else "$id"
            Box(
                Modifier.size(44.dp, 36.dp).clip(RoundedShape10)
                    .background(if (current) KeypadColors.Accent else KeypadColors.Surface3)
                    .clickable(enabled = enabled, role = Role.Button) { Haptic.tap(view); onGo(id) }
                    .semantics {
                        contentDescription = "Workspace $id"
                        if (current) stateDescription = tr("atual", "current")
                    },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = KeypadType.MonoValue, color = if (current) KeypadColors.OnAccent else KeypadColors.TextDim) }
        }
        if (network && workspaces.isNotEmpty()) {
            val next = (workspaces.maxOf { it.id } + 1).coerceAtMost(99)
            Row(
                Modifier.height(36.dp).clip(RoundedShape10).dashedBorder(KeypadColors.Line2, 10.dp).clickable(enabled = enabled) { Haptic.tap(view); onGo(next) }
                    .semantics { contentDescription = tr("Ir para um workspace novo ($next)", "Go to a new workspace ($next)") }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Glyph.Plus, null, Modifier.size(13.dp), tint = KeypadColors.TextMute)
                Text(tr("Novo", "New"), style = KeypadType.Caption, color = KeypadColors.TextMute)
            }
        }
    }
}

private val RoundedShape10 = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)

/**
 * "Onde está o cursor": the PC's monitors in real proportion, the current workspace of each, and a
 * dot where the cursor is. Tap a monitor → Ver PC of it; dashed slot → Tela extra.
 */
@Composable
private fun CursorMap(
    monitors: List<MonitorInfo>,
    cursor: CursorAt?,
    onOpenMonitor: (String?) -> Unit,
    onExtra: () -> Unit,
    compact: Boolean,
    modifier: Modifier,
) {
    val view = LocalView.current
    Box(modifier.clip(KeypadShapes.Macro).background(KeypadColors.Surface1).border(1.dp, KeypadColors.Line, KeypadShapes.Macro).sunken()) {
        val names = monitorNames(monitors)
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp)) {
            val extraSlot = 0.dp
            val labelSpace = 18.dp
            val areaW = maxWidth - extraSlot
            val areaH = maxHeight - labelSpace
            val left = monitors.minOf { it.x }
            val top = monitors.minOf { it.y }
            val spanW = (monitors.maxOf { it.x + it.width } - left).toFloat()
            val spanH = (monitors.maxOf { it.y + it.height } - top).toFloat()
            val gap = 10.dp
            val scale = minOf((areaW.value - gap.value * (monitors.size - 1)) / spanW, areaH.value / spanH)
            val used = spanW * scale + gap.value * (monitors.size - 1)
            val startX = (areaW.value - used) / 2
            // Horizontal gaps between neighbours keep small monitors readable.
            val order = monitors.sortedBy { it.x }
            for ((i, m) in order.withIndex()) {
                val x = startX + (m.x - left) * scale + gap.value * i
                val y = (m.y - top) * scale + (areaH.value - spanH * scale) / 2
                val w = m.width * scale
                val h = m.height * scale
                val here = cursor?.monitor == m.name
                val shape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp)
                Box(
                    Modifier.offset(x.dp, y.dp).size(w.dp, h.dp).clip(shape)
                        .background(if (here) KeypadColors.AccentTint06 else KeypadColors.Surface2)
                        .border(1.dp, if (here) KeypadColors.AccentBorder60 else KeypadColors.Line2, shape)
                        .clickable { Haptic.tap(view); onOpenMonitor(m.name) }
                        .semantics { contentDescription = "${names[m.name] ?: m.name} (${m.name})" + if (here) tr(", cursor aqui", ", cursor here") else "" },
                ) {
                    if (here && cursor != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            // Kept a dot's radius inside so it is never cut at the edge.
                            val r = 7.dp.toPx()
                            val c = Offset((cursor.x * size.width).coerceIn(r, size.width - r), (cursor.y * size.height).coerceIn(r, size.height - r))
                            drawCircle(KeypadColors.AccentTint16, 12.dp.toPx(), c)
                            drawCircle(KeypadColors.Accent, 6.dp.toPx(), c)
                        }
                    }
                }
                // Names share one baseline under the drawing, whatever the monitors' vertical offsets.
                val labelW = maxOf(w, 88f)
                Text(names[m.name] ?: m.name, Modifier.offset((x + w / 2 - labelW / 2).dp, (areaH.value + 2).dp).width(labelW.dp),
                    style = KeypadType.Caption, color = if (here) KeypadColors.Text else KeypadColors.TextMute, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** "ATALHOS · UM TOQUE" + Editar, then the five macro keys (or [count] of them without header). */
@Composable
fun ShortcutRow(
    shortcuts: List<Shortcut>,
    enabled: Boolean,
    onShortcut: (Shortcut) -> Unit,
    onEdit: (Int?) -> Unit,
    count: Int = shortcuts.size,
    header: Boolean = true,
) {
    Column(Modifier.alpha(if (enabled) 1f else 0.3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (header) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Overline(tr("Atalhos", "Shortcuts"), Modifier.weight(1f))
                Box(
                    Modifier.height(KeypadDimens.MinTouch).clickable(enabled = enabled, onClickLabel = tr("Editar atalhos", "Edit shortcuts")) { onEdit(null) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tr("Editar", "Edit"), Modifier.border(1.dp, KeypadColors.Line, KeypadShapes.Segment).padding(horizontal = 10.dp, vertical = 5.dp),
                        style = KeypadType.Caption, color = KeypadColors.TextDim)
                }
            }
        }
        KeyRow {
            shortcuts.take(count).forEachIndexed { i, shortcut ->
                // A letter alone reads as that letter: a Ctrl shortcut shows its ⌃, like the editor does.
                val glyph = if (shortcut.key.modifiers and ModifierKeys.CTRL != 0 && shortcut.glyph.length == 1) "⌃${shortcut.glyph}" else shortcut.glyph
                MacroKey(glyph, shortcut.caption, { onShortcut(shortcut) }, Modifier.weight(1f), enabled, onLongClick = { onEdit(i) })
            }
        }
    }
}

/** Left | Segurar | Right under the trackpad (or compact, with its own frame, inside layers). */
@Composable
fun ClickBar(dragging: Boolean, enabled: Boolean, onLeft: () -> Unit, onToggleHold: () -> Unit, onRight: () -> Unit, compact: Boolean = false) {
    val view = LocalView.current
    val height = if (compact) KeypadDimens.ClickBarCompact else KeypadDimens.ClickBarHeight
    val frame = if (compact) Modifier.clip(KeypadShapes.Macro).background(KeypadColors.Surface1).border(1.dp, KeypadColors.Line, KeypadShapes.Macro)
    else Modifier.background(KeypadColors.Surface2)
    Row(Modifier.fillMaxWidth().height(height).then(frame), verticalAlignment = Alignment.CenterVertically) {
        ClickZone(tr("Clique", "Click"), Modifier.weight(3f), enabled) { Haptic.tap(view); onLeft() }
        Divider()
        Box(
            Modifier.weight(2f).fillMaxSize().background(if (dragging) KeypadColors.Accent.copy(alpha = 0.14f) else Color.Transparent)
                .clickable(enabled = enabled, role = Role.Switch) { if (dragging) Haptic.off(view) else Haptic.on(view); onToggleHold() }
                .semantics { stateDescription = if (dragging) tr("segurando o botão", "holding the button") else tr("solto", "released") },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                val c = if (dragging) KeypadColors.Accent else KeypadColors.TextDim
                Icon(Glyph.Hand, null, Modifier.size(18.dp), tint = c)
                Text(tr("Segurar", "Hold"), style = KeypadType.KeySmall, color = c)
            }
        }
        Divider()
        ClickZone(tr("Direito", "Right"), Modifier.weight(3f), enabled) { Haptic.tap(view); onRight() }
    }
}

@Composable
private fun ClickZone(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(modifier.fillMaxSize().clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = KeypadType.KeySmall, color = KeypadColors.TextDim)
    }
}

@Composable
private fun Divider() = Box(Modifier.width(1.dp).fillMaxSize(0.62f).background(KeypadColors.Line))

/**
 * The trackpad (sunken, dot texture, "TRACKPAD", "?") with its click bar; when there is no connection
 * its place shows [empty] instead (same frame, dashed), so nothing else moves.
 */
@Composable
fun TrackpadPanel(
    active: Boolean,
    dragging: Boolean,
    naturalScroll: Boolean,
    onAction: (TouchpadAction) -> Unit,
    onHelp: () -> Unit,
    onLeft: () -> Unit,
    onToggleHold: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
    empty: (@Composable ColumnScope.() -> Unit)? = null,
    // The "?" is for the first days; later the tips live in Ajustes.
    showHelp: Boolean = true,
) {
    if (empty != null) {
        Column(
            modifier.fillMaxWidth().clip(KeypadShapes.Trackpad).background(KeypadColors.Surface1)
                .dashedBorder(KeypadColors.Line2, 24.dp).padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.Bottom),
            content = empty,
        )
        return
    }
    Column(modifier.fillMaxWidth().clip(KeypadShapes.Trackpad).background(KeypadColors.Surface1).border(1.dp, KeypadColors.Line, KeypadShapes.Trackpad)) {
        Box(Modifier.weight(1f).fillMaxWidth().sunken().dotGrid().touchpad(active, naturalScroll, onAction)) {
            if (showHelp) Box(
                Modifier.align(Alignment.TopEnd).padding(12.dp).size(KeypadDimens.MinTouch).clip(KeypadShapes.Macro)
                    .background(KeypadColors.Surface3.copy(alpha = 0.85f)).border(1.dp, KeypadColors.Line, KeypadShapes.Macro)
                    .clickable(onClickLabel = tr("Dicas de gestos", "Gesture tips"), onClick = onHelp).semantics { contentDescription = tr("Dicas de gestos", "Gesture tips") },
                contentAlignment = Alignment.Center,
            ) { Icon(Glyph.Help, null, Modifier.size(20.dp), tint = KeypadColors.TextMute) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(KeypadColors.Line))
        ClickBar(dragging, active, onLeft, onToggleHold, onRight)
    }
}

/** Laptop-style touchpad gestures (TouchpadGesture), distances in dp. */
@Composable
private fun Modifier.touchpad(active: Boolean, naturalScroll: Boolean, onAction: (TouchpadAction) -> Unit): Modifier {
    val density = LocalDensity.current.density
    val config = LocalViewConfiguration.current
    val act by rememberUpdatedState(onAction)
    val gesture = remember(density, config) {
        TouchpadGesture(
            touchSlop = config.touchSlop / density, tapTimeoutMs = config.longPressTimeoutMillis,
            doubleTapMs = config.doubleTapTimeoutMillis, scrollStep = 18f, swipeDistance = 90f, pinchEnabled = false,
        )
    }
    gesture.naturalScroll = naturalScroll
    return semantics { contentDescription = "Trackpad" }.pointerInput(active, gesture) {
        if (!active) return@pointerInput
        awaitEachGesture {
            do {
                val event = awaitPointerEvent()
                val points = event.changes.filter { it.pressed }.map { TouchPoint(it.id.value, it.position.x / density, it.position.y / density) }
                event.changes.forEach { it.consume() }
                gesture.onFrame(points, event.changes.maxOf { it.uptimeMillis }).forEach(act)
            } while (points.isNotEmpty())
        }
    }
}

/** Dock: Teclas (weight 1) · Texto · Terminal · Ver PC, each an icon with its name. */
@Composable
fun Dock(network: Boolean, onKeys: () -> Unit, onText: () -> Unit, onViewPc: () -> Unit, onTerminal: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(KeypadDimens.DockHeight), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Pressable(onKeys, Modifier.weight(1f).fillMaxSize(), shape = KeypadShapes.Dock, background = KeypadColors.Surface2, description = tr("Teclado", "Keyboard")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Glyph.Keyboard, null, Modifier.size(20.dp), tint = KeypadColors.Text)
                Text(tr("Teclado", "Keyboard"), style = KeypadType.Key.copy(fontWeight = KeypadType.Overline.fontWeight), color = KeypadColors.Text)
            }
        }
        DockButton(Glyph.TextAa, tr("Texto", "Text"), tr("Escrever texto no PC", "Type text on the PC"), true, onText)
        if (network) DockButton(Glyph.Terminal, "Terminal", tr("Terminal do PC", "The PC's terminal"), true, onTerminal)
        ViewPcButton(network, onViewPc, Modifier.width(KeypadDimens.DockSideButton).fillMaxSize(), label = tr("Ver PC", "View PC"))
    }
}

@Composable
private fun DockButton(icon: ImageVector, label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Pressable(onClick, Modifier.width(KeypadDimens.DockSideButton).fillMaxHeight(), shape = KeypadShapes.Dock, background = KeypadColors.Surface2,
        description = description) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) KeypadColors.Text else KeypadColors.TextMute)
            Text(label, style = KeypadType.MacroCaption.copy(fontSize = KeypadType.Mono.fontSize), color = KeypadColors.TextDim, maxLines = 1)
        }
    }
}

/** Ver PC: accent over the network; over Bluetooth dashed and muted (it explains itself when tapped). */
@Composable
fun ViewPcButton(network: Boolean, onClick: () -> Unit, modifier: Modifier, label: String? = null) {
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val c = if (network) KeypadColors.OnAccent else KeypadColors.TextMute
            Icon(Glyph.ViewPc, null, Modifier.size(20.dp), tint = c)
            if (label != null) Text(label, style = KeypadType.MacroCaption.copy(fontSize = KeypadType.Mono.fontSize, fontWeight = KeypadType.Overline.fontWeight), color = if (network) KeypadColors.OnAccent else KeypadColors.Text)
        }
    }
    if (network) {
        Pressable(onClick, modifier, shape = KeypadShapes.Dock, background = KeypadColors.Accent, border = KeypadColors.Accent, raised = false,
            description = tr("Ver PC", "View PC"), content = content)
    } else {
        Pressable(onClick, modifier.dashedBorder(KeypadColors.Line2, 17.dp), shape = KeypadShapes.Dock, background = Color.Transparent, border = null,
            raised = false, description = tr("Ver PC, só pela rede", "View PC, network only"), content = content)
    }
}

/** Empty state inside the trackpad frame: drawing, title, text, buttons at the bottom (thumb zone). */
@Composable
fun ColumnScope.EmptyContent(
    title: String, text: String?, textColor: Color = KeypadColors.TextDim,
    art: @Composable () -> Unit = { ConnectIllustration() }, buttons: @Composable () -> Unit = {},
) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { art() }
    Column(Modifier.widthIn(max = 300.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = KeypadType.EmptyTitle, color = KeypadColors.Text, textAlign = TextAlign.Center)
        if (text != null) Text(text, Modifier.padding(top = 8.dp), style = KeypadType.Body, color = textColor, textAlign = TextAlign.Center)
    }
    buttons()
}

/** Floating message above the dock (DESIGN §4.9): surface3, icon, gone after 4 s or on tap. */
@Composable
fun MessageBanner(text: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier, error: Boolean = false) {
    if (text == null) return
    // Long enough to read: longer texts and problems stay longer.
    LaunchedEffect(text) {
        delay((if (error) 6_000L else 2_500L) + 55L * text.length)
        onDismiss()
    }
    Row(
        modifier.widthIn(max = 520.dp).clip(KeypadShapes.Macro).background(KeypadColors.Surface3)
            .border(1.dp, if (error) KeypadColors.Danger.copy(alpha = 0.5f) else KeypadColors.Line, KeypadShapes.Macro)
            .clickable(onClickLabel = tr("Fechar aviso", "Dismiss message"), onClick = onDismiss)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).background(if (error) KeypadColors.Danger else KeypadColors.Ok.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) { Text(if (error) "!" else "✓", style = KeypadType.Caption.copy(fontWeight = KeypadType.Overline.fontWeight), color = KeypadColors.Bg) }
        Text(text, style = KeypadType.Body, color = KeypadColors.Text)
    }
}
