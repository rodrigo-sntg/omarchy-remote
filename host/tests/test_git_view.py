import asyncio
import subprocess

from keypad_host.git_view import file_diff, git_summary


import pytest


@pytest.fixture(autouse=True)
def home_is_tmp(tmp_path, monkeypatch):
    """The test repositories live in tmp_path: that is the "home" diffs may be read in."""
    from keypad_host import git_view
    monkeypatch.setattr(git_view, "HOME", tmp_path)


def repo(tmp_path):
    def git(*args):
        subprocess.run(["git", "-C", str(tmp_path), *args], check=True, capture_output=True)
    git("init", "-q", "-b", "main")
    git("config", "user.email", "t@t")
    git("config", "user.name", "t")
    (tmp_path / "a.txt").write_text("um\ndois\n")
    git("add", ".")
    git("commit", "-qm", "primeiro")
    (tmp_path / "a.txt").write_text("um\nDOIS\ntrês\n")
    (tmp_path / "novo.kt").write_text("x")
    return tmp_path


def test_the_projects_git_at_a_glance(tmp_path):
    got = asyncio.run(git_summary(str(repo(tmp_path))))
    assert got["branch"] == "main"
    assert {(f["path"], f["state"]) for f in got["files"]} == {("a.txt", "M"), ("novo.kt", "?")}
    a = next(f for f in got["files"] if f["path"] == "a.txt")
    assert (a["added"], a["removed"]) == (2, 1)
    assert got["commits"][0]["subject"] == "primeiro"


def test_a_files_diff(tmp_path):
    diff = asyncio.run(file_diff(str(repo(tmp_path)), "a.txt"))
    assert "-dois" in diff and "+DOIS" in diff and "+três" in diff
    assert asyncio.run(file_diff(str(tmp_path), "../etc/passwd")) == ""


def test_not_a_repository_is_said(tmp_path):
    assert asyncio.run(git_summary(str(tmp_path))) == {"repo": False}


def test_the_phone_asks_for_an_agents_git(tmp_path):
    from keypad_host.agents import AgentCommands
    from keypad_host.protocol import Message
    project = repo(tmp_path)

    class Herdr:
        async def cwd_of(self, target):
            return str(project)

    commands = AgentCommands(Herdr())
    summary = asyncio.run(commands.handle(Message("s", 1, "agent.git", {"id": "w1:p1"})))[0]
    assert summary["type"] == "agent.git" and summary["branch"] == "main" and len(summary["files"]) == 2
    diff = asyncio.run(commands.handle(Message("s", 2, "agent.gitdiff", {"id": "w1:p1", "path": "a.txt"})))[0]
    assert diff["path"] == "a.txt" and "+DOIS" in diff["diff"]
