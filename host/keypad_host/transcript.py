"""An agent's whole conversation for the phone, from the session file its CLI keeps: Claude Code's
~/.claude/projects/<dir>/<id>.jsonl, Codex's ~/.codex/sessions/Y/M/D/rollout-…-<id>.jsonl. herdr
says which session a pane runs. These files grow to tens of MB: a page is read backwards from a
byte offset and what is new from the last offset, never the whole file; each line becomes zero or
more chat items (yours, the agent's words, its actions and their output)."""
import json
import re
from datetime import datetime
from pathlib import Path

BLOCK = 64 * 1024
LINE_LIMIT = 4 * 1024 * 1024   # a line past this (an image, a huge output) is skipped, not loaded
FORWARD_LIMIT = 8 * 1024 * 1024
TEXT_LIMIT = 20_000             # characters of a message (a final summary is read whole)
OUTPUT_LIMIT = 4000             # characters of an action's output in a page; the rest on request
FULL_LIMIT = 60_000             # characters of an output fetched whole
TARGET_LIMIT = 300

_SESSION_ID = re.compile(r"[0-9a-fA-F][0-9a-fA-F-]{7,63}")
_BASH_INPUT = re.compile(r"<bash-input>(.*)</bash-input>", re.S)
_COMMAND = re.compile(r"<command-name>(/?[^<]{1,80})</command-name>")
_TARGET_KEYS = ("command", "file_path", "pattern", "url", "query", "notebook_path", "description", "prompt")


def find(kind: str, session_id: str, home: Path | None = None) -> Path | None:
    """The session's file, or None (unknown agent, malformed id, no such file)."""
    if not isinstance(session_id, str) or not _SESSION_ID.fullmatch(session_id):
        return None
    home = home or Path.home()
    if kind == "claude":
        # ~/.claude and any second account kept apart with CLAUDE_CONFIG_DIR=~/.claude-<name>.
        found = sorted(home.glob(f".claude/projects/*/{session_id}.jsonl")) + sorted(home.glob(f".claude-*/projects/*/{session_id}.jsonl"))
    elif kind == "codex":
        found = sorted(home.glob(f".codex/sessions/*/*/*/rollout-*-{session_id}.jsonl"))
    else:
        return None
    return found[-1] if found else None


def _cut(text: str, limit: int) -> tuple[str, bool]:
    return (text, False) if len(text) <= limit else (text[:limit], True)


def _you(text: str) -> dict:
    return {"k": "you", "t": _cut(text.strip(), TEXT_LIMIT)[0]}


def _said(text: str) -> dict:
    return {"k": "said", "t": _cut(text.strip(), TEXT_LIMIT)[0]}


def _target(args) -> str:
    if isinstance(args, str):
        text = args
    elif isinstance(args, dict):
        value = next((args[k] for k in _TARGET_KEYS if isinstance(args.get(k), (str, list))), None)
        if value is None:
            value = next((v for v in args.values() if isinstance(v, str)), "")
        text = " ".join(map(str, value)) if isinstance(value, list) else value
    else:
        text = ""
    return text.strip()[:TARGET_LIMIT]


def _blocks_text(content) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "\n".join(b.get("text", "") for b in content if isinstance(b, dict) and isinstance(b.get("text"), str))
    return ""


def _out(call_id, content, error: bool, limit: int) -> dict:
    text, cut = _cut(_blocks_text(content).strip(), limit)
    return {"k": "out", "id": str(call_id or ""), "t": text, "cut": cut, "err": bool(error)}


COMMAND_LIMIT = 1500
DIFF_LIMIT = 1500  # characters of each side of an edit


def _tool(call_id, name, args) -> dict:
    """An action: what it acts on; for an edit, what changed (the phone shows a diff); for a
    command, why, when the agent said it."""
    item = {"k": "tool", "id": str(call_id or ""), "n": str(name or "")[:60], "t": _target(args)}
    if isinstance(args, dict) and isinstance(args.get("command"), str):
        item["t"] = args["command"].strip()[:COMMAND_LIMIT]  # a command is read whole
    if isinstance(args, dict):
        if isinstance(args.get("old_string"), str) or isinstance(args.get("new_string"), str):
            item["old"] = str(args.get("old_string") or "")[:DIFF_LIMIT]
            item["new"] = str(args.get("new_string") or "")[:DIFF_LIMIT]
        elif isinstance(args.get("edits"), list):
            edits = [e for e in args["edits"] if isinstance(e, dict)]
            item["old"] = "\n…\n".join(str(e.get("old_string", "")) for e in edits)[:DIFF_LIMIT]
            item["new"] = "\n…\n".join(str(e.get("new_string", "")) for e in edits)[:DIFF_LIMIT]
        elif isinstance(args.get("content"), str) and name == "Write":
            item["new"] = args["content"][:DIFF_LIMIT]
        if isinstance(args.get("description"), str) and "command" in args:
            item["why"] = args["description"][:200]
    elif isinstance(args, str) and name == "apply_patch":
        item["patch"] = args[:DIFF_LIMIT * 2]
    return item


def _claude(d: dict, limit: int) -> list[dict]:
    kind = d.get("type")
    if d.get("isSidechain") or d.get("isMeta") or d.get("isCompactSummary"):
        return []
    if kind == "attachment":
        a = d.get("attachment") or {}
        prompt = a.get("prompt")
        # A message sent while the agent was working.
        if a.get("type") == "queued_command" and a.get("humanTurn") and isinstance(prompt, str) and prompt.strip() and not prompt.lstrip().startswith("<"):
            return [_you(prompt)]
        return []
    if kind not in ("user", "assistant"):
        return []
    content = (d.get("message") or {}).get("content")
    if isinstance(content, str):
        content = [{"type": "text", "text": content}]
    if not isinstance(content, list):
        return []
    items = []
    for block in content:
        if not isinstance(block, dict):
            continue
        what = block.get("type")
        if what == "text" and isinstance(block.get("text"), str):
            text = block["text"]
            if kind == "assistant":
                if text.strip():
                    items.append(_said(text))
            elif (bash := _BASH_INPUT.search(text)) is not None:
                items.append(_you("! " + bash.group(1).strip()))
            elif (command := _COMMAND.search(text)) is not None:
                items.append(_you(command.group(1).strip()))
            elif text.strip() and not text.lstrip().startswith("<"):
                items.append(_you(text))
        elif what == "tool_use" and kind == "assistant":
            items.append(_tool(block.get("id"), block.get("name"), block.get("input")))
        elif what == "tool_result" and kind == "user":
            items.append(_out(block.get("tool_use_id"), block.get("content"), block.get("is_error"), limit))
    return items


def _codex(d: dict, limit: int) -> list[dict]:
    if d.get("type") != "response_item":
        return []
    p = d.get("payload") or {}
    what = p.get("type")
    if what == "message":
        text = "\n".join(b.get("text", "") for b in p.get("content") or []
                         if isinstance(b, dict) and b.get("type") in ("input_text", "output_text") and isinstance(b.get("text"), str)).strip()
        if not text:
            return []
        if p.get("role") == "user":
            # Codex hands its instructions and environment to the model as user messages.
            return [] if text.startswith(("#", "<")) else [_you(text)]
        return [_said(text)] if p.get("role") == "assistant" else []
    if what in ("function_call", "custom_tool_call"):
        args = p.get("arguments") if what == "function_call" else p.get("input")
        if what == "function_call" and isinstance(args, str):
            try:
                args = json.loads(args)
            except ValueError:
                pass
        return [_tool(p.get("call_id"), p.get("name"), args)]
    if what in ("function_call_output", "custom_tool_call_output"):
        return [_out(p.get("call_id"), p.get("output"), False, limit)]
    return []


def items_of(line, kind: str, limit: int = OUTPUT_LIMIT) -> list[dict]:
    """One JSONL line as chat items; anything unreadable or internal is no item."""
    try:
        d = json.loads(line)
    except (ValueError, UnicodeDecodeError):
        return []
    if not isinstance(d, dict):
        return []
    items = _claude(d, limit) if kind == "claude" else _codex(d, limit) if kind == "codex" else []
    ts = _epoch(d.get("timestamp"))
    if ts is not None:
        for item in items:
            if item["k"] in ("you", "said"):
                item["ts"] = ts  # when it was said: the chat's time separators, "terminou há 2 min"
    return items


def _epoch(value) -> int | None:
    if not isinstance(value, str) or not value:
        return None
    try:
        return int(datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp())
    except ValueError:
        return None


def _items_at(line: bytes, offset: int, kind: str) -> list[dict]:
    items = items_of(line, kind)
    for item in items:
        if item["k"] == "out" and item["cut"]:
            item["at"] = offset  # where to fetch it whole
    return items


def read_back(path: Path, kind: str, before: int | None, want: int, block: int = BLOCK, line_limit: int = LINE_LIMIT,
              reads: list | None = None) -> dict:
    """The last `want` messages before byte `before` (the end: None). `start` is where the page
    begins (the next page's `before`), `end` where it ends (a line still being written is left out)."""
    with open(path, "rb") as f:
        size = f.seek(0, 2)
        end = size if before is None else max(0, min(before, size))
        pos = end
        buf = b""
        pages: list[list[dict]] = []   # newest line first
        visible = 0
        start = end
        first = True
        skipping = False
        while pos > 0 and visible < want:
            n = min(block, pos)
            pos -= n
            f.seek(pos)
            chunk = f.read(n)
            if reads is not None:
                reads.append(n)
            if skipping:
                cut = chunk.rfind(b"\n")
                if cut < 0:
                    continue
                # The huge line starts right after this newline; what is before it is read as usual.
                buf, skipping = chunk[:cut + 1], False
                start = pos + cut + 1
            else:
                buf = chunk + buf
            if first:
                first = False
                if not buf.endswith(b"\n"):
                    # A line still being written: it belongs to the next read_forward.
                    cut = buf.rfind(b"\n")
                    end = pos + cut + 1 if cut >= 0 else pos
                    buf = buf[:cut + 1] if cut >= 0 else b""
                    start = end
            parts = buf.split(b"\n")[:-1]   # complete lines end with \n
            head = parts[0] if pos > 0 and parts else None  # may be only a line's tail
            complete = parts[1:] if head is not None else parts
            offset = pos + (len(head) + 1 if head is not None else 0)
            offsets = []
            for line in complete:
                offsets.append(offset)
                offset += len(line) + 1
            for line, at in zip(reversed(complete), reversed(offsets)):
                if visible >= want:
                    break
                items = _items_at(line, at, kind)
                pages.append(items)
                visible += sum(1 for i in items if i["k"] != "out")
                start = at
            if visible >= want:
                break
            buf = (head + b"\n") if head is not None else b""
            if len(buf) > line_limit:
                buf, skipping = b"", True
        more = start > 0
        items = [item for page in reversed(pages) for item in page]
        return {"items": items, "start": start, "end": end, "more": more}


def read_forward(path: Path, kind: str, after: int, limit: int = FORWARD_LIMIT) -> tuple[list[dict], int]:
    """What was written after byte `after`, whole lines only; and where to continue from."""
    with open(path, "rb") as f:
        size = f.seek(0, 2)
        if after >= size:
            return [], min(after, size)
        f.seek(after)
        data = f.read(min(limit, size - after))
        cut = data.rfind(b"\n")
        if cut < 0:
            if len(data) < limit:
                return [], after  # half a line: next time
            # One line past the limit: skip to its end.
            f.seek(after + len(data))
            skipped = after + len(data)
            while chunk := f.read(BLOCK):
                nl = chunk.find(b"\n")
                if nl >= 0:
                    return [], skipped + nl + 1
                skipped += len(chunk)
            return [], after
        items = []
        offset = after
        for line in data[:cut].split(b"\n"):
            items += _items_at(line, offset, kind)
            offset += len(line) + 1
        return items, after + cut + 1


def full_output(path: Path, kind: str, at: int, call_id: str) -> str | None:
    """An action's whole output, from the line at byte `at` (as a page gave it)."""
    with open(path, "rb") as f:
        size = f.seek(0, 2)
        if not isinstance(at, int) or not 0 <= at < size:
            return None
        if at > 0:
            f.seek(at - 1)
            if f.read(1) != b"\n":
                return None  # not a line's start
        f.seek(at)
        line = f.readline(LINE_LIMIT)
    for item in items_of(line, kind, FULL_LIMIT):
        if item["k"] == "out" and item["id"] == call_id:
            return item["t"]
    return None
