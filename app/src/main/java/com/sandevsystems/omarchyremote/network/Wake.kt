package com.sandevsystems.omarchyremote.network

import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Wake-on-LAN: the PC tells the phone (host.get) each network card's MAC and addresses; with the PC
 * asleep or off, the phone sends the magic packet every way that may reach it on the home network.
 */
object Wake {
    data class Target(val mac: String, val address: String, val broadcast: String, val enabled: Boolean)
    data class Send(val mac: String, val host: String, val port: Int)

    private val macRe = Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$")

    fun targets(json: JSONArray?): List<Target> {
        if (json == null) return emptyList()
        return (0 until json.length()).mapNotNull { i ->
            val o = json.optJSONObject(i) ?: return@mapNotNull null
            val mac = o.optString("mac")
            if (!macRe.matches(mac)) return@mapNotNull null
            Target(mac.lowercase(), o.optString("address"), o.optString("broadcast"), o.optBoolean("wol"))
        }
    }

    fun save(targets: List<Target>): String = JSONArray(targets.map {
        JSONObject().put("mac", it.mac).put("address", it.address).put("broadcast", it.broadcast).put("wol", it.enabled)
    }).toString()

    fun anyEnabled(targets: List<Target>) = targets.any { it.enabled }

    fun packet(mac: String): ByteArray {
        val bytes = mac.split(":").map { it.toInt(16).toByte() }
        return ByteArray(6) { 0xff.toByte() } + (0 until 16).flatMap { bytes }.toByteArray()
    }

    fun sends(targets: List<Target>): List<Send> = targets.flatMap { t ->
        listOf("255.255.255.255", t.broadcast, t.address).filter { it.isNotBlank() }.flatMap { host -> listOf(9, 7).map { Send(t.mac, host, it) } }
    }.distinct()

    /** Sends every packet once (blocking: call off the main thread); how many went out. */
    fun send(targets: List<Target>): Int {
        var sent = 0
        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (s in sends(targets)) {
                val data = packet(s.mac)
                runCatching { socket.send(DatagramPacket(data, data.size, InetAddress.getByName(s.host), s.port)); sent++ }
            }
        }
        return sent
    }
}
