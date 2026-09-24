package com.sandevsystems.omarchyremote

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sandevsystems.omarchyremote.network.MirrorRules

/**
 * The phone's notifications on the PC (opt-in, Settings › Notifications): each new one goes to the PC
 * while connected; the PC's Reply answers through the notification's own reply action, and its
 * Dismiss clears it here. Android asks the person for "notification access" before this runs.
 */
class NotificationMirror : NotificationListenerService() {
    private val app get() = application as KeypadApp
    private val sentText = mutableMapOf<String, Int>()
    private val sent = LinkedHashMap<String, StatusBarNotification>()

    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { if (instance === this) instance = null }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!app.prefs.getBoolean(PREF, false) || app.network.state.value !is ConnectionState.Connected) return
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val c = MirrorRules.Candidate(sbn.packageName, sbn.isOngoing, n.flags and Notification.FLAG_GROUP_SUMMARY != 0, n.category, title, text)
        remember(sbn.packageName)
        if (!MirrorRules.send(c, packageName, excluded(app)) || !MirrorRules.changed(sbn.key, c, sentText)) return
        sent[sbn.key] = sbn
        if (sent.size > 100) sent.keys.firstOrNull()?.let { sent.remove(it) }
        app.network.phoneNotification(sbn.key, label(sbn.packageName), title, text, replyAction(n) != null)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        sentText.remove(sbn.key)
        if (sent.remove(sbn.key) != null && app.network.state.value is ConnectionState.Connected) app.network.phoneNotificationRemoved(sbn.key)
    }

    /** Answers a notification with the text typed on the PC, as if typed in its own reply field. */
    fun reply(key: String, text: String): Boolean {
        val sbn = allowed(key) ?: return false
        val action = replyAction(sbn.notification) ?: return false
        val results = Bundle().apply { action.remoteInputs.forEach { putCharSequence(it.resultKey, text) } }
        val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(action.remoteInputs, intent, results)
        return runCatching { action.actionIntent.send(this, 0, intent); true }.getOrDefault(false)
    }

    fun dismiss(key: String) {
        if (allowed(key) != null) runCatching { cancelNotification(key) }
    }

    /** Only notifications this phone forwarded, with mirroring on and their app not turned off. */
    private fun allowed(key: String): StatusBarNotification? {
        val sbn = sent[key] ?: return null
        return sbn.takeIf { MirrorRules.mayAnswer(key, sent.keys, app.prefs.getBoolean(PREF, false), it.packageName, excluded(app)) }
    }

    private fun replyAction(n: Notification): Notification.Action? =
        n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }

    private fun label(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** The apps that notified, for the choice of which go to the PC. */
    private fun remember(pkg: String) {
        val seen = app.prefs.getStringSet(SEEN, emptySet())!!
        if (pkg != packageName && pkg !in seen) app.prefs.edit().putStringSet(SEEN, seen + pkg).apply()
    }

    companion object {
        const val PREF = "phone_notifications"
        const val SEEN = "mirror_seen"
        const val EXCLUDED = "mirror_excluded"
        @Volatile var instance: NotificationMirror? = null
        fun excluded(app: KeypadApp): Set<String> = app.prefs.getStringSet(EXCLUDED, emptySet())!!
    }
}
