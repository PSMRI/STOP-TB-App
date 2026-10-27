---
name: update-skill
description: >-
  Edits an existing project skill in .claude/skills with a minimal change while keeping its
  name and triggers intact, then re-reviews it. Use when the user says update/fix a skill, a
  skill gave wrong guidance, or review-skill requested changes.
knowledge:
  - knowledge/workflow.md
---

# Update Skill

## Knowledge

[workflow.md](../../../knowledge/workflow.md); template in [write-a-skill/REFERENCE.md](../write-a-skill/REFERENCE.md).

## Process

1. Read the skill (and its REFERENCE.md). Confirm what was wrong or missing — ideally with the
   concrete mistake it allowed.
2. Minimal edit; keep `name`; keep the description trigger-rich.
3. If the fix is really a project must, put it in `knowledge/android-conventions.md`
   ([update-docs](../update-docs/SKILL.md)) and link it from the skill.
4. Run [review-skill](../review-skill/SKILL.md) until **Ready to use: yes**.

## Rules

| Rule | Detail |
|------|--------|
| Minimal diff | No casual rewrites |
| Only skills/docs | No app code here |
