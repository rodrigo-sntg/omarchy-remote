package com.sandevsystems.omarchyremote.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandevsystems.omarchyremote.KeypadViewModel
import com.sandevsystems.omarchyremote.network.Agent
import com.sandevsystems.omarchyremote.network.AgentConversation
import com.sandevsystems.omarchyremote.network.AgentConversation.Choice
import com.sandevsystems.omarchyremote.network.AgentConversation.Did
import com.sandevsystems.omarchyremote.network.AgentConversation.Said
import com.sandevsystems.omarchyremote.network.AgentConversation.You
import com.sandevsystems.omarchyremote.network.AgentScreen
import com.sandevsystems.omarchyremote.network.AgentsText
import com.sandevsystems.omarchyremote.network.ChatBlock
import com.sandevsystems.omarchyremote.network.ChatItem
import com.sandevsystems.omarchyremote.network.ChatState
import com.sandevsystems.omarchyremote.network.SlashCommand
import com.sandevsystems.omarchyremote.network.TuiPicker
import com.sandevsystems.omarchyremote.network.contextUsed
import com.sandevsystems.omarchyremote.network.quickActions
import com.sandevsystems.omarchyremote.network.separatorBefore
import com.sandevsystems.omarchyremote.network.timeLabel
import com.sandevsystems.omarchyremote.network.ansiRuns
import com.sandevsystems.omarchyremote.network.slashMatches
import com.sandevsystems.omarchyremote.network.slashQuery
import com.sandevsystems.omarchyremote.network.UsageProvider
import com.sandevsystems.omarchyremote.network.UsageState
import com.sandevsystems.omarchyremote.network.UsageText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The agents (design 4B): the plans' limits, then who needs you (answer right there), who is
 * running and who is idle. An agent opens as a conversation (5C/5D) in the same tab.
 */
@Composable
fun AgentsTab(vm: KeypadViewModel, selected: String?, onSelect: (String?) -> Unit, onTerminal: () -> Unit, modifier: Modifier = Modifier) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    val available by vm.herdrAvailable.collectAsStateWithLifecycle()
    var usageOpen by rememberSaveable { mutableStateOf(false) }
    var newOpen by rememberSaveable { mutableStateOf(false) }
    val (usage, now) = rememberUsage(vm)
    // An agent started from here opens as soon as it is ready.
    LaunchedEffect(vm.startedAgent) {
        vm.startedAgent?.let { newOpen = false; vm.startedAgent = null; onSelect(it) }
    }
    if (newOpen) NewAgentSheet(vm) { newOpen = false }
    val agent = agents.firstOrNull { it.id == selected }
    if (agent != null) {
        BackHandler { onSelect(null) }
        AgentDetail(vm, agent, usage, onBack = { onSelect(null) }, onTerminal = {
            vm.focusAgent(agent.id)
            onTerminal()
        }, modifier)
        return
    }
    // What the agents that matter now show, read every few seconds.
    val active = agents.filter { it.status != "idle" && it.status != "unknown" }.map { it.id }
    LaunchedEffect(active) {
        while (active.isNotEmpty()) {
            for (id in active) {
                vm.readAgentExcerpt(id)
                delay(400)
            }
            delay(3_500)
        }
    }
    val waiting = agents.filter { it.status == "blocked" || it.status == "done" }
    // An agent waiting on its subagents is working too.
    val working = agents.filter { it.status == "working" || (it.subagents > 0 && it.status != "blocked" && it.status != "done") }
    val idle = agents.filter { it !in waiting && it !in working }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val subtitle = buildAnnotatedString {
            when {
                !available -> append(tr("O herdr não está rodando no PC", "herdr isn't running on the PC"))
                agents.isEmpty() -> append(tr("Nenhum agente no herdr", "No agents in herdr"))
                else -> {
                    val parts = mutableListOf<String>()
                    if (waiting.isNotEmpty()) withStyle(SpanStyle(color = KeypadColors.Attention)) {
                        append(tr("${waiting.size} precisa de você", "${waiting.size} need you"))
                    }
                    if (working.isNotEmpty()) parts += tr("${working.size} rodando", "${working.size} running")
                    if (idle.isNotEmpty()) parts += tr("${idle.size} parados", "${idle.size} idle").let { if (idle.size == 1) tr("1 parado", "1 idle") else it }
                    if (parts.isNotEmpty()) append((if (waiting.isNotEmpty()) " · " else "") + parts.joinToString(" · "))
                }
            }
        }
        LargeTitle(tr("Agentes", "Agents"), subtitle) {
            CircleButton(Glyph.Plus, tr("Novo agente", "New agent"), { newOpen = true })
        }
        LimitCards(usage, now) { usageOpen = true }
        if (agents.isEmpty()) {
            Text(if (available) tr("Abra o Claude, o Codex ou outro agente no terminal e ele aparece aqui.", "Start Claude, Codex or another agent in the terminal and it shows up here.")
                else tr("O herdr organiza os agentes no PC. Abra o terminal para começar.", "herdr runs the agents on the PC. Open the terminal to start."),
                Modifier.padding(top = 8.dp), style = GroupType.Lead, color = KeypadColors.TextDim)
            ActionKey(tr("Abrir o terminal", "Open the terminal"), onTerminal)
        }
        if (waiting.isNotEmpty()) {
            SectionLabel(tr("Precisa de você", "Needs you"), KeypadColors.Attention)
            for (a in waiting) NeedsYouCard(vm, a) { onSelect(a.id) }
        }
        if (working.isNotEmpty()) {
            SectionLabel(tr("Rodando", "Running"))
            Group {
                working.forEachIndexed { i, a ->
                    if (i > 0) GroupDivider()
                    val subs = if (a.subagents > 0) subagentCount(a.subagents) + " · " else ""
                    GroupRow(agentTitle(a), subs + activity(vm.agentTexts[a.id], a), { onSelect(a.id) }, trailing = { Spinner() })
                }
            }
        }
        if (idle.isNotEmpty()) {
            SectionLabel(tr("Parados", "Idle"))
            Group {
                idle.forEachIndexed { i, a ->
                    if (i > 0) GroupDivider()
                    val spent = usage?.let { exhausted(it, a.kind) } == true
                    GroupRow(agentTitle(a), origin(a), { onSelect(a.id) }, minHeight = 56.dp, trailing = if (spent) {
                        { Text(tr("Sem limite", "Out of limit"), style = GroupType.Sub, color = KeypadColors.Danger) }
                    } else null)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (usageOpen) UsageSheet(vm) { usageOpen = false }
}

/**
 * A new agent from the phone: Claude or Codex, the project (herdr's open ones first), the task.
 * It starts in its own tab on the PC and opens here once ready.
 */
@Composable
private fun NewAgentSheet(vm: KeypadViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadProjects() }
    val projects by vm.projects.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf("claude") }
    var query by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var task by rememberSaveable { mutableStateOf("") }
    val view = LocalView.current
    AppSheet(tr("Novo agente", "New agent"), onDismiss, subtitle = tr("Começa numa aba nova do herdr, no projeto", "Starts in a new herdr tab, in the project"), tall = true) {
        Segmented(listOf("Claude", "Codex"), if (kind == "claude") 0 else 1, tr("Agente", "Agent"), { kind = if (it == 0) "claude" else "codex" })
        SectionLabel(tr("Projeto", "Project"))
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(KeypadColors.Surface1).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Glyph.Search, null, Modifier.size(16.dp), tint = KeypadColors.TextMute)
            BasicTextField(query, { query = it.take(80) }, Modifier.weight(1f).semantics { contentDescription = tr("Buscar projeto", "Search project") },
                textStyle = GroupType.Lead.copy(color = KeypadColors.Text), cursorBrush = SolidColor(KeypadColors.Text), singleLine = true,
                decorationBox = { f -> if (query.isEmpty()) Text(tr("Buscar projeto", "Search project"), style = GroupType.Lead, color = KeypadColors.TextMute); f() })
        }
        val shown = projects.filter { query.isBlank() || query.lowercase() in it.name.lowercase() || query.lowercase() in it.path.lowercase() }.take(12)
        Column(Modifier.fillMaxWidth().clip(GroupShape).background(KeypadColors.Surface1)) {
            if (projects.isEmpty()) Text(tr("Lendo os projetos…", "Reading projects…"), Modifier.padding(16.dp), style = GroupType.Sub, color = KeypadColors.TextMute)
            shown.forEachIndexed { i, p ->
                if (i > 0) GroupDivider()
                val on = p.path == chosen
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 54.dp).background(if (on) KeypadColors.Surface3 else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(onClickLabel = p.name) { Haptic.tap(view); chosen = p.path }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = GroupType.Title.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = KeypadColors.Text, maxLines = 1)
                        Text(p.path.replace(Regex("^/home/[^/]+"), "~") + (if (p.workspace != null) tr(" · aberto no herdr", " · open in herdr") else ""),
                            style = GroupType.Small, color = KeypadColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (on) Icon(Glyph.Check, null, Modifier.size(18.dp), tint = KeypadColors.Ok)
                }
            }
        }
        SectionLabel(tr("Tarefa (opcional)", "Task (optional)"))
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(KeypadColors.Surface1).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicTextField(task, { task = it.take(3000) }, Modifier.weight(1f).semantics { contentDescription = tr("Tarefa para o agente", "Task for the agent") },
                textStyle = GroupType.Lead.copy(color = KeypadColors.Text), cursorBrush = SolidColor(KeypadColors.Text), maxLines = 6,
                decorationBox = { f -> if (task.isEmpty()) Text(tr("O que ele deve fazer", "What it should do"), style = GroupType.Lead, color = KeypadColors.TextMute); f() })
            val dictate = rememberDictation({ vm.showMessage(tr("Este celular não tem reconhecimento de voz.", "This phone doesn't have speech recognition.")) }) { spoken ->
                task = if (task.isBlank()) spoken else task.trimEnd() + " " + spoken
            }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = tr("Ditar", "Dictate")) { dictate() }, contentAlignment = Alignment.Center) {
                Icon(Glyph.Mic, tr("Ditar", "Dictate"), Modifier.size(18.dp), tint = KeypadColors.TextDim)
            }
        }
        val name = if (kind == "claude") "Claude" else "Codex"
        if (vm.startingAgent) {
            Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Spinner()
                Text(tr("Iniciando o $name… pode levar meio minuto", "Starting $name… it can take half a minute"), style = GroupType.Sub, color = KeypadColors.TextDim)
            }
        } else {
            ActionKey(tr("Começar o $name", "Start $name"), { chosen?.let { vm.startAgent(it, kind, task.trim()) } }, enabled = chosen != null)
        }
    }
}

internal fun subagentCount(n: Int) = if (n == 1) tr("1 subagente", "1 subagent") else tr("$n subagentes", "$n subagents")

/** "agora", "há 3 min", "há 2 h". */
internal fun ago(seconds: Int) = when {
    seconds < 60 -> tr("agora", "now")
    seconds < 3600 -> tr("há ${seconds / 60} min", "${seconds / 60} min ago")
    seconds < 86_400 -> tr("há ${seconds / 3600} h", "${seconds / 3600} h ago")
    else -> tr("há ${seconds / 86_400} d", "${seconds / 86_400} d ago")
}

internal fun agentTitle(a: Agent) = a.title.ifBlank { AgentsText.kindName(a.kind) }

/** "Claude · omarchy-remote": who, and in which project. */
internal fun origin(a: Agent) = listOf(AgentsText.kindName(a.kind), a.cwd.trimEnd('/').substringAfterLast('/')).filter { it.isNotBlank() }.joinToString(" · ")

/** The plan behind this kind of agent has a limit at 100%: it cannot go on. */
private fun exhausted(usage: UsageState, kind: String) =
    usage.providers.any { p -> p.name.lowercase().startsWith(kind.lowercase()) && p.metrics.any { it.kind != "model" && it.percent >= 100 } }

/** What a running agent is doing: its last action, or how long it has been working. */
private fun activity(raw: String?, a: Agent): String {
    val chat = raw?.let { AgentConversation.parse(it) }
    val last = chat?.items?.lastOrNull()
    val time = chat?.footer?.substringBefore(" · ")?.takeIf { it.startsWith(tr("Trabalhando", "Working")) }
    return when {
        last is Did -> listOfNotNull(doing(last), time?.substringAfter("… ")).joinToString(" · ")
        time != null -> time
        else -> origin(a)
    }
}

// ---------------------------------------------------------------- limits

/** Each plan in a card (design 4B): the 5-hour window and the week as bars, and what comes next. */
@Composable
private fun LimitCards(state: UsageState?, now: Instant, onOpen: () -> Unit) {
    val s = state ?: return
    if (!s.available || s.providers.isEmpty()) return
    SectionLabel(tr("Limites", "Limits"), action = tr("Detalhes", "Details"), onAction = onOpen)
    for (row in s.providers.chunked(2)) {
        // Side by side, the same height (a longer line in one card must not make it taller alone).
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (p in row) LimitCard(p, now, onOpen, Modifier.weight(1f).fillMaxHeight())
            if (row.size == 1 && s.providers.size > 1) Spacer(Modifier.weight(1f))
        }
    }
}

private fun limitColor(percent: Int) = when {
    percent >= 100 -> KeypadColors.Danger
    percent >= 70 -> KeypadColors.Warn
    else -> KeypadColors.Text
}

@Composable
private fun LimitCard(p: UsageProvider, now: Instant, onOpen: () -> Unit, modifier: Modifier) {
    val view = LocalView.current
    val metrics = p.metrics.filter { it.kind != "model" }.take(2)
    Column(
        modifier.clip(GroupShape).background(KeypadColors.Surface1).clickable(onClickLabel = tr("Uso das IAs", "AI usage")) { Haptic.tap(view); onOpen() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(p.name, style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text, maxLines = 1)
            if (p.plan.isNotBlank()) Text(p.plan, style = GroupType.Sub, color = KeypadColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        for (m in metrics) {
            Row(Modifier.semantics { contentDescription = tr("${UsageText.label(m)}: ${m.percent} por cento", "${UsageText.label(m)}: ${m.percent} percent") },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(UsageText.label(m), Modifier.width(48.dp), style = GroupType.Small, color = KeypadColors.TextDim, maxLines = 1)
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(KeypadColors.Surface3)) {
                    Box(Modifier.fillMaxWidth(m.percent.coerceIn(0, 100) / 100f).fillMaxHeight().background(limitColor(m.percent)))
                }
                Text("${m.percent}%", Modifier.width(36.dp), style = GroupType.Small, color = if (m.percent >= 70) limitColor(m.percent) else KeypadColors.Text,
                    maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        val spent = metrics.firstOrNull { it.percent >= 100 }
        val week = metrics.firstOrNull { it.kind == "week" } ?: metrics.lastOrNull()
        val line = when {
            p.error != null && metrics.isEmpty() -> p.error
            spent != null -> tr("Esgotado", "Used up") + (spent.resetAt?.let { " · " + UsageText.reset(it, now).removePrefix(tr("volta em ", "resets in ")) } ?: "")
            week?.resetAt != null -> "${UsageText.label(week)} · ${UsageText.reset(week.resetAt, now)}"
            else -> null
        }
        if (line != null) Text(line, style = GroupType.Small, color = if (spent != null) KeypadColors.Danger else KeypadColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- the list's cards

/** An agent waiting on you: what it asks, answered right on the card; or that it finished, and its last words. */
@Composable
private fun NeedsYouCard(vm: KeypadViewModel, agent: Agent, onOpen: () -> Unit) {
    val view = LocalView.current
    val raw = vm.agentTexts[agent.id]
    val chat = remember(raw, I18n.lang) { raw?.let { AgentConversation.parse(it) } }
    val blocked = agent.status == "blocked"
    Column(
        Modifier.fillMaxWidth().clip(GroupShape).background(KeypadColors.Surface1)
            .clickable(onClickLabel = tr("Abrir agente", "Open agent")) { Haptic.tap(view); onOpen() }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(agentTitle(agent), style = GroupType.Title.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(origin(agent) + " · " + (if (blocked) tr("pede permissão", "asks permission") else tr("terminou", "finished")) +
                (if (agent.subagents > 0) " · " + subagentCount(agent.subagents) else ""),
                style = GroupType.Sub, color = KeypadColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (blocked) {
            val ask = chat?.ask
            val what = ask?.let { a -> a.detail.lineSequence().firstOrNull()?.ifBlank { null } ?: a.title.ifBlank { null } }
            if (what != null) {
                Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).clip(RoundedCornerShape(10.dp)).background(KeypadColors.Surface3).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isCommand(ask)) Text("$", style = KeypadType.Mono.copy(fontSize = 13.sp), color = KeypadColors.TextDim)
                    Text(what, style = KeypadType.Mono.copy(fontSize = 13.sp), color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Without a readable request, Claude Code's usual three numbered answers.
            val options = ask?.options ?: listOf(
                AgentConversation.Option("1", "Yes", Choice.YES), AgentConversation.Option("2", "Yes, always", Choice.ALWAYS),
                AgentConversation.Option("3", "No", Choice.NO),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (o in options) {
                    AnswerButton(shortLabel(o), o.choice == Choice.YES, Modifier.weight(1f).height(44.dp), RoundedCornerShape(12.dp)) {
                        vm.agentKeys(agent.id, listOf(o.key))
                    }
                }
            }
        } else {
            val last = chat?.items?.lastOrNull { it is Said } as? Said
            val words = last?.text ?: raw?.let { AgentScreen.clean(it).body.lines().filter { l -> l.isNotBlank() }.takeLast(2).joinToString(" ") { l -> l.trim() } }
            if (!words.isNullOrBlank()) Text(words, style = GroupType.Lead, color = KeypadColors.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AnswerButton(label: String, primary: Boolean, modifier: Modifier, shape: RoundedCornerShape, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        modifier.clip(shape).background(if (primary) KeypadColors.Text else KeypadColors.Surface3).clickable(onClickLabel = label) { Haptic.tap(view); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, Modifier.padding(horizontal = 8.dp), style = GroupType.Lead.copy(fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium),
            color = if (primary) KeypadColors.Bg else KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A running agent's mark: an arc going round. */
@Composable
private fun Spinner() {
    val turn by rememberInfiniteTransition(label = "spinner").animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    val track = KeypadColors.Surface3
    val arc = KeypadColors.Text
    Canvas(Modifier.size(18.dp).rotate(turn).semantics { contentDescription = tr("Rodando", "Running") }) {
        val stroke = 2.dp.toPx()
        drawCircle(track, size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
        drawArc(arc, -90f, 90f, false, Offset(stroke / 2, stroke / 2), androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------- words for Claude Code's

private fun isCommand(ask: AgentConversation.Ask) = ask.title.contains("bash", true) || ask.title.contains("command", true)

private fun acceptsEdits(o: AgentConversation.Option) = "accept edits" in o.label.lowercase()

private fun dontAskAgain(o: AgentConversation.Option) = o.label.lowercase().let { "don't ask" in it || "always" in it }

internal fun shortLabel(o: AgentConversation.Option) = when (o.choice) {
    Choice.YES -> tr("Permitir", "Allow")
    Choice.ALWAYS -> if (acceptsEdits(o)) tr("Aceitar edições", "Accept edits") else tr("Sempre", "Always")
    Choice.NO -> tr("Negar", "Deny")
    Choice.OTHER -> o.label
}

internal fun longLabel(o: AgentConversation.Option) = when (o.choice) {
    Choice.YES -> tr("Sim", "Yes")
    // Claude Code words its second "yes" in several ways; the ones we know are translated, the rest kept.
    Choice.ALWAYS -> when {
        acceptsEdits(o) -> tr("Sim, e aceitar edições nesta sessão", "Yes, and accept edits this session")
        dontAskAgain(o) -> tr("Sim, e não perguntar de novo", "Yes, and don't ask again")
        else -> o.label.substringBefore(" (")
    }
    Choice.NO -> tr("Não, dizer o que fazer", "No, and say what to do")
    Choice.OTHER -> o.label
}

/** The request box's name ("Bash command", "Edit file") in the app's language. */
private fun askTitle(title: String) = when (title.lowercase()) {
    "bash command" -> tr("Comando", "Command")
    "edit file" -> tr("Editar arquivo", "Edit file")
    "create file" -> tr("Criar arquivo", "Create file")
    "read file" -> tr("Ler arquivo", "Read file")
    "fetch" -> tr("Abrir página", "Fetch")
    else -> title
}

private fun question(ask: AgentConversation.Ask): String {
    val q = ask.question.lowercase()
    return when {
        isCommand(ask) && q.startsWith("do you want to proceed") -> tr("Quer executar este comando?", "Run this command?")
        q.startsWith("do you want to make this edit") -> tr("Quer fazer esta edição?", "Make this edit?")
        q.startsWith("do you want to create") -> tr("Quer criar este arquivo?", "Create this file?")
        q.startsWith("do you want to proceed") -> tr("Pode continuar?", "Proceed?")
        else -> ask.question
    }
}

private val summaryWords: List<Pair<Regex, (Int) -> String>> = listOf(
    Regex("^read (\\d+) files?$", RegexOption.IGNORE_CASE) to { n -> if (n == 1) tr("leu 1 arquivo", "read 1 file") else tr("leu $n arquivos", "read $n files") },
    Regex("^ran (\\d+) (?:shell |bash )?commands?$", RegexOption.IGNORE_CASE) to { n -> if (n == 1) tr("rodou 1 comando", "ran 1 command") else tr("rodou $n comandos", "ran $n commands") },
    Regex("^searched for (\\d+) patterns?$", RegexOption.IGNORE_CASE) to { n -> if (n == 1) tr("buscou 1 padrão", "searched 1 pattern") else tr("buscou $n padrões", "searched $n patterns") },
    Regex("^(?:edited|updated) (\\d+) files?$", RegexOption.IGNORE_CASE) to { n -> if (n == 1) tr("editou 1 arquivo", "edited 1 file") else tr("editou $n arquivos", "edited $n files") },
    Regex("^(?:wrote|created) (\\d+) files?$", RegexOption.IGNORE_CASE) to { n -> if (n == 1) tr("criou 1 arquivo", "wrote 1 file") else tr("criou $n arquivos", "wrote $n files") },
    Regex("^listed (\\d+) (?:directories|directory|paths?)$", RegexOption.IGNORE_CASE) to { n -> if (n == 1) tr("listou 1 pasta", "listed 1 folder") else tr("listou $n pastas", "listed $n folders") },
    Regex("^fetched (\\d+) (?:urls?|pages?)$", RegexOption.IGNORE_CASE) to { n -> if (n == 1) tr("abriu 1 página", "fetched 1 page") else tr("abriu $n páginas", "fetched $n pages") },
)

/** Claude Code's own summary of actions ("Read 1 file, ran 3 shell commands") in the app's language. */
private val background = Regex("^Background command \"(.*)\" (completed|failed).*$")

private fun translateSummary(text: String): String {
    background.find(text)?.let { m ->
        val done = m.groupValues[2] == "completed"
        return (if (done) tr("Comando em segundo plano terminou: ", "Background command finished: ") else tr("Comando em segundo plano falhou: ", "Background command failed: ")) + m.groupValues[1]
    }
    Regex("^Running (\\d+) (?:shell )?commands?(…|\\.\\.\\.)?$").find(text)?.let { m ->
        val n = m.groupValues[1].toInt()
        return (if (n == 1) tr("Rodando 1 comando", "Running 1 command") else tr("Rodando $n comandos", "Running $n commands")) + "…"
    }
    if (text.startsWith("Running ")) return tr("Rodando ", "Running ") + text.removePrefix("Running ")
    val parts = text.split(", ").map { part ->
        summaryWords.firstNotNullOfOrNull { (re, say) -> re.find(part.trim())?.let { say(it.groupValues[1].toInt()) } } ?: return text
    }
    return parts.joinToString(", ").replaceFirstChar { it.uppercase() }
}

private fun fileName(d: Did) = d.target.substringBefore(',').trim().trimEnd('/').substringAfterLast('/')

/** What a running agent is doing right now, short: "Rodando um comando", "Editando App.kt". */
internal fun doing(d: Did): String = when {
    d.tool == "Bash" || (d.tool.isEmpty() && d.target.startsWith("Running")) -> tr("Rodando um comando", "Running a command")
    d.tool == "Read" -> tr("Lendo ", "Reading ") + fileName(d)
    d.tool in setOf("Edit", "Update", "MultiEdit", "Write") -> tr("Editando ", "Editing ") + fileName(d)
    d.tool in setOf("Grep", "Search", "Glob") -> tr("Buscando", "Searching")
    else -> describe(d)
}

/** One action, the way a person would say it. */
internal fun describe(d: Did): String {
    val name = fileName(d)
    return when (d.tool) {
        "" -> translateSummary(d.target)
        "Bash" -> tr("Rodou ", "Ran ") + d.target
        "Read" -> tr("Leu ", "Read ") + name
        "Edit", "Update", "MultiEdit" -> tr("Editou ", "Edited ") + name
        "Write" -> tr("Criou ", "Wrote ") + name
        "Grep", "Search", "Glob" -> tr("Buscou ", "Searched ") + d.target
        "WebFetch", "Fetch" -> tr("Abriu ", "Opened ") + d.target
        "Agent", "Task" -> tr("Subagente: ", "Subagent: ") + d.target
        else -> "${d.tool} ${d.target}".trim()
    }
}

/** Several actions in a row, counted: "2 comandos · 1 arquivo lido". */
private fun summarize(actions: List<Did>): String {
    if (actions.size == 1) return describe(actions.single())
    val counts = actions.groupingBy {
        when (it.tool) {
            "Bash" -> 0
            "Read" -> 1
            "Edit", "Update", "MultiEdit", "Write" -> 2
            else -> 3
        }
    }.eachCount().toSortedMap()
    return counts.map { (kind, n) ->
        when (kind) {
            0 -> if (n == 1) tr("1 comando", "1 command") else tr("$n comandos", "$n commands")
            1 -> if (n == 1) tr("1 arquivo lido", "1 file read") else tr("$n arquivos lidos", "$n files read")
            2 -> if (n == 1) tr("1 arquivo editado", "1 file edited") else tr("$n arquivos editados", "$n files edited")
            else -> if (n == 1) tr("1 ação", "1 action") else tr("$n ações", "$n actions")
        }
    }.joinToString(" · ")
}

// ---------------------------------------------------------------- one agent

/**
 * One agent (design 5C/5D): Claude Code's screen as a conversation — your messages in bubbles, its
 * actions as a chip, its words as text, a permission request as a card of answers. Another agent's
 * screen shows as it is (5B). The terminal's keys are one tap away; the composer stays at the bottom.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgentDetail(vm: KeypadViewModel, agent: Agent, usage: UsageState?, onBack: () -> Unit, onTerminal: () -> Unit, modifier: Modifier) {
    val text by vm.agentText.collectAsStateWithLifecycle()
    val raw = text?.takeIf { it.first == agent.id }?.second
    val chat = remember(raw, I18n.lang) { raw?.let { AgentConversation.parse(it) } }
    val screen = remember(raw, I18n.lang) { raw?.let { AgentScreen.clean(it) } }
    var keysOpen by rememberSaveable(agent.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Choosing the model: /model was sent, its picker shows here as a list (and the next one, if any).
    var picking by remember(agent.id) { mutableStateOf(false) }
    // The screen, read every few seconds; faster while the terminal is open or a picker is awaited.
    LaunchedEffect(agent.id, keysOpen, picking) {
        while (true) {
            vm.readAgent(agent.id)
            delay(if (picking) 600 else if (keysOpen) 1_000 else 3_000)
        }
    }
    val model = remember(raw) { raw?.let { TuiPicker.model(it, agent.kind) } }
    val picker = remember(raw) { raw?.let { TuiPicker.parse(it) } }
    if (picking && picker != null) {
        PickerSheet(picker, onPick = { i ->
            vm.agentKeys(agent.id, picker.keysTo(i))
            scope.launch {
                // A second step (Codex asks the effort next) opens its own list; none: done.
                delay(2_500)
                if (TuiPicker.parse(vm.agentTexts[agent.id].orEmpty())?.title == picker.title || TuiPicker.parse(vm.agentTexts[agent.id].orEmpty()) == null) picking = false
            }
        }, onDismiss = {
            vm.agentKeys(agent.id, listOf("esc"))
            picking = false
        })
    }
    LaunchedEffect(agent.id) { vm.loadSlashMenu(agent.id) }
    val blocked = agent.status == "blocked"
    val ask = chat?.ask?.takeIf { blocked }
    // Commands the agent suggested, run in its session (Claude Code's "!" mode) after a confirmation.
    var confirm by remember { mutableStateOf<List<String>?>(null) }
    val runner: ((List<String>) -> Unit)? = if (agent.kind == "claude") { commands -> confirm = commands } else null
    confirm?.let { commands -> RunSheet(commands, onRun = { vm.runInAgent(agent, commands); confirm = null }, onDismiss = { confirm = null }) }
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleButton(Glyph.ChevronLeft, tr("Voltar para Agentes", "Back to Agents"), onBack)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(agentTitle(agent), style = GroupType.Title.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    val color = statusColor(agent.status)
                    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                    val lastSaid = vm.chat?.takeIf { it.id == agent.id }?.entries?.lastOrNull { it.item is ChatItem.Said }?.item as? ChatItem.Said
                    val state = if (agent.status == "done" && lastSaid?.ts != null)
                        tr("Terminou ", "Finished ") + ago((System.currentTimeMillis() / 1000 - lastSaid.ts).toInt())
                    else statusWord(agent.status).replaceFirstChar { it.uppercase() }
                    val project = agent.cwd.trimEnd('/').substringAfterLast('/')
                    Text(listOf(state, project).filter { it.isNotBlank() }.joinToString(" · "), style = GroupType.Small,
                        color = if (blocked) KeypadColors.Attention else KeypadColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // The model in use; a tap lists the others (the CLI's own /model, as a list here).
                if (model != null && (agent.kind == "claude" || agent.kind == "codex")) {
                    val view = LocalView.current
                    // Only between tasks: /model in the middle of one would get in its way.
                    val free = agent.status == "idle" || agent.status == "done"
                    Row(
                        Modifier.padding(top = 2.dp).heightIn(min = 30.dp).clip(RoundedCornerShape(15.dp)).background(KeypadColors.Surface1)
                            .alpha(if (free) 1f else 0.5f)
                            .clickable(enabled = free, onClickLabel = tr("Trocar o modelo", "Change the model")) {
                                Haptic.tap(view)
                                vm.agentPrompt(agent.id, "/model")
                                picking = true
                            }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(model, style = GroupType.Small, color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Glyph.ChevronDown, null, Modifier.size(11.dp), tint = KeypadColors.TextDim)
                    }
                }
            }
            CircleButton(Glyph.Terminal, tr("Abrir no terminal do PC", "Open in the PC terminal"), onTerminal)
        }
        Subagents(vm, agent)
        GitLine(vm, agent)
        // What is happening right now comes from the screen: a request to answer, the plan's limit.
        val live: @Composable () -> Unit = {
            if (ask != null) AskCard(ask) { key -> vm.agentKeys(agent.id, listOf(key)) }
            if (usage != null && exhausted(usage, agent.kind)) {
                Text(tr("O plano do ${AgentsText.kindName(agent.kind)} está sem limite agora.", "${AgentsText.kindName(agent.kind)}'s plan is out of limit right now."),
                    style = GroupType.Sub, color = KeypadColors.Danger)
            }
        }
        // The whole conversation from the session file on the PC; without one, the screen as read.
        DisposableEffect(agent.id) {
            vm.openChat(agent.id)
            onDispose { vm.closeChat() }
        }
        val history = vm.chat?.takeIf { it.id == agent.id && it.loaded && it.entries.isNotEmpty() }
        if (history != null) {
            Transcript(history, vm, chat?.footer ?: screen?.footer, live, Modifier.weight(1f).fillMaxWidth(), runner)
            QuickRow(vm, agent, raw, history, runner != null) { confirm = listOf(it) }
        } else {
            // The conversation keeps to its end while it grows, unless the person scrolled up to read.
            val scroll = rememberScrollState()
            var follow by remember { mutableStateOf(true) }
            LaunchedEffect(scroll) { snapshotFlow { scroll.maxValue }.collect { if (follow) scroll.scrollTo(it) } }
            LaunchedEffect(scroll) { snapshotFlow { scroll.isScrollInProgress to scroll.value }.collect { (moving, at) -> if (moving) follow = at >= scroll.maxValue - 60 } }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Spacer(Modifier.height(4.dp))
                when {
                    raw == null -> Text(tr("Lendo o terminal do agente…", "Reading the agent's terminal…"), style = GroupType.Lead, color = KeypadColors.TextMute)
                    chat != null -> Conversation(chat)
                    else -> {
                        Text(screen?.body?.ifBlank { null } ?: " ",
                            Modifier.fillMaxWidth().clip(GroupShape).background(KeypadColors.Surface1).padding(16.dp),
                            style = KeypadType.Mono.copy(fontSize = 13.sp, lineHeight = 20.sp), color = KeypadColors.Text)
                        screen?.footer?.let { Text(it, Modifier.padding(start = 16.dp), style = GroupType.Small, color = KeypadColors.TextMute) }
                    }
                }
                live()
                Spacer(Modifier.height(4.dp))
            }
            QuickRow(vm, agent, raw, null, false) { confirm = listOf(it) }
        }
        // The terminal's keys: numbered answers, arrows, Esc and Enter (open by themselves when an
        // agent asks something the app cannot read as a request).
        if (keysOpen || (blocked && ask == null)) TerminalPanel(vm, agent.id) { keysOpen = false }
        var field by rememberSaveable(agent.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
        val focus = remember { FocusRequester() }
        val prompt = field.text
        fun setPrompt(text: String) {
            field = TextFieldValue(text, TextRange(text.length))
        }
        // "/" (or Codex's "$") at the start: the agent's own commands and what is installed for it.
        val menu = vm.slashMenus[agent.id]
        val query = slashQuery(prompt)
        if (query != null && menu != null) {
            val matches = remember(query, menu) { slashMatches(menu, query) }
            if (matches.isNotEmpty()) SlashMenu(matches) { setPrompt(it.name + " ") }
        }
        if (vm.agentUploads > 0) {
            Text(if (vm.agentUploads == 1) tr("Enviando 1 arquivo ao projeto do agente…", "Sending 1 file to the agent's project…")
                else tr("Enviando ${vm.agentUploads} arquivos ao projeto do agente…", "Sending ${vm.agentUploads} files to the agent's project…"),
                Modifier.padding(start = 60.dp, top = 6.dp), style = GroupType.Small, color = KeypadColors.TextDim)
        }
        val no = ask?.options?.firstOrNull { it.choice == Choice.NO }
        val canWrite = !blocked || no != null
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val view = LocalView.current
            Box(
                Modifier.size(KeypadDimens.MinTouch).clip(CircleShape).background(if (keysOpen) KeypadColors.Text else KeypadColors.Surface1)
                    .clickable(onClickLabel = if (keysOpen) tr("Esconder as teclas", "Hide the keys") else tr("Mostrar as teclas do terminal", "Show the terminal keys")) {
                        Haptic.tap(view); keysOpen = !keysOpen
                    }.semantics { contentDescription = tr("Teclas do terminal", "Terminal keys") },
                contentAlignment = Alignment.Center,
            ) { Icon(Glyph.Keyboard, null, Modifier.size(20.dp), tint = if (keysOpen) KeypadColors.Bg else KeypadColors.Text) }
            Row(
                Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(KeypadColors.Surface1).padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val kind = AgentsText.kindName(agent.kind)
                // Files for the agent: they go into its project; the message mentions them.
                val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    vm.attachToAgent(agent, uris) { mention -> setPrompt(field.text.trimEnd().let { if (it.isEmpty()) "$mention " else "$it $mention " }) }
                }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable(enabled = canWrite, onClickLabel = tr("Anexar arquivo para o agente", "Attach a file for the agent")) {
                        Haptic.tap(view); pick.launch(arrayOf("*/*"))
                    }.semantics { contentDescription = tr("Anexar arquivo para o agente", "Attach a file for the agent") },
                    contentAlignment = Alignment.Center,
                ) { Icon(Glyph.Paperclip, null, Modifier.size(20.dp), tint = if (canWrite) KeypadColors.TextDim else KeypadColors.TextMute) }
                // The agent's menu without typing: "/" (Codex: again for its "$" skills).
                if (menu != null && prompt.isEmpty() || prompt == "/" || prompt == "$") {
                    val next = if (prompt == "/" && menu?.any { it.name.startsWith("$") } == true) "$" else if (prompt.isEmpty()) "/" else ""
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(if (prompt.isNotEmpty()) KeypadColors.Surface3 else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(enabled = canWrite, onClickLabel = tr("Comandos do agente", "The agent's commands")) {
                                Haptic.tap(view); setPrompt(next); if (next.isNotEmpty()) focus.requestFocus()
                            }.semantics { contentDescription = tr("Comandos do agente", "The agent's commands") },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (prompt == "/" && next == "$") "$" else "/", style = KeypadType.MonoValue.copy(fontSize = 17.sp), color = if (canWrite) KeypadColors.TextDim else KeypadColors.TextMute) }
                }
                BasicTextField(
                    field, { field = if (it.text.length <= 3000) it else it.copy(text = it.text.take(3000)) },
                    Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = tr("Mensagem para o $kind", "Message to $kind") },
                    enabled = canWrite, maxLines = 5,
                    textStyle = GroupType.Title.copy(color = KeypadColors.Text),
                    cursorBrush = SolidColor(KeypadColors.Text),
                    decorationBox = { field ->
                        if (prompt.isEmpty()) {
                            Text(when {
                                no != null -> tr("Ou diga o que fazer", "Or say what to do")
                                blocked -> tr("Responda com as teclas", "Answer with the keys")
                                else -> tr("Responder ao $kind", "Reply to $kind")
                            }, style = GroupType.Title, color = KeypadColors.TextMute, maxLines = 1)
                        }
                        field()
                    },
                )
                // Hands free: holding the mic sends what was said as soon as it is recognized.
                var sendSpoken by remember { mutableStateOf(false) }
                val sendNow: (String) -> Unit = { message ->
                    if (no != null) {
                        vm.agentKeys(agent.id, listOf(no.key))
                        scope.launch { delay(700); vm.agentPrompt(agent.id, message) }
                    } else vm.agentPrompt(agent.id, message)
                }
                val dictate = rememberDictation({ vm.showMessage(tr("Este celular não tem reconhecimento de voz.", "This phone doesn't have speech recognition.")) }) { spoken ->
                    if (sendSpoken && spoken.isNotBlank()) {
                        sendSpoken = false
                        sendNow((field.text.trimEnd() + " " + spoken).trim())
                        setPrompt("")
                    } else setPrompt(if (field.text.isBlank()) spoken else field.text.trimEnd() + " " + spoken)
                }
                val send = prompt.isNotBlank()
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(if (canWrite) KeypadColors.Text else KeypadColors.Surface3)
                        .combinedClickable(
                            enabled = canWrite, onClickLabel = if (send) tr("Enviar", "Send") else tr("Ditar", "Dictate"),
                            onLongClickLabel = tr("Falar e enviar", "Speak and send"),
                            onLongClick = { Haptic.tap(view); sendSpoken = true; dictate() },
                        ) {
                            if (!send) dictate() else {
                                val message = prompt.trim()
                                setPrompt("")
                                // A command that opens a screen in the terminal: show the terminal.
                                if (menu?.firstOrNull { it.name == message.substringBefore(' ') }?.live == true) keysOpen = true
                                if (no != null) {
                                    // "No, and tell Claude what to do": refuse, then say what instead.
                                    vm.agentKeys(agent.id, listOf(no.key))
                                    scope.launch {
                                        delay(700)
                                        vm.agentPrompt(agent.id, message)
                                    }
                                } else vm.agentPrompt(agent.id, message)
                            }
                        }.semantics { contentDescription = if (send) tr("Enviar", "Send") else tr("Ditar", "Dictate") },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (send) Glyph.Send else Glyph.Mic, null, Modifier.size(18.dp), tint = if (canWrite) KeypadColors.Bg else KeypadColors.TextMute) }
            }
        }
    }
}

@Composable
private fun Conversation(chat: AgentConversation.Chat) {
    // Actions in a row become one chip.
    val blocks = mutableListOf<Any>()
    for (item in chat.items) {
        val last = blocks.lastOrNull()
        if (item is Did && last is MutableList<*>) @Suppress("UNCHECKED_CAST") (last as MutableList<Did>).add(item)
        else blocks += if (item is Did) mutableListOf(item) else item
    }
    for (b in blocks) {
        when (b) {
            is You -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(b.text, Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp)).background(KeypadColors.Surface3).padding(horizontal = 14.dp, vertical = 10.dp),
                    style = GroupType.Chat, color = KeypadColors.Text)
            }
            is Said -> {
                val table = b.text.any { it in "│┌└├─" }
                Text(b.text, style = if (table) KeypadType.Mono.copy(fontSize = 12.sp, lineHeight = 17.sp) else GroupType.Chat, color = KeypadColors.Text)
            }
            is MutableList<*> -> @Suppress("UNCHECKED_CAST") ActionChip(b as List<Did>)
        }
    }
    chat.footer?.let { Text(it, style = GroupType.Small, color = KeypadColors.TextMute) }
}

/**
 * The conversation as a chat, newest at the bottom: older pages load as the top comes into view,
 * and new messages keep the list at its end while the person is there.
 */
@Composable
private fun Transcript(c: ChatState, vm: KeypadViewModel, status: String?, live: @Composable () -> Unit, modifier: Modifier, onRun: ((List<String>) -> Unit)?) {
    val blocks = remember(c.entries) { c.blocks() }
    // Each message's time by its key, to know the one before.
    val times = remember(blocks) {
        java.util.TreeMap<Long, Long>().apply {
            for (b in blocks) {
                val item = (b as? ChatBlock.Message)?.item
                val t = (item as? ChatItem.You)?.ts ?: (item as? ChatItem.Said)?.ts
                if (t != null) put(b.key, t)
            }
        }
    }
    val list = rememberLazyListState()
    val newest = blocks.lastOrNull()?.key
    LaunchedEffect(newest) { if (list.firstVisibleItemIndex <= 2) list.animateScrollToItem(0) }
    // Older pages come before the top is reached: reading up never waits.
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && last >= total - 8) vm.olderChat() }
    }
    LazyColumn(modifier, state = list, reverseLayout = true, verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        item(key = "live") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                status?.let { Text(it, style = GroupType.Small, color = KeypadColors.TextMute) }
                live()
            }
        }
        items(blocks.asReversed(), key = { it.key }) { b ->
            // A time above a message that comes after a break (design 5C's "Hoje 10:02").
            val ts = ((b as? ChatBlock.Message)?.item as? ChatItem.You)?.ts ?: ((b as? ChatBlock.Message)?.item as? ChatItem.Said)?.ts
            val previous = remember(b.key, blocks) { times.lowerEntry(b.key)?.value }
            Column {
            if (ts != null && separatorBefore(previous, ts)) {
                Text(timeLabel(ts, System.currentTimeMillis() / 1000), Modifier.fillMaxWidth().padding(bottom = 10.dp), style = GroupType.Small,
                    color = KeypadColors.TextMute, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            when (b) {
                is ChatBlock.Message -> when (val item = b.item) {
                    is ChatItem.You -> Bubble(item.text)
                    is ChatItem.Said -> MarkdownText(item.text, onRun = onRun)
                    else -> {}
                }
                is ChatBlock.Actions -> ActionSteps(b.steps, c.outputs) { call, at -> vm.chatOutput(call, at) }
            }
            }
        }
        item(key = "top") {
            if (c.more) {
                Text(tr("Carregando mensagens anteriores…", "Loading earlier messages…"), Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    style = GroupType.Small, color = KeypadColors.TextMute, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            } else {
                Text(tr("Início da conversa", "Start of the conversation"), Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    style = GroupType.Small, color = KeypadColors.TextMute, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

/**
 * One row over the composer (design 5C's chips): quick actions that fit this agent now (stop it,
 * continue, compact when its context fills up, review the changes, summarize), then the commands
 * its last reply suggested, numbered in its order (those already run are checked).
 */
@Composable
private fun QuickRow(vm: KeypadViewModel, agent: Agent, raw: String?, c: ChatState?, canRun: Boolean, onPick: (String) -> Unit) {
    val git by vm.agentGit.collectAsStateWithLifecycle()
    val changed = git?.takeIf { it.first == agent.id }?.second?.files?.size ?: 0
    val actions = remember(agent.kind, agent.status, raw, changed) { quickActions(agent.kind, agent.status, raw?.let { contextUsed(it) }, changed) }
    val commands = remember(c?.entries) {
        if (c == null || !canRun) emptyList() else {
            val lastYou = c.entries.indexOfLast { it.item is ChatItem.You }
            suggestedCommands(c.entries.drop(lastYou + 1).mapNotNull { (it.item as? ChatItem.Said)?.text }.joinToString("\n\n"))
        }
    }
    if (actions.isEmpty() && commands.isEmpty()) return
    val ran = vm.ranCommands[agent.id].orEmpty()
    val view = LocalView.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (a in actions) {
            Box(
                Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).background(KeypadColors.Surface1)
                    .then(if (a.urgent) Modifier.border(1.dp, KeypadColors.Attention, RoundedCornerShape(18.dp)) else Modifier)
                    .clickable(onClickLabel = a.label) {
                        Haptic.tap(view)
                        a.keys?.let { vm.agentKeys(agent.id, it) }
                        a.prompt?.let { vm.agentPrompt(agent.id, it) }
                    }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(a.label, style = GroupType.Lead, color = if (a.urgent) KeypadColors.Attention else KeypadColors.Text, maxLines = 1) }
        }
        commands.forEachIndexed { i, command ->
            val done = command in ran
            Row(
                Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).background(KeypadColors.Surface1)
                    .clickable(onClickLabel = tr("Rodar $command", "Run $command")) { Haptic.tap(view); onPick(command) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${i + 1}", style = KeypadType.Mono.copy(fontSize = 11.sp), color = KeypadColors.TextMute)
                Icon(if (done) Glyph.Check else Glyph.Play, null, Modifier.size(12.dp), tint = if (done) KeypadColors.Ok else KeypadColors.Text)
                Text(command, Modifier.widthIn(max = 240.dp), style = KeypadType.Mono.copy(fontSize = 12.sp), color = if (done) KeypadColors.TextDim else KeypadColors.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Running a suggested command, confirmed as design 5D asks for a request: the question, the command
 * in its own box, and big answers — the main one white.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RunSheet(commands: List<String>, onRun: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = KeypadColors.Bg, dragHandle = null,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(KeypadColors.Surface1).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (commands.size == 1) tr("Rodar este comando?", "Run this command?") else tr("Rodar estes ${commands.size} comandos?", "Run these ${commands.size} commands?"),
                    Modifier.padding(start = 4.dp), style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text)
                Text(tr("No terminal do agente, na pasta do projeto. Ele vê a saída.", "In the agent's terminal, in the project folder. It sees the output."),
                    Modifier.padding(start = 4.dp, bottom = 4.dp), style = GroupType.Sub, color = KeypadColors.TextDim)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KeypadColors.Surface3).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Comando", "Command"), style = GroupType.Small, color = KeypadColors.TextDim)
                    for ((i, c) in commands.withIndex()) {
                        Text((if (commands.size > 1) "${i + 1}. " else "") + c, style = KeypadType.Mono.copy(fontSize = 14.sp, lineHeight = 20.sp), color = KeypadColors.Text)
                    }
                }
                AnswerButton(tr("Rodar", "Run"), true, Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(14.dp), onRun)
                AnswerButton(tr("Cancelar", "Cancel"), false, Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(14.dp), onDismiss)
            }
        }
    }
}

@Composable
private fun Bubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(text, Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp)).background(KeypadColors.Surface3).padding(horizontal = 14.dp, vertical = 10.dp),
            style = GroupType.Chat, color = KeypadColors.Text)
    }
}

/**
 * Actions in a row as a card, one line each (a command as `$ …`, an edit with its +/−). A line
 * opens its output or its diff. A long run shows its last lines; the rest one tap away.
 */
@Composable
private fun ActionSteps(steps: List<ChatBlock.Step>, outputs: Map<String, String>, onFull: (String, Long) -> Unit) {
    var all by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    val hidden = if (all || steps.size <= 4) 0 else steps.size - 3
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KeypadColors.Surface1)) {
        if (hidden > 0) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { Haptic.tap(view); all = true }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Glyph.ChevronDown, null, Modifier.size(12.dp), tint = KeypadColors.TextDim)
                Text(if (hidden == 1) tr("Mais 1 ação", "1 more action") else tr("Mais $hidden ações", "$hidden more actions"), style = GroupType.Sub, color = KeypadColors.TextDim)
            }
        }
        steps.drop(hidden).forEachIndexed { i, step ->
            if (i > 0 || hidden > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(KeypadColors.Line))
            StepRow(step, outputs, onFull)
        }
    }
}

private val shells = setOf("Bash", "shell", "exec", "local_shell", "exec_command")

/** An action as a developer reads it: the command itself, or the tool and the file. */
private fun stepLabel(t: ChatItem.Tool): String {
    val target = t.target.lineSequence().firstOrNull().orEmpty()
    val path = target.trimEnd('/').split('/').takeLast(2).joinToString("/")
    return when {
        t.name in shells -> "$ $target"
        t.name in setOf("Read", "Edit", "Update", "MultiEdit", "Write", "NotebookEdit") -> "${t.name} $path"
        t.name == "apply_patch" -> "apply_patch"
        else -> "${t.name} $target".trim()
    }
}

private fun changed(t: ChatItem.Tool): Pair<Int, Int>? = when {
    t.patch != null -> t.patch.lines().count { it.startsWith("+") && !it.startsWith("+++") } to t.patch.lines().count { it.startsWith("-") && !it.startsWith("---") }
    t.new != null -> t.new.lines().size to (t.old?.lines()?.size ?: 0)
    else -> null
}

@Composable
private fun StepRow(step: ChatBlock.Step, outputs: Map<String, String>, onFull: (String, Long) -> Unit) {
    val tool = step.tool
    val out = step.out
    var open by rememberSaveable(tool?.id ?: out?.id) { mutableStateOf(false) }
    val view = LocalView.current
    val diff = tool?.let { changed(it) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = if (open) tr("Fechar", "Close") else tr("Abrir", "Open")) { Haptic.tap(view); open = !open }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                out == null -> Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(6.dp).clip(CircleShape).background(KeypadColors.TextMute)) }
                out.err -> Icon(Glyph.Close, tr("falhou", "failed"), Modifier.size(14.dp), tint = KeypadColors.Danger)
                else -> Icon(Glyph.Check, null, Modifier.size(14.dp), tint = KeypadColors.TextMute)
            }
            Text(tool?.let { stepLabel(it) } ?: tr("resultado", "result"), Modifier.weight(1f), style = KeypadType.Mono.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = KeypadColors.Text, maxLines = if (open) 6 else 1, overflow = TextOverflow.Ellipsis)
            if (diff != null) {
                Text("+${diff.first}", style = KeypadType.Mono.copy(fontSize = 11.sp), color = KeypadColors.Ok)
                if (diff.second > 0) Text("−${diff.second}", style = KeypadType.Mono.copy(fontSize = 11.sp), color = KeypadColors.Danger)
            }
            Icon(if (open) Glyph.ChevronDown else Glyph.ChevronRight, null, Modifier.size(12.dp), tint = KeypadColors.TextMute)
        }
        if (open) {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tool?.why?.let { Text(it, style = GroupType.Small, color = KeypadColors.TextDim) }
                if (tool != null && tool.name in shells && tool.target.contains('\n')) CodeBox(tool.target, tr("comando", "command"))
                when {
                    tool?.patch != null -> DiffBox(tool.patch.lines().map { l -> l.firstOrNull()?.takeIf { it == '+' || it == '-' } to l })
                    tool?.new != null -> DiffBox(tool.old.orEmpty().lines().filter { tool.old != null }.map { '-' to "- $it" } + tool.new.lines().map { '+' to "+ $it" })
                }
                if (out != null && out.text.isNotBlank()) {
                    val full = outputs[out.id]
                    CodeBox(full ?: out.text, if (out.err) tr("erro", "error") else tr("saída", "output"), color = if (out.err) KeypadColors.Danger else KeypadColors.TextDim)
                    if (out.cut && full == null && out.at != null) {
                        Text(tr("Buscar a saída inteira", "Fetch the whole output"), Modifier.clip(RoundedCornerShape(10.dp)).clickable { Haptic.tap(view); onFull(out.id, out.at) }
                            .padding(horizontal = 4.dp, vertical = 10.dp), style = GroupType.Sub.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text)
                    }
                }
            }
        }
    }
}

/** A change as red and green lines. */
@Composable
private fun DiffBox(lines: List<Pair<Char?, String>>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(KeypadColors.Surface1).horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        for ((mark, line) in lines.take(80)) {
            val color = when (mark) {
                '+' -> KeypadColors.Ok
                '-' -> KeypadColors.Danger
                else -> KeypadColors.TextDim
            }
            Text(line, Modifier.background(if (mark != null) color.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent).padding(horizontal = 12.dp),
                style = KeypadType.Mono.copy(fontSize = 12.sp, lineHeight = 17.sp), color = if (mark != null) color else KeypadColors.Text, softWrap = false)
        }
        if (lines.size > 80) Text(tr("… mais ${lines.size - 80} linhas", "… ${lines.size - 80} more lines"), Modifier.padding(horizontal = 12.dp),
            style = GroupType.Small, color = KeypadColors.TextMute)
    }
}

/** What the agent did, in one line; a tap lists each action. */
@Composable
private fun ActionChip(actions: List<Did>) {
    var open by remember { mutableStateOf(false) }
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, KeypadColors.Line, RoundedCornerShape(18.dp))
                .clickable(onClickLabel = if (open) tr("Esconder as ações", "Hide the actions") else tr("Ver as ações", "See the actions")) { Haptic.tap(view); open = !open }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Glyph.Check, null, Modifier.size(14.dp), tint = KeypadColors.TextDim)
            Text(summarize(actions), Modifier.weight(1f, fill = false), style = GroupType.Sub, color = KeypadColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(if (open) Glyph.ChevronDown else Glyph.ChevronRight, null, Modifier.size(12.dp), tint = KeypadColors.TextDim)
        }
        if (open) {
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (d in actions) Text(describe(d), style = KeypadType.Mono.copy(fontSize = 12.sp, lineHeight = 17.sp), color = KeypadColors.TextDim, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A permission request (design 5D): the question, what it is about, and each answer as a button. */
@Composable
private fun AskCard(ask: AgentConversation.Ask, onKey: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(KeypadColors.Surface1).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(question(ask), Modifier.padding(start = 4.dp, bottom = 4.dp), style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text)
        if (ask.detail.isNotBlank() || ask.title.isNotBlank()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KeypadColors.Surface3).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (ask.title.isNotBlank()) Text(askTitle(ask.title), style = GroupType.Small, color = KeypadColors.TextDim)
                if (ask.detail.isNotBlank()) {
                    Text(ask.detail.lines().take(8).joinToString("\n"), style = KeypadType.Mono.copy(fontSize = 14.sp, lineHeight = 20.sp), color = KeypadColors.Text)
                }
            }
        }
        for (o in ask.options) AnswerButton(longLabel(o), o.choice == Choice.YES, Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(14.dp)) { onKey(o.key) }
    }
}

/**
 * The subagents the agent launched: a line while any runs (or one just finished), and the list
 * on a tap — what each is for, its kind, since when, and what a finished one answered.
 */
@Composable
private fun Subagents(vm: KeypadViewModel, agent: Agent) {
    LaunchedEffect(agent.id, agent.subagents > 0) {
        vm.loadSubagents(agent.id)
        while (agent.subagents > 0) {
            delay(3_000)
            vm.loadSubagents(agent.id)
        }
    }
    val read by vm.agentSubagents.collectAsStateWithLifecycle()
    val subs = read?.takeIf { it.first == agent.id }?.second.orEmpty().filter { it.state == "running" || it.quiet < 600 }
    if (subs.isEmpty()) return
    val running = subs.count { it.state == "running" }
    var open by rememberSaveable(agent.id) { mutableStateOf(false) }
    val view = LocalView.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(14.dp)).background(KeypadColors.Surface1)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = if (open) tr("Esconder", "Hide") else tr("Ver os subagentes", "See the subagents")) {
                Haptic.tap(view); open = !open
            }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (running > 0) Spinner() else Icon(Glyph.Check, null, Modifier.size(16.dp), tint = KeypadColors.Ok)
            Text(if (running > 0) (if (running == 1) tr("1 subagente rodando", "1 subagent running") else tr("$running subagentes rodando", "$running subagents running"))
                else tr("Subagentes terminaram", "Subagents finished"), Modifier.weight(1f), style = GroupType.Sub.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text)
            Icon(if (open) Glyph.ChevronDown else Glyph.ChevronRight, null, Modifier.size(12.dp), tint = KeypadColors.TextDim)
        }
        if (open) {
            for (s in subs) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(KeypadColors.Line))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 2.dp)) {
                        when (s.state) {
                            "running" -> Box(Modifier.size(8.dp).clip(CircleShape).background(KeypadColors.Ok))
                            "done" -> Icon(Glyph.Check, null, Modifier.size(14.dp), tint = KeypadColors.TextDim)
                            else -> Icon(Glyph.Close, null, Modifier.size(14.dp), tint = KeypadColors.TextMute)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(s.description.ifBlank { s.type }, style = GroupType.Lead, color = KeypadColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val state = when (s.state) {
                            "running" -> tr("rodando · começou ${ago(s.since)}", "running · started ${ago(s.since)}")
                            "done" -> tr("terminou ${ago(s.quiet)}", "finished ${ago(s.quiet)}")
                            else -> tr("parou ${ago(s.quiet)}", "stopped ${ago(s.quiet)}")
                        }
                        Text(listOf(s.type, state).filter { it.isNotBlank() }.joinToString(" · "), style = GroupType.Small, color = KeypadColors.TextDim)
                        if (s.said.isNotBlank()) Text(s.said, style = GroupType.Sub, color = KeypadColors.TextDim, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** The agent's project in git in one line (branch, changed files, ahead); a tap opens the files, commits and diffs. */
@Composable
private fun GitLine(vm: KeypadViewModel, agent: Agent) {
    LaunchedEffect(agent.id, agent.seq, agent.status) { vm.loadGit(agent.id) }
    val read by vm.agentGit.collectAsStateWithLifecycle()
    val git = read?.takeIf { it.first == agent.id }?.second ?: return
    if (!git.repo) return
    var open by rememberSaveable(agent.id) { mutableStateOf(false) }
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 40.dp).clip(RoundedCornerShape(14.dp)).background(KeypadColors.Surface1)
            .clickable(onClickLabel = tr("Ver o git do projeto", "See the project's git")) { Haptic.tap(view); open = true; vm.loadGit(agent.id) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("⎇", style = KeypadType.MonoValue, color = KeypadColors.TextDim)
        Text(git.branch.ifBlank { "HEAD" }, style = KeypadType.Mono.copy(fontSize = 13.sp), color = KeypadColors.Text, maxLines = 1)
        val changed = git.files.size
        Text(if (changed == 0) tr("sem mudanças", "clean") else if (changed == 1) tr("1 arquivo mudado", "1 file changed") else tr("$changed arquivos mudados", "$changed files changed"),
            Modifier.weight(1f), style = GroupType.Sub, color = KeypadColors.TextDim, maxLines = 1)
        if (git.ahead > 0) Text("↑${git.ahead}", style = KeypadType.Mono.copy(fontSize = 12.sp), color = KeypadColors.Ok)
        if (git.behind > 0) Text("↓${git.behind}", style = KeypadType.Mono.copy(fontSize = 12.sp), color = KeypadColors.Attention)
        Icon(Glyph.ChevronRight, null, Modifier.size(12.dp), tint = KeypadColors.TextDim)
    }
    if (open) GitSheet(vm, agent, git) { open = false }
}

@Composable
private fun GitSheet(vm: KeypadViewModel, agent: Agent, git: com.sandevsystems.omarchyremote.network.GitSummary, onDismiss: () -> Unit) {
    var file by rememberSaveable { mutableStateOf<String?>(null) }
    val diffRead by vm.agentGitDiff.collectAsStateWithLifecycle()
    val view = LocalView.current
    val project = agent.cwd.trimEnd('/').substringAfterLast('/')
    AppSheet(file ?: "git · $project", { if (file != null) file = null else onDismiss() }, subtitle = if (file == null) "⎇ ${git.branch}" else null, tall = true) {
        val f = file
        if (f != null) {
            val diff = diffRead?.takeIf { it.first == agent.id && it.second == f }?.third
            if (diff == null) Text(tr("Lendo o diff…", "Reading the diff…"), style = GroupType.Sub, color = KeypadColors.TextMute)
            else DiffBox(diff.lines().filterNot { it.startsWith("diff --git") || it.startsWith("index ") || it.startsWith("---") || it.startsWith("+++") }
                .map { l -> (l.firstOrNull()?.takeIf { it == '+' || it == '-' }) to l })
            return@AppSheet
        }
        SectionLabel(tr("Mudanças", "Changes"))
        Column(Modifier.fillMaxWidth().clip(GroupShape).background(KeypadColors.Surface1)) {
            if (git.files.isEmpty()) Text(tr("Nada mudado desde o último commit.", "Nothing changed since the last commit."), Modifier.padding(16.dp), style = GroupType.Sub, color = KeypadColors.TextMute)
            git.files.forEachIndexed { i, g ->
                if (i > 0) GroupDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = tr("Ver o diff", "See the diff")) { Haptic.tap(view); file = g.path; vm.loadGitDiff(agent.id, g.path) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val color = when (g.state) {
                        "?", "A" -> KeypadColors.Ok
                        "D" -> KeypadColors.Danger
                        else -> KeypadColors.Attention
                    }
                    Text(g.state.replace("?", "N"), Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(top = 2.dp),
                        style = KeypadType.Mono.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = color, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(g.path, Modifier.weight(1f), style = KeypadType.Mono.copy(fontSize = 13.sp), color = KeypadColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (g.added > 0) Text("+${g.added}", style = KeypadType.Mono.copy(fontSize = 11.sp), color = KeypadColors.Ok)
                    if (g.removed > 0) Text("−${g.removed}", style = KeypadType.Mono.copy(fontSize = 11.sp), color = KeypadColors.Danger)
                }
            }
        }
        if (git.commits.isNotEmpty()) {
            SectionLabel(tr("Últimos commits", "Last commits") + if (git.ahead > 0) tr(" · ${git.ahead} a enviar", " · ${git.ahead} to push") else "")
            Column(Modifier.fillMaxWidth().clip(GroupShape).background(KeypadColors.Surface1)) {
                git.commits.forEachIndexed { i, c ->
                    if (i > 0) GroupDivider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(c.subject, style = GroupType.Sub, color = KeypadColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${c.hash} · ${c.`when`}", style = KeypadType.Mono.copy(fontSize = 11.sp), color = KeypadColors.TextMute)
                    }
                }
            }
        }
    }
}

/** A picker the agent's CLI shows (its /model, the effort after it…), as a native list: tap to pick. */
@Composable
private fun PickerSheet(picker: TuiPicker, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AppSheet(picker.title, onDismiss) {
        PickerOptions(picker, onPick)
    }
}

@Composable
private fun PickerOptions(picker: TuiPicker, onPick: (Int) -> Unit) {
    val view = LocalView.current
    Column(Modifier.fillMaxWidth().clip(GroupShape).background(KeypadColors.Surface1)) {
        picker.options.forEachIndexed { i, o ->
            if (i > 0) GroupDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = o.label) { Haptic.tap(view); onPick(i) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(o.label, style = GroupType.Title.copy(fontWeight = if (o.current) FontWeight.SemiBold else FontWeight.Normal), color = KeypadColors.Text)
                    if (o.detail.isNotBlank()) Text(o.detail, style = GroupType.Sub, color = KeypadColors.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (o.current) Icon(Glyph.Check, tr("atual", "current"), Modifier.size(18.dp), tint = KeypadColors.Ok)
            }
        }
    }
    picker.hint?.let { Text(it, Modifier.padding(start = 4.dp), style = GroupType.Small, color = KeypadColors.TextMute) }
}

/** The agent's "/" menu while its first word is typed: the command, whose it is, what it does. */
@Composable
private fun SlashMenu(items: List<SlashCommand>, onPick: (SlashCommand) -> Unit) {
    val view = LocalView.current
    LazyColumn(
        Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(max = 300.dp).clip(GroupShape).background(KeypadColors.Surface1),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(items, key = { it.name }) { c ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClickLabel = c.name) { Haptic.tap(view); onPick(c) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.name, Modifier.weight(1f, fill = false), style = KeypadType.Mono.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = KeypadColors.Text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val group = when (c.group) {
                            "" -> null
                            "skill" -> "skill"
                            "project" -> tr("projeto", "project")
                            else -> c.group
                        }
                        if (group != null) Text(group, Modifier.clip(RoundedCornerShape(6.dp)).background(KeypadColors.Surface3).padding(horizontal = 6.dp, vertical = 1.dp),
                            style = GroupType.Small.copy(fontSize = 11.sp), color = KeypadColors.TextDim, maxLines = 1)
                    }
                    val line = (if (I18n.lang == I18n.Lang.PT) c.pt else null) ?: c.description
                    if (line.isNotBlank()) Text(line, style = GroupType.Sub, color = KeypadColors.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (c.live) Icon(Glyph.Terminal, tr("abre uma tela no terminal", "opens a screen in the terminal"), Modifier.size(14.dp), tint = KeypadColors.TextMute)
            }
        }
    }
}

/**
 * The agent's terminal as it is now (a picker a command opened, a question), and its keys: what
 * the chat cannot show is operated here.
 */
@Composable
private fun TerminalPanel(vm: KeypadViewModel, id: String, onClose: () -> Unit) {
    // The screen as the terminal shows it, colors and all: a picker's selection, a panel's tab.
    LaunchedEffect(id) {
        while (true) {
            vm.readScreen(id)
            delay(1_000)
        }
    }
    val screen by vm.agentScreen.collectAsStateWithLifecycle()
    val raw = screen?.takeIf { it.first == id }?.second
    val fg = KeypadColors.Text
    val bg = KeypadColors.Bg
    val text = remember(raw, fg, bg) { raw?.let { ansiText(it, fg, bg) } }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(GroupShape).background(KeypadColors.Surface1).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Terminal do agente", "The agent's terminal"), Modifier.weight(1f), style = GroupType.Small.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.TextDim)
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = tr("Fechar o terminal", "Close the terminal"), onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Glyph.Close, tr("Fechar", "Close"), Modifier.size(16.dp), tint = KeypadColors.TextDim)
            }
        }
        val scroll = rememberScrollState()
        LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }
        Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(12.dp)).background(bg).verticalScroll(scroll).horizontalScroll(rememberScrollState())
            .padding(10.dp)) {
            if (text != null) Text(text, style = KeypadType.Mono.copy(fontSize = 10.sp, lineHeight = 13.sp), softWrap = false)
            else Text(tr("Lendo…", "Reading…"), style = KeypadType.Mono.copy(fontSize = 11.sp), color = KeypadColors.TextMute)
        }
        // A picker on the screen: its options as buttons too.
        val picker = remember(raw) { raw?.let { r -> TuiPicker.parse(ansiRuns(r).joinToString("") { it.text }) } }
        if (picker != null) PickerOptions(picker) { i -> vm.agentKeys(id, picker.keysTo(i)) }
        Text(tr("Setas e Tab navegam; Enter escolhe; Esc fecha.", "Arrows and Tab move; Enter picks; Esc closes."), style = GroupType.Small, color = KeypadColors.TextMute)
        AgentKeys(vm, id)
    }
}

/** A colored screen as styled text: inverse swaps the colors, dim fades, bold stays. */
private fun ansiText(raw: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString {
    val lines = raw.replace("\r", "").lines().dropLastWhile { it.isBlank() }.takeLast(60).joinToString("\n")
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    fun color(rgb: Int) = androidx.compose.ui.graphics.Color(0xFF000000.toInt() or rgb)
    for (run in ansiRuns(lines)) {
        val st = run.style
        var front = st.fg?.let(::color) ?: fg
        var back = st.bg?.let(::color)
        if (st.inverse) {
            val f = front
            front = back ?: bg
            back = f
        }
        if (st.dim) front = front.copy(alpha = 0.55f)
        builder.pushStyle(SpanStyle(color = front, background = back ?: androidx.compose.ui.graphics.Color.Unspecified,
            fontWeight = if (st.bold) FontWeight.Bold else null, fontStyle = if (st.italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
            textDecoration = if (st.underline) androidx.compose.ui.text.style.TextDecoration.Underline else null))
        builder.append(run.text)
        builder.pop()
    }
    return builder.toAnnotatedString()
}

@Composable
private fun AgentKeys(vm: KeypadViewModel, id: String) {
    val key: @Composable (String, List<String>, Modifier) -> Unit = { label, keys, m ->
        val view = LocalView.current
        Box(m.height(48.dp).clip(RoundedCornerShape(12.dp)).background(KeypadColors.Surface3).clickable(onClickLabel = label) { Haptic.tap(view); vm.agentKeys(id, keys) },
            contentAlignment = Alignment.Center) {
            when (label) {
                tr("Cima", "Up") -> Icon(Glyph.ArrowUp, label, Modifier.size(20.dp), tint = KeypadColors.Text)
                tr("Baixo", "Down") -> Icon(Glyph.ArrowDown, label, Modifier.size(20.dp), tint = KeypadColors.Text)
                tr("Esquerda", "Left") -> Icon(Glyph.ArrowLeft, label, Modifier.size(20.dp), tint = KeypadColors.Text)
                tr("Direita", "Right") -> Icon(Glyph.ArrowRight, label, Modifier.size(20.dp), tint = KeypadColors.Text)
                else -> Text(label, style = GroupType.Title.copy(fontWeight = FontWeight.Medium), color = KeypadColors.Text)
            }
        }
    }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in listOf("1", "2", "3")) key(n, listOf(n), Modifier.weight(1f))
            key(tr("Cima", "Up"), listOf("up"), Modifier.weight(1f))
            key(tr("Baixo", "Down"), listOf("down"), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            key("Esc", listOf("esc"), Modifier.weight(1.4f))
            key(tr("Esquerda", "Left"), listOf("left"), Modifier.weight(1f))
            key(tr("Direita", "Right"), listOf("right"), Modifier.weight(1f))
            key("Tab", listOf("tab"), Modifier.weight(1.2f))
            key("Enter", listOf("enter"), Modifier.weight(1.8f))
        }
    }
}
