package com.sandevsystems.omarchyremote.ui

// Tokens do Design v2 (design/DESIGN.md), copiados de design/compose/DesignTokens.kt; fontes em
// res/font (design/compose/font).

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sandevsystems.omarchyremote.R

/**
 * Design v2 colors. They are snapshot state: [apply] swaps in the PC's Omarchy theme and every
 * screen recomposes; without a theme the design's own graphite + lime stays (ThemePalette.DEFAULT).
 */
object KeypadColors {
    var Bg by mutableStateOf(Color(0xFF0B0C0D))
        private set
    var Surface1 by mutableStateOf(Color(0xFF121517))
        private set
    var Surface2 by mutableStateOf(Color(0xFF16191B))
        private set
    var Surface3 by mutableStateOf(Color(0xFF1D2124))
        private set
    var Line by mutableStateOf(Color(0xFF2B3034))
        private set
    var Line2 by mutableStateOf(Color(0xFF3A4045))
        private set
    var Text by mutableStateOf(Color(0xFFECEEF0))
        private set
    var TextDim by mutableStateOf(Color(0xFF99A1A8))
        private set
    var TextMute by mutableStateOf(Color(0xFF808890))
        private set
    var Accent by mutableStateOf(Color(0xFFC5F24A))
        private set
    var OnAccent by mutableStateOf(Color(0xFF0B0C0D))
        private set
    var Danger by mutableStateOf(Color(0xFFE8674B))
        private set
    /** Connected, working: green on every theme (the accent may be grey). */
    var Ok by mutableStateOf(Color(0xFF4CC38A))
        private set
    /** Needs the person: amber on every theme. */
    var Attention by mutableStateOf(Color(0xFFE3B341))
        private set
    /** A light Omarchy theme is in use (dark system bar icons, Material light defaults). */
    var Light by mutableStateOf(false)
        private set
    val Scrim = Color(0x99060708)
    /** Between fine (accent) and critical (danger): e.g. a plan limit past 70 %. */
    val Warn get() = Attention

    val AccentTint06 get() = Accent.copy(alpha = 0.06f)
    val AccentTint12 get() = Accent.copy(alpha = 0.12f)
    val AccentTint16 get() = Accent.copy(alpha = 0.16f)
    val AccentBorder35 get() = Accent.copy(alpha = 0.35f)
    val AccentBorder60 get() = Accent.copy(alpha = 0.60f)
    val DotGrid get() = Line.copy(alpha = 0.75f)
    val KeyHighlight = Color.White.copy(alpha = 0.06f)

    fun apply(p: Palette) {
        Bg = Color(p.bg); Surface1 = Color(p.surface1); Surface2 = Color(p.surface2); Surface3 = Color(p.surface3)
        Line = Color(p.line); Line2 = Color(p.line2); Text = Color(p.text); TextDim = Color(p.textDim); TextMute = Color(p.textMute)
        Accent = Color(p.accent); OnAccent = Color(p.onAccent); Danger = Color(p.danger); Light = p.light; Ok = Color(p.ok); Attention = Color(p.attention)
    }
}

val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

object KeypadType {
    val SheetTitle = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 24.sp)
    val EmptyTitle = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp)
    val HostName = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val Key = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    val KeyCompact = Key.copy(fontSize = 14.sp)
    val KeySmall = Key.copy(fontSize = 13.sp)
    val Body = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val Caption = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp)
    val Overline = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.1.em)
    val MacroGlyph = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 17.sp)
    val MacroCaption = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 10.sp)
    val Mono = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 11.sp)
    val MonoValue = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    val PadLabel = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.16.em)
}

object KeypadShapes {
    val Key = RoundedCornerShape(13.dp)
    val Macro = RoundedCornerShape(14.dp)
    val Field = RoundedCornerShape(14.dp)
    val SegmentTrack = RoundedCornerShape(15.dp)
    val Segment = RoundedCornerShape(12.dp)
    val Dock = RoundedCornerShape(17.dp)
    val Card = RoundedCornerShape(20.dp)
    val Trackpad = RoundedCornerShape(24.dp)
    val Layer = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

object KeypadDimens {
    val ScreenPadding = 14.dp
    val BlockGap = 11.dp
    val KeyGap = 7.dp
    val MinTouch = 48.dp  // Android's minimum touch target
    val StatusHeight = 40.dp
    val KeyHeight = 54.dp
    val KeyHeightSmall = 46.dp
    val ModifierStripKey = 42.dp
    val MacroHeight = 56.dp
    val ClickBarHeight = 62.dp
    val ClickBarCompact = 46.dp
    val DockHeight = 56.dp
    val DockSideButton = 64.dp
    val DockBottomInset = 14.dp
    val CursorMapHeight = 124.dp
    val DotGridStep = 26.dp
    val DotGridRadius = 1.1.dp
    val RemoteAnchor = 60.dp
    val RemoteAnchorInset = 16.dp
    val ArcButton = 56.dp
    val ArcRadius = 195.dp  // six 56 dp buttons over a quarter circle without touching
    val ArcLabelRadius = 242.dp
}

/** Base M3 para componentes que ainda usam o tema (Switch, Slider, ModalBottomSheet). */
/** Material colors from the current KeypadColors (read at composition, so they follow the theme). */
fun keypadColorScheme(): ColorScheme = (if (KeypadColors.Light) lightColorScheme() else darkColorScheme()).copy(
    primary = KeypadColors.Accent,
    onPrimary = KeypadColors.OnAccent,
    background = KeypadColors.Bg,
    onBackground = KeypadColors.Text,
    surface = KeypadColors.Surface2,
    onSurface = KeypadColors.Text,
    surfaceVariant = KeypadColors.Surface3,
    onSurfaceVariant = KeypadColors.TextDim,
    surfaceContainerLow = KeypadColors.Surface1,
    surfaceContainer = KeypadColors.Surface2,
    surfaceContainerHigh = KeypadColors.Surface3,
    outline = KeypadColors.Line2,
    outlineVariant = KeypadColors.Line,
    error = KeypadColors.Danger,
    scrim = KeypadColors.Scrim,
)
