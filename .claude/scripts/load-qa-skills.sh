#!/bin/bash
# Resolves qa-unified-tests path and symlinks its skills into .claude/skills/
# so Claude Code discovers them on next session start.
#
# Resolution order:
#   1. ../qa-unified-tests  (convention: both repos at same parent dir)
#   2. .claude/qa-path.local  (user-specific override, gitignored)
#
# Output is injected into Claude context as a system message.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
SKILLS_DIR="$PROJECT_DIR/.claude/skills"
QA_PATH_FILE="$PROJECT_DIR/.claude/qa-path.local"

QA_PATH=""

# 1. Convention path
CONVENTION_PATH="$(cd "$PROJECT_DIR/.." 2>/dev/null && pwd)/qa-unified-tests"
if [ -d "$CONVENTION_PATH/.claude/skills" ]; then
    QA_PATH="$CONVENTION_PATH"
fi

# 2. User-configured path
if [ -z "$QA_PATH" ] && [ -f "$QA_PATH_FILE" ]; then
    CONFIGURED="$(cat "$QA_PATH_FILE" | tr -d '[:space:]')"
    if [ -d "$CONFIGURED/.claude/skills" ]; then
        QA_PATH="$CONFIGURED"
    fi
fi

if [ -z "$QA_PATH" ]; then
    echo "QA_SKILLS_NOT_CONFIGURED: qa-unified-tests skills are not loaded. If the user wants to use them, ask where qa-unified-tests is located on disk and save the absolute path to .claude/qa-path.local (one line, no quotes). Skills will be available on the next session."
    exit 0
fi

mkdir -p "$SKILLS_DIR"

linked=0
already=0
for skill_dir in "$QA_PATH/.claude/skills"/*/; do
    [ -d "$skill_dir" ] || continue
    skill_name="$(basename "$skill_dir")"
    target="$SKILLS_DIR/$skill_name"
    if [ -L "$target" ]; then
        already=$((already + 1))
    elif [ ! -e "$target" ]; then
        ln -s "$skill_dir" "$target"
        linked=$((linked + 1))
    fi
done

total=$(ls "$SKILLS_DIR" 2>/dev/null | wc -l | tr -d ' ')

if [ "$linked" -gt 0 ]; then
    echo "QA_SKILLS_LINKED: $linked new skill(s) symlinked from $QA_PATH. Restart session for them to be available. Total skills: $total."
else
    echo "QA_SKILLS_READY: $total skill(s) from qa-unified-tests loaded (path: $QA_PATH)."
fi
