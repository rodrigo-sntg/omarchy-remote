import json

from keypad_host.protocol import parse
from keypad_host.session import Session
from keypad_host.workspaces import summarize
from tests.test_session import FakeInjector, msg


WORKSPACES = [
    {"id": 2, "name": "2", "monitor": "DP-1", "windows": 3},
    {"id": 1, "name": "1", "monitor": "HDMI-A-1", "windows": 1},
    {"id": 5, "name": "5", "monitor": "HDMI-A-1", "windows": 0},
    {"id": -98, "name": "special:scratchpad", "monitor": "DP-1", "windows": 1},
]
MONITORS = [
    {"name": "DP-1", "activeWorkspace": {"id": 2}, "focused": True},
    {"name": "HDMI-A-1", "activeWorkspace": {"id": 5}, "focused": False},
]


def test_summary_lists_regular_workspaces_in_order_with_monitor_and_active_flags():
    assert summarize(WORKSPACES, MONITORS) == [
        {"id": 1, "monitor": "HDMI-A-1", "windows": 1, "active": False, "focused": False},
        {"id": 2, "monitor": "DP-1", "windows": 3, "active": True, "focused": True},
        {"id": 5, "monitor": "HDMI-A-1", "windows": 0, "active": True, "focused": False},
    ]


def test_workspace_messages_are_validated():
    assert parse(msg(1, "workspace.go", {"id": 3})).payload == {"id": 3}
    assert parse(msg(1, "workspace.step", {"direction": -1})).payload == {"direction": -1}
    for bad in ({"id": 0}, {"id": 1000}, {"id": "3"}):
        try:
            parse(msg(1, "workspace.go", bad))
        except ValueError:
            continue
        raise AssertionError(bad)


class FakeDesktop:
    def __init__(self):
        self.calls = []

    def focus_workspace(self, target):
        self.calls.append(target)


def test_session_switches_workspaces_through_the_desktop():
    desktop = FakeDesktop()
    session = Session("abc", FakeInjector(), 0.0, desktop=desktop)
    assert session.handle(msg(1, "workspace.go", {"id": 3}), 0.1) == [{"type": "ack", "seq": 1, "ok": True}]
    session.handle(msg(2, "workspace.step", {"direction": 1}), 0.2)
    session.handle(msg(3, "workspace.step", {"direction": -1}), 0.3)
    assert desktop.calls == ["3", "e+1", "e-1"]


def test_without_desktop_workspace_messages_are_refused_not_crashing():
    session = Session("abc", FakeInjector(), 0.0)
    assert session.handle(msg(1, "workspace.go", {"id": 3}), 0.1) == [{"type": "ack", "seq": 1, "ok": False}]
