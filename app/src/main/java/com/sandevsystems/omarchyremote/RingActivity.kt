package com.sandevsystems.omarchyremote

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandevsystems.omarchyremote.ui.Glyph
import com.sandevsystems.omarchyremote.ui.KeypadColors
import com.sandevsystems.omarchyremote.ui.tr

/**
 * "Find my phone" in full screen, over the lock screen too: one big Parar. Any volume or power key
 * stops it as well; it closes when the ringing stops (from here, the PC, or the 1-minute limit).
 */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            val ringing by Ringer.ringing.collectAsStateWithLifecycle()
            LaunchedEffect(ringing) { if (!ringing) finish() }
            RingScreen { Ringer.stop(this) }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            Ringer.stop(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun RingScreen(onStop: () -> Unit) {
    val t = rememberInfiniteTransition(label = "ring")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "pulse")
    val accent = KeypadColors.Accent
    Column(
        Modifier.fillMaxSize().background(KeypadColors.Bg).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2
                    for (i in 0..1) {
                        val p = (pulse + i * 0.5f) % 1f
                        drawCircle(accent.copy(alpha = 0.45f * (1 - p)), radius = r * (0.38f + 0.62f * p), style = Stroke(3.dp.toPx()))
                    }
                    drawCircle(accent.copy(alpha = 0.14f), radius = r * 0.36f)
                }
                Icon(Glyph.Phone, null, Modifier.size(64.dp), tint = accent)
            }
        }
        Text(tr("O PC está procurando\neste celular", "Your PC is looking\nfor this phone"), style = androidx.compose.ui.text.TextStyle(
            fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold), color = KeypadColors.Text, textAlign = TextAlign.Center)
        Text(tr("Os botões de volume também param.", "The volume buttons stop it too."),
            Modifier.padding(top = 12.dp, bottom = 32.dp), fontSize = 16.sp, color = KeypadColors.TextDim, textAlign = TextAlign.Center)
        Box(
            Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(24.dp)).background(accent).clickable(onClickLabel = tr("Parar", "Stop"), onClick = onStop),
            contentAlignment = Alignment.Center,
        ) { Text(tr("Parar", "Stop"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = KeypadColors.OnAccent) }
    }
}
