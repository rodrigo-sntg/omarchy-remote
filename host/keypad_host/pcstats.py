"""How the PC is doing, at a glance: CPU use (two readings of /proc/stat), memory, the CPU's
temperature (its hwmon sensor), the battery of a laptop, time since boot."""
import asyncio
from pathlib import Path

CPU_SENSORS = ("k10temp", "coretemp", "zenpower", "cpu_thermal", "acpitz")


def _cpu_times(root: Path) -> tuple[int, int]:
    fields = (root / "proc/stat").read_text().splitlines()[0].split()[1:]
    values = [int(v) for v in fields]
    idle = values[3] + (values[4] if len(values) > 4 else 0)
    return sum(values), idle


def _read(path: Path) -> str | None:
    try:
        return path.read_text().strip()
    except OSError:
        return None


def _temperature(root: Path) -> int | None:
    best = None
    for hw in sorted((root / "sys/class/hwmon").glob("hwmon*")):
        name = _read(hw / "name")
        if name not in CPU_SENSORS:
            continue
        for sensor in sorted(hw.glob("temp*_input")):
            value = _read(sensor)
            if value and value.isdigit():
                best = max(best or 0, int(value) // 1000)
    return best


async def read_stats(root: Path = Path("/"), sleep=asyncio.sleep) -> dict:
    root = Path(root)
    total1, idle1 = _cpu_times(root)
    await sleep(0.25)
    total2, idle2 = _cpu_times(root)
    busy = (total2 - total1) - (idle2 - idle1)
    cpu = round(100 * busy / (total2 - total1)) if total2 > total1 else 0
    mem = {}
    for line in (root / "proc/meminfo").read_text().splitlines():
        key, _, value = line.partition(":")
        mem[key] = int(value.split()[0]) if value.split() else 0
    total_gb = mem.get("MemTotal", 0) / 1e6
    used_gb = (mem.get("MemTotal", 0) - mem.get("MemAvailable", 0)) / 1e6
    battery, charging = None, False
    for bat in sorted((root / "sys/class/power_supply").glob("BAT*")):
        capacity = _read(bat / "capacity")
        if capacity and capacity.isdigit():
            battery, charging = int(capacity), _read(bat / "status") == "Charging"
            break
    uptime = int(float((_read(root / "proc/uptime") or "0").split()[0]))
    return {"type": "stats", "cpu": cpu, "mem_used": round(used_gb, 1), "mem_total": round(total_gb, 1), "temp": _temperature(root),
            "battery": battery, "charging": charging, "uptime": uptime}
