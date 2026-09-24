package com.sandevsystems.omarchyremote.network

/**
 * What another app may hand to Omarchy Remote with "Share": only content:// from other apps (never a
 * file:// path, which could point at this app's own private files), and never without the person
 * confirming what goes to the PC.
 */
object ShareRules {
    fun accepts(scheme: String?, authority: String?, ownPackage: String): Boolean =
        scheme == "content" && !authority.isNullOrBlank() && !authority.startsWith(ownPackage)

    fun preview(text: String): String = if (text.length <= 200) text else text.take(200) + "…"
}
