---
name: review-skill
description: >-
  Audits a project skill in .claude/skills for trigger quality, structure, correct knowledge
  wiring and grounding in this repo, and reports whether it is ready to use. Use when
  write-a-skill or update-skill has drafted a change, or the user says review/audit a skill.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
---

# Review Skill

## Knowledge

[workflow.md](../../../knowledge/workflow.md); kinds and template in
[write-a-skill/REFERENCE.md](../write-a-skill/REFERENCE.md).

## Checklist

- [ ] `name` equals folder name; `description` third person, ≤ 1024 chars, has "Use when …"
- [ ] `knowledge:` present, every path exists (run `python3 scripts/validate_skills.py`)
- [ ] Workflow skills link `workflow.md`; executors link `workflow.md` + `execution.md`
- [ ] Body links shared playbooks instead of restating them; no contradiction with them
- [ ] Steps reference real files/classes in this repo; no web/API-stack leftovers
- [ ] Body ≤ ~100 lines, has Process + Rules
- [ ] Listed in `.claude/skills/README.md` and (if craft) as a concern in resolve-skills

## Output

```
Skill: <name>   Kind: workflow | executor | craft
Score: N/M
Failures: …
Ready to use: yes/no
```

Failures → [update-skill](../update-skill/SKILL.md) → re-review.

## Rules

| Rule | Detail |
|------|--------|
| Docs only | Audits SKILL.md, not product code (that is review-work) |
| Blocking | Wiring/playbook failures ⇒ not ready |
