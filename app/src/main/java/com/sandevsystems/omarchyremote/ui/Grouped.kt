package com.sandevsystems.omarchyremote.ui

import com.sandevsystems.omarchyremote.network.Accounts
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The grouped lists of the "Refino Apple" designs (canvas "Novo app", 3B/4B/5C): a large title, small
 * uppercase section labels, rounded groups of rows with an icon tile, title and one line under it.
 */
object GroupType {
    val LargeTitle = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.4).sp)
    val Title = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp)
    val Lead = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp)
    val Sub = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val Small = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val Label = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp)
    val Chat = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp)
}

val GroupShape = RoundedCornerShape(20.dp)

/** The place's name, big, and one line of context (which may color a part: who needs you). */
@Composable
fun LargeTitle(title: String, subtitle: AnnotatedString?, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = GroupType.LargeTitle, color = KeypadColors.Text, maxLines = 1)
            if (subtitle != null) Text(subtitle, Modifier.padding(top = 4.dp), style = GroupType.Lead, color = KeypadColors.TextDim)
        }
        trailing?.invoke()
    }
}

/** A section's small uppercase name over its group, with an optional "Detalhes ›" at the right. */
@Composable
fun SectionLabel(text: String, color: Color = KeypadColors.TextDim, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp).heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), Modifier.weight(1f), style = GroupType.Label, color = color)
        if (action != null) {
            Row(
                Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = action, onClick = onAction).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(action, style = GroupType.Sub, color = KeypadColors.Text)
                Icon(Glyph.ChevronRight, null, Modifier.size(12.dp), tint = KeypadColors.Text)
            }
        }
    }
}

@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(GroupShape).background(KeypadColors.Surface1), content = content)
}

/** The hairline between rows, starting where the text does. */
@Composable
fun GroupDivider(start: Dp = 16.dp) {
    Box(Modifier.fillMaxWidth().padding(start = start).height(1.dp).background(KeypadColors.Line))
}

/** A row: optional icon tile, title and one line under it, a chevron (or what [trailing] puts there). */
@Composable
fun GroupRow(
    title: String, subtitle: String?, onClick: () -> Unit, icon: ImageVector? = null, minHeight: Dp = 60.dp,
    subtitleColor: Color = KeypadColors.TextDim, trailing: (@Composable () -> Unit)? = null,
    /** An account other than the person's own: a thin bar in its color on the left, its name before the subtitle. */
    account: String = "",
) {
    val view = LocalView.current
    val mark = accountColor(account)
    Row(
        Modifier.fillMaxWidth().heightIn(min = minHeight).clickable(onClickLabel = title) { Haptic.tap(view); onClick() }
            .then(if (mark != null) Modifier.accountBar(mark) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(KeypadColors.Surface3), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = KeypadColors.Text)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = GroupType.Title, color = KeypadColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null || mark != null) Text(buildAnnotatedString {
                if (mark != null) {
                    withStyle(SpanStyle(color = mark, fontWeight = FontWeight.SemiBold)) { append(account.trim()) }
                    if (subtitle != null) append(" · ")
                }
                if (subtitle != null) append(subtitle)
            }, style = GroupType.Sub, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else Icon(Glyph.ChevronRight, null, Modifier.size(16.dp), tint = KeypadColors.TextMute)
    }
}

/** iOS's segmented control: one choice of a few, the current one raised. */
@Composable
fun Segmented(labels: List<String>, selected: Int, description: String, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(KeypadColors.Surface3).horizontalScroll(rememberScrollState()).padding(2.dp)
            .semantics { contentDescription = description },
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.widthIn(min = 44.dp).height(36.dp).clip(RoundedCornerShape(8.dp)).background(if (on) KeypadColors.Line2 else Color.Transparent)
                    .clickable(onClickLabel = label) { if (!on) { Haptic.tap(view); onSelect(i) } }.semantics { this.selected = on }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = GroupType.Sub.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = KeypadColors.Text) }
        }
    }
}

/** The round 44 dp buttons of the headers (back, terminal, new). */
@Composable
fun CircleButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    Box(
        modifier.size(KeypadDimens.MinTouch).clip(CircleShape).background(KeypadColors.Surface1)
            .clickable(onClickLabel = description) { Haptic.tap(view); onClick() }.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = KeypadColors.Text) }
}


/**
 * The color of an account other than the person's own (Accounts), or null for their own: soft
 * hues that read on dark and light themes, none of them the theme's accent or a status color.
 */
@Composable
fun accountColor(account: String): Color? {
    val slot = Accounts.slot(account) ?: return null
    val dark = !KeypadColors.Light
    return when (slot) {
        0 -> if (dark) Color(0xFF7FC8F8) else Color(0xFF1F6FA8)  // sky
        1 -> if (dark) Color(0xFFC3A6F7) else Color(0xFF6B47B8)  // violet
        2 -> if (dark) Color(0xFFF2B880) else Color(0xFFA35A12)  // amber
        3 -> if (dark) Color(0xFFF5A3C0) else Color(0xFFB0336A)  // rose
        else -> if (dark) Color(0xFF8FD9C4) else Color(0xFF1E7D66)  // teal
    }
}

/** A 3 dp bar of [color] along the left edge, inset from the corners. */
fun Modifier.accountBar(color: Color): Modifier = drawBehind {
    val w = 3.dp.toPx()
    val inset = 10.dp.toPx()
    drawRoundRect(color, topLeft = Offset(0f, inset), size = Size(w, size.height - 2 * inset), cornerRadius = CornerRadius(w / 2))
}
