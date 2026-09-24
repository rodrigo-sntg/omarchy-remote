import asyncio
import json
from datetime import datetime, timezone

from keypad_host.protocol import parse
from keypad_host.usage import UsageReader, summarize_usage

NOW = datetime(2026, 9, 24, 3, 0, tzinfo=timezone.utc)
DOC = {"entries": [
    {"id": "anthropic", "display_name": "Claude", "plan": "Claude Max 5x", "stale": False, "error": None, "status": "ready",
     "fetched_at": "2026-09-24T02:57:19Z", "reset_credits": None,
     "metrics": [
         {"label": "Session (5h)", "percent": 76, "reset_at": "2026-09-24T04:30:00Z", "severity": "high", "window_secs": 18000},
         {"label": "Weekly (7d)", "percent": 91, "reset_at": "2026-09-24T16:00:00Z", "severity": "critical", "window_secs": 604800},
         {"label": "Fable (7d)", "percent": 10, "reset_at": "2026-09-24T16:00:00Z", "severity": "low", "window_secs": 604800}]},
    {"id": "openai", "display_name": "Codex", "plan": "ChatGPT Plus", "stale": True, "error": None, "status": "ready",
     "fetched_at": "2026-09-24T02:40:00Z", "reset_credits": {"available": 3, "credits": [{"expires_at": "2026-10-04T00:48:40Z"}]},
     "metrics": [{"label": "Codex 5h", "percent": 0, "reset_at": "2026-09-24T07:57:19Z", "severity": "low", "window_secs": 18000}]},
    {"id": "zai", "display_name": "Z.AI", "plan": None, "stale": False, "error": "401 unauthorized", "status": "error", "metrics": []},
]}


def test_usage_is_trimmed_to_what_the_phone_shows():
    out = summarize_usage(DOC, NOW)
    claude, codex, zai = out["providers"]
    assert out["type"] == "usage"
    assert claude["name"] == "Claude" and claude["plan"] == "Max 5x"  # the vendor's name is not repeated
    session, week, model = claude["metrics"]
    assert (session["kind"], session["percent"], session["resetAt"]) == ("session", 76, "2026-09-24T04:30:00Z")
    # 1h30 of the 5h window left: 70% of it has passed.
    assert session["elapsed"] == 70
    assert (week["kind"], model["kind"], model["label"]) == ("week", "model", "Fable")
    assert codex["stale"] is True and codex["resets"] == 3 and codex["plan"] == "ChatGPT Plus"
    assert zai["error"] == "401 unauthorized" and zai["metrics"] == []


def test_the_phone_asks_with_usage_get():
    assert parse(json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": "usage.get", "payload": {}})).type == "usage.get"


def test_the_reader_caches_and_says_when_ai_usagebar_is_missing():
    calls = []

    async def run(args):
        calls.append(args)
        return 0, json.dumps(DOC)

    reader = UsageReader(run, ttl=60, clock=lambda: 100.0)
    first = asyncio.run(reader.read())
    again = asyncio.run(reader.read())
    assert len(calls) == 1 and first == again and first["providers"][0]["name"] == "Claude"

    async def missing(args):
        return 127, ""

    assert asyncio.run(UsageReader(missing).read()) == {"type": "usage", "available": False, "providers": []}
