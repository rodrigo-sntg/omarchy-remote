"""Which code the service runs: a fingerprint of its files taken at start, compared with the files
on disk now — different means new code is waiting for a restart (the phone offers it)."""
import hashlib
from pathlib import Path

PACKAGE = Path(__file__).parent
RESTART_EXIT = 75  # exits with this after a restart asked from the phone: systemd (Restart=on-failure) starts it again


def fingerprint(package: Path = PACKAGE) -> str:
    digest = hashlib.sha1()
    for f in sorted(Path(package).glob("*.py")) + sorted(Path(package).glob("*.json")):
        digest.update(f.name.encode())
        digest.update(f.read_bytes())
    return digest.hexdigest()[:10]


RUNNING = fingerprint()
