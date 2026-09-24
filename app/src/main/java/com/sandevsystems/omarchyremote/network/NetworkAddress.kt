package com.sandevsystems.omarchyremote.network

/** Host address typed by the user: a Tailscale MagicDNS name, optionally with a port. */
object NetworkAddress {
    const val DEFAULT_PORT = 8765
    private val pattern = Regex("""^([a-z0-9-]+(\.[a-z0-9-]+)*\.ts\.net)(:(\d{1,5}))?$""")
    private val loopback = Regex("""^localhost(:(\d{1,5}))?$""")

    /**
     * "localhost" for testing a phone without Tailscale through `adb reverse` against the host's
     * --dev-loopback mode. Set only by debuggable builds (KeypadApp); release builds never accept it.
     */
    var loopbackAllowed = false

    /** ws:// URL, or null when the address is not a *.ts.net name (the only cleartext domain allowed). */
    fun url(input: String, allowLoopback: Boolean = loopbackAllowed): String? {
        val text = input.trim().lowercase()
        if (allowLoopback) {
            loopback.matchEntire(text)?.let { match ->
                val port = match.groupValues[2].ifEmpty { "$DEFAULT_PORT" }.toInt()
                return if (port in 1..65535) "ws://127.0.0.1:$port/v1" else null
            }
        }
        val match = pattern.matchEntire(text) ?: return null
        val port = match.groupValues[4].ifEmpty { "$DEFAULT_PORT" }.toInt()
        if (port !in 1..65535) return null
        return "ws://${match.groupValues[1]}:$port/v1"
    }
}
