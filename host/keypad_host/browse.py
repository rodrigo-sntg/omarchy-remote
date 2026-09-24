"""The PC seen from the phone: links opened in its browser, and its folders browsed to bring a file
over (the file itself goes by the usual file.offer). Only web links; only folders inside the home."""
import os
import re
from pathlib import Path

URL = re.compile(r"https?://[^\s\x00-\x1f]{1,2000}")
LIMIT = 300
ROOTS = (("Downloads", "Downloads"), ("Documents", "Documentos"), ("Pictures", "Imagens"), ("Desktop", "Área de trabalho"), ("Videos", "Vídeos"))


def open_url_command(url: str) -> list[str]:
    if not isinstance(url, str) or not URL.fullmatch(url):
        raise ValueError("not a web link")
    return ["omarchy-launch-browser", url]


def within_home(path: str, home: Path) -> str | None:
    """The real path when it is the home or inside it (symlinks followed); None otherwise."""
    if not isinstance(path, str) or not path.startswith("/"):
        return None
    real = os.path.realpath(path)
    base = os.path.realpath(str(home))
    return real if real == base or real.startswith(base + os.sep) else None


def folder_listing(path: str, home: Path) -> dict:
    real = within_home(path, home)
    if real is None or not os.path.isdir(real):
        raise ValueError("not a folder in the home")
    items = []
    with os.scandir(real) as entries:
        for e in entries:
            if e.name.startswith("."):
                continue
            try:
                st = e.stat()
                is_dir = e.is_dir()
            except OSError:
                continue
            items.append({"name": e.name, "dir": is_dir, "size": 0 if is_dir else st.st_size, "mtime": int(st.st_mtime)})
    items.sort(key=lambda i: (not i["dir"], i["name"].lower() if i["dir"] else -i["mtime"]))
    base = os.path.realpath(str(home))
    parent = os.path.dirname(real) if real != base else None
    return {"path": real, "parent": parent, "items": items[:LIMIT]}


def roots(home: Path) -> list[dict]:
    home = Path(home)
    out = [{"path": str(home / d), "name": pt} for d, pt in ROOTS if (home / d).is_dir()]
    return out + [{"path": str(home), "name": "Início"}]
