---
name: fix-feature
description: >-
  Diagnose and apply a minimal fix for a bug in the Vite/FastAPI boilerplate.
  Use when the user reports a bug, broken CRUD, failing migration, API error,
  or asks to fix a feature.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/tdd.md
  - knowledge/stack.md
  - knowledge/api-conventions.md
  - knowledge/web-conventions.md
rules:
  - .cursor/rules/skills-first.mdc
  - .cursor/rules/api-conventions.mdc
  - .cursor/rules/web-conventions.mdc
---

# Fix Feature

## Knowledge & rules

Before executing: read [workflow.md](../../../knowledge/workflow.md) + [execution.md](../../../knowledge/execution.md) + [tdd.md](../../../knowledge/tdd.md).
Obey frontmatter `rules:`.
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

## Process

### 1. Require matrix

Need needed-vs-have from [resolve-skills](../resolve-skills/SKILL.md). If missing → run it first. API/bugs → include Tests → [add-test](../add-test/SKILL.md).

### 2. Clarify only if needed

Unclear repro → [grill-me](../grill-me/SKILL.md).

### 3. Shared execute + TDD fix

Follow [execution.md](../../../knowledge/execution.md) + [tdd.md](../../../knowledge/tdd.md): conflict check → **RED** failing test → minimal **GREEN** patch → review→verify.

Schema → [add-migration](../add-migration/SKILL.md). Structure → stack/craft from matrix.  
New concern → re-enter resolve-skills. Lasting choice → [write-adr](../write-adr/SKILL.md).

## Rules

| Rule | Detail |
|------|--------|
| After resolve-skills | Even without a full plan (see workflow.md) |
| Shared workflow / execute / TDD | Obey playbooks — red before fix |
| Shared execute | Obey `knowledge/execution.md` |
