---
name: review-skill
description: >-
  Audit an existing Cursor skill for description quality, length, triggers, and
  structure. Use when the user says review skill, audit skill, or after
  write-a-skill drafts a new skill.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
rules:
  - .cursor/rules/skills-first.mdc
---

# Review Skill

## Knowledge & rules

Before executing: read [workflow.md](../../../knowledge/workflow.md) + [execution.md](../../../knowledge/execution.md).
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

Required after every [write-a-skill](../write-a-skill/SKILL.md) draft (and after substantive [update-skill](../update-skill/SKILL.md) edits). **Fail = skill not ready to use.**

## Process

1. Read target `SKILL.md` (+ siblings).
2. Classify kind (workflow / executor / craft|stack|meta) using [write-a-skill](../write-a-skill/SKILL.md) table.
3. Score checklist; **playbook rows are blocking**.
4. Report pass/fail. Failures → [update-skill](../update-skill/SKILL.md) → re-run this skill.

## Checklist

### Structure

- [ ] YAML `description` has capability + "Use when …" triggers
- [ ] Frontmatter `knowledge:` and `rules:` present; paths exist
- [ ] `## Knowledge & rules` section present
- [ ] Description ≤ 1024 chars, third person
- [ ] Body roughly ≤ 100 lines (else `REFERENCE.md`)
- [ ] Concrete steps + rules table; links one level deep; no time-sensitive claims

### Shared playbooks (blocking)

- [ ] Workflow/orchestration skills declare + link `knowledge/workflow.md`
- [ ] Executor / slice-closing skills declare + link `knowledge/workflow.md` and `knowledge/execution.md`
- [ ] Body **points to** those files — does **not** restate pipeline, mandate, conflict-raise, or review→verify at length
- [ ] No contradictory instructions vs `workflow.md` / `execution.md`

### Follow-through

- [ ] Catalog / scenarios / validator categories updated if new skill
- [ ] If musts/how-to changed → `update-docs` (ADR if why)
- [ ] `make validate-skills-strict` would pass for this skill’s wiring

## Output format

```
Skill: <name>
Kind: workflow | executor | craft/stack/meta
Score: N/M passed
Playbooks: pass/fail
Failures:
- …
Ready to use: yes/no
Recommended fixes:
- …
```

**Ready to use: no** until playbooks + structure pass.

## Rules

| Rule | Detail |
|------|--------|
| Shared workflow | Obey `knowledge/workflow.md` |
| Blocking playbooks | Missing/forked playbooks ⇒ fail |
| Docs only | Audits SKILL.md — not product work (`review-work`) |
| Gate for new skills | write-a-skill incomplete without a pass here |
