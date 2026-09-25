"""How much of each AI plan is used (Claude, Codex…), from `ai-usagebar usage --json` (the Omarchy
plugin the person already has): the 5-hour and weekly windows, when they reset and how much of each
window has passed, trimmed to what the phone draws."""
import asyncio
import json
import os
import re
import time
import tomllib
from datetime import datetime, timezone
from pathlib import Path

COMMAND = ["ai-usagebar", "usage", "--json"]
SESSION_SECS = 5 * 3600
WEEK_SECS = 7 * 24 * 3600
_WINDOW_SUFFIX = re.compile(r"\s*\((?:\d+[hd]|weekly|session)\)\s*$", re.I)


async def run_usagebar(args: list[str]) -> tuple[int, str]:
    """(exit code, stdout): 127 when not installed, 124 when the providers took too long."""
    try:
        process = await asyncio.create_subprocess_exec(*args, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
    except OSError:
        return 127, ""
    try:
        out, _ = await asyncio.wait_for(process.communicate(), 25)
    except asyncio.TimeoutError:
        process.kill()
        await process.wait()
        return 124, ""
    return process.returncode, out.decode(errors="replace")


def _when(text: str | None) -> datetime | None:
    try:
        return datetime.fromisoformat(text.replace("Z", "+00:00")) if text else None
    except ValueError:
        return None


def summarize_usage(doc: dict, now: datetime) -> dict:
    providers = []
    for entry in doc.get("entries", []):
        if not isinstance(entry, dict):
            continue
        # A second Claude account comes as "anthropic@<label>", "Claude · <label>": its own card, named by account.
        entry_id = str(entry.get("id") or "")
        account = entry_id.split("@", 1)[1] if "@" in entry_id else ""
        name = str(entry.get("display_name") or entry_id or "IA")
        if account and name.endswith(" · " + account):
            name = name[: -len(" · " + account)]
        plan = entry.get("plan") or ""
        if plan.lower().startswith(name.lower() + " "):
            plan = plan[len(name) + 1:]
        metrics, seen = [], set()
        for m in entry.get("metrics") or []:
            window = m.get("window_secs")
            reset = _when(m.get("reset_at"))
            kind = "session" if window == SESSION_SECS else "week" if window == WEEK_SECS else "model"
            if kind in seen:
                kind = "model"  # a second weekly window is a model's own (e.g. "Fable")
            seen.add(kind)
            elapsed = None
            if reset and isinstance(window, int) and window > 0:
                elapsed = round(100 * (1 - (reset - now).total_seconds() / window))
                elapsed = max(0, min(100, elapsed))
            metrics.append({
                "kind": kind,
                "label": _WINDOW_SUFFIX.sub("", str(m.get("label", ""))),
                "percent": max(0, min(100, int(m.get("percent") or 0))),
                "resetAt": m.get("reset_at"),
                "elapsed": elapsed,
            })
        credits = entry.get("reset_credits") or {}
        providers.append({
            "id": entry_id or name, "name": name, "account": account, "plan": plan,
            "stale": bool(entry.get("stale")), "error": entry.get("error"),
            "fetchedAt": entry.get("fetched_at"), "metrics": metrics,
            "resets": int(credits.get("available") or 0) if isinstance(credits, dict) else 0,
        })
    return {"type": "usage", "available": True, "providers": providers}


def own_config_path() -> Path:
    base = os.environ.get("XDG_CONFIG_HOME") or os.path.expanduser("~/.config")
    return Path(base) / "ai-usagebar" / "config.toml"


def usage_config(home, own_config: Path) -> str | None:
    """ai-usagebar's config with every signed-in Claude account (~/.claude-<name>, as Claude Code keeps
    a second login with CLAUDE_CONFIG_DIR) added, so its limits show too; None when there is none to
    add. The person's own config comes first and whole; an account they listed already is not repeated."""
    home = Path(home)
    try:
        text = own_config.read_text()
        listed = tomllib.loads(text).get("anthropic", {}).get("accounts", [])
    except (OSError, tomllib.TOMLDecodeError, AttributeError):
        text, listed = "", []
    known = {os.path.realpath(os.path.expanduser(str(a.get("credentials_path", "")))) for a in listed if isinstance(a, dict)}
    blocks = []
    for folder in sorted(home.glob(".claude-*")):
        credentials = folder / ".credentials.json"
        if not credentials.is_file() or os.path.realpath(credentials) in known:
            continue
        label = re.sub(r"[^a-z0-9_-]", "-", folder.name[len(".claude-"):].lower()) or "conta"
        blocks.append(f'\n[[anthropic.accounts]]\nlabel = {json.dumps(label)}\ncredentials_path = {json.dumps(str(credentials))}\n')
    if not blocks:
        return None
    return text + ("\n" if text and not text.endswith("\n") else "") + "".join(blocks)


class UsageReader:
    """ai-usagebar asks the providers over the network: once a minute is plenty."""

    def __init__(self, run=run_usagebar, ttl: float = 60.0, clock=time.monotonic, home=None, own_config=None, runtime=None):
        self.run, self.ttl, self.clock = run, ttl, clock
        self.home = Path(home) if home else Path.home()
        self.own_config = Path(own_config) if own_config else own_config_path()
        self.runtime = Path(runtime) if runtime else Path(os.environ.get("XDG_RUNTIME_DIR") or "/tmp") / "omarchy-remote"
        self._cached: tuple[float, dict] | None = None

    def command(self) -> list[str]:
        """ai-usagebar as always, or with a private copy of its config that adds the other Claude accounts."""
        try:
            text = usage_config(self.home, self.own_config)
            if text is None:
                return COMMAND
            self.runtime.mkdir(parents=True, exist_ok=True, mode=0o700)
            path = self.runtime / "usage.toml"
            tmp = path.with_suffix(".tmp")
            fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
            with os.fdopen(fd, "w") as f:
                f.write(text)
            os.replace(tmp, path)
        except OSError:
            return COMMAND
        return ["ai-usagebar", "--config", str(path), "usage", "--json"]

    async def read(self, force: bool = False) -> dict:
        now = self.clock()
        if not force and self._cached and now - self._cached[0] < self.ttl:
            return self._cached[1]
        code, out = await self.run(await asyncio.to_thread(self.command))
        if code == 127:
            return {"type": "usage", "available": False, "providers": []}
        if code == 124:
            return {"type": "usage", "available": True, "providers": [], "error": "Os provedores demoraram demais para responder."}
        try:
            result = summarize_usage(json.loads(out), datetime.now(timezone.utc))
        except (ValueError, AttributeError, TypeError):
            return {"type": "usage", "available": True, "providers": [], "error": "Não consegui ler o ai-usagebar."}
        self._cached = (now, result)
        return result
