import asyncio

from aiohttp.test_utils import TestClient, TestServer

from keypad_host.desktop import cursor_on, monitor_map
from keypad_host.server import TOKEN_HEADER, create_app
from tests.test_server import PHONE, TAILNET, TOKEN
from tests.test_session import FakeInjector

MONITORS = [
    {"name": "DP-1", "x": 0, "y": 0, "width": 1920, "height": 1080, "scale": 1.0, "activeWorkspace": {"id": 2}},
    {"name": "HDMI-A-1", "x": 1920, "y": 0, "width": 3440, "height": 1440, "scale": 1.0, "activeWorkspace": {"id": 1}},
    {"name": "OMARCHYREMOTE", "x": 5360, "y": 0, "width": 2340, "height": 1080, "scale": 1.5, "activeWorkspace": {"id": 3}},
]


def test_monitor_map_is_logical_geometry_with_active_workspace_and_marks_the_extra_monitor():
    assert monitor_map(MONITORS) == [
        {"name": "DP-1", "x": 0, "y": 0, "width": 1920, "height": 1080, "workspace": 2, "extra": False},
        {"name": "HDMI-A-1", "x": 1920, "y": 0, "width": 3440, "height": 1440, "workspace": 1, "extra": False},
        {"name": "OMARCHYREMOTE", "x": 5360, "y": 0, "width": 1560, "height": 720, "workspace": 3, "extra": True},
    ]


def test_cursor_is_located_on_its_monitor_normalized():
    geometry = monitor_map(MONITORS)
    assert cursor_on(1920 + 1720, 720, geometry) == {"monitor": "HDMI-A-1", "x": 0.5, "y": 0.5}
    assert cursor_on(0, 0, geometry) == {"monitor": "DP-1", "x": 0.0, "y": 0.0}
    assert cursor_on(-5, 5000, geometry) is None


class DesktopHyprland:
    def __init__(self):
        self.cursor = (960, 540)

    def monitors(self):
        return MONITORS[:2]

    def workspaces(self):
        return [{"id": 1, "monitor": "HDMI-A-1", "windows": 1}, {"id": 2, "monitor": "DP-1", "windows": 3}]

    def cursor_pos(self):
        return self.cursor


def test_control_session_streams_monitors_and_cursor_without_video():
    async def main():
        hyprland = DesktopHyprland()

        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, hyprland=hyprland)
        async with TestClient(TestServer(app)) as client:
            ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
            received = {}
            while not {"monitors", "cursor", "workspaces"} <= received.keys():
                message = await asyncio.wait_for(ws.receive_json(), 2)
                received[message["type"]] = message
            assert [m["name"] for m in received["monitors"]["monitors"]] == ["DP-1", "HDMI-A-1"]
            assert received["cursor"] == {"type": "cursor", "monitor": "DP-1", "x": 0.5, "y": 0.5}
            hyprland.cursor = (1920 + 344, 144)  # the physical mouse moves onto HDMI-A-1
            while True:
                message = await asyncio.wait_for(ws.receive_json(), 2)
                if message["type"] == "cursor":
                    break
            assert message == {"type": "cursor", "monitor": "HDMI-A-1", "x": 0.1, "y": 0.1}
            await ws.close()

    asyncio.run(main())
