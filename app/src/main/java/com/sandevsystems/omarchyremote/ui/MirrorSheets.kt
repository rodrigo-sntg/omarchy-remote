package com.sandevsystems.omarchyremote.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.sandevsystems.omarchyremote.KeypadViewModel
import com.sandevsystems.omarchyremote.NotificationMirror

/** Why the phone's notifications need Android's notification access, and a way there. */
@Composable
fun MirrorAccessSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AppSheet(tr("Notificações do celular no PC", "Phone notifications on the PC"), onDismiss) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(KeypadColors.Surface1), contentAlignment = Alignment.Center) {
                Icon(Glyph.Phone, null, Modifier.size(26.dp), tint = KeypadColors.Text)
            }
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i -> Box(Modifier.size(6.dp).clip(CircleShape).background(if (i == 2) KeypadColors.Accent else KeypadColors.Line2)) }
            }
            Box {
                Box(Modifier.size(width = 84.dp, height = 56.dp).clip(RoundedCornerShape(12.dp)).background(KeypadColors.Surface1), contentAlignment = Alignment.Center) {
                    Icon(Glyph.Monitor, null, Modifier.size(26.dp), tint = KeypadColors.Text)
                }
                Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp).size(width = 30.dp, height = 12.dp)
                    .clip(RoundedCornerShape(6.dp)).background(KeypadColors.Accent))
            }
        }
        Group {
            MirrorPoint(Glyph.Check, text = tr("Mensagens, e-mails e lembretes aparecem no PC, com Responder quando o app permite.",
                "Messages, email and reminders show on the PC, with Reply when the app allows it."))
            GroupDivider(52.dp)
            MirrorPoint(Glyph.Close, neutral = true, text = tr("Não vão: as deste app, as fixas (música, downloads) e as dos apps que você desligar.",
                "Never: this app's, ongoing ones (music, downloads) and those of apps you turn off."))
            GroupDivider(52.dp)
            MirrorPoint(Glyph.Lock, text = tr("Só enquanto o celular está conectado, pelo Tailscale. Nada fica guardado no PC.",
                "Only while the phone is connected, over Tailscale. Nothing is kept on the PC."))
        }
        Text(tr("O Android pede para liberar o acesso às notificações a este app.", "Android asks you to allow notification access for this app."),
            Modifier.padding(top = 4.dp, start = 4.dp), style = GroupType.Sub, color = KeypadColors.TextDim)
        ActionButton(tr("Permitir nos Ajustes", "Allow in Settings"), {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, NotificationMirror::class.java).flattenToString())
            } else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            runCatching { context.startActivity(intent) }.onFailure { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            onDismiss()
        }, Modifier.fillMaxWidth(), height = KeypadDimens.DockHeight)
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = tr("Agora não", "Not now"), onClick = onDismiss), contentAlignment = Alignment.Center) {
            Text(tr("Agora não", "Not now"), style = KeypadType.Body, color = KeypadColors.TextDim)
        }
    }
}

@Composable
private fun MirrorPoint(icon: androidx.compose.ui.graphics.vector.ImageVector, neutral: Boolean = false, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = if (neutral) KeypadColors.TextMute else KeypadColors.Accent) }
        Text(text, style = GroupType.Sub.copy(fontWeight = FontWeight.Medium), color = KeypadColors.Text)
    }
}

/** Which apps' notifications go to the PC: the ones that notified here, each with a switch. */
@Composable
fun MirrorAppsSheet(vm: KeypadViewModel, onDismiss: () -> Unit) {
    val apps = remember { vm.mirrorApps() }
    val context = LocalContext.current
    val view = LocalView.current
    AppSheet(tr("Quais apps vão para o PC", "Which apps go to the PC"), onDismiss,
        subtitle = tr("Os que já mostraram notificação neste celular", "The ones that already notified on this phone"), tall = true) {
        if (apps.isEmpty()) {
            Text(tr("Nenhum app notificou desde que você ligou. Eles aparecem aqui conforme chegam.",
                "No app has notified since you turned it on. They show up here as they arrive."), style = KeypadType.Body, color = KeypadColors.TextMute)
            return@AppSheet
        }
        Group {
            apps.forEachIndexed { i, (pkg, name) ->
                if (i > 0) GroupDivider(64.dp)
                val on = pkg !in vm.mirrorExcluded
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { Haptic.tap(view); vm.setMirrorApp(pkg, !on) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val icon = remember(pkg) { runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                    if (icon != null) Image(icon, null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                    else Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(KeypadColors.Surface3))
                    Text(name, Modifier.weight(1f), style = GroupType.Title, color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Switch(on, { Haptic.tap(view); vm.setMirrorApp(pkg, it) }, colors = SwitchDefaults.colors(
                        checkedTrackColor = KeypadColors.Accent, checkedThumbColor = KeypadColors.OnAccent, checkedBorderColor = KeypadColors.Accent,
                        uncheckedTrackColor = KeypadColors.Surface3, uncheckedThumbColor = KeypadColors.TextDim, uncheckedBorderColor = KeypadColors.Line2))
                }
            }
        }
        Box(Modifier.width(1.dp))
    }
}
