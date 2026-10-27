---
name: review-work
description: >-
  Review implemented or fixed work against the skill matrix’s declared
  knowledge and rules before verify/done. Use when after execute skills
  (implement-feature, fix-feature, refactor-code, etc.) and before
  verify-feature — compliance pass/fail on musts and playbooks.
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

# Review Work

Post-execution **compliance** layer (not skill-doc review — that is [review-skill](../review-skill/SKILL.md)). Shared playbooks: [workflow.md](../../../knowledge/workflow.md), [execution.md](../../../knowledge/execution.md).

```
execute → review-work → verify-feature → done
```

## Knowledge & rules

Before reviewing: read every path in the **union** of matrix skills’ frontmatter `knowledge:` (not only this skill’s list), including [execution.md](../../../knowledge/execution.md).
Obey frontmatter `rules:` for those skills (scoped rules also auto-apply in Cursor).
If this skill’s own contracts change → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

## Process

### 1. Collect the contract set

From the session’s [resolve-skills](../resolve-skills/SKILL.md) matrix (HAVE skills used):

1. Open each skill’s `SKILL.md` frontmatter.
2. Union all `knowledge:` paths and `rules:` paths.
3. List files changed this task (plan touch list or git diff).

### 2. Load contracts + contradiction catch

Read every path in that union. Apply [execution.md](../../../knowledge/execution.md) §2: contract contradictions → review **fail** (straggler catch if an executor skipped the raise). Hand to [update-docs](../update-docs/SKILL.md) / [write-adr](../write-adr/SKILL.md); do not verify.

### 3. Review the diff against contracts

For each must/playbook item that applies to the changed files, mark **pass** / **fail** / **n/a**.

Minimum checks when API/web files changed:

| Source | Examples |
|--------|----------|
| API rules/knowledge | route→schema→model; `/api/v1`; Create/Update/Read; 404 shape; no logic in `main.py` |
| Web rules/knowledge | no raw `fetch` in pages; `api/client` + resource helper; page under `pages/`; route registered |
| Stack DoD | migration if models changed; Items-like naming; TDD tests if API |
| Skills-first | only matrix skills used; no freestyle structure |
| TDD | failing test existed before green for API/bug changes |

### 4. Report (required)

```text
Review-work:
| Check | Source | Result | Notes |
|-------|--------|--------|-------|
| …     | api-conventions.mdc | pass/fail | … |
```

- **Any fail** → do **not** call verify as “done”. Fix via the owning matrix skill (or [fix-feature](../fix-feature/SKILL.md)), then re-run **review-work**.
- **All pass / n/a** → hand off to [verify-feature](../verify-feature/SKILL.md).

## Rules

| Rule | Detail |
|------|--------|
| After execute | Required before verify/done (see `knowledge/execution.md`) |
| Shared workflow | Obey `knowledge/workflow.md` |
| Matrix-scoped | Review against skills that actually ran, not the whole repo |
| No silent pass | Publish the table |
| Not review-skill | That audits SKILL.md docs only |
