# Execution rules (shared)

**Single source** for shared execute-time musts. Executor skills declare this file in `knowledge:` and **point here** — do not copy these rules into each `SKILL.md`.

**Parent workflow:** [workflow.md](workflow.md) · **TDD:** [tdd.md](tdd.md) · **Musts pointer:** `.cursor/rules/skills-first.mdc` · **Why:** [ADR-0011](../docs/adr/0011-review-work-layer.md)–[0013](../docs/adr/0013-shared-execution-playbook.md), [ADR-0015](../docs/adr/0015-tdd-based-development.md)

Applies to: `implement-feature`, `fix-feature`, `refactor-code`, `manage-infra`, `update-docs`, and any skill that mutates the repo then finishes a slice.

## Pipeline position

```
resolve-skills matrix → conflict check → TDD (red→green→refactor) → edits
  → review-work → verify-feature (make test) → done
```

Full pipeline: [workflow.md](workflow.md). TDD detail: [tdd.md](tdd.md).

## 1. Matrix first

No execute without a needed-vs-have matrix from `resolve-skills` (or a plan that embeds it). Only **HAVE** skills. New mid-work concern → re-enter `resolve-skills` before more app code.

For API behavior, web behavior, or bug fixes: matrix **must** include Tests → `add-test`.

## 2. Contract conflict check (raise / hard stop)

Before mutating app, infra, or docs for the task:

1. Union all matrix skills’ frontmatter `knowledge:` + `rules:` (plus this file).
2. Read them. If any **contradiction on the same must** (knowledge↔rules, skill↔skill, rule↔rule, or knowledge↔Compose/Make/README) — e.g. ports, `/api/v1`, fetch policy, layering:

```text
Contract conflict:
| Source A | Source B | Clash |
|----------|----------|-------|
| …        | …        | …     |
```

3. **Raise** — hard stop. Do **not** pick a side silently.
4. Resolve via `update-docs` (+ `write-adr` if lasting why) → re-run `resolve-skills` → only then continue.

## 3. TDD (required for API / web / bugs)

Follow [tdd.md](tdd.md):

1. **RED** — failing test first (`add-test`: `src/**/*.test.ts`)
2. **GREEN** — minimal production code via matrix stack/craft skills
3. **REFACTOR** — keep tests green
4. Do not ship web behavior without a test that failed before the fix/feature existed

API/server TDD belongs in the separate API repo (ADR-0040). Docs/skills-only and pure infra-without-behavior may skip. E2E remains gated inside `add-test`.

## 4. Skills only

Structural work via stack/craft skills from the matrix. When adding *resource* CRUD, clone Items — do not freestyle a second CRUD style. Other concerns use their matrix skills (`api-patterns`, `refactor-code`, `manage-infra`, …).

Stack/craft skills run **only** under an executor parent (`implement-feature` / `fix-feature` / `refactor-code`) after resolve-skills — not as entry skills.

## 5. Review then verify (required)

After execute edits:

1. `review-work` — diff vs matrix knowledge/rules (+ TDD playbook); contradictions **fail**
2. `verify-feature` — smoke / DoD; run **`make test`** when web behavior is touched

Do not declare done without both (and green tests when applicable).

## 6. Docs ownership

Changing these shared musts → edit **this file** (and thin pointers in `skills-first.mdc` / [workflow.md](workflow.md) / [tdd.md](tdd.md)) via `update-docs`. Do not re-duplicate into individual executor skills.
