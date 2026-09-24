"""The S Pen as a drawing tablet on the PC (docs/PLANO-V2.md §8): a separate uinput pen device with
pressure, so apps (Xournal++, Krita) see a stylus, not a mouse. A tablet is mapped to all outputs
together, so positions span the bounding box of every monitor."""
from .display import DisplayError, Geometry, to_global

PEN_MAX = 32767
PRESSURE_MAX = 4095
# libinput decides "tip down" from pressure (a few percent of the range), not from BTN_TOUCH: a light
# S Pen stroke, or a sample with pressure 0, would lift the pen mid-line.
MIN_TIP_PRESSURE = PRESSURE_MAX // 12
STATES = ("hover", "down", "move", "up", "out")


def pen_position(nx: float, ny: float, geometry: Geometry, monitors: list[Geometry]) -> tuple[int, int]:
    gx, gy = to_global(nx, ny, geometry)
    left, top = min(m.x for m in monitors), min(m.y for m in monitors)
    width = max(m.x + m.width for m in monitors) - left
    height = max(m.y + m.height for m in monitors) - top
    return round((gx - left) / width * PEN_MAX), round((gy - top) / height * PEN_MAX)


def parse_pen(message: dict) -> tuple[str, float, float, int]:
    state, x, y = message.get("state"), message.get("x"), message.get("y")
    pressure = message.get("pressure", 0)
    numbers = (x, y, pressure)
    if state not in STATES or any(not isinstance(v, (int, float)) or isinstance(v, bool) for v in numbers):
        raise DisplayError("invalid pen")
    if not (0 <= x <= 1 and 0 <= y <= 1 and 0 <= pressure <= 1):
        raise DisplayError("invalid pen")
    if state not in ("down", "move"):
        return state, float(x), float(y), 0
    return state, float(x), float(y), max(round(pressure * PRESSURE_MAX), MIN_TIP_PRESSURE)


class PenDevice:
    """uinput stylus: position, pressure, pen in range (hover), tip down."""

    def __init__(self):
        from evdev import AbsInfo, UInput
        from evdev import ecodes as e
        self.e = e
        self.device = UInput({
            e.EV_KEY: [e.BTN_TOOL_PEN, e.BTN_TOUCH, e.BTN_STYLUS],
            e.EV_ABS: [
                (e.ABS_X, AbsInfo(0, 0, PEN_MAX, 0, 0, 100)),
                (e.ABS_Y, AbsInfo(0, 0, PEN_MAX, 0, 0, 100)),
                (e.ABS_PRESSURE, AbsInfo(0, 0, PRESSURE_MAX, 0, 0, 0)),
            ],
        }, name="Omarchy Remote Pen", input_props=[e.INPUT_PROP_POINTER])
        self.in_range = False
        self.touching = False

    def write(self, state: str, x: int, y: int, pressure: int):
        e, d = self.e, self.device
        if state == "out":
            if self.touching:
                d.write(e.EV_KEY, e.BTN_TOUCH, 0)
            d.write(e.EV_KEY, e.BTN_TOOL_PEN, 0)
            self.in_range = self.touching = False
            d.syn()
            return
        if not self.in_range:
            d.write(e.EV_KEY, e.BTN_TOOL_PEN, 1)
            self.in_range = True
        d.write(e.EV_ABS, e.ABS_X, x)
        d.write(e.EV_ABS, e.ABS_Y, y)
        touching = state in ("down", "move")
        d.write(e.EV_ABS, e.ABS_PRESSURE, pressure if touching else 0)
        if touching != self.touching:
            d.write(e.EV_KEY, e.BTN_TOUCH, 1 if touching else 0)
            self.touching = touching
        d.syn()
