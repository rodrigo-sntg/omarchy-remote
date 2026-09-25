import asyncio
import os

from keypad_host.images import MAX_BYTES, image_for, render

PNG = b"\x89PNG\r\n\x1a\n" + b"0" * 32


def test_an_image_the_agent_names_is_found_from_its_project(tmp_path):
    project = tmp_path / "proj"
    (project / "design").mkdir(parents=True)
    (project / "design/logo.png").write_bytes(PNG)
    assert image_for("design/logo.png", str(project), tmp_path) == str(project / "design/logo.png")
    assert image_for(str(project / "design/logo.png"), None, tmp_path) == str(project / "design/logo.png")
    assert image_for("~/proj/design/logo.png", None, tmp_path) == str(project / "design/logo.png")
    assert image_for("`design/logo.png`", str(project), tmp_path) is None  # the phone sends the path clean


def test_only_images_inside_the_home_and_outside_hidden_folders(tmp_path):
    (tmp_path / ".ssh").mkdir()
    (tmp_path / ".ssh/id.png").write_bytes(PNG)
    (tmp_path / "notes.txt").write_text("x")
    (tmp_path / "big.png").write_bytes(b"0" * (MAX_BYTES + 1))
    outside = tmp_path.parent / "outside.png"
    outside.write_bytes(PNG)
    (tmp_path / "link.png").symlink_to(outside)
    for path in (".ssh/id.png", "notes.txt", "big.png", "link.png", "../outside.png", "missing.png", "/etc/passwd"):
        assert image_for(path, str(tmp_path), tmp_path) is None, path


def test_a_bitmap_goes_as_it_is_and_an_svg_as_png(tmp_path):
    png = tmp_path / "a.png"
    png.write_bytes(PNG)
    assert asyncio.run(render(str(png))) == (PNG, "image/png")
    svg = tmp_path / "a.svg"
    svg.write_text('<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10"/></svg>')
    body, kind = asyncio.run(render(str(svg)))
    assert kind == "image/png" and body.startswith(b"\x89PNG")


def test_the_phone_gets_an_image_its_agent_named(tmp_path):
    from aiohttp.test_utils import TestClient, TestServer

    from keypad_host.server import TOKEN_HEADER, create_app
    from tests.controller import controller
    from tests.test_server import PHONE, TAILNET, TOKEN
    from tests.test_session import FakeInjector

    project = tmp_path / "proj"
    project.mkdir()
    (project / "logo.png").write_bytes(PNG)
    (project / "secret.txt").write_text("no")

    class Herdr:
        async def cwd_of(self, target):
            return str(project) if target == "w1:p1" else None

        async def agents(self):
            return []

    async def main():
        async def whois(_):
            return PHONE

        app = create_app(FakeInjector(), {"samsung-sm-s928b"}, TAILNET, TOKEN, whois, herdr=Herdr(), transcripts_home=tmp_path)
        async with TestClient(TestServer(app)) as client:
            H = {TOKEN_HEADER: TOKEN}
            before = await client.get("/v1/agent-image", params={"agent": "w1:p1", "path": "logo.png"}, headers=H)
            async with controller(client):
                ok = await client.get("/v1/agent-image", params={"agent": "w1:p1", "path": "logo.png"}, headers=H)
                body = await ok.read()
                text = await client.get("/v1/agent-image", params={"agent": "w1:p1", "path": "secret.txt"}, headers=H)
                bad_agent = await client.get("/v1/agent-image", params={"agent": "w1;rm", "path": "logo.png"}, headers=H)
                wrong_code = await client.get("/v1/agent-image", params={"agent": "w1:p1", "path": "logo.png"}, headers={TOKEN_HEADER: "x"})
            return before.status, ok.status, ok.content_type, body, text.status, bad_agent.status, wrong_code.status

    before, status, kind, body, text, bad_agent, wrong_code = asyncio.run(main())
    assert before == 403                 # only for the device holding the session
    assert (status, kind, body) == (200, "image/png", PNG)
    assert text == 404 and bad_agent == 400 and wrong_code == 403
