# Workflow — single source of the skills pipeline

Skills live in `.claude/skills/<name>/SKILL.md` (catalog: `.claude/skills/README.md`).
This file owns the pipeline; skills link here instead of copying it.

```
request
  └─ route-task (classify)
       └─ resolve-skills (needed-vs-have matrix, close GAPs)
            ├─ feature  → plan-feature → grill-me → implement-feature ─┐
            ├─ bug/perf → fix-feature (grill-me only if repro unclear) ┤
            ├─ refactor → refactor-code ────────────────────────────────┤
            ├─ docs     → update-docs / write-adr ──────────────────────┤
            └─ skills   → write-a-skill / update-skill → review-skill ──┤
                                                                        └─ review-work → verify-feature → done
```

## 1. Classify (route-task)

| Request looks like | Kind | Plan file? | Executor |
|--------------------|------|-----------|----------|
| New screen / form / field / sync flow / workflow change | feature | yes (`plans/`) | implement-feature |
| Crash, wrong data, slow/janky screen, sync failure | bug / perf | only if > 1 day or touches schema | fix-feature |
| Restructure without behavior change | refactor | if > 5 files | refactor-code |
| knowledge / CLAUDE.md / README / ADR | docs | no | update-docs / write-adr |
| Create / change a skill | skills | no | write-a-skill / update-skill → review-skill |
| Question only, no edits | read-only | — | answer directly; no pipeline |

## 2. Skill matrix (resolve-skills)

Publish before any code:

```
Skill matrix:
| Concern | Needed | Have | Skill or GAP |
|---------|--------|------|--------------|
| Schema change        | yes | yes | room-migration |
| DAO query            | yes | yes | room-dao-query |
| ...                  | n/a |     |                |
Execution order: android-test (RED) → room-migration → room-dao-query → ...
```

**Hard stop:** no app code while a needed concern is a GAP. Close it with `write-a-skill` →
`review-skill`, or the user explicitly accepts "proceed without a skill" (recorded as an assumption).

## 3. Plans and gates

- Plans: `plans/YYYY-MM-DD-<slug>.md` — goal, in/out of scope, touch list, frozen gates, matrix,
  DoD. Template in `plans/README.md`.
- Gates (open product/design decisions) are frozen with `grill-me`, one question at a time.
- "Proceed with assumptions" from the user → list them in the plan and continue.

## 4. Done means

`review-work` table all pass/n/a **and** `verify-feature` report with command output (or an explicit
"not run here — developer must run X"). Nothing is committed unless the user asks.

## 5. Where knowledge goes

| What | Where |
|------|-------|
| How (playbooks, conventions) | `knowledge/*.md` |
| Musts (short, checkable) | `knowledge/android-conventions.md` + summary in `CLAUDE.md` |
| Why (lasting trade-offs) | `docs/adr/NNNN-*.md` via `write-adr` |
| Per-feature decisions | `plans/*.md` |
| Repeatable procedure | a skill in `.claude/skills/` |

Mid-work new concern → back to `resolve-skills` before writing more code.
