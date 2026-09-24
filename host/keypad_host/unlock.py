"""Unlocking Omarchy's lock screen with the phone's fingerprint, without the PC's password.

The phone holds an EC key that only its fingerprint unlocks (enrolled once, confirmed on the PC).
While the PC is locked: the phone asks for a challenge, signs it after the fingerprint, and the PC
checks the signature. Then the PC makes a random one-time code (32 letters and digits, 60 s, one use),
keeps it in a file only this user reads, and types it into the lock's password field. A line in
/etc/pam.d/omarchy-lock-password (pam-unlock-code) accepts that code; the real password keeps working.
"""
import asyncio
import base64
import json
import logging
import os
import secrets
import time
from pathlib import Path

log = logging.getLogger(__name__)
CHALLENGE_TTL = 60.0
CODE_TTL = 60.0
LIMIT, WINDOW = 5, 60.0
ESCAPE, ENTER = 41, 40
ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"


def parse_status(text: str) -> bool | None:
    """True only when the lock is up and its surface secured (it has the keyboard)."""
    try:
        s = json.loads(text)
    except (ValueError, TypeError):
        return None
    return bool(s.get("locked") and s.get("sessionLocked") and s.get("secure"))


async def lock_status() -> bool | None:
    try:
        p = await asyncio.create_subprocess_exec("omarchy-shell", "lock", "status",
                                                 stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
        out, _ = await asyncio.wait_for(p.communicate(), 3)
    except (OSError, asyncio.TimeoutError):
        return None
    return parse_status(out.decode(errors="replace"))


def rule_installed(path="/etc/pam.d/omarchy-lock-password") -> bool:
    try:
        return "pam-unlock-code" in Path(path).read_text()
    except OSError:
        return False


def usage(ch: str) -> int:
    """HID usage of a lowercase letter or digit (same place on US and ABNT2 keyboards)."""
    if "a" <= ch <= "z":
        return 4 + ord(ch) - ord("a")
    return 39 if ch == "0" else 30 + ord(ch) - ord("1")


class Keys:
    """The phones' public keys (base64 SPKI DER), in ~/.config/omarchy-remote/unlock_keys.json (0600)."""

    def __init__(self, path):
        self.path = Path(path)
        try:
            self.data = json.loads(self.path.read_text())
        except (OSError, ValueError):
            self.data = {}

    def _save(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.path.with_suffix(".tmp")
        fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w") as f:
            json.dump(self.data, f)
        os.replace(tmp, self.path)

    def add(self, device: str, public_key: str):
        self.data[device] = public_key
        self._save()

    def get(self, device: str) -> str | None:
        return self.data.get(device)

    def remove(self, short_or_full: str) -> bool:
        key = short_or_full.lower().rstrip(".")
        hit = [d for d in self.data if d == key or d.split(".")[0] == key]
        for d in hit:
            del self.data[d]
        if hit:
            self._save()
        return bool(hit)


def verify(public_key_b64: str, data: bytes, signature_b64: str) -> bool:
    from cryptography.exceptions import InvalidSignature
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    try:
        key = serialization.load_der_public_key(base64.b64decode(public_key_b64))
        if not isinstance(key, ec.EllipticCurvePublicKey):
            return False
        key.verify(base64.b64decode(signature_b64), data, ec.ECDSA(hashes.SHA256()))
        return True
    except (InvalidSignature, ValueError, TypeError):
        return False


def valid_public_key(public_key_b64: str) -> bool:
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    try:
        key = serialization.load_der_public_key(base64.b64decode(public_key_b64))
    except (ValueError, TypeError):
        return False
    return isinstance(key, ec.EllipticCurvePublicKey) and key.curve.name == "secp256r1"


async def confirm_on_pc(device: str) -> bool:
    """Asks at the PC whether this phone may unlock it: clicking the notification allows (Omarchy's
    notifications have no buttons; a click is the default action), closing it refuses."""
    try:
        p = await asyncio.create_subprocess_exec(
            "notify-send", "--app-name=omarchy-remote", "--wait", "--urgency=critical", "--icon=phone",
            "--action=default=Permitir",
            "Desbloquear este PC pelo celular?",
            f"{device.split('.')[0]} quer desbloquear este PC com a digital. Clique aqui para permitir; feche para recusar.",
            stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
        out, _ = await asyncio.wait_for(p.communicate(), 120)
    except (OSError, asyncio.TimeoutError):
        return False
    return out.decode(errors="replace").strip().splitlines()[-1:] == ["default"]


class Unlocker:
    def __init__(self, injector, keys: Keys, run_dir, status=lock_status, notify=lambda *a: None,
                 clock=time.monotonic, wall=time.time, settle=0.8, key_gap=0.025, confirm_wait=0.25):
        self.injector, self.keys, self.run_dir = injector, keys, Path(run_dir)
        self.status, self.notify, self.clock, self.wall, self.settle = status, notify, clock, wall, settle
        self.key_gap, self.confirm_wait = key_gap, confirm_wait
        self.pending: dict[str, tuple[bytes, float]] = {}
        self.tries: list[float] = []

    def ready(self, device: str) -> dict:
        """What the phone needs to know to offer the unlock: its key here, the lock's rule installed."""
        return {"enrolled": bool(self.keys.get(device)), "rule": rule_installed()}

    async def enroll(self, device: str, public_key: str, confirm=confirm_on_pc) -> dict:
        """A phone's key, only with the PC unlocked and a yes from someone at the PC."""
        if not valid_public_key(public_key):
            return {"type": "unlock.enrolled", "ok": False, "reason": "bad-key"}
        if await self.status() is not False:
            return {"type": "unlock.enrolled", "ok": False, "reason": "locked"}
        if not await confirm(device):
            return {"type": "unlock.enrolled", "ok": False, "reason": "denied"}
        self.keys.add(device, public_key)
        log.info("unlock: key enrolled for %s", device)
        return {"type": "unlock.enrolled", "ok": True, **self.ready(device)}

    def _refused(self, reason: str) -> dict:
        return {"type": "unlock.result", "ok": False, "reason": reason}

    async def challenge(self, device: str) -> dict:
        if not self.keys.get(device):
            return {"type": "unlock.challenge", "ok": False, "reason": "not-enrolled"}
        if await self.status() is not True:
            return {"type": "unlock.challenge", "ok": False, "reason": "not-locked"}
        nonce = secrets.token_bytes(32)
        self.pending[device] = (nonce, self.clock())
        return {"type": "unlock.challenge", "ok": True, "nonce": base64.b64encode(nonce).decode()}

    async def respond(self, device: str, signature: str) -> dict:
        now = self.clock()
        self.tries = [t for t in self.tries if now - t < WINDOW]
        if len(self.tries) >= LIMIT:
            return self._refused("too-many")
        self.tries.append(now)
        nonce, at = self.pending.pop(device, (None, 0.0))   # spent whatever happens next
        if nonce is None or now - at > CHALLENGE_TTL:
            return self._refused("no-challenge")
        key = self.keys.get(device)
        if not key or not verify(key, nonce, signature):
            log.warning("unlock: bad signature from %s", device)
            return self._refused("bad-signature")
        if await self.status() is not True:
            return self._refused("not-locked")
        code = "".join(secrets.choice(ALPHABET) for _ in range(32))
        self._write_code(code)
        # Esc wakes a blanked lock and clears its field (the lock takes it, types nothing); the screen
        # needs a moment to come back, and keys fired all at once get lost: one at a time.
        self.injector.tap(ESCAPE, 0)
        await asyncio.sleep(self.settle)
        for ch in code:
            self.injector.tap(usage(ch), 0)
            await asyncio.sleep(self.key_gap)
        self.injector.tap(ENTER, 0)
        log.info("unlock: code typed for %s", device)
        # Only a lock that really went away counts (PAM takes a moment).
        for _ in range(24):
            await asyncio.sleep(self.confirm_wait)
            if await self.status() is False:
                self.notify("Desbloqueado pelo celular", device.split(".")[0])
                return {"type": "unlock.result", "ok": True}
        (self.run_dir / "unlock-code").unlink(missing_ok=True)   # not taken: never valid again
        log.warning("unlock: the lock did not take the code")
        return self._refused("not-accepted")

    def _write_code(self, code: str):
        self.run_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
        path = self.run_dir / "unlock-code"
        tmp = self.run_dir / "unlock-code.tmp"
        fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w") as f:
            f.write(f"{code} {self.wall() + CODE_TTL}\n")
        os.replace(tmp, path)
