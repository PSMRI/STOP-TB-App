---
name: resolve-skills
description: >-
  Build the needed-vs-have skill matrix for a mutating request: list concerns,
  match the local catalog, mark GAPs, then find or write missing skills. Use
  when after route-task classifies work and before plan-feature,
  implement-feature, fix-feature, or any execution skill — every mutating path
  must pass through this layer.
knowledge:
  - knowledge/workflow.md
  - knowledge/tdd.md
  - knowledge/stack.md
rules:
  - .cursor/rules/skills-first.mdc
---

# Resolve Skills

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — especially [workflow.md](../../../knowledge/workflow.md) §2.
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

Dedicated layer between **routing** and **execution**. Pipeline position: see workflow.md. **Do not write application code here.**

## Inputs

From `route-task`: request kind + short goal (per workflow classify table).

## Process

### 1. Needed — concerns in scope

Mark each row **in** or **N/A**:

| Concern | Typically needs |
|---------|-----------------|
| DB / migration | `add-migration` — **N/A in this UI repo** (API repo) |
| API CRUD | `add-api-endpoint` / `add-crud-resource` — **N/A** (API repo) |
| API beyond CRUD | `api-patterns` — **N/A** (API repo) |
| Auth | Client: `keycloak-auth`; server JWT — **API repo** |
| Web shell / route | `add-page` |
| Client state | `web-state` |
| UI (Items / simple) | `web-ui` |
| UI (Project Factory chrome) | `factory-ui` |
| Factory LLM runtime | `factory-llm` — **N/A** (API repo) |
| Tests (Web TDD) | `add-test` |
| Tests (API TDD) | **N/A** (API repo) |
| Tests (E2E / new runner) | `add-test` (gate inside skill) |
| Refactor | `refactor-code` |
| Infra / CI / Make | `manage-infra` |
| Docs / rules / knowledge | `update-docs` |
| New Cursor skill | `write-a-skill` → `review-skill` |
| Lasting why | `write-adr` |
| Orchestration | `plan-feature` / `implement-feature` / `fix-feature` / `review-work` / `verify-feature` as applicable |

Extra concerns (perf, …) → usually **GAP** until a skill exists.

### 2. Have — catalog match

Open `.cursor/skills/README.md`. For each in-scope concern: **HAVE** (local skill owns it) or **GAP**.

### 3. Publish the matrix (required)

```text
Skill matrix:
| Concern | Needed | Have | Skill or GAP |
|---------|--------|------|--------------|
| …       | yes    | yes  | web-state    |
```

List **execution order** of HAVE skills.

### 4. Close every GAP

1. [find-skills](../find-skills/SKILL.md) → user OK → install  
2. Else [write-a-skill](../write-a-skill/SKILL.md) → [review-skill](../review-skill/SKILL.md)  
3. Musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md)  
4. Re-run until all HAVE (or intentional gate: `api-auth` / `add-test`)

**Hard stop** while non-gate GAPs remain (see [workflow.md](../../../knowledge/workflow.md)). HAVE ⇒ valid `knowledge:` / `rules:` (`make validate-skills-strict`).

### 5. Hand off

Next step per workflow: plan / implement / fix / named executor — **matrix skills only**.

## Mid-work re-entry

New concern during execute → call **resolve-skills** again before more app code.

## Rules

| Rule | Detail |
|------|--------|
| Shared workflow | Obey `knowledge/workflow.md` |
| Needed vs have | Both columns required |
| No app code here | Matrix only |
| Approve installs | User OK before `npx skills add` |
