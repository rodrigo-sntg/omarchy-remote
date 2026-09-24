package com.sandevsystems.omarchyremote.display

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
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * A video WebSocket to the host (/v1/display or /v1/screen): JSON messages both ways, H.264 from the
 * host as binary frames. Video is decoded off the main thread; [onOpen], [onText] and [onClosed] run
 * on the main thread. Closing the channel makes the host stop capturing.
 */
class VideoChannel(
    url: String,
    pairingCode: String,
    private val onOpen: (VideoChannel) -> Unit,
    private val onText: (JSONObject) -> Unit,
    private val onClosed: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val splitter = AnnexBSplitter()
    private val assembler = AccessUnitAssembler()
    private val lock = Any()
    private val router = FrameRouter()
    private var closed = false
    private val socket: WebSocket

    init {
        val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()
        val request = Request.Builder().url(url).header("X-Keypad-Token", pairingCode).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                main.post { if (!isClosed()) onOpen(this@VideoChannel) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                router.onText(text)  // here, before the new stream's first frames arrive on this thread
                val message = JSONObject(text)
                if (message.optString("type") == "error") finish(message.optString("message", tr("O PC recusou o vídeo.", "The PC refused the video.")))
                else main.post { if (!isClosed()) onText(message) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                router.deliver(synchronized(lock) { splitter.feed(bytes.toByteArray()).flatMap(assembler::add) })
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                finish(tr("O PC encerrou o vídeo.", "The PC ended the video."))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "video failure ${response?.code}: $t")
                finish(if (response?.code == 403) tr("O PC recusou o vídeo.", "The PC refused the video.") else tr("A conexão de vídeo caiu.", "The video connection dropped."))
            }
        })
    }

    fun send(message: JSONObject) {
        socket.send(message.toString())
    }

    fun attach(decoder: VideoDecoder) = router.attach(decoder::submit)

    fun detach() = router.detach()

    fun close() {
        synchronized(lock) { closed = true }
        socket.close(1000, null)
    }

    private fun isClosed() = synchronized(lock) { closed }

    private fun finish(reason: String) {
        val notify = synchronized(lock) { !closed.also { closed = true } }
        if (notify) main.post { onClosed(reason) }
    }

    companion object {
        private const val TAG = "KeypadVideo"
    }
}
