"""The PC's clipboard for the phone (docs/PLANO-V2.md §6): wl-copy / wl-paste, text only."""
import asyncio
import base64
import logging

GET_LIMIT = 64 * 1024  # characters sent to the phone at most

log = logging.getLogger(__name__)


async def run_command(args: list[str], stdin: bytes | None = None) -> str | None:
    """stdout, or None when the command is missing or fails."""
    try:
        process = await asyncio.create_subprocess_exec(
            *args, stdin=asyncio.subprocess.PIPE if stdin is not None else asyncio.subprocess.DEVNULL,
            stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL,
        )
        out, _ = await asyncio.wait_for(process.communicate(stdin), 5)
    except (OSError, asyncio.TimeoutError):
        return None
    return out.decode(errors="replace") if process.returncode == 0 else None


async def set_clipboard(text: str, run=run_command) -> bool:
    return await run(["wl-copy"], stdin=text.encode()) is not None


async def get_clipboard(run=run_command) -> str | None:
    text = await run(["wl-paste", "-n", "-t", "text"])
    return None if text is None else text[:GET_LIMIT]


# wl-paste runs this on every copy (and once for what is already there), the copy on stdin:
# "C <base64>" per copy, "S" for a password manager's (it marks them; they never leave the PC).
_ON_COPY = ('if wl-paste --list-types 2>/dev/null | grep -qx x-kde-passwordManagerHint; then cat >/dev/null; echo S; '
            f'else printf "C "; head -c {GET_LIMIT * 4 + 4} | base64 -w0; echo; fi')
WATCH = ["wl-paste", "--type", "text", "--watch", "sh", "-c", _ON_COPY]


def parse_watch_line(line: str) -> str | None:
    """The text of one copy, or None: a password, an image, too long, or not a copy line."""
    if not line.startswith("C "):
        return None
    try:
        text = base64.b64decode(line[2:].strip(), validate=True).decode()
    except (ValueError, UnicodeDecodeError):
        return None
    return text if text and len(text) <= GET_LIMIT else None


async def watch_lines():
    process = await asyncio.create_subprocess_exec(*WATCH, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL,
                                                   limit=GET_LIMIT * 8)
    try:
        while line := await process.stdout.readline():
            yield line.decode(errors="replace")
    finally:
        if process.returncode is None:
            process.kill()
            await process.wait()


async def watch_clipboard(hub, lines=watch_lines, retry: float = 10.0):
    """Forever: each new copy on the PC goes to the phone (push is a no-op while none is connected).
    What the clipboard held when the watch started, and what the phone itself set, stay put."""
    while True:
        first = True
        try:
            async for line in lines():
                text = parse_watch_line(line)
                if first:
                    first = False
                    hub.clip_last = text if text is not None else hub.clip_last
                    continue
                if text is None or text == hub.clip_last:
                    continue
                hub.clip_last = text
                await hub.push({"type": "clipboard", "text": text, "auto": True})
        except (OSError, ValueError) as error:
            log.warning("clipboard watch: %s", error)
        await asyncio.sleep(retry)  # wl-paste missing or ended: try again later
