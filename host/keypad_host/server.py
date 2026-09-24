"""WebSocket endpoint /v1: authorize, hand out a session, inject input, release everything on exit."""
import asyncio
import json
import logging
import os
import signal
import time
import urllib.parse
import uuid
from pathlib import Path

from aiohttp import WSMsgType, web

from .agents import AgentCommands, Alerts, Follower, summarize as summarize_agents
from .herdr import HerdrError
from .pcstats import read_stats
from .thumbs import thumb_message
from .version import RUNNING, fingerprint
from .browse import folder_listing, open_url_command, roots, within_home
from .controls import control_command, read_controls
from .clip import get_clipboard, set_clipboard
from .theme import theme_file
from .palette import binds_for_phone, windows_for_phone
from .usage import UsageReader
from .wake import interfaces as wake_interfaces
from .phone_notices import PhoneNotices
from .files import AGENT_FOLDER, MAX_UPLOAD, Offers, agent_inbox, agent_name, downloads_dir, grim_args, run_grim, safe_name, unique_path
from .files import shots_dir as default_shots_dir
from .now import pc_command, media_command, read_now
from .pen import PenDevice, parse_pen, pen_position
from .omarchy_menu import MenuSource, desktop_apps, spawn_detached
from .term import Pty, TermError, herdr_command, parse_term_ack, parse_term_resize, parse_term_start
from .auth import is_allowed, machine_name, token_matches
from .display import (
    DisplayError, clamp_to, cursor_message, fit_size, focused_window, parse_pointer, parse_screen_options, parse_screen_start, parse_start,
    parse_touch, real_monitors,
    start_capture, stop_capture, to_global, to_normalized,
)
from .protocol import AGENT_TARGET, MAX_FRAME, READ_LIMIT
from .session import HEARTBEAT_TIMEOUT, Session
from .desktop import cursor_on, monitor_map
from .workspaces import summarize

log = logging.getLogger("keypad_host")
TOKEN_HEADER = "X-Keypad-Token"
# Terminal flow control: output sent but not yet drawn by the phone (term.ack). Past this the
# PTY is not read, so the program on the PC blocks instead of anything being lost or piling up.
TERM_WINDOW = 512 * 1024
# A terminal paste may be long; everything else stays under MAX_FRAME.
TERM_MAX_MESSAGE = 256 * 1024
# H.264 access unit delimiter: tells the phone the previous frame is complete.
ACCESS_UNIT_DELIMITER = b"\x00\x00\x00\x01\x09\xf0"
FRAME_QUIET = 0.004  # seconds without encoder output after which a frame is taken as whole


class Hub:
    """What the rest of the PC sees of the phone (control socket, bar widget, notices): who is
    connected, its battery, how many agents wait, and a way to push a message to it."""

    def __init__(self, state, state_path=None, notify=None, theme_reader=None, grace: float = 60.0):
        self._state = state
        self.state_path = state_path
        self.notify = notify or (lambda *args: None)
        self.theme_reader = theme_reader
        self.phone = None
        self.battery = None
        self.charging = False
        self.waiting = 0
        self.restart_requested = False  # the phone asked for a restart (new code): exit so systemd starts it again
        self.clip_last = None       # the clipboard's text as the phone already knows it (no echo)
        self.media = None           # what plays on the PC, as last pushed (a phone that connects gets it)
        self.devices = None         # the phones seen and revoked (omarchy-remote devices)
        self.rotate_token = lambda: None  # set by the service: a new pairing code
        self.offers = Offers()
        # Leaving the app briefly closes the session; notices wait out this grace period.
        self.grace = grace
        self._announced = None      # the phone the desktop was last told is connected
        self._leaving = None        # pending "disconnected" notice
        self.write()

    @property
    def connected(self) -> bool:
        return self._state["active"] is not None and self.phone is not None

    def write(self):
        """The bar widget reads this file (it watches it); written atomically."""
        if not self.state_path:
            return
        data = {"connected": self.connected, "phone": self.phone, "battery": self.battery,
                "charging": self.charging, "waiting": self.waiting}
        os.makedirs(os.path.dirname(self.state_path), mode=0o700, exist_ok=True)
        tmp = self.state_path + ".tmp"
        with open(tmp, "w") as f:
            json.dump(data, f)
        os.replace(tmp, self.state_path)

    async def offer(self, path, kind: str = "file", tag: str | None = None, temporary: bool = False) -> bool:
        """Tells the phone a file waits for it (it downloads GET /v1/file/<id>)."""
        if not self.connected:
            if temporary:
                Path(path).unlink(missing_ok=True)
            return False
        message = {"type": "file.offer", **self.offers.add(Path(path), temporary), "kind": kind}
        if tag:
            message["tag"] = tag
        return await self.push(message)

    def kick(self):
        """Drops the phone connected now (revoked, or the pairing code changed)."""
        ws = self._state["control"]
        if ws is not None and not ws.closed:
            asyncio.get_event_loop().create_task(ws.close(code=4001, message=b"revoked"))

    async def push(self, message: dict) -> bool:
        ws = self._state["control"]
        if not self.connected or ws is None or ws.closed:
            return False
        try:
            await ws.send_json(message)
        except (ConnectionResetError, RuntimeError):
            return False
        return True

    async def push_theme(self, preview: str | None = None) -> bool:
        """The active Omarchy theme, or an installed one by slug (preview: nothing changes on the PC)."""
        if not self.theme_reader:
            return False
        if preview is None:
            theme = await self.theme_reader()
        else:
            file = theme_file(preview)
            theme = await self.theme_reader(file=file) if file else None
        return bool(theme) and await self.push(theme)

    def opened(self, phone: str):
        self.phone, self.battery, self.charging, self.waiting = phone, None, False, 0
        self.write()
        if self._leaving is not None:
            self._leaving.cancel()
            self._leaving = None
        if self._announced != phone:
            self._announced = phone
            self.notify("Celular conectado", phone)

    def closed(self):
        phone, self.phone, self.battery, self.charging, self.waiting = self.phone, None, None, False, 0
        self.write()
        if self._announced is None:
            return

        async def announce_later():
            await asyncio.sleep(self.grace)
            self._announced, self._leaving = None, None
            self.notify("Celular desconectado", phone or "")

        try:
            self._leaving = asyncio.get_running_loop().create_task(announce_later())
        except RuntimeError:  # no loop (tests of the state alone)
            self._announced = None
            self.notify("Celular desconectado", phone or "")


HUB_KEY = web.AppKey("hub", Hub)
# Phone requests answered by other programs (wl-copy, bash checks, playerctl, hyprctl): off the input loop.
SLOW_TYPES = {"thumb.get", "stats.get", "host.get", "host.restart", "pc.open_url", "files.list", "files.fetch", "controls.get", "pc.control", "clipboard.set", "clipboard.get", "menu.get", "menu.run", "menu.show", "now.get", "media.cmd",
              "binds.get", "windows.get", "window.act", "pc.act", "shot.get", "usage.get"}


def create_app(
    injector, allowed: set[str], tailnet: str, token: str, whois,
    heartbeat_timeout: float | None = None, hyprland=None, capture=start_capture,
    herdr=None, agent_poll: float = 2.0, term_command=herdr_command, term_window: int = TERM_WINDOW,
    state_path=None, notify=None, theme_reader=None, events=None, clipboard=(set_clipboard, get_clipboard),
    notice_grace: float = 60.0, menu=None, apps=None, launch=None, pen=None, downloads=None,
    shoot=None, shots_dir=None, usage=None, transcripts_home=None, owner=None, devices=None, phone_notices_factory=None, unlocker=None,
) -> web.Application:
    # The pairing code may change while running (omarchy-remote new-code): read it at each request.
    current_token = token if callable(token) else (lambda: token)
    timeout = heartbeat_timeout or HEARTBEAT_TIMEOUT
    menu = menu or MenuSource()
    apps = apps or desktop_apps
    launch = launch or spawn_detached
    pen_holder = []
    usage_reader = usage or UsageReader()

    def pen_device():
        """Created on the first pen event: most sessions never draw."""
        if not pen_holder:
            pen_holder.append(pen() if pen else PenDevice())
        return pen_holder[0]
    agent_commands = AgentCommands(herdr, home=transcripts_home) if herdr is not None else None
    # active/owner/control: the control session, the machine it belongs to and its WebSocket;
    # video: the WebSocket of the current video session.
    state = {"active": None, "owner": None, "control": None, "video": None, "term": None}
    hub = Hub(state, state_path, notify, theme_reader, grace=notice_grace)
    hub.devices = devices
    hub.unlock_keys = unlocker.keys if unlocker is not None else None
    phone_notices = PhoneNotices(hub) if phone_notices_factory is None else phone_notices_factory(hub)

    async def authorize(request: web.Request) -> str:
        # Browsers always send Origin; the app never does. A web page must not reach these endpoints.
        if "Origin" in request.headers or not token_matches(request.headers.get(TOKEN_HEADER), current_token()):
            log.warning("refused request without valid pairing code (or from a browser)")
            raise web.HTTPForbidden(text="pairing code required")
        peer = request.transport.get_extra_info("peername") if request.transport else None
        address = f"{peer[0]}:{peer[1]}" if peer else ""
        identity = await whois(address)
        if not is_allowed(identity, allowed, tailnet, owner):
            log.warning("refused %s (%s)", address, machine_name(identity))
            raise web.HTTPForbidden(text="machine not allowed")
        if devices is not None and devices.revoked(machine_name(identity)):
            log.warning("refused revoked %s", machine_name(identity))
            raise web.HTTPForbidden(text="device revoked")
        return machine_name(identity)

    async def handle(request: web.Request) -> web.StreamResponse:
        identity = await authorize(request)

        ws = web.WebSocketResponse(max_msg_size=READ_LIMIT, heartbeat=None)
        await ws.prepare(request)
        if state["active"] is not None and state["owner"] == identity:
            # The same phone again (its app was killed and reopened): its old session is stale, replace it.
            state["active"].close()
            await state["control"].close()
            for _ in range(50):
                if state["active"] is None:
                    break
                await asyncio.sleep(0.1)
        if state["active"] is not None:
            await ws.send_json({"type": "error", "message": "Já existe outro controlador conectado."})
            await ws.close()
            return ws

        session = Session(uuid.uuid4().hex, injector, time.monotonic(), timeout, desktop=hyprland)
        state["active"], state["owner"], state["control"] = session, identity, ws
        hub.opened(identity.split(".")[0] if identity else "celular")
        if devices is not None and identity:
            devices.seen(identity)
        log.info("session %s from %s", session.session_id[:8], identity)
        if hub.media:
            await ws.send_json(hub.media)
        watchdog = asyncio.create_task(_watch(ws, session))
        workspaces = asyncio.create_task(_push_workspaces(ws, session)) if hyprland else None
        agents = asyncio.create_task(_push_agents(ws, session)) if herdr is not None else None
        lock_watch = asyncio.create_task(_push_lock(ws, identity)) if unlocker is not None else None
        # herdr calls can take seconds: they run one at a time off the input loop, so the touchpad
        # and pings never wait behind them.
        agent_queue: asyncio.Queue = asyncio.Queue(maxsize=32)
        agent_worker = asyncio.create_task(_run_agent_commands(ws, agent_queue)) if agent_commands is not None else None
        # The open agent's new messages, pushed while the phone shows it.
        follower = Follower(agent_commands, ws.send_json) if agent_commands is not None else None
        # The menu's checks, the clipboard, the player and hyprctl run subprocesses: same idea.
        slow_queue: asyncio.Queue = asyncio.Queue(maxsize=32)
        slow_worker = asyncio.create_task(_run_slow(ws, slow_queue))
        try:
            await ws.send_json({"type": "session", "sessionId": session.session_id})
            await hub.push_theme()
            async for message in ws:
                if message.type != WSMsgType.TEXT:
                    break
                for reply in session.handle(message.data, time.monotonic()):
                    await ws.send_json(reply)
                last = session.last_message
                if last is not None and last.type in SLOW_TYPES:
                    try:
                        slow_queue.put_nowait(last)
                    except asyncio.QueueFull:
                        await ws.send_json({"type": "ack", "seq": last.seq, "ok": False})
                if last is not None and last.type.startswith("unlock."):
                    asyncio.create_task(_unlock(ws, last, identity))
                if last is not None and last.type == "phone.notification":
                    await phone_notices.posted(last.payload)
                elif last is not None and last.type == "phone.notification.removed":
                    await phone_notices.removed(last.payload["key"])
                if last is not None and last.type == "phone.status":
                    hub.battery, hub.charging = last.payload["battery"], last.payload["charging"]
                    hub.write()
                if follower is not None and last is not None and last.type in ("agent.follow", "agent.unfollow"):
                    if last.type == "agent.follow":
                        follower.follow(last.payload["id"], last.payload["after"])
                    else:
                        follower.stop()
                elif agent_commands is not None and last is not None and last.type == "agent.start":
                    # Up to a minute (the agent getting ready): its own task, the other agents keep answering.
                    asyncio.create_task(_run_one(ws, last))
                elif agent_worker is not None and last is not None and (last.type.startswith("agent.") or last.type == "projects.list"):
                    try:
                        agent_queue.put_nowait(last)
                    except asyncio.QueueFull:
                        await ws.send_json({"type": "ack", "seq": last.seq, "ok": False, "error": "O herdr está ocupado."})
                if session.closed:
                    break
        finally:
            watchdog.cancel()
            if lock_watch:
                lock_watch.cancel()
            if workspaces:
                workspaces.cancel()
            if agents:
                agents.cancel()
            if agent_worker:
                agent_worker.cancel()
            if follower:
                follower.stop()
            slow_worker.cancel()
            session.close()  # releases keys and buttons
            if state["active"] is session:
                state["active"] = None
                hub.closed()
            await ws.close()
            log.info("session %s ended", session.session_id[:8])
        return ws

    async def _shot(ws, message):
        """A print for the phone at full resolution, offered like any file (the phone downloads it)."""
        p = message.payload
        geometry = None
        if "monitor" in p:
            try:
                geometry = real_monitors(hyprland.monitors()).get(p["monitor"]) if hyprland else None
            except (OSError, ValueError, KeyError):
                geometry = None
            if geometry is None:
                await ws.send_json({"type": "ack", "seq": message.seq, "ok": False})
                return
        folder = Path(shots_dir) if shots_dir is not None else default_shots_dir()
        path = folder / time.strftime("print-%Y%m%d-%H%M%S.png")
        path = unique_path(folder, path.name)
        ok = await (shoot or run_grim)(grim_args(str(path), p.get("monitor"), geometry, p.get("region")))
        await ws.send_json({"type": "ack", "seq": message.seq, "ok": ok and path.is_file()})
        if ok and path.is_file():
            await hub.offer(path, kind="shot", tag=p["tag"], temporary=True)

    async def send_file(request: web.Request) -> web.StreamResponse:
        """A file the PC offered to the phone (file.offer): only with the pairing code, only while offered."""
        await authorize(request)
        path = hub.offers.get(request.match_info["offer_id"])
        if path is None:
            raise web.HTTPNotFound(text="offer expired")
        return web.FileResponse(path, headers={"Content-Disposition": "attachment; filename*=UTF-8''" + urllib.parse.quote(path.name)})

    async def _run_slow(ws, queue):
        """Phone requests that wait on other programs, one at a time and in order."""
        while True:
            last = await queue.get()
            try:
                if last.type == "clipboard.set":
                    hub.clip_last = last.payload["text"]  # the PC's watcher sees it next: not back to the phone
                    await clipboard[0](last.payload["text"])
                elif last.type == "clipboard.get":
                    text = await clipboard[1]()
                    await ws.send_json({"type": "clipboard", "text": text or ""})  # empty: the phone says so
                elif last.type.startswith("menu."):
                    await _menu(ws, last)
                elif last.type == "now.get":
                    await ws.send_json(await read_now())
                elif last.type == "media.cmd":
                    await launch(media_command(last.payload["action"]))
                    await ws.send_json({"type": "ack", "seq": last.seq, "ok": True})
                    if not last.payload["action"].startswith("volume-"):
                        await asyncio.sleep(0.3)  # the player needs a moment to report the new track
                        await ws.send_json(await read_now())
                elif last.type == "usage.get":
                    await ws.send_json(await usage_reader.read(last.payload["force"]))
                elif last.type == "shot.get":
                    await _shot(ws, last)
                elif last.type == "pc.act":
                    await launch(pc_command(last.payload["action"]))
                    await ws.send_json({"type": "ack", "seq": last.seq, "ok": True})
                elif last.type == "thumb.get":
                    widths = {name: g.width for name, g in real_monitors(hyprland.monitors()).items()} if hyprland is not None else {}
                    message = await thumb_message(last.payload["monitor"], widths, last.payload["width"])
                    if message is not None:
                        await ws.send_json(message)
                elif last.type == "stats.get":
                    await ws.send_json(await read_stats())
                elif last.type == "host.get":
                    await ws.send_json(await host_info())
                elif last.type == "host.restart":
                    await ws.send_json({"type": "ack", "seq": last.seq, "ok": True})
                    # A clean stop (the virtual keyboard and mouse closed), then an exit systemd restarts.
                    hub.restart_requested = True
                    asyncio.get_running_loop().call_later(0.4, os.kill, os.getpid(), signal.SIGTERM)
                elif last.type == "pc.open_url":
                    try:
                        command = open_url_command(last.payload["url"])
                    except ValueError:
                        await ws.send_json({"type": "ack", "seq": last.seq, "ok": False})
                        continue
                    await launch(command)
                    await ws.send_json({"type": "ack", "seq": last.seq, "ok": True})
                elif last.type == "files.list":
                    home = Path.home()
                    try:
                        listing = await asyncio.to_thread(folder_listing, last.payload["path"] or str(downloads or downloads_dir()), home)
                    except (ValueError, OSError):
                        listing = await asyncio.to_thread(folder_listing, str(home), home)
                    await ws.send_json({"type": "files", **listing, "roots": roots(home)})
                elif last.type == "files.fetch":
                    real = within_home(last.payload["path"], Path.home())
                    ok = real is not None and os.path.isfile(real) and os.path.getsize(real) <= MAX_UPLOAD and await hub.offer(real)
                    await ws.send_json({"type": "ack", "seq": last.seq, "ok": bool(ok)})
                elif last.type == "controls.get":
                    await ws.send_json(await read_controls())
                elif last.type == "pc.control":
                    try:
                        command = control_command(last.payload["id"], last.payload["value"])
                    except ValueError:
                        await ws.send_json({"type": "ack", "seq": last.seq, "ok": False})
                        continue
                    await launch(command)  # detached: a recording keeps running
                    await ws.send_json({"type": "ack", "seq": last.seq, "ok": True})
                    await asyncio.sleep(0.6)  # the toggle settles (hyprsunset starting, the OSD…)
                    await ws.send_json(await read_controls())
                else:
                    await _desktop(ws, last)
            except (ConnectionResetError, RuntimeError):
                return

    async def _push_lock(ws, identity):
        """The PC's lock as it changes: the phone offers the fingerprint unlock while it is up."""
        last = None
        while not ws.closed:
            locked = await unlocker.status()
            state_now = {"type": "pc.lock", "locked": locked is True, **unlocker.ready(identity or "")}
            if state_now != last:
                last = state_now
                try:
                    await ws.send_json(state_now)
                except (ConnectionResetError, RuntimeError):
                    return
            await asyncio.sleep(2)

    async def _unlock(ws, message, identity):
        if unlocker is None or not identity:
            reply = {"type": "unlock.result", "ok": False, "reason": "unsupported"}
        elif message.type == "unlock.enroll":
            reply = await unlocker.enroll(identity, message.payload["key"])
        elif message.type == "unlock.challenge":
            reply = await unlocker.challenge(identity)
        else:
            reply = await unlocker.respond(identity, message.payload["signature"])
        try:
            await ws.send_json(reply)
        except (ConnectionResetError, RuntimeError):
            pass

    async def host_info():
        current = await asyncio.to_thread(fingerprint)
        # How to wake this PC later (the phone keeps it): the phone sends a magic packet on its LAN.
        wake = await asyncio.to_thread(wake_interfaces)
        return {"type": "host", "version": RUNNING, "outdated": current != RUNNING, "wake": wake}

    async def _desktop(ws, message):
        """Omarchy's keybindings (pressed by the phone as keys) and the PC's windows."""
        if hyprland is None:
            if message.type == "window.act":
                await ws.send_json({"type": "ack", "seq": message.seq, "ok": False})
            return
        try:
            if message.type == "binds.get":
                await ws.send_json({"type": "binds", "binds": binds_for_phone(hyprland.binds())})
            elif message.type == "windows.get":
                await ws.send_json({"type": "windows", "windows": windows_for_phone(hyprland.clients())})
            else:
                p = message.payload
                hyprland.window_act(p["address"], p["action"], p.get("workspace"))
                await ws.send_json({"type": "ack", "seq": message.seq, "ok": True})
                await ws.send_json({"type": "windows", "windows": windows_for_phone(hyprland.clients())})
        except (OSError, ValueError, KeyError):
            if message.type == "window.act":
                await ws.send_json({"type": "ack", "seq": message.seq, "ok": False})

    async def _menu(ws, message):
        """Omarchy's menu on the phone: the tree, running one item (its own action), the real menu on the PC."""
        p = message.payload
        if message.type == "menu.get":
            await ws.send_json({"type": "menu", "items": await menu.items(), "apps": apps()})
            return
        ok = True
        if message.type == "menu.show":
            await launch(["omarchy-menu", "summon", p["route"]])
        elif p["id"].startswith("app:"):
            app_id = p["id"][4:]
            ok = any(a["id"] == app_id for a in apps())
            if ok:
                await launch(["setsid", "-f", "uwsm-app", "--", f"{app_id}.desktop"])
        else:
            action = await menu.action(p["id"])
            ok = action is not None
            if ok:
                await launch(["setsid", "-f", "bash", "-lc", action])
        await ws.send_json({"type": "ack", "seq": message.seq, "ok": ok})

    async def _run_one(ws, message):
        try:
            for reply in await agent_commands.handle(message):
                await ws.send_json(reply)
        except (ConnectionResetError, RuntimeError):
            pass

    async def _run_agent_commands(ws, queue):
        while True:
            message = await queue.get()
            try:
                for reply in await agent_commands.handle(message):
                    await ws.send_json(reply)
            except (ConnectionResetError, RuntimeError):
                return

    async def _push_agents(ws, session):
        """The herdr agents as the phone sees them: the list when it changes, an alert when one
        becomes blocked or done. herdr down or missing is itself a state the phone shows."""
        alerts = Alerts()
        last = None
        while not session.closed:
            try:
                listed = summarize_agents(await herdr.agents())
                try:
                    # Subagents are extra: whatever goes wrong counting them, the list still goes out.
                    listed = await agent_commands.with_subagents(listed) if agent_commands is not None else listed
                except Exception as error:  # noqa: BLE001
                    log.debug("subagents not counted: %r", error)
                current = (listed, True)
            except (HerdrError, KeyError, TypeError, ValueError) as error:
                # herdr down, or an answer this version does not understand: shown as unavailable.
                if not isinstance(error, HerdrError):
                    log.warning("unexpected herdr agent list: %r", error)
                current = ([], False)
            try:
                if current != last:
                    last = current
                    waiting = sum(1 for a in current[0] if a["status"] in ("blocked", "done"))
                    if waiting != hub.waiting:
                        hub.waiting = waiting
                        hub.write()
                    await ws.send_json({"type": "agents", "agents": current[0], "available": current[1]})
                # While herdr is unreachable nothing is known about the agents: keep the last states,
                # or every agent still blocked/done would alert again when herdr answers.
                if current[1]:
                    for agent in alerts.update(current[0], time.monotonic()):
                        await ws.send_json({"type": "agent.alert", "agent": agent})
            except (ConnectionResetError, RuntimeError):
                return
            await asyncio.sleep(agent_poll)

    async def _push_workspaces(ws, session):
        """Keeps the phone's view of the desktop current, without video (design §6): workspaces and the
        monitor layout when they change, and the cursor position up to 15 times per second."""
        last = {"workspaces": None, "monitors": None, "cursor": None}
        tick = 0
        while not session.closed:
            try:
                # Layout and workspaces: when Hyprland reports a change (and every 5 s as a safety
                # net); without the event socket, about twice per second as before.
                if events is not None:
                    due = tick == 0 or events.changed.is_set() or tick % 75 == 0
                    events.changed.clear()
                else:
                    due = tick % 8 == 0
                if due:
                    monitors = hyprland.monitors()
                    layout = monitor_map(monitors)
                    updates = {"workspaces": summarize(hyprland.workspaces(), monitors), "monitors": layout}
                else:
                    layout, updates = last["monitors"], {}
                if layout is not None and session.watch_cursor:
                    position = cursor_on(*hyprland.cursor_pos(), layout)
                    updates["cursor"] = position
                for kind, value in updates.items():
                    if value != last[kind]:
                        last[kind] = value
                        if kind == "cursor":
                            await ws.send_json({"type": "cursor", **value} if value else {"type": "cursor", "monitor": None})
                        else:
                            await ws.send_json({"type": kind, kind: value})
            except (OSError, ValueError, KeyError):
                pass
            tick += 1
            await asyncio.sleep(1 / 15)

    async def _watch(ws, session):
        while not session.closed:
            await asyncio.sleep(min(1.0, timeout / 2))
            if session.expired(time.monotonic()):
                log.warning("session %s timed out", session.session_id[:8])
                session.close()
                await ws.close()

    def cursor_report(geometry) -> dict:
        x, y = hyprland.cursor_pos()
        if clamp_to(x, y, geometry):
            return {"type": "cursor", "inside": False}
        nx, ny = to_normalized(x, y, geometry)
        return {"type": "cursor", "inside": True, "x": round(nx, 4), "y": round(ny, 4)}

    async def report_cursor(ws, geometry, report):
        """Tells the phone where the cursor is (trackpad mode). Without a report of its own (Tela extra,
        where the phone shows only that monitor) the cursor is kept on the monitor being viewed."""
        if report is None:
            inside = clamp_to(*hyprland.cursor_pos(), geometry)
            if inside:
                hyprland.move_cursor(*inside)
            await ws.send_json(cursor_report(geometry))
        else:
            await ws.send_json(report())

    async def follow_cursor(ws, report, first: dict):
        """Also reports moves made with the PC's own mouse, so the phone's ring stays right."""
        last = first
        while not ws.closed:
            await asyncio.sleep(0.1)
            try:
                current = report()
            except (OSError, ValueError, KeyError):
                continue
            if current != last:
                await ws.send_json(current)
                last = current

    async def stream(ws, process, geometry, view_only=False, report=None, control=None, layout=None, follow=None):
        """Video to the phone while touches/pointer messages come back; returns when either side ends.
        view_only (preview): every input message is ignored. report: the cursor message (Ver PC lets the
        cursor leave for another monitor and says which); None keeps it on this monitor.
        control(data): screen.switch / screen.quality on the same connection; returns the new
        (process, geometry, report), or None when refused. follow(): true when what is shown moved
        (the focused window changed): the capture restarts as if the phone had asked."""
        pressed = False
        pen_at = None  # the pen's last position while it is in range here
        rest = [0.0, 0.0]  # sub-pixel remainder of relative moves

        async def forward_video():
            marked = True
            while True:
                try:
                    chunk = await asyncio.wait_for(process.stdout.read(65536), FRAME_QUIET)
                except asyncio.TimeoutError:
                    # The encoder writes each frame in one go: a pause means the frame is whole. The
                    # phone only sees a frame end when the next NAL starts, so mark it now.
                    if not marked:
                        await ws.send_bytes(ACCESS_UNIT_DELIMITER)
                        marked = True
                    chunk = await process.stdout.read(65536)
                if not chunk:
                    break
                await ws.send_bytes(chunk)
                marked = False
            await ws.close()  # capture ended (e.g. the monitor went away)

        pump = asyncio.create_task(forward_video())
        switching = asyncio.Lock()

        async def switch(data):
            """Only the capture restarts; the phone gets a new "screen" message before its frames."""
            nonlocal pump, process, geometry, report, rest
            async with switching:
                pump.cancel()
                old = process
                changed = await control(data)
                if changed is None:
                    pump = asyncio.create_task(forward_video())  # refused: keep streaming the old one
                    return
                process, geometry, report = changed  # first: from here the finally below owns it
                await stop_capture(old)
                rest = [0.0, 0.0]
                pump = asyncio.create_task(forward_video())

        async def follow_focus():
            while True:
                await asyncio.sleep(0.5)
                try:
                    moved = follow()
                except (OSError, ValueError, KeyError):
                    continue
                if moved:
                    await switch({"type": "screen.follow"})

        follower = asyncio.create_task(follow_focus()) if follow is not None and control is not None else None
        try:
            async for message in ws:
                if message.type != WSMsgType.TEXT:
                    break
                try:
                    data = json.loads(message.data)
                except ValueError:
                    break
                kind = data.get("type") if isinstance(data, dict) else None
                if kind == "video.ping":  # round trip for the phone's latency readout
                    await ws.send_json({"type": "video.pong", "t": data.get("t")})
                    continue
                if kind in ("screen.switch", "screen.quality") and control is not None:
                    await switch(data)
                    continue
                if view_only:
                    continue
                if kind == "pen" and layout:
                    try:
                        state, px, py, pressure = parse_pen(data)
                        x, y = pen_position(px, py, geometry, layout)
                        pen_device().write(state, x, y, pressure)
                        pen_at = None if state == "out" else (x, y)
                    except (DisplayError, OSError) as error:
                        log.warning("pen: %s", error)
                    continue
                try:
                    if isinstance(data, dict) and data.get("type") == "pointer":
                        action, dx, dy, value = parse_pointer(data)
                    else:
                        action, nx, ny, steps = parse_touch(data)
                except (DisplayError, ValueError):
                    break
                if data.get("type") == "pointer":
                    # Trackpad mode: relative moves (with the compositor's acceleration), actions at the cursor.
                    if action == "rel":
                        rest[0] += dx * geometry.width
                        rest[1] += dy * geometry.height
                        mx, my = int(rest[0]), int(rest[1])
                        rest[0] -= mx
                        rest[1] -= my
                        if mx or my:
                            injector.move(mx, my)
                            await report_cursor(ws, geometry, report)
                    elif action == "click":
                        injector.buttons({1: 1, 2: 2, 3: 4}[value])  # left, right, middle masks
                        injector.buttons(0)
                    elif action == "press":
                        injector.buttons(1)
                        pressed = True
                    elif action == "release":
                        injector.buttons(0)
                        pressed = False
                    elif action == "scroll":
                        injector.scroll(value)
                    elif action == "hscroll":
                        injector.hscroll(value)
                    continue
                hyprland.move_cursor(*to_global(nx, ny, geometry))
                if action == "down":
                    injector.buttons(1)
                    pressed = True
                elif action == "up":
                    injector.buttons(0)
                    pressed = False
                elif action == "right":
                    injector.buttons(2)
                    injector.buttons(0)
                elif action == "scroll":
                    injector.scroll(steps)
                elif action == "hscroll":
                    injector.hscroll(steps)
        finally:
            if follower is not None:
                follower.cancel()
            pump.cancel()
            await stop_capture(process)
            if pressed:
                injector.buttons(0)
            if pen_at is not None:  # the link went with the pen near or on the screen: lift it
                try:
                    pen_device().write("out", *pen_at, 0)
                except OSError as error:
                    log.warning("pen: %s", error)

    async def open_stream(request, key: str, needs_desktop: bool = True, max_msg_size: int = MAX_FRAME):
        """Authorized WebSocket with protocol pings, one per kind (video, term). A new one (switching
        monitor, the app coming back) takes over: the previous is closed and cleaned up first."""
        await authorize(request)
        # Protocol-level pings detect a phone that vanished.
        ws = web.WebSocketResponse(max_msg_size=max_msg_size, heartbeat=5.0)
        await ws.prepare(request)
        previous = state[key]
        if previous is not None:
            await previous.close()
            for _ in range(50):  # its handler stops the capture / child process first
                if state[key] is None:
                    break
                await asyncio.sleep(0.1)
        if state[key] is not None or (needs_desktop and hyprland is None):
            await ws.send_json({"type": "error", "message": "Indisponível no momento."})
            await ws.close()
            return ws, False
        state[key] = ws
        return ws, True

    async def display(request: web.Request) -> web.StreamResponse:
        """Extra monitor: create it, stream it, map touches to it, remove it on exit."""
        ws, ok = await open_stream(request, "video")
        if not ok:
            return ws
        created = False
        try:
            try:
                width, height, scale = parse_start(await ws.receive_json(timeout=5))
            except (DisplayError, ValueError, TypeError, asyncio.TimeoutError) as error:
                await ws.send_json({"type": "error", "message": f"Pedido de tela inválido: {error}"})
                return ws
            geometry = hyprland.create_output(width, height, scale)
            created = True
            process = await capture()
            await ws.send_json({"type": "display", "width": width, "height": height})
            log.info("display %dx%d@%s at %s", width, height, scale, geometry)
            await stream(ws, process, geometry)
        finally:
            if created:
                hyprland.remove_output()
            if state["video"] is ws:
                state["video"] = None
            await ws.close()
            log.info("display ended")
        return ws

    async def screen(request: web.Request) -> web.StreamResponse:
        """One of the user's own monitors, scaled to fit the phone, with touch input on it."""
        ws, ok = await open_stream(request, "video")
        if not ok:
            return ws
        try:
            monitors = real_monitors(hyprland.monitors())
            await ws.send_json({
                "type": "monitors",
                "monitors": [{"name": n, "width": g.width, "height": g.height} for n, g in monitors.items()],
            })
            try:
                # Input from a swipe that was going on while switching monitor may arrive first: skip it.
                deadline = time.monotonic() + 10
                while True:
                    start = await ws.receive_json(timeout=max(0.1, deadline - time.monotonic()))
                    if not (isinstance(start, dict) and start.get("type") in ("pointer", "touch")):
                        break
                name, max_w, max_h = parse_screen_start(start, set(monitors))
                view_only, fps = parse_screen_options(start)
                scale = start.get("scale", 1.0)
                if not isinstance(scale, (int, float)) or isinstance(scale, bool) or not 0.25 <= scale <= 1:
                    raise DisplayError("invalid scale")
            except (DisplayError, ValueError, TypeError, asyncio.TimeoutError) as error:
                await ws.send_json({"type": "error", "message": f"Pedido de monitor inválido: {error}"})
                return ws
            geometry = monitors[name]
            width, height = fit_size(geometry.width, geometry.height, int(max_w * scale), int(max_h * scale))
            process = await capture(name, (width, height), fps)
            await ws.send_json({"type": "screen", "monitor": name, "width": width, "height": height})
            log.info("screen %s as %dx%d @%d fps%s", name, width, height, fps, " (preview)" if view_only else "")
            if not view_only and clamp_to(*hyprland.cursor_pos(), geometry):
                # Bring the cursor onto the monitor being viewed, at its center.
                hyprland.move_cursor(*to_global(0.5, 0.5, geometry))
            # window: showing only the focused window (its address and rectangle in "focus").
            current = {"name": name, "max": (max_w, max_h), "fps": fps, "scale": float(scale), "window": False, "focus": None}

            def report():
                if current["window"] and current["focus"] is not None:
                    # Only inside or not: the phone must not follow the cursor to a monitor.
                    return cursor_message(*hyprland.cursor_pos(), "janela", {"janela": current["focus"][3]})
                return cursor_message(*hyprland.cursor_pos(), current["name"], monitors)

            def focus_moved():
                if not current["window"]:
                    return False
                now = focused_window(hyprland.active_window(), monitors)
                return now is not None and (now[0], now[3]) != (current["focus"][0], current["focus"][3])

            async def control(data):
                """Another monitor or another quality on the same connection: only the capture restarts."""
                try:
                    if data["type"] == "screen.switch" and data.get("window") is True:
                        current["window"] = True
                    elif data["type"] == "screen.switch":
                        target = data.get("monitor")
                        if target not in monitors:
                            raise DisplayError("unknown monitor")
                        current["name"], current["window"], current["focus"] = target, False, None
                    elif data["type"] == "screen.follow":
                        pass  # the focused window changed: read it again below
                    else:
                        fps_, scale_ = data.get("fps"), data.get("scale")
                        if type(fps_) is not int or not 1 <= fps_ <= 60 or not isinstance(scale_, (int, float)) or not 0.25 <= scale_ <= 1:
                            raise DisplayError("invalid quality")
                        current["fps"], current["scale"] = fps_, float(scale_)
                except DisplayError as error:
                    await ws.send_json({"type": "error", "message": f"Pedido de monitor inválido: {error}"})
                    return None
                screen_message = {"type": "screen"}
                if current["window"]:
                    focus = focused_window(hyprland.active_window(), monitors)
                    if focus is None:
                        current["window"] = current["focus"] is not None  # still showing the last one, if any
                        if current["focus"] is None:
                            await ws.send_json({"type": "error", "message": "Nenhuma janela em foco no PC."})
                        return None
                    current["focus"], current["name"] = focus, focus[2]
                    g = focus[3]
                    screen_message["window"] = focus[1]
                else:
                    g = monitors[current["name"]]
                w, h = fit_size(g.width, g.height, int(current["max"][0] * current["scale"]), int(current["max"][1] * current["scale"]))
                if current["window"]:
                    new_process = await capture(current["name"], (w, h), current["fps"], region=g)
                else:
                    new_process = await capture(current["name"], (w, h), current["fps"])
                try:
                    await ws.send_json({**screen_message, "monitor": current["name"], "width": w, "height": h})
                except BaseException:  # the phone left (or the session was cancelled) mid-switch
                    new_process.kill()
                    raise
                log.info("screen %s as %dx%d @%d fps (same connection)", current["name"], w, h, current["fps"])
                if not view_only and clamp_to(*hyprland.cursor_pos(), g):
                    hyprland.move_cursor(*to_global(0.5, 0.5, g))
                return new_process, g, report

            first = report()
            await ws.send_json(first)
            follower = asyncio.create_task(follow_cursor(ws, report, first))
            try:
                await stream(ws, process, geometry, view_only, report, control, layout=list(monitors.values()), follow=focus_moved)
            finally:
                follower.cancel()
        finally:
            if state["video"] is ws:
                state["video"] = None
            await ws.close()
            log.info("screen ended")
        return ws

    async def term(request: web.Request) -> web.StreamResponse:
        """A text terminal on the PC (herdr): bytes both ways; the phone sets the size."""
        ws, ok = await open_stream(request, "term", needs_desktop=False, max_msg_size=TERM_MAX_MESSAGE)
        if not ok:
            return ws
        pty = Pty()
        loop = asyncio.get_running_loop()
        queue: asyncio.Queue = asyncio.Queue()
        flow = {"unacked": 0, "reading": False}

        def set_reading(on: bool):
            if on != flow["reading"] and pty.master is not None:
                flow["reading"] = on
                (loop.add_reader(pty.master, on_readable) if on else loop.remove_reader(pty.master))

        def on_readable():
            try:
                data = os.read(pty.master, 65536)
            except BlockingIOError:
                return
            except OSError:  # the child closed its side (detached or exited)
                data = b""
            queue.put_nowait(data or None)
            if not data:
                set_reading(False)
                return
            flow["unacked"] += len(data)
            if flow["unacked"] >= term_window:
                set_reading(False)  # the phone has not drawn enough yet: let the program wait

        async def forward():
            try:
                while (chunk := await queue.get()) is not None:
                    await ws.send_bytes(chunk)
            except (ConnectionResetError, RuntimeError):
                pass
            await ws.close()

        pump = None
        try:
            try:
                cols, rows, session_name = parse_term_start(await ws.receive_json(timeout=10))
            except (TermError, ValueError, TypeError, asyncio.TimeoutError) as error:
                await ws.send_json({"type": "error", "message": f"Pedido de terminal inválido: {error}"})
                return ws
            try:
                await pty.spawn(term_command(session_name), cols, rows)
            except OSError as error:
                await ws.send_json({"type": "error", "message": f"Não foi possível abrir o terminal: {error.strerror}"})
                return ws
            set_reading(True)
            pump = asyncio.create_task(forward())
            await ws.send_json({"type": "term", "cols": cols, "rows": rows, "session": session_name})
            log.info("terminal %s as %dx%d", session_name, cols, rows)
            async for message in ws:
                if message.type == WSMsgType.BINARY:
                    await pty.write(message.data)
                elif message.type == WSMsgType.TEXT:
                    try:
                        data = json.loads(message.data)
                        if isinstance(data, dict) and data.get("type") == "term.ack":
                            flow["unacked"] = max(0, flow["unacked"] - parse_term_ack(data))
                            if flow["unacked"] < term_window:
                                set_reading(True)
                        else:
                            pty.resize(*parse_term_resize(data))
                    except (TermError, ValueError):
                        break
                else:
                    break
        finally:
            if pump is not None:
                pump.cancel()
            set_reading(False)
            await pty.close()
            if state["term"] is ws:
                state["term"] = None
            await ws.close()
            log.info("terminal ended")
        return ws

    async def receive_file(request: web.Request) -> web.Response:
        """A file shared on the phone: streamed into Downloads under a new name, then a notice on the PC."""
        await authorize(request)
        raw_name = urllib.parse.unquote(request.headers.get("X-Keypad-Name", ""))
        name = safe_name(raw_name)
        if (request.content_length or 0) > MAX_UPLOAD:
            raise web.HTTPRequestEntityTooLarge(max_size=MAX_UPLOAD, actual_size=request.content_length)
        agent = request.headers.get("X-Keypad-Agent")
        if agent is not None:
            # For an agent: into its project, under a name its @mention can hold.
            if not AGENT_TARGET.fullmatch(agent):
                return web.json_response({"ok": False, "error": "invalid agent"}, status=400)
            cwd = await herdr.cwd_of(agent) if herdr is not None else None
            folder = await asyncio.to_thread(agent_inbox, cwd)
            if folder is None:
                return web.json_response({"ok": False, "error": "O agente não está mais no herdr."}, status=409)
            target = folder / agent_name(folder, raw_name)
        else:
            folder = Path(downloads) if downloads is not None else downloads_dir()
            folder.mkdir(parents=True, exist_ok=True)  # it may have been removed since the service started
            target = unique_path(folder, name)
        partial = target.with_name(target.name + ".part")
        received = 0
        try:
            with open(partial, "xb") as out:
                async for chunk in request.content.iter_chunked(1 << 16):
                    received += len(chunk)
                    if received > MAX_UPLOAD:
                        raise web.HTTPRequestEntityTooLarge(max_size=MAX_UPLOAD, actual_size=received)
                    out.write(chunk)
            if agent is None:
                target = unique_path(folder, name)  # another file may have taken the name meanwhile
            elif target.exists():
                target = folder / agent_name(folder, target.name)
            os.replace(partial, target)
        finally:
            partial.unlink(missing_ok=True)
        log.info("received a file from the phone (%d bytes)", received)
        if agent is not None:
            hub.notify("Recebido do celular", f"{target.name} para o agente")
            return web.json_response({"ok": True, "name": target.name, "ref": f"{AGENT_FOLDER}/{target.name}", "path": str(target)})
        hub.notify("Recebido do celular", f"{target.name} em {folder.name}")
        return web.json_response({"ok": True, "name": target.name, "path": str(target)})

    app = web.Application()
    app[HUB_KEY] = hub
    app.router.add_get("/v1", handle)
    app.router.add_get("/v1/display", display)
    app.router.add_get("/v1/screen", screen)
    app.router.add_get("/v1/term", term)
    app.router.add_post("/v1/file", receive_file)
    app.router.add_get("/v1/file/{offer_id}", send_file)
    return app
