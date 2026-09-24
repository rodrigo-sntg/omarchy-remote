"""Hyprland's event socket (socket2): the host refreshes the desktop layout when something changed
instead of asking several times a second (docs/PLANO-V2.md §6)."""
import asyncio
import logging
import os

log = logging.getLogger("keypad_host")

_RELEVANT = {
    "workspace", "workspacev2", "focusedmon", "focusedmonv2", "createworkspace", "createworkspacev2",
    "destroyworkspace", "destroyworkspacev2", "moveworkspace", "moveworkspacev2", "monitoradded",
    "monitoraddedv2", "monitorremoved", "monitorremovedv2", "openwindow", "closewindow", "movewindow",
    "movewindowv2", "renameworkspace",
}


def relevant(line: str) -> bool:
    return line.partition(">>")[0] in _RELEVANT


class DesktopEvents:
    """Sets [changed] whenever Hyprland reports a layout change; reconnects if Hyprland restarts."""

    def __init__(self):
        self.changed = asyncio.Event()

    @staticmethod
    def socket_path() -> str:
        return f"{os.environ['XDG_RUNTIME_DIR']}/hypr/{os.environ['HYPRLAND_INSTANCE_SIGNATURE']}/.socket2.sock"

    async def run(self, path: str | None = None):
        path = path or self.socket_path()
        while True:
            try:
                reader, writer = await asyncio.open_unix_connection(path)
                self.changed.set()  # (re)connected: the layout may have changed meanwhile
                while line := await reader.readline():
                    if relevant(line.decode(errors="replace").strip()):
                        self.changed.set()
                writer.close()
            except (OSError, KeyError) as error:
                log.debug("hyprland events unavailable: %s", error)
            await asyncio.sleep(2)
