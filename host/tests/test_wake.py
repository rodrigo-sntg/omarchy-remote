from keypad_host.wake import interfaces_from

IP_ADDR = [
    {"ifname": "lo", "address": "00:00:00:00:00:00", "addr_info": [{"family": "inet", "local": "127.0.0.1", "prefixlen": 8}]},
    {"ifname": "enp14s0", "address": "02:00:00:00:00:01",
     "addr_info": [{"family": "inet", "local": "192.168.1.3", "prefixlen": 24, "broadcast": "192.168.1.255"},
                   {"family": "inet6", "local": "fe80::1", "prefixlen": 64}]},
    {"ifname": "wlp15s0", "address": "02:00:00:00:00:02",
     "addr_info": [{"family": "inet", "local": "192.168.2.20", "prefixlen": 24, "broadcast": "192.168.2.255"}]},
    {"ifname": "docker0", "address": "02:00:00:00:00:03", "addr_info": [{"family": "inet", "local": "172.17.0.1", "prefixlen": 16}]},
    {"ifname": "tailscale0", "addr_info": [{"family": "inet", "local": "100.64.0.10", "prefixlen": 32}]},
    {"ifname": "enp9s0", "address": "11:22:33:44:55:66", "addr_info": []},
]


def test_only_physical_interfaces_with_an_address_can_wake_the_pc():
    physical = {"enp14s0": False, "wlp15s0": True, "enp9s0": False}   # name -> wireless
    wol = {"enp14s0": True}
    got = interfaces_from(IP_ADDR, physical, wol)
    assert got == [
        {"name": "enp14s0", "mac": "02:00:00:00:00:01", "address": "192.168.1.3", "broadcast": "192.168.1.255", "wired": True, "wol": True},
        {"name": "wlp15s0", "mac": "02:00:00:00:00:02", "address": "192.168.2.20", "broadcast": "192.168.2.255", "wired": False, "wol": False},
    ]


def test_a_missing_broadcast_is_worked_out_from_the_prefix():
    ip = [{"ifname": "eth0", "address": "aa:bb:cc:dd:ee:ff", "addr_info": [{"family": "inet", "local": "10.0.3.7", "prefixlen": 22}]}]
    assert interfaces_from(ip, {"eth0": False}, {})[0]["broadcast"] == "10.0.3.255"
