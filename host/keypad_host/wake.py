"""What the phone needs to wake this PC with a magic packet (Wake-on-LAN): each physical interface's
MAC, IPv4 address and broadcast, and whether NetworkManager has wake-on-LAN on for it."""
import ipaddress
import json
import os
import subprocess

NET = "/sys/class/net"


def interfaces_from(ip_addr: list, physical: dict, wol: dict) -> list[dict]:
    """[physical] maps an interface name to "wireless?"; [wol] maps a name to "magic packet on?"."""
    out = []
    for iface in ip_addr:
        name = iface.get("ifname")
        if name not in physical or not iface.get("address"):
            continue
        for a in iface.get("addr_info", []):
            if a.get("family") != "inet":
                continue
            broadcast = a.get("broadcast") or str(ipaddress.ip_network(f"{a['local']}/{a['prefixlen']}", strict=False).broadcast_address)
            out.append({"name": name, "mac": iface["address"], "address": a["local"], "broadcast": broadcast,
                        "wired": not physical[name], "wol": bool(wol.get(name))})
            break
    return out


def _physical() -> dict:
    return {n: os.path.isdir(f"{NET}/{n}/wireless") for n in os.listdir(NET) if os.path.exists(f"{NET}/{n}/device")}


def _nmcli(*args) -> str:
    try:
        return subprocess.run(["nmcli", *args], capture_output=True, text=True, timeout=3).stdout.strip()
    except (OSError, subprocess.TimeoutExpired):
        return ""


def _wol(names) -> dict:
    out = {}
    for name in names:
        conn = _nmcli("-g", "GENERAL.CONNECTION", "device", "show", name)
        if not conn:
            continue
        key = "802-11-wireless.wake-on-wlan" if os.path.isdir(f"{NET}/{name}/wireless") else "802-3-ethernet.wake-on-lan"
        out[name] = "magic" in _nmcli("-g", key, "connection", "show", conn)
    return out


def interfaces() -> list[dict]:
    try:
        ip_addr = json.loads(subprocess.run(["ip", "-j", "addr"], capture_output=True, text=True, timeout=3).stdout or "[]")
    except (OSError, subprocess.TimeoutExpired, json.JSONDecodeError):
        return []
    physical = _physical()
    return interfaces_from(ip_addr, physical, _wol(physical))
