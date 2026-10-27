---
name: add-test
description: >-
  Drive frontend work with TDD using Vitest (src tests); gate E2E/Playwright
  until adopted. Use when the matrix includes Tests, when implementing or
  fixing web behavior, or when the user asks for tests / TDD.
knowledge:
  - knowledge/tdd.md
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/stack.md
  - knowledge/web-conventions.md
rules:
  - .cursor/rules/skills-first.mdc
  - .cursor/rules/web-conventions.mdc
---

# Add Test

## Knowledge & rules

Before executing: read [tdd.md](../../../knowledge/tdd.md) + other frontmatter paths.
Obey frontmatter `rules:`.
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

Owns **web unit TDD** in this UI-only repo ([ADR-0040](../../../docs/adr/0040-ui-only-repo.md)). Parent: matrix via `implement-feature` / `fix-feature` — not an entry skill. API/pytest TDD belongs in the separate API repo.

## Process

### 1. Scope

| Need | Action |
|------|--------|
| Web client / page logic | TDD with Vitest (§2) |
| API / server tests | **Separate API repo** — do not add `apps/api` here |
| E2E / Playwright | **Hard stop** — ADR + [find-skills](../find-skills/SKILL.md) / [write-a-skill](../write-a-skill/SKILL.md) |

### 2. Web TDD (Vitest)

1. **RED** — add/adjust `src/**/*.test.ts` (clone `api/client.test.ts`, `api/items.test.ts`).
2. Prefer testing resource clients / helpers; pages only when logic lives there (still no raw `fetch` in pages).
3. `make test` — confirm failure.
4. Stack/craft **GREEN**; re-run until green.

### 3. After green

[review-work](../review-work/SKILL.md) → [verify-feature](../verify-feature/SKILL.md) (`make test`).

## Rules

| Rule | Detail |
|------|--------|
| Shared TDD | Obey `knowledge/tdd.md` |
| Red first | No web feature/fix without a failing test first |
| Clone golden tests | Mirror Items web client tests |
| E2E | Hard stop until ADR + skill |
| Parent executor | Not an entry skill |
