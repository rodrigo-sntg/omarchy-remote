"""Desktop state for the phone's cursor map (design §6): monitor layout and where the cursor is."""
from .display import OUTPUT_NAME


def monitor_map(monitors: list[dict]) -> list[dict]:
    """Every monitor in logical coordinates, its visible workspace, and whether it is our virtual one."""
    result = []
    for m in monitors:
        scale = m.get("scale") or 1.0
        result.append({
            "name": m["name"], "x": m["x"], "y": m["y"],
            "width": round(m["width"] / scale), "height": round(m["height"] / scale),
            "workspace": m["activeWorkspace"]["id"], "extra": m["name"] == OUTPUT_NAME,
        })
    return result


def cursor_on(x: int, y: int, monitors: list[dict]) -> dict | None:
    """The monitor under (x, y) and the position on it, normalized 0..1; None if on none."""
    for m in monitors:
        if m["x"] <= x < m["x"] + m["width"] and m["y"] <= y < m["y"] + m["height"]:
            return {"monitor": m["name"], "x": round((x - m["x"]) / m["width"], 4), "y": round((y - m["y"]) / m["height"], 4)}
    return None
