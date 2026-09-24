package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.PcNotification
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.sandevsystems.omarchyremote.network.AgentNotice
import androidx.core.content.ContextCompat
import com.sandevsystems.omarchyremote.network.Agent
import com.sandevsystems.omarchyremote.network.AgentsText
import com.sandevsystems.omarchyremote.ui.tr

/** One notification per agent that needs the person (blocked) or finished (done); tapping opens it. */
class AgentNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, tr("Agentes do herdr", "herdr agents"), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = tr("Um agente travou pedindo aprovação ou terminou.", "An agent is waiting for approval or has finished.")
                },
            )
        }
    }

    /**
     * An agent that needs the person or finished. From its screen ([notice]): a permission request
     * gets Permitir / Negar / Responder; a finished agent, the start of its reply and Responder.
     * Every answer asks to unlock the phone first (someone holding a locked phone approves nothing),
     * and on the lock screen only "an agent needs you" shows, not the command or the reply.
     */
    fun notify(agent: Agent, notice: AgentNotice = AgentNotice.of(agent, null)) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, agent.id.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_AGENT, agent.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_keypad)
            .setContentTitle(notice.title)
            .setContentText(notice.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_keypad)
                    .setContentTitle(tr("Um agente precisa de você", "An agent needs you")).build(),
            )
        notice.allow?.let { builder.addAction(locked(tr("Permitir", "Allow"), keysIntent(agent.id, it, tr("Permitido", "Allowed")))) }
        notice.deny?.let { builder.addAction(locked(tr("Negar", "Deny"), keysIntent(agent.id, it, tr("Negado", "Denied")))) }
        if (agent.status == "done" || notice.replyAfter != null) {
            val input = RemoteInput.Builder(AgentActionReceiver.REPLY).setLabel(tr("Responder ao ${AgentsText.kindName(agent.kind)}", "Reply to ${AgentsText.kindName(agent.kind)}")).build()
            val intent = Intent(context, AgentActionReceiver::class.java).setAction(AgentActionReceiver.ACTION_REPLY)
                .putExtra(AgentActionReceiver.AGENT, agent.id).putExtra(AgentActionReceiver.KEY, notice.replyAfter)
            val pending = PendingIntent.getBroadcast(context, ("reply" + agent.id).hashCode(), intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(NotificationCompat.Action.Builder(0, tr("Responder", "Reply"), pending).addRemoteInput(input)
                .setAllowGeneratedReplies(false).setAuthenticationRequired(true).build())
        }
        manager.notify(agent.id.hashCode(), builder.build())
    }

    /** An action that asks to unlock the phone before it runs (Android 12+; older ones: the receiver checks). */
    private fun locked(label: String, intent: PendingIntent) =
        NotificationCompat.Action.Builder(0, label, intent).setAuthenticationRequired(true).build()

    private fun keysIntent(id: String, key: String, done: String): PendingIntent {
        val intent = Intent(context, AgentActionReceiver::class.java).setAction(AgentActionReceiver.ACTION_KEYS)
            .putExtra(AgentActionReceiver.AGENT, id).putExtra(AgentActionReceiver.KEY, key).putExtra(AgentActionReceiver.DONE, done)
        return PendingIntent.getBroadcast(context, (id + key).hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** The answer went (or did not): the agent's notification says so, quietly, and goes away. */
    fun answered(id: String, text: String) {
        manager.notify(id.hashCode(), NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_keypad).setContentTitle(text).setSilent(true).setAutoCancel(true)
            .setTimeoutAfter(4_000).build())
    }

    /**
     * The PC asked to open something. With the app on screen it opens at once; from the background
     * Android does not let an app start an activity, so a notification carries the request.
     */
    fun open(what: String) {
        val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, what)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (MainActivity.visible) {
            context.startActivity(intent)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val label = when (what) {
            "screen" -> tr("Ver a tela do PC", "View the PC screen")
            "extra" -> tr("Usar como tela extra", "Use as an extra screen")
            else -> tr("Abrir o herdr", "Open herdr")
        }
        val pending = PendingIntent.getActivity(context, what.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(OPEN_ID, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_keypad).setContentTitle(tr("Pedido do PC", "Request from the PC")).setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pending).build())
    }

    /** A notification from the PC, in its own channel so it can be silenced apart from the agents. */
    fun pc(n: PcNotification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (manager.getNotificationChannel(PC_CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(PC_CHANNEL, tr("Avisos do PC", "PC notifications"), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val title = n.title.ifBlank { n.app.ifBlank { "PC" } }
        manager.notify(PC_BASE + (pcCount++ % 50), NotificationCompat.Builder(context, PC_CHANNEL)
            .setSmallIcon(R.drawable.ic_keypad).setContentTitle(title).setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setSubText(n.app.takeIf { it.isNotBlank() && it != "omarchy-action" } ?: "PC")
            .setGroup(PC_CHANNEL).setAutoCancel(true).setContentIntent(open).build())
    }

    private var pcCount = 0

    companion object {
        private const val OPEN_ID = 2
        private const val PC_BASE = 7_000
        const val PC_CHANNEL = "avisos-pc"
        const val CHANNEL = "agentes"
    }
}
