import pytest

from tests.controller import controller
from keypad_host.display import DisplayError, Geometry
from keypad_host.pen import PEN_MAX, PRESSURE_MAX, parse_pen, pen_position

MONITORS = [Geometry(0, 0, 1920, 1080), Geometry(1920, 0, 3440, 1440)]


def test_a_point_on_a_monitor_lands_on_the_same_spot_of_the_whole_desktop():
    # A tablet maps to every output together: its range spans the bounding box of all monitors.
    assert pen_position(0.5, 0.5, MONITORS[1], MONITORS) == (round((1920 + 1720) / 5360 * PEN_MAX), round(720 / 1440 * PEN_MAX))
    assert pen_position(0.0, 0.0, MONITORS[0], MONITORS) == (0, 0)


def test_pen_messages_are_validated():
    assert parse_pen({"type": "pen", "state": "down", "x": 0.25, "y": 0.5, "pressure": 0.5}) == ("down", 0.25, 0.5, round(0.5 * PRESSURE_MAX))
    assert parse_pen({"type": "pen", "state": "hover", "x": 1, "y": 0})[3] == 0
    for bad in ({"type": "pen", "state": "zap", "x": 0, "y": 0}, {"type": "pen", "state": "down", "x": 2, "y": 0},
                {"type": "pen", "state": "down", "x": 0, "y": 0, "pressure": 3}):
        with pytest.raises(DisplayError):
            parse_pen(bad)


def test_pen_events_reach_the_pen_device_over_the_video_channel():
    import asyncio
    import json
    from aiohttp.test_utils import TestClient, TestServer
    from keypad_host.server import TOKEN_HEADER, create_app
    from tests.test_display_server import CursorHyprland, FakeCapture, MovingInjector
    from tests.test_server import PHONE, TAILNET, TOKEN

    class FakePen:
        def __init__(self):
            self.events = []

        def write(self, state, x, y, pressure):
            self.events.append((state, x, y, pressure))

    pen = FakePen()

    async def main():
        hyprland = CursorHyprland()

        async def capture(*_):
            return FakeCapture([b"\x00\x00\x00\x01\x67"])

        async def whois(_):
            return PHONE

        app = create_app(MovingInjector(hyprland), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, hyprland=hyprland, capture=capture,
                         pen=lambda: pen)
        async with TestClient(TestServer(app)) as client, controller(client):
            ws = await client.ws_connect("/v1/screen", headers={TOKEN_HEADER: TOKEN})
            await ws.receive_json()
            await ws.send_json({"type": "screen.start", "monitor": "DP-1", "maxWidth": 2340, "maxHeight": 1080})
            await ws.send_json({"type": "pen", "state": "down", "x": 0.5, "y": 0.5, "pressure": 1.0})
            await ws.send_json({"type": "pen", "state": "up", "x": 0.5, "y": 0.5, "pressure": 0})
            await ws.send_json({"type": "pen", "state": "down", "x": 0.5, "y": 0.5, "pressure": 1.0})
            await asyncio.sleep(0.2)
            await ws.close()

    asyncio.run(main())
    # The link dropped with the tip down: the pen leaves, or the PC would keep drawing.
    assert [e[0] for e in pen.events] == ["down", "up", "down", "out"] and pen.events[0][3] == PRESSURE_MAX


def test_a_light_touch_still_counts_as_the_tip_down():
    # libinput reads "tip down" from pressure: a light S Pen stroke must not look lifted mid-line.
    from keypad_host.pen import MIN_TIP_PRESSURE
    assert parse_pen({"type": "pen", "state": "move", "x": 0.5, "y": 0.5, "pressure": 0})[3] == MIN_TIP_PRESSURE
    assert parse_pen({"type": "pen", "state": "down", "x": 0.5, "y": 0.5, "pressure": 0.01})[3] == MIN_TIP_PRESSURE
    assert parse_pen({"type": "pen", "state": "hover", "x": 0.5, "y": 0.5, "pressure": 0.5})[3] == 0
