package com.sandevsystems.omarchyremote.input

/** The phone's volume buttons while connected: the PC's volume, or slides in presentation mode. */
object VolumeKeys {
    enum class Target { VolumeUp, VolumeDown, NextSlide, PreviousSlide }

    private const val VOLUME_UP = 24  // KeyEvent.KEYCODE_VOLUME_UP
    private const val VOLUME_DOWN = 25  // KeyEvent.KEYCODE_VOLUME_DOWN

    /** Null: the phone keeps the button (its own volume). Down = next, like a presenter clicker. */
    fun target(keyCode: Int, connected: Boolean, enabled: Boolean, presenting: Boolean): Target? {
        if (!connected || keyCode != VOLUME_UP && keyCode != VOLUME_DOWN) return null
        val up = keyCode == VOLUME_UP
        return when {
            presenting -> if (up) Target.PreviousSlide else Target.NextSlide
            enabled -> if (up) Target.VolumeUp else Target.VolumeDown
            else -> null
        }
    }
}
