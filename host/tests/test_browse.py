import os

import pytest

from keypad_host.browse import folder_listing, open_url_command, roots, within_home


def test_only_web_links_open_in_the_pcs_browser():
    assert open_url_command("https://github.com/x/y?a=1") == ["omarchy-launch-browser", "https://github.com/x/y?a=1"]
    for bad in ("file:///etc/passwd", "javascript:alert(1)", "https://a b", "--private", "http://" + "x" * 3000):
        with pytest.raises(ValueError):
            open_url_command(bad)


def test_a_folder_lists_folders_first_then_the_newest_files(tmp_path):
    (tmp_path / "b").mkdir()
    (tmp_path / "a").mkdir()
    (tmp_path / ".hidden").write_text("x")
    for name, age in (("old.txt", 500), ("new.pdf", 10)):
        (tmp_path / name).write_text("12345")
        os.utime(tmp_path / name, (1_000_000 - age, 1_000_000 - age))
    got = folder_listing(str(tmp_path), tmp_path)
    assert [i["name"] for i in got["items"]] == ["a", "b", "new.pdf", "old.txt"]
    assert got["items"][2] == {"name": "new.pdf", "dir": False, "size": 5, "mtime": 999_990}
    assert got["path"] == str(tmp_path) and got["parent"] is None  # the home has no parent here


def test_nothing_outside_the_home(tmp_path):
    home = tmp_path / "home"
    (home / "docs").mkdir(parents=True)
    os.symlink("/etc", home / "etc-link")
    assert within_home(str(home / "docs"), home) == str(home / "docs")
    assert within_home(str(home / "docs" / ".." / ".."), home) is None
    assert within_home(str(home / "etc-link"), home) is None
    assert within_home("relative", home) is None
    assert folder_listing(str(home / "docs"), home)["parent"] == str(home)


def test_the_usual_folders_are_the_roots(tmp_path):
    for name in ("Downloads", "Documents", "Pictures"):
        (tmp_path / name).mkdir()
    got = roots(tmp_path)
    assert [r["path"] for r in got] == [str(tmp_path / "Downloads"), str(tmp_path / "Documents"), str(tmp_path / "Pictures"), str(tmp_path)]


def test_hidden_files_and_folders_are_never_brought_to_the_phone(tmp_path):
    from keypad_host.browse import fetchable
    (tmp_path / ".ssh").mkdir()
    (tmp_path / ".ssh" / "id_ed25519").write_text("key")
    (tmp_path / "Docs").mkdir()
    (tmp_path / "Docs" / "a.pdf").write_text("pdf")
    (tmp_path / "Docs" / ".env").write_text("secret")
    (tmp_path / "Docs" / "link.pdf").symlink_to(tmp_path / ".ssh" / "id_ed25519")
    assert fetchable(str(tmp_path / "Docs" / "a.pdf"), tmp_path) == str(tmp_path / "Docs" / "a.pdf")
    assert fetchable(str(tmp_path / ".ssh" / "id_ed25519"), tmp_path) is None
    assert fetchable(str(tmp_path / "Docs" / ".env"), tmp_path) is None
    assert fetchable(str(tmp_path / "Docs" / "link.pdf"), tmp_path) is None     # a link into a hidden folder
    assert fetchable("/etc/passwd", tmp_path) is None


def test_an_offer_is_checked_again_when_downloaded(tmp_path):
    from keypad_host.files import Offers
    f = tmp_path / "a.pdf"
    f.write_text("pdf")
    ok = {"v": True}
    offers = Offers()
    offer = offers.add(f, check=lambda p: ok["v"])
    assert offers.get(offer["id"]) == f
    ok["v"] = False                         # swapped for a link to a secret since it was offered
    assert offers.get(offer["id"]) is None
