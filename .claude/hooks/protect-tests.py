"""PreToolUse hook: ask the developer before Claude changes an EXISTING test file.

Enforces the CLAUDE.md rule "Never weaken a test to make it pass" deterministically.
New test files pass freely; editing or overwriting one under src/test/ needs approval.
Claude Code passes the tool call as JSON on stdin; printing a "permissionDecision"
of "ask" makes it prompt the user instead of running the edit.
"""
import json
import os
import sys

GUARDED_TOOLS = {"Edit", "MultiEdit", "Write", "NotebookEdit"}


def main() -> int:
    try:
        event = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return 0  # not our business; never block on malformed input

    if event.get("tool_name") not in GUARDED_TOOLS:
        return 0

    tool_input = event.get("tool_input") or {}
    path = tool_input.get("file_path") or tool_input.get("notebook_path") or ""
    normalized = path.replace("\\", "/")

    if "/src/test/" not in "/" + normalized.lstrip("/") or not os.path.isfile(path):
        return 0

    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "ask",
            "permissionDecisionReason": (
                "Existing test file: " + os.path.basename(path) + ". CLAUDE.md: never weaken "
                "a test to make it pass. Approve only if the test itself is wrong and Claude "
                "has explained why."
            ),
        }
    }))
    return 0


if __name__ == "__main__":
    sys.exit(main())
