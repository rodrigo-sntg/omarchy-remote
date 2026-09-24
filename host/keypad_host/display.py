"""The phone as an extra monitor: a Hyprland headless output, captured and streamed as H.264."""
import asyncio
import json
import os
import socket
from dataclasses import dataclass

OUTPUT_NAME = "OMARCHYREMOTE"


class DisplayError(ValueError):
    pass


@dataclass(frozen=True)
class Geometry:
    """Logical (compositor) rectangle of the virtual monitor."""
    x: int
    y: int
    width: int
    height: int


def _number(message: dict, name: str, low: float, high: float) -> float:
    value = message.get(name)
    if type(value) not in (int, float) or not low <= value <= high:  # bool is not accepted
        raise DisplayError(f"invalid {name}")
    return value


def parse_start(message: dict) -> tuple[int, int, float]:
    if message.get("type") != "display.start":
        raise DisplayError("expected display.start")
    width, height = message.get("width"), message.get("height")
    if type(width) is not int or type(height) is not int or not (320 <= width <= 4096 and 320 <= height <= 4096):
        raise DisplayError("invalid size")
    if width % 2 or height % 2:
        raise DisplayError("size must be even")
    return width, height, _number(message, "scale", 1, 3)


_TOUCH_ACTIONS = ("down", "move", "up", "right", "scroll", "hscroll")


def parse_touch(message: dict) -> tuple[str, float, float, int]:
    """down/move/up drive the left button; right is a right click; scroll carries wheel steps."""
    if message.get("type") != "touch" or message.get("action") not in _TOUCH_ACTIONS:
        raise DisplayError("invalid touch")
    steps = 0
    if message["action"] in ("scroll", "hscroll"):
        steps = message.get("steps")
        if type(steps) is not int or not -20 <= steps <= 20:
            raise DisplayError("invalid steps")
    return message["action"], _number(message, "x", 0, 1), _number(message, "y", 0, 1), steps


def parse_pointer(message: dict) -> tuple[str, float, float, int]:
    """Trackpad mode: rel (dx, dy as fractions of the monitor), click (button 1/2), press, release, scroll."""
    action = message.get("action")
    if message.get("type") != "pointer" or action not in ("rel", "click", "press", "release", "scroll", "hscroll"):
        raise DisplayError("invalid pointer")
    if action == "rel":
        return action, _number(message, "dx", -1, 1), _number(message, "dy", -1, 1), 0
    if action == "click":
        button = message.get("button")  # 1 left, 2 right, 3 middle
        if button not in (1, 2, 3) or type(button) is not int:
            raise DisplayError("invalid button")
        return action, 0, 0, button
    if action in ("scroll", "hscroll"):
        steps = message.get("steps")
        if type(steps) is not int or not -20 <= steps <= 20:
            raise DisplayError("invalid steps")
        return action, 0, 0, steps
    return action, 0, 0, 0


def clamp_to(x: int, y: int, g: Geometry) -> tuple[int, int] | None:
    """The nearest point inside the monitor, or None when (x, y) is already inside."""
    cx = min(max(x, g.x), g.x + g.width - 1)
    cy = min(max(y, g.y), g.y + g.height - 1)
    return None if (cx, cy) == (x, y) else (cx, cy)


def to_normalized(x: int, y: int, g: Geometry) -> tuple[float, float]:
    return (x - g.x) / g.width, (y - g.y) / g.height


def cursor_message(x: int, y: int, viewed: str, monitors: dict[str, Geometry]) -> dict:
    """Where the cursor is for the phone: normalized on the viewed monitor, or the name of the
    other monitor it went to (the phone then follows it there)."""
    g = monitors.get(viewed)
    if g is not None and clamp_to(x, y, g) is None:
        nx, ny = to_normalized(x, y, g)
        return {"type": "cursor", "inside": True, "x": round(nx, 4), "y": round(ny, 4)}
    for name, other in monitors.items():
        if name != viewed and clamp_to(x, y, other) is None:
            return {"type": "cursor", "inside": False, "monitor": name}
    return {"type": "cursor", "inside": False}


def focused_window(window: dict, monitors: dict[str, Geometry]) -> tuple[str, str, str, Geometry] | None:
    """(address, title, monitor it is on, its rectangle) of Hyprland's active window; None without one."""
    try:
        (x, y), (w, h) = window["at"], window["size"]
    except (KeyError, TypeError, ValueError):
        return None
    if not all(type(v) is int for v in (x, y, w, h)) or w < 16 or h < 16:
        return None
    cx, cy = x + w // 2, y + h // 2
    monitor = next((name for name, g in monitors.items() if clamp_to(cx, cy, g) is None), None)
    if monitor is None:
        return None
    return str(window.get("address", "")), str(window.get("title") or window.get("class") or "Janela"), monitor, Geometry(x, y, w, h)


def parse_screen_options(message: dict) -> tuple[bool, int]:
    """viewOnly: a preview (no input, the cursor is never moved); fps: capture rate (default 60)."""
    view_only = message.get("viewOnly", False)
    fps = message.get("fps", 60)
    if type(view_only) is not bool or type(fps) is not int or not 1 <= fps <= 60:
        raise DisplayError("invalid options")
    return view_only, fps


def real_monitors(monitors: list[dict]) -> dict[str, Geometry]:
    """The user's monitors (not our virtual one), in logical coordinates, by name."""
    return {
        m["name"]: Geometry(m["x"], m["y"], round(m["width"] / m["scale"]), round(m["height"] / m["scale"]))
        for m in monitors if m["name"] != OUTPUT_NAME
    }


def fit_size(width: int, height: int, max_width: int, max_height: int) -> tuple[int, int]:
    """Largest even size with the monitor's aspect that fits the phone; never larger than the monitor."""
    factor = min(1.0, max_width / width, max_height / height)
    return round(width * factor / 2) * 2, round(height * factor / 2) * 2


def parse_screen_start(message: dict, monitors: set[str]) -> tuple[str, int, int]:
    if message.get("type") != "screen.start" or message.get("monitor") not in monitors:
        raise DisplayError("unknown monitor")
    width, height = message.get("maxWidth"), message.get("maxHeight")
    # Small sizes are allowed: the main screen's live preview asks for a thumbnail.
    if type(width) is not int or type(height) is not int or not (160 <= width <= 4096 and 160 <= height <= 4096):
        raise DisplayError("invalid size")
    return message["monitor"], width, height


def to_global(nx: float, ny: float, g: Geometry) -> tuple[int, int]:
    """Normalized point on the streamed image -> global compositor point; the far edges are exclusive."""
    x = g.x + min(int(nx * g.width), g.width - 1)
    y = g.y + min(int(ny * g.height), g.height - 1)
    return x, y


class Hyprland:
    """Hyprland IPC through its command socket (hyprctl would spawn a process per touch move)."""

    def __init__(self):
        runtime = os.environ["XDG_RUNTIME_DIR"]
        self.path = f"{runtime}/hypr/{os.environ['HYPRLAND_INSTANCE_SIGNATURE']}/.socket.sock"

    def request(self, command: str) -> str:
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as s:
            s.connect(self.path)
            s.sendall(command.encode())
            chunks = []
            while chunk := s.recv(65536):
                chunks.append(chunk)
        return b"".join(chunks).decode()

    def create_output(self, width: int, height: int, scale: float) -> Geometry:
        self.request(f"output create headless {OUTPUT_NAME}")
        self.request(
            f'eval hl.monitor({{ output = "{OUTPUT_NAME}", mode = "{width}x{height}@60", '
            f'position = "auto-right", scale = {scale} }})'
        )
        for monitor in json.loads(self.request("j/monitors")):
            if monitor["name"] == OUTPUT_NAME:
                s = monitor["scale"]
                return Geometry(monitor["x"], monitor["y"], round(monitor["width"] / s), round(monitor["height"] / s))
        raise DisplayError("virtual monitor was not created")

    def monitors(self) -> list[dict]:
        return json.loads(self.request("j/monitors"))

    def remove_output(self):
        # Hyprland moves the windows of a removed monitor to the remaining ones.
        self.request(f"output remove {OUTPUT_NAME}")

    def cursor_pos(self) -> tuple[int, int]:
        position = json.loads(self.request("j/cursorpos"))
        return position["x"], position["y"]

    def workspaces(self) -> list[dict]:
        return json.loads(self.request("j/workspaces"))

    def focus_workspace(self, target: str):
        """target: a workspace number or e+1 / e-1 (next/previous with windows), as Omarchy's binds use."""
        self.request(f'dispatch hl.dsp.focus({{ workspace = "{target}" }})')

    def binds(self) -> list[dict]:
        return json.loads(self.request("j/binds"))

    def active_window(self) -> dict:
        """The focused window ({} when none)."""
        return json.loads(self.request("j/activewindow") or "{}")

    def clients(self) -> list[dict]:
        return json.loads(self.request("j/clients"))

    def window_act(self, address: str, action: str, workspace: int | None = None):
        """Focus the window, then the same dispatch Omarchy's own bind for that action uses."""
        self.request(f'dispatch hl.dsp.focus({{ window = "address:{address}" }})')
        match action:
            case "close":
                self.request("dispatch hl.dsp.window.close()")
            case "float":
                self.request('dispatch hl.dsp.window.float({ action = "toggle" })')
            case "fullscreen":
                self.request('dispatch hl.dsp.window.fullscreen({ mode = "fullscreen" })')
            case "workspace":
                self.request(f'dispatch hl.dsp.window.move({{ workspace = "{int(workspace)}" }})')

    def move_cursor(self, x: int, y: int):
        self.request(f"dispatch hl.dsp.cursor.move({{ x = {x}, y = {y} }})")


def capture_args(output: str, size: tuple[int, int] | None, fps: int, region: "Geometry | None" = None) -> list[str]:
    """wf-recorder for a monitor, or for a rectangle of the desktop (one window) when [region] is set."""
    scale = ["-F", f"scale={size[0]}:{size[1]}"] if size else []
    rate = ["-r", str(fps)] if fps < 60 else []
    keyframes = str(max(fps * 2, 10) if fps < 60 else 60)  # a keyframe every ~2 s at low rates
    where = ["-g", f"{region.x},{region.y} {region.width}x{region.height}"] if region else ["-o", output]
    return [
        "wf-recorder", *where, "-c", "libx264", "-x", "yuv420p", *scale, *rate, "-m", "h264", "-f", "pipe:1",
        "-y", "-b", "0", "-p", "preset=ultrafast", "-p", "tune=zerolatency", "-p", f"g={keyframes}",
    ]


async def start_capture(
    output: str = OUTPUT_NAME, size: tuple[int, int] | None = None, fps: int = 60, region: "Geometry | None" = None,
) -> asyncio.subprocess.Process:
    """wf-recorder: H.264 Annex B on stdout, no B-frames, keyframe every 60 frames.

    x264 on the CPU (ultrafast/zerolatency): NVENC fails whenever the GPU memory is taken (e.g. by
    ollama), and VAAPI on the AMD iGPU cannot take frames from the compositor running on the NVIDIA GPU.
    """
    return await asyncio.create_subprocess_exec(
        *capture_args(output, size, fps, region), stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL,
    )


async def stop_capture(process) -> None:
    """Ends a capture now. SIGKILL, not SIGTERM: wf-recorder catches SIGTERM to finish its output, and
    with nobody reading the pipe any more it would wait forever — and the switch or the session
    waiting on it with it. A live stream has nothing to save."""
    if process.returncode is None:
        try:
            process.kill()
        except ProcessLookupError:
            pass
    try:
        await asyncio.wait_for(process.wait(), 2)
    except asyncio.TimeoutError:
        pass
