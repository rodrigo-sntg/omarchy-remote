"""Small hardenings from the security review."""
import asyncio
import os
from pathlib import Path


def test_a_non_ascii_pairing_code_is_simply_wrong():
    from keypad_host.auth import token_matches
    assert token_matches("ÁBCD-EFGH-IJKL-MNOP-QRST", "ABCD-EFGH-IJKL-MNOP-QRST") is False


def test_the_agent_inbox_is_never_a_link(tmp_path):
    from keypad_host.files import agent_inbox
    project = tmp_path / "repo"
    project.mkdir()
    elsewhere = tmp_path / "autostart"
    elsewhere.mkdir()
    (project / ".omarchy-remote").symlink_to(elsewhere)      # shipped in a cloned repository
    assert agent_inbox(str(project)) is None
    fine = tmp_path / "ok"
    fine.mkdir()
    assert agent_inbox(str(fine)) == fine / ".omarchy-remote"


def test_a_diff_is_only_read_inside_the_home(tmp_path, monkeypatch):
    from keypad_host import git_view
    monkeypatch.setattr(git_view, "HOME", tmp_path / "home")
    (tmp_path / "home").mkdir()
    outside = tmp_path / "outside"
    outside.mkdir()
    (outside / "secret.txt").write_text("x")
    assert asyncio.run(git_view.file_diff(str(outside), "secret.txt")) == ""


def test_a_project_label_never_starts_with_a_dash():
    from keypad_host.agents import tab_label
    assert tab_label("/home/u/--help") == "help"
    assert tab_label("/home/u/app") == "app"
    assert tab_label("/") == "home"
