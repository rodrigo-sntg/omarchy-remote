import asyncio
import json
import os

from keypad_host.agents import AgentCommands
from keypad_host.projects import projects
from keypad_host.protocol import Message


def test_projects_are_herdrs_open_ones_then_claudes_recent_ones(tmp_path):
    home = tmp_path
    for name in ("app", "api", "old", "site"):
        (home / "dev" / name).mkdir(parents=True)
    (home / ".claude.json").write_text(json.dumps({"projects": {str(home / "dev/api"): {}, str(home / "dev/old"): {}, str(home / "gone"): {}, str(home): {}}}))
    for name, age in (("api", 10), ("old", 5000)):
        folder = home / ".claude/projects" / ("-" + str(home / "dev" / name).strip("/").replace("/", "-"))
        folder.mkdir(parents=True)
        (folder / "s.jsonl").write_text("")
        os.utime(folder / "s.jsonl", (1_000_000 - age, 1_000_000 - age))
    panes = [{"workspace_id": "w1", "cwd": str(home / "dev/app")}, {"workspace_id": "w1", "cwd": str(home / "dev/app")}, {"workspace_id": "w2", "cwd": str(home)}]
    got = projects(panes, home)
    assert [(p["name"], p.get("workspace")) for p in got] == [("app", "w1"), ("api", None), ("old", None)]
    assert got[0]["path"] == str(home / "dev/app")


class Herdr:
    def __init__(self):
        self.calls = []

    async def panes(self):
        return [{"workspace_id": "w1", "cwd": "/home/u/app"}]

    async def create_tab(self, workspace, cwd, label):
        self.calls.append(("tab", workspace, cwd))
        return "w1:p7"

    async def create_workspace(self, cwd, label):
        self.calls.append(("workspace", cwd, label))
        return "w9:p1"

    async def start_agent(self, name, kind, pane):
        self.calls.append(("start", kind, pane))

    async def prompt(self, target, text):
        self.calls.append(("prompt", target, text))
        return {}


def run(message, herdr, home):
    return asyncio.run(AgentCommands(herdr, home=home).handle(message))


def test_an_agent_starts_in_a_new_tab_of_its_projects_workspace(tmp_path):
    project = tmp_path / "app"
    project.mkdir()
    herdr = Herdr()

    async def panes():
        return [{"workspace_id": "w1", "cwd": str(project)}]
    herdr.panes = panes
    reply = run(Message("s", 5, "agent.start", {"cwd": str(project), "kind": "claude", "prompt": "roda os testes"}), herdr, tmp_path)
    assert reply == [{"type": "ack", "seq": 5, "ok": True}, {"type": "agent.started", "id": "w1:p7"}]
    assert herdr.calls == [("tab", "w1", str(project)), ("start", "claude", "w1:p7"), ("prompt", "w1:p7", "roda os testes")]


def test_a_project_not_open_in_herdr_gets_its_own_workspace(tmp_path):
    project = tmp_path / "site"
    project.mkdir()
    herdr = Herdr()
    reply = run(Message("s", 6, "agent.start", {"cwd": str(project), "kind": "codex", "prompt": ""}), herdr, tmp_path)
    assert reply[1] == {"type": "agent.started", "id": "w9:p1"}
    assert herdr.calls == [("workspace", str(project), "site"), ("start", "codex", "w9:p1")]


def test_only_existing_folders_in_the_home(tmp_path):
    herdr = Herdr()
    for cwd in ("/etc", str(tmp_path / "missing"), "relative"):
        reply = run(Message("s", 7, "agent.start", {"cwd": cwd, "kind": "claude", "prompt": ""}), herdr, tmp_path)
        assert reply[0]["ok"] is False
    assert herdr.calls == []
