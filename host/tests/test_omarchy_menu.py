import asyncio

from keypad_host.omarchy_menu import MenuSource, desktop_apps, parse_desktop

DEFAULT = '''{
  // Root
  "apps": {"icon":"A","label":"Apps","provider":"apps"},
  "system": {"icon":"S","label":"System"},
  "system.lock": {"icon":"L","label":"Lock","action":"omarchy-system-lock"},
  "system.suspend": {"icon":"Z","label":"Suspend","when":"false","action":"systemctl suspend"},
  "toggle": {"icon":"T","label":"Toggle"},
  "toggle.nightlight": {"icon":"N","label":"Nightlight","checked":"true","action":"omarchy-toggle-nightlight"},
  "learn.keybindings": {"icon":"K","label":"Keybindings","action":"omarchy-menu-keybindings","description":"All binds"},
}'''
USER = '''{
  "phone": {"icon":"P","label":"Phone"},
  "phone.pair": {"label":"Pair","action":"omarchy-remote pair"},
  "system.lock": {"label":"Lock now"},
}'''


def source(tmp_path):
    (tmp_path / "default.jsonc").write_text(DEFAULT)
    (tmp_path / "user.jsonc").write_text(USER)

    async def guard(condition):
        return condition == "true"

    return MenuSource(str(tmp_path / "default.jsonc"), str(tmp_path / "user.jsonc"), guard=guard)


def test_menu_is_the_default_plus_the_users_with_guards_applied(tmp_path):
    items = asyncio.run(source(tmp_path).items())
    by_id = {i["id"]: i for i in items}
    assert by_id["system.lock"]["label"] == "Lock now" and by_id["system.lock"]["icon"] == "L"  # user overrides, keeps the rest
    assert "system.suspend" not in by_id  # when: false
    assert by_id["toggle.nightlight"]["checked"] is True
    assert by_id["system"]["parent"] == "" and by_id["system.lock"]["parent"] == "system"
    assert by_id["learn.keybindings"]["parent"] == "learn"
    assert by_id["system.lock"]["kind"] == "action" and by_id["system"]["kind"] == "submenu"
    assert by_id["apps"]["kind"] == "apps"
    assert "action" not in by_id["system.lock"]  # the command stays on the PC
    assert by_id["learn"]["kind"] == "submenu"  # implied parents exist


def test_running_an_item_uses_its_own_action_only(tmp_path):
    menu = source(tmp_path)
    assert asyncio.run(menu.action("system.lock")) == "omarchy-system-lock"
    assert asyncio.run(menu.action("system.suspend")) is None  # hidden by its guard
    assert asyncio.run(menu.action("system")) is None  # a submenu does nothing
    assert asyncio.run(menu.action("nope")) is None


def test_desktop_entries_become_apps(tmp_path):
    (tmp_path / "a.desktop").write_text("[Desktop Entry]\nType=Application\nName=Alacritty\nName[pt_BR]=Terminal\nIcon=alacritty\n")
    (tmp_path / "hidden.desktop").write_text("[Desktop Entry]\nType=Application\nName=Hidden\nNoDisplay=true\n")
    (tmp_path / "link.desktop").write_text("[Desktop Entry]\nType=Link\nName=Web\n")
    (tmp_path / "b.desktop").write_text("[Desktop Entry]\nType=Application\nName=Chromium\n[Desktop Action new]\nName=New window\n")
    assert parse_desktop((tmp_path / "a.desktop").read_text()) == {"name": "Alacritty", "icon": "alacritty"}
    apps = desktop_apps([str(tmp_path)])
    assert apps == [{"id": "a", "name": "Alacritty"}, {"id": "b", "name": "Chromium"}]


def test_menu_round_trip_over_the_control_channel(tmp_path):
    import json

    import pytest
    from aiohttp.test_utils import TestClient, TestServer
    from keypad_host.protocol import ProtocolError, parse
    from keypad_host.server import TOKEN_HEADER, create_app
    from tests.test_server import PHONE, TAILNET, TOKEN
    from tests.test_session import FakeInjector

    for bad in ({"id": "system.lock; rm"}, {"id": ""}, {"id": "app:../x"}):
        with pytest.raises(ProtocolError):
            parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "menu.run", "payload": bad}))

    launched = []

    async def launch(args):
        launched.append(args)

    async def main():
        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, menu=source(tmp_path),
                         apps=lambda: [{"id": "chromium", "name": "Chromium"}], launch=launch)
        async with TestClient(TestServer(app)) as client:
            ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
            sid = (await ws.receive_json())["sessionId"]

            async def send(seq, type_, payload):
                await ws.send_str(json.dumps({"v": 1, "sessionId": sid, "seq": seq, "type": type_, "payload": payload}))

            async def reply(type_):
                while True:
                    m = await asyncio.wait_for(ws.receive_json(), 2)
                    if m["type"] == type_:
                        return m

            await send(1, "menu.get", {})
            menu = await reply("menu")
            await send(2, "menu.run", {"id": "system.lock"})
            ran = await reply("ack")
            await send(3, "menu.run", {"id": "system"})
            refused = await reply("ack")
            await send(4, "menu.run", {"id": "app:chromium"})
            await reply("ack")
            await send(5, "menu.show", {"route": "system"})
            await reply("ack")
            await ws.close()
            return menu, ran, refused

    menu, ran, refused = asyncio.run(main())
    assert {i["id"] for i in menu["items"]} >= {"system", "system.lock", "phone.pair"}
    assert menu["apps"] == [{"id": "chromium", "name": "Chromium"}]
    assert ran["ok"] is True and refused["ok"] is False
    assert launched == [
        ["setsid", "-f", "bash", "-lc", "omarchy-system-lock"],
        ["setsid", "-f", "uwsm-app", "--", "chromium.desktop"],
        ["omarchy-menu", "summon", "system"],
    ]


def test_a_slow_menu_does_not_hold_up_pings(tmp_path):
    import json
    from aiohttp.test_utils import TestClient, TestServer
    from keypad_host.server import TOKEN_HEADER, create_app
    from tests.test_server import PHONE, TAILNET, TOKEN
    from tests.test_session import FakeInjector

    (tmp_path / "default.jsonc").write_text(DEFAULT)
    (tmp_path / "user.jsonc").write_text(USER)

    async def slow_guard(condition):
        await asyncio.sleep(1.5)
        return True

    async def main():
        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois,
                         menu=MenuSource(str(tmp_path / "default.jsonc"), str(tmp_path / "user.jsonc"), guard=slow_guard))
        async with TestClient(TestServer(app)) as client:
            ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
            sid = (await ws.receive_json())["sessionId"]
            await ws.send_str(json.dumps({"v": 1, "sessionId": sid, "seq": 1, "type": "menu.get", "payload": {}}))
            await ws.send_str(json.dumps({"v": 1, "sessionId": sid, "seq": 2, "type": "ping", "payload": {}}))
            started = asyncio.get_running_loop().time()
            while True:
                m = await asyncio.wait_for(ws.receive_json(), 3)
                if m["type"] == "pong":
                    break
            waited = asyncio.get_running_loop().time() - started
            await ws.close()
            return waited

    assert asyncio.run(main()) < 0.5


def test_conditions_are_cached_for_a_moment(tmp_path):
    calls = []
    (tmp_path / "default.jsonc").write_text(DEFAULT)
    (tmp_path / "user.jsonc").write_text(USER)

    async def guard(condition):
        calls.append(condition)
        return condition == "true"

    menu = MenuSource(str(tmp_path / "default.jsonc"), str(tmp_path / "user.jsonc"), guard=guard)

    async def main():
        await menu.items()
        await menu.action("system.lock")
    asyncio.run(main())
    assert len(calls) == 2  # one "when" and one "checked", evaluated once for both calls
