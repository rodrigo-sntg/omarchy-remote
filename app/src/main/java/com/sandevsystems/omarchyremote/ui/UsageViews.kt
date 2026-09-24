package com.sandevsystems.omarchyremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandevsystems.omarchyremote.KeypadViewModel
import com.sandevsystems.omarchyremote.network.UsageMetric
import com.sandevsystems.omarchyremote.network.UsageProvider
import com.sandevsystems.omarchyremote.network.UsageState
import com.sandevsystems.omarchyremote.network.UsageText
import kotlinx.coroutines.delay
import java.time.Instant

/** Fine, getting tight, spent: the same three steps everywhere a limit shows. */
internal fun severity(percent: Int): Color = when {
    percent >= 90 -> KeypadColors.Danger
    percent >= 70 -> KeypadColors.Warn
    else -> KeypadColors.Accent
}

/** Keeps asking while on screen (the host caches for a minute) and ticks the clock for the countdowns. */
@Composable
internal fun rememberUsage(vm: KeypadViewModel): Pair<UsageState?, Instant> {
    val state by vm.usage.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshUsage()
            now = Instant.now()
            delay(30_000)
        }
    }
    return state to now
}

/**
 * The bar: how much is used, and a thin mark for how much of the window has passed — past the mark
 * means burning faster than the time.
 */
@Composable
internal fun Meter(percent: Int, elapsed: Int?, height: Dp, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().height(height + 6.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(KeypadColors.Line)) {
            Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().clip(RoundedCornerShape(height / 2)).background(severity(percent)))
        }
        if (elapsed != null) {
            Box(Modifier.offset(x = maxWidth * (elapsed / 100f) - 1.dp).width(2.dp).fillMaxHeight().clip(RoundedCornerShape(1.dp))
                .background(KeypadColors.Text.copy(alpha = 0.8f)))
        }
    }
}

/** "Uso das IAs": every window of every plan, with its reset and pace. */
@Composable
fun UsageSheet(vm: KeypadViewModel, onDismiss: () -> Unit) {
    val (state, now) = rememberUsage(vm)
    AppSheet(tr("Uso das IAs", "AI usage"), onDismiss, subtitle = tr("Seus planos de IA, lidos no PC", "Your AI plans, read on the PC")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(2.dp).height(12.dp).background(KeypadColors.Text.copy(alpha = 0.8f)))
            Text(tr("O traço marca quanto do tempo já passou. Barra além dele: gastando mais rápido que o ritmo.", "The tick marks how much of the time has passed. A bar past it means you're burning faster than the pace."),
                style = KeypadType.Caption, color = KeypadColors.TextDim)
        }
        when {
            state == null -> Text(tr("Lendo os limites no PC…", "Reading limits on the PC…"), style = KeypadType.Body, color = KeypadColors.TextMute)
            !state.available -> Text(tr("O ai-usagebar não está instalado no PC.", "ai-usagebar isn't installed on the PC."), style = KeypadType.Body, color = KeypadColors.TextMute)
            state.error != null && state.providers.isEmpty() -> Text(state.error, style = KeypadType.Body, color = KeypadColors.Danger)
            else -> for (p in state.providers) ProviderCard(p, now)
        }
        if ((state?.hidden ?: 0) > 0) {
            Text(tr("${state!!.hidden} provedores sem dados (não configurados no ai-usagebar).", "${state!!.hidden} providers with no data (not set up in ai-usagebar)."), style = KeypadType.Caption, color = KeypadColors.TextMute)
        }
        ActionKey(tr("Atualizar agora", "Refresh now"), { vm.refreshUsage(force = true) })
    }
}

@Composable
private fun ProviderCard(p: UsageProvider, now: Instant) {
    Column(
        Modifier.fillMaxWidth().clip(KeypadShapes.Card).background(KeypadColors.Surface2).border(1.dp, KeypadColors.Line, KeypadShapes.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(p.name, style = KeypadType.SheetTitle.copy(fontSize = KeypadType.HostName.fontSize * 1.1f), color = KeypadColors.Text)
            if (p.plan.isNotBlank()) Text(p.plan, style = KeypadType.Caption, color = KeypadColors.TextMute)
            Spacer(Modifier.weight(1f))
            if (p.stale) Text(tr("desatualizado", "outdated"), style = KeypadType.Mono, color = KeypadColors.Warn)
        }
        if (p.error != null) Text(p.error, style = KeypadType.Caption, color = KeypadColors.Danger)
        for (m in p.metrics) {
            val big = m.kind != "model"
            Column(verticalArrangement = Arrangement.spacedBy(if (big) 8.dp else 5.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(UsageText.label(m), style = if (big) KeypadType.Key else KeypadType.Caption, color = KeypadColors.Text)
                    Spacer(Modifier.weight(1f))
                    Text("${m.percent}%", style = if (big) KeypadType.MonoValue else KeypadType.Mono, color = severity(m.percent))
                }
                Meter(m.percent, m.elapsed, if (big) 10.dp else 6.dp)
                Row {
                    val reset = m.resetAt?.let { UsageText.reset(it, now) }.orEmpty()
                    Text(if (m.percent >= 100) tr("esgotado · $reset", "used up · $reset") else reset, style = KeypadType.Caption,
                        color = if (m.percent >= 100) KeypadColors.Danger else KeypadColors.TextDim)
                    Spacer(Modifier.weight(1f))
                    if (big) UsageText.pace(m.percent, m.elapsed)?.let { pace ->
                        Text(pace, style = KeypadType.Caption, color = if (pace.contains(tr("acima", "ahead"))) severity(m.percent) else KeypadColors.TextDim)
                    }
                }
            }
        }
        if (p.resets > 0) {
            Box(
                Modifier.fillMaxWidth().clip(KeypadShapes.Field).background(KeypadColors.AccentTint06).border(1.dp, KeypadColors.AccentBorder35, KeypadShapes.Field)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(if (p.resets == 1) tr("1 recarga completa disponível", "1 full reset available") else tr("${p.resets} recargas completas disponíveis", "${p.resets} full resets available"),
                    style = KeypadType.Key.copy(fontSize = KeypadType.KeySmall.fontSize), color = KeypadColors.Accent)
            }
        }
    }
}
