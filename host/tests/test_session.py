import json

from keypad_host.session import HEARTBEAT_TIMEOUT, Session


class FakeInjector:
    def __init__(self):
        self.events = []

    def tap(self, usage, modifiers):
        self.events.append(("tap", usage, modifiers))
        return True

    def buttons(self, mask):
        self.events.append(("buttons", mask))

    def move(self, dx, dy):
        self.events.append(("move", dx, dy))

    def scroll(self, vertical):
        self.events.append(("scroll", vertical))

    def hscroll(self, horizontal):
        self.events.append(("hscroll", horizontal))

    def release_all(self):
        self.events.append(("release_all",))


def msg(seq, type_, payload=None, session="abc"):
    return json.dumps({"v": 1, "sessionId": session, "seq": seq, "type": type_, "payload": payload or {}})


def new_session(now=0.0):
    injector = FakeInjector()
    return Session("abc", injector, now), injector


def test_input_is_injected_and_critical_messages_are_acked():
    session, injector = new_session()
    assert session.handle(msg(1, "pointer.move", {"dx": 3, "dy": -2}), 0.1) == []
    assert session.handle(msg(2, "keyboard.tap", {"usage": 4, "modifiers": 1}), 0.2) == [{"type": "ack", "seq": 2, "ok": True}]
    assert session.handle(msg(3, "pointer.buttons", {"mask": 1}), 0.3) == [{"type": "ack", "seq": 3, "ok": True}]
    assert session.handle(msg(4, "pointer.scroll", {"vertical": -1}), 0.4) == []
    assert injector.events == [("move", 3, -2), ("tap", 4, 1), ("buttons", 1), ("scroll", -1)]


def test_ping_answers_pong():
    session, _ = new_session()
    assert session.handle(msg(1, "ping"), 1.0) == [{"type": "pong", "seq": 1}]


def test_invalid_message_ends_session_and_releases():
    session, injector = new_session()
    replies = session.handle(msg(1, "keyboard.tap", {"usage": 9999, "modifiers": 0}), 0.1)
    assert replies[0]["type"] == "error"
    assert session.closed
    assert injector.events == [("release_all",)]


def test_an_unknown_type_is_never_executed():
    session, injector = new_session()
    assert session.handle(msg(1, "shell.exec", {"cmd": "ls"}), 0.1) == [{"type": "ack", "seq": 1, "ok": False}]
    assert injector.events == [] and session.last_message is None


def test_wrong_session_or_non_increasing_seq_is_rejected():
    session, injector = new_session()
    session.handle(msg(5, "pointer.move", {"dx": 1, "dy": 1}), 0.1)
    assert session.handle(msg(5, "pointer.move", {"dx": 1, "dy": 1}), 0.2)[0]["type"] == "error"
    assert session.closed

    other, other_injector = new_session()
    assert other.handle(msg(1, "pointer.move", {"dx": 1, "dy": 1}, session="old"), 0.1)[0]["type"] == "error"
    assert ("move", 1, 1) not in other_injector.events


def test_heartbeat_timeout_releases_everything():
    session, injector = new_session(now=0.0)
    session.handle(msg(1, "pointer.buttons", {"mask": 1}), 1.0)
    assert not session.expired(1.0 + HEARTBEAT_TIMEOUT - 0.1)
    assert session.expired(1.0 + HEARTBEAT_TIMEOUT + 0.1)
    session.close()
    assert injector.events[-1] == ("release_all",)
    assert session.closed


def test_closed_session_injects_nothing():
    session, injector = new_session()
    session.close()
    session.handle(msg(1, "keyboard.tap", {"usage": 4, "modifiers": 0}), 0.1)
    assert ("tap", 4, 0) not in injector.events


def test_session_keeps_the_last_valid_message_for_async_handlers():
    session, injector = new_session()
    assert session.last_message is None
    session.handle(msg(1, "agent.focus", {"id": "w1:p1"}), 0.1)
    assert session.last_message.type == "agent.focus" and session.last_message.payload == {"id": "w1:p1"}
    assert injector.events == []  # nothing injected: agent.* is answered elsewhere
    session.handle('{"v": 1', 0.2)  # invalid: the session closes and nothing stale is left behind
    assert session.closed and session.last_message is None


def test_a_newer_phone_message_is_ignored_not_fatal():
    """A newer app may send types this host does not know yet: skip them, keep the session."""
    session, injector = new_session()
    assert session.handle(msg(1, "some.future.thing", {"x": 1}), 0.1) == [{"type": "ack", "seq": 1, "ok": False}]
    assert not session.closed
    assert session.handle(msg(2, "keyboard.tap", {"usage": 4, "modifiers": 0}), 0.2) == [{"type": "ack", "seq": 2, "ok": True}]


def test_an_oversized_message_is_refused_not_fatal():
    """Accented or emoji text makes more bytes than characters: too big is "not done", the session goes on."""
    session, injector = new_session()
    big = msg(1, "clipboard.set", {"text": "😀" * 5000})  # 5000 characters, 20 KB
    assert session.handle(big, 0.1) == [{"type": "ack", "seq": 1, "ok": False}]
    assert not session.closed and session.last_message is None
    assert session.handle(msg(2, "keyboard.tap", {"usage": 4, "modifiers": 0}), 0.2) == [{"type": "ack", "seq": 2, "ok": True}]
