import asyncio
import json

import pytest

from keypad_host.herdr import HerdrError
from keypad_host.links import Links, LinkError, header, parse_header

PROJECT = "/home/u/app"


class FakeHerdr:
    """Agents by pane: kind, cwd, status. Prompting one starts it "working"; the test finishes it."""

    def __init__(self, agents):
        self.agents_by_pane = {a["pane_id"]: dict(a, state_change_seq=1) for a in agents}
        self.prompts = []

    async def agents(self):
        return list(self.agents_by_pane.values())

    async def agent_info(self, target):
        a = self.agents_by_pane.get(target)
        if a is None:
            raise HerdrError("agent_not_found", "gone")
        return {"pane_id": target, "agent": a["agent"], "cwd": a["cwd"], "agent_status": a["agent_status"]}

    async def prompt(self, target, text):
        if target not in self.agents_by_pane:
            raise HerdrError("agent_not_found", "gone")
        self.prompts.append((target, text))
        self.agents_by_pane[target]["agent_status"] = "working"
        self.agents_by_pane[target]["state_change_seq"] += 1
        return {}

    def finish(self, target, status="done"):
        self.agents_by_pane[target]["agent_status"] = status
        self.agents_by_pane[target]["state_change_seq"] += 1


class FakeCommands:
    """What each agent last said after a time (the transcript), and new agents started."""

    def __init__(self, herdr):
        self.herdr = herdr
        self.said = {}
        self.started = []

    async def last_said(self, target, since):
        text, ts = self.said.get(target, (None, 0))
        return text if text and ts >= since else None

    async def start(self, cwd, kind, prompt):
        pane = f"w9:p{len(self.started) + 50}"
        self.started.append((cwd, kind, prompt))
        self.herdr.agents_by_pane[pane] = {"pane_id": pane, "agent": kind, "cwd": cwd, "agent_status": "working", "state_change_seq": 1}
        return pane


def agent(pane, kind, status="idle", cwd=PROJECT):
    return {"pane_id": pane, "agent": kind, "cwd": cwd, "agent_status": status}


def setup(*agents):
    herdr = FakeHerdr(agents)
    commands = FakeCommands(herdr)
    pushed = []

    async def push(snapshot):
        pushed.append(snapshot)
        return True

    clock = [1000.0]
    links = Links(herdr, commands, push, clock=lambda: clock[0], poll=0.01)
    return links, herdr, commands, pushed, clock


def test_the_header_names_who_sent_it_and_reads_back():
    h = header("claude", "/home/u/app", "w9:p2")
    assert h == "↪ Claude · app · w9:p2"
    assert header("codex", "/home/u/app", "w9:p1", "review") == "↪ Codex · app · w9:p1 · review"
    assert parse_header("↪ Claude · app · w9:p2\n\nthe message") == {"kind": "claude", "project": "app", "pane": "w9:p2", "what": "relay"}
    assert parse_header("↪ Codex · my app · w1:p10 · ask\n\nq")["what"] == "ask"
    assert parse_header("just text") is None


def test_a_message_goes_to_another_agent_with_where_it_came_from():
    links, herdr, _, pushed, _ = setup(agent("w9:p2", "claude"), agent("w9:p1", "codex"))
    asyncio.run(links.relay("w9:p2", "w9:p1", "Use a fila de eventos.", note="O que acha?"))
    target, text = herdr.prompts[0]
    assert target == "w9:p1"
    assert text == "↪ Claude · app · w9:p2\n\nO que acha?\n\n> Use a fila de eventos."
    snap = links.snapshot()
    assert snap["links"][0] | {"since": 0} == {"id": snap["links"][0]["id"], "kind": "relay", "from": "w9:p2", "to": "w9:p1", "state": "working", "since": 0}
    assert pushed and pushed[-1]["type"] == "agent.links"


def test_a_message_to_itself_or_to_no_agent_is_refused():
    links, herdr, _, _, _ = setup(agent("w9:p2", "claude"))
    with pytest.raises(LinkError):
        asyncio.run(links.relay("w9:p2", "w9:p2", "x"))
    with pytest.raises(LinkError):
        asyncio.run(links.relay("w9:p2", "w7:p7", "x"))
    assert herdr.prompts == []


def test_a_review_goes_to_a_free_agent_of_the_project_and_comes_back_as_a_notice():
    links, herdr, commands, pushed, clock = setup(
        agent("w9:p2", "claude"), agent("w9:p1", "codex", "working"), agent("w9:p4", "codex", "idle"),
        agent("w3:p1", "codex", "idle", cwd="/home/u/other"))

    async def main():
        reviewer = await links.review("w9:p2", "codex", "pt")
        assert reviewer == "w9:p4"   # the idle one, in this project (not the busy one, not another project)
        target, text = herdr.prompts[0]
        assert text.startswith("↪ Claude · app · w9:p2 · review\n\n") and "Não altere nenhum arquivo" in text
        await links.tick()                                   # it works…
        commands.said["w9:p4"] = ("Dois riscos: 1) … 2) …", clock[0] + 5)
        herdr.finish("w9:p4")
        await links.tick()                                   # …and finishes
    asyncio.run(main())
    notice = links.snapshot()["notices"][0]
    assert notice | {"id": "x", "ts": 0} == {"id": "x", "kind": "review", "origin": "w9:p2", "from": "w9:p4", "fromKind": "codex",
                                             "text": "Dois riscos: 1) … 2) …", "ts": 0}
    links.dismiss(notice["id"])
    assert links.snapshot()["notices"] == []


def test_without_a_free_reviewer_one_is_started_in_the_project():
    links, herdr, commands, _, _ = setup(agent("w9:p2", "claude"), agent("w9:p1", "codex", "working"))
    reviewer = asyncio.run(links.review("w9:p2", "codex", "en"))
    assert reviewer == "w9:p50"
    cwd, kind, prompt = commands.started[0]
    assert (cwd, kind) == (PROJECT, "codex") and prompt.startswith("↪ Claude · app · w9:p2 · review") and "Do not change any file" in prompt


def test_asking_waits_for_the_answer():
    links, herdr, commands, _, clock = setup(agent("w9:p2", "claude"), agent("w9:p1", "codex"))

    async def main():
        asking = asyncio.create_task(links.ask("w9:p2", "codex", "Qual lib de filas vocês usam?", timeout=5))
        await asyncio.sleep(0.05)
        target, text = herdr.prompts[0]
        assert target == "w9:p1" and text.startswith("↪ Claude · app · w9:p2 · ask\n\n")
        commands.said["w9:p1"] = ("A fila do asyncio.", clock[0] + 1)
        herdr.finish("w9:p1", "idle")
        return await asking
    assert asyncio.run(main()) == {"ok": True, "agent": "w9:p1", "text": "A fila do asyncio."}


def test_who_is_answering_a_question_cannot_ask_back():
    links, herdr, commands, _, clock = setup(agent("w9:p2", "claude"), agent("w9:p1", "codex"))

    async def main():
        first = asyncio.create_task(links.ask("w9:p2", "codex", "q", timeout=5))
        await asyncio.sleep(0.05)
        with pytest.raises(LinkError):
            await links.ask("w9:p1", "claude", "and you?", timeout=5)   # the answerer asking back: no ping-pong
        commands.said["w9:p1"] = ("a", clock[0] + 1)
        herdr.finish("w9:p1")
        await first
    asyncio.run(main())


def test_an_agent_that_closes_ends_the_wait_with_an_error():
    links, herdr, _, _, _ = setup(agent("w9:p2", "claude"), agent("w9:p1", "codex"))

    async def main():
        asking = asyncio.create_task(links.ask("w9:p2", "w9:p1", "q", timeout=5))
        await asyncio.sleep(0.05)
        del herdr.agents_by_pane["w9:p1"]
        return await asking
    reply = asyncio.run(main())
    assert reply["ok"] is False and "fechou" in reply["error"]


def test_a_question_nobody_answers_in_time_gives_up():
    links, herdr, _, _, _ = setup(agent("w9:p2", "claude"), agent("w9:p1", "codex"))
    reply = asyncio.run(links.ask("w9:p2", "codex", "q", timeout=0.1))
    assert reply["ok"] is False and "tempo" in reply["error"]


def test_an_agent_that_finished_between_two_looks_has_answered():
    """herdr's state counter moved since the prompt: it worked and finished, even unseen."""
    links, herdr, commands, _, clock = setup(agent("w9:p2", "claude"), agent("w9:p1", "codex"))
    asyncio.run(links.relay("w9:p2", "w9:p1", "x"))
    herdr.finish("w9:p1")
    asyncio.run(links.tick())
    assert links.snapshot()["links"][0]["state"] == "done"


def test_an_agent_not_yet_started_on_it_is_not_taken_as_done():
    links, herdr, commands, _, clock = setup(agent("w9:p2", "claude"), agent("w9:p1", "codex"))
    asyncio.run(links.relay("w9:p2", "w9:p1", "x"))
    herdr.agents_by_pane["w9:p1"]["agent_status"] = "idle"   # herdr hasn't noticed the prompt yet
    herdr.agents_by_pane["w9:p1"]["state_change_seq"] = 1
    asyncio.run(links.tick())
    assert links.snapshot()["links"][0]["state"] == "working"


def test_old_links_and_notices_fade_away():
    links, herdr, commands, _, clock = setup(agent("w9:p2", "claude"), agent("w9:p1", "codex"))
    asyncio.run(links.relay("w9:p2", "w9:p1", "x"))
    herdr.finish("w9:p1")
    asyncio.run(links.tick())
    assert links.snapshot()["links"][0]["state"] == "done"
    clock[0] += 3 * 60
    assert links.snapshot()["links"] == []   # a finished link shows for a moment only
    json.dumps(links.snapshot())            # always sendable as is


def test_a_new_agent_slow_to_start_is_not_taken_as_done():
    """Opened just for the review, it may take long to start: only its working and then stopping ends it."""
    links, herdr, commands, _, clock = setup(agent("w9:p2", "claude"))
    reviewer = asyncio.run(links.review("w9:p2", "codex", "pt"))
    herdr.agents_by_pane[reviewer]["agent_status"] = "idle"     # still starting: herdr shows it idle
    clock[0] += 60
    asyncio.run(links.tick())
    assert links.snapshot()["links"][0]["state"] == "working" and links.snapshot()["notices"] == []
    herdr.agents_by_pane[reviewer]["agent_status"] = "working"
    asyncio.run(links.tick())
    commands.said[reviewer] = ("Tudo certo.", clock[0] + 5)
    herdr.finish(reviewer)
    asyncio.run(links.tick())
    assert links.snapshot()["notices"][0]["text"] == "Tudo certo."


def test_asking_a_busy_agent_by_its_pane_is_refused():
    """Its current task finishing would pass for the answer."""
    links, herdr, _, _, _ = setup(agent("w9:p2", "claude"), agent("w9:p1", "codex", "working"))
    with pytest.raises(LinkError):
        asyncio.run(links.ask("w9:p2", "w9:p1", "q", timeout=5))
    assert herdr.prompts == []


def test_a_review_that_never_comes_ends_as_failed():
    links, herdr, commands, _, clock = setup(agent("w9:p2", "claude"), agent("w9:p4", "codex"))
    asyncio.run(links.review("w9:p2", "codex", "pt"))
    herdr.agents_by_pane["w9:p4"]["agent_status"] = "working"
    clock[0] += 31 * 60
    asyncio.run(links.tick())
    assert links.snapshot()["links"][0]["state"] == "failed"
