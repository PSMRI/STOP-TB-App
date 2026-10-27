---
name: update-skill
description: >-
  Safely edit an existing Cursor skill while preserving triggers and structure.
  Use when the user says update skill, edit skill, fix skill docs, or
  review-skill requested changes.
knowledge:
  - knowledge/workflow.md
rules:
  - .cursor/rules/skills-first.mdc
---

# Update Skill

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — [workflow.md](../../../knowledge/workflow.md).
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

## Process

1. Read the current skill and any `REFERENCE.md`.
2. Confirm the change intent (triggers, steps, links, rules).
3. Apply a minimal edit — keep `name` stable unless renaming is explicit.
4. Keep description trigger-rich; keep body concise; point to `workflow.md` / `execution.md` instead of forking shared musts.
5. **Required:** run [review-skill](../review-skill/SKILL.md) (full checklist). Not done until **Ready to use: yes**.

## Rules

| Rule | Detail |
|------|--------|
| Shared workflow | Obey `knowledge/workflow.md` |
| Minimal diff | Do not rewrite the whole skill casually |
| Triggers sacred | Description must still say when to use |
| No app code | This skill only edits `.cursor/skills/**` |
| Review after edit | `review-skill` required after substantive edits |
