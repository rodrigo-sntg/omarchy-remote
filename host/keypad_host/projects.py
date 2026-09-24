"""Where an agent can be started from the phone: the projects open in herdr (their workspace
known), then the ones Claude Code has worked in (~/.claude.json), most recent first."""
import json
import os
import re
from pathlib import Path

LIMIT = 40


def _recent(home: Path, path: str) -> float:
    folder = home / ".claude" / "projects" / re.sub(r"[^A-Za-z0-9]", "-", path)
    try:
        return max((f.stat().st_mtime for f in folder.glob("*.jsonl")), default=0.0)
    except OSError:
        return 0.0


def projects(panes: list[dict], home: Path) -> list[dict]:
    home = Path(home)
    skip = {str(home), str(home) + "/"}
    out, seen = [], set()
    for pane in panes:
        cwd = pane.get("cwd")
        if isinstance(cwd, str) and cwd not in seen and cwd not in skip and os.path.isdir(cwd):
            seen.add(cwd)
            out.append({"path": cwd, "name": os.path.basename(cwd.rstrip("/")), "workspace": pane.get("workspace_id")})
    try:
        known = json.loads((home / ".claude.json").read_text()).get("projects") or {}
    except (OSError, ValueError, AttributeError):
        known = {}
    others = [p for p in known if isinstance(p, str) and p not in seen and p not in skip and os.path.isdir(p)]
    others.sort(key=lambda p: _recent(home, p), reverse=True)
    out += [{"path": p, "name": os.path.basename(p.rstrip("/")), "workspace": None} for p in others]
    return out[:LIMIT]
