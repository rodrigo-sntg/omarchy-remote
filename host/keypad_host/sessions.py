"""The agents' recent sessions, to pick one up again from the phone: Claude Code's and Codex's own
session files (every Claude config dir, ~/.claude and ~/.claude-<name>), newest first, each with
its title, project and account. Reopening runs `claude --resume <id>` (or `codex resume <id>`) in
a new herdr tab, with the options that session was last seen running with."""
import json
import os
import re
import shlex
from pathlib import Path

from .transcript import items_of

LIMIT = 30
HEAD = 256 * 1024     # where the project and the first message are
TAIL = 256 * 1024     # where the latest title is
TITLE_LIMIT = 120
_UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
_CODEX_FILE = re.compile(r"rollout-.*-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\.jsonl")

# The options worth keeping when reopening (with how many values each takes). Anything else — a
# prompt, --resume, config files — is left out: reopening never runs something the person didn't pick.
FLAGS = {
    "claude": {"--dangerously-skip-permissions": 0, "--allow-dangerously-skip-permissions": 0, "--verbose": 0,
               "--model": 1, "--permission-mode": 1, "--add-dir": 1, "--effort": 1, "--fallback-model": 1},
    "codex": {"--dangerously-bypass-approvals-and-sandbox": 0, "--full-auto": 0, "--search": 0,
              "-m": 1, "--model": 1, "-s": 1, "--sandbox": 1, "-a": 1, "--ask-for-approval": 1, "-p": 1, "--profile": 1},
}


def kept_flags(kind: str, argv: list) -> list[str]:
    rules = FLAGS.get(kind, {})
    out: list[str] = []
    args = [a for a in argv[1:] if isinstance(a, str)]
    i = 0
    while i < len(args):
        arg = args[i]
        name = arg.split("=", 1)[0]
        takes = rules.get(name)
        if takes is None:
            i += 1
        elif "=" in arg or takes == 0:
            out.append(arg)
            i += 1
        elif i + 1 < len(args):
            out += [arg, args[i + 1]]
            i += 2
        else:
            i += 1
    return out


def resume_command(kind: str, session_id: str, flags: list[str]) -> str:
    """The shell line that reopens the session (typed into the new tab's shell)."""
    words = ["claude", *flags, "--resume", session_id] if kind == "claude" else ["codex", "resume", *flags, session_id]
    return shlex.join(words)


def _read_ends(path: Path) -> tuple[list[bytes], list[bytes]]:
    with open(path, "rb") as f:
        size = os.fstat(f.fileno()).st_size
        head = f.read(HEAD)
        if size <= HEAD:
            lines = head.split(b"\n")
            return lines, lines
        f.seek(max(HEAD, size - TAIL))
        tail = f.read()
    return head.split(b"\n")[:-1], tail.split(b"\n")[1:]


def _title(text: str) -> str:
    return " ".join(text.split())[:TITLE_LIMIT]


def describe(path: Path, kind: str) -> dict | None:
    try:
        head, tail = _read_ends(path)
    except OSError:
        return None
    cwd = None
    first = None
    for line in head:
        try:
            d = json.loads(line)
        except (ValueError, UnicodeDecodeError):
            continue
        if not isinstance(d, dict):
            continue
        if kind == "claude" and str(d.get("entrypoint") or "cli").startswith("sdk"):
            return None  # run by a program (a script, a review agent), not a conversation of the person's
        if cwd is None:
            c = d.get("cwd") if kind == "claude" else (d.get("payload") or {}).get("cwd") if d.get("type") == "session_meta" else None
            if isinstance(c, str) and c.startswith("/"):
                cwd = c
        if first is None:
            said = next((i["t"] for i in items_of(line, kind) if i["k"] == "you"), None)
            if said:
                first = said
        if cwd and first:
            break
    if cwd is None or first is None:
        return None
    title = None
    if kind == "claude":
        named = generated = None
        for line in tail:
            if b"-title" not in line:
                continue
            try:
                d = json.loads(line)
            except (ValueError, UnicodeDecodeError):
                continue
            if d.get("type") == "custom-title" and isinstance(d.get("customTitle"), str):
                named = d["customTitle"]
            elif d.get("type") == "ai-title" and isinstance(d.get("aiTitle"), str):
                generated = d["aiTitle"]
        title = named or generated
    return {"cwd": cwd, "title": _title(title or first)}


def _files(home: Path) -> list[tuple[str, str, str, Path]]:
    """(kind, id, account, path) of every session file."""
    out = []
    for config in sorted(home.glob(".claude*")):
        if not config.is_dir() or not (config.name == ".claude" or config.name.startswith(".claude-")):
            continue
        account = "" if config.name == ".claude" else config.name[len(".claude-"):]
        for path in config.glob("projects/*/*.jsonl"):
            if _UUID.fullmatch(path.stem):
                out.append(("claude", path.stem, account, path))
    for path in home.glob(".codex/sessions/*/*/*/rollout-*.jsonl"):
        if (m := _CODEX_FILE.fullmatch(path.name)) is not None:
            out.append(("codex", m.group(1), "", path))
    return out


def recent(home, limit: int = LIMIT, open_ids=frozenset()) -> list[dict]:
    """The latest sessions with something said in them, newest first; [open] ones are running in herdr now."""
    dated = []
    for kind, session_id, account, path in _files(Path(home)):
        try:
            dated.append((path.stat().st_mtime, kind, session_id, account, path))
        except OSError:
            continue
    dated.sort(key=lambda d: d[0], reverse=True)
    items = []
    for mtime, kind, session_id, account, path in dated:
        if len(items) >= limit:
            break
        about = describe(path, kind)
        if about is None:
            continue
        items.append({"kind": kind, "id": session_id, "title": about["title"], "cwd": about["cwd"],
                      "project": os.path.basename(about["cwd"].rstrip("/")) or about["cwd"], "account": account,
                      "updated": int(mtime), "open": session_id in open_ids})
    return items


def account_of(path: Path) -> str:
    """The account a Claude session file belongs to: "" for ~/.claude, <name> for ~/.claude-<name>."""
    name = Path(path).parents[2].name
    return name[len(".claude-"):] if name.startswith(".claude-") else ""


def config_dir_of(path: Path, home) -> str | None:
    """CLAUDE_CONFIG_DIR for a Claude session file outside ~/.claude; None for the default one."""
    config = path.parents[2]
    return None if config == Path(home) / ".claude" else str(config)


class Remembered:
    """The options each session was last seen running with (so reopening it matches), kept on disk."""

    MAX = 300

    def __init__(self, path: Path):
        self.path = Path(path)
        try:
            data = json.loads(self.path.read_text())
            self.data = data if isinstance(data, dict) else {}
        except (OSError, ValueError):
            self.data = {}

    def flags(self, session_id: str) -> list[str]:
        value = self.data.get(session_id) if _UUID.fullmatch(str(session_id)) else None
        return [f for f in value if isinstance(f, str)] if isinstance(value, list) else []

    def note(self, session_id: str, flags: list[str]) -> None:
        if not _UUID.fullmatch(session_id) or self.data.get(session_id) == flags:
            return
        self.data.pop(session_id, None)
        self.data[session_id] = flags
        while len(self.data) > self.MAX:
            self.data.pop(next(iter(self.data)))
        try:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            tmp = self.path.with_suffix(".tmp")
            tmp.write_text(json.dumps(self.data))
            os.replace(tmp, self.path)
        except OSError:
            pass
