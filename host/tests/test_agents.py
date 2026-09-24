import asyncio
import json

from keypad_host.agents import ALERT_COOLDOWN, AgentCommands, Alerts, Follower, summarize
from keypad_host.herdr import HerdrError
from keypad_host.protocol import Message

RAW = [
    {"pane_id": "w2:p1", "agent": "codex", "agent_status": "working", "workspace_id": "w2", "tab_id": "w2:t1",
     "cwd": "/home/u/dev/api/", "terminal_title_stripped": "Add endpoint", "state_change_seq": 3},
    {"pane_id": "w1:p1", "agent": "claude", "agent_status": "blocked", "workspace_id": "w1", "tab_id": "w1:t1",
     "cwd": "/home/u/proj", "terminal_title_stripped": "Fix tests", "state_change_seq": 7},
    {"pane_id": "w1:p2", "agent": "claude", "agent_status": "weird", "workspace_id": "w1", "tab_id": "w1:t1",
     "cwd": "", "terminal_title": "✳ raw", "state_change_seq": 1},
]


def test_summary_is_compact_sorted_and_only_the_last_directory():
    agents = summarize(RAW)
    assert [a["id"] for a in agents] == ["w1:p1", "w1:p2", "w2:p1"]
    assert agents[0] == {"id": "w1:p1", "kind": "claude", "status": "blocked", "title": "Fix tests",
                         "workspace": "w1", "cwd": "proj", "seq": 7}
    assert agents[2]["cwd"] == "api"
    assert agents[1]["status"] == "unknown" and agents[1]["title"] == "✳ raw"


def with_status(status, id_="w1:p1"):
    return [dict(summarize(RAW)[0], id=id_, status=status)]


def test_first_snapshot_never_alerts():
    assert Alerts().update(with_status("blocked"), now=0.0) == []


def test_becoming_blocked_or_done_alerts():
    alerts = Alerts()
    alerts.update(with_status("working"), 0.0)
    assert [a["status"] for a in alerts.update(with_status("blocked"), 1.0)] == ["blocked"]
    alerts.update(with_status("working"), 2.0)
    assert [a["status"] for a in alerts.update(with_status("done"), 3.0)] == ["done"]


def test_flapping_agent_alerts_once_per_cooldown():
    alerts = Alerts()
    alerts.update(with_status("working"), 0.0)
    assert alerts.update(with_status("blocked"), 1.0)
    alerts.update(with_status("working"), 2.0)
    assert alerts.update(with_status("blocked"), 3.0) == []  # same agent, same state, too soon
    alerts.update(with_status("working"), 4.0)
    assert alerts.update(with_status("blocked"), 1.0 + ALERT_COOLDOWN)


def test_an_agent_that_appears_already_blocked_alerts():
    alerts = Alerts()
    alerts.update([], 0.0)
    assert alerts.update(with_status("blocked", "w9:p1"), 1.0)


class FakeHerdr:
    def __init__(self):
        self.calls = []

    async def read(self, target, lines=40, ansi=False):
        self.calls.append(("read", target, lines, ansi) if ansi else ("read", target, lines))
        return "\x1b[7m Status \x1b[0m" * 3000 if ansi else "x" * 10_000

    async def send_keys(self, target, keys):
        self.calls.append(("keys", target, keys))
        if target == "w1:p9":
            raise HerdrError("agent_not_found", "agent target w1:p9 not found")
        return {}

    async def prompt(self, target, text):
        self.calls.append(("prompt", target, text))
        return {}

    async def focus(self, target):
        self.calls.append(("focus", target))
        return {}

    async def session(self, target):
        return ("claude", "856c1e70-0a17-485b-9553-e5f1a24ddb1f") if target == "w1:p1" else None

    async def agent_info(self, target):
        return {"agent": "claude", "cwd": "/nowhere"} if target == "w1:p1" else {}


def test_read_answers_with_the_last_8000_characters():
    herdr = FakeHerdr()
    replies = asyncio.run(AgentCommands(herdr).handle(Message("s", 4, "agent.read", {"id": "w1:p1", "lines": 40})))
    assert replies == [{"type": "agent.text", "id": "w1:p1", "text": "x" * 8000}]
    assert herdr.calls == [("read", "w1:p1", 40)]


def test_keys_prompt_and_focus_are_acked_and_errors_come_back_in_the_ack():
    herdr = FakeHerdr()
    commands = AgentCommands(herdr)
    assert asyncio.run(commands.handle(Message("s", 5, "agent.keys", {"id": "w1:p1", "keys": ["enter"]}))) == [{"type": "ack", "seq": 5, "ok": True}]
    assert asyncio.run(commands.handle(Message("s", 6, "agent.prompt", {"id": "w1:p1", "text": "go on"}))) == [{"type": "ack", "seq": 6, "ok": True}]
    assert asyncio.run(commands.handle(Message("s", 7, "agent.focus", {"id": "w1:p1"}))) == [{"type": "ack", "seq": 7, "ok": True}]
    failed = asyncio.run(commands.handle(Message("s", 8, "agent.keys", {"id": "w1:p9", "keys": ["y"]})))
    assert failed == [{"type": "ack", "seq": 8, "ok": False, "error": "agent target w1:p9 not found"}]
    assert asyncio.run(commands.handle(Message("s", 9, "pointer.move", {"dx": 1, "dy": 1}))) == []


def claude_file(home, lines):
    folder = home / ".claude/projects/-home-u-app"
    folder.mkdir(parents=True)
    f = folder / "856c1e70-0a17-485b-9553-e5f1a24ddb1f.jsonl"
    f.write_text("".join(json.dumps(line) + "\n" for line in lines))
    return f


def said(text):
    return {"type": "assistant", "message": {"content": [{"type": "text", "text": text}]}}


def test_history_is_a_page_of_the_session_file(tmp_path):
    f = claude_file(tmp_path, [{"type": "user", "message": {"content": "oi"}}, said("olá")])
    commands = AgentCommands(FakeHerdr(), home=tmp_path)
    reply = asyncio.run(commands.handle(Message("s", 3, "agent.history", {"id": "w1:p1", "before": None, "limit": 30})))
    assert reply == [{"type": "agent.history", "id": "w1:p1", "items": [{"k": "you", "t": "oi"}, {"k": "said", "t": "olá"}],
                      "start": 0, "end": f.stat().st_size, "more": False}]


def test_without_a_session_file_the_phone_is_told_so(tmp_path):
    commands = AgentCommands(FakeHerdr(), home=tmp_path)
    reply = asyncio.run(commands.handle(Message("s", 3, "agent.history", {"id": "w1:p2", "before": None, "limit": 30})))
    assert reply == [{"type": "agent.history", "id": "w1:p2", "none": True}]


def test_an_output_is_fetched_whole(tmp_path):
    f = claude_file(tmp_path, [{"type": "user", "message": {"content": [{"type": "tool_result", "tool_use_id": "t1", "content": "y" * 3000}]}}])
    commands = AgentCommands(FakeHerdr(), home=tmp_path)
    reply = asyncio.run(commands.handle(Message("s", 3, "agent.output", {"id": "w1:p1", "at": 0, "call": "t1"})))
    assert reply == [{"type": "agent.output", "id": "w1:p1", "call": "t1", "t": "y" * 3000}]


def test_the_follower_pushes_what_is_new(tmp_path):
    f = claude_file(tmp_path, [said("um")])
    sent = []

    async def main():
        async def send(message):
            sent.append(message)
        follower = Follower(AgentCommands(FakeHerdr(), home=tmp_path), send, poll=0.05)
        follower.follow("w1:p1", f.stat().st_size)
        await asyncio.sleep(0.2)
        with open(f, "a") as out:
            out.write(json.dumps(said("dois")) + "\n")
        for _ in range(40):
            await asyncio.sleep(0.05)
            if sent:
                break
        follower.stop()

    asyncio.run(main())
    assert sent == [{"type": "agent.items", "id": "w1:p1", "items": [{"k": "said", "t": "dois"}], "end": f.stat().st_size}]


def test_an_agents_slash_menu_comes_with_its_kind(tmp_path):
    commands = AgentCommands(FakeHerdr(), home=tmp_path)
    reply = asyncio.run(commands.handle(Message("s", 3, "agent.commands", {"id": "w1:p1"})))[0]
    assert reply["type"] == "agent.commands" and reply["id"] == "w1:p1" and reply["kind"] == "claude"
    assert any(i["n"] == "/model" for i in reply["items"])
    unknown = asyncio.run(commands.handle(Message("s", 4, "agent.commands", {"id": "w1:p2"})))[0]
    assert unknown == {"type": "agent.commands", "id": "w1:p2", "kind": "", "items": []}


def test_the_screen_with_its_colors_is_its_own_reply():
    herdr = FakeHerdr()
    reply = asyncio.run(AgentCommands(herdr).handle(Message("s", 4, "agent.read", {"id": "w1:p1", "lines": 60, "ansi": True})))
    assert reply[0]["type"] == "agent.screen" and reply[0]["id"] == "w1:p1"
    assert reply[0]["text"].endswith("\x1b[0m") and len(reply[0]["text"]) <= 60_000
    assert herdr.calls == [("read", "w1:p1", 60, True)]
