"""USB HID keyboard usages to Linux evdev key codes. Explicit table: HID numbers are never evdev codes."""
from evdev import ecodes as e

_TABLE = {
    **{0x04 + i: getattr(e, f"KEY_{chr(ord('A') + i)}") for i in range(26)},
    **{0x1E + i: getattr(e, f"KEY_{d}") for i, d in enumerate("1234567890")},
    0x28: e.KEY_ENTER, 0x29: e.KEY_ESC, 0x2A: e.KEY_BACKSPACE, 0x2B: e.KEY_TAB, 0x2C: e.KEY_SPACE,
    0x2D: e.KEY_MINUS, 0x2E: e.KEY_EQUAL, 0x2F: e.KEY_LEFTBRACE, 0x30: e.KEY_RIGHTBRACE,
    0x31: e.KEY_BACKSLASH, 0x32: e.KEY_BACKSLASH, 0x33: e.KEY_SEMICOLON, 0x34: e.KEY_APOSTROPHE,
    0x35: e.KEY_GRAVE, 0x36: e.KEY_COMMA, 0x37: e.KEY_DOT, 0x38: e.KEY_SLASH, 0x39: e.KEY_CAPSLOCK,
    **{0x3A + i: getattr(e, f"KEY_F{i + 1}") for i in range(12)},
    0x49: e.KEY_INSERT, 0x4A: e.KEY_HOME, 0x4B: e.KEY_PAGEUP, 0x4C: e.KEY_DELETE, 0x4D: e.KEY_END,
    0x4E: e.KEY_PAGEDOWN, 0x4F: e.KEY_RIGHT, 0x50: e.KEY_LEFT, 0x51: e.KEY_DOWN, 0x52: e.KEY_UP,
    0x64: e.KEY_102ND, 0x87: e.KEY_RO,
}

# Modifier byte bits, in HID order: LCtrl LShift LAlt LGui RCtrl RShift RAlt RGui.
_MODIFIERS = [
    e.KEY_LEFTCTRL, e.KEY_LEFTSHIFT, e.KEY_LEFTALT, e.KEY_LEFTMETA,
    e.KEY_RIGHTCTRL, e.KEY_RIGHTSHIFT, e.KEY_RIGHTALT, e.KEY_RIGHTMETA,
]


def keycode(usage: int) -> int | None:
    return _TABLE.get(usage)


def modifier_keys(bits: int) -> list[int]:
    return [key for bit, key in enumerate(_MODIFIERS) if bits & (1 << bit)]


def tap_codes(usage: int, modifiers: int) -> list[int] | None:
    """Keys to press in order (modifiers first) for one tap; usage 0 = the modifiers alone (e.g. Super)."""
    mods = modifier_keys(modifiers)
    if usage == 0:
        return mods or None
    key = keycode(usage)
    return None if key is None else [*mods, key]


ALL_KEYS = sorted(set(_TABLE.values()) | set(_MODIFIERS))
