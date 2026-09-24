package com.sandevsystems.omarchyremote.ui

// Tokens do Design v2 (design/DESIGN.md). Copie para app/src/main/java/.../ui/ e as fontes de
// design/compose/font/ para app/src/main/res/font/.

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sandevsystems.omarchyremote.R

object KeypadColors {
    val Bg = Color(0xFF0B0C0D)
    val Surface1 = Color(0xFF121517)
    val Surface2 = Color(0xFF16191B)
    val Surface3 = Color(0xFF1D2124)
    val Line = Color(0xFF2B3034)
    val Line2 = Color(0xFF3A4045)
    val Text = Color(0xFFECEEF0)
    val TextDim = Color(0xFF99A1A8)
    val TextMute = Color(0xFF808890)
    val Accent = Color(0xFFC5F24A)
    val OnAccent = Color(0xFF0B0C0D)
    val Danger = Color(0xFFE8674B)
    val Scrim = Color(0x99060708)

    val AccentTint06 = Accent.copy(alpha = 0.06f)
    val AccentTint12 = Accent.copy(alpha = 0.12f)
    val AccentTint16 = Accent.copy(alpha = 0.16f)
    val AccentBorder35 = Accent.copy(alpha = 0.35f)
    val AccentBorder60 = Accent.copy(alpha = 0.60f)
    val DotGrid = Color(0xFF212629)
    val KeyHighlight = Color.White.copy(alpha = 0.06f)
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
    val BlockGap = 14.dp
    val OverlineGap = 8.dp
    val KeyGap = 7.dp
    val MinTouch = 44.dp
    val StatusHeight = 40.dp
    val KeyHeight = 52.dp
    val KeyHeightSmall = 46.dp
    val ModifierStripKey = 42.dp
    val MacroHeight = 56.dp
    val ClickBarHeight = 62.dp
    val ClickBarCompact = 44.dp
    val DockHeight = 56.dp
    val DockSideButton = 72.dp
    val DockVerticalWidth = 78.dp
    val WorkspacePillWidth = 44.dp
    val WorkspacePillHeight = 36.dp
    val SheetMaxWidthLandscape = 720.dp
    val DockBottomInset = 14.dp
    val CursorMapHeight = 108.dp
    val DotGridStep = 26.dp
    val DotGridRadius = 1.1.dp
    val RemoteAnchor = 60.dp
    val RemoteAnchorInset = 16.dp
    val ArcButton = 56.dp
    val ArcRadius = 150.dp
    val ArcLabelRadius = 196.dp
}

/** Base M3 para componentes que ainda usam o tema (Switch, Slider, ModalBottomSheet). */
val KeypadColorScheme: ColorScheme = darkColorScheme(
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
