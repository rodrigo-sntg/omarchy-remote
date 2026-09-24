package com.sandevsystems.omarchyremote.network

import java.net.URI
import java.net.URLDecoder

/**
 * The pairing QR from Omarchy's Phone menu (`omarchy-remote pair`):
 * keypad://pair?host=<pc>.<tailnet>.ts.net&code=<pairing code>. Anything else is refused.
 */
object PairUri {
    private val code = Regex("[A-Z2-7]{4}(-[A-Z2-7]{4}){4}")

    fun parse(text: String, allowLoopback: Boolean = NetworkAddress.loopbackAllowed): Pair<String, String>? {
        val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "keypad" || uri.host != "pair") return null
        val query = (uri.rawQuery ?: return null).split("&").mapNotNull {
            val (key, value) = it.split("=", limit = 2).takeIf { parts -> parts.size == 2 } ?: return@mapNotNull null
            key to URLDecoder.decode(value, "UTF-8")
        }.toMap()
        val host = query["host"] ?: return null
        val pairing = query["code"]?.takeIf { code.matches(it) } ?: return null
        if (NetworkAddress.url(host, allowLoopback) == null) return null
        return host to pairing
    }
}
