"""Images an agent names in its chat (a logo it drew, a screenshot it read), for the phone to show
inline: a path from the agent's messages, resolved from its project; only image files, inside the
home, never under a hidden folder (browse.fetchable), not too big. SVG goes as PNG (the phone has
no SVG renderer)."""
import asyncio
import os
from pathlib import Path

from .browse import fetchable

MAX_BYTES = 20 * 1024 * 1024
TYPES = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".webp": "image/webp", ".gif": "image/gif", ".svg": "image/svg+xml"}
SVG_WIDTH = 1200


def image_for(path: str, cwd: str | None, home) -> str | None:
    """The real path of the image [path] names (absolute, ~/, or relative to the agent's [cwd]); None if it may not go."""
    if not isinstance(path, str) or not path or "\0" in path or len(path) > 1000:
        return None
    if path.startswith("~/"):
        path = os.path.join(str(home), path[2:])
    elif not os.path.isabs(path):
        if not cwd:
            return None
        path = os.path.join(cwd, path)
    real = fetchable(path, Path(home))
    if real is None or Path(real).suffix.lower() not in TYPES or not os.path.isfile(real):
        return None
    try:
        if os.path.getsize(real) > MAX_BYTES:
            return None
    except OSError:
        return None
    return real


async def render(path: str) -> tuple[bytes, str] | None:
    """(bytes, content type) to send; an SVG drawn as PNG. None if it can't be read or drawn."""
    kind = TYPES[Path(path).suffix.lower()]
    if kind != "image/svg+xml":
        try:
            return await asyncio.to_thread(Path(path).read_bytes), kind
        except OSError:
            return None
    try:
        process = await asyncio.create_subprocess_exec("rsvg-convert", "-w", str(SVG_WIDTH), "-b", "white", path,
                                                       stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
    except OSError:
        return None
    try:
        out, _ = await asyncio.wait_for(process.communicate(), 10)
    except asyncio.TimeoutError:
        process.kill()
        await process.wait()
        return None
    return (out, "image/png") if process.returncode == 0 and out else None
