package com.sandevsystems.omarchyremote.bluetooth

import com.sandevsystems.omarchyremote.input.KeyStroke

object HidReports {
    const val KEYBOARD_ID = 1
    const val MOUSE_ID = 2

    // Keyboard: modifiers, reserved, six usages; output: keyboard LEDs.
    // Mouse: three buttons, signed relative X/Y and vertical wheel.
    val descriptor = intArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xa1, 0x01, 0x85, KEYBOARD_ID,
        0x05, 0x07, 0x19, 0xe0, 0x29, 0xe7, 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x75, 0x08, 0x95, 0x01, 0x81, 0x01,
        0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x75, 0x01, 0x95, 0x05, 0x91, 0x02,
        0x75, 0x03, 0x95, 0x01, 0x91, 0x01,
        0x05, 0x07, 0x19, 0x00, 0x29, 0x73, 0x15, 0x00, 0x25, 0x73,
        0x75, 0x08, 0x95, 0x06, 0x81, 0x00, 0xc0,
        0x05, 0x01, 0x09, 0x02, 0xa1, 0x01, 0x85, MOUSE_ID,
        0x09, 0x01, 0xa1, 0x00,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, 0x95, 0x03, 0x81, 0x02,
        0x75, 0x05, 0x95, 0x01, 0x81, 0x01,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
        0x15, 0x81, 0x25, 0x7f, 0x75, 0x08, 0x95, 0x03, 0x81, 0x06,
        0xc0, 0xc0,
    ).map(Int::toByte).toByteArray()

    fun keyboard(key: KeyStroke? = null): ByteArray = byteArrayOf(
        (key?.modifiers ?: 0).toByte(), 0, (key?.usage ?: 0).toByte(), 0, 0, 0, 0, 0,
    )

    fun mouse(buttons: Int, dx: Int = 0, dy: Int = 0, wheel: Int = 0): ByteArray =
        byteArrayOf((buttons and 7).toByte(), dx.toByte(), dy.toByte(), wheel.toByte())

    /** Keyboard LED output report; some hosts prepend the report ID. Bit 1 is Caps Lock. */
    fun capsLock(output: ByteArray): Boolean {
        val leds = when (output.size) {
            0 -> return false
            1 -> output[0]
            else -> output[1]
        }
        return leds.toInt() and 0x02 != 0
    }

    /** Split large motions rather than overflowing an 8-bit signed axis. */
    fun mouseMotion(buttons: Int, dx: Int, dy: Int, wheel: Int = 0): List<ByteArray> {
        var x = dx
        var y = dy
        var w = wheel
        val reports = mutableListOf<ByteArray>()
        while (x != 0 || y != 0 || w != 0) {
            val nextX = x.coerceIn(-127, 127)
            val nextY = y.coerceIn(-127, 127)
            val nextW = w.coerceIn(-127, 127)
            reports += mouse(buttons, nextX, nextY, nextW)
            x -= nextX
            y -= nextY
            w -= nextW
        }
        return reports
    }
}
