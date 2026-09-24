"""Who may use the screen, terminal and file routes, and how revoking cuts everything off."""
import asyncio

from aiohttp import WSMsgType
from aiohttp.test_utils import TestClient, TestServer

from keypad_host.devices import Devices
from keypad_host.server import HUB_KEY, TOKEN_HEADER, create_app
from tests.test_server import TAILNET, TOKEN
from tests.test_session import FakeInjector

PHONE = {"Node": {"Name": "phone.tail1234.ts.net.", "StableID": "nPHONE"}, "UserProfile": {"LoginName": "me@example.com"}}
LAPTOP = {"Node": {"Name": "laptop.tail1234.ts.net.", "StableID": "nLAPTOP"}, "UserProfile": {"LoginName": "me@example.com"}}
RENAMED = {"Node": {"Name": "new-name.tail1234.ts.net.", "StableID": "nPHONE"}, "UserProfile": {"LoginName": "me@example.com"}}
H = {TOKEN_HEADER: TOKEN}
SHELL = lambda session: ["sh", "-c", "printf ready; cat"]


def run(scenario, tmp_path, who):
    """who: a one-item list naming the device the next request comes from (the test changes it)."""
    async def main():
        async def whois(_):
            return who[0]

        devices = Devices(tmp_path / "devices.json")
        app = create_app(FakeInjector(), set(), TAILNET, TOKEN, whois, owner="me@example.com", devices=devices, term_command=SHELL)
        async with TestClient(TestServer(app)) as client:
            await scenario(client, app[HUB_KEY], devices)

    asyncio.run(main())


async def closed(ws, timeout=3.0):
    async def wait():
        while True:
            m = await ws.receive()
            if m.type in (WSMsgType.CLOSE, WSMsgType.CLOSED, WSMsgType.CLOSING, WSMsgType.ERROR):
                return True
    return await asyncio.wait_for(wait(), timeout)


def test_the_terminal_needs_the_devices_own_open_session(tmp_path):
    who = [PHONE]

    async def scenario(client, hub, devices):
        r = await client.get("/v1/term", headers=H)
        assert r.status == 403                                  # no session open: refused
        main = await client.ws_connect("/v1", headers=H)
        assert (await main.receive_json())["type"] == "session"
        who[0] = LAPTOP                                          # another of the owner's devices, with the code
        r = await client.get("/v1/term", headers=H)
        assert r.status == 403                                  # it doesn't hold the session: refused
        r = await client.post("/v1/file", data=b"x", headers={**H, "X-Keypad-Name": "a.txt"})
        assert r.status == 403
        who[0] = PHONE
        term = await client.ws_connect("/v1/term", headers=H)   # the phone that holds it: fine
        await term.send_json({"type": "term.start", "cols": 80, "rows": 24})
        assert (await term.receive_json())["type"] == "term"
        await term.close()
        await main.close()

    run(scenario, tmp_path, who)


def test_revoking_closes_the_terminal_too_and_a_new_code_closes_everything(tmp_path):
    who = [PHONE]

    async def scenario(client, hub, devices):
        main = await client.ws_connect("/v1", headers=H)
        await main.receive_json()
        term = await client.ws_connect("/v1/term", headers=H)
        await term.send_json({"type": "term.start", "cols": 80, "rows": 24})
        await term.receive_json()
        hub.kick("phone")
        assert await closed(term) and await closed(main)
        # a new code (kick without a name): every connection goes
        main = await client.ws_connect("/v1", headers=H)
        await main.receive_json()
        hub.kick()
        assert await closed(main)

    run(scenario, tmp_path, who)


def test_renaming_the_device_does_not_undo_a_revoke(tmp_path):
    who = [PHONE]

    async def scenario(client, hub, devices):
        main = await client.ws_connect("/v1", headers=H)
        await main.receive_json()
        await main.close()
        assert devices.revoke("phone")
        who[0] = RENAMED                                         # same device (StableID), new name
        r = await client.get("/v1", headers=H)
        assert r.status == 403 and "revoked" in await r.text()

    run(scenario, tmp_path, who)
