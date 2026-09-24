"""The active Omarchy theme for the phone (docs/PLANO-V2.md §5.3): the palette the app maps onto its
design tokens, read through Omarchy's own resolver (`omarchy-theme-color`), never its files' format."""
import asyncio
import re
from pathlib import Path

COLOR_KEYS = (
    "accent", "background", "dark_background", "darker_background", "foreground",
    "bright_foreground", "muted", "selection", "red", "bright_red",
)
_HEX = re.compile(r"#[0-9a-fA-F]{6}")
THEME_NAME = Path.home() / ".local" / "state" / "omarchy" / "current" / "theme.name"


async def run_command(args: list[str]) -> str | None:
    """stdout of a command, or None when it is missing or fails."""
    try:
        process = await asyncio.create_subprocess_exec(
            *args, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL,
        )
        out, _ = await asyncio.wait_for(process.communicate(), 5)
    except (OSError, asyncio.TimeoutError):
        return None
    return out.decode(errors="replace") if process.returncode == 0 else None


def theme_name() -> str | None:
    try:
        return THEME_NAME.read_text().strip() or None
    except OSError:
        return None


_SLUG = re.compile(r"[a-z0-9][a-z0-9-]{0,63}")
THEME_ROOTS = [str(Path.home() / ".config" / "omarchy" / "themes"), "/usr/share/omarchy/themes"]


def theme_file(slug: str, roots=None) -> str | None:
    """colors.toml of an installed theme (user themes first), for a preview; None if unknown."""
    if not isinstance(slug, str) or not _SLUG.fullmatch(slug):
        return None
    for root in roots or THEME_ROOTS:
        path = Path(root) / slug / "colors.toml"
        if path.is_file():
            return str(path)
    return None


async def read_theme(run=run_command, name=theme_name, file: str | None = None) -> dict | None:
    """The active theme, or the one in [file] (a preview: nothing on the PC changes)."""
    source = ["--file", file] if file else []
    output = await run(["omarchy-theme-color", *source, "--all"])
    if output is None:
        return None
    colors = {}
    for line in output.splitlines():
        key, _, value = line.partition("\t")
        if key in COLOR_KEYS and _HEX.fullmatch(value.strip()):
            colors[key] = value.strip().lower()
    mode = (await run(["omarchy-theme-color", *source, "mode", "dark"]) or "dark").strip()
    label = Path(file).parent.name if file else (name() or "")
    return {"type": "theme", "name": label.strip(), "mode": "light" if mode == "light" else "dark", "colors": colors}
