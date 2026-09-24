import asyncio

from keypad_host.media import parse_media_line, watch_media


def test_a_playerctl_line_is_what_plays():
    assert parse_media_line("Playing\tDemo Beats\tNight Drive\tmpv\n") == {
        "type": "media", "playing": True, "artist": "Demo Beats", "title": "Night Drive", "player": "mpv"}
    assert parse_media_line("Paused\t\tSunrise Loop\tspotify")["playing"] is False
    # nothing playing any more (the player closed): an empty state
    assert parse_media_line("\n") == {"type": "media", "playing": False, "artist": "", "title": "", "player": ""}


class Hub:
    def __init__(self):
        self.pushed, self.media = [], None

    async def push(self, m):
        self.pushed.append(m)
        return True


def test_each_change_goes_to_the_phone_once():
    hub = Hub()

    async def lines():
        for line in ["Playing\tA\tOne\tmpv", "Playing\tA\tOne\tmpv", "Paused\tA\tOne\tmpv", ""]:
            yield line

    async def main():
        await asyncio.wait_for(watch_media(hub, lines, retry=0.01, once=True), 1)

    asyncio.run(main())
    assert [m["playing"] for m in hub.pushed] == [True, False, False]
    assert hub.pushed[-1]["title"] == "" and hub.media == hub.pushed[-1]
