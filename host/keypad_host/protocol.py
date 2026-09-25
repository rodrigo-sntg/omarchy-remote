"""Protocol v1: one JSON envelope per WebSocket text frame. Anything unexpected is rejected before input."""
import json
import re
from dataclasses import dataclass

MAX_FRAME = 16 * 1024
READ_LIMIT = 4 * MAX_FRAME  # read this much so an oversized message can be refused instead of dropping the link
_AXIS = 10_000


class ProtocolError(ValueError):
    pass


class UnknownType(ProtocolError):
    """A well-formed message of a type this host does not know (a newer app): skipped, not fatal."""

    def __init__(self, session_id: str, seq: int):
        super().__init__("unknown type")
        self.session_id = session_id
        self.seq = seq


@dataclass(frozen=True)
class Message:
    session_id: str
    seq: int
    type: str
    payload: dict


def _int(payload: dict, name: str, low: int, high: int) -> int:
    value = payload.get(name)
    if type(value) is not int or not low <= value <= high:  # bool is not accepted
        raise ProtocolError(f"invalid {name}")
    return value


class TooLarge(UnknownType):
    """A well-formed message over MAX_FRAME bytes (long non-ASCII text): refused, not fatal."""


CLIPBOARD_SET_LIMIT = 12_000  # characters: with its envelope a message stays under MAX_FRAME


def _clip_text(payload: dict) -> str:
    value = payload.get("text")
    if not isinstance(value, str) or not 1 <= len(value) <= CLIPBOARD_SET_LIMIT:
        raise ProtocolError("invalid text")
    return value


_MENU_ID = re.compile(r"[a-z0-9][a-z0-9._-]{0,63}")
_APP_ID = re.compile(r"app:[A-Za-z0-9][A-Za-z0-9._+-]{0,127}")


def _menu_id(payload: dict, name: str, allow_app: bool = False) -> str:
    """An Omarchy menu item id (system.lock), or app:<desktop id> for the launcher."""
    value = payload.get(name)
    if not isinstance(value, str) or not (_MENU_ID.fullmatch(value) or (allow_app and _APP_ID.fullmatch(value))) or ".." in value:
        raise ProtocolError(f"invalid {name}")
    return value


_ADDRESS = re.compile(r"0x[0-9a-f]{1,16}")
WINDOW_ACTIONS = ("focus", "close", "float", "fullscreen", "workspace")


def _window_act(payload: dict) -> dict:
    address, action = payload.get("address"), payload.get("action")
    if not isinstance(address, str) or not _ADDRESS.fullmatch(address) or action not in WINDOW_ACTIONS:
        raise ProtocolError("invalid window action")
    result = {"address": address, "action": action}
    if action == "workspace":
        result["workspace"] = _int(payload, "workspace", 1, 99)
    return result


def _media_action(payload: dict) -> str:
    value = payload.get("action")
    if value not in ("play-pause", "next", "previous", "volume-up", "volume-down"):
        raise ProtocolError("invalid action")
    return value


def _shot(payload: dict) -> dict:
    """A print for the phone: the whole desktop, a monitor, or a region of one (fractions 0..1)."""
    out = {"tag": payload.get("tag", "save")}
    if out["tag"] not in ("save", "ocr"):
        raise ProtocolError("invalid tag")
    monitor = payload.get("monitor")
    if monitor is not None:
        if not isinstance(monitor, str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,64}", monitor):
            raise ProtocolError("invalid monitor")
        out["monitor"] = monitor
    region = payload.get("region")
    if region is not None:
        if monitor is None or not isinstance(region, dict):
            raise ProtocolError("invalid region")
        values = [region.get(k) for k in ("x", "y", "w", "h")]
        if any(type(v) not in (int, float) for v in values):
            raise ProtocolError("invalid region")
        x, y, w, h = (float(v) for v in values)
        if not (0 <= x < 1 and 0 <= y < 1 and 0 < w <= 1 - x + 1e-6 and 0 < h <= 1 - y + 1e-6):
            raise ProtocolError("invalid region")
        out["region"] = (x, y, w, h)
    return out


def _pc_action(payload: dict) -> str:
    value = payload.get("action")
    if value not in ("lock", "suspend", "reboot", "shutdown"):
        raise ProtocolError("invalid action")
    return value


def _bool(payload: dict, name: str) -> bool:
    value = payload.get(name)
    if type(value) is not bool:
        raise ProtocolError(f"invalid {name}")
    return value


def _direction(payload: dict) -> int:
    value = payload.get("direction")
    if value not in (-1, 1) or type(value) is not int:
        raise ProtocolError("invalid direction")
    return value


# A herdr pane id or a live agent name; nothing else reaches the herdr CLI. herdr numbers
# workspaces and panes past 9 with letters (w5, wA, wD), so ids are alphanumeric.
_TARGET = re.compile(r"w[0-9A-Za-z]{1,8}:p[0-9A-Za-z]{1,8}|[a-z][a-z0-9_-]{0,31}")
AGENT_TARGET = _TARGET  # also for the file upload's X-Keypad-Agent
# Keys the phone may press in an agent (docs/PLANO-V2.md §4.3); herdr's own names.
AGENT_KEYS = frozenset({"enter", "esc", "tab", "up", "down", "left", "right", "y", "n", "ctrl+c", *"123456789"})
_PROMPT_LIMIT = 3000  # characters; a UTF-8 prompt stays under MAX_FRAME with its envelope


def _target(payload: dict) -> str:
    value = payload.get("id")
    if not isinstance(value, str) or not _TARGET.fullmatch(value):
        raise ProtocolError("invalid id")
    return value


def _keys(payload: dict) -> list[str]:
    value = payload.get("keys")
    if not isinstance(value, list) or not 1 <= len(value) <= 8 or any(key not in AGENT_KEYS for key in value):
        raise ProtocolError("invalid keys")
    return list(value)


def _prompt(payload: dict) -> str:
    value = payload.get("text")
    if not isinstance(value, str) or not 1 <= len(value) <= _PROMPT_LIMIT:
        raise ProtocolError("invalid text")
    if any(ord(c) < 32 and c not in "\n\t" for c in value):
        raise ProtocolError("invalid text")
    return value


def _cwd(payload: dict) -> str:
    value = payload.get("cwd")
    if not isinstance(value, str) or not value.startswith("/") or len(value) > 400 or "\x00" in value:
        raise ProtocolError("invalid cwd")
    return value


_UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")


def _session_uuid(payload: dict) -> str:
    value = payload.get("session")
    if not isinstance(value, str) or not _UUID.fullmatch(value):
        raise ProtocolError("invalid session")
    return value


def _kind(payload: dict) -> str:
    value = payload.get("kind")
    if value not in ("claude", "codex"):
        raise ProtocolError("invalid kind")
    return value


def _optional_prompt(payload: dict) -> str:
    return "" if not payload.get("text") else _prompt(payload)


def _text_field(payload: dict, name: str, limit: int) -> str:
    value = payload.get(name)
    if not isinstance(value, str) or not 1 <= len(value) <= limit or any(ord(c) < 32 for c in value):
        raise ProtocolError(f"invalid {name}")
    return value


def _free_text(payload: dict, name: str, limit: int) -> str:
    """Text shown to the person (a notification's title or body): may be empty or have line breaks;
    other control characters are dropped."""
    value = payload.get(name, "")
    if not isinstance(value, str) or len(value) > limit:
        raise ProtocolError(f"invalid {name}")
    return "".join(c for c in value if c == "\n" or ord(c) >= 32)


_BASE64 = re.compile(r"[A-Za-z0-9+/]+={0,2}")


def _base64(payload: dict, name: str, limit: int) -> str:
    value = payload.get(name)
    if not isinstance(value, str) or not 4 <= len(value) <= limit or not _BASE64.fullmatch(value):
        raise ProtocolError(f"invalid {name}")
    return value


def _phone_notification(p: dict) -> dict:
    return {"key": _text_field(p, "key", 300), "app": _text_field(p, "app", 60), "title": _free_text(p, "title", 200),
            "text": _free_text(p, "text", 1500), "reply": _bool(p, "reply")}


_CONTROL = re.compile(r"[a-z][a-z-]{0,23}")


def _control_id(payload: dict) -> str:
    value = payload.get("id")
    if not isinstance(value, str) or not _CONTROL.fullmatch(value):
        raise ProtocolError("invalid control")
    return value


_CALL_ID = re.compile(r"[A-Za-z0-9_-]{1,128}")
_OFFSET_MAX = 2 ** 53


def _offset(payload: dict, name: str, optional: bool = False) -> int | None:
    if optional and payload.get(name) is None:
        return None
    return _int(payload, name, 0, _OFFSET_MAX)


def _call(payload: dict) -> str:
    value = payload.get("call")
    if not isinstance(value, str) or not _CALL_ID.fullmatch(value):
        raise ProtocolError("invalid call")
    return value


_PAYLOADS = {
    "pointer.move": lambda p: {"dx": _int(p, "dx", -_AXIS, _AXIS), "dy": _int(p, "dy", -_AXIS, _AXIS)},
    "pointer.buttons": lambda p: {"mask": _int(p, "mask", 0, 7)},
    "pointer.scroll": lambda p: {"vertical": _int(p, "vertical", -100, 100)},
    "keyboard.tap": lambda p: {"usage": _int(p, "usage", 0, 255), "modifiers": _int(p, "modifiers", 0, 255)},
    "input.releaseAll": lambda p: {},
    "workspace.go": lambda p: {"id": _int(p, "id", 1, 99)},
    "workspace.step": lambda p: {"direction": _direction(p)},
    "agent.read": lambda p: {"id": _target(p), "lines": _int(p, "lines", 1, 200), "ansi": p.get("ansi") is True},
    "agent.keys": lambda p: {"id": _target(p), "keys": _keys(p)},
    "agent.prompt": lambda p: {"id": _target(p), "text": _prompt(p)},
    "agent.focus": lambda p: {"id": _target(p)},
    # The agent's conversation from its session file: a page, new messages while open, an output whole.
    "agent.history": lambda p: {"id": _target(p), "before": _offset(p, "before", optional=True), "limit": _int(p, "limit", 1, 60)},
    "agent.follow": lambda p: {"id": _target(p), "after": _offset(p, "after")},
    "agent.unfollow": lambda p: {},
    "agent.commands": lambda p: {"id": _target(p)},
    "agent.subagents": lambda p: {"id": _target(p)},
    "agent.git": lambda p: {"id": _target(p)},
    "agent.gitdiff": lambda p: {"id": _target(p), "path": _text_field(p, "path", 500)},
    "agent.start": lambda p: {"cwd": _cwd(p), "kind": _kind(p), "prompt": _optional_prompt({"text": p.get("prompt")})},
    "projects.list": lambda p: {},
    "agent.sessions": lambda p: {},
    "agent.resume": lambda p: {"kind": _kind(p), "session": _session_uuid(p)},
    "controls.get": lambda p: {},
    "host.get": lambda p: {},
    "stats.get": lambda p: {},
    "thumb.get": lambda p: {"monitor": _text_field(p, "monitor", 40), "width": _int(p, "width", 120, 960)},
    "host.restart": lambda p: {},
    "pc.open_url": lambda p: {"url": _text_field(p, "url", 2100)},
    "files.list": lambda p: {"path": _text_field(p, "path", 1000) if p.get("path") else None},
    "files.fetch": lambda p: {"path": _text_field(p, "path", 1000)},
    "pc.control": lambda p: {"id": _control_id(p), "value": p.get("value") if isinstance(p.get("value"), (int, str)) and not isinstance(p.get("value"), bool) else None},
    "agent.output": lambda p: {"id": _target(p), "at": _offset(p, "at"), "call": _call(p)},
    # Unlocking the PC with the phone's fingerprint (unlock.py): the key once, then a signed challenge.
    "unlock.enroll": lambda p: {"key": _base64(p, "key", 400)},
    "unlock.challenge": lambda p: {},
    "unlock.respond": lambda p: {"signature": _base64(p, "signature", 200)},
    "phone.notification": _phone_notification,
    "phone.notification.removed": lambda p: {"key": _text_field(p, "key", 300)},
    "phone.status": lambda p: {"battery": _int(p, "battery", 0, 100), "charging": _bool(p, "charging")},
    "watch": lambda p: {"cursor": _bool(p, "cursor")},
    "clipboard.set": lambda p: {"text": _clip_text(p)},
    "clipboard.get": lambda p: {},
    "menu.get": lambda p: {},
    "menu.run": lambda p: {"id": _menu_id(p, "id", allow_app=True)},
    "menu.show": lambda p: {"route": _menu_id(p, "route")},
    "binds.get": lambda p: {},
    "windows.get": lambda p: {},
    "window.act": lambda p: _window_act(p),
    "now.get": lambda p: {},
    "media.cmd": lambda p: {"action": _media_action(p)},
    "pc.act": lambda p: {"action": _pc_action(p)},
    "shot.get": _shot,
    "usage.get": lambda p: {"force": p.get("force") is True},
    "ping": lambda p: {},
}


def parse(raw: str) -> Message:
    size = len(raw.encode())
    if size > READ_LIMIT:
        raise ProtocolError("frame too large")
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as error:
        raise ProtocolError("invalid JSON") from error
    if not isinstance(data, dict) or data.get("v") != 1:
        raise ProtocolError("unsupported version")
    session_id, seq, type_, payload = data.get("sessionId"), data.get("seq"), data.get("type"), data.get("payload")
    if not isinstance(session_id, str) or type(seq) is not int or seq < 0 or not isinstance(payload, dict):
        raise ProtocolError("invalid envelope")
    if size > MAX_FRAME:
        raise TooLarge(session_id, seq)
    if type_ not in _PAYLOADS:
        raise UnknownType(session_id, seq)
    return Message(session_id, seq, type_, _PAYLOADS[type_](payload))
