import asyncio
import pytest

from keypad_host.display import DisplayError, Geometry, parse_start, parse_touch, to_global


def test_start_request_is_validated():
    assert parse_start({"type": "display.start", "width": 2340, "height": 1080, "scale": 1.5}) == (2340, 1080, 1.5)
    for bad in (
        {"type": "display.start", "width": 100, "height": 1080, "scale": 1.5},
        {"type": "display.start", "width": 2340, "height": 99999, "scale": 1.5},
        {"type": "display.start", "width": 2340, "height": 1080, "scale": 5},
        {"type": "display.start", "width": "2340", "height": 1080, "scale": 1},
        {"type": "display.start", "width": 2341, "height": 1080, "scale": 1},  # encoder needs even sizes
        {"type": "other"},
    ):
        with pytest.raises(DisplayError):
            parse_start(bad)


def test_touch_is_validated():
    assert parse_touch({"type": "touch", "action": "down", "x": 0.25, "y": 0.5}) == ("down", 0.25, 0.5, 0)
    for bad in (
        {"type": "touch", "action": "tap", "x": 0.1, "y": 0.1},
        {"type": "touch", "action": "move", "x": 1.5, "y": 0.1},
        {"type": "touch", "action": "move", "x": -0.1, "y": 0.1},
        {"type": "touch", "action": "move", "x": "0.1", "y": 0.1},
        {"type": "touch", "action": "move", "x": True, "y": 0.1},
    ):
        with pytest.raises(DisplayError):
            parse_touch(bad)


def test_normalized_point_maps_to_logical_global_coordinates():
    # 2340x1080 at scale 1.5 -> logical 1560x720, placed right of a 1920 px monitor.
    g = Geometry(x=1920, y=0, width=1560, height=720)
    assert to_global(0.0, 0.0, g) == (1920, 0)
    assert to_global(0.5, 0.5, g) == (2700, 360)


def test_right_and_bottom_edges_stay_inside_the_display():
    g = Geometry(x=1920, y=0, width=1560, height=720)
    assert to_global(1.0, 1.0, g) == (1920 + 1559, 719)


def test_negative_origin():
    g = Geometry(x=-1560, y=-720, width=1560, height=720)
    assert to_global(0.0, 0.0, g) == (-1560, -720)
    assert to_global(1.0, 1.0, g) == (-1, -1)


def test_a_capture_that_ignores_sigterm_still_stops():
    # wf-recorder catches SIGTERM to finish its output; with nobody reading it, it never would.
    import sys
    import time as _time
    from keypad_host.display import stop_capture

    async def main():
        process = await asyncio.create_subprocess_exec(
            sys.executable, "-c", "import signal, time; signal.signal(signal.SIGTERM, signal.SIG_IGN); print('up', flush=True); time.sleep(60)",
            stdout=asyncio.subprocess.PIPE)
        await process.stdout.readline()
        started = _time.monotonic()
        await stop_capture(process)
        return process.returncode, _time.monotonic() - started

    code, took = asyncio.run(main())
    assert code is not None and took < 2
