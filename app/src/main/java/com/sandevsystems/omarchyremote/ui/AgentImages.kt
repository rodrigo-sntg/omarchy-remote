package com.sandevsystems.omarchyremote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sandevsystems.omarchyremote.KeypadViewModel

private const val THUMB_SIDE = 720
private const val FULL_SIDE = 2400

/**
 * The images an agent named (ImageRefs), as thumbnails under its message; one that can't be shown
 * (not on the PC, not an image, a hidden folder) simply isn't there. A tap opens it full screen.
 */
@Composable
fun AgentImages(vm: KeypadViewModel, agent: String, paths: List<String>) {
    if (paths.isEmpty()) return
    var open by remember { mutableStateOf<String?>(null) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (path in paths) AgentImageThumb(vm, agent, path) { open = path }
    }
    open?.let { path -> ImageViewer(vm, agent, path) { open = null } }
}

@Composable
private fun AgentImageThumb(vm: KeypadViewModel, agent: String, path: String, onOpen: () -> Unit) {
    var image by remember(agent, path) { mutableStateOf<ImageBitmap?>(null) }
    var gone by remember(agent, path) { mutableStateOf(false) }
    LaunchedEffect(agent, path) {
        image = vm.agentImage(agent, path, THUMB_SIDE)
        gone = image == null
    }
    if (gone) return
    val view = LocalView.current
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(KeypadColors.Surface1)
            .clickable(onClickLabel = tr("Abrir a imagem", "Open the image")) { Haptic.tap(view); onOpen() },
    ) {
        val shown = image
        if (shown == null) {
            Box(Modifier.size(180.dp, 140.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), color = KeypadColors.TextDim, strokeWidth = 2.dp)
            }
        } else {
            // Its own proportions, 180 dp tall; very wide ones are cut to fit the screen.
            val width = (180f * shown.width / shown.height.coerceAtLeast(1)).coerceIn(90f, 320f)
            Image(shown, path, Modifier.size(width.dp, 180.dp).background(Color.White), contentScale = ContentScale.Fit)
        }
        Text(path.substringAfterLast('/'), Modifier.widthIn(max = 320.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            style = GroupType.Small, color = KeypadColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The image full screen: pinch to zoom, drag to move, double tap to zoom in or back; a tap outside or back closes. */
@Composable
private fun ImageViewer(vm: KeypadViewModel, agent: String, path: String, onClose: () -> Unit) {
    var image by remember(agent, path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(agent, path) { image = vm.agentImage(agent, path, FULL_SIDE) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF0000000)).safeDrawingPadding()) {
            val shown = image
            if (shown == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = KeypadColors.Text)
            else Image(
                shown, path,
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f },
                            onTap = { if (scale == 1f) onClose() },
                        )
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                contentScale = ContentScale.Fit,
            )
            Row(Modifier.align(Alignment.TopStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(0x99000000)).clickable(onClickLabel = tr("Fechar", "Close"), onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Glyph.Close, null, Modifier.size(16.dp), tint = Color.White) }
                Text(path, Modifier.height(20.dp), style = GroupType.Small, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
