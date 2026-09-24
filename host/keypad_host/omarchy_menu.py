"""Omarchy's Super+Space menu and app launcher for the phone (docs/PLANO-V2.md §7). The tree comes
from Omarchy's own menu definition plus the user's extension; `when`/`checked` are evaluated here;
the phone only ever sees ids and labels, and running an item runs that item's own action."""
import asyncio
import time
import configparser
import os
import re
from pathlib import Path

from . import jsonc

OMARCHY = os.environ.get("OMARCHY_PATH") or "/usr/share/omarchy"
DEFAULT_MENU = f"{OMARCHY}/default/omarchy/omarchy-menu.jsonc"
USER_MENU = str(Path.home() / ".config" / "omarchy" / "extensions" / "omarchy-menu.jsonc")
ITEM_ID = re.compile(r"[a-z0-9][a-z0-9._-]{0,63}")
APP_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._+-]{0,127}")


async def bash_guard(condition: str) -> bool:
    """A menu `when`/`checked` condition, as the Omarchy shell runs it (bash, exit status)."""
    try:
        process = await asyncio.create_subprocess_exec(
            "bash", "-c", condition, stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL,
        )
        return await asyncio.wait_for(process.wait(), 3) == 0
    except (OSError, asyncio.TimeoutError):
        return False


def _read(path: str) -> dict:
    try:
        data = jsonc.parse(Path(path).read_text())
    except (OSError, ValueError):
        return {}
    return data if isinstance(data, dict) else {}


class MenuSource:
    def __init__(self, default=DEFAULT_MENU, user=USER_MENU, guard=bash_guard, ttl=10.0):
        self.default, self.user, self.guard, self.ttl = default, user, guard, ttl
        self._cache: dict[str, tuple[float, bool]] = {}  # condition -> (when, result)

    async def _check(self, condition: str) -> bool:
        """A condition is a shell command: its answer is reused for a few seconds (opening a
        submenu and running an item should not run every check again)."""
        now = time.monotonic()
        hit = self._cache.get(condition)
        if hit is not None and now - hit[0] < self.ttl:
            return hit[1]
        result = await self.guard(condition)
        self._cache[condition] = (now, result)
        return result

    def _entries(self) -> dict:
        entries = {k: dict(v) for k, v in _read(self.default).items() if isinstance(v, dict)}
        for key, value in _read(self.user).items():
            if isinstance(value, dict):
                entries[key] = {**entries.get(key, {}), **value}  # "Existing fields are kept unless overridden."
        return {k: v for k, v in entries.items() if ITEM_ID.fullmatch(k)}

    async def _visible(self, entries: dict) -> tuple[dict, set]:
        """Entries whose `when` holds (and whose parents are visible), and the ids that are checked."""
        whens = {k: v["when"] for k, v in entries.items() if isinstance(v.get("when"), str)}
        checks = {k: v["checked"] for k, v in entries.items() if isinstance(v.get("checked"), str)}
        results = await asyncio.gather(*(self._check(c) for c in [*whens.values(), *checks.values()]))
        hidden = {k for k, ok in zip(whens, results[:len(whens)]) if not ok}
        checked = {k for k, ok in zip(checks, results[len(whens):]) if ok}
        visible = {k: v for k, v in entries.items() if not any(k == h or k.startswith(h + ".") for h in hidden)}
        return visible, checked

    async def items(self) -> list[dict]:
        visible, checked = await self._visible(self._entries())
        ids = set(visible)
        for key in list(visible):  # "personal.notes" implies a "personal" submenu
            parts = key.split(".")
            for i in range(1, len(parts)):
                ids.add(".".join(parts[:i]))
        items = []
        for key in sorted(ids, key=lambda k: list(visible).index(k) if k in visible else -1):
            entry = visible.get(key, {})
            kind = "apps" if entry.get("provider") == "apps" else "action" if entry.get("action") else "link" if entry.get("target") else "submenu"
            item = {"id": key, "parent": key.rpartition(".")[0], "icon": str(entry.get("icon", "")),
                    "label": str(entry.get("label", key.rpartition(".")[2].title())), "kind": kind, "checked": key in checked}
            if entry.get("description"):
                item["description"] = str(entry["description"])
            if kind == "link":
                item["target"] = str(entry["target"])
            items.append(item)
        return items

    async def action(self, item_id: str) -> str | None:
        visible, _ = await self._visible(self._entries())
        action = visible.get(item_id, {}).get("action")
        return action if isinstance(action, str) and action.strip() else None


def parse_desktop(text: str) -> dict | None:
    parser = configparser.RawConfigParser(strict=False, interpolation=None)
    parser.optionxform = str
    try:
        parser.read_string(text)
    except configparser.Error:
        return None
    if not parser.has_section("Desktop Entry"):
        return None
    entry = parser["Desktop Entry"]
    if entry.get("Type") != "Application" or entry.get("NoDisplay") == "true" or entry.get("Hidden") == "true" or not entry.get("Name"):
        return None
    result = {"name": entry["Name"]}
    if entry.get("Icon"):
        result["icon"] = entry["Icon"]
    return result


def application_dirs() -> list[str]:
    data = [os.environ.get("XDG_DATA_HOME") or str(Path.home() / ".local" / "share")]
    data += (os.environ.get("XDG_DATA_DIRS") or "/usr/local/share:/usr/share").split(":")
    return [os.path.join(d, "applications") for d in data]


def desktop_apps(dirs: list[str] | None = None) -> list[dict]:
    """Launchable apps by desktop id, the first directory winning (user entries override system)."""
    seen, apps = set(), []
    for directory in dirs or application_dirs():
        try:
            names = sorted(os.listdir(directory))
        except OSError:
            continue
        for name in names:
            app_id = name.removesuffix(".desktop")
            if not name.endswith(".desktop") or app_id in seen or not APP_ID.fullmatch(app_id):
                continue
            seen.add(app_id)
            try:
                entry = parse_desktop(Path(directory, name).read_text(errors="replace"))
            except OSError:
                entry = None
            if entry:
                apps.append({"id": app_id, "name": entry["name"]})
    return sorted(apps, key=lambda a: a["name"].lower())


async def spawn_detached(args: list[str]):
    """Starts a command for the desktop and returns at once (setsid -f / omarchy-menu return fast)."""
    try:
        process = await asyncio.create_subprocess_exec(*args, stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
        await asyncio.wait_for(process.wait(), 5)
    except (OSError, asyncio.TimeoutError):
        pass
