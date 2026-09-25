package com.sandevsystems.omarchyremote.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import com.sandevsystems.omarchyremote.display.ControlsLayout
import com.sandevsystems.omarchyremote.display.VideoQuality
import kotlin.math.roundToInt
import android.view.inputmethod.EditorInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.rememberCoroutineScope
import com.sandevsystems.omarchyremote.display.ScrollFling
import com.sandevsystems.omarchyremote.input.ModifierKeys
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedTextField
import com.sandevsystems.omarchyremote.display.TextGrab
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect as AndroidRect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.sandevsystems.omarchyremote.display.Loupe
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandevsystems.omarchyremote.ConnectionState
import com.sandevsystems.omarchyremote.KeypadViewModel
import com.sandevsystems.omarchyremote.VideoMode
import com.sandevsystems.omarchyremote.display.RemoteAction
import com.sandevsystems.omarchyremote.display.RemoteGesture
import com.sandevsystems.omarchyremote.display.ViewTransform
import com.sandevsystems.omarchyremote.input.PalmGuard
import com.sandevsystems.omarchyremote.input.MouseButtons
import com.sandevsystems.omarchyremote.input.TouchPoint
import com.sandevsystems.omarchyremote.input.TouchpadAction
import com.sandevsystems.omarchyremote.input.TouchpadGesture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private enum class Overlay { NONE, MENU, SCREENS, KEYS, SETTINGS, SHORTCUTS, GESTURES, SELECT_TEXT, TYPE, ARRANGE }

private val ControlsLayout.Tool.icon: ImageVector
    get() = when (this) {
        ControlsLayout.Tool.RIGHT -> Glyph.Mouse
        ControlsLayout.Tool.HOLD -> Glyph.Hand
        ControlsLayout.Tool.LOUPE -> Glyph.Search
        ControlsLayout.Tool.SCREENS -> Glyph.Grid
        ControlsLayout.Tool.TEXT -> Glyph.Lines
        ControlsLayout.Tool.PRINT -> Glyph.Monitor
        ControlsLayout.Tool.PRESENT -> Glyph.Play
        ControlsLayout.Tool.COPY -> Glyph.Copy
        ControlsLayout.Tool.PASTE -> Glyph.Paste
    }

/**
 * Ver PC and Tela extra (docs/UX-VER-PC.md): the video full screen with three translucent pieces on
 * top — the chip (where you are, latency), the workspace edge (left) and the action rail (right, one
 * tap each). Touch on the video follows the chosen mode: direct touch (like any touch screen) or
 * touchpad (the phone as a laptop touchpad).
 */
@Composable
fun RemoteScreen(vm: KeypadViewModel) {
    FullScreenLandscape()
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    SideEffect { vm.hardwareKeysToPc = true }  // Ver PC / Tela extra: a keyboard on the phone types there
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var idle by remember { mutableStateOf(false) }
    var lastBack by remember { mutableLongStateOf(0L) }
    // The system back gesture is easy to trigger from the screen's edges: leaving takes two.
    BackHandler {
        val now = System.currentTimeMillis()
        when {
            overlay != Overlay.NONE -> overlay = Overlay.NONE
            now - lastBack < 2_000 -> vm.closeVideo()
            else -> {
                lastBack = now
                vm.showMessage(tr("Voltar de novo sai da tela do PC.", "Go back again to leave the PC screen."))
            }
        }
    }
    LaunchedEffect(lastTouch) {
        idle = false
        delay(2_000)
        idle = true
    }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var windowOrigin by remember { mutableStateOf(Offset.Zero) }
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    // Bumped whenever zoom/pan change, so the ring and the zoom chip recompose.
    var transformVersion by remember { mutableIntStateOf(0) }
    val size = vm.videoSize
    val transform = remember(viewSize, size) {
        size?.takeIf { viewSize.width > 0 }?.let { ViewTransform(viewSize.width.toFloat(), viewSize.height.toFloat(), it.first, it.second) }
    }
    fun applyMatrix() {
        val (sx, sy, tx, ty) = transform?.matrixValues() ?: return
        textureView?.setTransform(Matrix().apply { setScale(sx, sy); postTranslate(tx, ty) })
        textureView?.invalidate()
        transformVersion++
    }
    val state by vm.connection.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val cursor = vm.cursor
    val touchpad = vm.videoTouchpad
    val context = LocalContext.current
    // Direct mode: "Direito" arms the next tap as a right click; the finger held down (magnifier); tap echo.
    var rightArmed by remember { mutableStateOf(false) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var echo by remember { mutableStateOf<Pair<Offset, Long>?>(null) }

    LaunchedEffect(cursor, transform) {
        val (x, y) = cursor ?: return@LaunchedEffect
        val t = transform ?: return@LaunchedEffect
        if (t.zoom > 1f && t.follow(x, y, margin = viewSize.width * 0.15f)) applyMatrix()
    }

    // Typing: the keyboard covers the lower half, so the view zooms in with the cursor (where the text
    // usually goes) in the upper part; back to the whole monitor when done.
    LaunchedEffect(overlay == Overlay.TYPE, transform) {
        val t = transform ?: return@LaunchedEffect
        if (overlay == Overlay.TYPE) {
            val (x, y) = cursor ?: (0.5f to 0.5f)
            t.focusOn(x, y, maxOf(t.zoom, 2f), viewSize.width / 2f, viewSize.height * 0.25f)
        } else t.reset()
        applyMatrix()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).onSizeChanged { viewSize = it }
            .onGloballyPositioned { windowOrigin = it.positionInWindow() }
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(pass = PointerEventPass.Initial); lastTouch = System.currentTimeMillis() } },
    ) {
        if (size != null) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) = vm.attachVideoSurface(Surface(st))
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                vm.detachVideoSurface()
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                        }
                        textureView = this
                    }
                },
                update = { applyMatrix() },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Waiting has a way out, and gives up with words instead of spinning forever.
            var late by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(12_000)
                late = true
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!late) {
                    CircularProgressIndicator(color = KeypadColors.Accent)
                    Text(tr("Abrindo a tela do PC…", "Opening the PC's screen…"), style = KeypadType.Body, color = KeypadColors.TextDim)
                    ActionKey(tr("Cancelar", "Cancel"), vm::closeVideo)
                } else {
                    Text(tr("A tela do PC não abriu.", "The PC's screen didn't open."), style = KeypadType.Body, color = KeypadColors.Text)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionKey(tr("Tentar de novo", "Try again"), {
                            val (w, h) = screenSize(context)
                            vm.closeVideo()
                            vm.openScreen(w, h)
                        })
                        ActionKey(tr("Sair", "Leave"), vm::closeVideo)
                    }
                }
            }
        }

        if (transform != null) {
            // The S Pen goes to the PC as a pen (pressure, hover); fingers keep the chosen mode.
            val layer = Modifier.fillMaxSize().let { if (vm.videoMode == VideoMode.SCREEN) it.stylus(vm, transform) else it }
            if (touchpad) TouchpadLayer(vm, transform, ::applyMatrix, layer)
            else DirectLayer(
                vm, transform, ::applyMatrix, layer,
                rightArmed = { rightArmed }, onRightUsed = { rightArmed = false },
                onFinger = { finger = it }, onEcho = { echo = it to System.nanoTime() },
            )
        }

        // A ring on the cursor in touchpad mode: the PC's own cursor is tiny on a scaled-down monitor.
        val loupe = vm.loupeZoom
        if (loupe > 0f && finger == null && cursor != null && transform != null) {
            @Suppress("UNUSED_EXPRESSION") transformVersion
            val (cx, cy) = transform.toView(cursor.first, cursor.second)
            LoupeLens(cx, cy, viewSize, loupe, windowOrigin)
        }

        // Holding a finger (or dragging) shows what is under it, magnified above it.
        finger?.let { f -> if (transform != null) LoupeLens(f.x, f.y, viewSize, 2.5f, windowOrigin) }
        echo?.let { (at, stamp) -> TapEcho(at, stamp) }

        // A ring on the cursor in touchpad mode (not with the magnifier: it would copy the ring too).
        if (touchpad && loupe == 0f && cursor != null && transform != null) {
            @Suppress("UNUSED_EXPRESSION") transformVersion
            val (cx, cy) = transform.toView(cursor.first, cursor.second)
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(KeypadColors.Accent.copy(alpha = 0.85f), radius = 22.dp.toPx(), center = Offset(cx, cy), style = Stroke(3.dp.toPx()))
                drawCircle(KeypadColors.Accent.copy(alpha = 0.2f), radius = 22.dp.toPx(), center = Offset(cx, cy))
            }
        }

        transform?.let { t ->
            @Suppress("UNUSED_EXPRESSION") transformVersion
            if (t.zoom > 1.01f) {
                Key(tr("%.1f× · ver tudo", "%.1f× · see all").format(t.zoom), { t.reset(); applyMatrix() },
                    Modifier.align(Alignment.TopCenter).padding(top = 34.dp).widthIn(min = 140.dp), height = KeypadDimens.MinTouch,
                    style = KeypadType.Mono)
            }
        }

        if (overlay == Overlay.SELECT_TEXT && transform != null) {
            TextSelection(transform, onDone = { region ->
                overlay = Overlay.NONE
                if (region != null) vm.readScreenText(region)
            })
        }
        if (vm.readingText) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 34.dp).clip(KeypadShapes.Card).background(KeypadColors.Surface2)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), color = KeypadColors.Accent, strokeWidth = 2.dp)
                Text(tr("Lendo o texto…", "Reading the text…"), style = KeypadType.Body, color = KeypadColors.Text)
            }
        }

        // Where you are and the workspaces, together along the top: the left edge is where a phone
        // held sideways has its camera.
        Row(
            Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RemoteChip(
                monitor = if (vm.videoMode == VideoMode.DISPLAY) tr("celular como tela", "phone as a screen") else vm.focusedWindow?.let { tr("janela · ${it.take(24)}", "window · ${it.take(24)}") } ?: vm.currentMonitor ?: "…",
                mode = vm.videoStatsText,
                faded = idle && overlay == Overlay.NONE,
                onClick = { overlay = if (overlay == Overlay.SCREENS) Overlay.NONE else Overlay.SCREENS },
                modifier = Modifier,
            )
            WorkspacePills(workspaces, idle && overlay == Overlay.NONE, vm::goToWorkspace)
        }
        // What is switched on, in words, with a way out: modes are easy to forget.
        val mode = when {
            vm.videoHold -> tr("Botão esquerdo segurado", "Left button held") to { vm.toggleVideoHold() }
            rightArmed -> tr("Próximo toque: clique direito", "Next tap: right click") to { rightArmed = false }
            vm.presenting -> tr("Apresentando · volume − avança, + volta", "Presenting · volume − next, + back") to { vm.togglePresenting() }
            else -> null
        }
        if (mode != null && overlay == Overlay.NONE) {
            ModePill(mode.first, mode.second, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
        }

        // The controls, where the person put them (Mais → Organizar controles, or hold any of them).
        val controls = vm.controls
        val arranging = overlay == Overlay.ARRANGE
        val showControls = arranging || (overlay == Overlay.NONE && !(idle && controls.idle == ControlsLayout.Idle.HIDE))
        val controlsAlpha = if (!arranging && idle && controls.idle == ControlsLayout.Idle.FADE) 0.4f else 1f
        val arrange = { overlay = Overlay.ARRANGE }
        if (arranging) Box(Modifier.fillMaxSize().background(Color(0x99060708)))
        if (showControls) {
            val buttonSize = controls.size.dp.dp
            DraggableGroup(controls.typeAt, viewSize, arranging, controlsAlpha, { vm.changeControls(vm.controls.copy(typeAt = it)) }) {
                ToolButton(Glyph.Keyboard, tr("Digitar", "Type"), buttonSize + 8.dp, on = false, enabled = !arranging, onLongClick = arrange) {
                    overlay = Overlay.TYPE
                }
            }
            DraggableGroup(controls.toolsAt, viewSize, arranging, controlsAlpha, { vm.changeControls(vm.controls.copy(toolsAt = it)) }) { vertical ->
                // In a column the most used sits lowest, under the thumb, and "Mais" on top; a row reads left to right.
                val chosen = ControlsLayout.Tool.entries.filter { it in controls.tools }
                val tools = if (vertical) chosen.reversed() else chosen
                val more: @Composable () -> Unit = {
                    ToolButton(Glyph.More, tr("Mais", "More"), buttonSize - 6.dp, on = false, enabled = !arranging, onLongClick = arrange) { overlay = Overlay.MENU }
                }
                val buttons: @Composable () -> Unit = {
                    if (vertical) more()
                    for (tool in tools) {
                        val on = when (tool) {
                            ControlsLayout.Tool.RIGHT -> rightArmed
                            ControlsLayout.Tool.HOLD -> vm.videoHold
                            ControlsLayout.Tool.LOUPE -> vm.loupeZoom > 0f
                            ControlsLayout.Tool.PRESENT -> vm.presenting
                            else -> false
                        }
                        val label = if (tool == ControlsLayout.Tool.LOUPE && vm.loupeZoom > 0f) tr("Lupa ${vm.loupeZoom.toInt()}×", "Magnifier ${vm.loupeZoom.toInt()}×") else tool.label
                        ToolButton(tool.icon, label, buttonSize, on, enabled = !arranging, onLongClick = arrange) {
                            when (tool) {
                                ControlsLayout.Tool.RIGHT -> if (touchpad) vm.videoPointer("click", value = 2) else rightArmed = !rightArmed
                                ControlsLayout.Tool.HOLD -> vm.toggleVideoHold()
                                ControlsLayout.Tool.LOUPE -> vm.cycleLoupe()
                                ControlsLayout.Tool.SCREENS -> overlay = Overlay.SCREENS
                                ControlsLayout.Tool.TEXT -> overlay = if (vm.videoMode == VideoMode.SCREEN) Overlay.SELECT_TEXT else Overlay.NONE
                                ControlsLayout.Tool.PRINT -> vm.printPc(vm.currentMonitor)
                                ControlsLayout.Tool.PRESENT -> vm.togglePresenting()
                                ControlsLayout.Tool.COPY -> vm.copyOnPc()
                                ControlsLayout.Tool.PASTE -> vm.pasteOnPc()
                            }
                        }
                    }
                    if (!vertical) more()
                }
                if (vertical) Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { buttons() }
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { buttons() }
            }
        }
        if (arranging) {
            ArrangePanel(vm.controls, vm::changeControls, onDone = { overlay = Overlay.NONE }, modifier = Modifier.align(Alignment.Center))
        }
        if (overlay == Overlay.TYPE) {
            TypingBar(vm, onClose = { overlay = Overlay.NONE }, modifier = Modifier.align(Alignment.BottomCenter))
        }
        MessageBanner(vm.message, vm::dismissMessage, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), vm.messageIsError)

        if (overlay == Overlay.KEYS) {
            LandscapeKeysLayer(
                vm, enabled = true,
                status = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Teclado", "Keyboard"), Modifier.weight(1f), style = KeypadType.HostName, color = KeypadColors.Text)
                        ActionKey(tr("Voltar à tela", "Back to the screen"), { overlay = Overlay.NONE })
                    }
                },
                trackpad = { modifier ->
                    TrackpadPanel(
                        active = true, dragging = vm.dragging, naturalScroll = vm.naturalScroll,
                        onAction = { remoteTouchpad(vm, it) }, onHelp = { overlay = Overlay.GESTURES },
                        onLeft = { vm.click(MouseButtons.LEFT) }, onToggleHold = vm::toggleDrag, onRight = { vm.click(MouseButtons.RIGHT) },
                        modifier = modifier,
                    )
                },
                onText = { overlay = Overlay.TYPE },
            )
        }
    }

    when (overlay) {
        Overlay.SCREENS -> AppSheet(tr("Onde olhar", "Screens"), { overlay = Overlay.NONE }) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (m in vm.monitors) {
                    val on = vm.videoMode == VideoMode.SCREEN && vm.focusedWindow == null && m == vm.currentMonitor
                    ScreenCard(Glyph.Monitor, m, if (on) tr("agora", "now") else "monitor", on) { overlay = Overlay.NONE; vm.switchMonitor(m) }
                }
                if (vm.videoMode == VideoMode.SCREEN) {
                    ScreenCard(Glyph.Swap, tr("Janela em foco", "Focused window"), vm.focusedWindow?.take(22) ?: tr("acompanha o foco", "follows focus"), vm.focusedWindow != null) {
                        overlay = Overlay.NONE
                        vm.showFocusedWindow()
                    }
                }
                val extra = vm.videoMode == VideoMode.DISPLAY
                ScreenCard(Glyph.ViewPc, if (extra) tr("Ver um monitor", "View a monitor") else tr("Celular como tela", "Phone as screen"), if (extra) tr("sair da tela extra", "leave extra screen") else tr("vira um monitor a mais", "becomes one more monitor"), extra) {
                    overlay = Overlay.NONE
                    val (w, h) = screenSize(context)
                    vm.closeVideo()
                    if (extra) vm.openScreen(w, h) else vm.openDisplay(w, h)
                }
            }
            Overline("Workspace")
            ScreensCard(true, true, emptyList(), null, workspaces, vm::goToWorkspace, {}, {})
        }
        Overlay.MENU -> AppSheet(tr("Mais", "More"), { overlay = Overlay.NONE }) {
            // Landscape: settings on the left, tools on the right, so everything fits without scrolling.
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Overline(tr("O dedo na tela", "Finger on screen"))
                    Segmented(listOf(false to tr("Toque direto", "Direct touch"), true to "Trackpad"), touchpad, { vm.changeVideoTouchpad(it) }, Modifier.fillMaxWidth())
                    Overline(tr("Imagem", "Picture"))
                    Segmented(VideoQuality.entries.map { it to it.label }, vm.videoQuality, vm::changeVideoQuality, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Key(tr("Ajustes", "Settings"), { overlay = Overlay.SETTINGS }, Modifier.weight(1f), height = 48.dp, style = KeypadType.KeySmall)
                        Key(tr("Gestos", "Gestures"), { overlay = Overlay.GESTURES }, Modifier.weight(1f), height = 48.dp, style = KeypadType.KeySmall)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Overline(tr("Ferramentas", "Tools"))
                    val screen = vm.videoMode == VideoMode.SCREEN
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Key(tr("Copiar", "Copy"), { overlay = Overlay.NONE; vm.copyOnPc() }, Modifier.weight(1f), height = 48.dp, style = KeypadType.KeySmall,
                            description = tr("Copia o que está selecionado no PC, e traz para o celular", "Copies what is selected on the PC, and brings it to the phone"))
                        Key(tr("Colar", "Paste"), { overlay = Overlay.NONE; vm.pasteOnPc() }, Modifier.weight(1f), height = 48.dp, style = KeypadType.KeySmall,
                            description = tr("Cola no PC o que você copiou no celular", "Pastes on the PC what you copied on the phone"))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Key(tr("Ler texto", "Read text"), { overlay = Overlay.SELECT_TEXT }, Modifier.weight(1f), height = 48.dp, style = KeypadType.KeySmall,
                            enabled = screen, description = if (screen) null else tr("Só ao ver um monitor", "Only when viewing a monitor"))
                        Key(tr("Print", "Screenshot"), { overlay = Overlay.NONE; vm.printPc(vm.currentMonitor) }, Modifier.weight(1f), height = 48.dp, style = KeypadType.KeySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Key(tr("Apresentar", "Present"), { overlay = Overlay.NONE; vm.togglePresenting() }, Modifier.weight(1f), height = 48.dp, style = KeypadType.KeySmall, on = vm.presenting)
                        Key(tr("Teclado", "Keyboard"), { overlay = Overlay.KEYS }, Modifier.weight(1f), height = 48.dp, style = KeypadType.KeySmall,
                            description = tr("Teclado completo e atalhos", "Full keyboard and shortcuts"))
                    }
                    Key(tr("Organizar controles", "Arrange controls"), { overlay = Overlay.ARRANGE }, Modifier.fillMaxWidth(), height = 48.dp, style = KeypadType.KeySmall)
                }
            }
            ActionKey(tr("Sair da tela do PC", "Leave the PC's screen"), vm::closeVideo, Modifier.fillMaxWidth())
        }
        Overlay.SETTINGS -> SettingsSheet(vm, onEditShortcuts = { overlay = Overlay.SHORTCUTS }, onGestures = { overlay = Overlay.GESTURES }) { overlay = Overlay.NONE }
        Overlay.SHORTCUTS -> ShortcutEditorSheet(vm, null) { overlay = Overlay.NONE }
        Overlay.GESTURES -> GesturesDialog(touchpad) { overlay = Overlay.NONE; vm.markHintsSeen() }
        else -> Unit
    }
    vm.screenText?.let { text -> ScreenTextSheet(vm, text) { vm.screenText = null } }
    if (!vm.hintsSeen && overlay == Overlay.NONE) GesturesDialog(touchpad) { vm.markHintsSeen() }
}

/** Main-session touchpad inside the Ver PC keys layer (relative over the control channel). */
private fun remoteTouchpad(vm: KeypadViewModel, action: TouchpadAction) {
    when (action) {
        is TouchpadAction.Move -> vm.move(action.dx, action.dy)
        is TouchpadAction.Click -> vm.click(when (action.button) { 1 -> MouseButtons.LEFT; 2 -> MouseButtons.RIGHT; else -> MouseButtons.MIDDLE })
        TouchpadAction.DragStart -> vm.setDrag(true)
        TouchpadAction.DragEnd -> vm.setDrag(false)
        is TouchpadAction.Scroll -> vm.scrollSteps(action.vertical)
        is TouchpadAction.WorkspaceStep -> vm.stepWorkspace(action.direction)
        else -> Unit
    }
}

/** Where you are (monitor or window) and how the link is doing; a tap switches where to look. */
@Composable
private fun RemoteChip(monitor: String, mode: String, faded: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val alpha by animateFloatAsState(if (faded) 0.45f else 1f, tween(300), label = "chip")
    Row(
        modifier.alpha(alpha).heightIn(min = 34.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xC70A0D0F))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(17.dp)).clickable(onClickLabel = tr("Trocar de tela", "Switch screen"), onClick = onClick)
            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (mode.isEmpty()) KeypadColors.Ok else KeypadColors.Attention))
        Text(monitor, style = KeypadType.Mono.copy(fontSize = KeypadType.KeySmall.fontSize * 0.92f), color = KeypadColors.Text)
        if (mode.isNotEmpty()) Text(mode, style = KeypadType.Mono, color = KeypadColors.Attention)
        Icon(Glyph.ChevronDown, null, Modifier.size(16.dp), tint = KeypadColors.TextDim)
    }
}

/** A mode that is on, said in words, with ✕ to switch it off. */
@Composable
private fun ModePill(text: String, onCancel: () -> Unit, modifier: Modifier) {
    Row(
        modifier.heightIn(min = 34.dp).clip(RoundedCornerShape(17.dp)).background(KeypadColors.Accent).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = KeypadType.Key.copy(fontSize = KeypadType.KeySmall.fontSize), color = KeypadColors.OnAccent)
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(KeypadColors.OnAccent.copy(alpha = 0.14f))
                .clickable(onClickLabel = tr("Desligar", "Turn off"), onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) { Icon(Glyph.Close, null, Modifier.size(14.dp), tint = KeypadColors.OnAccent) }
    }
}

/** One place to look at in "Onde olhar". */
@Composable
private fun ScreenCard(icon: ImageVector, title: String, subtitle: String, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(168.dp).clip(KeypadShapes.Card).background(KeypadColors.Surface2)
            .border(if (on) 2.dp else 1.dp, if (on) KeypadColors.Accent else KeypadColors.Line, KeypadShapes.Card)
            .clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = if (on) KeypadColors.Accent else KeypadColors.Text)
        Text(title, style = KeypadType.HostName, color = KeypadColors.Text, maxLines = 1)
        Text(subtitle, style = KeypadType.Caption, color = KeypadColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A round control; hold it to rearrange the controls. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolButton(icon: ImageVector, label: String, size: Dp, on: Boolean, enabled: Boolean, onLongClick: () -> Unit, onClick: () -> Unit) {
    val view = LocalView.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            Modifier.size(size).clip(CircleShape).background(if (on) KeypadColors.Accent else Color(0xD10A0D0F))
                .border(1.dp, if (on) KeypadColors.Accent else Color.White.copy(alpha = 0.1f), CircleShape)
                .combinedClickable(
                    enabled = enabled, onClickLabel = label,
                    onLongClick = { Haptic.tap(view); onLongClick() },
                    onClick = { Haptic.tap(view); onClick() },
                )
                .semantics { contentDescription = label; if (on) stateDescription = tr("ligado", "on") },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(size * 0.45f), tint = if (on) KeypadColors.OnAccent else KeypadColors.Text) }
        Text(label, style = KeypadType.MacroCaption.copy(fontSize = KeypadType.Mono.fontSize * 0.85f),
            color = if (on) KeypadColors.Accent else KeypadColors.Text.copy(alpha = 0.85f), maxLines = 1)
    }
}

/**
 * A group of controls placed by its center (fraction of the screen). While arranging it can be
 * dragged, and snaps to the nearest edge when dropped close to it; a column on the sides, a row on
 * the top and bottom.
 */
@Composable
private fun DraggableGroup(
    at: Pair<Float, Float>, view: IntSize, arranging: Boolean, alpha: Float, onMoved: (Pair<Float, Float>) -> Unit,
    content: @Composable (vertical: Boolean) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var dragged by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    val margin = with(density) { 10.dp.toPx() }
    val pull = with(density) { 56.dp.toPx() }
    val moved by rememberUpdatedState(onMoved)
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val placed = if (size == IntSize.Zero || view == IntSize.Zero) null
    else ControlsLayout.snap(at.first * view.width - w / 2, at.second * view.height - h / 2, w, h, view.width.toFloat(), view.height.toFloat(), margin, 0f)
    val pos = dragged ?: placed?.let { Offset(it.first, it.second) } ?: Offset.Zero
    val live = dragged?.let { ((it.x + w / 2) / view.width).coerceIn(0f, 1f) to ((it.y + h / 2) / view.height).coerceIn(0f, 1f) } ?: at
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
            .alpha(if (placed == null) 0f else alpha)
            .onSizeChanged { size = it }
            .systemGestureExclusion()
            .then(
                if (arranging) Modifier.clip(shape).background(KeypadColors.Accent.copy(alpha = 0.1f)).border(2.dp, KeypadColors.Accent, shape)
                    .pointerInput(view, size) {
                        detectDragGestures(
                            onDragStart = { dragged = pos },
                            onDragCancel = { dragged = null },
                            onDragEnd = {
                                val p = dragged ?: pos
                                val (x, y) = ControlsLayout.snap(p.x, p.y, w, h, view.width.toFloat(), view.height.toFloat(), margin, pull)
                                moved(((x + w / 2) / view.width) to ((y + h / 2) / view.height))
                                dragged = null
                            },
                        ) { change, amount ->
                            change.consume()
                            dragged = (dragged ?: pos) + amount
                        }
                    }
                else Modifier,
            )
            .padding(8.dp),
    ) { content(ControlsLayout.vertical(live.first, live.second)) }
}

/** "Organizar controles": which tools, how big, what happens when idle; the groups drag meanwhile. */
@Composable
private fun ArrangePanel(layout: ControlsLayout, onChange: (ControlsLayout) -> Unit, onDone: () -> Unit, modifier: Modifier) {
    Column(
        modifier.widthIn(max = 480.dp).clip(KeypadShapes.Card).background(KeypadColors.Surface2).border(1.dp, KeypadColors.Line, KeypadShapes.Card)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(tr("Organizar controles", "Arrange controls"), style = KeypadType.SheetTitle.copy(fontSize = KeypadType.HostName.fontSize * 1.15f), color = KeypadColors.Text)
        Text(tr("Arraste os grupos para onde não atrapalham; perto de uma borda, eles grudam nela.", "Drag the groups out of the way; near an edge, they snap to it."), style = KeypadType.Caption, color = KeypadColors.TextDim)
        Overline(tr("Botões", "Buttons"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (tool in ControlsLayout.Tool.entries) {
                val on = tool in layout.tools
                Key(tool.label, { onChange(layout.copy(tools = if (on) layout.tools - tool else layout.tools + tool)) },
                    Modifier.widthIn(min = 64.dp), height = 36.dp, style = KeypadType.KeySmall, on = on, inset = 12.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Overline(tr("Tamanho", "Size"))
                Segmented(ControlsLayout.Size.entries.map { it to it.label }, layout.size, { onChange(layout.copy(size = it)) }, Modifier.fillMaxWidth())
            }
            Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Overline(tr("Parado", "When idle"))
                Segmented(ControlsLayout.Idle.entries.map { it to it.label }, layout.idle, { onChange(layout.copy(idle = it)) }, Modifier.fillMaxWidth())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f))
            ActionKey(tr("Restaurar padrão", "Reset to default"), { onChange(ControlsLayout.DEFAULT) })
            ActionKey(tr("Pronto", "Done"), onDone, on = true)
        }
    }
}

/** The workspaces as numbered pills beside the chip: the current one in the accent; faded at rest like the chip. */
@Composable
private fun WorkspacePills(workspaces: List<com.sandevsystems.omarchyremote.network.Workspace>, faded: Boolean, onGo: (Int) -> Unit) {
    if (workspaces.isEmpty()) return
    val view = LocalView.current
    val alpha by animateFloatAsState(if (faded) 0.45f else 1f, tween(300), label = "workspaces")
    val shape = RoundedCornerShape(17.dp)
    Row(
        Modifier.alpha(alpha).height(34.dp).clip(shape).background(Color(0xC70A0D0F)).border(1.dp, Color.White.copy(alpha = 0.09f), shape)
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (w in workspaces) {
            val current = w.focused
            Box(
                Modifier.size(width = 34.dp, height = 28.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (current) KeypadColors.Accent.copy(alpha = 0.9f) else Color.Transparent)
                    .clickable { Haptic.tap(view); onGo(w.id) }
                    .semantics { contentDescription = "Workspace ${w.id}"; if (current) stateDescription = tr("atual", "current") },
                contentAlignment = Alignment.Center,
            ) {
                Text("${w.id}", style = KeypadType.Mono, color = if (current) KeypadColors.OnAccent else KeypadColors.Text.copy(alpha = 0.8f))
            }
        }
    }
}

/** Touchpad mode: relative motion (finer when zoomed), actions at the cursor. */
@Composable
private fun TouchpadLayer(vm: KeypadViewModel, t: ViewTransform, applyMatrix: () -> Unit, modifier: Modifier) {
    val config = LocalViewConfiguration.current
    val natural by rememberUpdatedState(vm.naturalScroll)
    Box(
        modifier.pointerInput(t) {
            val gesture = TouchpadGesture(
                touchSlop = config.touchSlop, tapTimeoutMs = config.longPressTimeoutMillis,
                doubleTapMs = config.doubleTapTimeoutMillis, scrollStep = 40.dp.toPx(), swipeDistance = 90.dp.toPx(),
            )
            fun handle(action: TouchpadAction) {
                when (action) {
                    is TouchpadAction.Move -> vm.videoPointer(
                        "rel", action.dx / t.displayedWidth * vm.sensitivity, action.dy / t.displayedHeight * vm.sensitivity,
                    )
                    is TouchpadAction.Click -> if (action.button != 1 || vm.tapToClick) vm.videoPointer("click", value = action.button)
                    TouchpadAction.DragStart -> vm.videoPointer("press")
                    TouchpadAction.DragEnd -> vm.videoPointer("release")
                    is TouchpadAction.Scroll -> {
                        if (action.vertical != 0) vm.videoPointer("scroll", value = action.vertical)
                        if (action.horizontal != 0) vm.videoPointer("hscroll", value = action.horizontal)
                    }
                    is TouchpadAction.WorkspaceStep -> vm.stepWorkspace(action.direction)
                    is TouchpadAction.Zoom -> { t.zoomBy(action.factor, action.focusX, action.focusY); applyMatrix() }
                    is TouchpadAction.Pan -> { t.panBy(action.dx, action.dy); applyMatrix() }
                }
            }
            awaitEachGesture {
                // The finger landing is the gesture's first frame: without it a quick tap reached the
                // gesture only as "lifted" and was lost (it clicked only when the finger wobbled).
                val first = awaitFirstDown(requireUnconsumed = true)
                gesture.naturalScroll = natural
                gesture.onFrame(listOf(TouchPoint(first.id.value, first.position.x, first.position.y)), first.uptimeMillis).forEach(::handle)
                var dragging = false
                try {
                    do {
                        val event = awaitPointerEvent()
                        val points = event.changes.filter { it.pressed }.map { TouchPoint(it.id.value, it.position.x, it.position.y) }
                        event.changes.forEach { it.consume() }
                        gesture.naturalScroll = natural
                        for (action in gesture.onFrame(points, event.changes.maxOf { it.uptimeMillis })) {
                            if (action == TouchpadAction.DragStart) dragging = true
                            if (action == TouchpadAction.DragEnd) dragging = false
                            handle(action)
                        }
                    } while (points.isNotEmpty())
                } catch (cancelled: CancellationException) {
                    if (dragging) vm.videoPointer("release") // never leave the button down
                    throw cancelled
                }
            }
        },
    )
}

/** Direct mode: like any touch screen (RemoteGesture). Tap clicks there, sliding scrolls, holding
 * shows the point magnified, then drags or (lifting) right-clicks. */
@Composable
private fun DirectLayer(
    vm: KeypadViewModel, t: ViewTransform, applyMatrix: () -> Unit, modifier: Modifier,
    rightArmed: () -> Boolean, onRightUsed: () -> Unit, onFinger: (Offset?) -> Unit, onEcho: (Offset) -> Unit,
) {
    val config = LocalViewConfiguration.current
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val flingJob = remember { arrayOfNulls<Job>(1) }
    Box(
        modifier.pointerInput(t) {
            val stepPx = 32.dp.toPx()
            val gesture = RemoteGesture(touchSlop = config.touchSlop, longPressMs = config.longPressTimeoutMillis, scrollStepPx = stepPx)
            var last = 0.5f to 0.5f
            fun content(x: Float, y: Float) = (t.toContent(x, y) ?: last).also { last = it }
            fun scroll(vertical: Int, horizontal: Int, x: Float, y: Float) {
                val sign = if (vm.naturalScroll) 1 else -1
                val (nx, ny) = content(x, y)
                if (vertical != 0) vm.videoTouch("scroll", nx, ny, sign * vertical)
                if (horizontal != 0) vm.videoTouch("hscroll", nx, ny, sign * horizontal)
            }
            fun handle(actions: List<RemoteAction>) {
                for (action in actions) when (action) {
                    is RemoteAction.Tap -> t.toContent(action.x, action.y)?.let { (x, y) ->
                        Haptic.tap(view)
                        onEcho(Offset(action.x, action.y))
                        if (rightArmed()) {
                            onRightUsed()
                            vm.videoTouch("right", x, y)
                        } else {
                            vm.videoTouch("down", x, y)
                            vm.videoTouch("up", x, y)
                        }
                    }
                    is RemoteAction.Hold -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFinger(Offset(action.x, action.y))
                        t.toContent(action.x, action.y)?.let { (x, y) -> last = x to y; vm.videoTouch("move", x, y) }
                    }
                    is RemoteAction.Press -> t.toContent(action.x, action.y)?.let { (x, y) -> last = x to y; vm.videoTouch("down", x, y) }
                    is RemoteAction.Drag -> {
                        onFinger(Offset(action.x, action.y))
                        content(action.x, action.y).let { (x, y) -> vm.videoTouch("move", x, y) }
                    }
                    is RemoteAction.Release -> {
                        onFinger(null)
                        content(action.x, action.y).let { (x, y) -> vm.videoTouch("up", x, y) }
                    }
                    is RemoteAction.RightClick -> {
                        onFinger(null)
                        onEcho(Offset(action.x, action.y))
                        t.toContent(action.x, action.y)?.let { (x, y) -> vm.videoTouch("right", x, y) }
                    }
                    is RemoteAction.Scroll -> scroll(action.vertical, action.horizontal, action.x, action.y)
                    is RemoteAction.Fling -> {
                        val fling = ScrollFling(action.vx, action.vy, stepPx)
                        flingJob[0] = scope.launch {
                            while (!fling.done) {
                                delay(16)
                                val (v, h) = fling.next(16)
                                scroll(v, h, action.x, action.y)
                            }
                        }
                    }
                    is RemoteAction.Zoom -> { t.zoomBy(action.factor, action.focusX, action.focusY); applyMatrix() }
                    is RemoteAction.Pan -> { t.panBy(action.dx, action.dy); applyMatrix() }
                    RemoteAction.ResetZoom -> { t.reset(); applyMatrix() }
                }
            }
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = true)
                flingJob[0]?.cancel()  // a touch stops the inertia, like a phone
                var pressed = false
                try {
                    handle(gesture.onFrame(listOf(TouchPoint(first.id.value, first.position.x, first.position.y)), first.uptimeMillis))
                    var now = first.uptimeMillis
                    while (true) {
                        val deadline = gesture.deadline()
                        val event = if (deadline != null) withTimeoutOrNull((deadline - now).coerceAtLeast(1)) { awaitPointerEvent() }
                        else awaitPointerEvent()
                        if (event == null) {
                            now = deadline!!
                            handle(gesture.onTimeout(now))
                            continue
                        }
                        now = event.changes.maxOf { it.uptimeMillis }
                        event.changes.forEach { it.consume() }
                        val points = event.changes.filter { it.pressed }.map { TouchPoint(it.id.value, it.position.x, it.position.y) }
                        val actions = gesture.onFrame(points, now)
                        if (actions.any { it is RemoteAction.Press }) pressed = true
                        if (actions.any { it is RemoteAction.Release }) pressed = false
                        handle(actions)
                        if (points.isEmpty()) break
                    }
                } catch (cancelled: CancellationException) {
                    if (pressed) vm.videoTouch("up", last.first, last.second)
                    throw cancelled
                } finally {
                    onFinger(null)
                }
            }
        },
    )
}

/** Where a tap landed: a ring that grows and fades (the PC's own cursor is tiny and far away). */
@Composable
private fun TapEcho(at: Offset, stamp: Long) {
    val progress = remember(stamp) { Animatable(0f) }
    LaunchedEffect(stamp) { progress.animateTo(1f, tween(380)) }
    val accent = KeypadColors.Accent
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        if (p < 1f) {
            drawCircle(accent.copy(alpha = 0.55f * (1 - p)), radius = (10 + 18 * p).dp.toPx(), center = at, style = Stroke(2.5.dp.toPx()))
        }
    }
}

/**
 * "Digitar": the Android keyboard types straight into the PC while the screen stays visible. The
 * field keeps what was typed so the keyboard's own corrections work; each change goes to the PC as
 * backspaces + new text (TypingDiff). Keys the phone keyboard lacks are on the row above it.
 */
@Composable
private fun TypingBar(vm: KeypadViewModel, onClose: () -> Unit, modifier: Modifier) {
    var ctrl by remember { mutableStateOf(false) }
    HideKeyboardOnLeave()
    val hideKeyboard = rememberHideKeyboard()
    val keys = listOf("Esc" to 0x29, "Tab" to 0x2B, "←" to 0x50, "↑" to 0x52, "↓" to 0x51, "→" to 0x4F, "⌫" to 0x2A, "⏎" to 0x28)
    Column(
        modifier.fillMaxWidth().imePadding().background(Color(0xF00A0D0F)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Key("Ctrl", { ctrl = !ctrl }, Modifier.weight(1.1f), height = 40.dp, style = KeypadType.KeySmall, on = ctrl,
                description = tr("Ctrl para a próxima tecla", "Ctrl for the next key"))
            for ((name, usage) in keys) {
                Key(name, {
                    vm.typeKey(usage, if (ctrl) ModifierKeys.CTRL else 0)
                    ctrl = false
                }, Modifier.weight(1f), height = 40.dp, style = KeypadType.KeySmall)
            }
            Key(tr("Copiar", "Copy"), vm::copyOnPc, Modifier.weight(1.3f), height = 40.dp, style = KeypadType.KeySmall,
                description = tr("Copia o que está selecionado no PC", "Copies what is selected on the PC"))
            Key(tr("Colar", "Paste"), vm::pasteOnPc, Modifier.weight(1.3f), height = 40.dp, style = KeypadType.KeySmall,
                description = tr("Cola no PC o que você copiou no celular", "Pastes on the PC what you copied on the phone"))
            Key(tr("Fechar", "Close"), { hideKeyboard(); onClose() }, Modifier.weight(1.4f), height = 40.dp, style = KeypadType.KeySmall)
        }
        AndroidView(
            factory = { ctx ->
                LiveTypingField(ctx) { change ->
                    if (ctrl && change.backspaces == 0 && change.insert.codePointCount(0, change.insert.length) == 1) {
                        vm.typeShortcut(change.insert, ModifierKeys.CTRL)  // Ctrl+C, Ctrl+V… not text
                        ctrl = false
                        true
                    } else {
                        vm.typeLive(change)
                        false
                    }
                }.also { it.post { it.requestFocus(); it.showKeyboard() } }
            },
            modifier = Modifier.fillMaxWidth().height(46.dp).clip(KeypadShapes.Field).background(KeypadColors.Surface1),
        )
    }
}

/** Stylus events (S Pen) become pen events on the PC; they are consumed so the finger gestures never see them.
 * While the pen is near, fingers are consumed too: that is the hand resting on the screen. */
private fun Modifier.stylus(vm: KeypadViewModel, t: ViewTransform): Modifier = pointerInput(t) {
    val palm = PalmGuard()
    awaitPointerEventScope {
        var down = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pens = event.changes.filter { it.type == PointerType.Stylus || it.type == PointerType.Eraser }
            val pen = pens.firstOrNull()
            if (pen == null) {
                if (palm.blocksFingers(event.changes.firstOrNull()?.uptimeMillis ?: 0L)) event.changes.forEach { it.consume() }
                continue
            }
            val state = when {
                event.type == PointerEventType.Exit -> "out"
                pen.pressed && !down -> "down"
                pen.pressed -> "move"
                down -> "up"
                else -> "hover"
            }
            down = pen.pressed
            palm.pen(state, pen.uptimeMillis)
            val point = t.toContent(pen.position.x, pen.position.y)
            if (point != null) vm.pen(state, point.first, point.second, if (pen.pressed) pen.pressure else 0f)
            else if (state == "up" || state == "out") vm.pen(state, 0.5f, 0.5f, 0f)
            pens.forEach { it.consume() }
        }
    }
}

/**
 * The magnifier: copies the small square of the screen around the cursor (what the phone already
 * shows) about 30 times a second and draws it larger in a round lens, with a mark on the cursor.
 */
@Composable
private fun LoupeLens(cursorX: Float, cursorY: Float, viewSize: IntSize, zoom: Float, origin: Offset) {
    val view = LocalView.current
    val density = LocalDensity.current
    val lensPx = with(density) { LoupeSize.toPx() }
    val layout = Loupe.layout(cursorX, cursorY, viewSize.width.toFloat(), viewSize.height.toFloat(), lensPx, zoom)
    val current by rememberUpdatedState(layout)
    val currentOrigin by rememberUpdatedState(origin)
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(zoom) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        val handler = Handler(Looper.getMainLooper())
        while (true) {
            val l = current
            val size = l.sourceSize.toInt().coerceAtLeast(1)
            val left = (currentOrigin.x + l.sourceLeft).toInt()
            val top = (currentOrigin.y + l.sourceTop).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val copied = suspendCancellableCoroutine { done ->
                runCatching {
                    PixelCopy.request(window, AndroidRect(left, top, left + size, top + size), bitmap,
                        { result -> if (done.isActive) done.resume(result == PixelCopy.SUCCESS) }, handler)
                }.onFailure { if (done.isActive) done.resume(false) }
            }
            if (copied) image = bitmap.asImageBitmap()
            delay(33)
        }
    }
    val lensDp = LoupeSize
    val ring = KeypadColors.Accent
    Box(
        Modifier.offset(with(density) { layout.lensLeft.toDp() }, with(density) { layout.lensTop.toDp() }).size(lensDp)
            .shadow(12.dp, CircleShape).clip(CircleShape).background(Color.Black).border(2.dp, ring, CircleShape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            image?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.High) }
            val mark = Offset(layout.markX, layout.markY)
            drawCircle(ring.copy(alpha = 0.9f), radius = 9.dp.toPx(), center = mark, style = Stroke(2.dp.toPx()))
            drawCircle(ring, radius = 2.dp.toPx(), center = mark)
        }
    }
}

private val LoupeSize = 190.dp

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** "Copiar texto da tela": drag a rectangle over the text; the fingers do nothing else meanwhile. */
@Composable
private fun TextSelection(t: ViewTransform, onDone: (FloatArray?) -> Unit) {
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }
    val done by rememberUpdatedState(onDone)
    BackHandler { done(null) }
    Box(
        Modifier.fillMaxSize().background(Color(0x55000000)).pointerInput(t) {
            awaitEachGesture {
                val down = awaitFirstDown()
                start = down.position
                end = down.position
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    end = change.position
                    change.consume()
                    if (!change.pressed) break
                }
                val a = start
                val b = end
                val region = if (a != null && b != null) TextGrab.region(a.x, a.y, b.x, b.y, t) else null
                start = null
                end = null
                if (region != null) done(region)
            }
        },
    ) {
        val a = start
        val b = end
        val accent = KeypadColors.Accent
        if (a != null && b != null) {
            Canvas(Modifier.fillMaxSize()) {
                val topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y))
                val size = androidx.compose.ui.geometry.Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y))
                drawRect(accent.copy(alpha = 0.18f), topLeft, size)
                drawRect(accent, topLeft, size, style = Stroke(2.dp.toPx()))
            }
        } else {
            Text(
                tr("Arraste sobre o texto que quer copiar", "Drag over the text you want to copy"),
                Modifier.align(Alignment.TopCenter).padding(top = 34.dp).clip(KeypadShapes.Card).background(KeypadColors.Surface2)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                style = KeypadType.Body, color = KeypadColors.Text,
            )
        }
        Key(tr("Cancelar", "Cancel"), { done(null) }, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).width(140.dp), height = 44.dp)
    }
}

/** The text read from the PC's screen: editable, then copied here or put on the PC's clipboard. */
@Composable
private fun ScreenTextSheet(vm: KeypadViewModel, text: String, onDismiss: () -> Unit) {
    var value by remember(text) { mutableStateOf(text) }
    AppSheet(tr("Texto da tela", "Screen text"), onDismiss) {
        OutlinedTextField(
            value, { value = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp),
            textStyle = KeypadType.Body.copy(color = KeypadColors.Text),
        )
        KeyRow {
            Key(tr("Copiar no celular", "Copy to phone"), { vm.copyToPhone(value); onDismiss() }, Modifier.weight(1f), height = 48.dp, on = true)
            Key(tr("Pôr no PC", "Send to PC"), { vm.sendClipboardToPc(value); onDismiss() }, Modifier.weight(1f), height = 48.dp)
        }
    }
}
