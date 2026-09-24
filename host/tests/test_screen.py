import pytest

from keypad_host.display import DisplayError, Geometry, fit_size, parse_screen_start, parse_touch, real_monitors

MONITORS = [
    {"name": "DP-1", "width": 1920, "height": 1080, "x": 0, "y": 0, "scale": 1.0},
    {"name": "HDMI-A-1", "width": 3440, "height": 1440, "x": 1920, "y": 0, "scale": 1.0},
    {"name": "OMARCHYREMOTE", "width": 2340, "height": 1080, "x": 5360, "y": 0, "scale": 1.5},
]


def test_real_monitors_exclude_the_virtual_one_and_give_logical_geometry():
    monitors = real_monitors(MONITORS)
    assert list(monitors) == ["DP-1", "HDMI-A-1"]
    assert monitors["HDMI-A-1"] == Geometry(1920, 0, 3440, 1440)


def test_fit_size_keeps_aspect_even_and_never_upscales():
    assert fit_size(3440, 1440, 2340, 1080) == (2340, 980)
    assert fit_size(1920, 1080, 2340, 1080) == (1920, 1080)
    assert fit_size(1920, 1080, 1280, 720) == (1280, 720)


def test_screen_start_is_validated_against_existing_monitors():
    names = {"DP-1", "HDMI-A-1"}
    assert parse_screen_start({"type": "screen.start", "monitor": "DP-1", "maxWidth": 2340, "maxHeight": 1080}, names) \
        == ("DP-1", 2340, 1080)
    for bad in (
        {"type": "screen.start", "monitor": "OMARCHYREMOTE", "maxWidth": 2340, "maxHeight": 1080},
        {"type": "screen.start", "monitor": "DP-1; rm -rf /", "maxWidth": 2340, "maxHeight": 1080},
        {"type": "screen.start", "monitor": "DP-1", "maxWidth": 100, "maxHeight": 1080},
        {"type": "screen.start", "monitor": "DP-1", "maxWidth": 2340},
    ):
        with pytest.raises(DisplayError):
            parse_screen_start(bad, names)


def test_touch_supports_right_click_and_scroll():
    assert parse_touch({"type": "touch", "action": "right", "x": 0.5, "y": 0.5}) == ("right", 0.5, 0.5, 0)
    assert parse_touch({"type": "touch", "action": "scroll", "x": 0.5, "y": 0.5, "steps": -3}) == ("scroll", 0.5, 0.5, -3)
    with pytest.raises(DisplayError):
        parse_touch({"type": "touch", "action": "scroll", "x": 0.5, "y": 0.5, "steps": 500})


from keypad_host.display import clamp_to, parse_pointer, to_normalized


def test_pointer_messages_are_validated():
    assert parse_pointer({"type": "pointer", "action": "rel", "dx": 0.01, "dy": -0.02}) == ("rel", 0.01, -0.02, 0)
    assert parse_pointer({"type": "pointer", "action": "click", "button": 2}) == ("click", 0, 0, 2)
    assert parse_pointer({"type": "pointer", "action": "press"}) == ("press", 0, 0, 0)
    assert parse_pointer({"type": "pointer", "action": "release"}) == ("release", 0, 0, 0)
    assert parse_pointer({"type": "pointer", "action": "scroll", "steps": -3}) == ("scroll", 0, 0, -3)
    for bad in (
        {"type": "pointer", "action": "rel", "dx": 2, "dy": 0},
        {"type": "pointer", "action": "click", "button": 4},
        {"type": "pointer", "action": "teleport"},
        {"type": "pointer", "action": "scroll", "steps": 99},
    ):
        with pytest.raises(DisplayError):
            parse_pointer(bad)


def test_cursor_is_kept_inside_the_viewed_monitor():
    g = Geometry(1920, 0, 3440, 1440)
    assert clamp_to(100, 50, g) == (1920, 50)
    assert clamp_to(6000, 2000, g) == (1920 + 3439, 1439)
    assert clamp_to(3000, 700, g) is None  # already inside


def test_cursor_position_is_reported_normalized():
    g = Geometry(1920, 0, 3440, 1440)
    assert to_normalized(1920 + 1720, 720, g) == (0.5, 0.5)


def test_middle_click_and_horizontal_scroll():
    assert parse_pointer({"type": "pointer", "action": "click", "button": 3}) == ("click", 0, 0, 3)
    assert parse_pointer({"type": "pointer", "action": "hscroll", "steps": -2}) == ("hscroll", 0, 0, -2)


from keypad_host.display import parse_screen_options


def test_screen_options_default_to_interactive_full_rate():
    assert parse_screen_options({}) == (False, 60)


def test_preview_options_are_validated():
    assert parse_screen_options({"viewOnly": True, "fps": 8}) == (True, 8)
    for bad in ({"fps": 0}, {"fps": 200}, {"fps": "8"}, {"viewOnly": "yes"}):
        with pytest.raises(DisplayError):
            parse_screen_options(bad)


from keypad_host.display import cursor_message


def test_cursor_inside_the_viewed_monitor_is_reported_normalized():
    monitors = real_monitors(MONITORS)
    assert cursor_message(1920 + 1720, 720, "HDMI-A-1", monitors) == {"type": "cursor", "inside": True, "x": 0.5, "y": 0.5}


def test_cursor_on_another_monitor_names_it_so_the_phone_can_follow():
    monitors = real_monitors(MONITORS)
    assert cursor_message(100, 50, "HDMI-A-1", monitors) == {"type": "cursor", "inside": False, "monitor": "DP-1"}


def test_cursor_nowhere_known_is_just_outside():
    monitors = real_monitors(MONITORS)
    assert cursor_message(9000, 50, "HDMI-A-1", monitors) == {"type": "cursor", "inside": False}
