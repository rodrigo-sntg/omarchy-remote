package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.Accounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsTest {
    @Test
    fun theOwnAccountHasNoMark() {
        assertNull(Accounts.slot(""))
        assertNull(Accounts.slot("  "))
    }

    @Test
    fun anAccountAlwaysGetsTheSameColor() {
        val studio = Accounts.slot("studio")!!
        assertEquals(studio, Accounts.slot("studio"))
        assertTrue(studio in 0 until Accounts.COLORS)
    }

    @Test
    fun differentAccountsUsuallyDiffer() {
        val slots = listOf("studio", "work", "team", "acme").map { Accounts.slot(it) }.toSet()
        assertTrue(slots.size >= 3)
    }
}
