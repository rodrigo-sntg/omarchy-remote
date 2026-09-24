package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.NetworkSession

/**
 * The shared clipboard's memory: which text both sides already have, so a copy crosses once and
 * never bounces back, and when the phone's clipboard last changed (reading it when it did not
 * would only make Android announce "pasted from your clipboard" for nothing).
 */
class ClipSync(
    private val limit: Int = NetworkSession.CLIPBOARD_SET_LIMIT,
    /** The copy last looked at, kept across restarts: an install must not send it again. */
    lastLooked: Long = Long.MIN_VALUE,
    private val onLooked: (Long) -> Unit = {},
) {
    private var shared: String? = null
    private var stamp = lastLooked

    /** A copy made on the PC: true when the phone's clipboard should get it. */
    @Synchronized
    fun fromPc(text: String): Boolean {
        if (text.isEmpty() || text == shared) return false
        shared = text
        return true
    }

    /** The phone's clipboard text: true when the PC should get it. */
    @Synchronized
    fun toPc(text: String): Boolean = text.isNotBlank() && text.length <= limit && text != shared

    /** The PC has it now. */
    @Synchronized
    fun sent(text: String) {
        shared = text
    }

    /** The phone's clipboard changed (by its timestamp) since it was last looked at. */
    @Synchronized
    fun changed(timestamp: Long): Boolean = timestamp != stamp

    @Synchronized
    fun looked(timestamp: Long) {
        if (timestamp != stamp) onLooked(timestamp)
        stamp = timestamp
    }
}
