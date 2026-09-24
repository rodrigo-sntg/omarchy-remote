"""Files between the phone and the PC: names that stay inside the folder, never overwriting."""
import os
import re
import subprocess
from pathlib import Path

MAX_UPLOAD = 2 * 1024 ** 3
FALLBACK_NAME = "arquivo-do-celular"
_CONTROL = re.compile(r"[\x00-\x1f\x7f]")


def safe_name(raw: str) -> str:
    """The last path part, without control characters or leading dots, at most 200 characters."""
    name = _CONTROL.sub("", raw.replace("\\", "/").rsplit("/", 1)[-1]).strip().lstrip(".").strip()
    if not name:
        return FALLBACK_NAME
    if len(name) > 200:
        stem, dot, ext = name.rpartition(".")
        name = stem[:200 - len(ext) - 1] + "." + ext if dot and len(ext) <= 16 else name[:200]
    return name


def unique_path(folder: Path, name: str) -> Path:
    """folder/name, or "name (1)", "name (2)"… when taken."""
    folder = Path(folder)
    candidate = folder / name
    stem, dot, ext = name.rpartition(".")
    if not dot or not stem:
        stem, ext = name, ""
    n = 1
    while candidate.exists():
        candidate = folder / (f"{stem} ({n}).{ext}" if ext else f"{stem} ({n})")
        n += 1
    return candidate


AGENT_FOLDER = ".omarchy-remote"
_MENTION_UNSAFE = re.compile(r"[^\w.\-]+")


def agent_inbox(cwd: str | None) -> Path | None:
    """Where a file for an agent goes: a folder in its project (it reads it without leaving it),
    kept out of git through the repository's own local exclude file (never committed)."""
    if not cwd or not os.path.isabs(cwd) or not os.path.isdir(cwd):
        return None
    project = Path(cwd)
    inbox = project / AGENT_FOLDER
    inbox.mkdir(exist_ok=True)
    info = project / ".git" / "info"
    if info.is_dir():
        exclude = info / "exclude"
        line = f"/{AGENT_FOLDER}/"
        text = exclude.read_text() if exclude.exists() else ""
        if line not in text.splitlines():
            exclude.write_text(text + ("" if not text or text.endswith("\n") else "\n") + line + "\n")
    return inbox


def agent_name(folder: Path, raw: str) -> str:
    """A name an @mention can hold (no spaces, no brackets), never overwriting: "log-2.txt"."""
    name = safe_name(raw)
    stem, dot, ext = name.rpartition(".")
    if not dot or not stem:
        stem, ext = name, ""
    stem = _MENTION_UNSAFE.sub("-", stem).strip("-") or FALLBACK_NAME
    ext = _MENTION_UNSAFE.sub("", ext)
    candidate = f"{stem}.{ext}" if ext else stem
    n = 2
    while (Path(folder) / candidate).exists():
        candidate = f"{stem}-{n}.{ext}" if ext else f"{stem}-{n}"
        n += 1
    return candidate


def downloads_dir() -> Path:
    """The user's Downloads folder (xdg-user-dir), created if missing."""
    try:
        path = subprocess.run(["xdg-user-dir", "DOWNLOAD"], capture_output=True, text=True, timeout=2).stdout.strip()
    except (OSError, subprocess.TimeoutExpired):
        path = ""
    folder = Path(path) if path and path != os.path.expanduser("~") else Path.home() / "Downloads"
    folder.mkdir(parents=True, exist_ok=True)
    return folder


class Offers:
    """Files offered to the phone: a random id each, valid for [ttl] seconds. Temporary ones (prints)
    are deleted when they expire."""

    def __init__(self, ttl: float = 600.0, clock=None):
        import time
        self.ttl = ttl
        self.clock = clock or time.monotonic
        self._items: dict[str, tuple[Path, float, bool]] = {}

    def add(self, path: Path, temporary: bool = False) -> dict:
        import secrets
        self.sweep()
        path = Path(path)
        offer_id = secrets.token_urlsafe(18)
        self._items[offer_id] = (path, self.clock() + self.ttl, temporary)
        return {"id": offer_id, "name": path.name, "size": path.stat().st_size}

    def get(self, offer_id: str) -> Path | None:
        self.sweep()
        item = self._items.get(offer_id)
        return item[0] if item and item[0].is_file() else None

    def sweep(self):
        now = self.clock()
        for offer_id, (path, until, temporary) in list(self._items.items()):
            if now > until:
                del self._items[offer_id]
                if temporary:
                    path.unlink(missing_ok=True)


def grim_args(path: str, monitor: str | None = None, geometry=None, region: tuple | None = None) -> list[str]:
    """grim for the whole desktop, one monitor, or a region of it (x, y, w, h as fractions)."""
    if monitor and geometry is not None and region is not None:
        x, y, w, h = region
        gx, gy = round(geometry.x + x * geometry.width), round(geometry.y + y * geometry.height)
        gw, gh = max(1, round(w * geometry.width)), max(1, round(h * geometry.height))
        return ["grim", "-g", f"{gx},{gy} {gw}x{gh}", path]
    if monitor:
        return ["grim", "-o", monitor, path]
    return ["grim", path]


def shots_dir() -> Path:
    """Where prints wait for the phone: private to this user, cleared when the offer expires."""
    base = os.environ.get("XDG_RUNTIME_DIR") or "/tmp"
    folder = Path(base) / "omarchy-remote-shots"
    folder.mkdir(mode=0o700, exist_ok=True)
    return folder


async def run_grim(args: list[str]) -> bool:
    import asyncio
    try:
        process = await asyncio.create_subprocess_exec(*args, stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
    except OSError:
        return False
    try:
        return await asyncio.wait_for(process.wait(), 10) == 0
    except asyncio.TimeoutError:
        process.kill()
        return False
