package com.sandevsystems.omarchyremote.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandevsystems.omarchyremote.network.AgentsText
import com.sandevsystems.omarchyremote.ConnectionState
import com.sandevsystems.omarchyremote.KeypadViewModel
import com.sandevsystems.omarchyremote.Transport
import com.sandevsystems.omarchyremote.VideoMode
import com.sandevsystems.omarchyremote.bluetooth.BluetoothHidController
import com.sandevsystems.omarchyremote.input.MouseButtons
import com.sandevsystems.omarchyremote.input.TouchpadAction
import kotlinx.coroutines.delay

/** Landscape side column: four buttons (Teclas, Texto, herdr, Ver PC) fit a phone held sideways. */
private val SIDE_BUTTON = 72.dp

private enum class Layer { NONE, KEYS, TEXT, KEYBOARD }
private enum class Sheet { NONE, COMPUTER, SETTINGS, SHORTCUTS, OMARCHY }

/** Main screen of design v2: the base never moves; keys, text and sheets are layers over it. */
@Composable
fun KeypadScreen(vm: KeypadViewModel) {
    // The PC's screen and the terminal show whatever is on the PC (code, passwords typed there):
    // out of screenshots, recordings and the recents preview while they are open.
    val window = androidx.activity.compose.LocalActivity.current?.window
    val secure = vm.secureScreens && (vm.videoMode != VideoMode.NONE || vm.terminalOpen)
    androidx.compose.runtime.SideEffect {
        if (secure) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
    when (vm.videoMode) {
        VideoMode.DISPLAY, VideoMode.SCREEN -> return RemoteScreen(vm)
        VideoMode.NONE -> Unit
    }
    val state by vm.connection.collectAsStateWithLifecycle()
    val capsLock by vm.capsLock.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val monitors by vm.monitorMap.collectAsStateWithLifecycle()
    val cursor by vm.cursorAt.collectAsStateWithLifecycle()
    val connected = state is ConnectionState.Connected
    val network = vm.transport == Transport.NETWORK
    // The tabs are the network's places (screen, agents, PC); over Bluetooth only Controle exists.
    val showTabs = connected && network
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // Following agents needs notifications and no battery optimization: asked only when the person
    // turns it on (never on every launch), after saying why.
    if (vm.askBackgroundAccess) {
        val power = context.getSystemService(PowerManager::class.java)
        val needsNotifications = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val needsBattery = !power.isIgnoringBatteryOptimizations(context.packageName)
        if (!needsNotifications && !needsBattery) {
            vm.askBackgroundAccess = false
        } else {
            AlertDialog(
                onDismissRequest = { vm.askBackgroundAccess = false },
                title = { Text(tr("Avisar quando um agente precisar de você", "Tell you when an agent needs you")) },
                text = {
                    Text(tr("Para isso o Android precisa deixar o app mostrar notificações e não pausá-lo em segundo plano.",
                        "For that, Android has to let the app show notifications and not pause it in the background."))
                },
                confirmButton = {
                    TextButton({
                        vm.askBackgroundAccess = false
                        if (needsNotifications) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        if (needsBattery) context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text(tr("Permitir", "Allow")) }
                },
                dismissButton = { TextButton({ vm.askBackgroundAccess = false }) { Text(tr("Agora não", "Not now")) } },
            )
        }
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var tab by rememberSaveable { mutableStateOf(MainTab.CONTROL) }
    var selectedAgent by rememberSaveable { mutableStateOf<String?>(null) }
    // The PC's Phone menu asked to open something.
    LaunchedEffect(vm.pendingOpen) {
        val what = vm.pendingOpen ?: return@LaunchedEffect
        vm.pendingOpen = null
        val (w, h) = screenSize(context)
        when (what) {
            "screen" -> { vm.closeTerminal(); vm.openScreen(w, h) }
            "extra" -> { vm.closeTerminal(); vm.openDisplay(w, h) }
            "terminal" -> vm.openTerminal()
        }
    }
    // A notification tapped while the terminal is open: back to the base, which opens the agent.
    LaunchedEffect(vm.pendingAgent) { if (vm.pendingAgent != null && vm.terminalOpen) vm.closeTerminal() }
    if (vm.terminalOpen) {
        SideEffect { vm.hardwareKeysToPc = false }  // the terminal takes the keys itself
        TerminalScreen(vm, onAgents = { vm.closeTerminal(); tab = MainTab.AGENTS })
        return
    }
    val agents by vm.agents.collectAsStateWithLifecycle()
    val herdrAvailable by vm.herdrAvailable.collectAsStateWithLifecycle()
    // The base (with its cursor map) is on screen: the PC reads the cursor only meanwhile.
    val baseConnected = state is ConnectionState.Connected
    DisposableEffect(baseConnected) {
        if (baseConnected) vm.watchCursor(true)
        onDispose { vm.watchCursor(false) }
    }

    var layer by rememberSaveable { mutableStateOf(Layer.NONE) }
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    val pcLock by vm.pcLock.collectAsStateWithLifecycle()
    var unlockSetup by rememberSaveable { mutableStateOf(false) }
    if (unlockSetup) UnlockSetupSheet(vm, pcLock) { unlockSetup = false }
    vm.pendingShare?.let { ShareConfirmSheet(vm, it) }
    // A keyboard attached to the phone types on the PC unless a text field could be focused.
    SideEffect { vm.hardwareKeysToPc = connected && sheet == Sheet.NONE && layer != Layer.TEXT && layer != Layer.KEYBOARD && tab == MainTab.CONTROL }
    var omarchyTab by rememberSaveable { mutableStateOf(OmarchyTab.MENU) }
    // A tapped agent notification opens that agent (the widget, "": the list).
    LaunchedEffect(vm.pendingAgent) {
        vm.pendingAgent?.let { id ->
            vm.pendingAgent = null
            selectedAgent = id.ifEmpty { null }
            tab = MainTab.AGENTS
        }
    }
    var editSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    var gestures by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var visibleUntil by rememberSaveable { mutableStateOf(0L) }
    var visibleSeconds by rememberSaveable { mutableStateOf<Int?>(null) }

    // Back closes the top layer before leaving (sheets and dialogs close themselves).
    BackHandler(layer != Layer.NONE) { layer = Layer.NONE }
    LaunchedEffect(connected, vm.hintsSeen) { if (connected && !vm.hintsSeen) gestures = true }
    LaunchedEffect(visibleUntil) {
        while (visibleUntil > System.currentTimeMillis()) {
            visibleSeconds = ((visibleUntil - System.currentTimeMillis()) / 1000).toInt()
            delay(1000)
        }
        visibleSeconds = null
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionDenied = it.values.any { granted -> !granted }
        vm.start()
    }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.start() }
    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_CANCELED) visibleUntil = System.currentTimeMillis() + 120_000
        vm.loadHosts()
    }
    fun openAppSettings() = context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    fun requestPermissions() = permissions.launch(BluetoothHidController.requestedPermissions().toTypedArray())
    fun makeDiscoverable() {
        val canAdvertise = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        if (!canAdvertise) return if (permissionDenied) openAppSettings() else requestPermissions()
        discoverable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120))
    }
    fun openComputer() { vm.loadHosts(); sheet = Sheet.COMPUTER }
    fun openViewPc(monitor: String? = null) {
        if (!network) return vm.showMessage(tr("Ver PC funciona pela rede (Tailscale).", "View PC works over the network (Tailscale)."))
        if (!connected) return vm.showMessage(tr("Conecte ao computador pela rede para ver a tela.", "Connect to the computer over the network to see its screen."))
        screenSize(context).let { (w, h) -> vm.openScreen(w, h, monitor) }
    }
    fun openExtra() = screenSize(context).let { (w, h) -> vm.openDisplay(w, h) }
    // Besides the monitors: the focused window and the phone as a screen (also in Ver PC's "Onde olhar").
    val otherSources = listOf(
        tr("Janela em foco", "Focused window") to { screenSize(context).let { (w, h) -> vm.openFocusedWindow(w, h) } },
        tr("Celular como tela", "Phone as a screen") to ::openExtra,
    )

    val view = LocalView.current
    DisposableEffect(connected) {
        view.keepScreenOn = connected
        onDispose { view.keepScreenOn = false }
    }

    val status = @Composable {
        StatusLine(
            state, network, capsLock, if (layer == Layer.KEYS) 0 else vm.modifiers,
            onComputer = ::openComputer, onSettings = { sheet = Sheet.SETTINGS }, onReleaseModifiers = vm::releaseModifiers,
            agentsSummary = agents.takeIf { network && connected && herdrAvailable && it.isNotEmpty() }?.let { list ->
                val waiting = list.count(AgentsText::needsYou)
                when {
                    waiting > 0 -> "!" + tr("$waiting esperando você", "$waiting waiting for you")
                    list.size == 1 -> tr("1 agente", "1 agent")
                    else -> tr("${list.size} agentes", "${list.size} agents")
                }
            },
            onAgents = { tab = MainTab.AGENTS },
            onOmarchy = if (network && connected) ({ sheet = Sheet.OMARCHY }) else null,
        )
    }
    val screens = @Composable { compact: Boolean ->
        ScreensCard(network, connected, monitors, cursor, workspaces, vm::goToWorkspace, { openViewPc(it) }, ::openExtra, compact = compact)
    }
    val trackpad = @Composable { modifier: Modifier ->
        TrackpadPanel(
            active = connected, dragging = vm.dragging, naturalScroll = vm.naturalScroll,
            onAction = { onTouchpad(vm, it) }, onHelp = { gestures = true },
            onLeft = { vm.click(MouseButtons.LEFT) }, onToggleHold = vm::toggleDrag, onRight = { vm.click(MouseButtons.RIGHT) },
            modifier = modifier, showHelp = !vm.hintsSeen,
            empty = emptyStateFor(
                state, permissionDenied, vm.lastHostAddress != null || (vm.transport == Transport.NETWORK && vm.networkAddress.isNotBlank()),
                onPermit = { if (permissionDenied) openAppSettings() else requestPermissions() },
                onEnableBluetooth = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                onBluetooth = { vm.chooseTransport(Transport.BLUETOOTH); openComputer() },
                onNetwork = { vm.chooseTransport(Transport.NETWORK); openComputer() },
                onRetry = vm::retry,
                onChoose = ::openComputer,
                wake = WakeUi(vm.wakeTargets.isNotEmpty() && vm.transport == Transport.NETWORK,
                    com.sandevsystems.omarchyremote.network.Wake.anyEnabled(vm.wakeTargets), vm.waking, vm::wakePc, vm::cancelWake),
            ),
        )
    }

    Box(Modifier.fillMaxSize().background(KeypadColors.Bg)) {
        if (landscape && layer == Layer.KEYS) {
            LandscapeKeysLayer(
                vm, connected,
                status = {
                    Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusDot(state, 7.dp)
                        Text(statusTexts(state, network).first, style = KeypadType.HostName.copy(fontSize = KeypadType.KeySmall.fontSize), color = KeypadColors.Text)
                        Text("${cursor?.monitor ?: ""}${if (cursor != null) " · " else ""}${"%.1f×".format(vm.sensitivity)}", Modifier.weight(1f),
                            style = KeypadType.Mono, color = KeypadColors.TextMute)
                        Pressable({ openViewPc() }, Modifier.height(26.dp), background = KeypadColors.Surface3, border = KeypadColors.Line, description = tr("Ver PC", "View PC")) {
                            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Glyph.ViewPc, null, Modifier.size(14.dp), tint = KeypadColors.Text)
                                Text(tr("Ver PC", "View PC"), style = KeypadType.MacroCaption.copy(fontSize = KeypadType.Mono.fontSize), color = KeypadColors.Text, maxLines = 1)
                            }
                        }
                    }
                },
                trackpad = trackpad,
                onText = { layer = Layer.TEXT },
                modifier = Modifier.safeDrawingPadding(),
            )
        } else if (landscape && showTabs && tab != MainTab.CONTROL) {
            // Landscape: the same places, with the tabs as a rail at the side.
            Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                TabBar(tab, agents.count(AgentsText::needsYou), vertical = true, onSelect = { tab = it; layer = Layer.NONE })
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (tab) {
                        MainTab.AGENTS -> AgentsTab(vm, selectedAgent, { selectedAgent = it }, { vm.openTerminal() })
                        MainTab.PC -> PcTab(vm, (state as? ConnectionState.Connected)?.host?.name ?: "PC", { omarchyTab = it; sheet = Sheet.OMARCHY }, { vm.openTerminal() })
                        MainTab.CONTROL -> Unit
                    }
                }
            }
        } else if (landscape) {
            Row(Modifier.fillMaxSize().safeDrawingPadding().padding(end = 14.dp, top = 12.dp, bottom = 14.dp, start = if (showTabs) 0.dp else 14.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                if (showTabs) TabBar(tab, agents.count(AgentsText::needsYou), vertical = true, onSelect = { tab = it; layer = Layer.NONE })
                Column(Modifier.width(270.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PcPill(state, network, ::openComputer)
                        Spacer(Modifier.weight(1f))
                        RoundIconButton(Glyph.Gear, tr("Ajustes", "Settings")) { sheet = Sheet.SETTINGS }
                    }
                    if (connected && network) {
                        LiveThumbs(vm, monitors)
                        MonitorStrip(monitors, cursor, vm.thumbs, otherSources) { openViewPc(it) }
                        WorkspacePills(workspaces, vm::goToWorkspace)
                    }
                    if (connected) ShortcutChips(vm.shortcutSlots, { vm.pressShortcut(it.key) }, { editSlot = it; sheet = Sheet.SHORTCUTS }, onEnter = { vm.typeKey(0x28) })
                }
                trackpad(Modifier.weight(1f).fillMaxHeight())
                if (connected) Column(Modifier.width(74.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically)) {
                    SideButton(tr("Teclado", "Keyboard"), Glyph.Keyboard) { layer = Layer.KEYBOARD }
                    if (network) ViewPcButton(true, { openViewPc() }, Modifier.fillMaxWidth().height(SIDE_BUTTON), label = tr("Ver PC", "View PC"))
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                    when (if (showTabs) tab else MainTab.CONTROL) {
                        MainTab.CONTROL -> Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PcPill(state, network, ::openComputer)
                                Spacer(Modifier.weight(1f))
                                if (connected && network) WorkspacePills(workspaces.take(6), vm::goToWorkspace, Modifier.widthIn(max = 170.dp))
                                RoundIconButton(Glyph.Gear, tr("Ajustes", "Settings")) { sheet = Sheet.SETTINGS }
                            }
                            if (connected && network) {
                                LiveThumbs(vm, monitors)
                                MonitorStrip(monitors, cursor, vm.thumbs, otherSources) { openViewPc(it) }
                            }
                            val lock = pcLock
                            if (connected && network && lock?.locked == true) PcLockedCard(vm, lock) { unlockSetup = true }
                            trackpad(Modifier.weight(1f))
                            if (connected) {
                                ShortcutChips(vm.shortcutSlots, { vm.pressShortcut(it.key) }, { editSlot = it; sheet = Sheet.SHORTCUTS }, onEnter = { vm.typeKey(0x28) })
                                KeyboardButton { layer = Layer.KEYBOARD }
                            }
                            Spacer(Modifier.height(if (showTabs) 4.dp else 12.dp).then(if (showTabs) Modifier else Modifier.navigationBarsPadding()))
                        }
                        MainTab.AGENTS -> AgentsTab(vm, selectedAgent, { selectedAgent = it }, { vm.openTerminal() })
                        MainTab.PC -> PcTab(vm, (state as? ConnectionState.Connected)?.host?.name ?: "PC", { omarchyTab = it; sheet = Sheet.OMARCHY }, { vm.openTerminal() })
                    }
                }
                // An open agent is a conversation: the whole screen, like Messages (design 5C).
                val inAgent = tab == MainTab.AGENTS && agents.any { it.id == selectedAgent }
                if (showTabs && !inAgent) {
                    val waiting = agents.count(AgentsText::needsYou)
                    TabBar(tab, waiting, vertical = false, onSelect = { tab = it; layer = Layer.NONE })
                }
            }
        }

        // Layers rise over the base with a scrim; the base underneath does not move.
        if (layer != Layer.NONE && layer != Layer.KEYBOARD && !(landscape && layer == Layer.KEYS)) Scrim { layer = Layer.NONE }
        Box(Modifier.align(Alignment.BottomCenter).safeDrawingPadding()) {
            AnimatedVisibility(
                visible = layer == Layer.KEYS && !landscape,
                enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it },
                exit = slideOutVertically(tween(180)) { it },
            ) { KeysLayer(vm, connected, onClose = { layer = Layer.NONE }) }
            if (layer == Layer.TEXT) TextLayer(vm, capsLock, connected, onClose = { layer = Layer.NONE })
            if (layer == Layer.KEYBOARD) KeyboardPanel(vm, onClose = { layer = Layer.NONE })
        }
        Column(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(start = 14.dp, end = 14.dp, bottom = 84.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TransferBanner(vm)
            MessageBanner(vm.message, vm::dismissMessage, error = vm.messageIsError)
        }
    }

    when (sheet) {
        Sheet.COMPUTER -> ComputerSheet(vm, state, visibleSeconds, ::makeDiscoverable) { sheet = Sheet.NONE }
        Sheet.SETTINGS -> SettingsSheet(vm, onUnlockSetup = { sheet = Sheet.NONE; unlockSetup = true }, onEditShortcuts = { editSlot = null; sheet = Sheet.SHORTCUTS }, onGestures = { sheet = Sheet.NONE; gestures = true }) { sheet = Sheet.NONE }
        Sheet.SHORTCUTS -> ShortcutEditorSheet(vm, editSlot) { sheet = Sheet.NONE }
        Sheet.OMARCHY -> OmarchySheet(vm, onShowOnPc = { sheet = Sheet.NONE; openViewPc() }, onAgents = { sheet = Sheet.NONE; tab = MainTab.AGENTS },
            onDismiss = { sheet = Sheet.NONE }, initialTab = omarchyTab, showControls = false)
        Sheet.NONE -> Unit
    }
    if (gestures) GesturesDialog(touchpad = true) { gestures = false; vm.markHintsSeen() }
}

@Composable
private fun SideButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth().height(SIDE_BUTTON), shape = KeypadShapes.Card.copy(all = androidx.compose.foundation.shape.CornerSize(18.dp)),
        background = KeypadColors.Surface2, description = label) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, Modifier.size(21.dp), tint = KeypadColors.Text)
            Text(label, style = KeypadType.MacroCaption.copy(fontSize = KeypadType.Mono.fontSize, fontWeight = KeypadType.Overline.fontWeight), color = KeypadColors.Text)
        }
    }
}

/** What the trackpad frame shows for each connection state (DESIGN §4.2); null = the live trackpad. */
private fun emptyStateFor(
    state: ConnectionState,
    permissionDenied: Boolean,
    hasLastHost: Boolean,
    onPermit: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onBluetooth: () -> Unit,
    onNetwork: () -> Unit,
    onRetry: () -> Unit,
    onChoose: () -> Unit,
    wake: WakeUi = WakeUi(),
): (@Composable ColumnScope.() -> Unit)? = when {
    state is ConnectionState.Connected -> null
    wake.waking == KeypadViewModel.Waking.WAKING -> {
        {
            EmptyContent(tr("Acordando o PC…", "Waking the PC…"), tr("Pode levar até um minuto. A tela dele aparece aqui quando ele voltar.",
                "It can take up to a minute. Its screen shows up here when it's back."), art = { WakingArt() }) {
                ActionButton(tr("Cancelar", "Cancel"), wake.onCancel, Modifier.fillMaxWidth(), primary = false, height = KeypadDimens.DockHeight)
            }
        }
    }
    wake.canWake && wake.waking == KeypadViewModel.Waking.FAILED && (state is ConnectionState.Disconnected || state is ConnectionState.Error) -> {
        {
            EmptyContent(tr("O PC não acordou", "The PC didn't wake up"),
                if (wake.enabled) tr("O celular precisa estar no Wi-Fi da mesma casa que o PC, e o PC ligado na tomada.",
                    "The phone has to be on the same home Wi-Fi as the PC, and the PC plugged in.")
                else tr("Ative o Wake-on-LAN no PC: rode omarchy-remote setup no terminal dele.",
                    "Turn on Wake-on-LAN on the PC: run omarchy-remote setup in its terminal."), KeypadColors.Attention) {
                TwoButtons(tr("Tentar de novo", "Try again"), wake.onWake, tr("Trocar de PC", "Switch PC"), onChoose, Glyph.Power)
            }
        }
    }
    wake.canWake && (state is ConnectionState.Disconnected || state is ConnectionState.Error) -> {
        {
            EmptyContent(tr("O PC não respondeu", "The PC didn't answer"), tr("Ele pode estar suspenso ou desligado.", "It may be asleep or off.")) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TwoButtons(tr("Ligar o PC", "Wake the PC"), wake.onWake, tr("Tentar de novo", "Try again"), onRetry, Glyph.Power)
                    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = tr("Trocar de PC", "Switch PC"), onClick = onChoose),
                        contentAlignment = Alignment.Center) {
                        Text(tr("Trocar de PC", "Switch PC"), style = KeypadType.Body, color = KeypadColors.TextDim)
                    }
                }
            }
        }
    }
    else -> emptyStateOf(state, permissionDenied, hasLastHost, onPermit, onEnableBluetooth, onBluetooth, onNetwork, onRetry, onChoose)
}

/** What "Ligar o PC" needs from the screen. */
data class WakeUi(
    val canWake: Boolean = false,
    val enabled: Boolean = false,
    val waking: KeypadViewModel.Waking = KeypadViewModel.Waking.IDLE,
    val onWake: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

/** A power ring that breathes while the PC wakes. */
@Composable
private fun WakingArt() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "wake")
    val pulse by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing)), label = "pulse")
    val accent = KeypadColors.Accent
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(accent.copy(alpha = 0.35f * (1 - pulse)), radius = r * (0.45f + 0.55f * pulse),
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            drawCircle(accent.copy(alpha = 0.12f), radius = r * 0.42f)
        }
        Icon(Glyph.Power, null, Modifier.size(34.dp), tint = accent)
    }
}

private fun emptyStateOf(
    state: ConnectionState,
    permissionDenied: Boolean,
    hasLastHost: Boolean,
    onPermit: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onBluetooth: () -> Unit,
    onNetwork: () -> Unit,
    onRetry: () -> Unit,
    onChoose: () -> Unit,
): (@Composable ColumnScope.() -> Unit)? = when (state) {
    is ConnectionState.Connected -> null
    ConnectionState.PermissionRequired -> {
        {
            EmptyContent(
                tr("Permita Dispositivos próximos", "Allow Nearby devices"),
                if (permissionDenied) tr("A permissão foi negada. Conceda nas configurações do app.", "Permission was denied. Grant it in the app settings.") else tr("É o que deixa o celular virar teclado e mouse Bluetooth.", "It's what lets the phone become a Bluetooth keyboard and mouse."),
            ) {
                TwoButtons(if (permissionDenied) tr("Abrir configurações", "Open settings") else tr("Permitir", "Allow"), onPermit,
                    tr("Usar pela rede", "Use the network"), onNetwork)
            }
        }
    }
    ConnectionState.BluetoothOff -> { { EmptyContent(tr("Bluetooth desligado", "Bluetooth is off"), tr("Ligue o Bluetooth, ou conecte pela rede.", "Turn on Bluetooth, or connect over the network.")) { TwoButtons(tr("Ligar Bluetooth", "Turn on Bluetooth"), onEnableBluetooth, tr("Rede", "Network"), onNetwork) } } }
    ConnectionState.Starting, is ConnectionState.Connecting -> { { Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { ConnectIllustration() } } }
    ConnectionState.Ready -> {
        {
            EmptyContent(tr("Escolha um PC", "Choose a PC"), tr("Pela rede você também vê a tela do PC e os agentes. Pelo Bluetooth, só teclado e mouse.",
                "Over the network you also see the PC's screen and agents. Over Bluetooth, just keyboard and mouse.")) {
                TwoButtons(tr("Rede", "Network"), onNetwork, "Bluetooth", onBluetooth, Glyph.Wifi, Glyph.Bluetooth)
            }
        }
    }
    is ConnectionState.Disconnected -> {
        {
            EmptyContent(tr("Desconectado", "Disconnected"), state.reason) {
                if (hasLastHost) TwoButtons(tr("Reconectar", "Reconnect"), onRetry, tr("Trocar de PC", "Switch PC"), onChoose) else ActionButton(tr("Escolher um PC", "Choose a PC"), onChoose, Modifier.fillMaxWidth())
            }
        }
    }
    is ConnectionState.Error -> { { EmptyContent(tr("Não conectou", "Couldn't connect"), state.message, KeypadColors.Danger) { TwoButtons(tr("Tentar de novo", "Try again"), onRetry, tr("Trocar de PC", "Switch PC"), onChoose) } } }
}

@Composable
private fun TwoButtons(
    primary: String, onPrimary: () -> Unit, secondary: String, onSecondary: () -> Unit,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector? = null, secondaryIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        ActionButton(primary, onPrimary, Modifier.weight(1f), icon = primaryIcon, height = KeypadDimens.DockHeight)
        ActionButton(secondary, onSecondary, Modifier.weight(1f), primary = false, icon = secondaryIcon, height = KeypadDimens.DockHeight)
    }
}

/** Base touchpad: pointer over Bluetooth or the network control session. */
private fun onTouchpad(vm: KeypadViewModel, action: TouchpadAction) {
    when (action) {
        is TouchpadAction.Move -> vm.move(action.dx, action.dy)
        is TouchpadAction.Click -> when (action.button) {
            1 -> if (vm.tapToClick) vm.click(MouseButtons.LEFT)
            2 -> vm.click(MouseButtons.RIGHT)
            else -> vm.click(MouseButtons.MIDDLE)
        }
        TouchpadAction.DragStart -> vm.setDrag(true)
        TouchpadAction.DragEnd -> vm.setDrag(false)
        is TouchpadAction.Scroll -> vm.scrollSteps(action.vertical)
        is TouchpadAction.WorkspaceStep -> vm.stepWorkspace(action.direction)
        is TouchpadAction.Zoom, is TouchpadAction.Pan -> Unit
    }
}

/** Physical screen size in pixels, independent of the current window and orientation. */
@Suppress("DEPRECATION")
fun screenSize(context: Context): Pair<Int, Int> {
    val windowManager = context.getSystemService(WindowManager::class.java)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
    }
}
