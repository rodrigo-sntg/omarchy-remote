package com.sandevsystems.omarchyremote.ui

import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sandevsystems.omarchyremote.KeypadViewModel
import com.sandevsystems.omarchyremote.network.ShareRules

/** Another app shared something: what exactly goes to the PC, and a clear yes or no. */
@Composable
fun ShareConfirmSheet(vm: KeypadViewModel, share: KeypadViewModel.PendingShare) {
    val context = LocalContext.current
    val title = when {
        share.files.isNotEmpty() -> if (share.files.size == 1) tr("Enviar este arquivo ao PC?", "Send this file to the PC?")
            else tr("Enviar ${share.files.size} arquivos ao PC?", "Send ${share.files.size} files to the PC?")
        share.openLink -> tr("Abrir este link no PC?", "Open this link on the PC?")
        else -> tr("Copiar este texto para o PC?", "Copy this text to the PC?")
    }
    val names = remember(share) {
        share.files.take(5).map { uri ->
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: tr("arquivo", "file")
        }
    }
    AppSheet(title, { vm.pendingShare = null }, subtitle = tr("Veio de outro app pelo Compartilhar", "It came from another app through Share")) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KeypadColors.Surface1).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (share.text != null) {
                Text(ShareRules.preview(share.text), style = KeypadType.Mono, color = KeypadColors.Text, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
            names.forEach { name ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Glyph.File, null, Modifier.size(16.dp), tint = KeypadColors.TextDim)
                    Text(name, style = KeypadType.Body, color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (share.files.size > names.size) Text(tr("e mais ${share.files.size - names.size}", "and ${share.files.size - names.size} more"),
                style = KeypadType.Caption, color = KeypadColors.TextMute)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ActionButton(tr("Cancelar", "Cancel"), { vm.pendingShare = null }, Modifier.weight(1f), primary = false, height = KeypadDimens.DockHeight)
            ActionButton(
                when {
                    share.files.isNotEmpty() -> tr("Enviar", "Send")
                    share.openLink -> tr("Abrir", "Open")
                    else -> tr("Copiar", "Copy")
                },
                vm::confirmShare, Modifier.weight(1f), height = KeypadDimens.DockHeight,
            )
        }
    }
}
