---
name: write-adr
description: >-
  Create or update Architecture Decision Records under docs/adr/ capturing why
  a lasting choice was made. Use when grill-me freezes an architectural,
  stack, workflow, or convention trade-off, or the user says write ADR, record
  decision, or ADR this.
knowledge:
  - knowledge/workflow.md
  - knowledge/stack.md
rules:
  - .cursor/rules/skills-first.mdc
---

# Write ADR

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — ADR placement in [workflow.md](../../../knowledge/workflow.md) §5.
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

## Pipeline position

Per [workflow.md](../../../knowledge/workflow.md): lasting why after grill-me / resolve gaps → write-adr → link from knowledge if as-built changes.

## When to write

Write an ADR for lasting **why** choices: stack, workflow, conventions, rejected alternatives.

**Do not** write an ADR for field names, copy, one-off bugfixes, or normal feature plans (`plans/` covers those).

## Process

1. **Scan** `docs/adr/` for an existing decision. Supersede instead of duplicating.
2. **Next number** — `NNNN` = max existing + 1, zero-padded to 4 digits.
3. **Create** `docs/adr/NNNN-<slug>.md` using the template below.
4. **Index** — add a row to `docs/adr/README.md`.
5. **Link** — if the decision changes day-to-day how-to, add a one-line pointer in the relevant `knowledge/*.md` file (do not move checklists into the ADR).

## Template

```md
# ADR-NNNN: Title

- Status: Accepted
- Date: YYYY-MM-DD

## Context

What problem or question forced a choice?

## Decision

What we chose, in one short paragraph.

## Consequences

What becomes easier, harder, or constrained.

## Alternatives considered

- Option A — why not
- Option B — why not
```

## Rules

| Rule | Detail |
|------|--------|
| Why not how | Playbooks stay in `knowledge/` |
| One decision | One ADR per trade-off |
| Immutable story | Prefer new ADR + Superseded status over rewriting history |
| No app code | This skill only touches `docs/adr/` and knowledge links |
