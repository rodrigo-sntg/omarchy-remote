"""What an agent's "/" menu offers, for the phone's composer: the CLI's own commands (as its menu
showed them, in agent_commands.json, with a Portuguese line and whether they open a screen in the
terminal) and what is installed on this PC, read from disk on each ask — Claude Code's skills,
commands and enabled plugins (user and project), Codex's skills (mentioned with "$")."""
import json
import re
from pathlib import Path

DESCRIPTION_LIMIT = 220
_CATALOG = Path(__file__).with_name("agent_commands.json")
_KEY = re.compile(r"^([A-Za-z][\w-]*):\s*(.*)$")


def frontmatter(text: str) -> dict:
    """The simple YAML at the top of a skill or command: key: value, quoted or folded (> / |)."""
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        return {}
    out, key, folded = {}, None, []
    for line in lines[1:]:
        if line.strip() == "---":
            break
        m = _KEY.match(line)
        if m and not line.startswith((" ", "\t")):
            if key and folded:
                out[key] = " ".join(folded)
            key, value, folded = m.group(1), m.group(2).strip(), []
            if value in (">", "|", ">-", "|-"):
                continue
            out[key] = value.strip("\"'")
            key = key if value == "" else None
        elif key is not None and line.strip():
            folded.append(line.strip())
    if key and folded:
        out[key] = " ".join(folded)
    return out


def _description(meta: dict, body: str) -> str:
    text = meta.get("description") or next((l.strip() for l in body.splitlines() if l.strip() and not l.startswith(("---", "#"))), "")
    return text[:DESCRIPTION_LIMIT]


def _read(path: Path) -> str:
    try:
        return path.read_text(errors="replace")[:20_000]
    except OSError:
        return ""


def _skills(folder: Path, prefix: str, group: str) -> list[dict]:
    items = []
    for f in sorted(folder.glob("*/SKILL.md")):
        text = _read(f)
        meta = frontmatter(text)
        if str(meta.get("user-invocable", "true")).lower() == "false":
            continue
        name = meta.get("name") or f.parent.name
        items.append({"n": f"{prefix}{name}", "d": _description(meta, ""), "g": group})
    return items


def _commands(folder: Path, prefix: str, group: str) -> list[dict]:
    items = []
    for f in sorted(folder.glob("*.md")):
        text = _read(f)
        meta = frontmatter(text)
        body = text.split("---", 2)[2] if meta else text
        items.append({"n": f"/{prefix}{f.stem}", "d": _description(meta, body), "g": group})
    return items


def _json(path: Path) -> dict:
    try:
        data = json.loads(path.read_text())
    except (OSError, ValueError):
        return {}
    return data if isinstance(data, dict) else {}


def installed_claude(cwd: str | None, home: Path) -> list[dict]:
    claude = home / ".claude"
    items = _skills(claude / "skills", "/", "skill") + _commands(claude / "commands", "", "skill")
    if cwd:
        project = Path(cwd) / ".claude"
        items += _skills(project / "skills", "/", "project") + _commands(project / "commands", "", "project")
    enabled = _json(claude / "settings.json").get("enabledPlugins") or {}
    installed = _json(claude / "plugins" / "installed_plugins.json")
    for key, installs in (installed.get("plugins", installed) or {}).items():
        if not isinstance(installs, list) or not installs or enabled.get(key) is False:
            continue
        name = key.split("@", 1)[0]
        path = Path(installs[-1].get("installPath", ""))
        if not path.is_dir():
            continue
        items += _skills(path / "skills", f"/{name}:", name) + _commands(path / "commands", f"{name}:", name)
    return items


def installed_codex(cwd: str | None, home: Path) -> list[dict]:
    folders = [home / ".codex" / "skills", home / ".codex" / "skills" / ".system"]
    if cwd:
        folders.append(Path(cwd) / ".codex" / "skills")
    items = []
    for folder in folders:
        items += _skills(folder, "$", "skill")
    return items


def _catalog() -> dict:
    try:
        return json.loads(_CATALOG.read_text())
    except (OSError, ValueError):
        return {}


def commands_for(kind: str, cwd: str | None, home: Path | None = None) -> list[dict]:
    """The menu for this kind of agent: its own commands first, then what is installed; each name once."""
    home = home or Path.home()
    if kind == "claude":
        found = installed_claude(cwd, home)
    elif kind == "codex":
        found = installed_codex(cwd, home)
    else:
        return []
    items, seen = [], set()
    for item in _catalog().get(kind, []) + found:
        if item["n"] not in seen:
            seen.add(item["n"])
            items.append(item)
    return items
