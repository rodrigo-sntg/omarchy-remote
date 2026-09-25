import json
import os

from keypad_host.sessions import Remembered, kept_flags, recent, resume_command

CLAUDE_ID = "87aa6636-950f-4a7b-9c68-5c35a87cc1b1"
OTHER_ID = "7a2a2b65-287a-4d90-809f-cc1033d25b36"
CODEX_ID = "01a0ce50-138e-7bb0-adf1-efe275fd8b69"


def claude_session(home, config, session_id, cwd, lines, mtime):
    folder = home / config / "projects" / cwd.replace("/", "-")
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f"{session_id}.jsonl"
    path.write_text("".join(json.dumps(line) + "\n" for line in lines))
    os.utime(path, (mtime, mtime))
    return path


def user(text, cwd="/home/u/proj"):
    return {"type": "user", "cwd": cwd, "message": {"role": "user", "content": text}}


def test_recent_sessions_newest_first_with_title_project_and_account(tmp_path):
    claude_session(tmp_path, ".claude", OTHER_ID, "/home/u/app", [user("arruma os testes", "/home/u/app")], 1000)
    claude_session(tmp_path, ".claude-studio", CLAUDE_ID, "/home/u/studio/nf", [
        user("estimate TASK-12", "/home/u/studio/nf"),
        {"type": "ai-title", "aiTitle": "Estimativa NF"},
        {"type": "ai-title", "aiTitle": "TASK-12 invoice flow"},
    ], 2000)
    codex = tmp_path / ".codex/sessions/2026/09/23" / f"rollout-2026-09-23T09-49-09-{CODEX_ID}.jsonl"
    codex.parent.mkdir(parents=True)
    codex.write_text(json.dumps({"type": "session_meta", "payload": {"id": CODEX_ID, "cwd": "/home/u/app"}}) + "\n"
                     + json.dumps({"type": "response_item", "payload": {"type": "message", "role": "user", "content": [{"type": "input_text", "text": "crie o app"}]}}) + "\n")
    os.utime(codex, (1500, 1500))

    items = recent(tmp_path, open_ids={OTHER_ID})
    assert [i["id"] for i in items] == [CLAUDE_ID, CODEX_ID, OTHER_ID]
    lug = items[0]
    assert lug == {"kind": "claude", "id": CLAUDE_ID, "title": "TASK-12 invoice flow", "cwd": "/home/u/studio/nf",
                   "project": "nf", "account": "studio", "updated": 2000, "open": False}
    assert items[1]["kind"] == "codex" and items[1]["title"] == "crie o app" and items[1]["project"] == "app"
    assert items[2]["title"] == "arruma os testes" and items[2]["account"] == "" and items[2]["open"] is True


def test_a_renamed_session_keeps_the_persons_name(tmp_path):
    claude_session(tmp_path, ".claude", CLAUDE_ID, "/home/u/p", [
        user("oi"), {"type": "custom-title", "customTitle": "Meu nome"}, {"type": "ai-title", "aiTitle": "Outro"}], 10)
    assert recent(tmp_path)[0]["title"] == "Meu nome"


def test_empty_or_broken_files_and_subagents_are_left_out(tmp_path):
    claude_session(tmp_path, ".claude", CLAUDE_ID, "/home/u/p", [], 10)            # nothing said yet
    claude_session(tmp_path, ".claude", OTHER_ID, "/home/u/p", [user("oi")], 20)
    sub = tmp_path / ".claude/projects/-home-u-p" / OTHER_ID / "subagents"
    sub.mkdir(parents=True)
    (sub / "agent-1.jsonl").write_text(json.dumps(user("sub")) + "\n")
    (tmp_path / ".claude/projects/-home-u-p/not-a-session.jsonl").write_text("{")
    assert [i["id"] for i in recent(tmp_path)] == [OTHER_ID]


def test_sessions_run_by_programs_are_not_the_persons(tmp_path):
    """Claude run through the SDK (a script, a review agent) is not a conversation to reopen."""
    claude_session(tmp_path, ".claude", CLAUDE_ID, "/home/u/p", [{**user("revise"), "entrypoint": "sdk-py"}], 20)
    claude_session(tmp_path, ".claude", OTHER_ID, "/home/u/p", [{**user("oi"), "entrypoint": "cli"}], 10)
    assert [i["id"] for i in recent(tmp_path)] == [OTHER_ID]


def test_the_list_is_limited(tmp_path):
    for n in range(5):
        claude_session(tmp_path, ".claude", f"{n}0000000-0000-4000-8000-000000000000", "/home/u/p", [user(f"t{n}")], 100 + n)
    assert [i["title"] for i in recent(tmp_path, limit=2)] == ["t4", "t3"]


def test_only_known_options_are_kept_to_resume_with():
    assert kept_flags("claude", ["claude", "--dangerously-skip-permissions", "--resume", CLAUDE_ID, "--model", "opus", "fix it", "--mcp-config", "x.json"]) \
        == ["--dangerously-skip-permissions", "--model", "opus"]
    assert kept_flags("claude", ["/usr/bin/claude", "--permission-mode=plan", "-c"]) == ["--permission-mode=plan"]
    assert kept_flags("codex", ["codex", "resume", CODEX_ID, "--dangerously-bypass-approvals-and-sandbox", "-m", "gpt-5", "-c", "x=1"]) \
        == ["--dangerously-bypass-approvals-and-sandbox", "-m", "gpt-5"]


def test_the_resume_command_is_quoted_for_the_shell():
    assert resume_command("claude", CLAUDE_ID, ["--dangerously-skip-permissions"]) == f"claude --dangerously-skip-permissions --resume {CLAUDE_ID}"
    assert resume_command("codex", CODEX_ID, ["-m", "gpt 5; rm -rf ~"]) == f"codex resume -m 'gpt 5; rm -rf ~' {CODEX_ID}"


def test_options_are_remembered_per_session(tmp_path):
    memory = Remembered(tmp_path / "state/sessions.json")
    assert memory.flags(CLAUDE_ID) == []
    memory.note(CLAUDE_ID, ["--dangerously-skip-permissions"])
    memory.note(OTHER_ID, [])
    again = Remembered(tmp_path / "state/sessions.json")
    assert again.flags(CLAUDE_ID) == ["--dangerously-skip-permissions"]
    assert again.flags("../../x") == []
