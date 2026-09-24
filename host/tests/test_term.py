import asyncio
import os
import struct

import pytest
from aiohttp import WSMsgType
from aiohttp.test_utils import TestClient, TestServer

from keypad_host.server import TOKEN_HEADER, create_app
from keypad_host.term import TermError, herdr_command, parse_term_resize, parse_term_start, winsize
from tests.test_server import PHONE, TAILNET, TOKEN
from tests.test_session import FakeInjector


def test_term_start_is_validated():
    assert parse_term_start({"type": "term.start", "cols": 120, "rows": 40}) == (120, 40, "default")
    assert parse_term_start({"type": "term.start", "cols": 80, "rows": 24, "session": "work-2"}) == (80, 24, "work-2")
    for bad in (
        {"type": "term.start", "cols": 19, "rows": 24},
        {"type": "term.start", "cols": 501, "rows": 24},
        {"type": "term.start", "cols": 80, "rows": 4},
        {"type": "term.start", "cols": "80", "rows": 24},
        {"type": "term.start", "cols": 80, "rows": 24, "session": "Default"},
        {"type": "term.start", "cols": 80, "rows": 24, "session": "a;b"},
        {"type": "term.resize", "cols": 80, "rows": 24},
        "nope",
    ):
        with pytest.raises(TermError):
            parse_term_start(bad)


def test_term_resize_is_validated():
    assert parse_term_resize({"type": "term.resize", "cols": 100, "rows": 30}) == (100, 30)
    with pytest.raises(TermError):
        parse_term_resize({"type": "term.resize", "cols": 100, "rows": 1000})


def test_winsize_packs_rows_then_cols():
    assert struct.unpack("HHHH", winsize(40, 120)) == (40, 120, 0, 0)


def test_herdr_command_attaches_to_the_named_session():
    assert herdr_command("default") == ["herdr", "--session", "default"]


def run(scenario, term_command, term_window=None):
    async def main():
        async def whois(_):
            return PHONE

        extra = {"term_window": term_window} if term_window else {}
        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, term_command=term_command, **extra)
        async with TestClient(TestServer(app)) as client:
            await scenario(client)

    asyncio.run(main())


async def collect(ws, until, timeout=3.0, ack=True):
    """Binary frames until `until(data)` is true, acknowledged as the app does once drawn."""
    data = b""

    async def wait():
        nonlocal data
        while not until(data):
            message = await ws.receive()
            assert message.type == WSMsgType.BINARY, message
            data += message.data
            if ack:
                await ws.send_json({"type": "term.ack", "bytes": len(message.data)})
    await asyncio.wait_for(wait(), timeout)
    return data


def test_terminal_streams_both_ways_and_resizes():
    async def scenario(client):
        ws = await client.ws_connect("/v1/term", headers={TOKEN_HEADER: TOKEN})
        await ws.send_json({"type": "term.start", "cols": 100, "rows": 30, "session": "default"})
        assert await ws.receive_json() == {"type": "term", "cols": 100, "rows": 30, "session": "default"}
        await collect(ws, lambda d: b"ready" in d)
        await ws.send_bytes(b"abc\n")
        assert b"abc" in await collect(ws, lambda d: b"abc" in d)
        await ws.send_json({"type": "term.resize", "cols": 120, "rows": 40})
        await ws.send_bytes(b"size\n")
        assert b"120x40" in await collect(ws, lambda d: b"120x40" in d)
        await ws.close()

    # `stty size` prints "rows cols"; the shell answers the resize after it happened.
    run(scenario, lambda session: ["sh", "-c", "printf ready; while read line; do if [ \"$line\" = size ]; then set -- $(stty size); echo \"$2x$1\"; else echo \"$line\"; fi; done"])


def test_bad_start_is_refused_with_an_error():
    async def scenario(client):
        ws = await client.ws_connect("/v1/term", headers={TOKEN_HEADER: TOKEN})
        await ws.send_json({"type": "term.start", "cols": 5, "rows": 5})
        error = await ws.receive_json()
        assert error["type"] == "error" and "invalid size" in error["message"]
        closing = await ws.receive()
        assert closing.type in (WSMsgType.CLOSE, WSMsgType.CLOSED, WSMsgType.CLOSING)

    run(scenario, lambda session: ["true"])


def test_burst_of_output_arrives_whole():
    async def scenario(client):
        ws = await client.ws_connect("/v1/term", headers={TOKEN_HEADER: TOKEN})
        await ws.send_json({"type": "term.start", "cols": 100, "rows": 30})
        await ws.receive_json()
        data = await collect(ws, lambda d: d.count(b"x") >= 300_000 and b"END" in d, timeout=10)
        assert data.count(b"x") == 300_000

    run(scenario, lambda session: ["sh", "-c", "head -c 300000 /dev/zero | tr '\\0' x; printf END; cat"])


def test_closing_the_socket_ends_the_child(tmp_path):
    pid_file = tmp_path / "pid"

    async def scenario(client):
        ws = await client.ws_connect("/v1/term", headers={TOKEN_HEADER: TOKEN})
        await ws.send_json({"type": "term.start", "cols": 100, "rows": 30})
        await ws.receive_json()
        await collect(ws, lambda d: b"ready" in d)
        await ws.close()
        pid = int(pid_file.read_text())
        for _ in range(40):
            try:
                os.kill(pid, 0)
            except ProcessLookupError:
                return
            await asyncio.sleep(0.05)
        raise AssertionError("child still alive after the socket closed")

    run(scenario, lambda session: ["sh", "-c", f"echo $$ > {pid_file}; printf ready; cat"])


def test_child_environment_is_a_fresh_terminal_outside_herdr():
    from keypad_host.term import child_env
    env = child_env({"PATH": "/usr/bin", "HERDR_ENV": "1", "HERDR_PANE_ID": "w1:p1", "TERM": "dumb", "HOME": "/home/u"})
    assert env == {"PATH": "/usr/bin", "HOME": "/home/u", "TERM": "xterm-256color", "COLORTERM": "truecolor"}


def test_session_name_rejects_a_trailing_newline():
    with pytest.raises(TermError):
        parse_term_start({"type": "term.start", "cols": 80, "rows": 24, "session": "default\n"})


def test_output_waits_for_the_phone_to_draw_it():
    """Flow control: past the window of unacknowledged bytes the host stops reading the PTY
    (the program blocks) and resumes as the phone acknowledges. Nothing is lost."""
    window = 65536

    async def scenario(client):
        ws = await client.ws_connect("/v1/term", headers={TOKEN_HEADER: TOKEN})
        await ws.send_json({"type": "term.start", "cols": 100, "rows": 30})
        await ws.receive_json()
        unacked = b""
        try:
            while True:
                unacked += (await asyncio.wait_for(ws.receive(), 1.0)).data
        except asyncio.TimeoutError:
            pass
        assert 0 < len(unacked) <= window + 65536  # at most one read past the window
        await ws.send_json({"type": "term.ack", "bytes": len(unacked)})
        rest = await collect(ws, lambda d: b"END" in d, timeout=10)
        assert (unacked + rest).count(b"x") == 2_000_000
        await ws.close()

    run(scenario, lambda session: ["sh", "-c", "head -c 2000000 /dev/zero | tr '\\0' x; printf END; cat"], term_window=window)
