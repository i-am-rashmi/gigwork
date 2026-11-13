#!/usr/bin/env bash
# Runs when Claude finishes a turn. If code in core/ changed, run the build and
# all tests. On failure, exit code 2 sends the error back to Claude so it keeps
# working on the fix instead of stopping.

input="$(cat)"

# Avoid an endless loop: if Claude is already continuing because of this hook,
# let it stop this time.
if echo "$input" | grep -q '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR" || exit 0

# Only run when something under core/ changed (staged, unstaged or untracked).
if [ -z "$(git status --porcelain -- core 2>/dev/null)" ]; then
  exit 0
fi

# Testcontainers needs Docker; don't block Claude if Docker isn't running.
if ! docker info >/dev/null 2>&1; then
  echo "verify-on-stop: Docker is not running, skipped ./mvnw verify" >&2
  exit 0
fi

log="$(mktemp)"
if (cd core && ./mvnw -B -q verify) >"$log" 2>&1; then
  rm -f "$log"
  exit 0
fi

{
  echo "./mvnw verify failed in core/. Fix the failure before finishing. Last lines of output:"
  tail -n 60 "$log"
} >&2
rm -f "$log"
exit 2
