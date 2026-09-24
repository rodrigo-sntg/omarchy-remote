package com.sandevsystems.omarchyremote.ui

/** An agent's state in color and in words (the list, the detail, the notifications' wording). */
internal fun statusColor(status: String) = when (status) {
    "working" -> KeypadColors.Ok
    "done" -> KeypadColors.Attention
    "blocked" -> KeypadColors.Attention
    else -> KeypadColors.TextMute
}

internal fun statusWord(status: String) = when (status) {
    "working" -> tr("trabalhando", "working")
    "blocked" -> tr("precisa de você", "needs you")
    "done" -> tr("terminou", "done")
    "idle" -> tr("parado", "idle")
    else -> tr("sem estado", "no status")
}
