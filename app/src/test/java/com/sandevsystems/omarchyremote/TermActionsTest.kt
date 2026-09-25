package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.input.TermActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermActionsTest {
    @Test
    fun onlyWhatThePcCanSendIsShown() {
        val groups = TermActions.groups(setOf("close_pane", "split_vertical", "new_tab", "prefix"))
        assertEquals(listOf("split_vertical", "close_pane"), groups[0].actions.map { it.id })
        assertEquals(listOf("new_tab"), groups[1].actions.map { it.id })
    }

    @Test
    fun emptyGroupsAreLeftOut() {
        assertTrue(TermActions.groups(setOf("prefix")).isEmpty())
        assertEquals(1, TermActions.groups(setOf("next_tab")).size)
    }

    @Test
    fun everyActionHasALabel() {
        val all = TermActions.groups(TermActions.all.map { it.id }.toSet()).flatMap { it.actions }
        assertEquals(TermActions.all.size, all.size)
        assertTrue(all.all { it.label.isNotBlank() })
    }
}
