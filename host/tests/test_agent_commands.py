import json

from keypad_host.agent_commands import commands_for, frontmatter, installed_claude, installed_codex


def skill(folder, name, description, extra=""):
    folder.mkdir(parents=True)
    (folder / "SKILL.md").write_text(f"---\nname: {name}\ndescription: {description}\n{extra}---\n\nBody\n")


def test_frontmatter_reads_plain_quoted_and_folded_values():
    assert frontmatter('---\nname: "imagegen"\ndescription: >\n  Generate images\n  for sites.\n---\nx') == {"name": "imagegen", "description": "Generate images for sites."}
    assert frontmatter("no frontmatter") == {}
    assert frontmatter("---\ndescription: Code review a pull request\nallowed-tools: Bash(gh:*)\n---") == \
        {"description": "Code review a pull request", "allowed-tools": "Bash(gh:*)"}


def test_claude_codes_installed_skills_commands_and_plugins(tmp_path):
    home = tmp_path / "home"
    project = tmp_path / "app"
    skill(home / ".claude/skills/omarchy", "omarchy", "Customize the desktop")
    skill(home / ".claude/skills/hidden", "hidden", "Model only", "user-invocable: false\n")
    (home / ".claude/commands").mkdir(parents=True)
    (home / ".claude/commands/deploy.md").write_text("---\ndescription: Deploy it\n---\nDo it")
    (project / ".claude/commands").mkdir(parents=True)
    (project / ".claude/commands/release.md").write_text("Cut a release\n\nsteps")
    plugin = tmp_path / "cache/superpowers/6.4.1"
    skill(plugin / "skills/brainstorming", "brainstorming", "Use before creative work")
    (plugin / "commands").mkdir(parents=True)
    (plugin / "commands/brainstorm.md").write_text("---\ndescription: Start brainstorming\n---\n")
    off = tmp_path / "cache/stripe/1"
    skill(off / "skills/test-cards", "test-cards", "Stripe test cards")
    (home / ".claude/plugins").mkdir(parents=True)
    (home / ".claude/plugins/installed_plugins.json").write_text(json.dumps({"version": 2, "plugins": {
        "superpowers@official": [{"scope": "user", "installPath": str(plugin)}],
        "stripe@official": [{"scope": "user", "installPath": str(off)}],
    }}))
    (home / ".claude/settings.json").write_text(json.dumps({"enabledPlugins": {"superpowers@official": True, "stripe@official": False}}))
    got = {c["n"]: c for c in installed_claude(str(project), home)}
    assert got["/omarchy"] == {"n": "/omarchy", "d": "Customize the desktop", "g": "skill"}
    assert "/hidden" not in got
    assert got["/deploy"]["d"] == "Deploy it"
    assert got["/release"] == {"n": "/release", "d": "Cut a release", "g": "project"}
    assert got["/superpowers:brainstorming"] == {"n": "/superpowers:brainstorming", "d": "Use before creative work", "g": "superpowers"}
    assert got["/superpowers:brainstorm"]["d"] == "Start brainstorming"
    assert "/stripe:test-cards" not in got  # a disabled plugin


def test_codexs_skills_are_dollar_mentions(tmp_path):
    home = tmp_path / "home"
    skill(home / ".codex/skills/.system/imagegen", '"imagegen"', "Generate or edit images")
    skill(home / ".codex/skills/omarchy", "omarchy", "Customize the desktop")
    got = installed_codex(None, home)
    assert {"n": "$imagegen", "d": "Generate or edit images", "g": "skill"} in got
    assert {"n": "$omarchy", "d": "Customize the desktop", "g": "skill"} in got


def test_the_menu_is_the_built_ins_then_whats_installed_without_repeats(tmp_path):
    home = tmp_path / "home"
    skill(home / ".claude/skills/omarchy", "omarchy", "Customize the desktop")
    claude = commands_for("claude", None, home)
    names = [c["n"] for c in claude]
    assert "/model" in names and "/compact" in names and "/omarchy" in names
    assert len(names) == len(set(names))
    model = next(c for c in claude if c["n"] == "/model")
    assert model["pt"] and model["live"] is True  # opens a picker in the terminal
    codex = [c["n"] for c in commands_for("codex", None, home)]
    assert "/model" in codex and "/review" in codex
    assert commands_for("pi", None, home) == []
