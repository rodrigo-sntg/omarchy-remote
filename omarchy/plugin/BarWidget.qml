import QtQuick
import Quickshell
import Quickshell.Io
import qs.Commons
import qs.Ui

// Omarchy Remote in the Omarchy bar: lit while the phone is connected, with its battery and the
// herdr agents waiting in the tooltip. Reads the host's state file; never talks to the phone.
BarWidget {
  id: root
  moduleName: "rodrigo.phone"

  property bool hostUp: false
  property bool connected: false
  property string phone: ""
  property var battery: null
  property bool charging: false
  property int waiting: 0

  implicitWidth: button.implicitWidth
  implicitHeight: button.implicitHeight

  function apply(text) {
    try {
      var state = JSON.parse(text)
      root.hostUp = true
      root.connected = state.connected === true
      root.phone = state.phone || ""
      root.battery = state.battery
      root.charging = state.charging === true
      root.waiting = state.waiting || 0
    } catch (error) {
      root.hostUp = false
    }
  }

  function tooltip() {
    if (!root.hostUp) return "Omarchy Remote: serviço parado"
    if (!root.connected) return "Nenhum celular conectado · clique para parear"
    var parts = [root.phone]
    if (root.battery !== null && root.battery !== undefined) parts.push(root.battery + "%" + (root.charging ? " carregando" : ""))
    if (root.waiting > 0) parts.push(root.waiting + (root.waiting === 1 ? " agente esperando" : " agentes esperando"))
    return parts.join(" · ")
  }

  FileView {
    path: Quickshell.env("XDG_RUNTIME_DIR") + "/omarchy-remote/state.json"
    watchChanges: true
    printErrors: false
    onLoaded: root.apply(text())
    onLoadFailed: root.hostUp = false
    onFileChanged: reload()
  }

  BarIconButton {
    id: button
    anchors.fill: parent
    bar: root.bar
    // nf-md-cellphone / cellphone-message when agents wait / cellphone-off when disconnected
    text: !root.connected ? "󰄟" : (root.waiting > 0 ? "󰢫" : "󰄜")
    active: root.connected && root.waiting > 0
    dimmed: !root.connected
    tooltipText: root.tooltip()
    onPressed: function(b) {
      if (!root.bar) return
      if (b === Qt.MiddleButton) root.bar.run("omarchy-remote send-clipboard")
      else root.bar.run("omarchy-menu summon phone")
    }
  }
}
