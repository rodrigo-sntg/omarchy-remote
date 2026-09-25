"""Agents talking to each other: a message passed on from the phone ("Mandar para…"), a review asked
of another agent of the same project, and a question one agent asks another (omarchy-remote ask).

Every prompt that crosses starts with one header line naming where it came from, so the receiving
agent knows and the phone can show it as such:

    ↪ Claude · omarchy-remote · w9:p2            a message passed on
    ↪ Claude · omarchy-remote · w9:p2 · review   a review asked for
    ↪ Claude · omarchy-remote · w9:p2 · ask      a question waiting for its answer

Links follows each exchange by itself (the phone may be away): whether the other agent is working,
finished or gone. A finished review becomes a notice for the agent that asked; a finished question
wakes whoever waits for it. The phone gets a snapshot of both whenever something changes. An agent
that is answering a question can't ask one back: agents don't ping-pong on their own.
"""
import asyncio
import os
import re
import secrets
import time
from dataclasses import dataclass, field

from .herdr import HerdrError
from .protocol import AGENT_TARGET

KINDS = ("claude", "codex")
ACTIVE = ("working", "blocked")
SHOW_ENDED = 120            # seconds a finished exchange stays on the phone
NOTICE_TTL = 24 * 3600
MAX_NOTICES = 20
LINK_TIMEOUT = 30 * 60     # an exchange not over by then has failed (a stuck or forgotten agent)
ASK_TIMEOUT = 600
_HEADER = re.compile(r"^↪ (\S+) · (.+?) · (w\S*:p\d+)(?: · (review|ask))?$")
_NAMES = {"claude": "Claude", "codex": "Codex"}

REVIEW = {
    "pt": ("Revise as mudanças ainda não commitadas deste repositório (git status, git diff e arquivos novos). "
           "Aponte bugs, riscos e o que falta, do mais grave ao menos grave, de forma curta e objetiva. "
           "Não altere nenhum arquivo: só a revisão."),
    "en": ("Review this repository's uncommitted changes (git status, git diff and new files). Point out bugs, "
           "risks and what is missing, most serious first, short and to the point. Do not change any file: "
           "the review only."),
}
ASK_TAIL = "(Answer directly and briefly: your reply goes back to the agent that asked.)"


class LinkError(Exception):
    """Why an exchange can't happen, in words for the person."""


def header(kind: str, cwd: str | None, pane: str, what: str | None = None) -> str:
    name = _NAMES.get(kind, kind.capitalize() if kind else "Terminal")
    project = os.path.basename((cwd or "").rstrip("/")) or "~"
    return f"↪ {name} · {project} · {pane}" + (f" · {what}" if what else "")


def parse_header(text: str) -> dict | None:
    m = _HEADER.match(text.split("\n", 1)[0])
    if m is None:
        return None
    return {"kind": m.group(1).lower(), "project": m.group(2), "pane": m.group(3), "what": m.group(4) or "relay"}


def quoted(text: str) -> str:
    return "\n".join("> " + line if line else ">" for line in text.strip().split("\n"))


@dataclass
class Link:
    id: str
    kind: str                  # relay, review or ask
    source: str | None         # the agent it came from (None: a plain terminal)
    target: str
    created: float
    state: str = "working"     # working, blocked, done, failed
    seen_working: bool = False
    seq: int | None = None     # herdr's state counter of the target before the prompt
    ended: float | None = None
    error: str | None = None
    done: asyncio.Future | None = field(default=None, repr=False)


class Links:
    def __init__(self, herdr, commands, push, clock=time.time, poll: float = 2.0):
        self.herdr, self.commands, self.push = herdr, commands, push
        self.clock, self.poll = clock, poll
        self.links: dict[str, Link] = {}
        self.notices: list[dict] = []
        self._task: asyncio.Task | None = None
        self._pushed = None

    # ---------------------------------------------------------------- what the phone sees

    def snapshot(self) -> dict:
        now = self.clock()
        links = [
            {"id": l.id, "kind": l.kind, "from": l.source, "to": l.target, "state": l.state, "since": int(l.created)}
            for l in self.links.values() if l.state in ACTIVE or (l.ended is not None and now - l.ended < SHOW_ENDED)
        ]
        self.notices = [n for n in self.notices if now - n["ts"] < NOTICE_TTL][-MAX_NOTICES:]
        return {"type": "agent.links", "links": links, "notices": list(self.notices)}

    async def publish(self, force: bool = False):
        snap = self.snapshot()
        if force or snap != self._pushed:
            self._pushed = snap
            await self.push(snap)

    def dismiss(self, notice_id: str):
        self.notices = [n for n in self.notices if n["id"] != notice_id]

    # ---------------------------------------------------------------- the three exchanges

    async def relay(self, source: str, target: str, text: str, note: str | None = None) -> str:
        """[text] (something [source] said) passed on to [target], with the person's [note] above it."""
        if source == target:
            raise LinkError("Escolha outro agente.")
        info = await self._info(source)
        await self._info(target)
        body = (note.strip() + "\n\n" if note and note.strip() else "") + quoted(text)
        seq = await self._seq(target)
        await self._prompt(target, header(info["agent"], info["cwd"], source) + "\n\n" + body)
        return self._add("relay", source, target, seq).id

    async def review(self, source: str, kind: str, lang: str = "pt") -> str:
        """Another agent of [source]'s project reviews its uncommitted changes; the reviewer's pane."""
        if kind not in KINDS:
            raise LinkError("Agente de revisão inválido.")
        info = await self._info(source)
        prompt = header(info["agent"], info["cwd"], source, "review") + "\n\n" + REVIEW.get(lang, REVIEW["en"])
        reviewer = await self._free(kind, info["cwd"], exclude=source)
        seq = None
        if reviewer is None:
            reviewer = await self._start(info["cwd"], kind, prompt)
        else:
            seq = await self._seq(reviewer)
            await self._prompt(reviewer, prompt)
        self._add("review", source, reviewer, seq)
        return reviewer

    async def ask(self, origin: str | None, target: str, question: str, timeout: float = ASK_TIMEOUT, cwd: str | None = None) -> dict:
        """[question] to [target] (a pane, or "claude"/"codex" of the same project), waiting for its
        answer: {"ok", "agent", "text"} or {"ok": False, "error"}."""
        if origin is not None and any(l.kind == "ask" and l.target == origin and l.state in ACTIVE for l in self.links.values()):
            raise LinkError("Quem está respondendo a uma pergunta não pode perguntar a outro agente.")
        info = await self._info(origin) if origin else {"agent": "", "cwd": cwd}
        prompt = header(info["agent"], info["cwd"], origin or "w0:p0", "ask") + "\n\n" + question.strip() + "\n\n" + ASK_TAIL
        if target in KINDS:
            if not info["cwd"]:
                raise LinkError("Sem projeto: rode de dentro da pasta do projeto.")
            pane = await self._free(target, info["cwd"], exclude=origin)
            seq = None
            if pane is None:
                pane = await self._start(info["cwd"], target, prompt)
            else:
                seq = await self._seq(pane)
                await self._prompt(pane, prompt)
        else:
            if target == origin:
                raise LinkError("Escolha outro agente.")
            await self._info(target)
            busy = next((a.get("agent_status") for a in await self.herdr.agents() if a.get("pane_id") == target), None)
            if busy in ACTIVE:
                # Its current task finishing would pass for the answer.
                raise LinkError("Esse agente está ocupado agora; pergunte quando ele terminar.")
            pane = target
            seq = await self._seq(pane)
            await self._prompt(pane, prompt)
        link = self._add("ask", origin, pane, seq)
        link.done = asyncio.get_running_loop().create_future()
        try:
            return await asyncio.wait_for(asyncio.shield(link.done), timeout)
        except asyncio.TimeoutError:
            self._end(link, "failed", "O agente não respondeu a tempo.")
            return {"ok": False, "error": "O agente não respondeu a tempo."}

    # ---------------------------------------------------------------- following them

    async def tick(self):
        """One look at herdr: each exchange moves on (working, blocked, finished, gone)."""
        active = [l for l in self.links.values() if l.state in ACTIVE]
        if active:
            try:
                listed = {a["pane_id"]: a for a in await self.herdr.agents()}
            except HerdrError:
                return
            for link in active:
                s = (listed.get(link.target) or {}).get("agent_status")
                moved = link.seq is not None and (listed.get(link.target) or {}).get("state_change_seq") != link.seq
                if s is None:
                    self._end(link, "failed", "O agente fechou antes de responder.")
                elif self.clock() - link.created > LINK_TIMEOUT:
                    self._end(link, "failed", "O agente não terminou a tempo.")
                elif s == "working":
                    link.seen_working, link.state = True, "working"
                elif s == "blocked":
                    link.state = "blocked"
                # Stopped after this prompt: seen working, or herdr's counter moved since it was sent. A new
                # agent (no counter) only counts once seen working: starting up can take long.
                elif link.seen_working or moved:
                    await self._finish(link)
        await self.publish()

    async def _finish(self, link: Link):
        text = await self.commands.last_said(link.target, link.created - 1)
        if link.kind == "review":
            info = await self._info_or_none(link.target)
            self.notices.append({
                "id": secrets.token_hex(6), "kind": "review", "origin": link.source, "from": link.target,
                "fromKind": (info or {}).get("agent", ""), "text": text or "", "ts": int(self.clock()),
            })
        self._end(link, "done", None if text else "O agente terminou sem responder.", text)

    def _end(self, link: Link, state: str, error: str | None, text: str | None = None):
        link.state, link.ended, link.error = state, self.clock(), error
        if link.done is not None and not link.done.done():
            if state == "done" and error is None:
                link.done.set_result({"ok": True, "agent": link.target, "text": text})
            else:
                link.done.set_result({"ok": False, "error": error or "Falhou."})

    async def _run(self):
        while any(l.state in ACTIVE for l in self.links.values()):
            await asyncio.sleep(self.poll)
            try:
                await self.tick()
            except Exception:  # noqa: BLE001 — a look that fails must not end the watch
                pass

    # ---------------------------------------------------------------- helpers

    def _add(self, kind: str, source: str | None, target: str, seq: int | None = None) -> Link:
        link = Link(secrets.token_hex(6), kind, source, target, self.clock(), seq=seq)
        self.links[link.id] = link
        # Only the last ones matter.
        for old in sorted(self.links.values(), key=lambda l: l.created)[:-50]:
            if old.state not in ACTIVE:
                self.links.pop(old.id, None)
        if self._task is None or self._task.done():
            self._task = asyncio.get_running_loop().create_task(self._run())
        asyncio.get_running_loop().create_task(self.publish())
        return link

    async def _info(self, pane: str) -> dict:
        if not isinstance(pane, str) or not AGENT_TARGET.fullmatch(pane):
            raise LinkError("Agente inválido.")
        info = await self._info_or_none(pane)
        if info is None:
            raise LinkError("Esse agente não está mais no herdr.")
        return info

    async def _info_or_none(self, pane: str) -> dict | None:
        try:
            info = await self.herdr.agent_info(pane)
        except HerdrError:
            return None
        return {"agent": str(info.get("agent") or ""), "cwd": info.get("cwd") or None}

    async def _seq(self, pane: str) -> int | None:
        try:
            return next((a.get("state_change_seq") for a in await self.herdr.agents() if a.get("pane_id") == pane), None)
        except HerdrError:
            return None

    async def _free(self, kind: str, cwd: str | None, exclude: str | None) -> str | None:
        """An agent of [kind] in the same project that isn't busy."""
        for a in await self.herdr.agents():
            if a.get("agent") == kind and a.get("cwd") == cwd and a.get("pane_id") != exclude and a.get("agent_status") in ("idle", "done"):
                return a["pane_id"]
        return None

    async def _prompt(self, pane: str, text: str):
        try:
            await self.herdr.prompt(pane, text)
        except HerdrError as error:
            raise LinkError(f"O agente não recebeu: {error}") from error

    async def _start(self, cwd: str, kind: str, prompt: str) -> str:
        try:
            return await self.commands.start(cwd, kind, prompt)
        except HerdrError as error:
            raise LinkError(f"Não consegui abrir o agente: {error}") from error
