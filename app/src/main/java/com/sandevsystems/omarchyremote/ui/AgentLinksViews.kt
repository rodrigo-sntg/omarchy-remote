package com.sandevsystems.omarchyremote.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandevsystems.omarchyremote.KeypadViewModel
import com.sandevsystems.omarchyremote.network.Agent
import com.sandevsystems.omarchyremote.network.AgentLinks
import com.sandevsystems.omarchyremote.network.AgentsText
import com.sandevsystems.omarchyremote.network.RelayHeader

// Agents talking to each other (host links.py), as the chat shows it: what an agent said can go to
// another; a review asked of the other kind comes back as a card; a message that came from another
// agent says so, and opens where it came from.

/** Under an agent's answer: copy it, or pass it on to another agent. Quiet until needed. */
@Composable
internal fun AnswerActions(text: String, onRelay: () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        SmallAction(Glyph.Copy, tr("Copiar", "Copy")) {
            Haptic.tap(view)
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("agent", text))
        }
        SmallAction(null, tr("↪ Mandar para…", "↪ Send to…"), onRelay)
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = label, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = KeypadColors.TextMute)
        Text(label, style = GroupType.Small.copy(fontWeight = FontWeight.Medium), color = KeypadColors.TextMute)
    }
}

/**
 * "Mandar para…": what the agent said, the agent it goes to (the others in herdr, the same project
 * first), a word from the person if they want, and one button that says where it goes.
 */
@Composable
internal fun RelaySheet(vm: KeypadViewModel, from: Agent, text: String, agents: List<Agent>, onDismiss: () -> Unit) {
    val others = remember(agents, from) {
        agents.filter { it.id != from.id && it.kind.isNotBlank() }.sortedWith(compareByDescending<Agent> { it.cwd == from.cwd }.thenByDescending { it.active })
    }
    var to by rememberSaveable { mutableStateOf(others.firstOrNull { it.cwd == from.cwd }?.id) }
    var note by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val view = LocalView.current
    AppSheet(tr("Mandar para…", "Send to…"), onDismiss, subtitle = tr("Chega como mensagem, dizendo de onde veio", "It arrives as a message, saying where it came from")) {
        // What goes, quoted: a bar on the left like a reply.
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KeypadColors.Surface1).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(3.dp).height(54.dp).clip(RoundedCornerShape(2.dp)).background(accountColor(from.account) ?: KeypadColors.Line2))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(agentTitle(from), style = GroupType.Small.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text.trim(), style = GroupType.Sub, color = KeypadColors.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (others.isEmpty()) {
            Text(tr("Nenhum outro agente aberto no herdr.", "No other agent open in herdr."), style = GroupType.Lead, color = KeypadColors.TextDim)
        } else {
            Group {
                others.forEachIndexed { i, a ->
                    if (i > 0) GroupDivider()
                    val on = to == a.id
                    GroupRow(agentTitle(a), origin(a), { Haptic.tap(view); to = a.id }, minHeight = 56.dp, account = a.account, trailing = {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(if (on) KeypadColors.Accent else KeypadColors.Surface3),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Glyph.Check, null, Modifier.size(13.dp), tint = KeypadColors.OnAccent) }
                    })
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(KeypadColors.Surface1).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(note, { note = it.take(2000) }, Modifier.fillMaxWidth().semantics { contentDescription = tr("Comentário", "Comment") },
                textStyle = GroupType.Lead.copy(color = KeypadColors.Text), cursorBrush = SolidColor(KeypadColors.Text), maxLines = 4,
                decorationBox = { field ->
                    if (note.isEmpty()) Text(tr("Adicionar um comentário (opcional)", "Add a comment (optional)"), style = GroupType.Lead, color = KeypadColors.TextMute)
                    field()
                })
        }
        val target = others.firstOrNull { it.id == to }
        val name = target?.let { AgentsText.kindName(it.kind) } ?: ""
        PrimaryButton(if (target == null) tr("Escolha um agente", "Pick an agent") else tr("Mandar ao $name", "Send to $name"), enabled = target != null && !sending, busy = sending) {
            val t = target ?: return@PrimaryButton
            sending = true
            vm.relayAgent(from.id, t.id, text, note, name) { ok -> if (ok) onDismiss() else sending = false }
        }
    }
}

/** The one strong button of a sheet: full width, the accent, a spinner while it works. */
@Composable
internal fun PrimaryButton(label: String, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) KeypadColors.Accent else KeypadColors.Surface3)
            .clickable(enabled = enabled, onClickLabel = label) { Haptic.tap(view); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = KeypadColors.OnAccent, strokeWidth = 2.dp)
        else Text(label, style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = if (enabled) KeypadColors.OnAccent else KeypadColors.TextMute)
    }
}

/**
 * Over the composer: the review this agent asked for, while it runs ("O Codex está revisando") and
 * when it's back — the reviewer's answer, to send to this agent in one tap, or open where it came from.
 */
@Composable
internal fun LinkCards(vm: KeypadViewModel, agent: Agent, links: AgentLinks, agents: List<Agent>, onOpenAgent: (String) -> Unit) {
    val view = LocalView.current
    val reviewing = links.reviewing(agent.id)
    val notices = links.noticesFor(agent.id)
    if (reviewing == null && notices.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (reviewing != null) {
            val name = agents.firstOrNull { it.id == reviewing.to }?.let { AgentsText.kindName(it.kind) } ?: tr("O agente", "The agent")
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(KeypadColors.Surface1)
                    .clickable(onClickLabel = tr("Abrir a revisão", "Open the review")) { Haptic.tap(view); onOpenAgent(reviewing.to) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), color = KeypadColors.TextDim, strokeWidth = 1.5.dp)
                Text(tr("$name está revisando as mudanças", "$name is reviewing the changes"), Modifier.weight(1f), style = GroupType.Sub, color = KeypadColors.Text, maxLines = 1)
                Text(tr("Ver", "View"), style = GroupType.Sub.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.TextDim)
            }
        }
        for (notice in notices) ReviewNoticeCard(vm, agent, notice, onOpenAgent)
    }
}

@Composable
private fun ReviewNoticeCard(vm: KeypadViewModel, agent: Agent, notice: AgentLinks.Notice, onOpenAgent: (String) -> Unit) {
    val view = LocalView.current
    var open by remember(notice.id) { mutableStateOf(false) }
    var sending by remember(notice.id) { mutableStateOf(false) }
    val reviewer = AgentsText.kindName(notice.fromKind)
    val me = AgentsText.kindName(agent.kind)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KeypadColors.Surface1).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Revisão do $reviewer", "$reviewer's review"), Modifier.weight(1f), style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text)
            Text(ago((System.currentTimeMillis() / 1000 - notice.ts).toInt().coerceAtLeast(0)), style = GroupType.Small, color = KeypadColors.TextMute)
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = tr("Dispensar", "Dismiss")) { Haptic.tap(view); vm.dismissNotice(notice.id) },
                contentAlignment = Alignment.Center,
            ) { Icon(Glyph.Close, null, Modifier.size(13.dp), tint = KeypadColors.TextMute) }
        }
        Column(Modifier.padding(end = 8.dp).clickable(onClickLabel = if (open) tr("Recolher", "Collapse") else tr("Ver tudo", "See all")) { open = !open }) {
            if (notice.text.isBlank()) Text(tr("O $reviewer terminou sem uma resposta escrita.", "$reviewer finished without a written answer."), style = GroupType.Sub, color = KeypadColors.TextDim)
            else if (open) MarkdownText(notice.text)
            else Text(notice.text.trim(), style = GroupType.Sub, color = KeypadColors.Text, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (notice.text.isNotBlank()) Box(Modifier.weight(1f)) {
                PrimaryButton(tr("Mandar ao $me", "Send to $me"), enabled = !sending, busy = sending) {
                    sending = true
                    vm.relayAgent(notice.from, agent.id, notice.text, null, me) { ok -> if (ok) vm.dismissNotice(notice.id) else sending = false }
                }
            }
            Box(
                Modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(KeypadColors.Surface3)
                    .clickable(onClickLabel = tr("Abrir a conversa do $reviewer", "Open $reviewer's chat")) { Haptic.tap(view); onOpenAgent(notice.from) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text(tr("Abrir", "Open"), style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text) }
        }
    }
}

/**
 * A message that came from another agent: the person's bubble, with where it came from above it —
 * a tap opens that agent. The header line itself isn't repeated.
 */
@Composable
internal fun RelayedBubble(header: RelayHeader, onOpen: (() -> Unit)?) {
    val view = LocalView.current
    val name = AgentsText.kindName(header.kind)
    val caption = when (header.what) {
        "review" -> tr("$name pediu uma revisão · ${header.project}", "$name asked for a review · ${header.project}")
        "ask" -> tr("$name perguntou · ${header.project}", "$name asked · ${header.project}")
        else -> tr("Veio do $name · ${header.project}", "From $name · ${header.project}")
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).then(if (onOpen != null) Modifier.clickable(onClickLabel = tr("Abrir o $name", "Open $name")) { Haptic.tap(view); onOpen() } else Modifier)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("↪", style = GroupType.Small, color = KeypadColors.TextMute)
            Text(caption, style = GroupType.Small.copy(fontWeight = FontWeight.Medium), color = KeypadColors.TextMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onOpen != null) Icon(Glyph.ChevronRight, null, Modifier.size(11.dp), tint = KeypadColors.TextMute)
        }
        Text(header.body, Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp)).background(KeypadColors.Surface3).padding(horizontal = 14.dp, vertical = 10.dp),
            style = GroupType.Chat.copy(fontSize = GroupType.Chat.fontSize * 0.95f, lineHeight = 21.sp), color = KeypadColors.Text, maxLines = 14, overflow = TextOverflow.Ellipsis)
    }
}

/** "↔ Codex": who an agent is exchanging with right now, for the agents list. */
internal fun talkLabel(links: AgentLinks, agents: List<Agent>, agent: Agent): String? {
    val names = links.talkingWith(agent.id).map { id -> agents.firstOrNull { it.id == id }?.let { AgentsText.kindName(it.kind) } ?: "Terminal" }
    return if (names.isEmpty()) null else "↔ " + names.distinct().joinToString(", ")
}
