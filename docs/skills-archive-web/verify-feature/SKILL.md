---
name: verify-feature
description: >-
  Run definition-of-done checks after implement or fix work on this Vite UI
  repo. Use when implementation or a bugfix finishes, before declaring done,
  or when the user asks to verify / smoke-test a change.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/tdd.md
  - knowledge/stack.md
  - knowledge/web-conventions.md
rules:
  - .cursor/rules/skills-first.mdc
  - .cursor/rules/web-conventions.mdc
---

# Verify Feature

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — [workflow.md](../../../knowledge/workflow.md) §4 + [execution.md](../../../knowledge/execution.md).
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

**Required before declaring any mutating work done** (app, skills, docs, infra).  
Run **after** [review-work](../review-work/SKILL.md) has passed (or n/a-only).

## Process

### 1. Scope

Use `plans/*.md` touch list if present; else files changed this task.

### 2. Checks

| Layer | Check |
|-------|--------|
| TDD / tests | `make test` if web behavior touched |
| Web | `api/client`; route linked if new page |
| Conventions | web rules + knowledge + `tdd.md` when applicable |
| Skills/docs | `make validate-skills-strict` if skills/catalog/scenarios/knowledge links changed |
| Infra | Note commands run if Make/Docker/CI touched |
| Skill matrix | Every concern still had an owner (no silent freestyle) |
| API | N/A in this repo — verify against remote API only if the change depends on it |

### 3. Report

Pass/fail table. Failures → [fix-feature](../fix-feature/SKILL.md) or owning skill — then re-verify.

## Rules

| Rule | Detail |
|------|--------|
| Mandatory | Done ⇒ this skill ran (see `knowledge/execution.md`) |
| Shared workflow | Obey `knowledge/workflow.md` |
| Evidence | Commands/logs over claims |
| No commit | Unless user asks |
