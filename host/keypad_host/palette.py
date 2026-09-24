"""Omarchy's keybindings and Hyprland's windows for the phone (docs/PLANO-V2.md §7). Binds are Lua
functions in Omarchy 4, so the phone runs one by pressing its keys through the virtual keyboard."""

# Hyprland modmask bits -> HID modifier bits (CTRL 1, SHIFT 2, ALT 4, SUPER 8), in display order.
_MODS = ((64, 0x08, "Super"), (4, 0x01, "Ctrl"), (8, 0x04, "Alt"), (1, 0x02, "Shift"))
_KEYS = {
    **{chr(ord("A") + i): 0x04 + i for i in range(26)},
    **{str(i): 0x1E + i - 1 for i in range(1, 10)}, "0": 0x27,
    "RETURN": 0x28, "ENTER": 0x28, "ESCAPE": 0x29, "BACKSPACE": 0x2A, "TAB": 0x2B, "SPACE": 0x2C,
    "MINUS": 0x2D, "EQUAL": 0x2E, "BRACKETLEFT": 0x2F, "BRACKETRIGHT": 0x30, "BACKSLASH": 0x31,
    "SEMICOLON": 0x33, "APOSTROPHE": 0x34, "GRAVE": 0x35, "COMMA": 0x36, "PERIOD": 0x37, "SLASH": 0x38,
    **{f"F{i}": 0x3A + i - 1 for i in range(1, 13)},
    "PRINT": 0x46, "INSERT": 0x49, "HOME": 0x4A, "PAGE_UP": 0x4B, "PRIOR": 0x4B, "DELETE": 0x4C, "END": 0x4D,
    "PAGE_DOWN": 0x4E, "NEXT": 0x4E, "RIGHT": 0x4F, "LEFT": 0x50, "DOWN": 0x51, "UP": 0x52,
}
_NAMES = {"RETURN": "Enter", "ESCAPE": "Esc", "BACKSPACE": "Backspace", "SPACE": "Space", "COMMA": ",", "PERIOD": ".",
          "SLASH": "/", "MINUS": "-", "EQUAL": "=", "PRINT": "Print", "DELETE": "Delete"}


def bind_for_phone(bind: dict) -> dict | None:
    """One described keyboard bind as {description, keys, usage, modifiers}, or None (mouse, media, submaps)."""
    description = bind.get("description") or ""
    key = str(bind.get("key", "")).upper()
    if not description or bind.get("mouse") or bind.get("release") or bind.get("submap") or key not in _KEYS:
        return None
    mask = int(bind.get("modmask", 0))
    modifiers, names = 0, []
    for bit, hid, name in _MODS:
        if mask & bit:
            modifiers |= hid
            names.append(name)
    names.append(_NAMES.get(key, key.title() if len(key) > 1 else key))
    return {"description": description, "keys": " + ".join(names), "usage": _KEYS[key], "modifiers": modifiers}


def binds_for_phone(binds: list[dict]) -> list[dict]:
    seen, result = set(), []
    for bind in binds:
        item = bind_for_phone(bind)
        if item and item["description"] not in seen:
            seen.add(item["description"])
            result.append(item)
    return sorted(result, key=lambda b: b["description"].lower())


def windows_for_phone(clients: list[dict]) -> list[dict]:
    """Mapped windows, by workspace (special ones last), with the focused one marked."""
    windows = []
    for c in clients:
        if not c.get("mapped", True) or not c.get("address"):
            continue
        workspace = c.get("workspace") or {}
        windows.append((
            (1 if int(workspace.get("id", 0)) < 0 else 0, int(workspace.get("id", 0)) if int(workspace.get("id", 0)) > 0 else 0,
             int(c.get("focusHistoryID", 99))),
            {"address": c["address"], "title": c.get("title") or c.get("class") or "", "app": c.get("class") or "",
             "workspace": str(workspace.get("name", "")), "floating": bool(c.get("floating")),
             "fullscreen": bool(c.get("fullscreen")), "focused": c.get("focusHistoryID") == 0},
        ))
    return [w for _, w in sorted(windows, key=lambda x: x[0])]
