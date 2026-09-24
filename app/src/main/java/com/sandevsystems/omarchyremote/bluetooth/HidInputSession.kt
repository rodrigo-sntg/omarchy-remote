package com.sandevsystems.omarchyremote.bluetooth

import com.sandevsystems.omarchyremote.bluetooth.HidReports.KEYBOARD_ID
import com.sandevsystems.omarchyremote.bluetooth.HidReports.MOUSE_ID
import com.sandevsystems.omarchyremote.input.KeyStroke
import com.sandevsystems.omarchyremote.input.RemoteInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Input for one HID connection. [send] delivers one report (ID separate from the payload).
 * After [close] nothing else is sent, so pending text never reaches a later connection.
 */
class HidInputSession(private val send: (reportId: Int, data: ByteArray) -> Boolean) : RemoteInput {
    private val textLock = Mutex()
    private val keyLock = Mutex()
    private var buttons = 0

    @Volatile
    private var open = true

    private fun report(id: Int, data: ByteArray) = open && send(id, data)

    override fun movePointer(dx: Int, dy: Int) {
        HidReports.mouseMotion(buttons, dx, dy).forEach { report(MOUSE_ID, it) }
    }

    override fun scroll(steps: Int) {
        HidReports.mouseMotion(buttons, 0, 0, steps).forEach { report(MOUSE_ID, it) }
    }

    override fun setMouseButtons(buttons: Int) {
        this.buttons = buttons
        report(MOUSE_ID, HidReports.mouse(buttons))
    }

    /** A single key waits for text in progress, so it never lands in the middle of it. */
    override suspend fun tapKey(key: KeyStroke): Boolean = textLock.withLock { tap(key) }

    private suspend fun tap(key: KeyStroke): Boolean = keyLock.withLock {
        if (!report(KEYBOARD_ID, HidReports.keyboard(key))) {
            report(KEYBOARD_ID, HidReports.keyboard())
            return@withLock false
        }
        var released = false
        try {
            delay(KEY_DELAY_MS)
        } finally {
            // Also runs on cancellation: a key is never left pressed.
            released = report(KEYBOARD_ID, HidReports.keyboard())
        }
        delay(KEY_DELAY_MS)
        released
    }

    override suspend fun type(keys: List<KeyStroke>): Int = textLock.withLock {
        var sent = 0
        for (key in keys) {
            if (!tap(key)) break
            sent++
        }
        sent
    }

    override fun releaseAll() {
        buttons = 0
        report(KEYBOARD_ID, HidReports.keyboard())
        report(MOUSE_ID, HidReports.mouse(0))
    }

    fun close() {
        releaseAll()
        open = false
    }

    companion object {
        /** Initial press/release spacing; to be measured on hardware, not a protocol requirement. */
        const val KEY_DELAY_MS = 12L
    }
}
