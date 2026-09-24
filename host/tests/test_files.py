import asyncio
import urllib.parse

from aiohttp.test_utils import TestClient, TestServer

from keypad_host.files import safe_name, unique_path
from keypad_host.server import TOKEN_HEADER, create_app
from tests.test_server import PHONE, TAILNET, TOKEN
from tests.test_session import FakeInjector


def test_names_from_the_phone_cannot_leave_the_folder():
    assert safe_name("foto.jpg") == "foto.jpg"
    assert safe_name("../../.bashrc") == "bashrc"
    assert safe_name("a/b\\c.txt") == "c.txt"
    assert safe_name("rel\x00at\x1fório final.pdf") == "relatório final.pdf"
    assert safe_name("") == "arquivo-do-celular"
    assert safe_name("...") == "arquivo-do-celular"
    assert len(safe_name("x" * 400 + ".png")) <= 200 and safe_name("x" * 400 + ".png").endswith(".png")


def test_an_existing_file_is_never_overwritten(tmp_path):
    (tmp_path / "foto.jpg").write_bytes(b"old")
    (tmp_path / "foto (1).jpg").write_bytes(b"old")
    assert unique_path(tmp_path, "foto.jpg").name == "foto (2).jpg"
    assert unique_path(tmp_path, "novo.txt").name == "novo.txt"


def upload(tmp_path, body: bytes, name: str, headers=None):
    notices = []

    async def main():
        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, downloads=tmp_path,
                         notify=lambda *a: notices.append(a))
        async with TestClient(TestServer(app)) as client:
            h = {TOKEN_HEADER: TOKEN, "X-Keypad-Name": urllib.parse.quote(name)}
            h.update(headers or {})
            response = await client.post("/v1/file", data=body, headers=h)
            return response.status, await response.json() if response.status == 200 else await response.text()

    status, reply = asyncio.run(main())
    return status, reply, notices


def test_a_file_from_the_phone_lands_in_downloads(tmp_path):
    status, reply, notices = upload(tmp_path, b"\x89PNG data" * 1000, "print ção.png")
    assert status == 200 and reply == {"ok": True, "name": "print ção.png", "path": str(tmp_path / "print ção.png")}
    assert (tmp_path / "print ção.png").read_bytes() == b"\x89PNG data" * 1000
    assert not list(tmp_path.glob("*.part"))
    assert notices and "print ção.png" in " ".join(notices[-1])


def test_a_missing_downloads_folder_is_created(tmp_path):
    folder = tmp_path / "gone"
    status, reply, _ = upload(folder, b"log", "erro.txt")
    assert status == 200 and (folder / "erro.txt").read_bytes() == b"log"


def test_uploads_need_the_pairing_code(tmp_path):
    status, _, _ = upload(tmp_path, b"x", "a.txt", headers={TOKEN_HEADER: "wrong"})
    assert status == 403 and not list(tmp_path.iterdir())


def test_offers_expire_and_unknown_ids_are_refused(tmp_path):
    from keypad_host.files import Offers
    f = tmp_path / "a.pdf"
    f.write_bytes(b"pdf")
    clock = [100.0]
    offers = Offers(ttl=600, clock=lambda: clock[0])
    offer = offers.add(f)
    assert offer["name"] == "a.pdf" and offer["size"] == 3 and len(offer["id"]) >= 16
    assert offers.get(offer["id"]) == f
    assert offers.get("nope") is None
    clock[0] += 601
    assert offers.get(offer["id"]) is None


def test_temporary_offers_are_deleted_when_they_expire(tmp_path):
    from keypad_host.files import Offers
    shot = tmp_path / "shot.png"
    shot.write_bytes(b"png")
    clock = [0.0]
    offers = Offers(ttl=600, clock=lambda: clock[0])
    offers.add(shot, temporary=True)
    clock[0] = 700
    offers.sweep()
    assert not shot.exists()


def test_grim_takes_the_desktop_a_monitor_or_a_region_of_it():
    from keypad_host.display import Geometry
    from keypad_host.files import grim_args
    g = Geometry(1920, 0, 3440, 1440)
    assert grim_args("/r/s.png") == ["grim", "/r/s.png"]
    assert grim_args("/r/s.png", "HDMI-A-1", g) == ["grim", "-o", "HDMI-A-1", "/r/s.png"]
    assert grim_args("/r/s.png", "HDMI-A-1", g, (0.5, 0.25, 0.25, 0.5)) == ["grim", "-g", "3640,360 860x720", "/r/s.png"]


def test_a_file_sent_from_the_pc_is_offered_and_downloaded(tmp_path):
    import json
    from keypad_host.control import handle_command
    from keypad_host.server import HUB_KEY

    doc = tmp_path / "relatório.pdf"
    doc.write_bytes(b"%PDF-1.7 " * 500)

    async def main():
        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois)
        async with TestClient(TestServer(app)) as client:
            ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
            await ws.receive_json()
            hub = app[HUB_KEY]
            missing = await handle_command({"cmd": "send-file", "path": str(tmp_path / "nope")}, hub)
            relative = await handle_command({"cmd": "send-file", "path": "relatório.pdf"}, hub)
            sent = await handle_command({"cmd": "send-file", "path": str(doc)}, hub)
            while True:
                offer = await asyncio.wait_for(ws.receive_json(), 2)
                if offer["type"] == "file.offer":
                    break
            got = await client.get(f"/v1/file/{offer['id']}", headers={TOKEN_HEADER: TOKEN})
            body = await got.read()
            refused = await client.get(f"/v1/file/{offer['id']}", headers={TOKEN_HEADER: "wrong"})
            unknown = await client.get("/v1/file/xyz", headers={TOKEN_HEADER: TOKEN})
            await ws.close()
            return missing, relative, sent, offer, got.status, body, refused.status, unknown.status

    missing, relative, sent, offer, status, body, refused, unknown = asyncio.run(main())
    assert missing["ok"] is False and relative["ok"] is False and sent == {"ok": True}
    assert offer["name"] == "relatório.pdf" and offer["size"] == len(body) and offer["kind"] == "file"
    assert status == 200 and body == b"%PDF-1.7 " * 500
    assert refused == 403 and unknown == 404


def test_the_phone_asks_for_a_print_of_a_monitor_region(tmp_path):
    import json
    from tests.test_display_server import CursorHyprland

    taken = []

    async def shoot(args):
        taken.append(args)
        with open(args[-1], "wb") as f:
            f.write(b"\x89PNG fake")
        return True

    async def main():
        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, hyprland=CursorHyprland(),
                         shoot=shoot, shots_dir=tmp_path)
        async with TestClient(TestServer(app)) as client:
            ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: TOKEN})
            sid = (await ws.receive_json())["sessionId"]
            await ws.send_str(json.dumps({"v": 1, "sessionId": sid, "seq": 1, "type": "shot.get",
                                          "payload": {"monitor": "DP-1", "region": {"x": 0, "y": 0, "w": 0.5, "h": 0.5}, "tag": "ocr"}}))
            while True:
                offer = await asyncio.wait_for(ws.receive_json(), 2)
                if offer["type"] == "file.offer":
                    break
            got = await client.get(f"/v1/file/{offer['id']}", headers={TOKEN_HEADER: TOKEN})
            body = await got.read()
            await ws.close()
            return offer, body

    offer, body = asyncio.run(main())
    assert offer["kind"] == "shot" and offer["tag"] == "ocr" and offer["name"].endswith(".png")
    assert body == b"\x89PNG fake"
    assert taken[0][:2] == ["grim", "-g"]


def test_an_agents_inbox_is_in_its_project_and_git_ignores_it(tmp_path):
    from keypad_host.files import agent_inbox
    project = tmp_path / "app"
    (project / ".git/info").mkdir(parents=True)
    inbox = agent_inbox(str(project))
    assert inbox == project / ".omarchy-remote" and inbox.is_dir()
    agent_inbox(str(project))
    assert (project / ".git/info/exclude").read_text().count("/.omarchy-remote/") == 1
    # A worktree (.git is a file) or no repository: just the folder.
    other = tmp_path / "loose"
    other.mkdir()
    assert agent_inbox(str(other)) == other / ".omarchy-remote"
    assert agent_inbox("relative/path") is None
    assert agent_inbox(str(tmp_path / "missing")) is None


def test_an_agents_file_gets_a_name_a_mention_can_hold(tmp_path):
    from keypad_host.files import agent_name
    assert agent_name(tmp_path, "Foto do erro (1).png") == "Foto-do-erro-1.png"
    (tmp_path / "log.txt").write_text("x")
    assert agent_name(tmp_path, "log.txt") == "log-2.txt"


class Herdr:
    def __init__(self, cwd):
        self.cwd = cwd

    async def cwd_of(self, target):
        return self.cwd if target == "w1:p1" else None


def upload_for_agent(tmp_path, herdr, agent):
    async def main():
        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, downloads=tmp_path / "dl", herdr=herdr)
        async with TestClient(TestServer(app)) as client:
            h = {TOKEN_HEADER: TOKEN, "X-Keypad-Name": urllib.parse.quote("tela do app.png"), "X-Keypad-Agent": agent}
            response = await client.post("/v1/file", data=b"img", headers=h)
            return response.status, await response.json()

    return asyncio.run(main())


def test_a_file_for_an_agent_lands_where_it_can_read_it(tmp_path):
    project = tmp_path / "app"
    project.mkdir()
    status, reply = upload_for_agent(tmp_path, Herdr(str(project)), "w1:p1")
    assert status == 200
    assert reply == {"ok": True, "name": "tela-do-app.png", "ref": ".omarchy-remote/tela-do-app.png",
                     "path": str(project / ".omarchy-remote/tela-do-app.png")}
    assert (project / ".omarchy-remote/tela-do-app.png").read_bytes() == b"img"


def test_an_unknown_agent_is_said_not_guessed(tmp_path):
    status, reply = upload_for_agent(tmp_path, Herdr(None), "w1:p1")
    assert status == 409 and reply["ok"] is False
    status, reply = upload_for_agent(tmp_path, Herdr(str(tmp_path)), "../etc")
    assert status == 400
