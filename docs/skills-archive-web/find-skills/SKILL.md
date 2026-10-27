---
name: find-skills
description: >-
  Discover and install agent skills from the open ecosystem (skills.sh). Use
  when no local skill covers a task, or the user asks to find/install a skill
  for X, "how do I do X", or to extend agent capabilities.
knowledge:
  - knowledge/workflow.md
rules:
  - .cursor/rules/skills-first.mdc
---

# Find Skills

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — GAP branch in [workflow.md](../../../knowledge/workflow.md).
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

Discover installable skills from [skills.sh](https://www.skills.sh/) when local `.cursor/skills/` has no match.

## Process

1. **Clarify need** — domain + task (e.g. React performance, PR review).
2. **Leaderboard first** — check [skills.sh](https://www.skills.sh/) for high-install options.
3. **Search** — `npx skills find [query]` if needed.
4. **Quality bar** — prefer 1K+ installs; known orgs (`vercel-labs`, `anthropics`, `microsoft`); treat low-star repos cautiously.
5. **Present** — name, what it does, installs/source, install command, skills.sh link.
6. **Install only after user OK** — copy into `.cursor/skills/<name>/` (source of truth). Update `.cursor/skills/README.md` and run `make validate-skills`.
7. **No match** — use [write-a-skill](../write-a-skill/SKILL.md), not freestyle chat procedures.

## Rules

| Rule | Detail |
|------|--------|
| Shared workflow | Obey `knowledge/workflow.md` |
| User OK first | Never install without approval |
| Local catalog | Installed skills live under `.cursor/skills/` |
| Validate | `make validate-skills` after install |
