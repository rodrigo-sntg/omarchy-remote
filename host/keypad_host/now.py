"""What is going on at the PC, at a glance (docs/PLANO-V2.md §7 "Agora"): media playing, the active
reminder, whether an Omarchy update is waiting. Read through Omarchy's commands and playerctl."""
import asyncio
import json

MEDIA_ACTIONS = {"play-pause": "playPause", "next": "next", "previous": "previous"}


async def run_status(args: list[str]) -> tuple[int, str]:
    """(exit code, stdout); (127, "") when the command is missing."""
    try:
        process = await asyncio.create_subprocess_exec(*args, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
        out, _ = await asyncio.wait_for(process.communicate(), 5)
    except (OSError, asyncio.TimeoutError):
        return 127, ""
    return process.returncode, out.decode(errors="replace")


async def read_now(run=run_status) -> dict:
    media_task = run(["playerctl", "metadata", "--format", "{{status}}\t{{artist}}\t{{title}}\t{{playerName}}"])
    reminder_task = run(["omarchy-reminder", "show", "--json"])
    update_task = run(["omarchy-update-available"])
    (media_code, media_out), (_, reminder_out), (update_code, _) = await asyncio.gather(media_task, reminder_task, update_task)
    media = None
    if media_code == 0 and media_out.strip():
        status, artist, title, player = (media_out.strip().split("\t") + ["", "", ""])[:4]
        if title or artist:
            media = {"playing": status == "Playing", "artist": artist, "title": title, "player": player}
    try:
        reminder_data = json.loads(reminder_out)
        reminder = reminder_data.get("tooltip") if reminder_data.get("active") else None
    except (ValueError, AttributeError):
        reminder = None
    return {"type": "now", "media": media, "reminder": reminder, "update": update_code == 0}


VOLUME_ACTIONS = {"volume-up": "raise", "volume-down": "lower"}
PC_ACTIONS = {"lock": ["omarchy-system-lock"], "suspend": ["systemctl", "suspend"],
              "reboot": ["omarchy-system-reboot"], "shutdown": ["omarchy-system-shutdown"]}


def media_command(action: str) -> list[str]:
    """The same control Omarchy's media and volume keys use (the volume one shows Omarchy's OSD)."""
    if action in VOLUME_ACTIONS:
        return ["omarchy-audio-output-volume", VOLUME_ACTIONS[action]]
    return ["omarchy-shell", "-q", "media", MEDIA_ACTIONS[action]]


def pc_command(action: str) -> list[str]:
    """Lock (Omarchy's own: locks and turns the displays off) or suspend."""
    return list(PC_ACTIONS[action])
