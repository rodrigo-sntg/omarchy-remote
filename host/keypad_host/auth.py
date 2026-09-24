"""Authorization: the peer must be an allow-listed machine of this tailnet and present the pairing code."""
import asyncio
import base64
import hmac
import json
import os
import secrets
from pathlib import Path


def machine_name(whois: dict | None) -> str | None:
    name = ((whois or {}).get("Node") or {}).get("Name")
    return name.rstrip(".").lower() if name else None


def is_allowed(whois: dict | None, allowed: set[str], tailnet: str, owner: str | None = None) -> bool:
    """With an allow list: the full MagicDNS name must match (a short name only means that machine
    in *this* tailnet). Without one: any machine of this tailnet owned by this PC's owner (never a
    tagged one). The pairing code is checked on top of this either way."""
    name = machine_name(whois)
    if name is None:
        return False
    if not allowed:
        login = ((whois or {}).get("UserProfile") or {}).get("LoginName")
        return bool(owner) and owner != "tagged-devices" and login == owner and name.endswith("." + tailnet)
    full_names = {entry if "." in entry else f"{entry}.{tailnet}" for entry in allowed}
    return name in full_names


def token_matches(presented: str | None, token: str) -> bool:
    return presented is not None and hmac.compare_digest(presented.strip().upper(), token)


def migrate_config(old: Path, new: Path):
    """The project was called android-keypad: its config folder (the pairing code) moves to the new
    name once, so the phone stays paired. A folder already under the new name is never touched."""
    if old.is_dir() and not new.exists():
        new.parent.mkdir(parents=True, exist_ok=True)
        old.rename(new)


def _fresh_code() -> str:
    raw = base64.b32encode(secrets.token_bytes(15)).decode()  # 20 characters, 120 bits
    return "-".join(raw[i:i + 4] for i in range(0, 20, 4))


def load_or_create_token(path: Path) -> str:
    """Pairing code shown once on the PC and typed once in the app; stored readable only by the user."""
    if path.exists():
        return path.read_text().strip()
    token = _fresh_code()
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as file:
        file.write(token + "\n")
    return token


def new_token(path: Path) -> str:
    """A new pairing code in place of the old one (every phone pairs again); written atomically, 0600."""
    token = _fresh_code()
    tmp = Path(str(path) + ".new")
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as file:
        file.write(token + "\n")
    os.replace(tmp, path)
    return token


async def tailscale_whois(address: str) -> dict | None:
    """`tailscale whois` for "ip:port" of the connecting peer; None if it is not a tailnet peer."""
    process = await asyncio.create_subprocess_exec(
        "tailscale", "whois", "--json", address,
        stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL,
    )
    try:
        stdout, _ = await asyncio.wait_for(process.communicate(), timeout=3)
    except TimeoutError:
        process.kill()
        await process.wait()
        return None
    if process.returncode != 0:
        return None
    try:
        return json.loads(stdout)
    except json.JSONDecodeError:
        return None


# --dev-loopback: a phone without Tailscale reaches the host through `adb reverse`, which arrives
# on 127.0.0.1. Only such peers get this fixed identity; the pairing code is still required.
LOOPBACK_MACHINE = "adb-dev.localhost"


async def loopback_whois(address: str) -> dict | None:
    host = address.rsplit(":", 1)[0]
    return {"Node": {"Name": LOOPBACK_MACHINE + "."}} if host == "127.0.0.1" else None
