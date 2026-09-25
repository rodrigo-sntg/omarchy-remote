package com.sandevsystems.omarchyremote.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sandevsystems.omarchyremote.ui.tr
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The terminal WebSocket (/v1/term): the host runs herdr on a PTY; bytes go both ways as binary
 * frames. Callbacks run on the main thread. Closing ends the herdr client on the PC (not its server).
 */
class TermChannel(
    url: String,
    pairingCode: String,
    private val cols: Int,
    private val rows: Int,
    /** With the herdr shortcuts the PC can send (TermActions); empty from an older PC. */
    private val onOpen: (Set<String>) -> Unit,
    private val onBytes: (ByteArray) -> Unit,
    private val onClosed: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    private val socket: WebSocket

    init {
        val client = com.sandevsystems.omarchyremote.network.TailnetDns.client().connectTimeout(5, TimeUnit.SECONDS).build()
        val request = Request.Builder().url(url).header("X-Keypad-Token", pairingCode).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("type", "term.start").put("cols", cols).put("rows", rows).put("session", "default").toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = JSONObject(text)
                when (message.optString("type")) {
                    "term" -> {
                        val list = message.optJSONArray("actions")
                        val actions = (0 until (list?.length() ?: 0)).map { list!!.optString(it) }.toSet()
                        main.post { if (!closed) onOpen(actions) }
                    }
                    "error" -> finish(message.optString("message", tr("O PC recusou o terminal.", "The PC refused the terminal.")))
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                main.post { if (!closed) onBytes(data) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                finish(tr("O computador encerrou o terminal.", "The computer closed the terminal."))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "terminal failure ${response?.code}: $t")
                finish(if (response?.code == 403) tr("O PC recusou o terminal.", "The PC refused the terminal.") else tr("A conexão do terminal caiu.", "The terminal connection dropped."))
            }
        })
    }

    fun send(data: ByteArray) {
        if (!closed) socket.send(data.toByteString())
    }

    /** One of herdr's shortcuts by name; the PC types the person's own keys for it. */
    fun action(name: String) {
        if (!closed) socket.send(JSONObject().put("type", "term.action").put("action", name).toString())
    }

    fun resize(cols: Int, rows: Int) {
        if (!closed) socket.send(JSONObject().put("type", "term.resize").put("cols", cols).put("rows", rows).toString())
    }

    /** Flow control: [bytes] of output were drawn; the host reads the PC's terminal again. */
    fun ack(bytes: Int) {
        if (!closed && bytes > 0) socket.send(JSONObject().put("type", "term.ack").put("bytes", bytes).toString())
    }

    fun close() {
        closed = true
        socket.close(1000, null)
    }

    private fun finish(reason: String) {
        if (closed) return
        closed = true
        main.post { onClosed(reason) }
    }

    companion object {
        private const val TAG = "KeypadTerm"
    }
}
