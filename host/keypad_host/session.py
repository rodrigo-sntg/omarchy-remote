"""One authorized controller. Independent of sockets and uinput so it can be tested directly."""
from .protocol import ProtocolError, UnknownType, parse

HEARTBEAT_TIMEOUT = 6.0
_ACKED = {"keyboard.tap", "pointer.buttons", "input.releaseAll", "workspace.go", "workspace.step", "clipboard.set"}


_INPUT = {"pointer.move", "pointer.buttons", "pointer.scroll", "keyboard.tap", "workspace.go", "workspace.step"}


class Session:
    def __init__(self, session_id: str, injector, now: float, heartbeat_timeout: float = HEARTBEAT_TIMEOUT, desktop=None):
        self.desktop = desktop  # Hyprland IPC for workspaces; None when unavailable
        self.session_id = session_id
        self.heartbeat_timeout = heartbeat_timeout
        self.injector = injector
        self.last_seen = now
        self.last_seq = 0
        self.closed = False
        self.last_message = None  # the last valid message, for handlers that answer asynchronously
        self.watch_cursor = True  # the phone shows the cursor map (it says when it does not)
        # Set by the server: true while the PC asks whether a phone may unlock it (the answer must
        # come from someone at the PC, so the phones' keyboard and mouse are off meanwhile).
        self.input_blocked = lambda: False

    def handle(self, raw: str, now: float) -> list[dict]:
        if self.closed:
            return []
        self.last_message = None
        try:
            message = parse(raw)
        except UnknownType as unknown:
            message = unknown  # a newer app's message: checked like any other, then skipped
        except ProtocolError as error:
            self.close()
            return [{"type": "error", "message": str(error)}]
        if message.session_id != self.session_id or message.seq <= self.last_seq:
            self.close()
            return [{"type": "error", "message": "wrong session" if message.session_id != self.session_id else "sequence not increasing"}]
        if isinstance(message, UnknownType):
            # Newer app, older host: "not done", and the session goes on.
            self.last_seq, self.last_seen = message.seq, now
            return [{"type": "ack", "seq": message.seq, "ok": False}]
        self.last_seq = message.seq
        self.last_seen = now
        self.last_message = message
        ok = self._inject(message)
        if message.type == "ping":
            return [{"type": "pong", "seq": message.seq}]
        if message.type in _ACKED:
            # Processed by the host; says nothing about what the focused application did with it.
            return [{"type": "ack", "seq": message.seq, "ok": ok}]
        return []

    def _inject(self, message) -> bool:
        p = message.payload
        if message.type in _INPUT and self.input_blocked():
            return False
        match message.type:
            case "pointer.move":
                self.injector.move(p["dx"], p["dy"])
            case "pointer.scroll":
                self.injector.scroll(p["vertical"])
            case "pointer.buttons":
                self.injector.buttons(p["mask"])
            case "keyboard.tap":
                return self.injector.tap(p["usage"], p["modifiers"])
            case "watch":
                self.watch_cursor = p["cursor"]
            case "input.releaseAll":
                self.injector.release_all()
            case "workspace.go" | "workspace.step":
                if self.desktop is None:
                    return False
                target = str(p["id"]) if message.type == "workspace.go" else ("e+1" if p["direction"] > 0 else "e-1")
                self.desktop.focus_workspace(target)
        return True

    def expired(self, now: float) -> bool:
        return now - self.last_seen > self.heartbeat_timeout

    def close(self):
        if not self.closed:
            self.injector.release_all()
            self.closed = True
