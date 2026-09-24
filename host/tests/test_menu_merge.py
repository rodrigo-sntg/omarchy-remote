import importlib.util
from pathlib import Path

import pytest

spec = importlib.util.spec_from_file_location("menu_merge", Path(__file__).parents[2] / "omarchy" / "menu_merge.py")
menu_merge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(menu_merge)
merge, parse_jsonc = menu_merge.merge, menu_merge.parse_jsonc

BLOCK = '''  // omarchy-remote: the Phone menu
  "phone": {"icon":"x","label":"Phone"},
  "phone.pair": {"label":"Pair","action":"omarchy-remote pair"},
  // omarchy-remote end
'''


def test_block_goes_right_after_the_opening_brace():
    text = '{\n  // mine\n  "a": {"action":"omarchy-launch-webapp https://example.com"},\n}\n'
    out = merge(text, BLOCK)
    assert list(parse_jsonc(out)) == ["phone", "phone.pair", "a"]


def test_urls_and_block_comments_do_not_confuse_it():
    text = '/* header { */\n{\n  "a": {"action":"open https://x.y/z"} /* trailing */\n}\n'
    assert list(parse_jsonc(merge(text, BLOCK))) == ["phone", "phone.pair", "a"]


def test_running_again_replaces_the_block_instead_of_adding_it():
    once = merge('{\n}\n', BLOCK)
    newer = BLOCK.replace('"Pair"', '"Pair phone"')
    twice = merge(once, newer)
    assert twice.count("omarchy-remote: the Phone menu") == 1
    assert parse_jsonc(twice)["phone.pair"]["label"] == "Pair phone"


def test_an_empty_file_gets_an_object():
    assert list(parse_jsonc(merge("", BLOCK))) == ["phone", "phone.pair"]


def test_a_file_that_does_not_parse_is_left_alone():
    with pytest.raises(ValueError):
        merge('{ "a": ', BLOCK)


def test_an_entry_on_the_same_line_as_the_brace_is_kept():
    text = '{ "mine": {"label":"Mine"},\n  "other": {"label":"Other"}\n}\n'
    assert list(parse_jsonc(merge(text, BLOCK))) == ["phone", "phone.pair", "mine", "other"]
