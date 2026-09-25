package com.sandevsystems.omarchyremote.network

/**
 * Which account an agent runs in (Claude's config dir: ~/.claude is the person's own, ~/.claude-<name>
 * another). The own one has no mark; each other gets one of a few colors, picked by its name so it
 * never changes between screens or days.
 */
object Accounts {
    const val COLORS = 5

    fun slot(account: String): Int? {
        val name = account.trim().lowercase()
        if (name.isEmpty()) return null
        var h = 7
        for (c in name) h = h * 31 + c.code
        return Math.floorMod(h, COLORS)
    }
}
