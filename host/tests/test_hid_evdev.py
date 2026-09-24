from evdev import ecodes as e

from keypad_host.hid_evdev import keycode, modifier_keys


def test_letters_digits_and_edit_keys():
    assert keycode(0x04) == e.KEY_A
    assert keycode(0x1D) == e.KEY_Z
    assert keycode(0x1E) == e.KEY_1
    assert keycode(0x27) == e.KEY_0
    assert keycode(0x28) == e.KEY_ENTER
    assert keycode(0x29) == e.KEY_ESC
    assert keycode(0x2A) == e.KEY_BACKSPACE
    assert keycode(0x2B) == e.KEY_TAB
    assert keycode(0x4C) == e.KEY_DELETE
    assert [keycode(u) for u in (0x4F, 0x50, 0x51, 0x52)] == [e.KEY_RIGHT, e.KEY_LEFT, e.KEY_DOWN, e.KEY_UP]


def test_abnt2_positions_are_physical_keys():
    assert keycode(0x31) == e.KEY_BACKSLASH  # ]} on ABNT2
    assert keycode(0x33) == e.KEY_SEMICOLON  # ç
    assert keycode(0x34) == e.KEY_APOSTROPHE  # ~^
    assert keycode(0x35) == e.KEY_GRAVE  # '"
    assert keycode(0x38) == e.KEY_SLASH  # ;:
    assert keycode(0x64) == e.KEY_102ND  # \|


def test_hid_number_is_not_used_as_evdev_code():
    assert keycode(0x04) != 0x04


def test_unknown_usage_is_none():
    assert keycode(0x00) is None
    assert keycode(0xFF) is None


def test_modifier_bits_in_hid_order():
    assert modifier_keys(0x01 | 0x02 | 0x04 | 0x08) == [e.KEY_LEFTCTRL, e.KEY_LEFTSHIFT, e.KEY_LEFTALT, e.KEY_LEFTMETA]
    assert modifier_keys(0x40) == [e.KEY_RIGHTALT]
    assert modifier_keys(0) == []


from keypad_host.hid_evdev import tap_codes


def test_tap_codes_press_modifiers_then_key():
    assert tap_codes(0x06, 0x01) == [e.KEY_LEFTCTRL, e.KEY_C]


def test_modifier_alone_is_a_valid_tap():
    # The ⌘ shortcut: Super pressed and released on its own (opens Omarchy's launcher binds).
    assert tap_codes(0x00, 0x08) == [e.KEY_LEFTMETA]


def test_nothing_to_tap_is_refused():
    assert tap_codes(0x00, 0x00) is None
    assert tap_codes(0xFF, 0x01) is None
