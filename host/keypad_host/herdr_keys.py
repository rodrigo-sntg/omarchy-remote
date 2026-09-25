"""herdr's own shortcuts as terminal bytes, so the phone's terminal can split, close and switch panes
with one tap. The bindings come from the person's herdr config (the same keys they press on the
PC), with herdr's defaults for what it leaves out; the bytes go down the phone's own terminal, so
they act on what the phone shows."""
import os
import tomllib
from pathlib import Path

# herdr's defaults (the commented [keys] section it ships) for the actions the phone offers.
DEFAULTS = {
    "prefix": "ctrl+b",
    "split_vertical": "prefix+v",
    "split_horizontal": "prefix+minus",
    "close_pane": "prefix+x",
    "zoom": "prefix+z",
    "cycle_pane_next": "prefix+tab",
    "focus_pane_left": "prefix+h",
    "focus_pane_down": "prefix+j",
    "focus_pane_up": "prefix+k",
    "focus_pane_right": "prefix+l",
    "new_tab": "prefix+c",
    "previous_tab": "prefix+p",
    "next_tab": "prefix+n",
    "close_tab": "prefix+shift+x",
}
ACTIONS = tuple(DEFAULTS)

_NAMED = {
    "enter": b"\r", "return": b"\r", "tab": b"\t", "esc": b"\x1b", "escape": b"\x1b",
    "space": b" ", "backspace": b"\x7f", "minus": b"-", "plus": b"+",
}
_ARROWS = {"up": "A", "down": "B", "right": "C", "left": "D"}


def config_path() -> Path:
    base = os.environ.get("XDG_CONFIG_HOME") or os.path.expanduser("~/.config")
    return Path(base) / "herdr" / "config.toml"


def load(path: Path | None = None) -> dict:
    """The [keys] of the herdr config over the defaults; the defaults alone when it can't be read."""
    keys = dict(DEFAULTS)
    try:
        with open(path or config_path(), "rb") as f:
            section = tomllib.load(f).get("keys", {})
    except (OSError, tomllib.TOMLDecodeError):
        return keys
    if isinstance(section, dict):
        keys.update({k: v for k, v in section.items() if k in DEFAULTS})
    return keys


def _key(name: str, ctrl: bool, alt: bool, shift: bool) -> bytes | None:
    if name in _ARROWS:
        mod = 1 + shift + 2 * alt + 4 * ctrl
        return f"\x1b[{_ARROWS[name]}".encode() if mod == 1 else f"\x1b[1;{mod}{_ARROWS[name]}".encode()
    if name == "tab" and shift and not ctrl:
        out = b"\x1b[Z"
    elif len(name) == 1 and name.isprintable():
        c = name.upper() if shift else name
        if ctrl:
            if name == " " or c == "@":
                out = b"\x00"
            elif c.isascii() and c.isalpha():
                out = bytes([ord(c.upper()) - 64])
            elif c in "[\\]^_":
                out = bytes([ord(c) - 64])
            else:
                return None
        else:
            out = c.encode()
    elif name in _NAMED:
        if name == "space" and ctrl:
            out = b"\x00"
        elif ctrl or shift:
            return None
        else:
            out = _NAMED[name]
    else:
        return None
    return b"\x1b" + out if alt else out


def encode(binding: str, prefix: bytes | None) -> bytes | None:
    """One binding ("prefix+shift+x", "alt+enter"…) as bytes, or None if a terminal can't send it."""
    parts = binding.strip().lower().split("+") if binding else []
    if not parts or "" in parts or ".." in binding:
        return None
    lead = b""
    if parts[0] == "prefix":
        if prefix is None:
            return None
        lead, parts = prefix, parts[1:]
    *mods, name = parts
    if not name or any(m not in ("ctrl", "alt", "shift") for m in mods):
        return None
    out = _key(name, "ctrl" in mods, "alt" in mods, "shift" in mods)
    return lead + out if out is not None else None


def _first(value, prefix: bytes | None) -> bytes | None:
    bindings = value if isinstance(value, list) else [value]
    for binding in bindings:
        if isinstance(binding, str) and (out := encode(binding, prefix)) is not None:
            return out
    return None


def sequences(keys: dict) -> dict[str, bytes]:
    """Every action that has a sendable binding, as the bytes to write."""
    prefix = _first(keys.get("prefix"), None)
    out = {"prefix": prefix} if prefix is not None else {}
    for action in ACTIONS[1:]:
        if (seq := _first(keys.get(action), prefix)) is not None:
            out[action] = seq
    return out
