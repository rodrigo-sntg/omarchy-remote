"""Monitor thumbnails for the phone's screen map (on Wi-Fi only; the phone asks every few seconds
while it shows them): a small JPEG from grim, scaled on the PC so little travels."""
import asyncio
import base64

QUALITY = "55"


def thumb_command(monitor: str, monitor_width: int, width: int) -> list[str]:
    scale = min(1.0, width / max(1, monitor_width))
    return ["grim", "-o", monitor, "-s", f"{scale:.3f}", "-t", "jpeg", "-q", QUALITY, "-"]


async def grim_bytes(args: list[str]) -> bytes | None:
    try:
        process = await asyncio.create_subprocess_exec(*args, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
        out, _ = await asyncio.wait_for(process.communicate(), 5)
    except (OSError, asyncio.TimeoutError):
        return None
    return out if process.returncode == 0 and out else None


async def thumb_message(monitor: str, widths: dict, width: int, capture=grim_bytes) -> dict | None:
    """The thumbnail of one of the PC's monitors ([widths]: name -> width), or None."""
    if monitor not in widths:
        return None
    jpeg = await capture(thumb_command(monitor, widths[monitor], width))
    return {"type": "thumb", "monitor": monitor, "jpeg": base64.b64encode(jpeg).decode()} if jpeg else None
