package com.sandevsystems.omarchyremote.network

/** The images an agent names (a path in what it said, or a file it read or wrote), to show them in its chat. */
object ImageRefs {
    private const val EXT = "png|jpe?g|webp|gif|svg"
    // A path without spaces ending in an image extension, not part of a link and not followed by more name.
    private val path = Regex("""(?<![\w/.:~-])((?:~/|/)?[\w.\-/]*\w\.(?:$EXT))(?!\w|\.\w)""", RegexOption.IGNORE_CASE)
    private val tools = setOf("Read", "Write", "Edit", "view_image")

    fun inText(text: String): List<String> = path.findAll(text)
        .filter { m -> m.range.first < 3 || !text.substring(maxOf(0, m.range.first - 3), m.range.first).endsWith("://") }
        .map { it.groupValues[1] }
        .distinct().take(6).toList()

    fun ofTool(name: String, target: String): String? =
        target.trim().takeIf { name in tools && path.matches(it) }
}
