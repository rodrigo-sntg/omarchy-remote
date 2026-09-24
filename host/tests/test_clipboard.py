import asyncio
import json

import pytest

import base64

from keypad_host.clip import GET_LIMIT, get_clipboard, parse_watch_line, set_clipboard, watch_clipboard
from keypad_host.protocol import ProtocolError, parse


def frame(type_, payload):
    return json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": type_, "payload": payload})


def test_clipboard_messages_are_validated():
    assert parse(frame("clipboard.set", {"text": "olá"})).payload == {"text": "olá"}
    assert parse(frame("clipboard.get", {})).payload == {}
    for payload in ({"text": ""}, {"text": 5}, {"text": "x" * 12_001}):
        with pytest.raises(ProtocolError):
            parse(frame("clipboard.set", payload))


def test_set_feeds_wl_copy_through_stdin():
    calls = []

    async def runner(args, stdin=None):
        calls.append((args, stdin))
        return ""

    assert asyncio.run(set_clipboard("oi\nmundo", runner)) is True
    assert calls == [(["wl-copy"], b"oi\nmundo")]


def test_get_reads_wl_paste_and_caps_the_size():
    async def runner(args, stdin=None):
        assert args == ["wl-paste", "-n", "-t", "text"]
        return "y" * (GET_LIMIT + 10)

    assert len(asyncio.run(get_clipboard(runner))) == GET_LIMIT


def test_no_wayland_clipboard_means_nothing():
    async def runner(args, stdin=None):
        return None

    assert asyncio.run(get_clipboard(runner)) is None
    assert asyncio.run(set_clipboard("x", runner)) is False


def test_phone_sets_and_reads_the_pc_clipboard():
    from aiohttp.test_utils import TestClient, TestServer
    from keypad_host.server import TOKEN_HEADER, create_app
    from tests.test_server import PHONE, TAILNET, TOKEN
    from tests.test_session import FakeInjector

    pc = {"text": "do PC"}

    async def setter(text):
        pc["text"] = text
        return True

    async def getter():
        return pc["text"]

    async def main():
        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, clipboard=(setter, getter))
        async with TestClient(TestServer(app)) as client:
            ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
            hello = await ws.receive_json()
            sid = hello["sessionId"]
            await ws.send_str(json.dumps({"v": 1, "sessionId": sid, "seq": 1, "type": "clipboard.set", "payload": {"text": "do celular"}}))
            assert (await ws.receive_json()) == {"type": "ack", "seq": 1, "ok": True}
            await ws.send_str(json.dumps({"v": 1, "sessionId": sid, "seq": 2, "type": "clipboard.get", "payload": {}}))
            got = await asyncio.wait_for(ws.receive_json(), 2)
            await ws.close()
            from keypad_host.server import HUB_KEY
            return got, app[HUB_KEY].clip_last

    got, last = asyncio.run(main())
    assert pc["text"] == "do celular"
    assert last == "do celular"  # the watcher will not send it back
    assert got == {"type": "clipboard", "text": "do celular"}


def copied(text):
    return "C " + base64.b64encode(text.encode()).decode() + "\n"


def test_each_copy_on_the_pc_is_read_from_the_watch():
    assert parse_watch_line(copied("olá\nmundo")) == "olá\nmundo"


def test_passwords_empty_and_broken_lines_are_not_copies():
    assert parse_watch_line("S\n") is None          # a password manager's copy
    assert parse_watch_line("C \n") is None         # an image, nothing as text
    assert parse_watch_line("C %%%\n") is None
    assert parse_watch_line("junk") is None


def test_too_long_a_copy_stays_on_the_pc():
    assert parse_watch_line(copied("x" * (GET_LIMIT + 1))) is None


def test_new_pc_copies_reach_the_phone_once_and_never_bounce_back():
    pushed = []

    class Hub:
        clip_last = None

        async def push(self, message):
            pushed.append(message)
            return True

    hub = Hub()

    async def lines():
        yield copied("já estava lá")   # what the clipboard held when the watch started
        yield copied("novo")
        yield copied("novo")
        yield "S\n"
        hub.clip_last = "do celular"   # the phone just set it
        yield copied("do celular")
        yield copied("outro")

    async def run_once():
        task = asyncio.create_task(watch_clipboard(hub, lines, retry=10))
        await asyncio.sleep(0.05)
        task.cancel()

    asyncio.run(run_once())
    assert pushed == [{"type": "clipboard", "text": "novo", "auto": True},
                      {"type": "clipboard", "text": "outro", "auto": True}]
