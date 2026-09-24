package com.sandevsystems.omarchyremote.network

/** One row of Omarchy's Super+Space menu (host/keypad_host/omarchy_menu.py). kind: submenu, action, link, apps. */
data class MenuItem(
    val id: String,
    val parent: String,
    val icon: String,
    val label: String,
    val kind: String,
    val checked: Boolean = false,
    val description: String = "",
    val target: String = "",
)

/** A desktop app the launcher can start on the PC. */
data class LauncherApp(val id: String, val name: String)

/** The menu as the phone navigates it: children, links, search, breadcrumb. Pure. */
class OmarchyMenu(val items: List<MenuItem>, val apps: List<LauncherApp>) {
    private val byId = items.associateBy { it.id }

    fun byId(id: String) = byId[id]

    fun children(parent: String) = items.filter { it.parent == parent }

    /** The route a tap on a submenu or link goes to. */
    fun open(item: MenuItem): String = if (item.kind == "link" && item.target.isNotEmpty()) item.target else item.id

    data class Results(val items: List<MenuItem>, val apps: List<LauncherApp>)

    fun search(query: String): Results {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun matches(text: String) = words.all { text.lowercase().contains(it) }
        if (words.isEmpty()) return Results(emptyList(), emptyList())
        return Results(
            items.filter { matches(it.label + " " + it.description) },
            apps.filter { matches(it.name + " " + it.id) },
        )
    }

    fun path(route: String): List<MenuItem> {
        if (route.isEmpty()) return emptyList()
        val parts = route.split(".")
        return parts.indices.mapNotNull { byId[parts.take(it + 1).joinToString(".")] }
    }
}

/** An Omarchy keybinding the phone runs by pressing its keys (host/keypad_host/palette.py). */
data class Bind(val description: String, val keys: String, val stroke: com.sandevsystems.omarchyremote.input.KeyStroke)

/** A window on the PC. */
data class PcWindow(
    val address: String, val title: String, val app: String, val workspace: String,
    val floating: Boolean, val fullscreen: Boolean, val focused: Boolean,
)

/** The PC at a glance (host/keypad_host/now.py). */
data class NowState(
    val mediaTitle: String?, val mediaArtist: String?, val playing: Boolean, val reminder: String?, val update: Boolean,
    /** The player playing it ("brave", "spotify"): "Tocando no Brave". */
    val mediaPlayer: String? = null,
)
