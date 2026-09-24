package com.sandevsystems.omarchyremote.input

import java.net.URI

/**
 * Where the terminal's WebView may go. It carries the `Android` bridge, which types into the PC,
 * so it never leaves the bundled page; web links printed by programs open in the phone's browser.
 */
object TermLinks {
    private const val PAGE_DIR = "/android_asset/term/"

    fun staysInPage(url: String): Boolean {
        val uri = runCatching { URI(url).normalize() }.getOrNull() ?: return false
        return uri.scheme == "file" && uri.path?.startsWith(PAGE_DIR) == true && !uri.path.contains("..")
    }

    /** The URL to hand to the browser, or null when it is not a plain web link. */
    fun external(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        return url.takeIf { uri.scheme == "http" || uri.scheme == "https" }
    }
}
