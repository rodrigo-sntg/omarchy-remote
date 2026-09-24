package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.LauncherApp
import com.sandevsystems.omarchyremote.network.MenuItem
import com.sandevsystems.omarchyremote.network.OmarchyMenu
import org.junit.Assert.assertEquals
import org.junit.Test

class OmarchyMenuTest {
    private val menu = OmarchyMenu(
        listOf(
            MenuItem("apps", "", "A", "Apps", "apps"),
            MenuItem("system", "", "S", "System", "submenu"),
            MenuItem("system.lock", "system", "L", "Lock", "action"),
            MenuItem("style", "", "Y", "Style", "submenu"),
            MenuItem("style.theme", "style", "T", "Theme", "submenu", description = "Pick a theme"),
            MenuItem("setup.theme", "setup", "T", "Theme link", "link", target = "style.theme"),
            MenuItem("setup", "", "U", "Setup", "submenu"),
        ),
        listOf(LauncherApp("chromium", "Chromium"), LauncherApp("org.gnome.Nautilus", "Files")),
    )

    @Test
    fun childrenFollowTheTree() {
        assertEquals(listOf("apps", "system", "style", "setup"), menu.children("").map { it.id })
        assertEquals(listOf("system.lock"), menu.children("system").map { it.id })
    }

    @Test
    fun aLinkOpensItsTarget() {
        assertEquals("style.theme", menu.open(menu.byId("setup.theme")!!))
        assertEquals("system", menu.open(menu.byId("system")!!))
    }

    @Test
    fun searchFindsItemsAndAppsByWords() {
        assertEquals(listOf("style.theme", "setup.theme"), menu.search("theme").items.map { it.id })
        assertEquals(listOf("org.gnome.Nautilus"), menu.search("fil").apps.map { it.id })
        assertEquals(listOf("system.lock"), menu.search("LOCK").items.map { it.id })
    }

    @Test
    fun breadcrumbNamesTheWayDown() {
        assertEquals(listOf("Style", "Theme"), menu.path("style.theme").map { it.label })
        assertEquals(emptyList<String>(), menu.path("").map { it.label })
    }
}
