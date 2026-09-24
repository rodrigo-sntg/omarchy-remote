import asyncio
import json
import os

from keypad_host.agents import AgentCommands
from keypad_host.protocol import Message
from keypad_host.subagents import subagents


def sub(folder, agent_id, description, last, age=0.0, now=1_000_000.0, kind="general-purpose"):
    folder.mkdir(parents=True, exist_ok=True)
    (folder / f"agent-{agent_id}.meta.json").write_text(json.dumps({"agentType": kind, "description": description, "requestShape": "background"}))
    f = folder / f"agent-{agent_id}.jsonl"
    f.write_text(json.dumps({"type": "user", "message": {"content": "go"}}) + "\n" + json.dumps(last) + "\n")
    os.utime(f, (now - age, now - age))
    os.utime(folder / f"agent-{agent_id}.meta.json", (now - age - 60, now - age - 60))


def using_tool():
    return {"type": "assistant", "message": {"content": [{"type": "text", "text": "Vou ler"}, {"type": "tool_use", "id": "t", "name": "Read", "input": {}}]}}


def answered():
    return {"type": "assistant", "message": {"content": [{"type": "text", "text": "Pronto: 3 telas traduzidas."}]}}


def test_a_subagent_runs_until_it_answers(tmp_path):
    session = tmp_path / "s1.jsonl"
    session.write_text("")
    folder = tmp_path / "s1" / "subagents"
    sub(folder, "a1", "i18n sweep", using_tool(), age=5)
    sub(folder, "a2", "revisar o host", answered(), age=30)
    sub(folder, "a3", "esquecido", using_tool(), age=3600)
    got = {s["id"]: s for s in subagents(session, now=1_000_000.0)}
    assert got["a1"]["state"] == "running" and got["a1"]["desc"] == "i18n sweep" and got["a1"]["type"] == "general-purpose"
    assert got["a2"]["state"] == "done" and got["a2"]["said"] == "Pronto: 3 telas traduzidas."
    assert got["a3"]["state"] == "stale"
    assert got["a1"]["since"] == 65 and got["a1"]["quiet"] == 5  # started 65 s ago, last wrote 5 s ago
    assert list(got)[0] == "a1"  # the most recent first


def test_no_subagents_folder_is_none(tmp_path):
    session = tmp_path / "s2.jsonl"
    session.write_text("")
    assert subagents(session, now=1.0) == []


class Herdr:
    async def session(self, target):
        return ("claude", "856c1e70-0a17-485b-9553-e5f1a24ddb1f") if target == "w1:p1" else None

    async def agent_info(self, target):
        return {}


def claude_home(tmp_path):
    folder = tmp_path / ".claude/projects/-home-u-app"
    folder.mkdir(parents=True)
    (folder / "856c1e70-0a17-485b-9553-e5f1a24ddb1f.jsonl").write_text("")
    return folder / "856c1e70-0a17-485b-9553-e5f1a24ddb1f" / "subagents"


def test_the_phone_asks_for_an_agents_subagents(tmp_path):
    import time
    sub(claude_home(tmp_path), "a1", "i18n sweep", using_tool(), now=time.time())
    reply = asyncio.run(AgentCommands(Herdr(), home=tmp_path).handle(Message("s", 1, "agent.subagents", {"id": "w1:p1"})))[0]
    assert reply["type"] == "agent.subagents" and reply["id"] == "w1:p1"
    assert [(s["id"], s["state"]) for s in reply["items"]] == [("a1", "running")]


def test_the_list_counts_each_agents_running_subagents(tmp_path):
    import time
    folder = claude_home(tmp_path)
    sub(folder, "a1", "um", using_tool(), now=time.time())
    sub(folder, "a2", "dois", using_tool(), now=time.time())
    sub(folder, "a3", "feito", answered(), now=time.time())
    commands = AgentCommands(Herdr(), home=tmp_path)
    agents = [{"id": "w1:p1", "kind": "claude"}, {"id": "w2:p1", "kind": "codex"}]
    counted = asyncio.run(commands.with_subagents(agents))
    assert counted == [{"id": "w1:p1", "kind": "claude", "subagents": 2}, {"id": "w2:p1", "kind": "codex"}]
