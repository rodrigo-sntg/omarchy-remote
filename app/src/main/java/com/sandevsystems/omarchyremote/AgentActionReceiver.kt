package com.sandevsystems.omarchyremote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.launch

/** An agent answered from its notification: a key (Permitir/Negar) or a reply, sent without opening the app. */
class AgentActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as KeypadApp
        val id = intent.getStringExtra(AGENT) ?: return
        val key = intent.getStringExtra(KEY)
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REPLY)?.toString()?.trim()
        val pending = goAsync()
        app.scope.launch {
            try {
                app.answerAgent(id, key, if (intent.action == ACTION_REPLY) reply else null, intent.getStringExtra(DONE))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_KEYS = "com.sandevsystems.omarchyremote.AGENT_KEYS"
        const val ACTION_REPLY = "com.sandevsystems.omarchyremote.AGENT_REPLY"
        const val AGENT = "agent"
        const val KEY = "key"
        const val DONE = "done"
        const val REPLY = "reply"
    }
}
