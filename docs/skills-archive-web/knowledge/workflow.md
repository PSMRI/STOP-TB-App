# Workflow (shared)

**Single source** for the skills-first pipeline and shared workflow musts. Workflow skills declare this file in `knowledge:` and **point here** — do not copy pipeline/mandate text into each `SKILL.md`.

**Scope:** every **mutating development task** — features (CRUD or not), bugs, refactors, infra/CI, docs/rules/knowledge, skills, ADRs. Read-only Q&A is the only skip. **Items CRUD** is the reference *style* for resource slices, not the only kind of work this pipeline handles.

**Always-apply pointer:** `.cursor/rules/skills-first.mdc` · **Execute:** [execution.md](execution.md) · **TDD:** [tdd.md](tdd.md) · **Why:** [ADR-0002](../docs/adr/0002-skills-first-development.md), [ADR-0014](../docs/adr/0014-shared-workflow-playbook.md), [ADR-0015](../docs/adr/0015-tdd-based-development.md)

Applies to: `route-task`, `resolve-skills`, `plan-feature`, `grill-me`, `implement-feature`, `fix-feature`, `review-work`, `verify-feature`, `find-skills`, `write-a-skill`, `review-skill`, `update-skill`, `write-adr`, and other skills that participate in the mutating pipeline.

## Pipeline

```
route-task (classify only)
  → resolve-skills (needed vs have)     ← required
  → plan-feature? → grill-me → plans/*.md
  → execute (TDD: red → green → refactor)  ← execution.md + tdd.md
  → review-work → verify-feature           ← run tests
         ↓ GAP
    find-skills | write-a-skill → review-skill
```

**Routing ≠ skill resolution.** After classify, always run `resolve-skills` before plan or execution.

## 1. Classify (`route-task`)

| Kind | Plan after resolve? | Eventual execution |
|------|---------------------|--------------------|
| Read-only Q&A | — | Answer — **stop** (no resolve-skills) |
| Bug, clear repro | No | `fix-feature` |
| Bug, unclear | Thin chat only | `fix-feature` |
| New/unclear feature | Yes | `plan-feature` → later `implement-feature` |
| Implement frozen plan | No (plan exists) | Re-resolve → `implement-feature` |
| Refactor | Usually no | `refactor-code` |
| Infra / CI / Make / Docker | No | `manage-infra` |
| Docs / rules / knowledge | No | `update-docs` |
| New/edit skill | No | Still `resolve-skills` then `write-a-skill` / `update-skill` |
| Record why | No | `write-adr` |

`route-task` does **not** write application code. Skill matching lives in `resolve-skills`.

## 2. Resolve (required for mutating work)

Always publish a needed-vs-have matrix before plan/execute. Close every non-gate **GAP** (`find-skills` with user OK, or `write-a-skill` → **`review-skill` must pass** before the new skill is used). Hard stop while GAPs remain or while a new skill fails review. Auth → `api-auth`. **Tests:** API and/or web behavior (and bugs) → `add-test` (TDD, [tdd.md](tdd.md)); E2E/new runners → still gated inside `add-test`. Mid-work new concern → re-enter `resolve-skills`. Detail: `resolve-skills` skill.

**New skills** must classify (workflow / executor / craft|stack|meta), declare the matching shared playbooks (`workflow.md` / `execution.md`), **point** to them (never fork), and pass `review-skill` playbook checks. See `write-a-skill` + `review-skill`.

## 3. Plan (optional)

New/unclear product features: `plan-feature` → `grill-me` → `plans/YYYY-MM-DD-<slug>.md` (include full matrix). Clear bugs may skip a plan file but **must** still pass resolve. Skip plan → list assumptions; still resolve + follow [execution.md](execution.md).

## 4. Execute → review → verify

Follow [execution.md](execution.md) + [tdd.md](tdd.md): matrix-only skills, contract conflict **raise**, **TDD** for API/web behavior (and bugs), then `review-work` → `verify-feature`. Do not declare done without both (and green tests when those layers are touched).

## 5. Shared musts (do not fork)

| Must | Detail |
|------|--------|
| Route first | Load `route-task` before mutating the repo |
| Always resolve | Mutating → `resolve-skills` before plan/execute |
| Skills only | Prefer local `.cursor/skills/`; no freestyle structure |
| Hard gap | Unowned concern → stop; do not invent stacks |
| Clone Items (when CRUD) | New *resource* CRUD mirrors Items (`add-crud-resource` + craft) — not a limit on other work |
| ADRs | Lasting why → `write-adr` → `docs/adr/` |
| Knowledge + rules | Every skill declares `knowledge:` + `rules:`; how → `update-docs`; why → `write-adr` |
| Shared playbooks | Workflow → **this file**; execute → `execution.md`; TDD → `tdd.md` |
| New skill gate | `write-a-skill` → `review-skill` pass (playbooks blocking) before use |
| TDD | API/web behavior + bugs: red → green → refactor ([tdd.md](tdd.md)) |

## 6. Escape hatches

- **Skip plan** / proceed with assumptions → still `resolve-skills` + [execution.md](execution.md).
- Clear bug → `fix-feature` (no `plans/` required); resolve + execution still required.

## 7. Paths

| Concern | Path |
|---------|------|
| Web | `src/` (repo root Vite app; [ADR-0040](../docs/adr/0040-ui-only-repo.md)) |
| API | **Separate repo** — not in this tree |
| Skills | `.cursor/skills/` |
| Plans | `plans/` |
| ADRs | `docs/adr/` |
| Knowledge (how) | `knowledge/` |
| Rules (musts) | `.cursor/rules/` |
| Shared workflow | `knowledge/workflow.md` (this file) |
| Shared execute | `knowledge/execution.md` |
| Shared TDD | `knowledge/tdd.md` |

## 8. Docs ownership

Change shared workflow musts **here** (and the one-line pointer in `skills-first.mdc` if needed) via `update-docs`. Skills keep only skill-specific process and **link** to this file.
