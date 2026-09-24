package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.input.KeyStroke
import com.sandevsystems.omarchyremote.input.ModifierKeys.SHIFT
import com.sandevsystems.omarchyremote.input.ModifierKeys.SUPER
import com.sandevsystems.omarchyremote.input.Workspaces
import org.junit.Assert.assertEquals
import org.junit.Test

/** Omarchy default binds (default/hypr/bindings/tiling.lua): SUPER + keys 1..0, SUPER [+ SHIFT] + TAB. */
class WorkspacesTest {
    @Test
    fun numberedWorkspacesUseSuperAndDigitRow() {
        assertEquals(KeyStroke(0x1e, SUPER), Workspaces.switchTo(1))
        assertEquals(KeyStroke(0x26, SUPER), Workspaces.switchTo(9))
        assertEquals(KeyStroke(0x27, SUPER), Workspaces.switchTo(10)) // key 0
        assertEquals((1..10).toList(), Workspaces.numbers)
    }

    @Test
    fun nextAndPreviousUseSuperTab() {
        assertEquals(KeyStroke(0x2b, SUPER), Workspaces.next)
        assertEquals(KeyStroke(0x2b, SUPER or SHIFT), Workspaces.previous)
    }
}
