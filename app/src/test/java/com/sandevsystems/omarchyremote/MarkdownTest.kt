package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.ui.Md
import com.sandevsystems.omarchyremote.ui.inlineSpans
import com.sandevsystems.omarchyremote.ui.markdownBlocks
import com.sandevsystems.omarchyremote.ui.suggestedCommands
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
    @Test
    fun blocksComeApart() {
        val md = """
## O que mudou

A lupa segue o cursor.
Agora com **negrito**.

- um
- dois

```kotlin
val x = 1
```

| Tela | ok |
|------|----|
| A    | B  |
""".trimIndent()
        assertEquals(
            listOf(
                Md.Heading("O que mudou"),
                Md.Para("A lupa segue o cursor. Agora com **negrito**."),
                Md.Bullet("um"), Md.Bullet("dois"),
                Md.Code("val x = 1", "kotlin"),
                Md.Code("| Tela | ok |\n|------|----|\n| A    | B  |"),
            ),
            markdownBlocks(md),
        )
    }

    @Test
    fun numberedItemsKeepTheirNumber() {
        assertEquals(listOf(Md.Bullet("fazer isso", "1."), Md.Bullet("depois aquilo", "2.")), markdownBlocks("1. fazer isso\n2. depois aquilo"))
    }

    @Test
    fun inlineCodeAndBoldAreMarkedAndTheMarksGo() {
        val (text, spans) = inlineSpans("roda `./gradlew test` e **confere** o log")
        assertEquals("roda ./gradlew test e confere o log", text)
        assertEquals(listOf(Triple("code", 5, 19), Triple("bold", 22, 29)), spans)
    }

    @Test
    fun anUnclosedMarkIsJustText() {
        assertEquals("a ** b ` c" to emptyList<Triple<String, Int, Int>>(), inlineSpans("a ** b ` c"))
    }

    @Test
    fun italicIsMarkedButBoldStaysBold() {
        assertEquals("disse isso e aquilo" to listOf(Triple("italic", 6, 10), Triple("bold", 13, 19)), inlineSpans("disse *isso* e **aquilo**"))
        assertEquals("2 * 3 * 4" to emptyList<Triple<String, Int, Int>>(), inlineSpans("2 * 3 * 4"))
    }

    @Test
    fun theCommandsAReplySuggestsComeInOrder() {
        val reply = """
Rode isto:

```
! systemctl --user restart omarchy-remote-host.service
```

Depois:

```bash
# confere
${'$'} omarchy-remote status
git log -1
```

```kotlin
val x = 1
```

```
só um texto
```
""".trimIndent()
        assertEquals(listOf("systemctl --user restart omarchy-remote-host.service", "omarchy-remote status", "git log -1"), suggestedCommands(reply))
    }

    @Test
    fun aLongScriptIsNoSuggestion() {
        assertEquals(emptyList<String>(), suggestedCommands("```bash\n" + (1..6).joinToString("\n") { "echo $it" } + "\n```"))
    }
}
