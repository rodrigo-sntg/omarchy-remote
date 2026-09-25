package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.KeypadApp.ClipSend
import com.sandevsystems.omarchyremote.ui.tr

/**
 * "Colar" on the phone: the phone's copy goes to the PC first, then the PC pastes (Omarchy's
 * Super+V, which works in terminals too). Nothing new on the phone: the PC pastes what it has.
 * A copy that didn't arrive never pastes something older in its place.
 */
object PcClipboard {
    data class Outcome(val paste: Boolean, val message: String? = null)

    fun afterSend(result: ClipSend): Outcome = when (result) {
        ClipSend.SENT, ClipSend.NOTHING, ClipSend.UNREADABLE -> Outcome(paste = true)
        ClipSend.TOO_LONG -> Outcome(false, tr("Texto grande demais para colar no PC (12 mil caracteres).", "Too long to paste on the PC (12,000 characters max)."))
        ClipSend.OFFLINE -> Outcome(false, tr("Conecte-se pela rede para colar no PC.", "Connect over the network to paste on the PC."))
        ClipSend.FAILED -> Outcome(false, tr("O PC não recebeu o texto.", "The PC didn't get the text."))
    }
}
