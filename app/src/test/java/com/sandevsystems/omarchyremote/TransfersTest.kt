package com.sandevsystems.omarchyremote

import com.sandevsystems.omarchyremote.network.Transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransfersTest {
    private fun t(incoming: Boolean, done: Long = 0, total: Long? = null, state: Transfer.State = Transfer.State.RUNNING, where: String? = null, error: String? = null) =
        Transfer(1, "polissonografia.pdf", incoming, done, total, state, where, error = error)

    @Test fun runningShowsPercentAndSizes() {
        assertEquals("Trazendo · 45% · 3,2 de 7,0 MB", t(true, 3_355_443, 7_340_032).status())
        assertEquals("Enviando · 10% · 100 de 1000 KB", t(false, 102_400, 1_024_000).status())
    }

    @Test fun unknownSizeShowsWhatCameSoFar() {
        assertEquals("Trazendo · 2,0 MB", t(true, 2_097_152).status())
        assertEquals("Trazendo…", t(true).status())
    }

    @Test fun doneSaysWhere() {
        assertEquals("No celular · Download/Omarchy Remote", t(true, state = Transfer.State.DONE, where = "Download/Omarchy Remote").status())
        assertEquals("No PC · ~/Downloads", t(false, state = Transfer.State.DONE, where = "~/Downloads").status())
    }

    @Test fun failedSaysWhy() {
        assertEquals("Falhou: a oferta expirou", t(true, state = Transfer.State.FAILED, error = "a oferta expirou").status())
    }

    @Test fun percentOnlyWithTotal() {
        assertEquals(0.5f, t(true, 50, 100).fraction)
        assertNull(t(true, 50).fraction)
    }

    @Test fun pcPathShownFromHome() {
        assertEquals("~/Downloads", Transfer.pcFolder("/home/rodrigo/Downloads/a.pdf"))
        assertEquals("/mnt/x", Transfer.pcFolder("/mnt/x/a.pdf"))
    }
}
