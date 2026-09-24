"""Waiting for a file to change: inotify (the kernel says when it is written) where there is one,
else a look at its size every `poll` seconds. Nothing else: the reader decides what is new."""
import asyncio
import ctypes
import ctypes.util
import os

_IN_MODIFY = 0x2
_IN_CLOSE_WRITE = 0x8
_IN_NONBLOCK = os.O_NONBLOCK
_IN_CLOEXEC = os.O_CLOEXEC
_COALESCE = 0.15  # an agent writes several lines at once: one wake-up for them


def _inotify(path) -> int | None:
    try:
        libc = ctypes.CDLL(ctypes.util.find_library("c") or "libc.so.6", use_errno=True)
        fd = libc.inotify_init1(_IN_NONBLOCK | _IN_CLOEXEC)
        if fd < 0:
            return None
        if libc.inotify_add_watch(fd, os.fsencode(path), _IN_MODIFY | _IN_CLOSE_WRITE) < 0:
            os.close(fd)
            return None
        return fd
    except (OSError, AttributeError):
        return None


def _drain(fd: int):
    try:
        while os.read(fd, 4096):
            pass
    except (BlockingIOError, OSError):
        pass


async def changes(path, poll: float = 1.0, use_inotify: bool = True):
    """Yields at once (something may have come in meanwhile), then after each change."""
    yield
    fd = _inotify(path) if use_inotify else None
    if fd is None:
        size = os.stat(path).st_size
        while True:
            await asyncio.sleep(poll)
            now = os.stat(path).st_size
            if now != size:
                size = now
                yield
    loop = asyncio.get_running_loop()
    ready = asyncio.Event()
    loop.add_reader(fd, ready.set)
    try:
        while True:
            await ready.wait()
            await asyncio.sleep(_COALESCE)
            ready.clear()
            _drain(fd)
            yield
    finally:
        loop.remove_reader(fd)
        os.close(fd)
