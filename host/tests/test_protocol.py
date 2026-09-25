import json

import pytest

from keypad_host.protocol import MAX_FRAME, ProtocolError, parse


def frame(type_, payload=None, seq=1, session="s1"):
    return json.dumps({"v": 1, "sessionId": session, "seq": seq, "type": type_, "payload": payload or {}})


def test_valid_messages():
    assert parse(frame("pointer.move", {"dx": 12, "dy": -3})).payload == {"dx": 12, "dy": -3}
    assert parse(frame("pointer.buttons", {"mask": 1})).type == "pointer.buttons"
    assert parse(frame("pointer.scroll", {"vertical": -1})).payload["vertical"] == -1
    m = parse(frame("keyboard.tap", {"usage": 6, "modifiers": 1}, seq=4))
    assert (m.type, m.seq, m.session_id) == ("keyboard.tap", 4, "s1")
    assert parse(frame("input.releaseAll")).type == "input.releaseAll"
    assert parse(frame("ping")).type == "ping"


@pytest.mark.parametrize(
    "raw",
    [
        "not json",
        json.dumps([1, 2]),
        frame("shell.exec", {"cmd": "rm -rf /"}),
        frame("pointer.move", {"dx": "12", "dy": 0}),
        frame("pointer.move", {"dx": 1.5, "dy": 0}),
        frame("pointer.move", {"dx": True, "dy": 0}),
        frame("pointer.move", {"dx": 100000, "dy": 0}),
        frame("pointer.buttons", {"mask": 8}),
        frame("pointer.scroll", {"vertical": 1000}),
        frame("keyboard.tap", {"usage": 256, "modifiers": 0}),
        frame("keyboard.tap", {"usage": 4, "modifiers": -1}),
        frame("keyboard.tap", {"usage": 4}),
        json.dumps({"v": 2, "sessionId": "s1", "seq": 1, "type": "ping", "payload": {}}),
        json.dumps({"v": 1, "sessionId": "s1", "seq": -1, "type": "ping", "payload": {}}),
    ],
)
def test_invalid_messages_are_rejected(raw):
    with pytest.raises(ProtocolError):
        parse(raw)


def test_oversized_frame_is_rejected():
    with pytest.raises(ProtocolError):
        parse("x" * (MAX_FRAME + 1))


def test_agent_commands_are_parsed():
    read = parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "agent.read", "payload": {"id": "w12:p3", "lines": 40}}))
    assert read.payload == {"id": "w12:p3", "lines": 40, "ansi": False}
    keys = parse(json.dumps({"v": 1, "sessionId": "s", "seq": 2, "type": "agent.keys", "payload": {"id": "reviewer", "keys": ["enter", "y"]}}))
    assert keys.payload == {"id": "reviewer", "keys": ["enter", "y"]}
    prompt = parse(json.dumps({"v": 1, "sessionId": "s", "seq": 3, "type": "agent.prompt", "payload": {"id": "w1:p1", "text": "continue\nplease"}}))
    assert prompt.payload["text"] == "continue\nplease"


def test_agent_targets_are_validated():
    for payload in (
        {"id": "w1:p1; rm -rf ~", "lines": 40},
        {"id": "", "lines": 40},
        {"id": "W1:P1", "lines": 40},
        {"id": "w1:p1", "lines": 0},
        {"id": "w1:p1", "lines": 201},
    ):
        with pytest.raises(ProtocolError):
            parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "agent.read", "payload": payload}))
    for type_, payload in (
        ("agent.keys", {"id": "w1:p1", "keys": ["rm"]}),
        ("agent.keys", {"id": "w1:p1", "keys": []}),
        ("agent.keys", {"id": "w1:p1", "keys": "enter"}),
        ("agent.prompt", {"id": "w1:p1", "text": ""}),
        ("agent.prompt", {"id": "w1:p1", "text": "x" * 3001}),
        ("agent.prompt", {"id": "w1:p1", "text": "bell\x07"}),
    ):
        with pytest.raises(ProtocolError):
            parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": type_, "payload": payload}))


def test_real_herdr_ids_are_accepted_and_trailing_newlines_are_not():
    # herdr numbers workspaces past 9 with letters (w5, wA, wD); seen on the user's PC.
    for target in ("wD:p1", "wA:p12", "w5:p3"):
        assert parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "agent.focus", "payload": {"id": target}})).payload["id"] == target
    for target in ("w1:p1\n", "reviewer\n", "wD:p1 "):
        with pytest.raises(ProtocolError):
            parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "agent.focus", "payload": {"id": target}}))


def test_numbered_choices_can_be_answered():
    # Claude Code's permission prompts are numbered (1. Yes, 2. Yes and don't ask, 3. No).
    keys = parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "agent.keys", "payload": {"id": "wD:p1", "keys": ["1"]}}))
    assert keys.payload["keys"] == ["1"]
    with pytest.raises(ProtocolError):
        parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "agent.keys", "payload": {"id": "wD:p1", "keys": ["10"]}}))


def test_the_agents_history_messages_are_validated():
    from keypad_host.protocol import ProtocolError, parse
    def frame(t, p):
        import json
        return json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": t, "payload": p})
    assert parse(frame("agent.history", {"id": "w1:p1", "limit": 30})).payload == {"id": "w1:p1", "before": None, "limit": 30}
    assert parse(frame("agent.history", {"id": "w1:p1", "before": 1234, "limit": 30})).payload["before"] == 1234
    assert parse(frame("agent.follow", {"id": "w1:p1", "after": 99})).payload == {"id": "w1:p1", "after": 99}
    assert parse(frame("agent.unfollow", {})).payload == {}
    assert parse(frame("agent.output", {"id": "w1:p1", "at": 5, "call": "toolu_01AbC-9"})).payload == {"id": "w1:p1", "at": 5, "call": "toolu_01AbC-9"}
    for t, p in (("agent.history", {"id": "w1:p1", "before": -1, "limit": 30}), ("agent.history", {"id": "w1:p1", "limit": 500}),
                 ("agent.follow", {"id": "w1:p1"}), ("agent.output", {"id": "w1:p1", "at": 5, "call": "../x"})):
        with pytest.raises(ProtocolError):
            parse(frame(t, p))


def test_the_slash_menu_is_asked_for_an_agent():
    import json
    from keypad_host.protocol import parse
    assert parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "agent.commands", "payload": {"id": "w1:p1"}})).payload == {"id": "w1:p1"}


def test_a_screen_read_may_ask_for_its_colors():
    import json
    from keypad_host.protocol import parse
    def frame(p):
        return json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "agent.read", "payload": p})
    assert parse(frame({"id": "w1:p1", "lines": 60})).payload == {"id": "w1:p1", "lines": 60, "ansi": False}
    assert parse(frame({"id": "w1:p1", "lines": 60, "ansi": True})).payload["ansi"] is True


def test_a_session_to_reopen_is_named_by_its_uuid_only():
    ok = parse(frame("agent.resume", {"kind": "codex", "session": "01a0ce50-138e-7bb0-adf1-efe275fd8b69"}))
    assert ok.payload == {"kind": "codex", "session": "01a0ce50-138e-7bb0-adf1-efe275fd8b69"}
    assert parse(frame("agent.sessions", {})).payload == {}
    for bad in ({"kind": "claude", "session": "../../etc/passwd"}, {"kind": "claude", "session": "01A0CE50-138E-7BB0-ADF1-EFE275FD8B69"},
                {"kind": "pi", "session": "01a0ce50-138e-7bb0-adf1-efe275fd8b69"}, {"kind": "claude"}):
        with pytest.raises(ProtocolError):
            parse(frame("agent.resume", bad))
