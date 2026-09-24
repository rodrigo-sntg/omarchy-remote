"""The phones that connected (for `omarchy-remote devices`) and the ones revoked: a revoked phone is
refused even with the right pairing code. Kept in ~/.config/omarchy-remote/devices.json (0600)."""
import json
import os
import time
from pathlib import Path


class Devices:
    def __init__(self, path, clock=time.time):
        self.path = Path(path)
        self.clock = clock
        try:
            self.data = json.loads(self.path.read_text())
        except (OSError, ValueError):
            self.data = {}
        self.data.setdefault("seen", {})
        self.data.setdefault("revoked", [])

    def _save(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.path.with_suffix(".tmp")
        fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w") as f:
            json.dump(self.data, f, indent=1)
        os.replace(tmp, self.path)

    def seen(self, name: str):
        now = int(self.clock())
        entry = self.data["seen"].setdefault(name, {"first": now})
        entry["last"] = now
        self._save()

    def list(self) -> list[dict]:
        return [{"name": n, **v, "revoked": n in self.data["revoked"]} for n, v in sorted(self.data["seen"].items())]

    def _match(self, short_or_full: str) -> str | None:
        key = short_or_full.lower().rstrip(".")
        for name in self.data["seen"]:
            if name == key or name.split(".")[0] == key:
                return name
        return None

    def revoke(self, short_or_full: str) -> bool:
        name = self._match(short_or_full)
        if name is None:
            return False
        if name not in self.data["revoked"]:
            self.data["revoked"].append(name)
            self._save()
        return True

    def revoked(self, name: str) -> bool:
        return name.lower().rstrip(".") in self.data["revoked"]

    def forget_all(self):
        self.data = {"seen": {}, "revoked": []}
        self._save()
