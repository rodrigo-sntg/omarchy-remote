import asyncio
import json
import os

from keypad_host.control import handle_command, pair_uri, peer_allowed, serve


class FakeHub:
    def __init__(self, connected=True):
        self.connected = connected
        self.phone = "meu-celular" if connected else None
        self.battery = 81
        self.pushed = []
        self.theme_pushes = 0

    async def push(self, message):
        if not self.connected:
            return False
        self.pushed.append(message)
        return True

    async def push_theme(self, preview=None):
        self.theme_pushes += 1
        return self.connected


def run(coro):
    return asyncio.run(coro)


def test_status_reports_the_phone():
    assert run(handle_command({"cmd": "status"}, FakeHub())) == {"ok": True, "connected": True, "phone": "meu-celular", "battery": 81}
    assert run(handle_command({"cmd": "status"}, FakeHub(connected=False)))["connected"] is False


def test_clipboard_goes_to_the_phone():
    hub = FakeHub()
    assert run(handle_command({"cmd": "clipboard", "text": "olá"}, hub)) == {"ok": True}
    assert hub.pushed == [{"type": "clipboard", "text": "olá"}]


def test_clipboard_without_a_phone_says_so():
    reply = run(handle_command({"cmd": "clipboard", "text": "x"}, FakeHub(connected=False)))
    assert reply["ok"] is False and "celular" in reply["error"]


def test_clipboard_is_limited_and_open_targets_are_fixed():
    hub = FakeHub()
    assert run(handle_command({"cmd": "clipboard", "text": "x" * (64 * 1024 + 1)}, hub))["ok"] is False
    assert run(handle_command({"cmd": "open", "what": "terminal"}, hub)) == {"ok": True}
    assert hub.pushed[-1] == {"type": "open", "what": "terminal"}
    assert run(handle_command({"cmd": "open", "what": "rm -rf"}, hub))["ok"] is False
    assert run(handle_command({"cmd": "nope"}, hub))["ok"] is False
    assert run(handle_command("garbage", hub))["ok"] is False


def test_theme_change_is_pushed():
    hub = FakeHub()
    assert run(handle_command({"cmd": "theme"}, hub)) == {"ok": True}
    assert hub.theme_pushes == 1


def test_pair_uri_carries_host_and_code():
    assert pair_uri("meu-pc.tail1234.ts.net", "ABCD-EFGH") == "keypad://pair?host=meu-pc.tail1234.ts.net&code=ABCD-EFGH"


def test_only_the_same_user_may_use_the_socket():
    assert peer_allowed(1000, 1000)
    assert not peer_allowed(0, 1000)
    assert not peer_allowed(1001, 1000)


def test_unix_socket_round_trip(tmp_path):
    async def main():
        path = tmp_path / "control.sock"
        server = await serve(str(path), FakeHub())
        assert oct(path.stat().st_mode & 0o777) == "0o600"
        reader, writer = await asyncio.open_unix_connection(str(path))
        writer.write(json.dumps({"cmd": "status"}).encode() + b"\n")
        await writer.drain()
        reply = json.loads(await reader.readline())
        writer.close()
        server.close()
        await server.wait_closed()
        return reply

    assert run(main())["connected"] is True


def test_long_accented_text_gets_an_answer_not_a_crash(tmp_path):
    async def main():
        path = tmp_path / "control.sock"
        hub = FakeHub()
        server = await serve(str(path), hub)
        reader, writer = await asyncio.open_unix_connection(str(path))
        # 11 000 "é": 22 KB of UTF-8, 66 KB once JSON-escaped (ensure_ascii) — within the 64 KB text limit.
        writer.write(json.dumps({"cmd": "clipboard", "text": "é" * 11_000}).encode() + b"\n")
        await writer.drain()
        ok = json.loads(await reader.readline())
        writer.close()
        reader, writer = await asyncio.open_unix_connection(str(path))
        writer.write(json.dumps({"cmd": "clipboard", "text": "é" * 40_000}).encode() + b"\n")
        await writer.drain()
        too_big = json.loads(await reader.readline())
        writer.close()
        server.close()
        await server.wait_closed()
        return ok, too_big

    ok, too_big = run(main())
    assert ok == {"ok": True}
    assert too_big["ok"] is False and "64 KB" in too_big["error"]


def test_ring_makes_the_phone_ring_and_stop():
    hub = FakeHub()
    assert run(handle_command({"cmd": "ring"}, hub)) == {"ok": True}
    assert hub.pushed[-1] == {"type": "phone.ring", "on": True}
    assert run(handle_command({"cmd": "ring", "stop": True}, hub)) == {"ok": True}
    assert hub.pushed[-1] == {"type": "phone.ring", "on": False}
    assert run(handle_command({"cmd": "ring"}, FakeHub(connected=False)))["ok"] is False


def test_devices_can_be_listed_revoked_and_the_code_renewed(tmp_path):
    from keypad_host.devices import Devices
    hub = FakeHub()
    hub.devices = Devices(tmp_path / "devices.json", clock=lambda: 50)
    hub.devices.seen("meu-celular.tailnet.ts.net")
    hub.kicked, hub.renewed = 0, 0

    def kick(device=None):
        hub.kicked += 1

    def renew():
        hub.renewed += 1

    hub.kick, hub.rotate_token = kick, renew
    listed = run(handle_command({"cmd": "devices"}, hub))
    assert listed["ok"] and listed["devices"][0]["name"] == "meu-celular.tailnet.ts.net" and listed["current"] == "meu-celular"
    # revoking the phone that is connected drops it at once
    assert run(handle_command({"cmd": "revoke", "name": "meu-celular"}, hub)) == {"ok": True}
    assert hub.devices.revoked("meu-celular.tailnet.ts.net") and hub.kicked == 1
    assert run(handle_command({"cmd": "revoke", "name": "unknown"}, hub))["ok"] is False
    # a new code: every phone must pair again, so the one connected is dropped too
    assert run(handle_command({"cmd": "new-code"}, hub)) == {"ok": True}
    assert hub.renewed == 1 and hub.kicked == 2


def test_an_agent_asks_another_through_the_control_socket():
    from keypad_host.links import LinkError

    class Links:
        async def ask(self, origin, target, question, timeout, cwd=None):
            if target == "claude" and origin == "w9:p1":
                raise LinkError("Quem está respondendo a uma pergunta não pode perguntar a outro agente.")
            return {"ok": True, "agent": "w9:p1", "text": f"{origin}|{target}|{question}|{timeout}|{cwd}"}

    hub = FakeHub()
    hub.links = Links()
    ok = run(handle_command({"cmd": "ask", "from": "w9:p2", "target": "codex", "question": "q?", "timeout": 30, "cwd": "/home/u/app"}, hub))
    assert ok == {"ok": True, "agent": "w9:p1", "text": "w9:p2|codex|q?|30|/home/u/app"}
    plain = run(handle_command({"cmd": "ask", "target": "w9:p1", "question": "q?"}, hub))
    assert plain["text"] == "None|w9:p1|q?|600|None"   # from a plain terminal, default wait
    refused = run(handle_command({"cmd": "ask", "from": "w9:p1", "target": "claude", "question": "q?"}, hub))
    assert refused == {"ok": False, "error": "Quem está respondendo a uma pergunta não pode perguntar a outro agente."}
    for bad in ({"target": "../x", "question": "q"}, {"target": "codex", "question": ""}, {"target": "codex", "question": "q", "from": "w9;x"},
                {"target": "codex", "question": "q", "timeout": 99999}, {"target": "codex", "question": "q", "cwd": "relative"}):
        assert run(handle_command({"cmd": "ask", **bad}, hub))["ok"] is False
