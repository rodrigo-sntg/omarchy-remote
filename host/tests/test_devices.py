import json

from keypad_host.auth import is_allowed
from keypad_host.devices import Devices

TAILNET = "tail1234.ts.net"
MINE = {"Node": {"Name": "pixel-9.tail1234.ts.net."}, "UserProfile": {"LoginName": "me@example.com"}}
SOMEONE = {"Node": {"Name": "their-phone.tail1234.ts.net."}, "UserProfile": {"LoginName": "friend@example.com"}}
SHARED_IN = {"Node": {"Name": "pixel-9.other.ts.net."}, "UserProfile": {"LoginName": "me@example.com"}}
TAGGED = {"Node": {"Name": "server.tail1234.ts.net."}, "UserProfile": {"LoginName": "tagged-devices"}}


def test_without_an_allow_list_only_the_owners_own_devices_get_in():
    assert is_allowed(MINE, set(), TAILNET, owner="me@example.com")
    assert not is_allowed(SOMEONE, set(), TAILNET, owner="me@example.com")
    assert not is_allowed(SHARED_IN, set(), TAILNET, owner="me@example.com")
    assert not is_allowed(TAGGED, set(), TAILNET, owner="tagged-devices")
    assert not is_allowed(MINE, set(), TAILNET, owner=None)


def test_an_allow_list_still_decides_when_given():
    assert not is_allowed(MINE, {"samsung-sm-s928b"}, TAILNET, owner="me@example.com")


def test_devices_are_remembered_and_can_be_revoked(tmp_path):
    path = tmp_path / "devices.json"
    d = Devices(path, clock=lambda: 1000)
    d.seen("pixel-9.tail1234.ts.net")
    d.seen("s24.tail1234.ts.net")
    assert [x["name"] for x in d.list()] == ["pixel-9.tail1234.ts.net", "s24.tail1234.ts.net"]
    assert d.revoke("pixel-9") is True           # a short name works
    assert d.revoked("pixel-9.tail1234.ts.net")
    assert not d.revoked("s24.tail1234.ts.net")
    assert d.revoke("nope") is False
    # kept on disk, private
    again = Devices(path)
    assert again.revoked("pixel-9.tail1234.ts.net")
    assert oct(path.stat().st_mode & 0o777) == "0o600"
    # seen again after being revoked does not un-revoke it
    again.seen("pixel-9.tail1234.ts.net")
    assert again.revoked("pixel-9.tail1234.ts.net")
    # a new pairing code forgives nobody by itself, but "forget" clears the list
    again.forget_all()
    assert again.list() == [] and not again.revoked("pixel-9.tail1234.ts.net")
    json.loads(path.read_text())


def test_a_new_pairing_code_replaces_the_old_one_privately(tmp_path):
    from keypad_host.auth import load_or_create_token, new_token
    path = tmp_path / "token"
    old = load_or_create_token(path)
    fresh = new_token(path)
    assert fresh != old and load_or_create_token(path) == fresh
    assert oct(path.stat().st_mode & 0o777) == "0o600" and len(fresh.replace("-", "")) == 20
