import asyncio
import json

from aiohttp import WSMsgType
from aiohttp.test_utils import TestClient, TestServer

from keypad_host.auth import is_allowed, load_or_create_token
from keypad_host.server import TOKEN_HEADER, create_app
from tests.test_session import FakeInjector

PHONE = {"Node": {"Name": "samsung-sm-s928b.tail1234.ts.net."}, "UserProfile": {"LoginName": "me@example.com"}}
OTHER = {"Node": {"Name": "macbook-pro.tail1234.ts.net."}, "UserProfile": {"LoginName": "me@example.com"}}


TAILNET = "tail1234.ts.net"
TOKEN = "ABCD-EFGH-IJKL-MNOP-QRST"


def test_allowlist_matches_full_machine_name():
    assert is_allowed(PHONE, {"samsung-sm-s928b"}, TAILNET)
    assert is_allowed(PHONE, {"samsung-sm-s928b.tail1234.ts.net"}, TAILNET)
    assert not is_allowed(OTHER, {"samsung-sm-s928b"}, TAILNET)
    assert not is_allowed(None, {"samsung-sm-s928b"}, TAILNET)
    assert not is_allowed({"Node": {}}, {"samsung-sm-s928b"}, TAILNET)


def test_same_short_name_from_a_shared_tailnet_is_refused():
    shared = {"Node": {"Name": "samsung-sm-s928b.other-tailnet.ts.net."}}
    assert not is_allowed(shared, {"samsung-sm-s928b"}, TAILNET)


def test_token_is_created_once_and_private(tmp_path):
    path = tmp_path / "keypad" / "token"
    token = load_or_create_token(path)
    assert load_or_create_token(path) == token
    assert len(token.replace("-", "")) == 20
    assert (path.stat().st_mode & 0o777) == 0o600


def run(scenario, whois_result=PHONE, heartbeat_timeout=None, whois_sequence=None, allowed=frozenset({"samsung-sm-s928b"})):
    async def main():
        injector = FakeInjector()
        sequence = list(whois_sequence or [])

        async def whois(_address):
            return sequence.pop(0) if sequence else whois_result

        app = create_app(injector, set(allowed), TAILNET, TOKEN, whois, heartbeat_timeout=heartbeat_timeout)
        async with TestClient(TestServer(app)) as client:
            await scenario(client, injector)
        return injector

    return asyncio.run(main())


def frame(session, seq, type_, payload=None):
    return json.dumps({"v": 1, "sessionId": session, "seq": seq, "type": type_, "payload": payload or {}})


def test_authorized_client_gets_session_and_input_is_injected():
    async def scenario(client, injector):
        ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        hello = await ws.receive_json()
        assert hello["type"] == "session"
        await ws.send_str(frame(hello["sessionId"], 1, "keyboard.tap", {"usage": 4, "modifiers": 0}))
        assert await ws.receive_json() == {"type": "ack", "seq": 1, "ok": True}
        await ws.close()

    injector = run(scenario)
    assert ("tap", 4, 0) in injector.events
    assert injector.events[-1] == ("release_all",)  # disconnect releases everything


def test_unauthorized_node_is_refused_before_upgrade():
    async def scenario(client, injector):
        response = await client.get("/v1", headers={"Upgrade": "websocket", "Connection": "Upgrade",
                                                   "Sec-WebSocket-Version": "13", "Sec-WebSocket-Key": "dGhlIHNhbXBsZSBub25jZQ=="})
        assert response.status == 403

    injector = run(scenario, whois_result=OTHER)
    assert injector.events == []


UPGRADE = {"Upgrade": "websocket", "Connection": "Upgrade", "Sec-WebSocket-Version": "13",
           "Sec-WebSocket-Key": "dGhlIHNhbXBsZSBub25jZQ=="}


def test_browser_origin_is_refused_even_with_token():
    async def scenario(client, injector):
        response = await client.get("/v1", headers={**UPGRADE, TOKEN_HEADER: TOKEN, "Origin": "http://evil.example"})
        assert response.status == 403

    assert run(scenario).events == []


def test_missing_or_wrong_token_is_refused():
    async def scenario(client, injector):
        assert (await client.get("/v1", headers=UPGRADE)).status == 403
        assert (await client.get("/v1", headers={**UPGRADE, TOKEN_HEADER: "WRONG"})).status == 403

    assert run(scenario).events == []


def test_another_machine_is_refused_while_a_controller_is_active():
    async def scenario(client, injector):
        first = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        await first.receive_json()
        second = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        message = await second.receive_json()
        assert message["type"] == "error" and "outro controlador" in message["message"]
        closing = await second.receive()
        assert closing.type in (WSMsgType.CLOSE, WSMsgType.CLOSED, WSMsgType.CLOSING)
        await first.close()

    run(scenario, whois_sequence=[PHONE, OTHER], allowed={"samsung-sm-s928b", "macbook-pro"})


def test_same_phone_takes_over_its_stale_session():
    """The app was killed and reopened before the old session timed out: it must not lock itself out."""
    async def scenario(client, injector):
        first = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        await first.receive_json()
        second = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        assert (await second.receive_json())["type"] == "session"
        closing = await asyncio.wait_for(first.receive(), timeout=3)
        assert closing.type in (WSMsgType.CLOSE, WSMsgType.CLOSED, WSMsgType.CLOSING)
        await second.close()

    run(scenario)


def test_silent_client_is_dropped_and_input_released():
    async def scenario(client, injector):
        ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        hello = await ws.receive_json()
        await ws.send_str(frame(hello["sessionId"], 1, "pointer.buttons", {"mask": 1}))
        await ws.receive_json()
        closing = await asyncio.wait_for(ws.receive(), timeout=3)
        assert closing.type in (WSMsgType.CLOSE, WSMsgType.CLOSED, WSMsgType.CLOSING)

    injector = run(scenario, heartbeat_timeout=0.5)
    assert injector.events == [("buttons", 1), ("release_all",)]


def test_dev_loopback_whois_names_only_local_peers():
    from keypad_host.auth import LOOPBACK_MACHINE, loopback_whois
    assert asyncio.run(loopback_whois("127.0.0.1:50122"))["Node"]["Name"] == LOOPBACK_MACHINE + "."
    assert asyncio.run(loopback_whois("100.64.0.20:40000")) is None
    assert asyncio.run(loopback_whois("")) is None
    assert is_allowed(asyncio.run(loopback_whois("127.0.0.1:1")), {LOOPBACK_MACHINE}, TAILNET)


def test_an_oversized_frame_does_not_drop_the_session():
    async def main():
        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois)
        async with TestClient(TestServer(app)) as client:
            ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
            sid = (await ws.receive_json())["sessionId"]
            await ws.send_str(json.dumps({"v": 1, "sessionId": sid, "seq": 1, "type": "clipboard.set",
                                          "payload": {"text": "é" * 12_000}}, ensure_ascii=False))
            await ws.send_str(json.dumps({"v": 1, "sessionId": sid, "seq": 2, "type": "ping", "payload": {}}))
            seen = []
            while not any(m.get("type") == "pong" for m in seen):
                seen.append(await asyncio.wait_for(ws.receive_json(), 3))
            await ws.close()
            return seen

    seen = asyncio.run(main())
    assert {"type": "ack", "seq": 1, "ok": False} in seen


def test_a_revoked_phone_is_refused_and_a_new_code_counts_at_once(tmp_path):
    from keypad_host.devices import Devices
    from keypad_host.server import HUB_KEY
    devices = Devices(tmp_path / "devices.json")
    code = {"v": TOKEN}

    async def main():
        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), set(), TAILNET, lambda: code["v"], whois, owner="me@example.com", devices=devices)
        async with TestClient(TestServer(app)) as client:
            ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
            assert (await ws.receive_json())["type"] == "session"
            await ws.close()
            assert [d["name"] for d in devices.list()] == ["samsung-sm-s928b.tail1234.ts.net"]
            code["v"] = "NEW0-NEW0-NEW0-NEW0-NEW0"
            old = await client.get("/v1", headers={TOKEN_HEADER: TOKEN})
            assert old.status == 403
            devices.revoke("samsung-sm-s928b")
            revoked = await client.get("/v1", headers={TOKEN_HEADER: code["v"]})
            assert revoked.status == 403 and "revoked" in await revoked.text()
            assert app[HUB_KEY] is not None

    asyncio.run(main())
