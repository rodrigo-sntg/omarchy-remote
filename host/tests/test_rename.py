"""The project was android-keypad: an old install keeps its pairing code and loses its old menu block."""
import sys
from pathlib import Path

from keypad_host.auth import migrate_config

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "omarchy"))
from menu_merge import merge  # noqa: E402


def test_the_old_config_folder_moves_to_the_new_name(tmp_path):
    old, new = tmp_path / "android-keypad", tmp_path / "omarchy-remote"
    old.mkdir()
    (old / "token").write_text("ABCD-EFGH\n")
    migrate_config(old, new)
    assert (new / "token").read_text() == "ABCD-EFGH\n" and not old.exists()


def test_a_new_config_folder_is_never_overwritten(tmp_path):
    old, new = tmp_path / "android-keypad", tmp_path / "omarchy-remote"
    old.mkdir(); new.mkdir()
    (old / "token").write_text("OLD\n")
    (new / "token").write_text("NEW\n")
    migrate_config(old, new)
    assert (new / "token").read_text() == "NEW\n" and (old / "token").exists()


def test_nothing_to_migrate(tmp_path):
    migrate_config(tmp_path / "android-keypad", tmp_path / "omarchy-remote")
    assert not (tmp_path / "omarchy-remote").exists()


def test_the_old_menu_block_is_replaced_by_the_new_one():
    old = ('{\n  // android-keypad: the Phone menu (installed by android-keypad/omarchy/install.sh)\n'
           '  "phone.pair": {"label":"Pair","action":"android-keypad pair"},\n  // android-keypad end\n'
           '  "mine": {"label":"x"}\n}\n')
    block = ('  // omarchy-remote: the Phone menu (installed by omarchy-remote/omarchy/install.sh)\n'
             '  "phone.pair": {"label":"Pair","action":"omarchy-remote pair"},\n  // omarchy-remote end\n')
    merged = merge(old, block)
    assert "android-keypad" not in merged and merged.count("omarchy-remote end") == 1 and '"mine"' in merged


def test_the_block_can_be_taken_out_again():
    from menu_merge import remove
    text = '{\n  // omarchy-remote: the Phone menu (installed)\n  "phone": {"label":"Phone"},\n  // omarchy-remote end\n  "mine": {"label":"x"}\n}\n'
    out = remove(text)
    assert "omarchy-remote" not in out and '"mine"' in out and '"phone"' not in out
    assert remove('{\n  "mine": {}\n}\n') == '{\n  "mine": {}\n}\n'
