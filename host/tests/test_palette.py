import asyncio
import json

import pytest

from keypad_host.palette import bind_for_phone, binds_for_phone, windows_for_phone
from keypad_host.protocol import ProtocolError, parse

BINDS = [
    {"description": "Close window", "modmask": 64, "key": "W", "mouse": False, "release": False, "submap": ""},
    {"description": "Terminal", "modmask": 64, "key": "RETURN", "mouse": False, "release": False, "submap": ""},
    {"description": "Omarchy menu", "modmask": 64, "key": "SPACE", "mouse": False, "release": False, "submap": ""},
    {"description": "Move window to workspace 3", "modmask": 65, "key": "3", "mouse": False, "release": False, "submap": ""},
    {"description": "Volume up", "modmask": 0, "key": "XF86AudioRaiseVolume", "mouse": False, "release": False, "submap": ""},
    {"description": "Move window", "modmask": 64, "key": "mouse:272", "mouse": True, "release": False, "submap": ""},
    {"description": "", "modmask": 64, "key": "Q", "mouse": False, "release": False, "submap": ""},
]


def test_binds_become_key_presses_the_phone_can_send():
    assert bind_for_phone(BINDS[0]) == {"description": "Close window", "keys": "Super + W", "usage": 0x1A, "modifiers": 0x08}
    assert bind_for_phone(BINDS[1])["usage"] == 0x28
    assert bind_for_phone(BINDS[3]) == {"description": "Move window to workspace 3", "keys": "Super + Shift + 3", "usage": 0x20, "modifiers": 0x0A}


def test_media_mouse_and_undescribed_binds_are_left_out():
    assert [b["description"] for b in binds_for_phone(BINDS)] == ["Close window", "Move window to workspace 3", "Omarchy menu", "Terminal"]


CLIENTS = [
    {"address": "0x1a", "title": "notes.md - Neovim", "class": "Alacritty", "workspace": {"id": 2, "name": "2"}, "monitor": 1,
     "floating": False, "fullscreen": 0, "focusHistoryID": 0, "mapped": True, "hidden": False},
    {"address": "0x2b", "title": "", "class": "chromium", "workspace": {"id": 1, "name": "1"}, "monitor": 0,
     "floating": True, "fullscreen": 0, "focusHistoryID": 1, "mapped": True, "hidden": False},
    {"address": "0x3c", "title": "hidden", "class": "x", "workspace": {"id": -98, "name": "special:scratchpad"}, "monitor": 0,
     "floating": False, "fullscreen": 0, "focusHistoryID": 2, "mapped": True, "hidden": True},
]


def test_windows_are_grouped_by_workspace_with_the_focused_one_marked():
    windows = windows_for_phone(CLIENTS)
    assert [w["address"] for w in windows] == ["0x2b", "0x1a", "0x3c"]
    assert windows[1] == {"address": "0x1a", "title": "notes.md - Neovim", "app": "Alacritty", "workspace": "2",
                          "floating": False, "fullscreen": False, "focused": True}
    assert windows[0]["title"] == "chromium"  # untitled: the app's name


def test_window_actions_are_validated():
    ok = parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "window.act", "payload": {"address": "0x1a", "action": "workspace", "workspace": 3}}))
    assert ok.payload == {"address": "0x1a", "action": "workspace", "workspace": 3}
    for payload in ({"address": "1a", "action": "focus"}, {"address": "0x1a", "action": "rm"}, {"address": "0x1a;x", "action": "focus"},
                    {"address": "0x1a", "action": "workspace", "workspace": 0}):
        with pytest.raises(ProtocolError):
            parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "window.act", "payload": payload}))


def test_palette_and_windows_over_the_control_channel():
    from aiohttp.test_utils import TestClient, TestServer
    from keypad_host.server import TOKEN_HEADER, create_app
    from tests.test_server import PHONE, TAILNET, TOKEN
    from tests.test_session import FakeInjector

    class FakeHyprland:
        def __init__(self):
            self.actions = []

        def monitors(self):
            return []

        def workspaces(self):
            return []

        def cursor_pos(self):
            return 0, 0

        def binds(self):
            return BINDS

        def clients(self):
            return CLIENTS

        def window_act(self, address, action, workspace=None):
            self.actions.append((address, action, workspace))

    hyprland = FakeHyprland()

    async def main():
        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, hyprland=hyprland)
        async with TestClient(TestServer(app)) as client:
            ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
            sid = (await ws.receive_json())["sessionId"]

            async def reply(type_):
                while True:
                    m = await asyncio.wait_for(ws.receive_json(), 2)
                    if m["type"] == type_:
                        return m

            for seq, type_, payload in ((1, "binds.get", {}), (2, "windows.get", {}), (3, "window.act", {"address": "0x1a", "action": "workspace", "workspace": 3})):
                await ws.send_str(json.dumps({"v": 1, "sessionId": sid, "seq": seq, "type": type_, "payload": payload}))
            binds, windows, acked = await reply("binds"), await reply("windows"), await reply("ack")
            await ws.close()
            return binds, windows, acked

    binds, windows, acked = asyncio.run(main())
    assert binds["binds"][0]["description"] == "Close window"
    assert windows["windows"][1]["focused"] is True
    assert acked == {"type": "ack", "seq": 3, "ok": True}
    assert hyprland.actions == [("0x1a", "workspace", 3)]
