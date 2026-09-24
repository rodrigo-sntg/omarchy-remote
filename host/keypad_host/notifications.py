"""The PC's desktop notifications on the phone: every Notify call on the session bus, as seen by
`busctl monitor` (the same user may watch its own bus), becomes a pc.notification message."""
import asyncio
import json
import logging
import re

log = logging.getLogger(__name__)

# The host's own notices (__main__.omarchy_notify via the Hub): the phone already knows these.
OWN_TITLES = {"Celular conectado", "Celular desconectado", "Recebido do celular"}
_MARKUP = re.compile(r"<[^>]{0,200}>")
MONITOR = ["busctl", "--user", "--json=short", "monitor", "org.freedesktop.Notifications"]


def parse_notify(line: str) -> dict | None:
    try:
        message = json.loads(line)
        if message.get("type") != "method_call" or message.get("member") != "Notify":
            return None
        app, _, _, title, body = message["payload"]["data"][:5]
    except (ValueError, KeyError, TypeError, AttributeError):
        return None
    if not isinstance(title, str) or not isinstance(body, str) or title in OWN_TITLES or app == "omarchy-remote":
        return None  # (omarchy-remote: the phone's own notifications, shown on the PC)
    body = _MARKUP.sub("", body).strip()
    return {"type": "pc.notification", "app": str(app)[:60], "title": title.strip()[:120], "body": body[:600]}


async def busctl_lines():
    process = await asyncio.create_subprocess_exec(*MONITOR, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL, limit=1 << 20)
    try:
        while line := await process.stdout.readline():
            yield line.decode(errors="replace")
    finally:
        if process.returncode is None:
            process.kill()
            await process.wait()


async def watch_notifications(hub, lines=busctl_lines, retry: float = 10.0):
    """Forever: forwards each notification while a phone is connected (push is a no-op otherwise)."""
    while True:
        try:
            async for line in lines():
                notification = parse_notify(line)
                if notification is not None:
                    await hub.push(notification)
        except (OSError, ValueError) as error:
            log.warning("notifications: %s", error)
        await asyncio.sleep(retry)  # busctl missing or ended: try again later
