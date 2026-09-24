import asyncio
import stat

import pytest

from keypad_host.herdr import Herdr, HerdrError

FAKE = r'''#!/usr/bin/env python3
import json, sys
args = sys.argv[1:]
if args[:2] == ["status", "server"]:
    print(json.dumps({"status": "running", "running": True, "version": "0.8.2"}))
elif args[:2] == ["agent", "list"]:
    print(json.dumps({"id": "cli:agent:list", "result": {"type": "agent_list", "agents": [
        {"pane_id": "w1:p1", "agent": "claude", "agent_status": "blocked", "workspace_id": "w1", "tab_id": "w1:t1",
         "cwd": "/home/u/proj", "terminal_title_stripped": "Fix tests", "state_change_seq": 7}]}}))
elif args[:2] == ["agent", "read"]:
    if args[2] == "nope":
        print(json.dumps({"error": {"code": "agent_not_found", "message": "agent target nope not found"}, "id": "cli:agent:read"}))
    elif args[2] == "busy" and "--source" not in args:
        # herdr only scrolls back through an idle agent; a working one has just its screen.
        sys.stderr.write(json.dumps({"error": {"code": "agent_not_idle", "message": "cannot read 120 lines while busy is working"}}))
        sys.exit(1)
    elif args[2] == "busy":
        sys.stdout.write("on screen now\n" if args[args.index("--source") + 1] == "visible" else "wrong source\n")
    elif "--source" in args and args[args.index("--source") + 1] == "visible" and args[-1] == "ansi":
        sys.stdout.write("ansi screen\n")
    elif "--source" in args and args[args.index("--source") + 1] == "visible":
        sys.stdout.write("line one\nline two\n")
    else:
        # Any other source makes herdr scroll the pane on the PC to capture its history.
        sys.stdout.write("scrolled the pane\n")
elif args[:2] == ["agent", "prompt"] and any(a == "--" or (a.startswith("-") and i >= 3) for i, a in enumerate(args)):
    # herdr's own parser: no "--" separator, and a dash-led word is an option.
    sys.stderr.write("unknown option\n"); sys.exit(2)
elif args[:2] == ["agent", "get"]:
    session = {"agent": "claude", "kind": "id", "source": "herdr:claude", "value": "856c1e70-0a17-485b-9553-e5f1a24ddb1f"}
    print(json.dumps({"id": "cli:agent:get", "result": {"type": "agent_info", "agent": {"pane_id": args[2], "agent": "claude",
        "agent_session": session if args[2] == "w1:p1" else None}}}))
elif args[:2] in (["agent", "send-keys"], ["agent", "prompt"], ["agent", "focus"]):
    print(json.dumps({"id": "cli", "result": {"args": args}}))
else:
    print(json.dumps({"error": {"code": "unknown", "message": "unknown command"}}))
'''


@pytest.fixture
def herdr(tmp_path):
    binary = tmp_path / "herdr"
    binary.write_text(FAKE)
    binary.chmod(binary.stat().st_mode | stat.S_IEXEC)
    return Herdr(binary=str(binary))


def test_agents_come_from_the_result(herdr):
    agents = asyncio.run(herdr.agents())
    assert agents[0]["pane_id"] == "w1:p1" and agents[0]["agent_status"] == "blocked"


def test_available_reads_the_server_status(herdr):
    assert asyncio.run(herdr.available()) is True


def test_reading_never_scrolls_the_pane_on_the_pc(herdr):
    # herdr captures an idle agent's history by scrolling its pane: the PC jumps on every read.
    assert asyncio.run(herdr.read("w1:p1", lines=60)) == "line one\nline two\n"


def test_read_returns_plain_text(herdr):
    assert asyncio.run(herdr.read("w1:p1", lines=2)) == "line one\nline two\n"


def test_a_working_agent_is_read_from_its_screen(herdr):
    assert asyncio.run(herdr.read("busy", lines=120)) == "on screen now\n"


def test_errors_carry_the_herdr_code(herdr):
    with pytest.raises(HerdrError) as error:
        asyncio.run(herdr.read("nope"))
    assert error.value.code == "agent_not_found"
    assert "nope" in str(error.value)


def test_commands_pass_their_arguments_through(herdr):
    result = asyncio.run(herdr.send_keys("w1:p1", ["enter", "y"]))
    assert result["args"] == ["agent", "send-keys", "w1:p1", "enter", "y"]
    result = asyncio.run(herdr.prompt("w1:p1", "continue"))
    assert result["args"] == ["agent", "prompt", "w1:p1", "continue"]


def test_a_prompt_that_looks_like_an_option_stays_text(herdr):
    # Security: "--wait" typed on the phone must reach the agent as text, never as a herdr flag.
    # herdr has no "--" separator (it answers "unknown option"): a leading space keeps it text.
    result = asyncio.run(herdr.prompt("w1:p1", "--wait"))
    assert result["args"][-1] == " --wait"


def test_missing_binary_is_not_available():
    herdr = Herdr(binary="/nonexistent/herdr")
    assert asyncio.run(herdr.available()) is False
    with pytest.raises(HerdrError) as error:
        asyncio.run(herdr.agents())
    assert error.value.code == "not_installed"


def test_the_session_a_pane_runs_comes_from_herdr(herdr):
    assert asyncio.run(herdr.session("w1:p1")) == ("claude", "856c1e70-0a17-485b-9553-e5f1a24ddb1f")
    assert asyncio.run(herdr.session("w1:p2")) is None


def test_the_screen_can_be_read_with_its_colors(herdr):
    assert asyncio.run(herdr.read("w1:p1", 60, ansi=True)) == "ansi screen\n"
