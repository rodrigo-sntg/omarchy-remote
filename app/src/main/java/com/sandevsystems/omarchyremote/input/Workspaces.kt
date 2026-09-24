package com.sandevsystems.omarchyremote.input

import com.sandevsystems.omarchyremote.input.ModifierKeys.SHIFT
import com.sandevsystems.omarchyremote.input.ModifierKeys.SUPER

/**
 * Omarchy's default workspace shortcuts (default/hypr/bindings/tiling.lua), sent as ordinary keys
 * so the user's own Hyprland binds decide what happens.
 */
object Workspaces {
    private const val TAB = 0x2b

    val numbers = (1..10).toList()

    /** SUPER + digit-row key; workspace 10 is the 0 key. */
    fun switchTo(number: Int) = KeyStroke(if (number == 10) 0x27 else 0x1e + number - 1, SUPER)

    val next = KeyStroke(TAB, SUPER)
    val previous = KeyStroke(TAB, SUPER or SHIFT)
}
