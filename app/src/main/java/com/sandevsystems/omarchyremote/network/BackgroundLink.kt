package com.sandevsystems.omarchyremote.network

import android.content.SharedPreferences
import android.os.SystemClock
import com.sandevsystems.omarchyremote.ConnectionState.Connected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the network session alive while "Acompanhar agentes" is on, with or without an Activity:
 * one reconnection attempt every [RETRY_MS] while disconnected, using the saved address and code.
 */
class BackgroundLink(
    private val scope: CoroutineScope,
    private val network: NetworkController,
    private val prefs: SharedPreferences,
    private val pairingCode: () -> String,
) {
    private var job: Job? = null

    /** Set by an explicit "Desconectar": no automatic reconnection until the person connects again. */
    var held = false

    val running get() = job?.isActive == true

    fun start() {
        if (running) return
        job = scope.launch {
            var lastTry = 0L
            while (true) {
                val now = SystemClock.elapsedRealtime()
                if (reconnectDue(network.state.value is Connected, lastTry, now, held)) {
                    lastTry = now
                    val address = prefs.getString("network_address", null)
                    if (!address.isNullOrBlank() && overNetwork(prefs.getString("transport", null))) {
                        network.connect(address, pairingCode())
                    }
                }
                delay(CHECK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        const val RETRY_MS = 30_000L
        const val CHECK_MS = 5_000L

        /** Wait before foreground retry number [attempt] (0-based) after the PC dropped the session. */
        fun retryDelayMs(attempt: Int): Long = minOf(2_000L shl minOf(attempt, 10), RETRY_MS)

        /** The saved transport means the network; nothing saved yet (a fresh pairing) too, as on the screen. */
        fun overNetwork(saved: String?): Boolean = saved == null || saved == "NETWORK"

        fun reconnectDue(connected: Boolean, lastTryMs: Long, nowMs: Long, held: Boolean = false): Boolean =
            !held && !connected && (lastTryMs == 0L || nowMs - lastTryMs >= RETRY_MS)
    }
}

/** The persistent notification's text: posted only when it changes (the agent list is re-sent often). */
class StatusText {
    private var last: String? = null

    fun offer(text: String): Boolean {
        if (text == last) return false
        last = text
        return true
    }

    fun reset() {
        last = null
    }
}
