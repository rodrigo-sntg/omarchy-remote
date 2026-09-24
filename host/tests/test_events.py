import asyncio
import json

from aiohttp.test_utils import TestClient, TestServer

from keypad_host.events import DesktopEvents, relevant
from keypad_host.server import TOKEN_HEADER, create_app
from tests.test_server import PHONE, TAILNET, TOKEN
from tests.test_session import FakeInjector

MONITORS = [{"name": "DP-1", "x": 0, "y": 0, "width": 1920, "height": 1080, "scale": 1.0,
             "activeWorkspace": {"id": 1}, "focused": True}]


def test_only_desktop_layout_events_matter():
    assert relevant("workspace>>2")
    assert relevant("focusedmon>>DP-1,2")
    assert relevant("openwindow>>abc,2,kitty,title")
    assert relevant("monitoradded>>HDMI-A-1")
    assert not relevant("activewindow>>kitty,title")
    assert not relevant("windowtitle>>abc")
    assert not relevant("")


class CountingHyprland:
    def __init__(self):
        self.monitor_reads = 0
        self.cursor_reads = 0

    def monitors(self):
        self.monitor_reads += 1
        return MONITORS

    def workspaces(self):
        return [{"id": 1, "monitor": "DP-1", "windows": 1}]

    def cursor_pos(self):
        self.cursor_reads += 1
        return 10, 10


def run(scenario, hyprland, events):
    async def main():
        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, hyprland=hyprland, events=events)
        async with TestClient(TestServer(app)) as client:
            await scenario(client)

    asyncio.run(main())


def frame(session, seq, type_, payload):
    return json.dumps({"v": 1, "sessionId": session, "seq": seq, "type": type_, "payload": payload})


def test_layout_is_read_on_events_not_all_the_time_and_the_cursor_only_when_watched():
    hyprland = CountingHyprland()
    events = DesktopEvents()

    async def scenario(client):
        ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        hello = await ws.receive_json()
        await asyncio.sleep(0.6)
        assert hyprland.monitor_reads == 1  # once at start, then quiet: nothing changed
        events.changed.set()
        await asyncio.sleep(0.3)
        assert hyprland.monitor_reads == 2
        await ws.send_str(frame(hello["sessionId"], 1, "watch", {"cursor": False}))
        await asyncio.sleep(0.2)
        reads = hyprland.cursor_reads
        await asyncio.sleep(0.4)
        assert hyprland.cursor_reads == reads  # map hidden on the phone: no cursor polling
        await ws.close()

    run(scenario, hyprland, events)


def test_watch_is_validated():
    import pytest
    from keypad_host.protocol import ProtocolError, parse
    assert parse(frame("s", 1, "watch", {"cursor": True})).payload == {"cursor": True}
    with pytest.raises(ProtocolError):
        parse(frame("s", 1, "watch", {"cursor": 1}))
