"""The subagents an agent launched (Claude Code keeps each in <session>/subagents/agent-<id>.jsonl,
with a .meta.json saying what for): running while its last entry is a tool call or its result,
done once it answers, stale when it went quiet too long (interrupted). Only each file's end is read."""
import json
import time
from pathlib import Path

STALE = 600.0    # seconds without writing: no longer running (the session was stopped)
TAIL = 64 * 1024
SAID = 200


def _last_entry(path: Path) -> dict | None:
    try:
        with open(path, "rb") as f:
            size = f.seek(0, 2)
            f.seek(max(0, size - TAIL))
            lines = f.read().splitlines()
    except OSError:
        return None
    for line in reversed(lines):
        try:
            d = json.loads(line)
        except (ValueError, UnicodeDecodeError):
            continue
        if isinstance(d, dict) and d.get("type") in ("user", "assistant"):
            return d
    return None


def subagents(session_file: Path, now: float | None = None) -> list[dict]:
    now = time.time() if now is None else now
    folder = Path(session_file).with_suffix("") / "subagents"
    if not folder.is_dir():
        return []
    items = []
    for log in folder.glob("agent-*.jsonl"):
        meta_path = log.with_name(log.name[: -len(".jsonl")] + ".meta.json")
        try:
            meta = json.loads(meta_path.read_text())
            started = meta_path.stat().st_mtime
            updated = log.stat().st_mtime
        except (OSError, ValueError):
            continue
        last = _last_entry(log) or {}
        content = (last.get("message") or {}).get("content")
        blocks = content if isinstance(content, list) else [{"type": "text", "text": content}] if isinstance(content, str) else []
        answered = last.get("type") == "assistant" and not any(isinstance(b, dict) and b.get("type") == "tool_use" for b in blocks)
        said = next((b.get("text", "") for b in reversed(blocks) if isinstance(b, dict) and b.get("type") == "text"), "") if answered else ""
        state = "done" if answered else "stale" if now - updated > STALE else "running"
        items.append({
            "id": log.stem.removeprefix("agent-"), "desc": str(meta.get("description") or "")[:160], "type": str(meta.get("agentType") or ""),
            "state": state, "since": int(now - started), "quiet": int(now - updated), "said": said.strip()[:SAID],
        })
    return sorted(items, key=lambda s: s["quiet"])


def running(session_file: Path, now: float | None = None) -> int:
    """How many run now; files quiet for longer than STALE are not even opened (this runs every poll)."""
    now = time.time() if now is None else now
    folder = Path(session_file).with_suffix("") / "subagents"
    if not folder.is_dir():
        return 0
    count = 0
    for log in folder.glob("agent-*.jsonl"):
        try:
            if now - log.stat().st_mtime > STALE:
                continue
        except OSError:
            continue
        last = _last_entry(log) or {}
        content = (last.get("message") or {}).get("content")
        blocks = content if isinstance(content, list) else []
        if not (last.get("type") == "assistant" and not any(isinstance(b, dict) and b.get("type") == "tool_use" for b in blocks)):
            count += 1
    return count
