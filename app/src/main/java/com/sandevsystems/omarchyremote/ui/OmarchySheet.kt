package com.sandevsystems.omarchyremote.ui

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandevsystems.omarchyremote.network.AgentsText
import com.sandevsystems.omarchyremote.KeypadViewModel
import androidx.compose.foundation.layout.ColumnScope
import com.sandevsystems.omarchyremote.network.Agent
import com.sandevsystems.omarchyremote.network.Bind
import com.sandevsystems.omarchyremote.network.NowState
import com.sandevsystems.omarchyremote.network.LauncherApp
import com.sandevsystems.omarchyremote.network.PcWindow
import com.sandevsystems.omarchyremote.network.MenuItem

/**
 * Omarchy's Super+Space menu and app launcher on the phone (docs/PLANO-V2.md §7): the same tree and
 * glyphs, a search over items and apps; a tap runs it on the PC. "No PC" opens the real menu there
 * and shows the PC's screen, to follow it or drive it from the phone.
 */
@Composable
fun OmarchySheet(
    vm: KeypadViewModel, onShowOnPc: () -> Unit, onAgents: () -> Unit, onDismiss: () -> Unit,
    // From the PC tab: straight to Menu, Atalhos or Janelas, without the Controles tab (the tab is it).
    initialTab: OmarchyTab = OmarchyTab.NOW, showControls: Boolean = true,
) {
    val menu by vm.omarchyMenu.collectAsStateWithLifecycle()
    val binds by vm.binds.collectAsStateWithLifecycle()
    val windows by vm.pcWindows.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val now by vm.now.collectAsStateWithLifecycle()
    val agents by vm.agents.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(tab) {
        when (tab) {
            OmarchyTab.NOW -> while (true) {  // at a glance: refreshed while on screen
                vm.loadNow()
                kotlinx.coroutines.delay(5_000)
            }
            OmarchyTab.MENU -> vm.loadOmarchyMenu()
            OmarchyTab.BINDS -> vm.loadBinds()
            OmarchyTab.WINDOWS -> vm.loadWindows()
        }
    }
    val context = LocalContext.current
    // Omarchy's menu glyphs are Nerd Font icons: the same font the terminal bundles.
    val nerd = remember { FontFamily(Font("term/JetBrainsMonoNerdFont-Regular.ttf", context.assets)) }
    val path = menu?.path(route).orEmpty()
    AppSheet(
        if (tab == OmarchyTab.MENU) path.lastOrNull()?.label ?: (if (showControls) "PC" else "Omarchy") else if (showControls) "PC" else "Omarchy",
        onDismiss,
        tall = true,
        subtitle = when {
            tab == OmarchyTab.NOW -> null
            tab == OmarchyTab.BINDS -> tr("Os atalhos do Omarchy: toque para usar no PC", "Omarchy's shortcuts: tap to use on the PC")
            tab == OmarchyTab.WINDOWS -> tr("Janelas abertas no PC", "Windows open on the PC")
            path.isEmpty() -> tr("O menu do Omarchy (Super + Espaço)", "Omarchy's menu (Super + Space)")
            else -> (listOf("Omarchy") + path.dropLast(1).map { it.label }).joinToString(" › ")
        },
    ) {
        Segmented(listOfNotNull(if (showControls) OmarchyTab.NOW to tr("Controles", "Controls") else null, OmarchyTab.MENU to "Menu", OmarchyTab.BINDS to tr("Atalhos", "Shortcuts"), OmarchyTab.WINDOWS to tr("Janelas", "Windows")), tab, { tab = it; query = "" },
            Modifier.fillMaxWidth())
        if (tab == OmarchyTab.NOW) {
            NowCards(vm, now, agents, onAgents)
            return@AppSheet
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == OmarchyTab.MENU && route.isNotEmpty() && query.isEmpty()) {
                Pressable({ route = route.substringBeforeLast('.', "") }, Modifier.size(KeypadDimens.MinTouch), background = KeypadColors.Surface3,
                    description = tr("Voltar", "Back")) { Icon(Glyph.ArrowLeft, null, Modifier.size(19.dp), tint = KeypadColors.Text) }
            }
            Row(
                Modifier.weight(1f).heightIn(min = KeypadDimens.MinTouch).clip(KeypadShapes.Field).background(KeypadColors.Surface1)
                    .border(1.dp, KeypadColors.Line, KeypadShapes.Field).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Glyph.Search, null, Modifier.size(17.dp), tint = KeypadColors.TextMute)
                BasicTextField(
                    query, { query = it }, Modifier.weight(1f).semantics { contentDescription = tr("Buscar no menu e nos apps", "Search the menu and apps") },
                    singleLine = true, textStyle = KeypadType.Body.copy(color = KeypadColors.Text), cursorBrush = SolidColor(KeypadColors.Accent),
                    decorationBox = { field ->
                        if (query.isEmpty()) Text(if (tab == OmarchyTab.MENU) tr("Buscar apps e ações", "Search apps and actions") else if (tab == OmarchyTab.BINDS) tr("Buscar atalhos", "Search shortcuts") else tr("Buscar janelas", "Search windows"),
                            style = KeypadType.Body, color = KeypadColors.TextMute)
                        field()
                    },
                )
            }
            if (tab == OmarchyTab.MENU) Pressable({ vm.showMenuOnPc(if (route == "apps") "apps" else route); onShowOnPc() }, Modifier.heightIn(min = KeypadDimens.MinTouch),
                background = KeypadColors.Surface3, description = tr("Abrir este menu no PC e ver a tela", "Open this menu on the PC and view the screen")) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Glyph.ViewPc, null, Modifier.size(17.dp), tint = KeypadColors.Text)
                    Text(tr("Abrir no PC", "Open on PC"), style = KeypadType.KeySmall, color = KeypadColors.Text)
                }
            }
        }
        val current = menu
        when {
            tab == OmarchyTab.BINDS -> {
                val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val shown = binds.filter { b -> words.all { (b.description + " " + b.keys).lowercase().contains(it) } }
                if (binds.isEmpty()) Waiting(tr("Lendo os atalhos…", "Reading shortcuts…"), tab) { vm.loadBinds() }
                shown.forEach { bind -> BindRow(bind) { vm.runBind(bind) } }
            }
            tab == OmarchyTab.WINDOWS -> {
                val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val shown = windows.filter { w -> words.all { (w.title + " " + w.app).lowercase().contains(it) } }
                if (windows.isEmpty()) Waiting(tr("Lendo as janelas…", "Reading windows…"), tab) { vm.loadWindows() }
                shown.groupBy { it.workspace }.forEach { (workspace, list) ->
                    Overline(if (workspace.startsWith("special")) tr("Escondidas (scratchpad)", "Hidden (scratchpad)") else "Workspace $workspace")
                    list.forEach { window -> WindowRow(window, workspaces.map { it.id }.filter { it > 0 }, vm) }
                }
            }
            current == null -> Waiting(tr("Lendo o menu do Omarchy…", "Reading the Omarchy menu…"), tab) { vm.loadOmarchyMenu() }
            query.isNotBlank() -> {
                val results = current.search(query)
                if (results.items.isEmpty() && results.apps.isEmpty()) Text(tr("Nada encontrado", "Nothing found"), style = KeypadType.Body, color = KeypadColors.TextMute)
                results.apps.take(8).forEach { app -> AppRow(app) { vm.runMenuItem("app:${app.id}", app.name) } }
                results.items.take(30).forEach { item -> ItemRow(item, nerd) { tap(item, current::open, vm) { route = it; query = "" } } }
            }
            route == "apps" -> current.apps.forEach { app -> AppRow(app) { vm.runMenuItem("app:${app.id}", app.name) } }
            else -> current.children(route).forEach { item -> ItemRow(item, nerd) { tap(item, current::open, vm) { route = it } } }
        }
    }
}

enum class OmarchyTab { NOW, MENU, BINDS, WINDOWS }

/** Loading that does not go on forever: after a few seconds, say so and offer to try again. */
@Composable
private fun Waiting(text: String, key: Any, retry: () -> Unit) {
    var late by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key, late) {
        if (!late) {
            kotlinx.coroutines.delay(6_000)
            late = true
        }
    }
    if (!late) {
        Text(text, style = KeypadType.Body, color = KeypadColors.TextMute)
    } else {
        Text(tr("O PC não respondeu.", "The PC didn't respond."), style = KeypadType.Body, color = KeypadColors.TextDim)
        ActionKey(tr("Tentar de novo", "Try again"), { late = false; retry() })
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().clip(KeypadShapes.Card).background(KeypadColors.Surface1).border(1.dp, KeypadColors.Line, KeypadShapes.Card).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
)

/** "Agora": agents, media, reminder and updates at a glance, each with its one action. */
@Composable
private fun NowCards(vm: KeypadViewModel, now: NowState?, agents: List<Agent>, onAgents: () -> Unit) {
    val waiting = agents.count(AgentsText::needsYou)
    PcCard(vm)
    if (agents.isNotEmpty()) Card {
        Overline(tr("Agentes do herdr", "herdr agents"), color = if (waiting > 0) KeypadColors.Accent else KeypadColors.TextMute)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (waiting > 0) tr("$waiting esperando você · ${agents.size} no total", "$waiting waiting for you · ${agents.size} in total") else tr("${agents.size} agentes, nenhum esperando", "${agents.size} agents, none waiting"),
                Modifier.weight(1f), style = KeypadType.Key, color = KeypadColors.Text,
            )
            ActionKey(tr("Ver", "View"), onAgents, on = waiting > 0)
        }
    }
    val media = now?.takeIf { it.mediaTitle != null || it.mediaArtist != null }
    if (media != null) Card {
        Overline(tr("Tocando no PC", "Playing on the PC"))
        Text(media.mediaTitle.orEmpty(), style = KeypadType.Key, color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!media.mediaArtist.isNullOrEmpty()) Text(media.mediaArtist, style = KeypadType.Caption, color = KeypadColors.TextDim, maxLines = 1)
        KeyRow {
            Key(tr("Anterior", "Previous"), { vm.media("previous") }, Modifier.weight(1f), height = 46.dp, icon = Glyph.Previous)
            Key(if (media.playing) tr("Pausar", "Pause") else tr("Tocar", "Play"), { vm.media("play-pause") }, Modifier.weight(1.4f), height = 46.dp,
                icon = if (media.playing) Glyph.Pause else Glyph.Play, on = true)
            Key(tr("Próxima", "Next"), { vm.media("next") }, Modifier.weight(1f), height = 46.dp, icon = Glyph.Next)
        }
    }
    if (now?.reminder != null) Card {
        Overline(tr("Lembrete", "Reminder"))
        Text(now.reminder, style = KeypadType.Key, color = KeypadColors.Text)
    }
    if (now?.update == true) Card {
        Overline(tr("Atualização do Omarchy", "Omarchy update"), color = KeypadColors.Accent)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Uma nova versão está disponível", "A new version is available"), Modifier.weight(1f), style = KeypadType.Body, color = KeypadColors.Text)
            ActionKey(tr("Atualizar no PC", "Update on PC"), { vm.runMenuItem("update.omarchy", tr("Atualização", "Update")) })
        }
    }
}

/** The PC itself: lock, suspend (a second tap confirms), presentation mode for the volume buttons. */
@Composable
private fun PcCard(vm: KeypadViewModel) = Card {
    Overline(tr("Este PC", "This PC"))
    var confirmSuspend by remember { mutableStateOf(false) }
    LaunchedEffect(confirmSuspend) {
        if (confirmSuspend) {
            delay(3_000)
            confirmSuspend = false
        }
    }
    KeyRow {
        Key(tr("Bloquear", "Lock"), { vm.pcAct("lock") }, Modifier.weight(1f), height = 44.dp, style = KeypadType.KeySmall)
        Key(if (confirmSuspend) tr("Confirmar", "Confirm") else tr("Suspender", "Suspend"), {
            if (confirmSuspend) vm.pcAct("suspend") else confirmSuspend = true
        }, Modifier.weight(1f), height = 44.dp, style = KeypadType.KeySmall, on = confirmSuspend,
            description = if (confirmSuspend) tr("Toque de novo para suspender o PC", "Tap again to suspend the PC") else tr("Suspender o PC", "Suspend the PC"))
        Key(tr("Apresentação", "Present"), vm::togglePresenting, Modifier.weight(1.2f), height = 44.dp, style = KeypadType.KeySmall, on = vm.presenting,
            description = tr("Modo apresentação: os botões de volume trocam os slides", "Presentation mode: the volume buttons change slides"))
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.sendFiles(uris) }
    KeyRow {
        Key(tr("Print do PC", "PC screenshot"), { vm.printPc() }, Modifier.weight(1f), height = 44.dp, style = KeypadType.KeySmall,
            description = tr("Print de todos os monitores, salvo em Imagens", "Screenshot of all monitors, saved to Pictures"))
        Key(tr("Enviar arquivo", "Send file"), { pick.launch(arrayOf("*/*")) }, Modifier.weight(1f), height = 44.dp, style = KeypadType.KeySmall,
            description = tr("Escolher arquivos do celular para a pasta Downloads do PC", "Pick files from the phone for the PC's Downloads folder"))
    }
    Text(
        if (vm.presenting) tr("Apresentando: volume − avança o slide, volume + volta.", "Presenting: volume − goes to the next slide, volume + goes back.")
        else if (vm.volumeKeysToPc) tr("Os botões de volume do celular controlam o som do PC.", "The phone's volume buttons control the PC's sound.") else tr("Suspender derruba a conexão até o PC acordar.", "Suspending drops the connection until the PC wakes up."),
        style = KeypadType.Caption, color = KeypadColors.TextMute,
    )
}

@Composable
private fun BindRow(bind: Bind, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(KeypadShapes.Macro).background(KeypadColors.Surface1)
            .clickable(onClickLabel = bind.description, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(bind.description, Modifier.weight(1f), style = KeypadType.Key, color = KeypadColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(bind.keys, Modifier.clip(KeypadShapes.Segment).background(KeypadColors.Surface3).padding(horizontal = 8.dp, vertical = 4.dp),
            style = KeypadType.Mono, color = KeypadColors.TextDim, maxLines = 1)
    }
}

/** A window: tap focuses it on the PC; the chevron opens its actions. */
@Composable
private fun WindowRow(window: PcWindow, workspaces: List<Int>, vm: KeypadViewModel) {
    var open by rememberSaveable(window.address) { mutableStateOf(false) }
    // Closing a window on the PC can lose work: a second tap confirms.
    var confirmClose by remember(window.address) { mutableStateOf(false) }
    LaunchedEffect(confirmClose) {
        if (confirmClose) {
            kotlinx.coroutines.delay(3_000)
            confirmClose = false
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(KeypadShapes.Macro).background(KeypadColors.Surface1)
            .border(1.dp, if (window.focused) KeypadColors.AccentBorder60 else KeypadColors.Line, KeypadShapes.Macro),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClickLabel = tr("Focar no PC", "Focus on PC")) { vm.windowAct(window, "focus") }
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(28.dp).clip(KeypadShapes.Segment).background(KeypadColors.Surface3), contentAlignment = Alignment.Center) {
                Text(window.app.take(1).uppercase(), style = KeypadType.KeySmall, color = if (window.focused) KeypadColors.Accent else KeypadColors.Text)
            }
            Column(Modifier.weight(1f)) {
                Text(window.title, style = KeypadType.Key, color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(window.app, tr("flutuante", "floating").takeIf { window.floating }, tr("tela cheia", "fullscreen").takeIf { window.fullscreen }).joinToString(" · "),
                    style = KeypadType.Caption, color = KeypadColors.TextMute, maxLines = 1)
            }
            Pressable({ open = !open }, Modifier.size(KeypadDimens.MinTouch), background = KeypadColors.Surface1, border = null, raised = false,
                description = if (open) tr("Fechar ações", "Close actions") else tr("Ações da janela", "Window actions")) {
                Icon(if (open) Glyph.ChevronDown else Glyph.ChevronRight, null, Modifier.size(16.dp), tint = KeypadColors.TextMute)
            }
        }
        if (open) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyRow {
                    Key(if (confirmClose) tr("Confirmar", "Confirm") else tr("Fechar", "Close"), {
                        if (confirmClose) vm.windowAct(window, "close") else confirmClose = true
                    }, Modifier.weight(1f), height = 44.dp, style = KeypadType.KeySmall, on = confirmClose)
                    Key(if (window.floating) tr("Encaixar", "Tile") else tr("Flutuar", "Float"), { vm.windowAct(window, "float") }, Modifier.weight(1f), height = 40.dp, style = KeypadType.KeySmall)
                    Key(tr("Tela cheia", "Fullscreen"), { vm.windowAct(window, "fullscreen") }, Modifier.weight(1f), height = 40.dp, style = KeypadType.KeySmall, on = window.fullscreen)
                }
                if (workspaces.isNotEmpty()) {
                    Text(tr("Mover para", "Move to"), style = KeypadType.Caption, color = KeypadColors.TextMute)
                    KeyRow {
                        (workspaces.take(8)).forEach { ws ->
                            Key("$ws", { vm.windowAct(window, "workspace", ws) }, Modifier.weight(1f), height = 38.dp, style = KeypadType.MonoValue,
                                on = window.workspace == "$ws")
                        }
                    }
                }
            }
        }
    }
}

private fun tap(item: MenuItem, open: (MenuItem) -> String, vm: KeypadViewModel, go: (String) -> Unit) {
    if (item.kind == "action") vm.runMenuItem(item.id, item.label) else go(open(item))
}

@Composable
private fun ItemRow(item: MenuItem, nerd: FontFamily, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(KeypadShapes.Macro).background(KeypadColors.Surface1)
            .clickable(onClickLabel = item.label, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(item.icon, Modifier.width(24.dp), style = TextStyle(fontFamily = nerd, fontSize = 18.sp, textAlign = TextAlign.Center), color = KeypadColors.TextDim)
        Column(Modifier.weight(1f)) {
            Text(item.label, style = KeypadType.Key, color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.description.isNotEmpty()) {
                Text(item.description, style = KeypadType.Caption, color = KeypadColors.TextMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            item.checked -> Text("✓", style = KeypadType.MonoValue, color = KeypadColors.Accent)
            item.kind != "action" -> Icon(Glyph.ChevronRight, null, Modifier.size(16.dp), tint = KeypadColors.TextMute)
        }
    }
}

@Composable
private fun AppRow(app: LauncherApp, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(KeypadShapes.Macro).background(KeypadColors.Surface1)
            .clickable(onClickLabel = tr("Abrir ${app.name} no PC", "Open ${app.name} on the PC"), onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).clip(KeypadShapes.Segment).background(KeypadColors.Surface3), contentAlignment = Alignment.Center) {
            Text(app.name.take(1).uppercase(), style = KeypadType.KeySmall, color = KeypadColors.Text)
        }
        Text(app.name, Modifier.weight(1f), style = KeypadType.Key, color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
