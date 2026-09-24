import json

from tests.controller import controller


def own(events):
    """The screen's own input, without the main session's release_all when it closes."""
    return [e for e in events if e != ("release_all",)]
from keypad_host.display import fit_size
import asyncio

from aiohttp import WSMsgType
from aiohttp.test_utils import TestClient, TestServer

from keypad_host.display import Geometry
from keypad_host.server import TOKEN_HEADER, create_app
from tests.test_server import PHONE, TAILNET, TOKEN
from tests.test_session import FakeInjector


class FakeHyprland:
    def __init__(self):
        self.calls = []

    def create_output(self, width, height, scale):
        self.calls.append(("create", width, height, scale))
        return Geometry(1920, 0, 1560, 720)

    def remove_output(self):
        self.calls.append(("remove",))

    def move_cursor(self, x, y):
        self.calls.append(("cursor", x, y))


AUD = b"\x00\x00\x00\x01\x09\xf0"


class FakeCapture:
    def __init__(self, chunks):
        self.stdout = asyncio.StreamReader()
        for chunk in chunks:
            self.stdout.feed_data(chunk)
        self.terminated = False
        self.returncode = None

    def terminate(self):
        self.terminated = True
        self.stdout.feed_eof()

    kill = terminate  # "terminated" means stopped, however

    async def wait(self):
        self.returncode = 0
        return 0


def run(scenario):
    async def main():
        injector, hyprland = FakeInjector(), FakeHyprland()
        captures = []

        async def capture(*_):
            captures.append(FakeCapture([b"\x00\x00\x00\x01\x67", b"\x00\x00\x00\x01\x65"]))
            return captures[-1]

        async def whois(_):
            return PHONE

        app = create_app(injector, {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, hyprland=hyprland, capture=capture)
        async with TestClient(TestServer(app)) as client, controller(client):
            await scenario(client)
        return injector, hyprland, captures

    return asyncio.run(main())


def test_display_session_streams_video_and_maps_touch():
    async def scenario(client):
        ws = await client.ws_connect("/v1/display", headers={TOKEN_HEADER: TOKEN})
        await ws.send_json({"type": "display.start", "width": 2340, "height": 1080, "scale": 1.5})
        info = await ws.receive_json()
        assert info == {"type": "display", "width": 2340, "height": 1080}
        video = b""
        while len(video) < 10:
            message = await ws.receive()
            assert message.type == WSMsgType.BINARY
            video += message.data
        assert video == b"\x00\x00\x00\x01\x67\x00\x00\x00\x01\x65"
        await ws.send_json({"type": "touch", "action": "down", "x": 0.5, "y": 0.5})
        await ws.send_json({"type": "touch", "action": "up", "x": 1.0, "y": 1.0})
        await asyncio.sleep(0.1)
        await ws.close()

    injector, hyprland, captures = run(scenario)
    assert hyprland.calls[0] == ("create", 2340, 1080, 1.5)
    assert ("cursor", 2700, 360) in hyprland.calls
    assert ("cursor", 1920 + 1559, 719) in hyprland.calls
    assert injector.events[:2] == [("buttons", 1), ("buttons", 0)]
    assert hyprland.calls[-1] == ("remove",)
    assert captures[0].terminated


def test_touch_held_when_phone_disconnects_is_released():
    async def scenario(client):
        ws = await client.ws_connect("/v1/display", headers={TOKEN_HEADER: TOKEN})
        await ws.send_json({"type": "display.start", "width": 2340, "height": 1080, "scale": 1.5})
        await ws.receive_json()
        await ws.send_json({"type": "touch", "action": "down", "x": 0.1, "y": 0.1})
        await asyncio.sleep(0.1)
        await ws.close()

    injector, hyprland, _ = run(scenario)
    assert own(injector.events)[-1] == ("buttons", 0)
    assert hyprland.calls[-1] == ("remove",)


def test_invalid_start_creates_nothing():
    async def scenario(client):
        ws = await client.ws_connect("/v1/display", headers={TOKEN_HEADER: TOKEN})
        await ws.send_json({"type": "display.start", "width": 99999, "height": 1080, "scale": 1})
        message = await ws.receive_json()
        assert message["type"] == "error"

    _, hyprland, captures = run(scenario)
    assert hyprland.calls == [] and captures == []


def test_display_requires_pairing_code():
    async def scenario(client):
        response = await client.get("/v1/display", headers={"Upgrade": "websocket", "Connection": "Upgrade",
                                                           "Sec-WebSocket-Version": "13", "Sec-WebSocket-Key": "dGhlIHNhbXBsZSBub25jZQ=="})
        assert response.status == 403

    _, hyprland, _ = run(scenario)
    assert hyprland.calls == []


class FakeMonitorsHyprland(FakeHyprland):
    def cursor_pos(self):
        return (100, 100)

    def monitors(self):
        return [
            {"name": "DP-1", "width": 1920, "height": 1080, "x": 0, "y": 0, "scale": 1.0},
            {"name": "HDMI-A-1", "width": 3440, "height": 1440, "x": 1920, "y": 0, "scale": 1.0},
        ]


def run_screen(scenario):
    async def main():
        injector, hyprland = FakeInjector(), FakeMonitorsHyprland()
        captures = []

        async def capture(output, size, fps=60):
            captures.append((output, size, FakeCapture([b"\x00\x00\x00\x01\x67"])))
            return captures[-1][2]

        async def whois(_):
            return PHONE

        app = create_app(injector, {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, hyprland=hyprland, capture=capture)
        async with TestClient(TestServer(app)) as client, controller(client):
            await scenario(client)
        return injector, hyprland, captures

    return asyncio.run(main())


def test_screen_lists_monitors_streams_the_chosen_one_and_maps_gestures():
    async def scenario(client):
        ws = await client.ws_connect("/v1/screen", headers={TOKEN_HEADER: TOKEN})
        listing = await ws.receive_json()
        assert [m["name"] for m in listing["monitors"]] == ["DP-1", "HDMI-A-1"]
        await ws.send_json({"type": "screen.start", "monitor": "HDMI-A-1", "maxWidth": 2340, "maxHeight": 1080})
        assert await ws.receive_json() == {"type": "screen", "monitor": "HDMI-A-1", "width": 2340, "height": 980}
        assert (await ws.receive_json())["type"] == "cursor"
        assert (await ws.receive()).type == WSMsgType.BINARY
        await ws.send_json({"type": "touch", "action": "right", "x": 0.5, "y": 0.5})
        await ws.send_json({"type": "touch", "action": "scroll", "x": 0.0, "y": 0.0, "steps": -2})
        await ws.send_json({"type": "touch", "action": "hscroll", "x": 0.0, "y": 0.0, "steps": 3})
        await asyncio.sleep(0.1)
        await ws.close()

    injector, hyprland, captures = run_screen(scenario)
    assert captures[0][:2] == ("HDMI-A-1", (2340, 980))
    assert ("cursor", 1920 + 1720, 720) in hyprland.calls
    assert ("cursor", 1920, 0) in hyprland.calls
    assert own(injector.events) == [("buttons", 2), ("buttons", 0), ("scroll", -2), ("hscroll", 3)]
    assert ("create", 2340, 1080, 1.0) not in hyprland.calls  # no virtual monitor for "Ver PC"
    assert captures[0][2].terminated


def test_screen_refuses_unknown_monitor():
    async def scenario(client):
        ws = await client.ws_connect("/v1/screen", headers={TOKEN_HEADER: TOKEN})
        await ws.receive_json()
        await ws.send_json({"type": "screen.start", "monitor": "OMARCHYREMOTE", "maxWidth": 2340, "maxHeight": 1080})
        assert (await ws.receive_json())["type"] == "error"

    _, _, captures = run_screen(scenario)
    assert captures == []


def test_new_video_session_takes_over_the_previous_one():
    """Switching monitors closes one channel and opens the next immediately; the new one must win."""
    async def scenario(client):
        first = await client.ws_connect("/v1/screen", headers={TOKEN_HEADER: TOKEN})
        await first.receive_json()
        await first.send_json({"type": "screen.start", "monitor": "DP-1", "maxWidth": 2340, "maxHeight": 1080})
        await first.receive_json()
        second = await client.ws_connect("/v1/screen", headers={TOKEN_HEADER: TOKEN})
        listing = await second.receive_json()
        assert listing["type"] == "monitors"
        await second.send_json({"type": "screen.start", "monitor": "HDMI-A-1", "maxWidth": 2340, "maxHeight": 1080})
        assert (await second.receive_json())["monitor"] == "HDMI-A-1"
        while (await first.receive()).type not in (WSMsgType.CLOSE, WSMsgType.CLOSED, WSMsgType.CLOSING):
            pass
        await second.close()

    _, _, captures = run_screen(scenario)
    assert [c[0] for c in captures] == ["DP-1", "HDMI-A-1"]
    assert all(c[2].terminated for c in captures)


class CursorHyprland(FakeMonitorsHyprland):
    def __init__(self):
        super().__init__()
        self.cursor = (100, 100)  # starts on another monitor

    def cursor_pos(self):
        return self.cursor

    def move_cursor(self, x, y):
        super().move_cursor(x, y)
        self.cursor = (x, y)


class MovingInjector(FakeInjector):
    def __init__(self, hyprland):
        super().__init__()
        self.hyprland = hyprland

    def move(self, dx, dy):
        super().move(dx, dy)
        x, y = self.hyprland.cursor
        self.hyprland.cursor = (x + dx, y + dy)


def test_trackpad_pointer_moves_relatively_clicks_at_cursor_and_reports_it():
    async def main():
        hyprland = CursorHyprland()
        injector = MovingInjector(hyprland)

        async def capture(*_):
            return FakeCapture([])

        async def whois(_):
            return PHONE

        app = create_app(injector, {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, hyprland=hyprland, capture=capture)
        async with TestClient(TestServer(app)) as client, controller(client):
            ws = await client.ws_connect("/v1/screen", headers={TOKEN_HEADER: TOKEN})
            await ws.receive_json()
            await ws.send_json({"type": "screen.start", "monitor": "HDMI-A-1", "maxWidth": 2340, "maxHeight": 1080})
            await ws.receive_json()
            # The cursor was brought onto the monitor being viewed (its center) and reported.
            assert await ws.receive_json() == {"type": "cursor", "inside": True, "x": 0.5, "y": 0.5}
            await ws.send_json({"type": "pointer", "action": "rel", "dx": 0.1, "dy": 0.0})  # 344 px
            report = await ws.receive_json()
            assert report["type"] == "cursor" and abs(report["x"] - 0.6) < 0.001
            await ws.send_json({"type": "pointer", "action": "rel", "dx": -1.0, "dy": 0.0})  # past the left edge
            report = await ws.receive_json()
            # Not held back: it went on to DP-1 and the phone is told, so it can follow.
            assert report == {"type": "cursor", "inside": False, "monitor": "DP-1"}
            assert hyprland.cursor[0] < 1920
            await ws.send_json({"type": "pointer", "action": "click", "button": 2})
            await ws.send_json({"type": "pointer", "action": "scroll", "steps": 3})
            await asyncio.sleep(0.2)
            await ws.close()
        return injector

    injector = asyncio.run(main())
    assert ("move", 344, 0) in injector.events
    assert [e for e in injector.events if e[0] in ("buttons", "scroll")] == [("buttons", 2), ("buttons", 0), ("scroll", 3)]


def test_view_only_preview_neither_moves_the_cursor_nor_accepts_input_and_reports_it():
    async def main():
        hyprland = CursorHyprland()  # cursor starts on another monitor
        injector = MovingInjector(hyprland)
        captures = []

        async def capture(output, size, fps=60):
            captures.append((output, size, fps))
            return FakeCapture([])

        async def whois(_):
            return PHONE

        app = create_app(injector, {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, hyprland=hyprland, capture=capture)
        async with TestClient(TestServer(app)) as client, controller(client):
            ws = await client.ws_connect("/v1/screen", headers={TOKEN_HEADER: TOKEN})
            await ws.receive_json()
            await ws.send_json({"type": "screen.start", "monitor": "HDMI-A-1", "maxWidth": 480, "maxHeight": 270,
                                "viewOnly": True, "fps": 8})
            assert (await ws.receive_json())["type"] == "screen"
            assert await ws.receive_json() == {"type": "cursor", "inside": False, "monitor": "DP-1"}  # not moved
            hyprland.cursor = (1920 + 1720, 720)  # the user moves the physical mouse onto HDMI-A-1
            report = await asyncio.wait_for(ws.receive_json(), 2)
            assert report == {"type": "cursor", "inside": True, "x": 0.5, "y": 0.5}
            await ws.send_json({"type": "pointer", "action": "click", "button": 1})
            await ws.send_json({"type": "touch", "action": "down", "x": 0.1, "y": 0.1})
            await asyncio.sleep(0.2)
            await ws.close()
        return injector, hyprland, captures

    injector, hyprland, captures = asyncio.run(main())
    assert captures == [("HDMI-A-1", (480, 200), 8)]
    assert not any(c[0] == "cursor" for c in hyprland.calls)  # never warped
    assert own(injector.events) == []  # view only: no input at all


def test_input_sent_before_the_monitor_request_is_ignored_not_fatal():
    """Switching monitor mid-swipe: the phone's moves can reach the new session before screen.start."""
    async def main():
        hyprland = CursorHyprland()
        injector = MovingInjector(hyprland)

        async def capture(*_):
            return FakeCapture([])

        async def whois(_):
            return PHONE

        app = create_app(injector, {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, hyprland=hyprland, capture=capture)
        async with TestClient(TestServer(app)) as client, controller(client):
            ws = await client.ws_connect("/v1/screen", headers={TOKEN_HEADER: TOKEN})
            await ws.receive_json()
            await ws.send_json({"type": "pointer", "action": "rel", "dx": 0.1, "dy": 0.0})
            await ws.send_json({"type": "screen.start", "monitor": "HDMI-A-1", "maxWidth": 2340, "maxHeight": 1080})
            reply = await ws.receive_json()
            await ws.close()
        return reply, injector

    reply, injector = asyncio.run(main())
    assert reply["type"] == "screen" and reply["monitor"] == "HDMI-A-1"
    assert not [e for e in injector.events if e[0] == "move"]  # the early move was dropped


def test_end_of_each_frame_is_marked_so_a_still_screen_is_shown():
    """The phone only knows a frame ended when the next NAL starts; after the encoder goes quiet the
    host appends an access unit delimiter so the last frame of a still screen is shown whole."""
    async def scenario(client):
        ws = await client.ws_connect("/v1/display", headers={TOKEN_HEADER: TOKEN})
        await ws.send_json({"type": "display.start", "width": 2340, "height": 1080, "scale": 1.5})
        await ws.receive_json()
        video = b""
        while not video.endswith(AUD):
            video += (await asyncio.wait_for(ws.receive(), timeout=2)).data
        assert video == b"\x00\x00\x00\x01\x67\x00\x00\x00\x01\x65" + AUD
        await ws.close()

    run(scenario)


def test_switching_monitor_and_quality_keeps_the_connection():
    async def main():
        hyprland = CursorHyprland()
        injector = MovingInjector(hyprland)
        captures = []

        async def capture(output, size, fps=60):
            captures.append((output, size, fps))
            return FakeCapture([b"\x00\x00\x00\x01\x67"])

        async def whois(_):
            return PHONE

        app = create_app(injector, {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, hyprland=hyprland, capture=capture)
        async with TestClient(TestServer(app)) as client, controller(client):
            ws = await client.ws_connect("/v1/screen", headers={TOKEN_HEADER: TOKEN})
            await ws.receive_json()
            await ws.send_json({"type": "screen.start", "monitor": "HDMI-A-1", "maxWidth": 2340, "maxHeight": 1080, "scale": 0.75, "fps": 30})

            async def next_json(type_):
                while True:
                    message = await asyncio.wait_for(ws.receive(), 2)
                    assert message.type in (WSMsgType.TEXT, WSMsgType.BINARY), message
                    if message.type == WSMsgType.TEXT and json.loads(message.data)["type"] == type_:
                        return json.loads(message.data)

            started = await next_json("screen")
            assert (started["monitor"], started["width"], started["height"]) == ("HDMI-A-1", *fit_size(3440, 1440, 1755, 810))  # 3/4 of the phone
            await ws.send_json({"type": "screen.switch", "monitor": "DP-1"})
            switched = await next_json("screen")
            await ws.send_json({"type": "screen.quality", "fps": 30, "scale": 0.5})
            lighter = await next_json("screen")
            await ws.send_json({"type": "video.ping", "t": 123})
            pong = await next_json("video.pong")
            await ws.send_json({"type": "screen.switch", "monitor": "OMARCHYREMOTE"})
            refused = await next_json("error")
            await ws.close()
            return captures, switched, lighter, pong, refused

    captures, switched, lighter, pong, refused = asyncio.run(main())
    assert (switched["width"], switched["height"]) == fit_size(1920, 1080, 1755, 810)  # the 3/4 stays
    assert lighter == {"type": "screen", "monitor": "DP-1", "width": 960, "height": 540}  # half of the phone's 2340x1080
    assert [c[0] for c in captures] == ["HDMI-A-1", "DP-1", "DP-1"] and captures[-1][2] == 30
    assert pong == {"type": "video.pong", "t": 123}
    assert "monitor" in refused["message"]


def test_a_capture_started_as_the_phone_leaves_is_stopped():
    class SlowToStop(FakeCapture):
        async def wait(self):
            await asyncio.sleep(0.2)  # a real encoder takes a moment to exit
            return await super().wait()

    async def main():
        hyprland = CursorHyprland()
        processes, phone = [], {}

        async def capture(output, size, fps=60):
            processes.append(SlowToStop([b"\x00\x00\x00\x01\x67"]))
            if len(processes) == 2:
                phone["ws"]._response.connection.transport.abort()  # the network drops mid-switch
            return processes[-1]

        async def whois(_):
            return PHONE

        app = create_app(MovingInjector(hyprland), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, hyprland=hyprland, capture=capture)
        async with TestClient(TestServer(app)) as client, controller(client):
            ws = phone["ws"] = await client.ws_connect("/v1/screen", headers={TOKEN_HEADER: TOKEN})
            await ws.receive_json()
            await ws.send_json({"type": "screen.start", "monitor": "DP-1", "maxWidth": 2340, "maxHeight": 1080})
            while (await asyncio.wait_for(ws.receive(), 2)).type != WSMsgType.TEXT:
                pass
            await ws.send_json({"type": "screen.quality", "fps": 30, "scale": 0.5})
            await asyncio.sleep(0.8)
        return processes

    processes = asyncio.run(main())
    assert len(processes) == 2 and all(p.terminated for p in processes)


def test_a_window_is_captured_by_its_rectangle():
    from keypad_host.display import capture_args
    args = capture_args("DP-1", (940, 1030), 30, region=Geometry(967, 38, 941, 1030))
    assert "-o" not in args and args[args.index("-g") + 1] == "967,38 941x1030"
    assert capture_args("DP-1", None, 60)[1:3] == ["-o", "DP-1"]


class WindowHyprland(CursorHyprland):
    def __init__(self):
        super().__init__()
        self.window = {"address": "0xa", "at": [100, 50], "size": [800, 600], "monitor": 0, "title": "Editor", "class": "code"}

    def active_window(self):
        return self.window


def test_ver_pc_can_show_just_the_focused_window_and_follows_focus():
    async def main():
        hyprland = WindowHyprland()
        captures = []

        async def capture(output, size, fps=60, region=None):
            captures.append((output, size, region))
            return FakeCapture([b"\x00\x00\x00\x01\x67"])

        async def whois(_):
            return PHONE

        app = create_app(MovingInjector(hyprland), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, hyprland=hyprland, capture=capture)
        async with TestClient(TestServer(app)) as client, controller(client):
            ws = await client.ws_connect("/v1/screen", headers={TOKEN_HEADER: TOKEN})
            await ws.receive_json()
            await ws.send_json({"type": "screen.start", "monitor": "DP-1", "maxWidth": 2340, "maxHeight": 1080})

            async def next_screen():
                while True:
                    message = await asyncio.wait_for(ws.receive(), 3)
                    if message.type == WSMsgType.TEXT and json.loads(message.data)["type"] == "screen":
                        return json.loads(message.data)

            await next_screen()
            await ws.send_json({"type": "screen.switch", "window": True})
            window = await next_screen()
            # Touch at the middle of the video lands at the middle of the window.
            await ws.send_json({"type": "touch", "action": "move", "x": 0.5, "y": 0.5})
            await asyncio.sleep(0.1)
            moved_to = hyprland.cursor
            hyprland.window = {"address": "0xb", "at": [1920, 0], "size": [1720, 1440], "monitor": 1, "title": "Browser", "class": "chromium"}
            followed = await next_screen()
            await ws.send_json({"type": "screen.switch", "monitor": "DP-1"})
            back = await next_screen()
            await ws.close()
            return captures, window, followed, back, moved_to

    captures, window, followed, back, moved_to = asyncio.run(main())
    assert window["window"] == "Editor" and window["monitor"] == "DP-1"
    assert captures[1][2] == Geometry(100, 50, 800, 600)
    assert followed["window"] == "Browser" and followed["monitor"] == "HDMI-A-1" and captures[2][2] == Geometry(1920, 0, 1720, 1440)
    assert "window" not in back and captures[3][2] is None
    assert moved_to == (500, 350)
