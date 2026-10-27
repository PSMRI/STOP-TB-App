---
name: write-a-skill
description: >-
  Authors a new project skill in .claude/skills/<name>/SKILL.md for a repeatable STOP-TB
  procedure, wired to the right knowledge files and catalog. Use when resolve-skills reports a
  GAP, or the user says write/create a new skill or turn this procedure into a skill.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/stack.md
---

# Write a Skill

## Knowledge

[workflow.md](../../../knowledge/workflow.md), [execution.md](../../../knowledge/execution.md),
template and policy in [REFERENCE.md](REFERENCE.md).

## Process

1. Gather: the procedure, its triggers, 1–2 real examples in this repo (file paths), the musts it
   enforces.
2. Classify: workflow / executor / craft (table in REFERENCE.md) → decide `knowledge:` paths.
3. Draft `.claude/skills/<kebab-name>/SKILL.md` from the template. Ground every step in real
   files/classes of this repo; keep the body ≤ ~100 lines (overflow → `REFERENCE.md`).
4. New musts → add them to `knowledge/android-conventions.md` via [update-docs](../update-docs/SKILL.md).
5. Add a row to `.claude/skills/README.md` and the concern row in
   [resolve-skills](../resolve-skills/SKILL.md).
6. Run [review-skill](../review-skill/SKILL.md) — not usable until **Ready to use: yes**.

## Rules

| Rule | Detail |
|------|--------|
| Link, don't copy | Shared playbooks are referenced |
| Grounded | Steps name real files/classes |
| Review gate | review-skill must pass |
