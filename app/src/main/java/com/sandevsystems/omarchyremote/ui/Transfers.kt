package com.sandevsystems.omarchyremote.ui

import android.os.SystemClock
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandevsystems.omarchyremote.KeypadViewModel
import com.sandevsystems.omarchyremote.network.Transfer
import kotlinx.coroutines.delay

/** How long a finished transfer stays on screen. */
private const val LINGER_MS = 8_000L

/** The transfers going now or just ended, over everything but sheets (they show their own). */
@Composable
fun TransferBanner(vm: KeypadViewModel, modifier: Modifier = Modifier) {
    val all by vm.transfers.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val shown = all.filter { it.endedAt == null || now - it.endedAt < LINGER_MS }.takeLast(3)
    LaunchedEffect(shown.isNotEmpty()) {
        while (shown.isNotEmpty()) { delay(1_000); now = SystemClock.elapsedRealtime() }
    }
    if (shown.isEmpty()) return
    Column(modifier.widthIn(max = 520.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (t in shown) TransferCard(t, { vm.openTransfer(t) }, { vm.dismissTransfer(t.id) })
    }
}

/** A file on its way: which way, its name, how far, and where it landed (with Abrir when it can open). */
@Composable
fun TransferCard(t: Transfer, onOpen: () -> Unit, onDismiss: (() -> Unit)? = null) {
    val view = LocalView.current
    val tint = when (t.state) {
        Transfer.State.DONE -> KeypadColors.Ok
        Transfer.State.FAILED -> KeypadColors.Danger
        Transfer.State.RUNNING -> KeypadColors.Text
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KeypadColors.Surface3)
            .border(1.dp, if (t.state == Transfer.State.FAILED) KeypadColors.Danger.copy(alpha = 0.5f) else KeypadColors.Line, RoundedCornerShape(16.dp))
            .then(if (onDismiss != null && t.state != Transfer.State.RUNNING) Modifier.clickable(onClickLabel = tr("Fechar", "Dismiss"), onClick = onDismiss) else Modifier)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                val f = t.fraction
                if (t.state == Transfer.State.RUNNING) {
                    if (f != null) CircularProgressIndicator(progress = { f }, Modifier.size(36.dp), color = KeypadColors.Text, trackColor = KeypadColors.Line2, strokeWidth = 3.dp)
                    else CircularProgressIndicator(Modifier.size(36.dp), color = KeypadColors.Text, trackColor = KeypadColors.Line2, strokeWidth = 3.dp)
                    Icon(if (t.incoming) Glyph.Download else Glyph.Upload, null, Modifier.size(16.dp), tint = KeypadColors.Text)
                } else {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        if (t.state == Transfer.State.DONE) Icon(Glyph.Check, null, Modifier.size(18.dp), tint = tint)
                        else Text("!", style = GroupType.Lead.copy(fontWeight = FontWeight.Bold), color = tint)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(t.name, style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                Text(t.status(), style = GroupType.Sub, color = if (t.state == Transfer.State.FAILED) KeypadColors.Danger else KeypadColors.TextDim,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (t.state == Transfer.State.DONE && t.incoming && t.uri != null) {
                Box(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(KeypadColors.Text)
                    .clickable(onClickLabel = tr("Abrir ${t.name}", "Open ${t.name}")) { Haptic.tap(view); onOpen() }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center) {
                    Text(tr("Abrir", "Open"), style = GroupType.Sub.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Bg)
                }
            }
        }
        if (t.state == Transfer.State.RUNNING) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(KeypadColors.Line)) {
                t.fraction?.let { Box(Modifier.fillMaxHeight().fillMaxWidth(it).background(KeypadColors.Text)) }
            }
        }
    }
}

