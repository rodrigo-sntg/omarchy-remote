"""What plays on the PC, pushed to the phone as it changes (its lock screen shows it with controls)."""
import asyncio
import logging

log = logging.getLogger(__name__)
FORMAT = "{{status}}\t{{artist}}\t{{title}}\t{{playerName}}"


def parse_media_line(line: str) -> dict:
    status, artist, title, player = (line.rstrip("\n").split("\t") + ["", "", "", ""])[:4]
    return {"type": "media", "playing": status == "Playing", "artist": artist, "title": title, "player": player}


async def follow_lines():
    process = await asyncio.create_subprocess_exec(
        "playerctl", "--follow", "metadata", "--format", FORMAT,
        stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
    try:
        while line := await process.stdout.readline():
            yield line.decode(errors="replace")
    finally:
        if process.returncode is None:
            process.kill()


async def watch_media(hub, lines=follow_lines, retry: float = 10.0, once: bool = False):
    """Forever: each change of what plays goes to the phone (and is kept for a phone that connects later)."""
    while True:
        try:
            async for line in lines():
                state = parse_media_line(line)
                if state == hub.media:
                    continue
                hub.media = state
                await hub.push(state)
        except (OSError, ValueError) as error:
            log.warning("media watch: %s", error)
        if once:
            return
        await asyncio.sleep(retry)  # playerctl missing or ended: try again later
