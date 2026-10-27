---
name: verify-feature
description: >-
  Runs the definition-of-done checks for a STOP-TB change — Gradle compile, unit and
  instrumented tests, lint, skills validation, manual smoke on a large dataset — and reports
  evidence. Use when implementation or a fix is finished and review-work passed, before
  saying done, or when the user asks to verify or smoke-test.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/testing.md
---

# Verify Feature

## Knowledge

Commands: [execution.md](../../../knowledge/execution.md#commands). Smoke checklist:
[testing.md](../../../knowledge/testing.md).

## Process

1. Scope = plan touch list or this task's diff.
2. Run what applies and capture output:

| Layer | Check |
|-------|-------|
| Compile | `./gradlew :app:compileStoptbUatDebugKotlin` (or assemble) |
| Unit | `./gradlew :app:testStoptbUatDebugUnitTest` if `src/test` or pure logic touched |
| DB | `connectedStoptbUatDebugAndroidTest` (at least the affected classes) if DAO/migration touched |
| Lint | `./gradlew :app:lintStoptbUatDebug` for UI/manifest/resources changes |
| Skills/docs | `python3 scripts/validate_skills.py` if `.claude/skills`, `knowledge/`, `CLAUDE.md` changed |
| Manual | testing.md smoke steps for UI/perf changes, with ≥ 5k beneficiaries |

3. Report a pass/fail table with commands and results. Anything not runnable here
   (no SDK/device) is reported as **not run** with the exact command for the developer.
4. Failures → [fix-feature](../fix-feature/SKILL.md) or the owning skill, then re-verify.

## Rules

| Rule | Detail |
|------|--------|
| Evidence over claims | Never say "builds" without output |
| No commit | Unless the user asks |
