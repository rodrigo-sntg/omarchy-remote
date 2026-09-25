package com.sandevsystems.omarchyremote.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sandevsystems.omarchyremote.R
import com.sandevsystems.omarchyremote.ui.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Files between the phone and the PC over HTTP on the same host (same pairing code and Tailscale
 * check as the session). Runs in the app's scope, so a transfer outlives the screen that started it;
 * progress and the result are notifications.
 */
class FileTransfer(private val context: Context, private val scope: CoroutineScope) {
    private val client = com.sandevsystems.omarchyremote.network.TailnetDns.client().connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val ids = AtomicInteger(NOTIFICATION_BASE)  // two per transfer: the notification and its share action

    /** The transfers of this run, newest last, for the screen (a sheet covers the message banner). */
    private val _transfers = kotlinx.coroutines.flow.MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: kotlinx.coroutines.flow.StateFlow<List<Transfer>> = _transfers

    private fun track(id: Int, change: (Transfer?) -> Transfer) {
        _transfers.update { list ->
            val old = list.firstOrNull { it.id == id }
            (list.filter { it.id != id } + change(old)).takeLast(20)
        }
    }

    /** Takes a finished transfer off the screen. */
    fun dismiss(id: Int) = _transfers.update { list -> list.filter { it.id != id || it.state == Transfer.State.RUNNING } }

    private fun ended(t: Transfer, state: Transfer.State, where: String? = null, uri: Uri? = null, mime: String? = null, error: String? = null) =
        t.copy(state = state, where = where, uri = uri, mime = mime, error = error, endedAt = SystemClock.elapsedRealtime(), done = t.total ?: t.done)

    /** Sends each file to the PC's Downloads, one after the other. */
    fun send(uris: List<Uri>, sessionUrl: String, code: String) {
        val url = httpUrl(sessionUrl, "file") ?: return
        scope.launch(Dispatchers.IO) {
            for (uri in uris) upload(uri, url, code)
        }
    }

    /**
     * Sends one file into the agent's project (the PC puts it in .omarchy-remote/); the path to
     * mention it by, relative to the project, or null when it did not go.
     */
    suspend fun sendToAgent(uri: Uri, sessionUrl: String, code: String, agent: String): String? {
        val url = httpUrl(sessionUrl, "file") ?: return null
        return kotlinx.coroutines.withContext(Dispatchers.IO) { upload(uri, url, code, agent)?.optString("ref")?.ifBlank { null } }
    }

    private fun upload(uri: Uri, url: String, code: String, agent: String? = null): JSONObject? {
        val (name, size) = describe(uri)
        val id = ids.addAndGet(2)
        progress(id, tr("Enviando ao PC", "Sending to the PC"), name, 0, size)
        val visible = agent == null  // a file for an agent shows in its chat
        if (visible) track(id) { Transfer(id, name, false, 0, size, Transfer.State.RUNNING) }
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = size ?: -1L
            override fun writeTo(sink: BufferedSink) {
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException(tr("arquivo ilegível", "unreadable file"))
                input.use {
                    val buffer = ByteArray(64 * 1024)
                    var sent = 0L
                    var shown = 0L
                    while (true) {
                        val read = it.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        sent += read
                        val now = SystemClock.elapsedRealtime()
                        if (now - shown > 400) {
                            shown = now
                            progress(id, tr("Enviando ao PC", "Sending to the PC"), name, sent, size)
                            if (visible) track(id) { it!!.copy(done = sent) }
                        }
                    }
                }
            }
        }
        val request = Request.Builder().url(url).header("X-Keypad-Token", code).header("X-Keypad-Name", encodeName(name))
            .apply { if (agent != null) header("X-Keypad-Agent", agent) }.post(body).build()
        val result = runCatching {
            client.newCall(request).execute().use { response ->
                val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrDefault(JSONObject())
                if (!response.isSuccessful) throw IOException(when (response.code) {
                    403 -> tr("o PC recusou", "the PC refused")
                    409 -> tr("o agente não está mais no herdr", "the agent is no longer in herdr")
                    else -> tr("erro ${response.code}", "error ${response.code}")
                })
                json
            }
        }
        result.onSuccess { json ->
            val saved = json.optString("name", name)
            if (agent != null) done(id, tr("Enviado ao agente", "Sent to the agent"), json.optString("ref", saved))
            else {
                val folder = json.optString("path").ifBlank { null }?.let { Transfer.pcFolder(it) } ?: "~/Downloads"
                done(id, tr("Enviado ao PC", "Sent to the PC"), tr("$saved · em $folder", "$saved · in $folder"))
                track(id) { ended(it!!.copy(name = saved), Transfer.State.DONE, where = folder) }
            }
        }.onFailure { e ->
            val why = e.message ?: tr("a conexão caiu", "the connection dropped")
            done(id, tr("Não foi enviado ao PC", "Couldn't send to the PC"), "$name: $why")
            if (visible) track(id) { ended(it!!, Transfer.State.FAILED, error = why) }
        }
        return result.getOrNull()
    }

    /**
     * Downloads a file the PC offered. [onImage] set: the bytes go there (a print to read with OCR),
     * nothing is saved. Otherwise a print lands in Pictures/Omarchy Remote and anything else in
     * Download/Omarchy Remote, with a notification that opens it.
     */
    fun receive(offer: FileOffer, sessionUrl: String, code: String, onImage: ((ByteArray?) -> Unit)? = null) {
        val url = httpUrl(sessionUrl, "file/${offer.id}") ?: return
        scope.launch(Dispatchers.IO) {
            val request = Request.Builder().url(url).header("X-Keypad-Token", code).build()
            if (onImage != null) {
                val bytes = runCatching {
                    client.newCall(request).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
                }.getOrNull()
                launch(Dispatchers.Main) { onImage(bytes) }
                return@launch
            }
            val id = ids.addAndGet(2)
            val shot = offer.kind == "shot"
            progress(id, if (shot) tr("Print do PC", "PC screenshot") else tr("Recebendo do PC", "Receiving from the PC"), offer.name, 0, offer.size)
            track(id) { Transfer(id, offer.name, true, 0, offer.size, Transfer.State.RUNNING) }
            val saved = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException(if (response.code == 404) tr("a oferta expirou", "the offer expired") else tr("erro ${response.code}", "error ${response.code}"))
                    val body = response.body ?: throw IOException(tr("vazio", "empty"))
                    save(offer.name, shot) { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var got = 0L
                            var shown = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                got += read
                                val now = SystemClock.elapsedRealtime()
                                if (now - shown > 400) {
                                    shown = now
                                    progress(id, tr("Recebendo do PC", "Receiving from the PC"), offer.name, got, offer.size)
                                    track(id) { it!!.copy(done = got) }
                                }
                            }
                        }
                    }
                }
            }
            saved.onSuccess { (uri, mime) ->
                done(id, if (shot) tr("Print do PC salvo", "PC screenshot saved") else tr("Recebido do PC", "Received from the PC"),
                    tr("${offer.name} · em ${if (shot) "Imagens" else "Downloads"}/Omarchy Remote", "${offer.name} · in ${if (shot) "Pictures" else "Downloads"}/Omarchy Remote"), uri, mime, share = shot)
                track(id) { ended(it!!, Transfer.State.DONE, where = (if (shot) tr("Imagens", "Pictures") else "Download") + "/Omarchy Remote", uri = uri, mime = mime) }
            }.onFailure { e ->
                val why = e.message ?: tr("a conexão caiu", "the connection dropped")
                done(id, tr("Não foi recebido", "Couldn't receive it"), "${offer.name}: $why")
                track(id) { ended(it!!, Transfer.State.FAILED, error = why) }
            }
        }
    }

    /** Writes into MediaStore (visible in Files/Gallery); on Android 9 into the app's own folder. */
    private fun save(rawName: String, image: Boolean, write: (java.io.OutputStream) -> Unit): Pair<Uri?, String> {
        val name = safeName(rawName)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val folder = context.getExternalFilesDir(if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS)
                ?: throw IOException(tr("sem armazenamento", "no storage"))
            java.io.File(folder, name).outputStream().use(write)
            return null to mime
        }
        val resolver = context.contentResolver
        val collection = if (image) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, (if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS) + "/Omarchy Remote")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException(tr("não consegui criar o arquivo", "couldn't create the file"))
        try {
            (resolver.openOutputStream(uri) ?: throw IOException(tr("não consegui gravar", "couldn't write it"))).use(write)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri to mime
    }

    private fun describe(uri: Uri): Pair<String, Long?> {
        var name = uri.lastPathSegment ?: tr("arquivo", "file")
        var size: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        return name to size
    }

    private fun channel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("Arquivos", "Files"), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun progress(id: Int, title: String, name: String, done: Long, total: Long?) {
        channel()
        val builder = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle(title).setContentText(name).setOngoing(true).setOnlyAlertOnce(true)
            .setSilent(true)
        if (total != null && total > 0) builder.setProgress(1000, (done * 1000 / total).toInt(), false)
        else builder.setProgress(0, 0, true)
        post(id, builder)
    }

    private fun done(id: Int, title: String, text: String, open: Uri? = null, mime: String? = null, share: Boolean = false) {
        channel()
        val builder = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle(title).setContentText(text).setAutoCancel(true)
        if (open != null) {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(open, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.setContentIntent(PendingIntent.getActivity(context, id, view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            if (share) {
                val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, open)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val chooser = Intent.createChooser(send, tr("Compartilhar print", "Share screenshot")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                builder.addAction(0, tr("Compartilhar", "Share"), PendingIntent.getActivity(context, id + 1, chooser, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
        }
        post(id, builder)
    }

    private fun post(id: Int, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return  // no permission: the transfer still happens, silently
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    companion object {
        const val CHANNEL = "arquivos"
        private const val NOTIFICATION_BASE = 5_000

        /** ws://host:port/v1 (the session) -> http://host:port/v1/[path]; null for anything else. */
        fun httpUrl(sessionUrl: String, path: String): String? =
            if (sessionUrl.startsWith("ws://")) "http://" + sessionUrl.removePrefix("ws://").trimEnd('/') + "/" + path else null

        /**
         * A name the PC chose, made safe to save: its last part only (no "/" or "\\", so never
         * "../" out of the folder), no control characters, no leading dots, at most 120 characters.
         */
        fun safeName(raw: String): String {
            val last = raw.replace('\\', '/').substringAfterLast('/').filter { it >= ' ' }.trimStart('.').trim()
            val name = if (last.length > 120) {
                val ext = last.substringAfterLast('.', "").take(10)
                last.take(120 - if (ext.isEmpty()) 0 else ext.length + 1) + if (ext.isEmpty()) "" else ".$ext"
            } else last
            return name.ifBlank { tr("arquivo", "file") }
        }

        /** Percent-encoded UTF-8, every byte but the unreserved ones (RFC 3986). */
        fun encodeName(name: String): String = buildString {
            for (byte in name.toByteArray(Charsets.UTF_8)) {
                val c = byte.toInt() and 0xff
                if (c.toChar().isLetterOrDigit() && c < 0x80 || c.toChar() in "-._~") append(c.toChar())
                else append('%').append("%02X".format(c))
            }
        }
    }
}
