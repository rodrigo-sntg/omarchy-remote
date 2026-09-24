"""The phone's notifications on the PC (the phone sends them; the person opted in on the phone).
Each shows as a desktop notification with Responder (when the phone can answer it) and Dispensar;
Responder asks for the text with Omarchy's own input box, and the phone sends it as the reply."""
import asyncio
import logging
import shutil

log = logging.getLogger(__name__)
APP_NAME = "omarchy-remote"   # notifications.py skips this app: no echo back to the phone


def notify_args(n: dict) -> list[str]:
    args = ["notify-send", f"--app-name={APP_NAME}", "--print-id", "--wait", "--icon=phone"]
    # Omarchy's notifications have no buttons: clicking one runs its default action. A message that
    # can be answered opens the reply box; any other is dismissed on the phone (seen).
    args.append("--action=default=Responder" if n.get("reply") else "--action=default=Dispensar")
    title = f"{n['app']} · {n['title']}" if n.get("title") else n["app"]
    return args + [title, n.get("text") or ""]


async def notify_send(args):
    """notify-send prints the id, then (with --wait) the action clicked, or nothing when closed."""
    process = await asyncio.create_subprocess_exec(*args, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
    try:
        while line := await process.stdout.readline():
            yield line.decode(errors="replace").strip()
    finally:
        if process.returncode is None:
            process.kill()


async def menu_input(prompt: str) -> str:
    tool = shutil.which("omarchy-menu-input")
    if not tool:
        return ""
    process = await asyncio.create_subprocess_exec(tool, prompt, "--width", "520", stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
    out, _ = await process.communicate()
    return out.decode(errors="replace").strip()


def close_notification(notification_id: int):
    import subprocess
    subprocess.Popen(["busctl", "--user", "call", "org.freedesktop.Notifications", "/org/freedesktop/Notifications",
                      "org.freedesktop.Notifications", "CloseNotification", "u", str(notification_id)],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


class PhoneNotices:
    def __init__(self, hub, show=notify_send, ask=menu_input, close=close_notification):
        self.hub, self.show, self.ask, self.close = hub, show, ask, close
        self.ids: dict[str, int] = {}
        self.tasks: dict[str, asyncio.Task] = {}

    async def posted(self, n: dict):
        key = n["key"]
        old = self.tasks.pop(key, None)
        if old:
            old.cancel()
        if key in self.ids:
            self.close(self.ids.pop(key))
        self.tasks[key] = asyncio.create_task(self._run(n))

    async def removed(self, key: str):
        task = self.tasks.pop(key, None)
        if task:
            task.cancel()
        if key in self.ids:
            self.close(self.ids.pop(key))

    async def _run(self, n: dict):
        key = n["key"]
        try:
            first = True
            async for line in self.show(notify_args(n)):
                if first:
                    first = False
                    if line.isdigit():
                        self.ids[key] = int(line)
                    continue
                if line == "default" and n.get("reply") and self.ask:
                    text = await self.ask(f"Responder a {n.get('title') or n['app']}")
                    if text:
                        await self.hub.push({"type": "phone.reply", "key": key, "text": text[:2000]})
                elif line == "default":
                    await self.hub.push({"type": "phone.dismiss", "key": key})
                break
        except OSError as error:
            log.warning("phone notification: %s", error)
        finally:
            self.ids.pop(key, None)
            if self.tasks.get(key) is asyncio.current_task():
                self.tasks.pop(key, None)

    async def wait(self):
        """Tests: until the notifications shown so far are done."""
        await asyncio.gather(*self.tasks.values(), return_exceptions=True)
