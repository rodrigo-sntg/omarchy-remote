package com.sandevsystems.omarchyremote.network

/** A file or folder on the PC, as its browser lists it (host browse.py). */
data class PcFile(val name: String, val dir: Boolean, val size: Long, val mtime: Long)

/** A folder of the PC: where it is, its parent (null at the home), what is in it, and the usual folders. */
data class FolderListing(val path: String, val parent: String?, val items: List<PcFile>, val roots: List<Pair<String, String>>)

private val link = Regex("https?://[^\\s<>\"]+")

/** The first web link in a shared text (a page shared from a browser often comes with its title). */
fun firstLink(text: String): String? = link.find(text)?.value?.trimEnd('.', ',', ')', ';')
