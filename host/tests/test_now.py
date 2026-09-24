import asyncio
import json

import pytest

from keypad_host.now import read_now
from keypad_host.protocol import ProtocolError, parse


def runner(outputs):
    calls = []

    async def run(args):
        calls.append(args)
        for prefix, (code, out) in outputs.items():
            if args[:len(prefix)] == list(prefix):
                return code, out
        return 1, ""
    return run, calls


def test_now_has_media_reminders_and_updates():
    run, _ = runner({
        ("playerctl", "metadata"): (0, "Playing\tRadiohead\tReckoner\tspotify\n"),
        ("omarchy-reminder", "show"): (0, '{"count":1,"active":true,"tooltip":"Stand up in 5 min","reminders":[]}'),
        ("omarchy-update-available",): (0, "Update available\n"),
    })
    now = asyncio.run(read_now(run))
    assert now == {"type": "now", "media": {"playing": True, "artist": "Radiohead", "title": "Reckoner", "player": "spotify"},
                   "reminder": "Stand up in 5 min", "update": True}


def test_nothing_playing_no_reminder_up_to_date():
    run, _ = runner({("playerctl", "metadata"): (1, ""), ("omarchy-reminder", "show"): (0, '{"count":0,"active":false,"tooltip":"Set Reminder"}'),
                     ("omarchy-update-available",): (1, "up to date")})
    assert asyncio.run(read_now(run)) == {"type": "now", "media": None, "reminder": None, "update": False}


def test_media_commands_are_a_fixed_list():
    assert parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "media.cmd", "payload": {"action": "next"}})).payload == {"action": "next"}
    with pytest.raises(ProtocolError):
        parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "media.cmd", "payload": {"action": "rm"}}))


def test_volume_and_pc_actions_are_fixed_lists():
    from keypad_host.now import media_command, pc_command
    assert media_command("volume-up") == ["omarchy-audio-output-volume", "raise"]
    assert media_command("volume-down") == ["omarchy-audio-output-volume", "lower"]
    assert pc_command("lock") == ["omarchy-system-lock"]
    assert pc_command("suspend") == ["systemctl", "suspend"]
    # Restart and shut down the way Omarchy does (closing the apps first).
    assert pc_command("reboot") == ["omarchy-system-reboot"]
    assert pc_command("shutdown") == ["omarchy-system-shutdown"]
    ok = parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "pc.act", "payload": {"action": "lock"}}))
    assert ok.payload == {"action": "lock"}
    for bad in ({"action": "poweroff-now"}, {"action": "lock; rm -rf ~"}, {}):
        with pytest.raises(ProtocolError):
            parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "pc.act", "payload": bad}))


def test_pc_actions_run_on_the_pc_and_are_acked():
    from aiohttp.test_utils import TestClient, TestServer
    from keypad_host.server import TOKEN_HEADER, create_app
    from tests.test_server import PHONE, TAILNET, TOKEN
    from tests.test_session import FakeInjector

    launched = []

    async def launch(args):
        launched.append(args)

    async def main():
        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, launch=launch)
        async with TestClient(TestServer(app)) as client:
            ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
            sid = (await ws.receive_json())["sessionId"]
            await ws.send_str(json.dumps({"v": 1, "sessionId": sid, "seq": 1, "type": "pc.act", "payload": {"action": "lock"}}))
            await ws.send_str(json.dumps({"v": 1, "sessionId": sid, "seq": 2, "type": "media.cmd", "payload": {"action": "volume-up"}}))
            acks = []
            while len(acks) < 2:
                m = await asyncio.wait_for(ws.receive_json(), 2)
                if m["type"] == "ack":
                    acks.append(m)
            await ws.close()
            return acks

    acks = asyncio.run(main())
    assert [a["ok"] for a in acks] == [True, True]
    assert launched == [["omarchy-system-lock"], ["omarchy-audio-output-volume", "raise"]]
