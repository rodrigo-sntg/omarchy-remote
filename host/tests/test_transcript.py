import json

from keypad_host.transcript import OUTPUT_LIMIT, find, full_output, items_of, read_back, read_forward


def claude(type_, content, **extra):
    return json.dumps({"type": type_, "message": {"role": type_, "content": content}, **extra})


def queued(prompt):
    return json.dumps({"type": "attachment", "attachment": {"type": "queued_command", "prompt": prompt, "humanTurn": True}})


def codex(payload):
    return json.dumps({"type": "response_item", "payload": payload})


def test_claude_codes_entries_become_chat_items():
    assert items_of(claude("user", "Olha o log do herdr"), "claude") == [{"k": "you", "t": "Olha o log do herdr"}]
    assert items_of(claude("assistant", [
        {"type": "thinking", "thinking": "hmm"},
        {"type": "text", "text": "Vou olhar."},
        {"type": "tool_use", "id": "toolu_1", "name": "Bash", "input": {"command": "tail herdr.log", "description": "Read the log"}},
    ]), "claude") == [{"k": "said", "t": "Vou olhar."}, {"k": "tool", "id": "toolu_1", "n": "Bash", "t": "tail herdr.log", "why": "Read the log"}]
    assert items_of(claude("user", [{"type": "tool_result", "tool_use_id": "toolu_1", "content": "linha 1\nlinha 2", "is_error": False}]), "claude") == \
        [{"k": "out", "id": "toolu_1", "t": "linha 1\nlinha 2", "cut": False, "err": False}]
    # A message sent while the agent worked comes as a queued command.
    assert items_of(queued("e o terminal?"), "claude") == [{"k": "you", "t": "e o terminal?"}]


def test_claude_codes_internals_stay_out():
    assert items_of(claude("user", "<task-notification>x</task-notification>"), "claude") == []
    assert items_of(claude("user", "Caveat: meta", isMeta=True), "claude") == []
    assert items_of(claude("assistant", [{"type": "text", "text": "sub"}], isSidechain=True), "claude") == []
    assert items_of(json.dumps({"type": "attachment", "attachment": {"type": "total_tokens_reminder"}}), "claude") == []
    assert items_of(json.dumps({"type": "ai-title", "aiTitle": "x"}), "claude") == []
    assert items_of("not json", "claude") == []


def test_a_shell_command_typed_in_claude_code_is_yours():
    assert items_of(claude("user", "<bash-input>systemctl --user restart x</bash-input>"), "claude") == [{"k": "you", "t": "! systemctl --user restart x"}]


def test_tool_targets_are_what_a_person_recognizes():
    def target(name, args):
        return items_of(claude("assistant", [{"type": "tool_use", "id": "t", "name": name, "input": args}]), "claude")[0]["t"]
    assert target("Read", {"file_path": "/home/u/app/App.kt"}) == "/home/u/app/App.kt"
    assert target("Grep", {"pattern": "clip_last", "path": "host"}) == "clip_last"
    assert target("WebFetch", {"url": "https://x.dev"}) == "https://x.dev"
    assert target("Anything", {"z": 1, "note": "o que fazer"}) == "o que fazer"


def test_long_texts_are_cut_and_say_so():
    out = items_of(claude("user", [{"type": "tool_result", "tool_use_id": "t", "content": [{"type": "text", "text": "x" * (OUTPUT_LIMIT + 1)}]}]), "claude")[0]
    assert out["cut"] is True and len(out["t"]) <= OUTPUT_LIMIT


def test_codexs_entries_become_chat_items():
    assert items_of(codex({"type": "message", "role": "user", "content": [{"type": "input_text", "text": "Cria o controle"}]}), "codex") == [{"k": "you", "t": "Cria o controle"}]
    assert items_of(codex({"type": "message", "role": "user", "content": [{"type": "input_text", "text": "# AGENTS.md instructions"}]}), "codex") == []
    assert items_of(codex({"type": "message", "role": "user", "content": [{"type": "input_text", "text": "<environment_context>x"}]}), "codex") == []
    assert items_of(codex({"type": "message", "role": "developer", "content": [{"type": "input_text", "text": "rules"}]}), "codex") == []
    assert items_of(codex({"type": "message", "role": "assistant", "content": [{"type": "output_text", "text": "Feito."}]}), "codex") == [{"k": "said", "t": "Feito."}]
    assert items_of(codex({"type": "function_call", "name": "shell", "arguments": json.dumps({"command": ["bash", "-lc", "cargo test"]}), "call_id": "c1"}), "codex") == \
        [{"k": "tool", "id": "c1", "n": "shell", "t": "bash -lc cargo test"}]
    assert items_of(codex({"type": "custom_tool_call", "name": "exec", "input": "ls -la", "call_id": "c2"}), "codex") == \
        [{"k": "tool", "id": "c2", "n": "exec", "t": "ls -la"}]
    assert items_of(codex({"type": "function_call_output", "call_id": "c1", "output": [{"type": "input_text", "text": "ok"}]}), "codex") == \
        [{"k": "out", "id": "c1", "t": "ok", "cut": False, "err": False}]
    assert items_of(codex({"type": "reasoning", "summary": []}), "codex") == []


def write(path, lines):
    path.write_text("".join(line + "\n" for line in lines))


def conversation(n):
    lines = []
    for i in range(n):
        lines.append(claude("user", f"pergunta {i}"))
        lines.append(json.dumps({"type": "attachment", "attachment": {"type": "total_tokens_reminder"}}))
        lines.append(claude("assistant", [{"type": "text", "text": f"resposta {i} " + "z" * 300}]))
    return lines


def test_pages_are_read_backwards_from_the_end(tmp_path):
    f = tmp_path / "s.jsonl"
    write(f, conversation(20))
    page = read_back(f, "claude", None, 6, block=512)
    assert [i["t"][:11] for i in page["items"]] == ["pergunta 17", "resposta 17", "pergunta 18", "resposta 18", "pergunta 19", "resposta 19"]
    assert page["end"] == f.stat().st_size and page["more"] is True
    older = read_back(f, "claude", page["start"], 6, block=512)
    assert [i["t"][:11] for i in older["items"]][0] == "pergunta 14"
    assert older["end"] == page["start"]
    first = read_back(f, "claude", 10, 6, block=512)
    assert first["items"] == [] and first["more"] is False


def test_the_whole_file_is_never_read_for_a_page(tmp_path):
    f = tmp_path / "s.jsonl"
    write(f, conversation(2000))
    reads = []
    page = read_back(f, "claude", None, 4, block=4096, reads=reads)
    assert len(page["items"]) == 4
    assert sum(reads) < 20_000 < f.stat().st_size


def test_a_huge_line_is_skipped_not_loaded(tmp_path):
    f = tmp_path / "s.jsonl"
    write(f, [claude("user", "antes"), claude("user", [{"type": "tool_result", "tool_use_id": "t", "content": "i" * 50_000}]), claude("user", "depois")])
    page = read_back(f, "claude", None, 5, block=1024, line_limit=10_000)
    assert [i["t"] for i in page["items"] if i["k"] == "you"] == ["antes", "depois"]


def test_new_lines_are_read_from_where_it_stopped_and_a_half_line_waits(tmp_path):
    f = tmp_path / "s.jsonl"
    write(f, [claude("user", "um")])
    end = f.stat().st_size
    with open(f, "a") as out:
        out.write(claude("assistant", [{"type": "text", "text": "dois"}]) + "\n" + claude("user", "tr")[:10])
    items, new_end = read_forward(f, "claude", end)
    assert items == [{"k": "said", "t": "dois"}]
    with open(f, "a") as out:
        out.write(claude("user", "tr")[10:] + "\n")
    items, final = read_forward(f, "claude", new_end)
    assert items == [{"k": "you", "t": "tr"}] and final == f.stat().st_size


def test_an_output_is_fetched_whole_by_its_line(tmp_path):
    f = tmp_path / "s.jsonl"
    write(f, [claude("user", "x"), claude("user", [{"type": "tool_result", "tool_use_id": "t9", "content": "y" * (OUTPUT_LIMIT + 500)}])])
    page = read_back(f, "claude", None, 5)
    out = next(i for i in page["items"] if i["k"] == "out")
    assert out["cut"] is True and "at" in out
    assert full_output(f, "claude", out["at"], "t9") == "y" * (OUTPUT_LIMIT + 500)
    assert full_output(f, "claude", out["at"], "other") is None
    assert full_output(f, "claude", 10 ** 9, "t9") is None


def test_a_session_in_another_claude_config_dir_is_found(tmp_path):
    """CLAUDE_CONFIG_DIR=~/.claude-work keeps a second account's sessions apart."""
    (tmp_path / ".claude-work/projects/-home-u-app").mkdir(parents=True)
    c = tmp_path / ".claude-work/projects/-home-u-app/7a2a2b65-287a-4d90-809f-cc1033d25b36.jsonl"
    c.write_text("")
    assert find("claude", "7a2a2b65-287a-4d90-809f-cc1033d25b36", tmp_path) == c


def test_the_session_file_is_found_by_its_id(tmp_path):
    (tmp_path / ".claude/projects/-home-u-app").mkdir(parents=True)
    c = tmp_path / ".claude/projects/-home-u-app/856c1e70-0a17-485b-9553-e5f1a24ddb1f.jsonl"
    c.write_text("")
    (tmp_path / ".codex/sessions/2026/09/23").mkdir(parents=True)
    x = tmp_path / ".codex/sessions/2026/09/23/rollout-2026-09-23T09-49-09-01a0ce50-138e-7bb0-adf1-efe275fd8b69.jsonl"
    x.write_text("")
    assert find("claude", "856c1e70-0a17-485b-9553-e5f1a24ddb1f", tmp_path) == c
    assert find("codex", "01a0ce50-138e-7bb0-adf1-efe275fd8b69", tmp_path) == x
    assert find("claude", "../../etc/passwd", tmp_path) is None
    assert find("claude", "00000000-0000-0000-0000-000000000000", tmp_path) is None
    assert find("pi", "856c1e70-0a17-485b-9553-e5f1a24ddb1f", tmp_path) is None


def test_edits_carry_what_changed_for_a_diff():
    edit = items_of(claude("assistant", [{"type": "tool_use", "id": "t", "name": "Edit",
        "input": {"file_path": "/a/App.kt", "old_string": "val x = 1", "new_string": "val x = 2\nval y = 3"}}]), "claude")[0]
    assert edit["old"] == "val x = 1" and edit["new"] == "val x = 2\nval y = 3"
    write = items_of(claude("assistant", [{"type": "tool_use", "id": "t", "name": "Write", "input": {"file_path": "/a/N.kt", "content": "x" * 5000}}]), "claude")[0]
    assert "old" not in write and len(write["new"]) == 1500
    multi = items_of(claude("assistant", [{"type": "tool_use", "id": "t", "name": "MultiEdit", "input": {"file_path": "/a/B.kt",
        "edits": [{"old_string": "a", "new_string": "b"}, {"old_string": "c", "new_string": "d"}]}}]), "claude")[0]
    assert multi["old"] == "a\n…\nc" and multi["new"] == "b\n…\nd"
    patch = items_of(codex({"type": "custom_tool_call", "name": "apply_patch", "input": "*** Begin Patch\n-a\n+b", "call_id": "c"}), "codex")[0]
    assert patch["patch"] == "*** Begin Patch\n-a\n+b"


def test_a_command_says_why_when_the_agent_said_it():
    bash = items_of(claude("assistant", [{"type": "tool_use", "id": "t", "name": "Bash", "input": {"command": "ls", "description": "List files"}}]), "claude")[0]
    assert bash["t"] == "ls" and bash["why"] == "List files"


def test_a_long_command_comes_whole_enough_to_read():
    long = "python3 - <<'EOF'\n" + "print(1)\n" * 150 + "EOF"
    bash = items_of(claude("assistant", [{"type": "tool_use", "id": "t", "name": "Bash", "input": {"command": long}}]), "claude")[0]
    assert bash["t"] == long[:1500]


def test_messages_carry_when_they_were_said():
    you = items_of(claude("user", "oi", timestamp="2026-09-24T13:02:05.123Z"), "claude")[0]
    assert you == {"k": "you", "t": "oi", "ts": 1790254925}
    said = items_of(json.dumps({"type": "response_item", "timestamp": "2026-09-24T13:02:05Z", "payload": {"type": "message", "role": "assistant",
        "content": [{"type": "output_text", "text": "ok"}]}}), "codex")[0]
    assert said["ts"] == 1790254925
    # Actions do not need it.
    tool = items_of(claude("assistant", [{"type": "tool_use", "id": "t", "name": "Read", "input": {}}], timestamp="2026-09-24T13:02:05Z"), "claude")[0]
    assert "ts" not in tool
