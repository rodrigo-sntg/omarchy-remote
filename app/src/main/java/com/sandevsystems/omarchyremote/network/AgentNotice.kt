package com.sandevsystems.omarchyremote.network

import com.sandevsystems.omarchyremote.ui.tr

/**
 * What an agent's notification says, from its screen: the command a permission request is about
 * (with the keys that allow or deny it), or the start of a finished agent's reply. [replyAfter]:
 * the key that refuses so a reply can say what to do instead.
 */
data class AgentNotice(val title: String, val body: String, val allow: String?, val deny: String?, val replyAfter: String?) {
    companion object {
        private const val BODY = 240

        fun of(agent: Agent, chat: AgentConversation.Chat?): AgentNotice {
            val title = AgentsText.alertTitle(agent)
            val where = AgentsText.alertBody(agent)
            val ask = chat?.ask?.takeIf { agent.status == "blocked" }
            if (ask != null) {
                val what = ask.detail.lineSequence().firstOrNull()?.ifBlank { null } ?: ask.title
                val yes = ask.options.firstOrNull { it.choice == AgentConversation.Choice.YES }?.key
                val no = ask.options.firstOrNull { it.choice == AgentConversation.Choice.NO }?.key
                val body = listOf(agent.title, tr("quer rodar: ", "wants to run: ") + what).filter { it.isNotBlank() }.joinToString(" · ")
                return AgentNotice(title, body.take(BODY), yes, no, no)
            }
            val said = (chat?.items?.lastOrNull { it is AgentConversation.Said } as? AgentConversation.Said)?.text
            if (agent.status == "done" && !said.isNullOrBlank()) return AgentNotice(title, said.take(BODY), null, null, null)
            return AgentNotice(title, where, null, null, null)
        }
    }
}
