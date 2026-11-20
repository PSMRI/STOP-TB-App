---
name: bug-fix-workflow
description: Standard workflow for fixing a STOP-TB ticket (Stop-XXX) — reproduce, find the root cause, propose a minimal fix, get approval, apply with the Edit tool, compile-verify and give manual test steps. Encodes team conventions for branches and commits.
---

# Bug Fix Workflow (STOP-TB)

## 1. Understand
- Ticket ID (Stop-XXX), expected vs actual behaviour, screen, flavor, build version, steps.
- Ask only for what can't be found in the code or logs.

## 2. Find the root cause (read-only)
- Trace UI → ViewModel → repository → DAO/API/worker (see `/android-architecture-review`).
- Use `git log -S '<symbol>'` / `git blame` to see when and why the code changed.
- For crashes use `/crash-anr-triage`; for sync issues `/offline-sync-debug`.
- State the root cause with file:line and a concrete failure scenario.

## 3. Propose (wait for OK)
- Files to change and a one-line description of each change
- Any DB migration (→ `/room-migration`) or API field change
- Git commands you will run, with the branch name

## 4. Implement
- Edit tool only, one file at a time; never Bash/sed/python for source edits.
- **Minimal fix**: no flow changes, no edits to shared constants or existing UI strings.
- CFU shares code with Community/Occupation contact tracing: put CFU-only behaviour in guarded CFU-only functions; don't change the shared path.
- Match the surrounding code style.

## 5. Verify
- `./gradlew :app:compileStoptbDebugKotlin --console=plain -q`
- No unit tests unless the user asks; give numbered manual test steps, including regression checks on related flows.

## 6. Git (only when asked, and only after showing the commands)
- Branch: `feat/<task-wise-work>` (no ticket number), or follow the existing `bugFix/...` pattern if the user asks.
- Commit message style: `fix(Stop-XXX) : <summary>`
- Never commit to `main` / `release-*` directly.
