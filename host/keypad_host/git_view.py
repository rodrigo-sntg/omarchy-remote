"""An agent's project in git, read-only: the branch, what changed (with +/-), the last commits, and
one file's diff. Only reading commands; a path must stay inside the project."""
import asyncio
import os

DIFF_LIMIT = 60_000
HOME = None  # the user's home (tests set it): diffs are only read inside it
COMMITS = 8


async def _git(cwd: str, *args) -> tuple[int, str]:
    try:
        process = await asyncio.create_subprocess_exec(
            "git", "-C", cwd, "--no-pager", *args, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL,
            env={**os.environ, "GIT_OPTIONAL_LOCKS": "0"},  # reading must not take the index lock the agent uses
        )
        out, _ = await asyncio.wait_for(process.communicate(), 8)
    except (OSError, asyncio.TimeoutError):
        return 1, ""
    return process.returncode, out.decode(errors="replace")


async def git_summary(cwd: str) -> dict:
    code, top = await _git(cwd, "rev-parse", "--show-toplevel")
    if code != 0:
        return {"repo": False}
    (_, status), (_, numstat), (_, log), (_, ahead) = await asyncio.gather(
        _git(cwd, "status", "--porcelain=v1", "-b", "--untracked-files=normal"),
        _git(cwd, "diff", "HEAD", "--numstat"),
        _git(cwd, "log", f"-{COMMITS}", "--format=%h%x09%ar%x09%s"),
        _git(cwd, "rev-list", "--left-right", "--count", "@{upstream}...HEAD"),
    )
    lines = status.splitlines()
    branch = lines[0][3:].split("...")[0] if lines and lines[0].startswith("## ") else ""
    counts = {}
    for line in numstat.splitlines():
        parts = line.split("\t")
        if len(parts) == 3:
            counts[parts[2]] = (int(parts[0]) if parts[0].isdigit() else 0, int(parts[1]) if parts[1].isdigit() else 0)
    files = []
    for line in lines[1:]:
        code_xy, path = line[:2], line[3:].split(" -> ")[-1]
        state = "?" if code_xy == "??" else (code_xy.strip() or "M")[0]
        added, removed = counts.get(path, (0, 0))
        files.append({"path": path, "state": state, "added": added, "removed": removed})
    behind_ahead = ahead.split()
    commits = [dict(zip(("hash", "when", "subject"), l.split("\t", 2))) for l in log.splitlines() if l.count("\t") >= 2]
    return {
        "repo": True, "branch": branch, "files": files[:200], "commits": commits,
        "ahead": int(behind_ahead[1]) if len(behind_ahead) == 2 else 0, "behind": int(behind_ahead[0]) if len(behind_ahead) == 2 else 0,
    }


async def file_diff(cwd: str, path: str) -> str:
    """The file's changes against the last commit (a new file: its content as added lines)."""
    root = os.path.realpath(cwd)
    real = os.path.realpath(os.path.join(root, path))
    home = os.path.realpath(str(HOME or os.path.expanduser("~")))
    # Inside the agent's folder, and that folder inside the home (an agent started in / can't read /etc).
    if not real.startswith(root + os.sep) or not (root == home or root.startswith(home + os.sep)):
        return ""
    code, diff = await _git(cwd, "diff", "HEAD", "--", path)
    if code == 0 and diff.strip():
        return diff[:DIFF_LIMIT]
    try:
        with open(real, errors="replace") as f:
            return "".join("+" + l for l in f.read(DIFF_LIMIT).splitlines(keepends=True))
    except OSError:
        return ""
