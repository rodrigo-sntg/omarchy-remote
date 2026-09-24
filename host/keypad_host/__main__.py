"""python -m keypad_host --allow <tailscale machine name> [--port 8765]"""
import argparse
import asyncio
import json
import logging
import os
import shutil
import subprocess
from pathlib import Path

from aiohttp import web

from .auth import LOOPBACK_MACHINE, load_or_create_token, migrate_config, new_token, loopback_whois, tailscale_whois
from .display import Hyprland
from .herdr import Herdr
from .control import serve as serve_control
from .server import HUB_KEY
from .version import RESTART_EXIT
from .theme import read_theme
from .events import DesktopEvents
from .clip import watch_clipboard
from .media import watch_media
from .devices import Devices
from .unlock import Keys, Unlocker
from .notifications import watch_notifications
from .injector import UInputInjector
from .server import create_app


TOKEN_PATH = Path.home() / ".config" / "omarchy-remote" / "token"


def tailscale_ipv4() -> str:
    return subprocess.run(["tailscale", "ip", "-4"], capture_output=True, text=True, check=True).stdout.split()[0]


def tailscale_owner() -> str | None:
    """This PC's Tailscale user: without --allow, that user's own devices may connect."""
    status = json.loads(subprocess.run(["tailscale", "status", "--json"], capture_output=True, text=True, check=True).stdout)
    user = (status.get("User") or {}).get(str((status.get("Self") or {}).get("UserID")))
    return (user or {}).get("LoginName")


def tailnet_self_name() -> str:
    """This PC's MagicDNS name, as the phone reaches it (unlock signatures are bound to it)."""
    try:
        status = json.loads(subprocess.run(["tailscale", "status", "--json"], capture_output=True, text=True, check=True).stdout)
        return (status.get("Self") or {}).get("DNSName", "").rstrip(".").lower()
    except (OSError, subprocess.CalledProcessError, ValueError):
        return "localhost"


def tailnet_suffix() -> str:
    status = subprocess.run(["tailscale", "status", "--json"], capture_output=True, text=True, check=True).stdout
    return json.loads(status)["MagicDNSSuffix"].lower()


def runtime_dir(dev: bool) -> str:
    """Where the control socket and the bar widget's state live; the test instance keeps apart."""
    base = os.environ.get("XDG_RUNTIME_DIR") or f"/run/user/{os.getuid()}"
    return os.path.join(base, "omarchy-remote-dev" if dev else "omarchy-remote")


def omarchy_notify(title: str, body: str):
    """A desktop notice through Omarchy, when available; never blocks the host."""
    if shutil.which("omarchy-notification-send"):
        subprocess.Popen(["omarchy-notification-send", "-g", "\U000f011c", title, body],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def main():
    parser = argparse.ArgumentParser(description="Omarchy Remote host: input over Tailscale.")
    parser.add_argument("--allow", action="append", default=[],
                        help="only these Tailscale machines (default: any of your own devices on this tailnet)")
    parser.add_argument("--dev-loopback", action="store_true",
                        help="testing only: listen on 127.0.0.1 for a phone behind `adb reverse` (no Tailscale); pairing code still required")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    migrate_config(TOKEN_PATH.parent.with_name("android-keypad"), TOKEN_PATH.parent)
    code = {"token": load_or_create_token(TOKEN_PATH)}
    owner = None

    if args.dev_loopback:
        address, tailnet, whois = "127.0.0.1", "localhost", loopback_whois
        args.allow = [LOOPBACK_MACHINE]
    else:
        owner = tailscale_owner()
        address = tailscale_ipv4()  # only the tailnet interface; nothing is exposed on the LAN
        tailnet, whois = tailnet_suffix(), tailscale_whois
    # Never printed: as a service it would end up in the journal.
    logging.info("pairing code: cat %s", TOKEN_PATH)
    logging.info("creating virtual keyboard and mouse (uinput)…")
    injector = UInputInjector()
    allowed = {name.lower().rstrip(".") for name in args.allow}
    logging.info("listening on ws://%s:%d/v1, allowed: %s (tailnet %s)", address, args.port, ", ".join(sorted(allowed)), tailnet)
    try:
        run_dir = runtime_dir(args.dev_loopback)
        events = DesktopEvents()
        app = create_app(injector, allowed, tailnet, lambda: code["token"], whois, hyprland=Hyprland(), herdr=Herdr(),
                         state_path=os.path.join(run_dir, "state.json"), notify=omarchy_notify, theme_reader=read_theme,
                         events=events, owner=owner, devices=Devices(TOKEN_PATH.parent / "devices.json"),
                         unlocker=Unlocker(injector, Keys(TOKEN_PATH.parent / "unlock_keys.json"), run_dir,
                                           host=tailnet_self_name(), notify=omarchy_notify))

        def rotate():
            code["token"] = new_token(TOKEN_PATH)
            logging.info("new pairing code: every phone pairs again")

        app[HUB_KEY].rotate_token = rotate

        control = []

        async def start_control(app):
            control.append(await serve_control(os.path.join(run_dir, "control.sock"), app[HUB_KEY]))
            control.append(asyncio.create_task(events.run()))
            control.append(asyncio.create_task(watch_notifications(app[HUB_KEY])))
            control.append(asyncio.create_task(watch_clipboard(app[HUB_KEY])))
            control.append(asyncio.create_task(watch_media(app[HUB_KEY])))

        async def stop_control(app):
            hub = app[HUB_KEY]
            hub.phone = None  # the bar must not keep showing a phone after the host stopped
            hub.write()
            for item in control:
                (item.cancel if isinstance(item, asyncio.Task) else item.close)()

        app.on_startup.append(start_control)
        app.on_cleanup.append(stop_control)
        web.run_app(app, host=address, port=args.port, print=None)
    finally:
        injector.close()
    if app[HUB_KEY].restart_requested:
        logging.info("restarting for the new code")
        raise SystemExit(RESTART_EXIT)


if __name__ == "__main__":
    main()
