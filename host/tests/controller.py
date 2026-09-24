"""The phone's main session (/v1), open for the length of a test: the screen, terminal and file
routes only serve the device that has it open, as the app does."""
import contextlib

from keypad_host.server import TOKEN_HEADER


@contextlib.asynccontextmanager
async def controller(client, token=None):
    from tests.test_server import TOKEN
    ws = await client.ws_connect("/v1", headers={TOKEN_HEADER: token or TOKEN})
    hello = await ws.receive_json()
    assert hello["type"] == "session", hello
    try:
        yield ws
    finally:
        await ws.close()
