# ADR-0001: Skills-based development workflow

- Status: Accepted
- Date: 2026-10-28

## Context

AI-assisted changes were producing inconsistent structure (fragment scopes, DAO patterns, worker
policies) and repeated the same performance mistakes. A skills catalog had been copied from a
React/FastAPI project, but it targeted the wrong stack, lived in a folder no tool loads
(`skills/`), and pointed at knowledge files that did not exist here.

## Decision

Use Claude Code project skills in `.claude/skills/`, with one shared pipeline in
`knowledge/workflow.md` (route-task → resolve-skills → plan/fix → review-work → verify-feature),
Android-specific craft skills derived from this codebase, and musts in
`knowledge/android-conventions.md`. `CLAUDE.md` makes `route-task` the entry point.
The original web catalog is archived under `docs/skills-archive-web/` for reference.

## Consequences

- Every mutating task publishes a skill matrix; gaps become new skills instead of ad-hoc code.
- Skills and knowledge must be kept in sync (`update-docs`, `scripts/validate_skills.py`).
- Small tasks carry some ceremony; the classify table lets read-only questions skip the pipeline.

## Alternatives considered

- Keep the web catalog and mark rows N/A — almost every craft row was N/A; no Android guidance.
- Only a `CLAUDE.md` with rules — no repeatable procedures for migrations, lists, workers.
