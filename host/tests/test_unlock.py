import asyncio
import base64
import json
import os
import subprocess
import sys
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from keypad_host.unlock import Keys, Unlocker, parse_status

PAM = Path(__file__).resolve().parents[1] / "pam-unlock-code"
LOCKED = json.dumps({"locked": True, "sessionLocked": True, "secure": True})
OPEN = json.dumps({"locked": False, "sessionLocked": False, "secure": False})


def new_key():
    k = ec.generate_private_key(ec.SECP256R1())
    der = k.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    return k, base64.b64encode(der).decode()


HOST = "pc.tail1234.ts.net"


def sign(k, nonce_b64, host=HOST):
    """What the phone signs: a fixed label, the PC's name and the nonce (a signature for one PC is
    worthless on another)."""
    message = b"omarchy-remote-unlock-v1\0" + host.encode() + b"\0" + base64.b64decode(nonce_b64)
    return base64.b64encode(k.sign(message, ec.ECDSA(hashes.SHA256()))).decode()


def test_the_lock_status_is_read():
    assert parse_status(LOCKED) is True
    assert parse_status(OPEN) is False
    assert parse_status("garbage") is None
    assert parse_status(json.dumps({"locked": True, "sessionLocked": False, "secure": False})) is False


class Injector:
    """Records the keys; Enter "unlocks" the fake lock when it accepts (like PAM taking the code)."""

    def __init__(self):
        self.taps = []
        self.on_enter = lambda: None
        self.on_tap = lambda n: None

    def tap(self, usage, mods):
        self.taps.append((usage, mods))
        self.on_tap(len(self.taps))
        if usage == 40:
            self.on_enter()
        return True


def make(tmp_path, locked=True, clock=None):
    state = {"locked": locked}

    async def status():
        return state["locked"]

    keys = Keys(tmp_path / "unlock_keys.json")
    inj = Injector()
    notes = []
    code = tmp_path / "run" / "unlock-code"

    def enter():
        # The fake lock: PAM takes the code (deletes it) and unlocks, unless told to refuse it.
        if state.get("refuse"):
            return
        if not state.get("ignore_code"):
            code.unlink(missing_ok=True)
        state["locked"] = False

    inj.on_enter = enter
    u = Unlocker(inj, keys, tmp_path / "run", host=HOST, status=status, notify=lambda *a: notes.append(a),
                 clock=clock or (lambda: 100.0), settle=0, key_gap=0, confirm_wait=0.01)
    return u, keys, inj, notes, state


def test_a_signed_challenge_types_a_one_time_code_never_a_password(tmp_path):
    u, keys, inj, notes, state = make(tmp_path)
    k, pub = new_key()
    keys.add("pixel.ts.net", pub)
    ch = asyncio.run(u.challenge("pixel.ts.net"))
    assert ch["type"] == "unlock.challenge" and len(base64.b64decode(ch["nonce"])) == 32
    result = asyncio.run(u.respond("pixel.ts.net", sign(k, ch["nonce"])))
    assert result == {"type": "unlock.result", "ok": True}
    assert not (tmp_path / "run" / "unlock-code").exists()   # taken by the lock
    assert inj.taps[0] == (41, 0) and inj.taps[-1] == (40, 0) and len(inj.taps) == 34   # Esc, the code, Enter
    assert notes


def test_what_is_refused(tmp_path):
    u, keys, inj, _, state = make(tmp_path)
    k, pub = new_key()
    other, _ = new_key()
    keys.add("pixel.ts.net", pub)
    # a phone without a key
    assert asyncio.run(u.challenge("stranger.ts.net"))["reason"] == "not-enrolled"
    # a signature by another key
    ch = asyncio.run(u.challenge("pixel.ts.net"))
    assert asyncio.run(u.respond("pixel.ts.net", sign(other, ch["nonce"])))["reason"] == "bad-signature"
    # the challenge is spent even when the signature failed
    assert asyncio.run(u.respond("pixel.ts.net", sign(k, ch["nonce"])))["reason"] == "no-challenge"
    # the PC not locked: no challenge at all
    state["locked"] = False
    assert asyncio.run(u.challenge("pixel.ts.net"))["reason"] == "not-locked"
    assert inj.taps == []


def test_a_challenge_expires(tmp_path):
    t = [100.0]
    u, keys, inj, _, _ = make(tmp_path, clock=lambda: t[0])
    k, pub = new_key()
    keys.add("pixel.ts.net", pub)
    ch = asyncio.run(u.challenge("pixel.ts.net"))
    t[0] += 61
    assert asyncio.run(u.respond("pixel.ts.net", sign(k, ch["nonce"])))["reason"] == "no-challenge"


def test_keys_are_kept_privately_and_can_be_removed(tmp_path):
    keys = Keys(tmp_path / "unlock_keys.json")
    _, pub = new_key()
    keys.add("pixel.ts.net", pub)
    assert Keys(tmp_path / "unlock_keys.json").get("pixel.ts.net") == pub
    assert oct((tmp_path / "unlock_keys.json").stat().st_mode & 0o777) == "0o600"
    assert keys.remove("pixel") and keys.get("pixel.ts.net") is None


def run_pam(tmp_path, typed: str, now=None):
    env = {"PAM_USER": os.environ.get("USER", "x"), "PATH": os.environ["PATH"]}
    args = [sys.executable, str(PAM), "--dir", str(tmp_path / "run")] + (["--now", str(now)] if now else [])
    return subprocess.run(args, input=typed.encode() + b"\0", env=env).returncode


def test_the_pam_check_accepts_the_code_once_and_nothing_else(tmp_path):
    u, keys, inj, _, _ = make(tmp_path)
    code = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"
    u._write_code(code)
    assert run_pam(tmp_path, "my real password") != 0
    assert (tmp_path / "run" / "unlock-code").exists()        # a wrong try does not spend it
    assert run_pam(tmp_path, code) == 0
    assert run_pam(tmp_path, code) != 0                        # single use
    assert not (tmp_path / "run" / "unlock-code").exists()


def test_the_pam_check_refuses_an_old_code(tmp_path):
    u, keys, inj, _, _ = make(tmp_path)
    u._write_code("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6")
    code = (tmp_path / "run" / "unlock-code").read_text().split()[0]
    expires = float((tmp_path / "run" / "unlock-code").read_text().split()[1])
    assert run_pam(tmp_path, code, now=expires + 1) != 0


def test_a_key_is_enrolled_only_with_the_pc_open_and_a_yes_at_the_pc(tmp_path):
    u, keys, _, _, state = make(tmp_path, locked=False)
    _, pub = new_key()

    async def yes(device):
        return True

    async def no(device):
        return False

    assert asyncio.run(u.enroll("pixel.ts.net", pub, confirm=no))["reason"] == "denied"
    assert keys.get("pixel.ts.net") is None
    assert asyncio.run(u.enroll("pixel.ts.net", "AAAA", confirm=yes))["reason"] == "bad-key"
    state["locked"] = True
    assert asyncio.run(u.enroll("pixel.ts.net", pub, confirm=yes))["reason"] == "locked"
    state["locked"] = False
    got = asyncio.run(u.enroll("pixel.ts.net", pub, confirm=yes))
    assert got["ok"] and got["enrolled"] and keys.get("pixel.ts.net") == pub


def test_the_new_messages_are_validated():
    import pytest
    from keypad_host.protocol import ProtocolError, parse

    def frame(t, p):
        return json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": t, "payload": p})

    assert parse(frame("unlock.respond", {"signature": "MEUCIQ=="})).payload == {"signature": "MEUCIQ=="}
    assert parse(frame("unlock.challenge", {})).payload == {}
    with pytest.raises(ProtocolError):
        parse(frame("unlock.respond", {"signature": "not base64!"}))


def test_the_pc_confirmation_is_a_click_on_the_notification(monkeypatch):
    import keypad_host.unlock as unlock
    seen = {}

    class Proc:
        async def communicate(self):
            return b"default\n", b""

    async def fake_exec(*args, **kw):
        seen["args"] = args
        return Proc()

    monkeypatch.setattr(unlock.asyncio, "create_subprocess_exec", fake_exec)
    assert asyncio.run(unlock.confirm_on_pc("pixel.ts.net")) is True
    assert "--action=default=Permitir" in seen["args"]


def test_it_only_counts_when_the_pc_really_opens(tmp_path):
    u, keys, inj, notes, state = make(tmp_path)
    state["refuse"] = True          # the lock stays up after Enter (the code didn't go in right)
    k, pub = new_key()
    keys.add("pixel.ts.net", pub)
    ch = asyncio.run(u.challenge("pixel.ts.net"))
    assert asyncio.run(u.respond("pixel.ts.net", sign(k, ch["nonce"]))) == {"type": "unlock.result", "ok": False, "reason": "not-accepted"}
    assert not (tmp_path / "run" / "unlock-code").exists()     # a code that didn't work is thrown away
    assert notes == []                                         # no "unlocked" notice


def test_a_signature_for_another_pc_is_refused(tmp_path):
    u, keys, inj, _, _ = make(tmp_path)
    k, pub = new_key()
    keys.add("pixel.ts.net", pub)
    ch = asyncio.run(u.challenge("pixel.ts.net"))
    assert asyncio.run(u.respond("pixel.ts.net", sign(k, ch["nonce"], host="other.tail1234.ts.net")))["reason"] == "bad-signature"
    assert inj.taps == []


def test_the_code_is_never_entered_once_the_lock_is_gone(tmp_path):
    u, keys, inj, notes, state = make(tmp_path)
    k, pub = new_key()
    keys.add("pixel.ts.net", pub)
    inj.on_tap = lambda n: state.update(locked=False) if n == 10 else None   # the person typed the password meanwhile
    ch = asyncio.run(u.challenge("pixel.ts.net"))
    assert asyncio.run(u.respond("pixel.ts.net", sign(k, ch["nonce"])))["reason"] == "lock-gone"
    assert (40, 0) not in inj.taps                               # no Enter into whatever window has focus
    assert not (tmp_path / "run" / "unlock-code").exists() and notes == []


def test_only_a_code_the_lock_used_counts(tmp_path):
    u, keys, inj, notes, state = make(tmp_path)
    state["ignore_code"] = True                                   # unlocked, but not by our code
    k, pub = new_key()
    keys.add("pixel.ts.net", pub)
    ch = asyncio.run(u.challenge("pixel.ts.net"))
    assert asyncio.run(u.respond("pixel.ts.net", sign(k, ch["nonce"])))["reason"] == "not-accepted"
    assert not (tmp_path / "run" / "unlock-code").exists() and notes == []


def test_the_phone_cant_click_its_own_enrollment(tmp_path):
    u, keys, _, _, state = make(tmp_path, locked=False)
    _, pub = new_key()
    seen = {}

    async def confirm(device):
        seen["blocked"] = u.input_blocked
        state["locked"] = True                                    # locked while the prompt was up
        return True

    assert asyncio.run(u.enroll("pixel.ts.net", pub, confirm=confirm))["reason"] == "locked"
    assert seen["blocked"] is True and u.input_blocked is False
    assert keys.get("pixel.ts.net") is None
