import asyncio
import json

from aiohttp.test_utils import TestClient, TestServer

from keypad_host.herdr import HerdrError
from keypad_host.server import TOKEN_HEADER, create_app
from tests.test_server import PHONE, TAILNET, TOKEN
from tests.test_session import FakeInjector


class FakeHerdr:
    def __init__(self):
        self.raw = [{"pane_id": "w1:p1", "agent": "claude", "agent_status": "working", "workspace_id": "w1",
                     "tab_id": "w1:t1", "cwd": "/home/u/proj", "terminal_title_stripped": "Fix tests", "state_change_seq": 1}]
        self.down = False
        self.calls = []

    async def agents(self):
        if self.down:
            raise HerdrError("failed", "no server")
        return [dict(a) for a in self.raw]

    async def read(self, target, lines=40):
        self.calls.append(("read", target, lines))
        return "last lines"

    async def send_keys(self, target, keys):
        self.calls.append(("keys", target, keys))
        return {}

    async def prompt(self, target, text):
        return {}

    async def focus(self, target):
        return {}


def run(scenario, herdr):
    async def main():
        injector = FakeInjector()

        async def whois(_):
            return PHONE

        app = create_app(injector, {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, herdr=herdr, agent_poll=0.05)
        async with TestClient(TestServer(app)) as client:
            await scenario(client)
        return injector

    return asyncio.run(main())


def frame(session, seq, type_, payload):
    return json.dumps({"v": 1, "sessionId": session, "seq": seq, "type": type_, "payload": payload})


async def receive_type(ws, type_, timeout=2.0):
    """The next message of that type, skipping the others (workspaces, cursor…)."""
    async def wait():
        while True:
            message = await ws.receive_json()
            if message["type"] == type_:
                return message
    return await asyncio.wait_for(wait(), timeout)


def test_agents_are_pushed_on_connect_and_when_they_change():
    herdr = FakeHerdr()

    async def scenario(client):
        ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        await ws.receive_json()  # session
        first = await receive_type(ws, "agents")
        assert first["available"] is True and first["agents"][0] == {
            "id": "w1:p1", "kind": "claude", "status": "working", "title": "Fix tests", "workspace": "w1", "cwd": "proj", "seq": 1}
        herdr.raw[0]["agent_status"] = "blocked"
        herdr.raw[0]["state_change_seq"] = 2
        update = await receive_type(ws, "agents")
        assert update["agents"][0]["status"] == "blocked"
        alert = await receive_type(ws, "agent.alert")
        assert alert["agent"]["id"] == "w1:p1" and alert["agent"]["status"] == "blocked"
        await ws.close()

    run(scenario, herdr)


def test_agent_read_and_keys_round_trip():
    herdr = FakeHerdr()

    async def scenario(client):
        ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        hello = await ws.receive_json()
        await ws.send_str(frame(hello["sessionId"], 1, "agent.read", {"id": "w1:p1", "lines": 20}))
        assert await receive_type(ws, "agent.text") == {"type": "agent.text", "id": "w1:p1", "text": "last lines"}
        await ws.send_str(frame(hello["sessionId"], 2, "agent.keys", {"id": "w1:p1", "keys": ["enter"]}))
        assert await receive_type(ws, "ack") == {"type": "ack", "seq": 2, "ok": True}
        await ws.close()

    run(scenario, herdr)
    assert ("read", "w1:p1", 20) in herdr.calls and ("keys", "w1:p1", ["enter"]) in herdr.calls


def test_herdr_going_down_is_reported_as_unavailable():
    herdr = FakeHerdr()

    async def scenario(client):
        ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        await ws.receive_json()
        assert (await receive_type(ws, "agents"))["available"] is True
        herdr.down = True
        down = await receive_type(ws, "agents")
        assert down == {"type": "agents", "agents": [], "available": False}
        await ws.close()

    run(scenario, herdr)


def test_without_herdr_nothing_about_agents_is_sent():
    """The phone starts with herdr unavailable, so silence means the same and old clients see no change."""
    async def scenario(client):
        ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        await ws.receive_json()
        try:
            message = await asyncio.wait_for(ws.receive_json(), 0.3)
        except asyncio.TimeoutError:
            message = None
        assert message is None or message["type"] != "agents"
        await ws.close()

    run(scenario, herdr=None)


class SlowHerdr(FakeHerdr):
    async def send_keys(self, target, keys):
        await asyncio.sleep(1.5)
        return await super().send_keys(target, keys)


def test_a_slow_herdr_does_not_hold_up_input_or_pings():
    herdr = SlowHerdr()

    async def scenario(client):
        ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        hello = await ws.receive_json()
        await ws.send_str(frame(hello["sessionId"], 1, "agent.keys", {"id": "w1:p1", "keys": ["enter"]}))
        await ws.send_str(frame(hello["sessionId"], 2, "ping", {}))
        started = asyncio.get_running_loop().time()
        assert await receive_type(ws, "pong") == {"type": "pong", "seq": 2}
        assert asyncio.get_running_loop().time() - started < 0.5  # not behind the 1.5 s herdr call
        assert await receive_type(ws, "ack", timeout=3) == {"type": "ack", "seq": 1, "ok": True}
        await ws.close()

    run(scenario, herdr)


def test_a_herdr_hiccup_does_not_repeat_old_alerts(monkeypatch):
    import keypad_host.agents
    monkeypatch.setattr(keypad_host.agents, "ALERT_COOLDOWN", 0.0)  # the old alert is long past its cooldown
    herdr = FakeHerdr()

    async def scenario(client):
        ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        await ws.receive_json()
        await receive_type(ws, "agents")
        herdr.raw[0]["agent_status"] = "done"
        assert (await receive_type(ws, "agent.alert"))["agent"]["status"] == "done"
        herdr.down = True
        assert (await receive_type(ws, "agents"))["available"] is False
        herdr.down = False
        assert (await receive_type(ws, "agents"))["available"] is True
        try:
            again = await receive_type(ws, "agent.alert", timeout=0.4)
        except asyncio.TimeoutError:
            again = None
        assert again is None  # still the same "done": nothing new to tell
        await ws.close()

    run(scenario, herdr)


def test_the_open_agents_history_and_new_messages_reach_the_phone(tmp_path):
    folder = tmp_path / ".claude/projects/-home-u-proj"
    folder.mkdir(parents=True)
    f = folder / "856c1e70-0a17-485b-9553-e5f1a24ddb1f.jsonl"
    f.write_text(json.dumps({"type": "user", "message": {"content": "roda os testes"}}) + "\n")

    class Herdr(FakeHerdr):
        async def session(self, target):
            return ("claude", "856c1e70-0a17-485b-9553-e5f1a24ddb1f")

    got = {}

    async def scenario(client):
        from keypad_host.server import create_app  # noqa: F401 (the app is built by run)
        ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
        sid = (await ws.receive_json())["sessionId"]
        await ws.send_str(frame(sid, 1, "agent.history", {"id": "w1:p1", "limit": 30}))
        page = await receive_type(ws, "agent.history")
        got["page"] = page
        await ws.send_str(frame(sid, 2, "agent.follow", {"id": "w1:p1", "after": page["end"]}))
        await asyncio.sleep(0.3)
        with open(f, "a") as out:
            out.write(json.dumps({"type": "assistant", "message": {"content": [{"type": "text", "text": "verde"}]}}) + "\n")
        got["new"] = await receive_type(ws, "agent.items", timeout=4)
        await ws.send_str(frame(sid, 3, "agent.unfollow", {}))
        await ws.close()

    run_with_home(scenario, Herdr(), tmp_path)
    assert got["page"]["items"] == [{"k": "you", "t": "roda os testes"}]
    assert got["new"]["items"] == [{"k": "said", "t": "verde"}] and got["new"]["end"] == f.stat().st_size


def run_with_home(scenario, herdr, home):
    async def main():
        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, herdr=herdr, agent_poll=0.05, transcripts_home=home)
        async with TestClient(TestServer(app)) as client:
            await scenario(client)

    asyncio.run(main())
