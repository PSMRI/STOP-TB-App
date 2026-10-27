# TDD (shared)

**Single source** for test-driven development musts. Concerned skills declare this file and **point here** — do not fork the cycle into each `SKILL.md`.

**Parent:** [workflow.md](workflow.md) · [execution.md](execution.md) · **Why:** [ADR-0015](../docs/adr/0015-tdd-based-development.md) · **UI-only:** [ADR-0040](../docs/adr/0040-ui-only-repo.md)

## When TDD applies

| Change | TDD required? |
|--------|----------------|
| Web resource clients / client helpers / page logic | **Yes** — Vitest via [add-test](../.cursor/skills/add-test/SKILL.md) |
| Bug fix with clear failing behavior (web) | **Yes** — failing test first |
| API / DB / FastAPI | **Separate API repo** — not this tree |
| Pure docs / skills / ADR-only | No app tests |
| E2E / Playwright / full browser | Still **gate** — ADR + skill before inventing (see `add-test` § e2e) |

## Cycle (red → green → refactor)

```
1. RED    Write a failing test that specifies the behavior
2. GREEN  Minimal production code to pass
3. REFACTOR  Clean up; keep tests green
4. VERIFY make test before done
```

Do **not** add feature behavior without a RED test first (except docs/skills-only work).

## Stack (accepted)

| Layer | Tool | Location | Command |
|-------|------|----------|---------|
| Web unit | Vitest (+ jsdom) | `src/**/*.test.ts` | `make test` |
| E2E | Not installed | — | Hard stop → ADR + `find-skills` / `write-a-skill` |

Golden path:

- Web: `src/api/client.test.ts`, `src/api/items.test.ts`

## Process (with skills)

1. Matrix includes Tests → `add-test` for web behavior / bugs
2. **RED** — add/adjust tests via `add-test` (`src/**/*.test.ts`)
3. **GREEN** — implement via matrix craft/stack skills
4. **REFACTOR** — keep green
5. `review-work` → `verify-feature` (run `make test`)
