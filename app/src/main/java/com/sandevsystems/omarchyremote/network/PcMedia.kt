package com.sandevsystems.omarchyremote.network

import com.sandevsystems.omarchyremote.ui.tr

/** What plays on the PC (host media.py), for the phone's lock screen player. */
data class PcMedia(val playing: Boolean, val title: String, val artist: String, val player: String) {
    fun show(connected: Boolean, enabled: Boolean) = connected && enabled && title.isNotBlank()

    /** "No PC · Spotify": the player's name without its instance suffix. */
    fun where(): String {
        val name = player.substringBefore('.').replaceFirstChar { it.uppercase() }
        return tr("No PC", "On the PC") + if (name.isNotBlank()) " · $name" else ""
    }
}
