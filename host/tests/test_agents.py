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


TASK = "87aa6636-950f-4a7b-9c68-5c35a87cc1b1"
OPEN = "7a2a2b65-287a-4d90-809f-cc1033d25b36"


class SessionsHerdr(FakeHerdr):
    """herdr with one agent open (OPEN, run with --dangerously-skip-permissions) in workspace w5."""

    async def panes(self):
        return [{"pane_id": "w5:p3", "workspace_id": "w5", "agent": "claude", "cwd": self.cwd},
                {"pane_id": "w6:p1", "workspace_id": "w6", "agent": None, "cwd": "/elsewhere"}]

    async def processes(self, pane):
        self.calls.append(("processes", pane))
        return [{"argv": ["claude", "--dangerously-skip-permissions", "--resume", OPEN], "pid": 1}] if pane == "w5:p3" else []

    async def create_tab(self, workspace, cwd, label, env=None):
        self.calls.append(("tab", workspace, cwd, label, env))
        return "w5:p9"

    async def create_workspace(self, cwd, label, env=None):
        self.calls.append(("workspace", cwd, label, env))
        return "wX:p1"

    async def run(self, pane, command):
        self.calls.append(("run", pane, command))


def two_sessions(home):
    project = home / "studio/nf"
    project.mkdir(parents=True)
    for session_id, title in ((TASK, "TASK-12 invoices"), (OPEN, "TASK-7 reports")):
        folder = home / ".claude-studio/projects" / str(project).replace("/", "-")
        folder.mkdir(parents=True, exist_ok=True)
        (folder / f"{session_id}.jsonl").write_text(
            json.dumps({"type": "user", "cwd": str(project), "entrypoint": "cli", "message": {"content": "oi"}}) + "\n"
            + json.dumps({"type": "ai-title", "aiTitle": title}) + "\n")
    return project


def test_the_recent_sessions_say_which_are_open_and_remember_their_options(tmp_path):
    project = two_sessions(tmp_path)
    herdr = SessionsHerdr()
    herdr.cwd = str(project)
    commands = AgentCommands(herdr, home=tmp_path)
    reply = asyncio.run(commands.handle(Message("s", 1, "agent.sessions", {})))
    items = {i["id"]: i for i in reply[0]["items"]}
    assert reply[0]["type"] == "agent.sessions"
    assert items[OPEN]["open"] is True and items[OPEN]["pane"] == "w5:p3"
    assert items[TASK]["open"] is False and "pane" not in items[TASK]
    assert items[TASK]["account"] == "studio" and items[TASK]["title"] == "TASK-12 invoices"
    assert commands.remembered.flags(OPEN) == ["--dangerously-skip-permissions"]


def test_reopening_a_closed_session_resumes_it_in_its_projects_workspace(tmp_path):
    project = two_sessions(tmp_path)
    herdr = SessionsHerdr()
    herdr.cwd = str(project)
    commands = AgentCommands(herdr, home=tmp_path)
    commands.remembered.note(TASK, ["--dangerously-skip-permissions"])
    reply = asyncio.run(commands.handle(Message("s", 2, "agent.resume", {"kind": "claude", "session": TASK})))
    assert reply == [{"type": "ack", "seq": 2, "ok": True}, {"type": "agent.started", "id": "w5:p9"}]
    assert ("tab", "w5", str(project), "TASK-12 invoices", {"CLAUDE_CONFIG_DIR": str(tmp_path / ".claude-studio")}) in herdr.calls
    assert ("run", "w5:p9", f"claude --dangerously-skip-permissions --resume {TASK}") in herdr.calls


def test_reopening_one_that_is_open_goes_to_it(tmp_path):
    project = two_sessions(tmp_path)
    herdr = SessionsHerdr()
    herdr.cwd = str(project)
    reply = asyncio.run(AgentCommands(herdr, home=tmp_path).handle(Message("s", 3, "agent.resume", {"kind": "claude", "session": OPEN})))
    assert reply[-1] == {"type": "agent.started", "id": "w5:p3"}
    assert not any(c[0] in ("tab", "run") for c in herdr.calls)


def test_an_unknown_session_or_a_project_outside_home_is_refused(tmp_path):
    herdr = SessionsHerdr()
    herdr.cwd = "/x"
    commands = AgentCommands(herdr, home=tmp_path)
    reply = asyncio.run(commands.handle(Message("s", 4, "agent.resume", {"kind": "claude", "session": TASK})))
    assert reply[0]["ok"] is False
    folder = tmp_path / ".claude/projects/-etc"
    folder.mkdir(parents=True)
    (folder / f"{TASK}.jsonl").write_text(json.dumps({"type": "user", "cwd": "/etc", "message": {"content": "oi"}}) + "\n")
    reply = asyncio.run(commands.handle(Message("s", 5, "agent.resume", {"kind": "claude", "session": TASK})))
    assert reply[0]["ok"] is False
    assert not any(c[0] in ("tab", "run", "workspace") for c in herdr.calls)


def test_each_claude_agent_says_which_account_it_runs_in(tmp_path):
    """~/.claude is the person's own (no account); ~/.claude-<name> is <name>'s."""
    folder = tmp_path / ".claude-studio/projects/-home-u-app"
    folder.mkdir(parents=True)
    (folder / "856c1e70-0a17-485b-9553-e5f1a24ddb1f.jsonl").write_text("")
    commands = AgentCommands(FakeHerdr(), home=tmp_path)
    listed = asyncio.run(commands.with_subagents([
        {"id": "w1:p1", "kind": "claude"}, {"id": "w1:p2", "kind": "claude"}, {"id": "w2:p1", "kind": "codex"}]))
    assert listed[0]["account"] == "studio"
    assert "account" not in listed[1] and "account" not in listed[2]


def test_the_own_account_has_no_name(tmp_path):
    claude_file(tmp_path, [said("oi")])
    listed = asyncio.run(AgentCommands(FakeHerdr(), home=tmp_path).with_subagents([{"id": "w1:p1", "kind": "claude"}]))
    assert "account" not in listed[0]


def test_each_agent_says_when_it_last_did_something(tmp_path):
    """The session file's last write: the phone lists the most recently active first."""
    import os
    f = claude_file(tmp_path, [said("oi")])
    os.utime(f, (1_790_000_000, 1_790_000_000))
    listed = asyncio.run(AgentCommands(FakeHerdr(), home=tmp_path).with_subagents([{"id": "w1:p1", "kind": "claude"}, {"id": "w1:p2", "kind": "claude"}]))
    assert listed[0]["active"] == 1_790_000_000
    assert "active" not in listed[1]  # no session file known


def test_what_an_agent_last_said_after_a_moment_comes_from_its_session(tmp_path):
    claude_file(tmp_path, [
        {"type": "assistant", "timestamp": "2026-09-25T10:00:00Z", "message": {"content": [{"type": "text", "text": "antes"}]}},
        {"type": "assistant", "timestamp": "2026-09-25T10:05:00Z", "message": {"content": [{"type": "text", "text": "Vou olhar."}]}},
        {"type": "assistant", "timestamp": "2026-09-25T10:06:00Z", "message": {"content": [{"type": "text", "text": "Dois riscos."}]}},
    ])
    commands = AgentCommands(FakeHerdr(), home=tmp_path)
    from datetime import datetime, timezone
    since = datetime(2026, 9, 25, 10, 1, tzinfo=timezone.utc).timestamp()
    assert asyncio.run(commands.last_said("w1:p1", since)) == "Dois riscos."
    later = datetime(2026, 9, 25, 11, 0, tzinfo=timezone.utc).timestamp()
    assert asyncio.run(commands.last_said("w1:p1", later)) is None     # nothing new since
    assert asyncio.run(commands.last_said("w1:p2", since)) is None     # no session known


def test_the_phones_agent_to_agent_requests_go_to_the_links():
    from keypad_host.links import LinkError

    class Links:
        def __init__(self):
            self.calls = []

        async def relay(self, source, target, text, note=None):
            self.calls.append(("relay", source, target, text, note))
            if target == "w1:p9":
                raise LinkError("Esse agente não está mais no herdr.")
            return "id1"

        async def review(self, source, kind, lang):
            self.calls.append(("review", source, kind, lang))
            return "w1:p5"

        def dismiss(self, notice):
            self.calls.append(("dismiss", notice))

        async def publish(self, force=False):
            self.calls.append(("publish", force))

    commands = AgentCommands(FakeHerdr())
    commands.links = Links()
    ok = asyncio.run(commands.handle(Message("s", 1, "agent.relay", {"from": "w1:p1", "to": "w1:p2", "text": "x", "note": None})))
    gone = asyncio.run(commands.handle(Message("s", 2, "agent.relay", {"from": "w1:p1", "to": "w1:p9", "text": "x", "note": None})))
    review = asyncio.run(commands.handle(Message("s", 3, "agent.review", {"id": "w1:p1", "kind": "codex", "lang": "pt"})))
    asyncio.run(commands.handle(Message("s", 4, "agent.notice.dismiss", {"id": "abcdef12"})))
    assert ok == [{"type": "ack", "seq": 1, "ok": True}]
    assert gone == [{"type": "ack", "seq": 2, "ok": False, "error": "Esse agente não está mais no herdr."}]
    assert review == [{"type": "ack", "seq": 3, "ok": True}, {"type": "agent.reviewing", "id": "w1:p1", "reviewer": "w1:p5"}]
    assert ("dismiss", "abcdef12") in commands.links.calls and ("publish", False) in commands.links.calls
