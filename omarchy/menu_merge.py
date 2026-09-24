#!/usr/bin/env python3
"""Adds (or refreshes) the omarchy-remote block in Omarchy's user menu extension, a JSONC object.

The block goes right after the object's opening brace, so no comma logic depends on the user's
last entry; a previous block (between its start and end markers) is replaced. The result must
parse, or nothing is written.  Usage: menu_merge.py <omarchy-menu.jsonc> <block.jsonc>
"""
import json
import sys

START = "// omarchy-remote: the Phone menu"
END = "// omarchy-remote end"
# The block as installed when the project was called android-keypad: replaced the same way.
OLD_MARKERS = ("// android-keypad: the Phone menu", "// android-keypad end")


def _strip(text: str) -> str:
    """JSON without comments and trailing commas; strings (with // or /* inside) kept intact."""
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            out.append(text[i:j + 1])
            i = j + 1
        elif text.startswith("//", i):
            i = text.find("\n", i) if "\n" in text[i:] else n
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            i = n if end < 0 else end + 2
        else:
            out.append(c)
            i += 1
    code = "".join(out)
    # Trailing commas before } or ] (outside strings, which are already whole tokens here).
    result, k = [], 0
    while k < len(code):
        if code[k] == ",":
            rest = code[k + 1:].lstrip()
            if rest.startswith("}") or rest.startswith("]"):
                k += 1
                continue
        if code[k] == '"':
            j = k + 1
            while j < len(code) and code[j] != '"':
                j += 2 if code[j] == "\\" else 1
            result.append(code[k:j + 1])
            k = j + 1
            continue
        result.append(code[k])
        k += 1
    return "".join(result)


def parse_jsonc(text: str) -> dict:
    return json.loads(_strip(text) or "{}")


def _opening_brace(text: str) -> int:
    """Index of the first { that is code (not in a comment or string), or -1."""
    i, n = 0, len(text)
    while i < n:
        if text.startswith("//", i):
            i = text.find("\n", i) if "\n" in text[i:] else n
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            i = n if end < 0 else end + 2
        elif text[i] == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            i = j + 1
        elif text[i] == "{":
            return i
        else:
            i += 1
    return -1


def merge(text: str, block: str) -> str:
    if not text.strip():
        text = "{\n}\n"
    parse_jsonc(text)  # the user's file must be valid before we touch it
    start_marker, end_marker = START, END
    if text.find(START) < 0 and text.find(OLD_MARKERS[0]) >= 0:
        start_marker, end_marker = OLD_MARKERS
    start = text.find(start_marker)
    if start >= 0 and text.find(end_marker, start) >= 0:
        end = text.find("\n", text.find(end_marker, start))
        end = len(text) if end < 0 else end  # the marker on the last line, without a newline
        line_start = text.rfind("\n", 0, start) + 1
        merged = text[:line_start] + block.rstrip("\n") + text[end:]
    else:
        brace = _opening_brace(text)
        if brace < 0:
            raise ValueError("no JSON object in the menu extension")
        # The block ends in a // comment: a newline after it, or the user's first entry would join it.
        merged = text[:brace + 1] + "\n" + block.rstrip("\n") + "\n" + text[brace + 1:]
    missing = set(parse_jsonc(text)) - set(parse_jsonc(merged)) - set(parse_jsonc("{" + block + "}"))
    if missing:
        raise ValueError(f"would lose entries: {sorted(missing)}")
    return merged


def remove(text: str) -> str:
    """The user's menu without our block (omarchy-remote-remove)."""
    for start_marker, end_marker in ((START, END), OLD_MARKERS):
        start = text.find(start_marker)
        if start >= 0 and text.find(end_marker, start) >= 0:
            end = text.find("\n", text.find(end_marker, start))
            end = len(text) if end < 0 else end + 1
            text = text[:text.rfind("\n", 0, start) + 1] + text[end:]
    return text


if __name__ == "__main__":
    if sys.argv[1] == "--remove":
        path = sys.argv[2]
        try:
            with open(path) as f:
                current = f.read()
        except FileNotFoundError:
            sys.exit(0)
        with open(path, "w") as f:
            f.write(remove(current))
        sys.exit(0)
    path, block_path = sys.argv[1], sys.argv[2]
    try:
        current = open(path).read()
    except FileNotFoundError:
        current = ""
    try:
        merged = merge(current, open(block_path).read())
    except ValueError as error:
        sys.exit(f"menu extension not changed: {error}")
    with open(path, "w") as f:
        f.write(merged)
