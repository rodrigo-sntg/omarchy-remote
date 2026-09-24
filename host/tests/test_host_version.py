from keypad_host.version import fingerprint


def test_the_fingerprint_follows_the_code(tmp_path):
    pkg = tmp_path / "keypad_host"
    pkg.mkdir()
    (pkg / "server.py").write_text("a = 1\n")
    (pkg / "agent_commands.json").write_text("{}")
    first = fingerprint(pkg)
    assert first == fingerprint(pkg) and len(first) == 10
    (pkg / "server.py").write_text("a = 2\n")
    assert fingerprint(pkg) != first
    (pkg / "__pycache__").mkdir()
    (pkg / "__pycache__" / "x.pyc").write_bytes(b"junk")
    second = fingerprint(pkg)
    (pkg / "__pycache__" / "x.pyc").write_bytes(b"other")
    assert fingerprint(pkg) == second  # compiled files do not count


def test_the_phone_is_told_when_the_code_changed_after_the_start():
    import json
    from keypad_host.protocol import parse
    for t in ("host.get", "host.restart"):
        assert parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": t, "payload": {}})).payload == {}
