from keypad_host.herdr_keys import ACTIONS, encode, load, sequences


def test_prefix_bindings_become_the_prefix_then_the_key():
    assert encode("prefix+x", b"\x00") == b"\x00x"
    assert encode("prefix+minus", b"\x02") == b"\x02-"
    assert encode("prefix+shift+x", b"\x00") == b"\x00X"
    assert encode("prefix+tab", b"\x00") == b"\x00\t"
    assert encode("prefix+shift+tab", b"\x00") == b"\x00\x1b[Z"
    assert encode("prefix+;", b"\x00") == b"\x00;"


def test_plain_bindings_are_terminal_bytes():
    assert encode("ctrl+space", None) == b"\x00"
    assert encode("ctrl+b", None) == b"\x02"
    assert encode("alt+enter", None) == b"\x1b\r"
    assert encode("alt+esc", None) == b"\x1b\x1b"
    assert encode("ctrl+alt+left", None) == b"\x1b[1;7D"
    assert encode("alt+shift+right", None) == b"\x1b[1;4C"
    assert encode("up", None) == b"\x1b[A"


def test_what_a_terminal_cannot_send_is_none():
    assert encode("prefix+1..9", b"\x00") is None
    assert encode("", b"\x00") is None
    assert encode("prefix+x", None) is None  # no prefix to send
    assert encode("super+x", None) is None
    assert encode("ctrl+shift+alt+up", None) == b"\x1b[1;8A"
    assert encode("ctrl+1", None) is None


def test_defaults_without_a_config(tmp_path):
    keys = sequences(load(tmp_path / "missing.toml"))
    assert keys["prefix"] == b"\x02"
    assert keys["close_pane"] == b"\x02x"
    assert keys["split_horizontal"] == b"\x02-"
    assert keys["split_vertical"] == b"\x02v"
    assert keys["new_tab"] == b"\x02c"
    assert keys["focus_pane_left"] == b"\x02h"
    assert set(keys) <= set(ACTIONS)


def test_the_persons_config_wins_and_the_first_sendable_binding_is_used(tmp_path):
    config = tmp_path / "config.toml"
    config.write_text(
        '[keys]\nprefix = "ctrl+space"\n'
        'split_horizontal = ["prefix+h", "alt+enter"]\n'
        'close_pane = ["prefix+1..9", "alt+esc"]\n'
        'cycle_pane_next = ""\n'
        '[ui]\naccent = "blue"\n'
    )
    keys = sequences(load(config))
    assert keys["prefix"] == b"\x00"
    assert keys["split_horizontal"] == b"\x00h"
    assert keys["close_pane"] == b"\x1b\x1b"
    assert "cycle_pane_next" not in keys  # unbound on purpose
    assert keys["zoom"] == b"\x00z"  # not in the config: herdr's default, with this prefix


def test_a_broken_config_falls_back_to_the_defaults(tmp_path):
    config = tmp_path / "config.toml"
    config.write_text("[keys\nprefix = ")
    assert sequences(load(config))["prefix"] == b"\x02"
