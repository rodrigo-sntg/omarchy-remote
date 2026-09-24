import asyncio
import json

from aiohttp.test_utils import TestClient, TestServer

from keypad_host.server import HUB_KEY, TOKEN_HEADER, create_app
from tests.test_server import PHONE, TAILNET, TOKEN
from tests.test_session import FakeInjector

THEME = {"type": "theme", "name": "solitude", "mode": "dark", "colors": {"accent": "#798186"}}


def run(scenario, tmp_path):
    notices = []

    async def main():
        async def whois(_):
            return PHONE

        async def theme():
            return THEME

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois,
                         state_path=str(tmp_path / "state.json"), notify=lambda *a: notices.append(a), theme_reader=theme,
                         notice_grace=0.05)
        async with TestClient(TestServer(app)) as client:
            await scenario(client, app[HUB_KEY])

    asyncio.run(main())
    return notices


def state(tmp_path):
    return json.loads((tmp_path / "state.json").read_text())


def frame(session, seq, type_, payload):
    return json.dumps({"v": 1, "sessionId": session, "seq": seq, "type": type_, "payload": payload})


async def receive_type(ws, type_, timeout=2.0):
    async def wait():
        while True:
            message = await ws.receive_json()
            if message["type"] == type_:
                return message
    return await asyncio.wait_for(wait(), timeout)


def test_session_state_theme_battery_and_notices(tmp_path):
    async def scenario(client, hub):
        assert state(tmp_path)["connected"] is False
        ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        hello = await ws.receive_json()
        assert await receive_type(ws, "theme") == THEME  # the phone takes the PC's colors on connect
        assert state(tmp_path) == {"connected": True, "phone": "samsung-sm-s928b", "battery": None, "charging": False, "waiting": 0}
        await ws.send_str(frame(hello["sessionId"], 1, "phone.status", {"battery": 64, "charging": True}))
        for _ in range(20):
            if state(tmp_path)["battery"] == 64:
                break
            await asyncio.sleep(0.05)
        assert state(tmp_path)["battery"] == 64 and state(tmp_path)["charging"] is True
        assert await hub.push({"type": "clipboard", "text": "oi"})
        assert await receive_type(ws, "clipboard") == {"type": "clipboard", "text": "oi"}
        await ws.close()
        for _ in range(20):
            if not state(tmp_path)["connected"]:
                break
            await asyncio.sleep(0.05)
        assert state(tmp_path)["connected"] is False
        assert await hub.push({"type": "clipboard", "text": "x"}) is False
        await asyncio.sleep(0.1)  # the "disconnected" notice waits out the grace period

    notices = run(scenario, tmp_path)
    assert notices[0][0] == "Celular conectado" and notices[-1][0] == "Celular desconectado"


def test_phone_status_is_validated():
    import pytest
    from keypad_host.protocol import ProtocolError, parse
    ok = parse(frame("s", 1, "phone.status", {"battery": 100, "charging": False}))
    assert ok.payload == {"battery": 100, "charging": False}
    for payload in ({"battery": 101, "charging": False}, {"battery": 50, "charging": "yes"}, {"battery": True, "charging": False}):
        with pytest.raises(ProtocolError):
            parse(frame("s", 1, "phone.status", payload))


def test_switching_apps_on_the_phone_does_not_spam_notices():
    from keypad_host.server import Hub

    async def main():
        notices = []
        hub = Hub({"active": object(), "control": None}, notify=lambda *a: notices.append(a[0]), grace=0.2)
        hub.opened("s24")
        hub.closed()
        await asyncio.sleep(0.05)
        hub.opened("s24")  # back within the grace period: nothing new to say
        hub.closed()
        await asyncio.sleep(0.3)  # gone for real
        hub.opened("s24")
        await asyncio.sleep(0.01)
        return notices

    assert asyncio.run(main()) == ["Celular conectado", "Celular desconectado", "Celular conectado"]
