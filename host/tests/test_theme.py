import asyncio

from keypad_host.theme import COLOR_KEYS, read_theme

OUTPUT = "\n".join([
    "accent\t#798186", "background\t#101315", "bg\t#101315", "dark_background\t#0c0e10",
    "darker_background\t#080a0b", "foreground\t#cacccc", "bright_foreground\t#a5aeb4",
    "muted\t#4b4e55", "selection\t#343d41", "red\t#565d60", "bright_red\t#de6145", "cursor\trgba(1,2,3,1)",
]) + "\n"


def fake_runner(output, name="solitude\n", mode="dark"):
    async def run(args):
        if args[0] == "omarchy-theme-color" and args[1:] == ["--all"]:
            return output
        if args[0] == "omarchy-theme-color" and args[1:] == ["mode", "dark"]:
            return mode + "\n"
        return None
    return run, name


def test_theme_message_has_the_palette_the_app_uses():
    run, name = fake_runner(OUTPUT)
    theme = asyncio.run(read_theme(run, lambda: name))
    assert theme["type"] == "theme" and theme["name"] == "solitude" and theme["mode"] == "dark"
    assert theme["colors"]["accent"] == "#798186"
    assert set(theme["colors"]) == set(COLOR_KEYS)


def test_values_that_are_not_plain_hex_are_left_out():
    run, name = fake_runner("accent\trgba(1,2,3,1)\nbackground\t#101315\nforeground\tred\n")
    theme = asyncio.run(read_theme(run, lambda: name))
    assert theme["colors"] == {"background": "#101315"}


def test_no_omarchy_means_no_theme():
    async def run(args):
        return None
    assert asyncio.run(read_theme(run, lambda: None)) is None


def test_a_theme_can_be_previewed_from_its_file_without_applying_it():
    calls = []

    async def run(args):
        calls.append(args)
        return "accent\t#7aa2f7\nbackground\t#1a1b26\n" if "--all" in args else "dark\n"

    theme = asyncio.run(read_theme(run, lambda: "solitude", file="/usr/share/omarchy/themes/tokyo-night/colors.toml"))
    assert calls[0] == ["omarchy-theme-color", "--file", "/usr/share/omarchy/themes/tokyo-night/colors.toml", "--all"]
    assert theme["name"] == "tokyo-night" and theme["colors"]["accent"] == "#7aa2f7"


def test_preview_names_are_plain_theme_slugs():
    from keypad_host.theme import theme_file
    assert theme_file("tokyo-night", roots=["/nonexistent"]) is None
    assert theme_file("../../etc/passwd") is None
    assert theme_file("Tokyo Night") is None
