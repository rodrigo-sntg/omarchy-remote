package com.sandevsystems.omarchyremote.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandevsystems.omarchyremote.KeypadViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// The lit colors of the Control Center's toggles, as iOS does (design 6B); the rest follows the theme.
private val BluetoothBlue = Color(0xFF0A84FF)
private val FocusPurple = Color(0xFF5E5CE6)
private val NightOrange = Color(0xFFFF9F0A)
private val AwakeGreen = Color(0xFF30D158)
private val RecordRed = Color(0xFFFF453A)

/**
 * The PC as a Control Center (design 6B): how it is doing, a power menu (6C), the search, its
 * toggles and what is playing in tiles, the volume, the power profile, shortcuts, the look, its
 * windows and the Omarchy menu.
 */
@Composable
fun PcTab(vm: KeypadViewModel, host: String, onOpenSheet: (OmarchyTab) -> Unit, onTerminal: () -> Unit, modifier: Modifier = Modifier) {
    val now by vm.now.collectAsStateWithLifecycle()
    val windows by vm.pcWindows.collectAsStateWithLifecycle()
    val controls by vm.pcControls.collectAsStateWithLifecycle()
    val stats by vm.pcStats.collectAsStateWithLifecycle()
    val hostInfo by vm.hostInfo.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.loadWindows()
        while (true) {
            vm.loadNow()
            vm.loadStats()
            vm.loadControls()
            delay(5_000)
        }
    }
    var filesOpen by rememberSaveable { mutableStateOf(false) }
    if (filesOpen) PcFilesSheet(vm) { filesOpen = false }
    var confirm by remember { mutableStateOf<String?>(null) }
    confirm?.let { action ->
        val restart = action == "reboot"
        ConfirmSheet(
            if (restart) tr("Reiniciar o PC?", "Restart the PC?") else tr("Desligar o PC?", "Shut the PC down?"),
            tr("Os apps abertos são fechados. O Omarchy Remote volta quando o PC ligar.", "Open apps are closed. Omarchy Remote comes back when the PC starts."),
            if (restart) tr("Reiniciar", "Restart") else tr("Desligar", "Shut down"), danger = !restart,
            onConfirm = { vm.pcAct(action); confirm = null }, onDismiss = { confirm = null },
        )
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.sendFiles(it) }
    val view = LocalView.current
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val st = stats
        val up = st?.uptime?.let { it / 3600 }?.let { h -> if (h >= 24) tr(" · ligado há ${h / 24} d", " · up ${h / 24} d") else tr(" · ligado há $h h", " · up $h h") }.orEmpty()
        Box {
            var powerOpen by remember { mutableStateOf(false) }
            LargeTitle("PC", androidx.compose.ui.text.AnnotatedString("$host · Omarchy$up")) {
                Box {
                    Box(
                        Modifier.size(KeypadDimens.MinTouch).clip(CircleShape).background(if (powerOpen) KeypadColors.Text else KeypadColors.Surface1)
                            .clickable(onClickLabel = tr("Energia do PC", "PC power")) { Haptic.tap(view); powerOpen = true }
                            .semantics { contentDescription = tr("Energia do PC", "PC power") },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Glyph.Power, null, Modifier.size(20.dp), tint = if (powerOpen) KeypadColors.Bg else KeypadColors.Text) }
                    PowerMenu(powerOpen, onDismiss = { powerOpen = false }) { action ->
                        powerOpen = false
                        when (action) {
                            "reboot", "shutdown" -> confirm = action
                            else -> vm.pcAct(action)
                        }
                    }
                }
            }
        }
        st?.let {
            val parts = listOfNotNull(
                "CPU ${it.cpu}%",
                tr("RAM ${"%.0f".format(it.memUsed)} de ${"%.0f".format(it.memTotal)} GB", "RAM ${"%.0f".format(it.memUsed)} of ${"%.0f".format(it.memTotal)} GB"),
                it.temp?.let { t -> "$t °C" },
                it.battery?.let { b -> "$b%" + if (it.charging) " ⚡" else "" },
            )
            Text(parts.joinToString("   ·   "), Modifier.padding(start = 2.dp).padding(top = 0.dp), style = GroupType.Sub,
                color = if ((it.temp ?: 0) >= 85 || it.cpu >= 90) KeypadColors.Attention else KeypadColors.TextMute)
        }
        if (hostInfo?.outdated == true) HostUpdateCard { vm.restartHost() }
        // Search: the Omarchy menu, apps and actions.
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(KeypadColors.Surface1)
                .clickable(onClickLabel = tr("Buscar no PC", "Search the PC")) { onOpenSheet(OmarchyTab.MENU) }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Glyph.Search, null, Modifier.size(17.dp), tint = KeypadColors.TextMute)
            Text(tr("Buscar apps e ações no PC", "Search PC apps and actions"), style = GroupType.Title, color = KeypadColors.TextMute, maxLines = 1)
        }
        val c = controls
        // The tiles: toggles in a square, what is playing in another, then awake, capture and record.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val gap = 12.dp
            val cell = (maxWidth - gap * 3) / 4
            val big = cell * 2 + gap
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Box(Modifier.size(big).clip(RoundedCornerShape(24.dp)).background(KeypadColors.Surface1).padding(14.dp)) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(Modifier.fillMaxWidth()) {
                                RoundToggle(Glyph.Bluetooth, "Bluetooth", c?.bluetooth == true, BluetoothBlue) { vm.setControl("bluetooth") }
                                RoundToggle(Glyph.Mic, if (c?.micMuted == true) tr("Mic mudo", "Mic muted") else tr("Microfone", "Microphone"), c?.micMuted == true, RecordRed) { vm.setControl("mic") }
                            }
                            Row(Modifier.fillMaxWidth()) {
                                RoundToggle(Glyph.BellOff, tr("Não perturbe", "Focus"), c?.dnd == true, FocusPurple) { vm.setControl("dnd") }
                                RoundToggle(Glyph.Moon, tr("Luz noturna", "Night light"), c?.nightlight == true, NightOrange) { vm.setControl("nightlight") }
                            }
                        }
                    }
                    MediaTile(vm, now, Modifier.size(big))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    val awake = c?.awake == true
                    Row(
                        Modifier.width(big).height(cell).clip(RoundedCornerShape(24.dp)).background(KeypadColors.Surface1)
                            .clickable(onClickLabel = tr("Acordado", "Awake")) { Haptic.tap(view); vm.setControl("awake") }
                            .semantics { stateDescription = if (awake) tr("ligado", "on") else tr("desligado", "off") }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(if (awake) AwakeGreen else KeypadColors.Surface3), contentAlignment = Alignment.Center) {
                            Icon(Glyph.Coffee, null, Modifier.size(20.dp), tint = if (awake) Color.Black else KeypadColors.Text)
                        }
                        Column {
                            Text(tr("Acordado", "Awake"), style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text, maxLines = 1)
                            Text(if (awake) tr("Não bloqueia", "Won't lock") else tr("Bloqueia sozinho", "Locks by itself"), style = GroupType.Sub, color = KeypadColors.TextDim, maxLines = 1)
                        }
                    }
                    SquareTile(Glyph.Camera, tr("Captura", "Capture"), false, null, Modifier.size(cell)) { vm.printPc() }
                    val recording = c?.recording == true
                    SquareTile(Glyph.Record, if (recording) tr("Gravando", "Recording") else tr("Gravar", "Record"), recording, RecordRed, Modifier.size(cell)) { vm.setControl("record") }
                }
            }
        }
        // The volume: a wide slider (the fill is the level), the output a tap away.
        if (c != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VolumeSlider(c.volume ?: 0, c.muted, { vm.setControl("volume", it) }, { vm.setControl("mute") }, Modifier.weight(1f))
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background(KeypadColors.Surface1)
                    .clickable(onClickLabel = tr("Trocar a saída de som", "Switch the sound output")) { Haptic.tap(view); vm.setControl("output") }
                    .semantics { contentDescription = tr("Trocar a saída de som", "Switch the sound output") }, contentAlignment = Alignment.Center) {
                    Icon(Glyph.Swap, null, Modifier.size(20.dp), tint = KeypadColors.Text)
                }
            }
            c.output?.let { Text(tr("Saída: ", "Output: ") + it, Modifier.padding(start = 16.dp), style = GroupType.Sub, color = KeypadColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (c.powers.size > 1) {
                SectionLabel(tr("Modo de energia", "Power mode"))
                val names = c.powers.map { p ->
                    when (p) {
                        "power-saver" -> tr("Economia", "Saver")
                        "balanced" -> tr("Equilibrado", "Balanced")
                        "performance" -> tr("Desempenho", "Performance")
                        else -> p
                    }
                }
                EvenSegmented(names, c.powers.indexOf(c.power)) { vm.setControl("power", c.powers[it]) }
            }
        }
        SectionLabel(tr("Atalhos", "Shortcuts"))
        Row(Modifier.fillMaxWidth()) {
            RoundShortcut(Glyph.Upload, tr("Enviar ao PC", "Send to PC"), false, Modifier.weight(1f)) { pick.launch(arrayOf("*/*")) }
            RoundShortcut(Glyph.Download, tr("Pegar do PC", "Get from PC"), false, Modifier.weight(1f)) { filesOpen = true }
            RoundShortcut(Glyph.Play, tr("Apresentar", "Present"), vm.presenting, Modifier.weight(1f)) { vm.togglePresenting() }
            RoundShortcut(Glyph.Terminal, "Terminal", false, Modifier.weight(1f), onTerminal)
        }
        SectionLabel(tr("Aparência", "Look"))
        Row(Modifier.fillMaxWidth().height(76.dp).clip(GroupShape).background(KeypadColors.Surface1)) {
            LookCell(Glyph.Image, tr("Fundo", "Wallpaper"), tr("próximo", "next"), Modifier.weight(1f)) { vm.setControl("wallpaper") }
            Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 16.dp).background(KeypadColors.Line))
            LookCell(Glyph.Bar, tr("Barra", "Bar"), null, Modifier.weight(1f)) { vm.setControl("bar") }
            Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 16.dp).background(KeypadColors.Line))
            LookCell(Glyph.Gaps, tr("Espaços", "Gaps"), null, Modifier.weight(1f)) { vm.setControl("gaps") }
        }
        if (now?.update == true) {
            Row(Modifier.fillMaxWidth().clip(GroupShape).background(KeypadColors.Surface1).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Nova versão do Omarchy", "New Omarchy version"), Modifier.weight(1f), style = GroupType.Lead, color = KeypadColors.Text)
                ActionKey(tr("Atualizar", "Update"), { vm.runMenuItem("update.omarchy", tr("Atualização", "Update")) })
            }
        }
        SectionLabel(tr("Janelas abertas", "Open windows"), action = tr("Ver todas", "See all"), onAction = { onOpenSheet(OmarchyTab.WINDOWS) })
        Group {
            if (windows.isEmpty()) Text(tr("Lendo as janelas…", "Reading windows…"), Modifier.padding(16.dp), style = GroupType.Sub, color = KeypadColors.TextMute)
            windows.take(5).forEachIndexed { i, w ->
                if (i > 0) GroupDivider(58.dp)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClickLabel = w.title) { onOpenSheet(OmarchyTab.WINDOWS) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppBadge(w.app, 30.dp)
                    Text(w.title, Modifier.weight(1f), style = GroupType.Title.copy(fontSize = 16.sp), color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("Área ${w.workspace}", "Space ${w.workspace}"), style = GroupType.Sub, color = KeypadColors.TextDim)
                }
            }
        }
        SectionLabel("Omarchy")
        Group {
            GroupRow(tr("Menu do Omarchy", "Omarchy menu"), tr("Apps, temas, captura, sistema", "Apps, themes, capture, system"), { onOpenSheet(OmarchyTab.MENU) })
            GroupDivider()
            GroupRow(tr("Atalhos de teclado", "Keyboard shortcuts"), tr("Toque para usar no PC", "Tap to use on the PC"), { onOpenSheet(OmarchyTab.BINDS) })
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** 6C: the PC's power — lock, suspend, restart, and shut down apart and in red. */
@Composable
private fun PowerMenu(open: Boolean, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    DropdownMenu(open, onDismiss, containerColor = KeypadColors.Surface3, shape = RoundedCornerShape(16.dp), modifier = Modifier.width(250.dp)) {
        val item: @Composable (String, ImageVector, String, Color) -> Unit = { label, icon, action, color ->
            Row(
                Modifier.fillMaxWidth().height(48.dp).clickable(onClickLabel = label) { onAction(action) }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, Modifier.weight(1f), style = GroupType.Title, color = color)
                Icon(icon, null, Modifier.size(20.dp), tint = color)
            }
        }
        item(tr("Bloquear", "Lock"), Glyph.Lock, "lock", KeypadColors.Text)
        Box(Modifier.fillMaxWidth().height(1.dp).background(KeypadColors.Line2))
        item(tr("Suspender", "Suspend"), Glyph.Moon, "suspend", KeypadColors.Text)
        Box(Modifier.fillMaxWidth().height(1.dp).background(KeypadColors.Line2))
        item(tr("Reiniciar", "Restart"), Glyph.Restart, "reboot", KeypadColors.Text)
        Box(Modifier.fillMaxWidth().height(8.dp).background(KeypadColors.Surface1))
        item(tr("Desligar…", "Shut down…"), Glyph.Power, "shutdown", RecordRed)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.RoundToggle(icon: ImageVector, label: String, on: Boolean, lit: Color, onClick: () -> Unit) {
    val view = LocalView.current
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = label) { Haptic.tap(view); onClick() }
            .semantics { stateDescription = if (on) tr("ligado", "on") else tr("desligado", "off") },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(if (on) lit else KeypadColors.Surface3), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (on) Color.White else KeypadColors.Text)
        }
        Text(label, style = GroupType.Small.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = if (on) KeypadColors.Text else KeypadColors.TextDim, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SquareTile(icon: ImageVector, label: String, on: Boolean, lit: Color?, modifier: Modifier, onClick: () -> Unit) {
    val view = LocalView.current
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(KeypadColors.Surface1).clickable(onClickLabel = label) { Haptic.tap(view); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = if (on && lit != null) lit else KeypadColors.Text)
        Text(label, style = GroupType.Small.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = KeypadColors.Text, maxLines = 1)
    }
}

/** What is playing on the PC: in which app, the title, and its controls. */
@Composable
private fun MediaTile(vm: KeypadViewModel, now: com.sandevsystems.omarchyremote.network.NowState?, modifier: Modifier) {
    val playing = now?.takeIf { it.mediaTitle != null || it.mediaArtist != null }
    val player = playing?.mediaPlayer?.substringBefore('.')?.replaceFirstChar { it.uppercase() }
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(KeypadColors.Surface1).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppBadge(player ?: "♪", 36.dp)
            Text(
                if (playing == null) tr("Nada\ntocando", "Nothing\nplaying")
                else (if (playing.playing) tr("Tocando", "Playing") else tr("Pausado", "Paused")) + (player?.let { tr("\nno $it", "\nin $it") } ?: ""),
                style = GroupType.Small.copy(fontSize = 12.sp), color = KeypadColors.TextDim, maxLines = 2,
            )
        }
        Text(playing?.mediaTitle ?: playing?.mediaArtist ?: "", Modifier.weight(1f).padding(top = 8.dp), style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold),
            color = KeypadColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val view = LocalView.current
            Box(Modifier.size(36.dp, 44.dp).clickable(enabled = playing != null, onClickLabel = tr("Anterior", "Previous")) { Haptic.tap(view); vm.media("previous") }, contentAlignment = Alignment.Center) {
                Icon(Glyph.Previous, tr("Anterior", "Previous"), Modifier.size(18.dp), tint = if (playing != null) KeypadColors.Text else KeypadColors.TextMute)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (playing != null) KeypadColors.Text else KeypadColors.Surface3)
                .clickable(enabled = playing != null, onClickLabel = if (playing?.playing == true) tr("Pausar", "Pause") else tr("Tocar", "Play")) { Haptic.tap(view); vm.media("play-pause") },
                contentAlignment = Alignment.Center) {
                Icon(if (playing?.playing == true) Glyph.Pause else Glyph.Play, null, Modifier.size(18.dp), tint = if (playing != null) KeypadColors.Bg else KeypadColors.TextMute)
            }
            Box(Modifier.size(36.dp, 44.dp).clickable(enabled = playing != null, onClickLabel = tr("Próxima", "Next")) { Haptic.tap(view); vm.media("next") }, contentAlignment = Alignment.Center) {
                Icon(Glyph.Next, tr("Próxima", "Next"), Modifier.size(18.dp), tint = if (playing != null) KeypadColors.Text else KeypadColors.TextMute)
            }
        }
    }
}

/** An app as a colored square with its initial (Brave orange, Spotify green…). */
@Composable
private fun AppBadge(app: String, size: androidx.compose.ui.unit.Dp) {
    val name = app.lowercase()
    val color = when {
        "brave" in name -> Color(0xFFFB542B)
        "spotify" in name -> Color(0xFF1DB954)
        "firefox" in name -> Color(0xFFFF7139)
        "chrom" in name -> Color(0xFF4285F4)
        "code" in name -> Color(0xFF2F80ED)
        "whatsapp" in name -> Color(0xFF25D366)
        else -> null
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(color ?: KeypadColors.Surface3), contentAlignment = Alignment.Center) {
        Text(app.take(1).uppercase(), style = GroupType.Lead.copy(fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Bold),
            color = if (color != null) Color.White else KeypadColors.TextDim)
    }
}

/** The volume as iOS draws it: a wide bar filled up to the level; the speaker mutes. */
@Composable
private fun VolumeSlider(level: Int, muted: Boolean, onLevel: (Int) -> Unit, onMute: () -> Unit, modifier: Modifier) {
    var value by remember(level) { mutableFloatStateOf(level / 100f) }
    val view = LocalView.current
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(20.dp)).background(KeypadColors.Surface1)
            .pointerInput(Unit) {
                detectTapGestures { at -> value = (at.x / size.width).coerceIn(0f, 1f); onLevel((value * 100).roundToInt()) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { onLevel((value * 100).roundToInt()) }) { change, drag ->
                    change.consume()
                    value = (value + drag / size.width).coerceIn(0f, 1f)
                }
            }
            .semantics {
                contentDescription = tr("Volume do PC", "PC volume")
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { v -> value = v; onLevel((v * 100).roundToInt()); true }
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value).background(KeypadColors.Text))
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val left = if (value > 0.3f) KeypadColors.Bg else KeypadColors.Text
            val right = if (value > 0.88f) KeypadColors.Bg else KeypadColors.Text
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClickLabel = if (muted) tr("Ligar o som", "Unmute") else tr("Silenciar", "Mute")) { Haptic.tap(view); onMute() },
                contentAlignment = Alignment.Center) {
                Icon(if (muted) Glyph.VolumeOff else Glyph.Volume, null, Modifier.size(20.dp), tint = left)
            }
            Text(if (muted) tr("Mudo", "Muted") else "Volume", Modifier.weight(1f), style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = left)
            Text("${(value * 100).roundToInt()}%", style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = right)
        }
    }
}

/** A segmented control whose parts share the whole width (the power mode). */
@Composable
private fun EvenSegmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val view = LocalView.current
    Row(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(KeypadColors.Surface3).padding(2.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(if (on) KeypadColors.Line2 else Color.Transparent)
                .clickable(onClickLabel = label) { if (!on) { Haptic.tap(view); onSelect(i) } }.semantics { if (on) stateDescription = tr("atual", "current") },
                contentAlignment = Alignment.Center) {
                Text(label, style = GroupType.Sub.copy(fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = KeypadColors.Text, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RoundShortcut(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val view = LocalView.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = label) { Haptic.tap(view); onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(if (on) KeypadColors.Text else KeypadColors.Surface1), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (on) KeypadColors.Bg else KeypadColors.Text)
        }
        Text(label, style = GroupType.Small, color = KeypadColors.Text, maxLines = 1)
    }
}

@Composable
private fun LookCell(icon: ImageVector, label: String, detail: String?, modifier: Modifier, onClick: () -> Unit) {
    val view = LocalView.current
    Column(modifier.fillMaxHeight().clickable(onClickLabel = label) { Haptic.tap(view); onClick() }.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        Icon(icon, null, Modifier.size(20.dp), tint = KeypadColors.Text)
        Row {
            Text(label, style = GroupType.Sub.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = KeypadColors.Text, maxLines = 1)
            if (detail != null) Text(" · $detail", style = GroupType.Sub.copy(fontSize = 14.sp), color = KeypadColors.TextDim, maxLines = 1)
        }
    }
}

/** A confirmation as design 5D draws one: the question, what it means, and big answers — the main one white, or red when it can't be undone. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(title: String, detail: String, action: String, danger: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val view = LocalView.current
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = KeypadColors.Bg, dragHandle = null,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(KeypadColors.Surface1).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, Modifier.padding(start = 4.dp), style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text)
                Text(detail, Modifier.padding(start = 4.dp, bottom = 4.dp), style = GroupType.Sub, color = KeypadColors.TextDim)
                val answer: @Composable (String, Color, Color, () -> Unit) -> Unit = { label, bg, fg, onClick ->
                    Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClickLabel = label) { Haptic.tap(view); onClick() },
                        contentAlignment = Alignment.Center) {
                        Text(label, style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = fg)
                    }
                }
                answer(action, if (danger) RecordRed else KeypadColors.Text, if (danger) Color.White else KeypadColors.Bg, onConfirm)
                answer(tr("Cancelar", "Cancel"), KeypadColors.Surface3, KeypadColors.Text, onDismiss)
            }
        }
    }
}

/**
 * The PC service has new code waiting: amber, as everything that needs the person, with a "?" that
 * says what the service is and that restarting it closes nothing.
 */
@Composable
private fun HostUpdateCard(onRestart: () -> Unit) {
    val view = LocalView.current
    var restarting by remember { mutableStateOf(false) }
    var why by remember { mutableStateOf(false) }
    LaunchedEffect(restarting) { if (restarting) { delay(15_000); restarting = false } }
    val amber = KeypadColors.Attention
    Column(
        Modifier.fillMaxWidth().clip(GroupShape).background(amber.copy(alpha = 0.12f))
            .border(1.dp, amber.copy(alpha = 0.45f), GroupShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(amber), contentAlignment = Alignment.Center) {
                Icon(Glyph.Restart, null, Modifier.size(20.dp), tint = Color.Black)
            }
            Column(Modifier.weight(1f)) {
                Text(tr("Atualização do serviço do PC", "PC service update"), style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text)
                Text(tr("Reinicie para usar as novidades", "Restart to use what's new"), style = GroupType.Sub, color = KeypadColors.TextDim)
            }
            Box {
                Box(
                    Modifier.size(KeypadDimens.MinTouch).clip(CircleShape).clickable(onClickLabel = tr("O que é isso?", "What is this?")) { Haptic.tap(view); why = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(26.dp).clip(CircleShape).border(1.5.dp, KeypadColors.TextDim, CircleShape), contentAlignment = Alignment.Center) {
                        Text("?", style = GroupType.Sub.copy(fontWeight = FontWeight.Bold), color = KeypadColors.TextDim)
                    }
                }
                DropdownMenu(why, { why = false }, containerColor = KeypadColors.Surface3, shape = RoundedCornerShape(16.dp), modifier = Modifier.width(290.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("O que é o serviço do PC?", "What is the PC service?"), style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text)
                        Text(
                            tr(
                                "É o programa que roda no PC e faz o que o celular pede: mouse, teclado, tela, agentes, arquivos. O código dele mudou desde que ele começou, então as funções novas do app só funcionam depois de reiniciá-lo.",
                                "It's the program on the PC that does what the phone asks: mouse, keyboard, screen, agents, files. Its code changed since it started, so the app's new features only work after restarting it.",
                            ),
                            style = GroupType.Sub, color = KeypadColors.TextDim,
                        )
                        Text(
                            tr("Leva uns segundos e não fecha nada: apps, janelas e agentes continuam rodando.", "It takes a few seconds and closes nothing: apps, windows and agents keep running."),
                            style = GroupType.Sub, color = KeypadColors.Text,
                        )
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (restarting) amber.copy(alpha = 0.35f) else amber)
                .clickable(enabled = !restarting, onClickLabel = tr("Reiniciar o serviço", "Restart the service")) { Haptic.tap(view); restarting = true; onRestart() },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (restarting) tr("Reiniciando…", "Restarting…") else tr("Reiniciar o serviço", "Restart the service"),
                style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = Color.Black)
        }
    }
}
