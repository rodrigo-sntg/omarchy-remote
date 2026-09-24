package com.sandevsystems.omarchyremote.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sandevsystems.omarchyremote.KeypadViewModel
import com.sandevsystems.omarchyremote.network.PcLock

const val UNLOCK_COMMAND = "sudo omarchy-remote-unlock-setup"

/** The PC is locked: unlock it with the fingerprint, or set that up. Shown above the trackpad. */
@Composable
fun PcLockedCard(vm: KeypadViewModel, lock: PcLock, onSetup: () -> Unit) {
    val view = LocalView.current
    val activity: Activity? = androidx.activity.compose.LocalActivity.current
    val ready = lock.enrolled && lock.rule
    val busy = vm.unlockStep != KeypadViewModel.Unlock.IDLE
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(KeypadColors.Surface1)
            .border(1.dp, KeypadColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(KeypadColors.Accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Glyph.Lock, null, Modifier.size(20.dp), tint = KeypadColors.Accent)
        }
        Column(Modifier.weight(1f)) {
            Text(tr("O PC está bloqueado", "The PC is locked"), style = GroupType.Lead.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text)
            Text(
                when {
                    vm.unlockStep == KeypadViewModel.Unlock.FINGER -> tr("Toque no sensor de digital", "Touch the fingerprint sensor")
                    busy -> tr("Desbloqueando…", "Unlocking…")
                    ready -> tr("Com a sua digital, sem digitar a senha", "With your fingerprint, no password")
                    else -> tr("Configure para desbloquear pela digital", "Set it up to unlock with your fingerprint")
                },
                style = GroupType.Sub, color = KeypadColors.TextDim,
            )
        }
        Box(
            Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (ready) KeypadColors.Accent else KeypadColors.Surface3)
                .clickable(enabled = !busy, onClickLabel = if (ready) tr("Desbloquear", "Unlock") else tr("Configurar", "Set up")) {
                    Haptic.tap(view)
                    if (ready && activity != null) vm.unlockPc(activity) else onSetup()
                }.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = KeypadColors.OnAccent, strokeWidth = 2.dp)
            else Text(if (ready) tr("Desbloquear", "Unlock") else tr("Configurar", "Set up"),
                style = GroupType.Sub.copy(fontWeight = FontWeight.SemiBold), color = if (ready) KeypadColors.OnAccent else KeypadColors.Text)
        }
    }
}

/** Setting up the fingerprint unlock: this phone's key (confirmed at the PC) and the lock's rule (sudo, once). */
@Composable
fun UnlockSetupSheet(vm: KeypadViewModel, lock: PcLock?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    AppSheet(tr("Desbloquear o PC pela digital", "Unlock the PC with your fingerprint"), onDismiss,
        subtitle = tr("Sem a senha do PC sair dele", "Without the PC's password ever leaving it")) {
        Group {
            UnlockStep(1, tr("Chave deste celular", "This phone's key"),
                if (lock?.enrolled == true) tr("Cadastrada no PC", "Enrolled on the PC")
                else tr("Com o PC desbloqueado, toque em Cadastrar e confirme no PC", "With the PC unlocked, tap Enroll and confirm on the PC"),
                done = lock?.enrolled == true) {
                if (lock?.enrolled != true) {
                    val busy = vm.unlockStep == KeypadViewModel.Unlock.ENROLLING
                    Box(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(KeypadColors.Text)
                        .clickable(enabled = !busy) { Haptic.tap(view); vm.enrollUnlock() }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center) {
                        Text(if (busy) tr("Confirme no PC…", "Confirm on the PC…") else tr("Cadastrar", "Enroll"),
                            style = GroupType.Sub.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Bg)
                    }
                }
            }
            GroupDivider(56.dp)
            UnlockStep(2, tr("Regra no bloqueio do PC", "Rule in the PC's lock"),
                if (lock?.rule == true) tr("Instalada", "Installed") else tr("Rode uma vez no terminal do PC:", "Run once in the PC's terminal:"),
                done = lock?.rule == true) {}
            if (lock?.rule != true) {
                Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, bottom = 14.dp).clip(RoundedCornerShape(12.dp))
                    .background(KeypadColors.Surface3).clickable {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("cmd", UNLOCK_COMMAND))
                        vm.showMessage(tr("Comando copiado.", "Command copied."))
                    }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(UNLOCK_COMMAND, Modifier.weight(1f), style = KeypadType.Mono, color = KeypadColors.Text)
                    Icon(Glyph.Copy, tr("Copiar", "Copy"), Modifier.size(16.dp), tint = KeypadColors.TextDim)
                }
            }
        }
        Text(
            tr("Como funciona: o PC manda um desafio, o celular assina com uma chave que só a sua digital libera, e o PC digita no bloqueio um código de uso único que vale 60 segundos. Sua senha continua funcionando como sempre e nunca sai do PC.",
                "How it works: the PC sends a challenge, the phone signs it with a key only your fingerprint unlocks, and the PC types a one-time code, valid for 60 seconds, into the lock. Your password keeps working as always and never leaves the PC."),
            Modifier.padding(horizontal = 4.dp), style = GroupType.Sub, color = KeypadColors.TextDim,
        )
        Text(tr("Para desfazer: sudo omarchy-remote-unlock-setup --remove", "To undo: sudo omarchy-remote-unlock-setup --remove"),
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp), style = GroupType.Small, color = KeypadColors.TextMute)
    }
}

@Composable
private fun UnlockStep(n: Int, title: String, detail: String, done: Boolean, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(if (done) KeypadColors.Ok else KeypadColors.Surface3), contentAlignment = Alignment.Center) {
            if (done) Icon(Glyph.Check, null, Modifier.size(14.dp), tint = KeypadColors.Bg)
            else Text("$n", style = GroupType.Small.copy(fontWeight = FontWeight.Bold), color = KeypadColors.Text)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = GroupType.Title, color = KeypadColors.Text)
            Text(detail, style = GroupType.Sub, color = KeypadColors.TextDim)
        }
        action()
    }
}
