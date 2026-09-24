"""A terminal for the phone (docs/PLANO-V2.md §4.2): a pseudo-terminal running herdr, raw bytes
both ways over a WebSocket, resized from the phone."""
import asyncio
import fcntl
import os
import re
import signal
import struct
import termios

_SESSION = re.compile(r"[a-z0-9_-]{1,32}")
COLS = (20, 500)
ROWS = (5, 200)


class TermError(ValueError):
    pass


def _size(message: dict) -> tuple[int, int]:
    cols, rows = message.get("cols"), message.get("rows")
    if type(cols) is not int or type(rows) is not int or not COLS[0] <= cols <= COLS[1] or not ROWS[0] <= rows <= ROWS[1]:
        raise TermError("invalid size")
    return cols, rows


def parse_term_start(message) -> tuple[int, int, str]:
    if not isinstance(message, dict) or message.get("type") != "term.start":
        raise TermError("expected term.start")
    cols, rows = _size(message)
    session = message.get("session", "default")
    if not isinstance(session, str) or not _SESSION.fullmatch(session):
        raise TermError("invalid session")
    return cols, rows, session


def parse_term_resize(message) -> tuple[int, int]:
    if not isinstance(message, dict) or message.get("type") != "term.resize":
        raise TermError("expected term.resize")
    return _size(message)


def parse_term_ack(message) -> int:
    """How many output bytes the phone has drawn (flow control)."""
    if not isinstance(message, dict) or message.get("type") != "term.ack":
        raise TermError("expected term.ack")
    count = message.get("bytes")
    if type(count) is not int or not 0 < count <= 1 << 30:
        raise TermError("invalid ack")
    return count


def winsize(rows: int, cols: int) -> bytes:
    return struct.pack("HHHH", rows, cols, 0, 0)


def herdr_command(session: str) -> list[str]:
    """Attach to (or start) the named persistent herdr session; the server outlives this client."""
    return ["herdr", "--session", session]


def child_env(base: dict) -> dict:
    """The environment for the terminal's child: a fresh xterm, and never inside a herdr pane
    (herdr refuses to nest, and the host may itself have been started from one)."""
    env = {key: value for key, value in base.items() if not key.startswith("HERDR_")}
    env.update(TERM="xterm-256color", COLORTERM="truecolor")
    return env


def _controlling_terminal():
    # In the child, after fds 0/1/2 already point at the slave: become a session leader and
    # make that slave the controlling terminal, as TUIs that open /dev/tty expect.
    os.setsid()
    fcntl.ioctl(0, termios.TIOCSCTTY, 0)


class Pty:
    """One child on a pseudo-terminal. Read the master with loop.add_reader; write and resize here."""

    def __init__(self):
        self.master = None
        self.process = None

    async def spawn(self, command: list[str], cols: int, rows: int):
        master, slave = os.openpty()
        fcntl.ioctl(slave, termios.TIOCSWINSZ, winsize(rows, cols))
        env = child_env(dict(os.environ))
        try:
            self.process = await asyncio.create_subprocess_exec(
                *command, stdin=slave, stdout=slave, stderr=slave, env=env, preexec_fn=_controlling_terminal,
            )
        except OSError:
            os.close(master)
            raise
        finally:
            os.close(slave)
        os.set_blocking(master, False)
        self.master = master

    def resize(self, cols: int, rows: int):
        if self.master is not None:
            fcntl.ioctl(self.master, termios.TIOCSWINSZ, winsize(rows, cols))

    async def write(self, data: bytes):
        while data and self.master is not None:
            try:
                written = os.write(self.master, data)
            except BlockingIOError:
                await asyncio.sleep(0.01)
                continue
            data = data[written:]

    async def close(self):
        process, self.process = self.process, None
        if process is not None and process.returncode is None:
            try:
                os.killpg(process.pid, signal.SIGHUP)
            except ProcessLookupError:
                pass
            try:
                await asyncio.wait_for(process.wait(), 2.0)
            except asyncio.TimeoutError:
                process.kill()
                await process.wait()
        if self.master is not None:
            os.close(self.master)
            self.master = None
