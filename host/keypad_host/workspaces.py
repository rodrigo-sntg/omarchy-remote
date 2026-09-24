"""Workspaces as the phone shows them: regular ones only, in order, with their monitor and state."""


def summarize(workspaces: list[dict], monitors: list[dict]) -> list[dict]:
    active = {m["activeWorkspace"]["id"] for m in monitors}
    focused = {m["activeWorkspace"]["id"] for m in monitors if m.get("focused")}
    return [
        {"id": w["id"], "monitor": w["monitor"], "windows": w["windows"],
         "active": w["id"] in active, "focused": w["id"] in focused}
        for w in sorted(workspaces, key=lambda w: w["id"]) if w["id"] > 0
    ]
