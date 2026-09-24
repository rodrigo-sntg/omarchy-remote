import asyncio

import pytest

from keypad_host.controls import control_command, read_controls


def fake(outputs):
    calls = []

    async def run(args):
        calls.append(args)
        return outputs.get(" ".join(args), (1, ""))
    return run, calls


def test_the_pcs_toggles_are_read_as_omarchys_bar_reads_them(tmp_path):
    (tmp_path / "stay-awake").write_text("")
    run, _ = fake({
        "wpctl get-volume @DEFAULT_AUDIO_SINK@": (0, "Volume: 0.45 [MUTED]\n"),
        "wpctl get-volume @DEFAULT_AUDIO_SOURCE@": (0, "Volume: 0.94\n"),
        "pactl get-default-sink": (0, "alsa_output.usb-headset\n"),
        "pactl -f json list sinks": (0, '[{"name":"alsa_output.usb-headset","description":"Headset USB"},{"name":"hdmi","description":"HDMI"}]'),
        "hyprctl hyprsunset temperature": (0, "4000\n"),
        "omarchy-shell notifications dndState": (0, "on\n"),
        "pgrep -f ^gpu-screen-recorder": (1, ""),
        "powerprofilesctl get": (0, "balanced\n"),
        "omarchy-powerprofiles-list": (0, "power-saver\nbalanced\nperformance\n"),
        "omarchy-bluetooth-power is-on": (0, ""),
    })
    got = asyncio.run(read_controls(run, indicators=tmp_path))
    assert got == {"type": "controls", "volume": 45, "muted": True, "mic_muted": False, "output": "Headset USB",
                   "nightlight": True, "awake": True, "dnd": True, "recording": False,
                   "power": "balanced", "powers": ["power-saver", "balanced", "performance"], "bluetooth": True}


def test_what_cannot_be_read_is_off_not_a_crash(tmp_path):
    run, _ = fake({})
    got = asyncio.run(read_controls(run, indicators=tmp_path))
    assert got["nightlight"] is False and got["volume"] is None and got["powers"] == [] and got["bluetooth"] is False


def test_each_control_runs_omarchys_own_command():
    assert control_command("volume", 45) == ["wpctl", "set-volume", "-l", "1.0", "@DEFAULT_AUDIO_SINK@", "0.45"]
    assert control_command("mute", None) == ["wpctl", "set-mute", "@DEFAULT_AUDIO_SINK@", "toggle"]
    assert control_command("mic", None) == ["omarchy-audio-input-mute"]
    assert control_command("output", None) == ["omarchy-audio-output-switch"]
    assert control_command("nightlight", None) == ["omarchy-toggle-nightlight"]
    assert control_command("awake", None) == ["omarchy-toggle-idle"]
    assert control_command("dnd", None) == ["omarchy-toggle-notification-silencing"]
    assert control_command("record", None) == ["omarchy-capture-screenrecording"]
    assert control_command("wallpaper", None) == ["omarchy-theme-bg-next"]
    assert control_command("bluetooth", None) == ["omarchy-bluetooth-power", "toggle"]
    assert control_command("bar", None) == ["omarchy-toggle-bar"]
    assert control_command("gaps", None) == ["omarchy-hyprland-window-gaps-toggle"]
    assert control_command("power", "performance") == ["omarchy-powerprofiles-set", "performance"]
    with pytest.raises(ValueError):
        control_command("power", "rm -rf")
    with pytest.raises(ValueError):
        control_command("volume", 300)
    with pytest.raises(ValueError):
        control_command("reboot", None)


def test_the_messages_are_validated():
    import json
    from keypad_host.protocol import ProtocolError, parse

    def frame(t, p):
        return json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": t, "payload": p})
    assert parse(frame("controls.get", {})).payload == {}
    assert parse(frame("pc.control", {"id": "volume", "value": 40})).payload == {"id": "volume", "value": 40}
    assert parse(frame("pc.control", {"id": "dnd"})).payload == {"id": "dnd", "value": None}
    with pytest.raises(ProtocolError):
        parse(frame("pc.control", {"id": "x" * 40}))
