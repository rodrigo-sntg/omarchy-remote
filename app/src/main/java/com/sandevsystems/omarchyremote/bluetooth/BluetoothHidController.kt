package com.sandevsystems.omarchyremote.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.sandevsystems.omarchyremote.ConnectionState.BluetoothOff
import com.sandevsystems.omarchyremote.ConnectionState.Connected
import com.sandevsystems.omarchyremote.ConnectionState.Connecting
import com.sandevsystems.omarchyremote.ConnectionState.Disconnected
import com.sandevsystems.omarchyremote.ConnectionState.Error
import com.sandevsystems.omarchyremote.ConnectionState.PermissionRequired
import com.sandevsystems.omarchyremote.ConnectionState.Ready
import com.sandevsystems.omarchyremote.ConnectionState.Starting
import com.sandevsystems.omarchyremote.ConnectionState
import com.sandevsystems.omarchyremote.Host
import com.sandevsystems.omarchyremote.ui.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Exposes the phone as a Bluetooth HID keyboard + mouse. Callbacks run on the main executor,
 * so all fields are touched only from the main thread.
 *
 * Every Bluetooth call is behind [start], which returns early without BLUETOOTH_CONNECT; that is
 * why MissingPermission is suppressed on this class only.
 */
@SuppressLint("MissingPermission")
class BluetoothHidController(private val context: Context) {
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val _state = MutableStateFlow<ConnectionState>(Starting)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()
    private val _capsLock = MutableStateFlow(false)
    val capsLock: StateFlow<Boolean> = _capsLock.asStateFlow()

    private var hid: BluetoothHidDevice? = null
    private var registered = false
    private var host: BluetoothDevice? = null
    private var pendingHost: BluetoothDevice? = null
    private var closing = false
    private var proxyRequested = false
    /** False while another transport is in use; Bluetooth turning on must not re-register HID then. */
    private var enabled = true
    private var registering = false

    /** Input for the current connection only; replaced on every new connection. */
    var input: HidInputSession? = null
        private set

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                    closeProxy()
                    _state.value = BluetoothOff
                }
                BluetoothAdapter.STATE_ON -> if (enabled) start()
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context, bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun hasPermissions(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Opens the HID profile and registers the app; safe to call repeatedly. */
    fun start() {
        enabled = true
        val adapter = adapter
        when {
            adapter == null -> _state.value = Error(tr("Este aparelho não tem Bluetooth.", "This device has no Bluetooth."))
            !hasPermissions() -> _state.value = PermissionRequired
            !adapter.isEnabled -> _state.value = BluetoothOff
            registered || registering -> Unit
            hid != null -> register()
            proxyRequested -> Unit
            else -> {
                _state.value = Starting
                proxyRequested = adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
                if (!proxyRequested) _state.value = Error(tr("Este celular não consegue ser teclado Bluetooth. Use pela rede.", "This phone can't act as a Bluetooth keyboard. Use the network."))
            }
        }
    }

    fun bondedHosts(): List<Host> {
        if (!hasPermissions()) return emptyList()
        return adapter?.bondedDevices.orEmpty().map { it.toHost() }
            .sortedWith(compareByDescending<Host> { it.isComputer }.thenBy { it.name.lowercase() })
    }

    fun connect(address: String) {
        val device = adapter?.takeIf { hasPermissions() }?.getRemoteDevice(address) ?: return start()
        val hid = hid
        if (hid == null || !registered) {
            pendingHost = device
            return start()
        }
        pendingHost = null
        _state.value = Connecting(device.toHost())
        Log.i(TAG, "connect ${device.address}")
        if (!hid.connect(device)) _state.value = Error(tr("O Android não iniciou a conexão com ${device.toHost().name}.", "Android didn't start the connection to ${device.toHost().name}."))
    }

    fun disconnect() {
        pendingHost = null
        closeInput()
        host?.let { hid?.disconnect(it) }
    }

    /** Stops being a HID device (another transport is in use); [start] registers again. */
    fun stop() {
        enabled = false
        pendingHost = null
        closeProxy()
        _state.value = Starting
    }

    /** Final shutdown: releases input, disconnects, unregisters and closes the proxy. */
    fun close() {
        context.unregisterReceiver(bluetoothStateReceiver)
        closeProxy()
    }

    private fun register() {
        val hid = hid ?: return
        _state.value = Starting
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Omarchy Remote", "Teclado e trackpad", "Omarchy Remote",
            BluetoothHidDevice.SUBCLASS1_COMBO, HidReports.descriptor,
        )
        // true only means the request was sent; onAppStatusChanged confirms the registration.
        registering = hid.registerApp(sdp, null, null, context.mainExecutor, callback)
        if (!registering) _state.value = Error(tr("Outro app já está usando o celular como teclado Bluetooth. Feche-o e tente de novo.", "Another app is already using the phone as a Bluetooth keyboard. Close it and try again."))
    }

    private fun closeProxy() {
        closing = true
        closeInput()
        hid?.let { proxy ->
            host?.let { proxy.disconnect(it) }
            if (registered) proxy.unregisterApp()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
        }
        hid = null
        proxyRequested = false
        registering = false
        registered = false
        host = null
        closing = false
    }

    private fun closeInput() {
        input?.close()
        input = null
        _capsLock.value = false
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            hid = proxy as BluetoothHidDevice
            proxyRequested = false
            register()
        }

        override fun onServiceDisconnected(profile: Int) {
            closeInput()
            hid = null
            proxyRequested = false
            registering = false
            registered = false
            host = null
            if (!closing) _state.value = Error(tr("O Bluetooth do celular parou. Tente de novo.", "The phone's Bluetooth stopped. Try again."))
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.i(TAG, "onAppStatusChanged registered=$registered plugged=${pluggedDevice?.address}")
            this@BluetoothHidController.registered = registered
            registering = false
            if (closing || isShutDown()) return
            if (!registered) {
                closeInput()
                host = null
                _state.value = Disconnected(tr("O Android desligou o teclado Bluetooth quando o app saiu da tela.", "Android turned off the Bluetooth keyboard when the app left the screen."))
                return
            }
            if (_state.value !is Connected) _state.value = Ready
            pendingHost?.let { connect(it.address) }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.i(TAG, "onConnectionStateChanged ${device.address} state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTING -> _state.value = Connecting(device.toHost())
                BluetoothProfile.STATE_CONNECTED -> {
                    closeInput()
                    host = device
                    val session = HidInputSession { id, data -> hid?.sendReport(device, id, data) == true }
                    session.releaseAll() // a new connection starts from neutral reports
                    input = session
                    _state.value = Connected(device.toHost())
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasConnecting = _state.value is Connecting
                    if (host == device) {
                        closeInput()
                        host = null
                    }
                    if (closing || isShutDown()) return
                    _state.value = Disconnected(
                        if (wasConnecting) tr("Não conectou ao ${device.toHost().name}. Confira se o celular está pareado no Bluetooth do PC.", "Couldn't connect to ${device.toHost().name}. Check the phone is paired in the PC's Bluetooth.")
                        else tr("Desconectado de ${device.toHost().name}.", "Disconnected from ${device.toHost().name}."),
                    )
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val hid = hid ?: return
            val reply = when {
                type == BluetoothHidDevice.REPORT_TYPE_INPUT && id.toInt() == HidReports.KEYBOARD_ID -> HidReports.keyboard()
                type == BluetoothHidDevice.REPORT_TYPE_INPUT && id.toInt() == HidReports.MOUSE_ID -> HidReports.mouse(0)
                else -> null
            }
            if (reply != null) hid.replyReport(device, type, id, reply)
            else hid.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            val hid = hid ?: return
            if (type == BluetoothHidDevice.REPORT_TYPE_OUTPUT && id.toInt() == HidReports.KEYBOARD_ID) {
                _capsLock.value = HidReports.capsLock(data)
                hid.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
            } else {
                hid.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            }
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            if (reportId.toInt() == HidReports.KEYBOARD_ID) _capsLock.value = HidReports.capsLock(data)
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            Log.i(TAG, "onVirtualCableUnplug ${device.address}")
            if (host == device) closeInput()
            host = null
            _state.value = Disconnected(tr("${device.toHost().name} removeu o teclado virtual.", "${device.toHost().name} removed the virtual keyboard."))
        }
    }

    /** Late callbacks after Bluetooth was turned off or the proxy closed must not replace that state. */
    private fun isShutDown() = hid == null || adapter?.isEnabled != true

    private fun BluetoothDevice.toHost() = Host(
        name = name ?: address,
        address = address,
        isComputer = bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER,
    )

    companion object {
        private const val TAG = "KeypadHid"

        /** Requested together; only CONNECT is required to work, ADVERTISE is for becoming discoverable. */
        fun requestedPermissions(): List<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
            } else {
                emptyList()
            }
    }
}
