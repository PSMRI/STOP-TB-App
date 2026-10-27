---
name: update-docs
description: >-
  Updates knowledge/*.md, CLAUDE.md, README or the ADR index so documentation matches how the
  STOP-TB code actually works. Use when docs drift from code, a change adds or alters a must or
  how-to, or a new skill needs its knowledge wired.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
---

# Update Docs

## Knowledge

Ownership table: [workflow.md](../../../knowledge/workflow.md) §5.

## Process

1. Pick the target: `knowledge/` (how), `knowledge/android-conventions.md` + `CLAUDE.md` summary
   (musts), `docs/adr/` (why → [write-adr](../write-adr/SKILL.md)), `README.md` (product overview).
2. Pipeline text only in `workflow.md`; execute contract only in `execution.md` — link, never copy.
3. Keep `CLAUDE.md` short: pointer + top musts + commands.
4. New/changed skill → update `.claude/skills/README.md` and its `knowledge:` paths.
5. Run `python3 scripts/validate_skills.py`; close with review-work → verify-feature.

## Rules

| Rule | Detail |
|------|--------|
| As-built | Document what the code does, not intentions |
| Single source | One owner file per topic |
