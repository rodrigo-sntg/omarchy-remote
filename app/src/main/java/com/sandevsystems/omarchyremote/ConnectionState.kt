package com.sandevsystems.omarchyremote

sealed interface ConnectionState {
    data object PermissionRequired : ConnectionState
    data object BluetoothOff : ConnectionState
    data object Starting : ConnectionState
    /** Ready to connect (Bluetooth: HID app registered); no host connected yet. */
    data object Ready : ConnectionState
    data class Connecting(val host: Host) : ConnectionState
    data class Connected(val host: Host) : ConnectionState
    data class Disconnected(val reason: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class Host(val name: String, val address: String, val isComputer: Boolean = false)
