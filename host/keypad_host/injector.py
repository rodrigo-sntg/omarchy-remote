"""Virtual keyboard and mouse through /dev/uinput; the compositor sees ordinary input devices."""
from evdev import UInput
from evdev import ecodes as e

from .hid_evdev import ALL_KEYS, tap_codes

_BUTTONS = [(1, e.BTN_LEFT), (2, e.BTN_RIGHT), (4, e.BTN_MIDDLE)]


class UInputInjector:
    def __init__(self):
        self.keyboard = UInput({e.EV_KEY: ALL_KEYS}, name="Omarchy Remote Keyboard")
        self.mouse = UInput(
            {e.EV_KEY: [b for _, b in _BUTTONS], e.EV_REL: [e.REL_X, e.REL_Y, e.REL_WHEEL, e.REL_WHEEL_HI_RES, e.REL_HWHEEL, e.REL_HWHEEL_HI_RES]},
            name="Omarchy Remote Mouse",
        )
        self.mask = 0

    def tap(self, usage: int, modifiers: int) -> bool:
        codes = tap_codes(usage, modifiers)
        if codes is None:
            return False
        for code in codes:
            self.keyboard.write(e.EV_KEY, code, 1)
        self.keyboard.syn()
        for code in reversed(codes):
            self.keyboard.write(e.EV_KEY, code, 0)
        self.keyboard.syn()
        return True

    def buttons(self, mask: int):
        for bit, button in _BUTTONS:
            if (mask ^ self.mask) & bit:
                self.mouse.write(e.EV_KEY, button, 1 if mask & bit else 0)
        self.mask = mask
        self.mouse.syn()

    def move(self, dx: int, dy: int):
        self.mouse.write(e.EV_REL, e.REL_X, dx)
        self.mouse.write(e.EV_REL, e.REL_Y, dy)
        self.mouse.syn()

    def scroll(self, vertical: int):
        # Same sign as the HID wheel: positive scrolls up.
        self.mouse.write(e.EV_REL, e.REL_WHEEL, vertical)
        self.mouse.write(e.EV_REL, e.REL_WHEEL_HI_RES, vertical * 120)
        self.mouse.syn()

    def hscroll(self, horizontal: int):
        """Positive scrolls right."""
        self.mouse.write(e.EV_REL, e.REL_HWHEEL, horizontal)
        self.mouse.write(e.EV_REL, e.REL_HWHEEL_HI_RES, horizontal * 120)
        self.mouse.syn()

    def release_all(self):
        # Taps release their keys immediately; only mouse buttons can stay down.
        self.buttons(0)

    def close(self):
        self.keyboard.close()
        self.mouse.close()
