"""What the phone sees of herdr's agents (docs/PLANO-V2.md §4.3): compact summaries, alerts when
one becomes blocked or done, and the agent.* commands answered through the herdr CLI."""
import asyncio
import logging
import os
import re
import secrets
from pathlib import Path

from .agent_commands import commands_for
from .herdr import HerdrError, running_session
from .links import LinkError
from .sessions import LIMIT, account_of, Remembered, config_dir_of, describe, kept_flags, recent, resume_command
from .git_view import file_diff, git_summary
from .projects import projects
from .subagents import running as running_subagents
from .subagents import subagents
from .transcript import find, full_output, read_back, read_forward
from .watch import changes

log = logging.getLogger(__name__)

STATES = ("idle", "working", "blocked", "done", "unknown")
ALERT_STATES = ("blocked", "done")
ALERT_COOLDOWN = 30.0  # seconds: a flapping agent wakes the phone once, not every second
TEXT_LIMIT = 8000
SCREEN_LIMIT = 60_000  # the screen with its color codes: a full panel fits


def summarize(raw: list[dict]) -> list[dict]:
    """One compact dict per agent; the full path stays on the PC, only the last directory travels."""
    agents = []
    for agent in raw:
        status = agent.get("agent_status")
        agents.append({
            "id": agent["pane_id"],
            "kind": agent.get("agent", ""),
            "status": status if status in STATES else "unknown",
            "title": agent.get("terminal_title_stripped") or agent.get("terminal_title") or "",
            "workspace": agent.get("workspace_id", ""),
            "cwd": os.path.basename((agent.get("cwd") or "").rstrip("/")),
            "seq": int(agent.get("state_change_seq", 0)),
        })
    return sorted(agents, key=lambda a: (a["workspace"], a["id"]))


class Alerts:
    """Which agents to wake the phone for: those that just became blocked or done."""

    def __init__(self):
        self._status = None  # id -> status at the previous update; None before the first
        self._alerted = {}   # (id, status) -> when it last alerted

    def update(self, agents: list[dict], now: float) -> list[dict]:
        alerts = []
        if self._status is not None:
            for agent in agents:
                status = agent["status"]
                if status in ALERT_STATES and self._status.get(agent["id"]) != status:
                    key = (agent["id"], status)
                    if key not in self._alerted or now - self._alerted[key] >= ALERT_COOLDOWN:
                        self._alerted[key] = now
                        alerts.append(agent)
        self._status = {agent["id"]: agent["status"] for agent in agents}
        return alerts


class AgentCommands:
    """agent.* messages from the phone (already validated by the protocol), answered via herdr."""

    def __init__(self, herdr, home=None):
        self.herdr = herdr
        self.home = home
        self._files = {}  # pane -> (when resolved, (kind, session file) or None)
        home_dir = Path(home) if home else Path.home()
        self.remembered = Remembered(home_dir / ".local/state/omarchy-remote/sessions.json")
        self._observing = None
        self.links = None   # links.Links, set by the server

    async def _observe_quietly(self):
        try:
            await self.observe()
        except Exception as error:  # a background look: never takes the agent list down
            log.debug("observing the sessions: %s", error)
        finally:
            self._observing = None

    async def observe(self) -> dict[str, str]:
        """The sessions running in herdr now ({session id: pane}); each one's options are remembered,
        so reopening it later (after a tab closed by mistake) runs it the same way."""
        home = self.home or os.path.expanduser("~")
        running = {}
        for pane in await self.herdr.panes():
            if not pane.get("agent") or not isinstance(pane.get("pane_id"), str):
                continue
            try:
                processes = await self.herdr.processes(pane["pane_id"])
            except HerdrError:
                continue
            for process in processes:
                found = await asyncio.to_thread(running_session, process, home)
                if found is not None:
                    running[found[1]] = pane["pane_id"]
                    self.remembered.note(found[1], kept_flags(found[0], process.get("argv") or []))
                    break
        return running

    async def resume(self, kind: str, session_id: str) -> str:
        """The session running again: its pane if it is open, else a new tab in its project's workspace
        running `claude --resume` (or `codex resume`) with the options it last had. The pane's id."""
        running = await self.observe()
        if session_id in running:
            return running[session_id]
        home = Path(self.home) if self.home else Path.home()
        path = await asyncio.to_thread(find, kind, session_id, home)
        about = await asyncio.to_thread(describe, path, kind) if path else None
        if about is None:
            raise HerdrError("no_session", "Essa sessão não existe mais no PC.")
        real = os.path.realpath(about["cwd"])
        if not os.path.isdir(real) or not real.startswith(str(home) + os.sep):
            raise HerdrError("invalid_cwd", "A pasta dessa sessão não existe mais no seu usuário do PC.")
        config = config_dir_of(path, home) if kind == "claude" else None
        env = {"CLAUDE_CONFIG_DIR": config} if config else None
        label = about["title"][:32].lstrip("-") or tab_label(real)
        workspace = next((p.get("workspace_id") for p in await self.herdr.panes() if p.get("cwd") == real and p.get("workspace_id")), None)
        pane = await self.herdr.create_tab(workspace, real, label, env) if workspace else await self.herdr.create_workspace(real, label, env)
        await self.herdr.run(pane, resume_command(kind, session_id, self.remembered.flags(session_id)))
        return pane

    async def with_subagents(self, agents: list[dict], ttl: float = 30.0) -> list[dict]:
        """The list with each Claude agent's running subagents counted (which session a pane runs is
        asked of herdr at most every [ttl] seconds)."""
        import time as _time
        now = _time.monotonic()
        # Once a minute, what runs where (and with which options), for reopening a closed session.
        if now - getattr(self, "_observed", -1e9) > 60 and self._observing is None:
            self._observed = now
            self._observing = asyncio.create_task(self._observe_quietly())
        out = []
        for agent in agents:
            if agent.get("kind") not in ("claude", "codex"):
                out.append(agent)
                continue
            cached = self._files.get(agent["id"])
            if cached is None or now - cached[0] > ttl:
                cached = (now, await self.transcript(agent["id"]))
                self._files[agent["id"]] = cached
            found = cached[1]
            if not found:
                out.append(agent)
                continue
            try:
                active = {"active": int(found[1].stat().st_mtime)}  # its last write: the list's order
            except OSError:
                active = {}
            if agent.get("kind") != "claude":
                out.append({**agent, **active})
                continue
            count = await asyncio.to_thread(running_subagents, found[1])
            account = account_of(found[1])
            out.append({**agent, "subagents": count, **active, **({"account": account} if account else {})})
        return out

    async def transcript(self, target: str):
        """(agent kind, session file) of the pane, or None: herdr does not know its session, or no file."""
        try:
            found = await self.herdr.session(target)
        except HerdrError:
            return None
        if found is None:
            return None
        kind, session_id = found
        path = await asyncio.to_thread(find, kind, session_id, self.home)
        return (kind, path) if path is not None else None

    async def _links(self, message) -> list[dict]:
        """Agents talking to each other (links.py): a message passed on, a review, a notice read."""
        p = message.payload
        if self.links is None:
            return [{"type": "ack", "seq": message.seq, "ok": False, "error": "Indisponível."}]
        try:
            if message.type == "agent.relay":
                await self.links.relay(p["from"], p["to"], p["text"], p["note"])
            elif message.type == "agent.review":
                reviewer = await self.links.review(p["id"], p["kind"], p["lang"])
                return [{"type": "ack", "seq": message.seq, "ok": True}, {"type": "agent.reviewing", "id": p["id"], "reviewer": reviewer}]
            else:
                self.links.dismiss(p["id"])
                await self.links.publish()
        except LinkError as error:
            return [{"type": "ack", "seq": message.seq, "ok": False, "error": str(error)}]
        return [{"type": "ack", "seq": message.seq, "ok": True}]

    async def last_said(self, target: str, since: float) -> str | None:
        """What the agent said last, if it said it after [since] (epoch seconds): its answer."""
        found = await self.transcript(target)
        if found is None:
            return None
        page = await asyncio.to_thread(read_back, found[1], found[0], None, 60)
        for item in reversed(page["items"]):
            if item["k"] == "said":
                return item["t"] if item.get("ts", since) >= since else None
        return None

    async def start(self, cwd: str, kind: str, prompt: str) -> str:
        """A new agent in the project: a tab in its herdr workspace (or a workspace of its own), the
        agent started there, the task sent. The new pane's id."""
        home = Path(self.home) if self.home else Path.home()
        real = os.path.realpath(cwd)
        if not os.path.isdir(real) or not (real == str(home) or real.startswith(str(home) + os.sep)):
            raise HerdrError("invalid_cwd", "Essa pasta não está no seu usuário do PC.")
        label = tab_label(real)
        workspace = next((p.get("workspace_id") for p in await self.herdr.panes() if p.get("cwd") == real and p.get("workspace_id")), None)
        pane = await self.herdr.create_tab(workspace, real, label) if workspace else await self.herdr.create_workspace(real, label)
        name = re.sub(r"[^a-z0-9-]", "-", f"{kind}-{label}".lower())[:24].strip("-") + "-" + secrets.token_hex(2)
        await self.herdr.start_agent(name, kind, pane)
        if prompt:
            await self.herdr.prompt(pane, prompt)
        return pane

    async def handle(self, message) -> list[dict]:
        if message.type == "agent.sessions":
            try:
                running = await self.observe()
            except HerdrError:
                running = {}
            home = self.home or os.path.expanduser("~")
            items = await asyncio.to_thread(recent, home, LIMIT, set(running))
            return [{"type": "agent.sessions", "items": [{**i, "pane": running[i["id"]]} if i["id"] in running else i for i in items]}]
        if message.type == "projects.list":
            try:
                panes = await self.herdr.panes()
            except HerdrError:
                panes = []
            home = Path(self.home) if self.home else Path.home()
            return [{"type": "projects", "items": await asyncio.to_thread(projects, panes, home)}]
        if not message.type.startswith("agent."):
            return []
        p = message.payload
        try:
            if message.type == "agent.history":
                found = await self.transcript(p["id"])
                if found is None:
                    return [{"type": "agent.history", "id": p["id"], "none": True}]
                page = await asyncio.to_thread(read_back, found[1], found[0], p["before"], p["limit"])
                return [{"type": "agent.history", "id": p["id"], **page}]
            if message.type in ("agent.relay", "agent.review", "agent.notice.dismiss"):
                return await self._links(message)
            if message.type == "agent.resume":
                pane = await self.resume(p["kind"], p["session"])
                return [{"type": "ack", "seq": message.seq, "ok": True}, {"type": "agent.started", "id": pane}]
            if message.type == "agent.start":
                pane = await self.start(p["cwd"], p["kind"], p["prompt"])
                return [{"type": "ack", "seq": message.seq, "ok": True}, {"type": "agent.started", "id": pane}]
            if message.type == "agent.git":
                cwd = await self.herdr.cwd_of(p["id"])
                summary = await git_summary(cwd) if cwd else {"repo": False}
                return [{"type": "agent.git", "id": p["id"], **summary}]
            if message.type == "agent.gitdiff":
                cwd = await self.herdr.cwd_of(p["id"])
                return [{"type": "agent.gitdiff", "id": p["id"], "path": p["path"], "diff": await file_diff(cwd, p["path"]) if cwd else ""}]
            if message.type == "agent.subagents":
                found = await self.transcript(p["id"])
                items = await asyncio.to_thread(subagents, found[1]) if found else []
                return [{"type": "agent.subagents", "id": p["id"], "items": items}]
            if message.type == "agent.commands":
                info = await self.herdr.agent_info(p["id"])
                kind = str(info.get("agent") or "")
                items = await asyncio.to_thread(commands_for, kind, info.get("cwd"), self.home) if kind else []
                return [{"type": "agent.commands", "id": p["id"], "kind": kind, "items": items}]
            if message.type == "agent.output":
                found = await self.transcript(p["id"])
                text = await asyncio.to_thread(full_output, found[1], found[0], p["at"], p["call"]) if found else None
                return [{"type": "agent.output", "id": p["id"], "call": p["call"], "t": text or ""}]
            if message.type == "agent.read" and p.get("ansi"):
                screen = await self.herdr.read(p["id"], p["lines"], ansi=True)
                return [{"type": "agent.screen", "id": p["id"], "text": screen[-SCREEN_LIMIT:]}]
            if message.type == "agent.read":
                text = await self.herdr.read(p["id"], p["lines"])
                return [{"type": "agent.text", "id": p["id"], "text": text[-TEXT_LIMIT:]}]
            if message.type == "agent.keys":
                await self.herdr.send_keys(p["id"], p["keys"])
            elif message.type == "agent.prompt":
                await self.herdr.prompt(p["id"], p["text"])
            elif message.type == "agent.focus":
                await self.herdr.focus(p["id"])
            return [{"type": "ack", "seq": message.seq, "ok": True}]
        except HerdrError as error:
            return [{"type": "ack", "seq": message.seq, "ok": False, "error": str(error)}]


class Follower:
    """While the phone has an agent open: its new messages, as they are written to the session file."""

    def __init__(self, commands: AgentCommands, send, poll: float = 1.0):
        self.commands = commands
        self.send = send
        self.poll = poll
        self._task = None

    def follow(self, target: str, after: int):
        self.stop()
        self._task = asyncio.get_running_loop().create_task(self._run(target, after))

    def stop(self):
        if self._task is not None:
            self._task.cancel()
            self._task = None

    async def _run(self, target: str, after: int):
        found = await self.commands.transcript(target)
        if found is None:
            return
        kind, path = found
        offset = after
        try:
            async for _ in changes(path, self.poll):
                items, offset = await asyncio.to_thread(read_forward, path, kind, offset)
                if items:
                    await self.send({"type": "agent.items", "id": target, "items": items, "end": offset})
        except (OSError, ConnectionResetError, RuntimeError) as error:
            log.info("stopped following %s: %s", target, error)


def tab_label(path: str) -> str:
    """The herdr tab's name: the folder's, never starting with "-" (herdr would read it as an option)."""
    return os.path.basename(path.rstrip("/")).lstrip("-") or "home"
