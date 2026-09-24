package com.sandevsystems.omarchyremote.network

import com.sandevsystems.omarchyremote.ui.tr

/** Plain words for agents: the service notification, the alert notification. */
object AgentsText {
    fun status(host: String, agents: List<Agent>): String {
        val waiting = agents.count { it.status == "blocked" || it.status == "done" }
        return buildString {
            append(tr("Ligado a ", "Connected to ")).append(host)
            if (agents.isNotEmpty()) append(" · ").append(agents.size).append(if (agents.size == 1) tr(" agente", " agent") else tr(" agentes", " agents"))
            if (waiting > 0) append(" · ").append(waiting).append(tr(" esperando você", " waiting for you"))
        }
    }

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
        val waiting = agents.count { it.status == "blocked" || it.status == "done" }
        val working = agents.count { it.status == "working" }
        val headline = when {
            waiting > 0 -> tr("$waiting esperando você", "$waiting waiting for you")
            working > 0 -> tr("$working trabalhando", "$working working")
            else -> tr("Todos parados", "All idle")
        }
        return headline to if (agents.size == 1) tr("1 agente no herdr", "1 agent in herdr") else tr("${agents.size} agentes no herdr", "${agents.size} agents in herdr")
    }

    /** The sheet's sections, who needs the person first; empty sections are left out. */
    fun groups(agents: List<Agent>): List<Pair<String, List<Agent>>> = listOf(
        tr("Precisam de você", "Need you") to agents.filter { it.status == "blocked" }.plus(agents.filter { it.status == "done" }),
        tr("Trabalhando", "Working") to agents.filter { it.status == "working" },
        tr("Parados", "Idle") to agents.filter { it.status != "blocked" && it.status != "done" && it.status != "working" },
    ).filter { it.second.isNotEmpty() }
}
