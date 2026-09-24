"""JSONC as Omarchy writes it (comments, trailing commas); strings with // or /* stay intact.
Same parser as omarchy/menu_merge.py, which must stay standalone for install.sh."""
import json


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


def parse(text: str) -> dict:
    return json.loads(_strip(text) or "{}")
