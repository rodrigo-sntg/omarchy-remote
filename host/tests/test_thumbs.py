import asyncio
import base64

import pytest

from keypad_host.thumbs import thumb_command, thumb_message


def test_a_thumbnail_is_a_small_jpeg_of_the_monitor():
    assert thumb_command("HDMI-A-1", 3440, 480) == ["grim", "-o", "HDMI-A-1", "-s", "0.140", "-t", "jpeg", "-q", "55", "-"]
    assert thumb_command("DP-1", 1920, 480)[4] == "0.250"
    assert thumb_command("DP-1", 400, 480)[4] == "1.000"  # never bigger than the monitor


def test_the_message_carries_the_image(tmp_path):
    async def capture(args):
        return b"\xff\xd8jpeg"

    got = asyncio.run(thumb_message("DP-1", {"DP-1": 1920}, 480, capture))
    assert got == {"type": "thumb", "monitor": "DP-1", "jpeg": base64.b64encode(b"\xff\xd8jpeg").decode()}
    assert asyncio.run(thumb_message("HDMI-9", {"DP-1": 1920}, 480, capture)) is None


def test_the_request_is_validated():
    import json
    from keypad_host.protocol import ProtocolError, parse

    def frame(p):
        return json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "thumb.get", "payload": p})
    assert parse(frame({"monitor": "DP-1", "width": 480})).payload == {"monitor": "DP-1", "width": 480}
    with pytest.raises(ProtocolError):
        parse(frame({"monitor": "DP-1", "width": 5000}))
