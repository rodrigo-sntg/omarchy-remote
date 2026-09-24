package com.sandevsystems.omarchyremote.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.view.inputmethod.InputMethodManager
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandevsystems.omarchyremote.KeypadViewModel
import com.sandevsystems.omarchyremote.input.TermLinks
import org.json.JSONObject

/**
 * The PC's herdr in a text terminal (docs/PLANO-V2.md §4.2): xterm.js in a WebView, the socket in
 * Kotlin, and a toolbar with the keys a phone keyboard lacks. Portrait or landscape, as the phone is.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalScreen(vm: KeypadViewModel, onAgents: () -> Unit) {
    val context = LocalContext.current
    val state by vm.connection.collectAsStateWithLifecycle()
    val agents by vm.agents.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    BackHandler { vm.closeTerminal() }
    DisposableEffect(Unit) {
        onDispose {
            vm.termSink = null
            webView?.destroy()
        }
    }

    fun syncModifiers() {
        ctrl = vm.termModifiers.ctrl
        alt = vm.termModifiers.alt
    }

    fun key(name: String) {
        vm.termKey(name)
        syncModifiers()
    }

    Column(Modifier.fillMaxSize().background(KeypadColors.Bg).safeDrawingPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().height(KeypadDimens.StatusHeight).padding(horizontal = KeypadDimens.ScreenPadding),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(state)
            Text("Terminal", style = KeypadType.HostName, color = KeypadColors.Text, maxLines = 1)
            Text(statusTexts(state, true).first, style = KeypadType.Caption, color = KeypadColors.TextMute, maxLines = 1, modifier = Modifier.weight(1f))
            val waiting = agents.count { it.status == "blocked" || it.status == "done" }
            // Agents: accent with the count when some need the person.
            Pressable(onAgents, Modifier.height(KeypadDimens.MinTouch), shape = KeypadShapes.Segment,
                background = if (waiting > 0) KeypadColors.Attention.copy(alpha = 0.16f) else KeypadColors.Surface3,
                border = if (waiting > 0) KeypadColors.Attention.copy(alpha = 0.6f) else KeypadColors.Line, raised = false,
                description = if (waiting > 0) tr("Agentes, $waiting esperando você", "Agents, $waiting waiting for you") else tr("Agentes", "Agents")) {
                Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("Agentes", "Agents"), style = KeypadType.KeySmall, color = if (waiting > 0) KeypadColors.Attention else KeypadColors.Text)
                    if (waiting > 0) Text("$waiting", style = KeypadType.MonoValue, color = KeypadColors.Attention)
                }
            }
            Pressable({ vm.closeTerminal() }, Modifier.size(KeypadDimens.MinTouch), shape = KeypadShapes.Segment, background = KeypadColors.Surface3,
                raised = false, description = tr("Fechar o terminal", "Close terminal")) {
                Icon(Glyph.Close, null, Modifier.size(16.dp), tint = KeypadColors.Text)
            }
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    // Without this the WebView measures its content (wrap_content) and the page gets no height.
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    setBackgroundColor(KeypadColors.Bg.toArgb())
                    addJavascriptInterface(object {
                        @JavascriptInterface fun send(b64: String) {
                            val text = String(Base64.decode(b64, Base64.NO_WRAP), Charsets.UTF_8)
                            post {
                                vm.termType(text)
                                syncModifiers()
                            }
                        }

                        @JavascriptInterface fun openLink(url: String) {
                            post { openLink(context, url) }
                        }

                        @JavascriptInterface fun ack(bytes: Int) {
                            post { vm.termAck(bytes) }
                        }

                        @JavascriptInterface fun resize(cols: Int, rows: Int) {
                            post { vm.termResize(cols, rows) }
                        }

                        @JavascriptInterface fun ready(cols: Int, rows: Int) {
                            post { vm.startTerminal(cols, rows) }
                        }
                    }, "Android")
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            Log.i("KeypadTerm", "js: ${message.message()} (${message.sourceId().substringAfterLast('/')}:${message.lineNumber()})")
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        // The page carries the bridge that types into the PC: it never navigates away.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url.toString()
                            if (TermLinks.staysInPage(url)) return false
                            openLink(context, url)
                            return true
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            view.evaluateJavascript("termTheme(${JSONObject.quote(terminalTheme())})", null)
                            vm.termSink = { bytes ->
                                view.evaluateJavascript("termWrite('${Base64.encodeToString(bytes, Base64.NO_WRAP)}')", null)
                            }
                        }
                    }
                    // Assets through the WebView's own asset URL: no general file access is needed.
                    loadUrl("file:///android_asset/term/index.html")
                    webView = this
                }
            },
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = KeypadDimens.ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(KeypadDimens.KeyGap),
        ) {
            val h = KeypadDimens.KeyHeightSmall
            KeyRow {
                Key("Esc", { key("esc") }, Modifier.weight(1f), height = h)
                Key("Tab", { key("tab") }, Modifier.weight(1f), height = h)
                Key("Ctrl", { key("ctrl") }, Modifier.weight(1f), height = h, on = ctrl)
                Key("Alt", { key("alt") }, Modifier.weight(1f), height = h, on = alt)
                Key("Ctrl+␣", { key("prefix") }, Modifier.weight(1.3f), height = h, description = tr("Ctrl+Espaço, o atalho do herdr", "Ctrl+Space, herdr's shortcut key"))
            }
            KeyRow {
                Key(tr("Esquerda", "Left"), { key("left") }, Modifier.weight(1f), height = h, icon = Glyph.ArrowLeft)
                Key(tr("Cima", "Up"), { key("up") }, Modifier.weight(1f), height = h, icon = Glyph.ArrowUp)
                Key(tr("Baixo", "Down"), { key("down") }, Modifier.weight(1f), height = h, icon = Glyph.ArrowDown)
                Key(tr("Direita", "Right"), { key("right") }, Modifier.weight(1f), height = h, icon = Glyph.ArrowRight)
                Key("Enter", { key("enter") }, Modifier.weight(1.3f), height = h)
                Key(tr("Teclado", "Keyboard"), {
                    webView?.let { view ->
                        view.evaluateJavascript("termFocus()", null)
                        view.requestFocus()
                        context.getSystemService(InputMethodManager::class.java).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    }
                }, Modifier.weight(1.3f), height = h, icon = Glyph.Keyboard)
            }
        }
    }
}

/** Web links printed in the terminal open in the phone's browser; anything else is ignored. */
private fun openLink(context: Context, url: String) {
    val link = TermLinks.external(url) ?: return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** xterm.js theme from the design tokens (the Omarchy theme takes over in phase 2). */
private fun terminalTheme(): String = JSONObject()
    .put("background", "#0b0c0d").put("foreground", "#eceef0").put("cursor", "#c5f24a")
    .put("selectionBackground", "#343d41").put("black", "#0b0c0d").put("brightBlack", "#3a4045")
    .toString()
