"""How much of each AI plan is used (Claude, Codex…), from `ai-usagebar usage --json` (the Omarchy
plugin the person already has): the 5-hour and weekly windows, when they reset and how much of each
window has passed, trimmed to what the phone draws."""
import asyncio
import json
import re
import time
from datetime import datetime, timezone

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
        name = str(entry.get("display_name") or entry.get("id") or "IA")
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
            "id": str(entry.get("id", name)), "name": name, "plan": plan,
            "stale": bool(entry.get("stale")), "error": entry.get("error"),
            "fetchedAt": entry.get("fetched_at"), "metrics": metrics,
            "resets": int(credits.get("available") or 0) if isinstance(credits, dict) else 0,
        })
    return {"type": "usage", "available": True, "providers": providers}


class UsageReader:
    """ai-usagebar asks the providers over the network: once a minute is plenty."""

    def __init__(self, run=run_usagebar, ttl: float = 60.0, clock=time.monotonic):
        self.run, self.ttl, self.clock = run, ttl, clock
        self._cached: tuple[float, dict] | None = None

    async def read(self, force: bool = False) -> dict:
        now = self.clock()
        if not force and self._cached and now - self._cached[0] < self.ttl:
            return self._cached[1]
        code, out = await self.run(COMMAND)
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
