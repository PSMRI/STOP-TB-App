---
name: write-adr
description: >-
  Creates or supersedes an Architecture Decision Record in docs/adr/ capturing why a lasting
  choice was made (architecture, data/sync model, conventions, rejected alternatives). Use when
  grill-me or a fix freezes a lasting trade-off, or the user says write an ADR / record this decision.
knowledge:
  - knowledge/workflow.md
---

# Write ADR

## Knowledge

Placement rules: [workflow.md](../../../knowledge/workflow.md) §5. Index: [docs/adr/README.md](../../../docs/adr/README.md).

## When

Lasting **why**: architecture, sync/conflict model, DB design, conventions, rejected options.
Not for field names, copy, one-off bug fixes or feature plans (those go to `plans/`).

## Process

1. Scan `docs/adr/` — supersede an existing ADR instead of duplicating.
2. Number = highest + 1, 4 digits. File `docs/adr/NNNN-<slug>.md`:

```md
# ADR-NNNN: Title
- Status: Accepted
- Date: YYYY-MM-DD
## Context
## Decision
## Consequences
## Alternatives considered
```

3. Add a row to `docs/adr/README.md`. If superseding, set the old one to `Superseded by ADR-NNNN`.
4. If the decision creates a must or how-to, add a one-line pointer in the relevant
   `knowledge/*.md` via [update-docs](../update-docs/SKILL.md).

## Rules

| Rule | Detail |
|------|--------|
| Why, not how | Checklists stay in knowledge/ |
| One decision per ADR | |
| History is immutable | Supersede, don't rewrite |
