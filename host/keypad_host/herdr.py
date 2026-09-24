"""herdr's CLI as an async adapter (docs/PLANO-V2.md §4.1). Only the CLI, never its socket: every
command prints JSON ({"id", "result"} or {"error": {"code", "message"}}) with exit code 0, except
`agent read --format text`, which prints the terminal text itself."""
import asyncio
import json
import shutil


class HerdrError(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


class Herdr:
    def __init__(self, binary: str = "herdr", timeout: float = 5.0):
        self.binary = binary
        self.timeout = timeout

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
        if session.get("kind") != "id" or not isinstance(session.get("value"), str):
            return None
        return str(session.get("agent") or info.get("agent") or ""), session["value"]

    async def panes(self) -> list[dict]:
        return list((await self._run("pane", "list")).get("panes", []))

    async def create_tab(self, workspace: str, cwd: str, label: str) -> str:
        """A new tab in [workspace] at [cwd], not focused; its first pane's id."""
        result = await self._run("tab", "create", "--workspace", workspace, "--cwd", cwd, "--label", label, "--no-focus")
        return result["root_pane"]["pane_id"]

    async def create_workspace(self, cwd: str, label: str) -> str:
        result = await self._run("workspace", "create", "--cwd", cwd, "--label", label, "--no-focus")
        return result["root_pane"]["pane_id"]

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
