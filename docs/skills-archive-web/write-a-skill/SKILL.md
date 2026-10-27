---
name: write-a-skill
description: >-
  Create new agent skills with proper structure, progressive disclosure, and
  bundled resources. Produces a SKILL.md (and optional siblings) inside
  .cursor/skills/<slug>/. Use when user wants to create, write, or build a
  new skill, says "new skill", "write a skill", "create skill for", or asks
  how to structure a SKILL.md file.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/stack.md
rules:
  - .cursor/rules/skills-first.mdc
---

# Write a Skill

## Knowledge & rules

Before executing: read [workflow.md](../../../knowledge/workflow.md) + [execution.md](../../../knowledge/execution.md) + other frontmatter paths.
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

```
write-a-skill → review-skill (required pass) → update-docs? → catalog/CI
```

**Hard stop:** do not treat a new skill as usable until [review-skill](../review-skill/SKILL.md) passes (including shared-playbook checks).

## Process

1. **Gather** — task/domain, triggers, which domain `knowledge/` + `rules/` apply.
2. **Classify the new skill** (required):

| Kind | Must declare in `knowledge:` | Body |
|------|------------------------------|------|
| Workflow / orchestration | `knowledge/workflow.md` (+ `execution.md` if it closes a slice) | Point to playbooks; no forked pipeline/mandate |
| Executor / slice-closing | `knowledge/workflow.md` + `knowledge/execution.md` | Point to playbooks; no forked conflict/review/verify text |
| Craft / stack / meta | Domain knowledge + `skills-first`; add `workflow.md` / `execution.md` if it participates in pipeline or closes a slice | Skill-specific steps only |

3. **Draft `SKILL.md`** — see [REFERENCE.md](REFERENCE.md). Required: `name`, `description` (Use when…), `knowledge:`, `rules:` (≥ `skills-first.mdc`), `## Knowledge & rules`, link to shared playbooks when declared.
4. Domain how-to/musts changed → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).
5. **Review required** — run [review-skill](../review-skill/SKILL.md). Failures → [update-skill](../update-skill/SKILL.md) → re-review until pass.
6. Catalog (`.cursor/skills/README.md`), validator categories, scenario if craft/stack/meta; `make validate-skills-strict`.

## Rules

| Rule | Detail |
|------|--------|
| Shared playbooks | Wire + link `workflow.md` / `execution.md` per classify table — never copy them |
| Review before use | `review-skill` must pass |
| Grow docs with skills | Musts/how-to → update-docs |
| Strict CI | `make validate-skills-strict` must pass |
