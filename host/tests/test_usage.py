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

    reader = UsageReader(run, ttl=60, clock=lambda: 100.0, home="/nonexistent")
    first = asyncio.run(reader.read())
    again = asyncio.run(reader.read())
    assert len(calls) == 1 and first == again and first["providers"][0]["name"] == "Claude"

    async def missing(args):
        return 127, ""

    assert asyncio.run(UsageReader(missing, home="/nonexistent").read()) == {"type": "usage", "available": False, "providers": []}


def test_another_claude_account_is_its_own_card_named_by_its_account():
    doc = {"entries": [{"id": "anthropic@studio", "display_name": "Claude · studio", "plan": "Claude Team 5x", "metrics": []},
                       {"id": "anthropic", "display_name": "Claude", "plan": "Claude Max 5x", "metrics": []}]}
    studio, own = summarize_usage(doc, NOW)["providers"]
    assert (studio["name"], studio["account"], studio["plan"]) == ("Claude", "studio", "Team 5x")
    assert (own["name"], own["account"], own["plan"]) == ("Claude", "", "Max 5x")


def login(home, name):
    folder = home / f".claude-{name}"
    folder.mkdir()
    (folder / ".credentials.json").write_text("{}")


def test_signed_in_claude_accounts_are_added_to_a_copy_of_the_persons_config(tmp_path):
    from keypad_host.usage import usage_config
    login(tmp_path, "studio")
    login(tmp_path, "work")
    (tmp_path / ".claude-empty").mkdir()  # never signed in: nothing to read
    own = tmp_path / "config.toml"
    own.write_text(f'[zai]\napi_key = "k"\n\n[[anthropic.accounts]]\nlabel = "trabalho"\ncredentials_path = "{tmp_path}/.claude-work/.credentials.json"\n')
    text = usage_config(tmp_path, own)
    assert text.startswith(own.read_text())  # the person's config, untouched, first
    import tomllib
    accounts = tomllib.loads(text)["anthropic"]["accounts"]
    assert [a["label"] for a in accounts] == ["trabalho", "studio"]  # "work" was already there, by its path
    assert accounts[1]["credentials_path"] == str(tmp_path / ".claude-studio/.credentials.json")


def test_without_other_accounts_ai_usagebar_runs_as_always(tmp_path):
    from keypad_host.usage import usage_config
    assert usage_config(tmp_path, tmp_path / "missing.toml") is None
    login(tmp_path, "studio")
    text = usage_config(tmp_path, tmp_path / "missing.toml")  # no config of the person's: only the accounts
    import tomllib
    assert tomllib.loads(text)["anthropic"]["accounts"][0]["label"] == "studio"


def test_an_odd_folder_name_is_a_safe_label(tmp_path):
    from keypad_host.usage import usage_config
    login(tmp_path, 'a"b]\nc')
    import tomllib
    account = tomllib.loads(usage_config(tmp_path, tmp_path / "none.toml"))["anthropic"]["accounts"][0]
    assert account["label"] == "a-b--c"


def test_the_reader_passes_the_config_with_the_accounts(tmp_path):
    calls = []

    async def run(args):
        calls.append(args)
        return 0, json.dumps(DOC)

    login(tmp_path, "studio")
    reader = UsageReader(run, home=tmp_path, own_config=tmp_path / "none.toml", runtime=tmp_path / "run")
    asyncio.run(reader.read())
    assert calls[0][:2] == ["ai-usagebar", "--config"] and calls[0][-2:] == ["usage", "--json"]
    written = tmp_path / "run" / "usage.toml"
    assert calls[0][2] == str(written) and oct(written.stat().st_mode & 0o777) == "0o600"
