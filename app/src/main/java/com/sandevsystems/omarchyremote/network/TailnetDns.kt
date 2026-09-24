package com.sandevsystems.omarchyremote.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Resolves the PC's name but keeps only Tailscale addresses (100.64.0.0/10, fd7a:115c:a1e0::/48).
 * The pairing code travels without TLS because the Tailscale tunnel encrypts it; with Tailscale off,
 * "*.ts.net" would be asked to the Wi-Fi's DNS, and a hostile network could answer with its own
 * machine and collect the code. So any other answer is refused before a byte is sent.
 */
class TailnetDns(
    private val loopback: Boolean = NetworkAddress.loopbackAllowed,
    private val system: (String) -> List<InetAddress> = { Dns.SYSTEM.lookup(it) },
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val found = system(hostname).filter { isTailnet(it, loopback) }
        if (found.isEmpty()) throw UnknownHostException("$hostname is not on your Tailscale network (is Tailscale on?)")
        return found
    }

    companion object {
        fun isTailnet(address: InetAddress, loopback: Boolean): Boolean {
            val b = address.address
            return when (address) {
                is Inet4Address -> (b[0].toInt() and 0xff) == 100 && (b[1].toInt() and 0xc0) == 64 ||
                    loopback && address.isLoopbackAddress
                is Inet6Address -> (b[0].toInt() and 0xff) == 0xfd && (b[1].toInt() and 0xff) == 0x7a && (b[2].toInt() and 0xff) == 0x11 &&
                    (b[3].toInt() and 0xff) == 0x5c && (b[4].toInt() and 0xff) == 0xa1 && (b[5].toInt() and 0xff) == 0xe0 ||
                    loopback && address.isLoopbackAddress
                else -> false
            }
        }

        /** Every client that talks to the PC goes through this. */
        fun client(): OkHttpClient.Builder = OkHttpClient.Builder().dns(TailnetDns())
    }
}
