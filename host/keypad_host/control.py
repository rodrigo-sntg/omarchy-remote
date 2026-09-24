"""Local control socket (docs/superpowers/plans/2026-09-23-fase2-omarchy.md, task 1): the Omarchy
menu, the theme hook and the bar widget talk to the running host through the `omarchy-remote` CLI.
One JSON object per line each way; only the same user may connect."""
import asyncio
import json
import os
import socket
import struct
from urllib.parse import urlencode

CLIPBOARD_LIMIT = 64 * 1024
# One request line: 64 KB of text can grow to 6x once JSON-escaped (\uXXXX).
LINE_LIMIT = 8 * CLIPBOARD_LIMIT
OPEN_TARGETS = ("screen", "extra", "terminal")


def pair_uri(host: str, token: str) -> str:
    """What the pairing QR code carries: the host's MagicDNS name and the pairing code."""
    return "keypad://pair?" + urlencode({"host": host, "code": token})


def peer_allowed(peer_uid: int, own_uid: int) -> bool:
    return peer_uid == own_uid


def _no_phone():
    return {"ok": False, "error": "Nenhum celular conectado."}


async def handle_command(cmd, hub) -> dict:
    if not isinstance(cmd, dict) or not isinstance(cmd.get("cmd"), str):
        return {"ok": False, "error": "Comando inválido."}
    match cmd["cmd"]:
        case "status":
            return {"ok": True, "connected": hub.connected, "phone": hub.phone, "battery": hub.battery}
        case "clipboard":
            text = cmd.get("text")
            if not isinstance(text, str) or not text or len(text.encode()) > CLIPBOARD_LIMIT:
                return {"ok": False, "error": "Texto vazio ou maior que 64 KB."}
            return {"ok": True} if await hub.push({"type": "clipboard", "text": text}) else _no_phone()
        case "open":
            what = cmd.get("what")
            if what not in OPEN_TARGETS:
                return {"ok": False, "error": "Destino inválido."}
            return {"ok": True} if await hub.push({"type": "open", "what": what}) else _no_phone()
        case "send-file":
            path = cmd.get("path")
            if not isinstance(path, str) or not os.path.isabs(path) or not os.path.isfile(path):
                return {"ok": False, "error": "Arquivo não encontrado (use o caminho completo)."}
            if not os.access(path, os.R_OK):
                return {"ok": False, "error": "Sem permissão para ler o arquivo."}
            ok = await hub.offer(path, kind="shot" if cmd.get("shot") else "file", temporary=bool(cmd.get("temporary")))
            return {"ok": True} if ok else _no_phone()
        case "ring":
            ok = await hub.push({"type": "phone.ring", "on": not cmd.get("stop")})
            return {"ok": True} if ok else _no_phone()
        case "devices" | "revoke" if getattr(hub, "devices", None) is None:
            return {"ok": False, "error": "Este serviço não guarda a lista de aparelhos."}
        case "devices":
            return {"ok": True, "devices": hub.devices.list(), "current": hub.phone}
        case "revoke":
            name = cmd.get("name")
            if not isinstance(name, str) or not hub.devices.revoke(name):
                return {"ok": False, "error": "Aparelho desconhecido (veja omarchy-remote devices)."}
            if getattr(hub, "unlock_keys", None) is not None:
                hub.unlock_keys.remove(name)   # a revoked phone can't unlock the PC either
            if hub.phone and hub.phone.lower() == name.lower().split(".")[0]:
                hub.kick()
            return {"ok": True}
        case "new-code":
            hub.rotate_token()
            hub.kick()
            return {"ok": True}
        case "theme":
            await hub.push_theme()
            return {"ok": True}
        case "theme-preview":
            if not await hub.push_theme(cmd.get("slug")):
                return {"ok": False, "error": "Tema desconhecido ou nenhum celular conectado."}
            return {"ok": True}
    return {"ok": False, "error": "Comando desconhecido."}


def _peer_uid(writer) -> int:
    sock = writer.get_extra_info("socket")
    creds = sock.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, struct.calcsize("3i"))
    return struct.unpack("3i", creds)[1]


async def serve(path: str, hub):
    """Listen on a Unix socket readable only by this user (the directory is 0700 too)."""
    os.makedirs(os.path.dirname(path), mode=0o700, exist_ok=True)
    if os.path.exists(path):
        os.unlink(path)
    own = os.getuid()

    async def client(reader, writer):
        try:
            if not peer_allowed(_peer_uid(writer), own):
                return
            try:
                line = await asyncio.wait_for(reader.readline(), 5)
                cmd = json.loads(line)
                reply = await handle_command(cmd, hub)
            except ValueError:  # line longer than LINE_LIMIT, or not JSON
                reply = {"ok": False, "error": "Texto vazio ou maior que 64 KB."}
            writer.write(json.dumps(reply, ensure_ascii=False).encode() + b"\n")
            await writer.drain()
        except (asyncio.TimeoutError, ConnectionError):
            pass
        finally:
            writer.close()

    old = os.umask(0o177)
    try:
        server = await asyncio.start_unix_server(client, path=path, limit=LINE_LIMIT)
    finally:
        os.umask(old)
    return server
