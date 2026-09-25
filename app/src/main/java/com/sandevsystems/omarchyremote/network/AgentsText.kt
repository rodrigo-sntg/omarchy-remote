package com.sandevsystems.omarchyremote.network

import androidx.compose.runtime.mutableStateMapOf
import com.sandevsystems.omarchyremote.ui.tr

/**
 * The finished agents the person already opened on the phone. herdr keeps "done" until the pane is
 * looked at on the PC; read here, it no longer needs them. Keyed by herdr's state_change_seq, so
 * finishing again (a new seq) needs them again. Compose state: screens update the moment it changes.
 */
object SeenAgents {
    private val seen = mutableStateMapOf<String, Int>()

    /** Called with the whole list, encoded, whenever it changes (the app keeps it across restarts). */
    var onChange: (String) -> Unit = {}

    fun saw(agent: Agent) {
        if (agent.status != "done" || seen[agent.id] == agent.seq) return
        seen[agent.id] = agent.seq
        // Only the last few matter: panes come and go.
        while (seen.size > 50) seen.remove(seen.keys.first())
        onChange(seen.entries.joinToString(";") { "${it.key}=${it.value}" })
    }

    fun load(saved: String?) {
        for (pair in saved.orEmpty().split(";")) {
            val (id, seq) = pair.split("=").takeIf { it.size == 2 } ?: continue
            seq.toIntOrNull()?.let { if (id.isNotBlank()) seen[id] = it }
        }
    }

    fun seen(agent: Agent): Boolean = seen[agent.id] == agent.seq

    fun clear() = seen.clear()
}

/** Plain words for agents: the service notification, the alert notification. */
object AgentsText {
    /** Asking for permission, or finished with an answer not read yet (SeenAgents). */
    fun needsYou(agent: Agent): Boolean = agent.status == "blocked" || (agent.status == "done" && !SeenAgents.seen(agent))

    fun status(host: String, agents: List<Agent>): String {
        val waiting = agents.count(::needsYou)
        return buildString {
            append(tr("Ligado a ", "Connected to ")).append(host)
            if (agents.isNotEmpty()) append(" · ").append(agents.size).append(if (agents.size == 1) tr(" agente", " agent") else tr(" agentes", " agents"))
            if (waiting > 0) append(" · ").append(waiting).append(tr(" esperando você", " waiting for you"))
        }
    }

    /** Most recently active first; those without a time after them, the latest state change first. */
    fun byActivity(agents: List<Agent>): List<Agent> = agents.sortedWith(compareByDescending<Agent> { it.active }.thenByDescending { it.seq })

    fun kindName(kind: String) = when (kind) {
        "claude" -> "Claude"
        "codex" -> "Codex"
        "" -> tr("Agente", "Agent")
        else -> kind.replaceFirstChar { it.uppercase() }
    }

    fun alertTitle(agent: Agent) = kindName(agent.kind) + if (agent.status == "done") tr(" terminou", " is done") else tr(" precisa de você", " needs you")

    fun alertBody(agent: Agent) = listOf(agent.title, agent.cwd).filter { it.isNotBlank() }.joinToString(" · ")

    /** The home screen widget: the headline, then the detail line. */
    fun widget(connected: Boolean, agents: List<Agent>): Pair<String, String> {
        if (!connected) return tr("Desconectado", "Disconnected") to "Omarchy Remote"
        if (agents.isEmpty()) return tr("Sem agentes", "No agents") to "herdr"
        val waiting = agents.count(::needsYou)
        val working = agents.count { it.status == "working" }
        val headline = when {
            waiting > 0 -> tr("$waiting esperando você", "$waiting waiting for you")
            working > 0 -> tr("$working trabalhando", "$working working")
            else -> tr("Todos parados", "All idle")
        }
        return headline to if (agents.size == 1) tr("1 agente no herdr", "1 agent in herdr") else tr("${agents.size} agentes no herdr", "${agents.size} agents in herdr")
    }

    /** The sheet's sections, who needs the person first; empty sections are left out. */
    fun groups(all: List<Agent>): List<Pair<String, List<Agent>>> = byActivity(all).let { agents -> listOf(
        tr("Precisam de você", "Need you") to agents.filter { it.status == "blocked" }.plus(agents.filter { it.status == "done" && needsYou(it) }),
        tr("Trabalhando", "Working") to agents.filter { it.status == "working" },
        tr("Parados", "Idle") to agents.filter { !needsYou(it) && it.status != "working" },
    ).filter { it.second.isNotEmpty() } }
}
