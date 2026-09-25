"""herdr's CLI as an async adapter (docs/PLANO-V2.md §4.1). Only the CLI, never its socket: every
command prints JSON ({"id", "result"} or {"error": {"code", "message"}}) with exit code 0, except
`agent read --format text`, which prints the terminal text itself."""
import asyncio
import json
import os
import re
import shutil
from pathlib import Path


class HerdrError(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


class Herdr:
    def __init__(self, binary: str = "herdr", timeout: float = 5.0, home=None):
        self.binary = binary
        self.timeout = timeout
        self.home = home

    async def _output(self, *args, timeout: float | None = None) -> str:
        if shutil.which(self.binary) is None:
            raise HerdrError("not_installed", "O herdr não está instalado no computador.")
        process = await asyncio.create_subprocess_exec(
            self.binary, *args, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE,
        )
        try:
            out, err = await asyncio.wait_for(process.communicate(), timeout or self.timeout)
        except asyncio.TimeoutError:
            process.kill()
            await process.wait()
            raise HerdrError("timeout", "O herdr não respondeu.")
        if process.returncode != 0 and not out.strip():
            detail = err.decode(errors="replace").strip()
            try:  # herdr says what went wrong as JSON on stderr: keep its code
                self._raise_if_error(json.loads(detail))
            except ValueError:
                pass
            raise HerdrError("failed", detail[:200] or "O herdr falhou.")
        return out.decode(errors="replace")

    @staticmethod
    def _raise_if_error(data):
        if isinstance(data, dict) and "error" in data:
            error = data["error"] or {}
            raise HerdrError(error.get("code", "error"), error.get("message", "Erro do herdr."))

    async def _run(self, *args, timeout: float | None = None) -> dict:
        text = await self._output(*args, timeout=timeout)
        try:
            data = json.loads(text)
        except ValueError:
            raise HerdrError("bad_output", "Resposta inválida do herdr.")
        self._raise_if_error(data)
        # `status server --json` answers at the top level; everything else under "result".
        return data.get("result", data) if isinstance(data, dict) else {}

    async def available(self) -> bool:
        try:
            status = await self._run("status", "server", "--json")
        except HerdrError:
            return False
        return bool(status.get("running"))

    async def agents(self) -> list[dict]:
        return list((await self._run("agent", "list")).get("agents", []))

    async def read(self, target: str, lines: int = 40, ansi: bool = False) -> str:
        # Only what is on the agent's screen: herdr captures more history by scrolling the pane,
        # and that scroll shows on the PC (and can stall herdr) on every read. [ansi]: with its colors
        # (a picker's selection, a panel's current tab are only color).
        text = await self._output("agent", "read", target, "--source", "visible", "--format", "ansi" if ansi else "text")
        if text.startswith('{"error"'):
            try:
                data = json.loads(text)
            except ValueError:
                return text
            self._raise_if_error(data)
        return text

    async def agent_info(self, target: str) -> dict:
        """What herdr knows of the pane's agent (kind, cwd, session…); {} if it cannot say."""
        try:
            info = (await self._run("agent", "get", target)).get("agent")
        except HerdrError:
            return {}
        return info if isinstance(info, dict) else {}

    async def cwd_of(self, target: str) -> str | None:
        """The pane's working directory (the agent's project), as herdr reports it."""
        info = await self.agent_info(target)
        cwd = info.get("cwd")
        return cwd if isinstance(cwd, str) and cwd else None

    async def session(self, target: str) -> tuple[str, str] | None:
        """(agent, session id) of the CLI running in the pane, as herdr detected it; None if unknown."""
        info = (await self._run("agent", "get", target)).get("agent") or {}
        session = info.get("agent_session") or {}
        if session.get("kind") == "id" and isinstance(session.get("value"), str):
            return str(session.get("agent") or info.get("agent") or ""), session["value"]
        # herdr didn't identify it (started before its hooks, another config dir): the command line
        # it was resumed with says which session it is.
        pane = info.get("pane_id")
        if not isinstance(pane, str):
            return None
        for process in await self.processes(pane):
            found = await asyncio.to_thread(running_session, process, self.home or os.path.expanduser("~"))
            if found is not None:
                return found
        return None

    async def panes(self) -> list[dict]:
        return list((await self._run("pane", "list")).get("panes", []))

    async def processes(self, pane: str) -> list[dict]:
        """What runs in the pane's foreground (argv, pid)."""
        info = (await self._run("pane", "process-info", "--pane", pane)).get("process_info") or {}
        return [p for p in info.get("foreground_processes") or [] if isinstance(p, dict)]

    @staticmethod
    def _env(env: dict | None) -> list[str]:
        return [a for k, v in (env or {}).items() for a in ("--env", f"{k}={v}")]

    async def create_tab(self, workspace: str, cwd: str, label: str, env: dict | None = None) -> str:
        """A new tab in [workspace] at [cwd], not focused; its first pane's id."""
        result = await self._run("tab", "create", "--workspace", workspace, "--cwd", cwd, "--label", label, *self._env(env), "--no-focus")
        return result["root_pane"]["pane_id"]

    async def create_workspace(self, cwd: str, label: str, env: dict | None = None) -> str:
        result = await self._run("workspace", "create", "--cwd", cwd, "--label", label, *self._env(env), "--no-focus")
        return result["root_pane"]["pane_id"]

    async def run(self, pane: str, command: str) -> None:
        """Types [command] into the pane's shell and runs it."""
        await self._run("pane", "run", pane, command)

    async def start_agent(self, name: str, kind: str, pane: str) -> None:
        """Starts the agent in the pane and returns once it is ready for input (herdr waits up to 60 s)."""
        await self._run("agent", "start", name, "--kind", kind, "--pane", pane, "--timeout", "60000", timeout=70.0)

    async def send_keys(self, target: str, keys: list[str]) -> dict:
        return await self._run("agent", "send-keys", target, *keys)

    async def prompt(self, target: str, text: str) -> dict:
        # herdr's parser has no "--" separator ("unknown option") and reads a dash-led word as an
        # option: a leading space keeps such a prompt text for the agent, never a herdr flag.
        if text.startswith("-"):
            text = " " + text
        return await self._run("agent", "prompt", target, text)

    async def focus(self, target: str) -> dict:
        return await self._run("agent", "focus", target)


_UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")


def resumed_session(argv: list) -> tuple[str, str] | None:
    """(agent, session id) from `claude --resume <id>` / `--session-id <id>` or `codex resume <id>`."""
    if not argv or not all(isinstance(a, str) for a in argv):
        return None
    kind = os.path.basename(argv[0])
    if kind == "claude":
        flags = ("--resume", "-r", "--session-id")
        ids = [argv[i + 1] for i, a in enumerate(argv[:-1]) if a in flags]
    elif kind == "codex":
        ids = [argv[i + 1] for i, a in enumerate(argv[:-1]) if a == "resume"]
    else:
        return None
    ids = [i for i in ids if _UUID.fullmatch(i)]
    return (kind, ids[-1]) if ids else None


def running_session(process: dict, home) -> tuple[str, str] | None:
    """The session a foreground process is in: Claude Code's own record of it
    (<config dir>/sessions/<pid>.json, current after a /clear too), else its --resume argument."""
    argv = process.get("argv") or []
    pid = process.get("pid")
    if argv and os.path.basename(str(argv[0])) == "claude" and type(pid) is int and pid > 0:
        for record in sorted(Path(home).glob(f".claude*/sessions/{pid}.json")):
            try:
                session_id = json.loads(record.read_text()).get("sessionId")
            except (OSError, ValueError, AttributeError):
                continue
            if isinstance(session_id, str) and _UUID.fullmatch(session_id):
                return "claude", session_id
    return resumed_session(argv)
