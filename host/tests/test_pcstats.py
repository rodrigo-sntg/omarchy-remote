import asyncio

from keypad_host.pcstats import read_stats


def fake_root(tmp_path, busy_after):
    (tmp_path / "proc").mkdir()
    (tmp_path / "proc/meminfo").write_text("MemTotal:       32000000 kB\nMemFree:  1000 kB\nMemAvailable:   24000000 kB\n")
    (tmp_path / "proc/uptime").write_text("7260.5 1000.0\n")
    stat = tmp_path / "proc/stat"
    stat.write_text("cpu  100 0 100 800 0 0 0 0 0 0\n")
    hw = tmp_path / "sys/class/hwmon/hwmon0"
    hw.mkdir(parents=True)
    (hw / "name").write_text("k10temp\n")
    (hw / "temp1_input").write_text("54500\n")
    other = tmp_path / "sys/class/hwmon/hwmon1"
    other.mkdir(parents=True)
    (other / "name").write_text("nvme\n")
    (other / "temp1_input").write_text("70000\n")
    bat = tmp_path / "sys/class/power_supply/BAT0"
    bat.mkdir(parents=True)
    (bat / "capacity").write_text("81\n")
    (bat / "status").write_text("Charging\n")

    async def later():
        stat.write_text(f"cpu  {100 + busy_after} 0 100 {800 + 100 - busy_after} 0 0 0 0 0 0\n")
    return later


def test_cpu_memory_temperature_battery_and_uptime(tmp_path):
    later = fake_root(tmp_path, busy_after=25)

    async def main():
        async def pause(_):
            await later()
        return await read_stats(root=tmp_path, sleep=pause)

    got = asyncio.run(main())
    assert got == {"type": "stats", "cpu": 25, "mem_used": 8.0, "mem_total": 32.0, "temp": 54, "battery": 81, "charging": True, "uptime": 7260}


def test_a_desktop_has_no_battery(tmp_path):
    later = fake_root(tmp_path, busy_after=0)
    import shutil
    shutil.rmtree(tmp_path / "sys/class/power_supply")

    async def main():
        async def pause(_):
            await later()
        return await read_stats(root=tmp_path, sleep=pause)

    got = asyncio.run(main())
    assert got["battery"] is None and got["charging"] is False and got["cpu"] == 0
