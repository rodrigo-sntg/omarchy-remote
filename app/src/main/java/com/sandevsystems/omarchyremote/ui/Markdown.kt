package com.sandevsystems.omarchyremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Markdown an agent writes, in the few forms a chat needs: headings, paragraphs, lists, code, tables. */
sealed interface Md {
    data class Heading(val text: String) : Md
    data class Para(val text: String) : Md
    data class Bullet(val text: String, val mark: String = "•") : Md
    /** Code and tables: kept as written, in a monospaced box. */
    data class Code(val code: String, val lang: String = "") : Md
}

private val heading = Regex("^#{1,6}\\s+(.*)$")
private val bullet = Regex("^\\s*[-*•]\\s+(.*)$")
private val numbered = Regex("^\\s*(\\d+[.)])\\s+(.*)$")

fun markdownBlocks(text: String): List<Md> {
    val lines = text.lines()
    val out = mutableListOf<Md>()
    val para = mutableListOf<String>()
    fun flush() {
        if (para.isNotEmpty()) out += Md.Para(para.joinToString(" ") { it.trim() })
        para.clear()
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                flush()
                val lang = line.trimStart().removePrefix("```").trim()
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) code += lines[i++]
                out += Md.Code(code.joinToString("\n").trimEnd(), lang)
            }
            line.trimStart().startsWith("|") -> {
                flush()
                val table = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) table += lines[i++].trim()
                out += Md.Code(table.joinToString("\n"))
                continue
            }
            line.isBlank() -> flush()
            heading.matches(line) -> {
                flush()
                out += Md.Heading(heading.find(line)!!.groupValues[1].trim())
            }
            bullet.matches(line) -> {
                flush()
                out += Md.Bullet(bullet.find(line)!!.groupValues[1].trim())
            }
            numbered.matches(line) -> {
                flush()
                val m = numbered.find(line)!!
                out += Md.Bullet(m.groupValues[2].trim(), m.groupValues[1])
            }
            // A wrapped list item's next line.
            line.startsWith("  ") && para.isEmpty() && out.lastOrNull() is Md.Bullet -> {
                val last = out.removeAt(out.size - 1) as Md.Bullet
                out += last.copy(text = last.text + " " + line.trim())
            }
            else -> para += line
        }
        i++
    }
    flush()
    return out
}

private val shellLangs = setOf("bash", "sh", "shell", "console", "zsh", "fish", "terminal")
private const val SCRIPT_LINES = 5

/** The shell commands of a code block, one per line ("$ " and "! " taken off, comments out); none if it is not one. */
fun commandsOf(code: Md.Code): List<String> {
    if (code.code.startsWith("|")) return emptyList()
    val lines = code.code.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    if (lines.isEmpty() || lines.size > SCRIPT_LINES) return emptyList()
    val shell = code.lang.lowercase() in shellLangs
    val marked = lines.all { it.startsWith("!") || it.startsWith("$") }
    if (!(shell || (code.lang.isBlank() && marked))) return emptyList()
    return lines.map { it.removePrefix("!").removePrefix("$").trim() }.filter { it.isNotEmpty() }
}

/** Every command a reply suggests, in the order it suggests them. */
fun suggestedCommands(text: String): List<String> =
    markdownBlocks(text).filterIsInstance<Md.Code>().flatMap { commandsOf(it) }.distinct()

/** The text without `code` and **bold** marks, and where each span went: (kind, start, end). */
fun inlineSpans(text: String): Pair<String, List<Triple<String, Int, Int>>> {
    val sb = StringBuilder()
    val spans = mutableListOf<Triple<String, Int, Int>>()
    var i = 0
    while (i < text.length) {
        if (text[i] == '`') {
            val close = text.indexOf('`', i + 1)
            if (close > i + 1) {
                spans += Triple("code", sb.length, sb.length + (close - i - 1))
                sb.append(text, i + 1, close)
                i = close + 1
                continue
            }
        } else if (text.startsWith("**", i)) {
            val close = text.indexOf("**", i + 2)
            if (close > i + 2) {
                spans += Triple("bold", sb.length, sb.length + (close - i - 2))
                sb.append(text, i + 2, close)
                i = close + 2
                continue
            }
        } else if (text[i] == '*' && i + 1 < text.length && !text[i + 1].isWhitespace()) {
            // *italic*: the marks touch the words ("2 * 3" is no italic).
            val close = text.indexOf('*', i + 1)
            if (close > i + 1 && !text[close - 1].isWhitespace() && !text.startsWith("**", close)) {
                spans += Triple("italic", sb.length, sb.length + (close - i - 1))
                sb.append(text, i + 1, close)
                i = close + 1
                continue
            }
        }
        sb.append(text[i])
        i++
    }
    return sb.toString() to spans
}

private fun annotated(text: String, codeBackground: Color): AnnotatedString {
    val (plain, spans) = inlineSpans(text)
    return AnnotatedString.Builder(plain).apply {
        for ((kind, start, end) in spans) {
            addStyle(
                when (kind) {
                    "code" -> SpanStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, background = codeBackground)
                    "italic" -> SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    else -> SpanStyle(fontWeight = FontWeight.SemiBold)
                },
                start, end,
            )
        }
    }.toAnnotatedString()
}

/** An agent's reply, formatted; its text can be selected and copied. [onRun]: its commands get a "Rodar". */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, onRun: ((List<String>) -> Unit)? = null) {
    val blocks = remember(text) { markdownBlocks(text) }
    val codeBg = KeypadColors.Surface3
    SelectionContainer(modifier) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in blocks) {
            when (b) {
                is Md.Heading -> Text(annotated(b.text, codeBg), style = GroupType.Title.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text)
                is Md.Para -> Text(annotated(b.text, codeBg), style = GroupType.Chat, color = KeypadColors.Text)
                is Md.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(b.mark, Modifier.widthIn(min = 16.dp), style = GroupType.Chat, color = KeypadColors.TextDim)
                    Text(annotated(b.text, codeBg), style = GroupType.Chat, color = KeypadColors.Text)
                }
                is Md.Code -> {
                    val commands = if (onRun != null) commandsOf(b) else emptyList()
                    CodeBox(b.code, b.lang.ifBlank { if (b.code.startsWith("|")) tr("tabela", "table") else tr("código", "code") },
                        onRun = if (commands.isNotEmpty()) { { onRun?.invoke(commands) } } else null)
                }
            }
        }
    } }
}

/**
 * Code as a developer reads it: monospaced, not wrapped (it scrolls sideways), with what it is and
 * a copy button on top. Long code shows its start until "Ver tudo".
 */
@Composable
fun CodeBox(code: String, label: String, modifier: Modifier = Modifier, color: Color = KeypadColors.Text, maxLines: Int = 40, onRun: (() -> Unit)? = null) {
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current
    var copied by remember { mutableStateOf(false) }
    var whole by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    val lines = remember(code) { code.lines() }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(KeypadColors.Surface1)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = KeypadType.Mono.copy(fontSize = 11.sp), color = KeypadColors.TextMute, maxLines = 1)
            if (onRun != null) {
                Row(
                    Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = tr("Rodar no terminal do agente", "Run in the agent's terminal")) {
                        Haptic.tap(view); onRun()
                    }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Glyph.Play, null, Modifier.size(13.dp), tint = KeypadColors.Text)
                    Text(tr("Rodar", "Run"), style = GroupType.Small.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text)
                }
            }
            Row(
                Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = tr("Copiar", "Copy")) {
                    clipboard.setText(AnnotatedString(code)); Haptic.tap(view); copied = true
                }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(if (copied) Glyph.Check else Glyph.Copy, null, Modifier.size(13.dp), tint = KeypadColors.TextDim)
                Text(if (copied) tr("Copiado", "Copied") else tr("Copiar", "Copy"), style = GroupType.Small, color = KeypadColors.TextDim)
            }
        }
        val cut = !whole && lines.size > maxLines
        Text(if (cut) lines.take(maxLines).joinToString("\n") else code,
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            style = KeypadType.Mono.copy(fontSize = 12.sp, lineHeight = 17.sp), color = color, softWrap = false)
        if (cut) {
            Text(tr("Ver tudo (${lines.size} linhas)", "See all (${lines.size} lines)"),
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { whole = true }.padding(horizontal = 12.dp, vertical = 10.dp),
                style = GroupType.Sub.copy(fontWeight = FontWeight.SemiBold), color = KeypadColors.Text)
        }
    }
}
