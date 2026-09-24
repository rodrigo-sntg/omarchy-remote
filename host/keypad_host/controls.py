"""The PC's Control Center for the phone: Omarchy's toggles read as its bar reads them, and set
with Omarchy's own commands (they show its OSD and keep its indicators right)."""
import asyncio
import json
import re
from pathlib import Path

from .now import run_status

INDICATORS = Path.home() / ".local" / "state" / "omarchy" / "indicators"
_PROFILE = re.compile(r"[a-z][a-z0-9-]{0,31}")

TOGGLES = {
    "mute": ["wpctl", "set-mute", "@DEFAULT_AUDIO_SINK@", "toggle"],
    "mic": ["omarchy-audio-input-mute"],
    "output": ["omarchy-audio-output-switch"],
    "nightlight": ["omarchy-toggle-nightlight"],
    "awake": ["omarchy-toggle-idle"],
    "dnd": ["omarchy-toggle-notification-silencing"],
    "record": ["omarchy-capture-screenrecording"],
    "wallpaper": ["omarchy-theme-bg-next"],
    "bluetooth": ["omarchy-bluetooth-power", "toggle"],
    "bar": ["omarchy-toggle-bar"],
    "gaps": ["omarchy-hyprland-window-gaps-toggle"],
    "transparency": ["omarchy-hyprland-window-transparency-toggle"],
}


def control_command(control: str, value) -> list[str]:
    if control == "volume":
        if type(value) is not int or not 0 <= value <= 100:
            raise ValueError("invalid volume")
        return ["wpctl", "set-volume", "-l", "1.0", "@DEFAULT_AUDIO_SINK@", f"{value / 100:.2f}"]
    if control == "power":
        if not isinstance(value, str) or not _PROFILE.fullmatch(value):
            raise ValueError("invalid profile")
        return ["omarchy-powerprofiles-set", "autodetect", value]  # [ac|battery|autodetect] [profile]
    if control in TOGGLES:
        return list(TOGGLES[control])
    raise ValueError("unknown control")


def _volume(out: str) -> tuple[int | None, bool]:
    m = re.search(r"Volume:\s*([0-9.]+)", out)
    return (round(float(m.group(1)) * 100) if m else None), "MUTED" in out


async def read_controls(run=run_status, indicators: Path = INDICATORS) -> dict:
    names = ["wpctl get-volume @DEFAULT_AUDIO_SINK@", "wpctl get-volume @DEFAULT_AUDIO_SOURCE@", "pactl get-default-sink", "pactl -f json list sinks",
             "hyprctl hyprsunset temperature", "omarchy-shell notifications dndState", "pgrep -f ^gpu-screen-recorder", "powerprofilesctl get",
             "omarchy-powerprofiles-list", "omarchy-bluetooth-power is-on"]
    results = dict(zip(names, await asyncio.gather(*(run(n.split(" ")) for n in names))))
    volume, muted = _volume(results[names[0]][1]) if results[names[0]][0] == 0 else (None, False)
    mic_muted = results[names[1]][0] == 0 and "MUTED" in results[names[1]][1]
    output = None
    default = results[names[2]][1].strip()
    try:
        sinks = json.loads(results[names[3]][1]) if results[names[3]][0] == 0 else []
        output = next((s.get("description") for s in sinks if s.get("name") == default), None)
    except (ValueError, AttributeError):
        pass
    temp = re.search(r"\d+", results[names[4]][1]) if results[names[4]][0] == 0 else None
    powers = [l.strip() for l in results[names[8]][1].splitlines() if _PROFILE.fullmatch(l.strip())] if results[names[8]][0] == 0 else []
    return {
        "type": "controls", "volume": volume, "muted": muted, "mic_muted": mic_muted, "output": output,
        "nightlight": bool(temp) and int(temp.group()) < 6000, "awake": (Path(indicators) / "stay-awake").exists(),
        "dnd": results[names[5]][0] == 0 and results[names[5]][1].strip() == "on", "recording": results[names[6]][0] == 0,
        "power": results[names[7]][1].strip() or None if results[names[7]][0] == 0 else None, "powers": powers,
        "bluetooth": results[names[9]][0] == 0,
    }
