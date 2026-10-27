---
name: web-ui
description: >-
  Apply this repo’s default UI patterns (Items page + index.css) when building
  or restyling web pages. Use when adding layout, forms, lists, or visual
  polish for this UI — keep CRUD UI simple unless the plan asks for a
  designed surface.
knowledge:
  - knowledge/web-conventions.md
  - knowledge/stack.md
  - knowledge/factory-ui.md
rules:
  - .cursor/rules/web-conventions.mdc
  - .cursor/rules/skills-first.mdc
---

# Web UI

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:`.
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

**Parent only:** run from a resolve-skills matrix via `implement-feature` / `fix-feature` / `add-page` / `add-crud-resource` — not as an entry skill.

## Pipeline position

```
add-page | implement-feature → web-ui → markup/styles
```

## Default pattern (Items)

- Structure: `main.page`, `form.form`, simple headings/lists
- Styles: reuse classes in `src/index.css`
- Do not invent a parallel component library for routine CRUD

## Process

1. Read `knowledge/web-conventions.md` and skim `ItemsPage` + `index.css`.
2. If the plan says **simple CRUD UI** → match Items; reuse existing CSS classes.
3. If the plan says **designed / branded surface** → do not freestyle a new system:
   - **Project Factory wizard chrome** → [factory-ui](../factory-ui/SKILL.md) ([factory-ui.md](../../../knowledge/factory-ui.md), ADR-0016)
   - Prefer [find-skills](../find-skills/SKILL.md) (user OK to install), or
   - [write-a-skill](../write-a-skill/SKILL.md) for a repo design skill, and
   - [write-adr](../write-adr/SKILL.md) if adopting lasting design tokens/system.
4. Keep accessibility basics: labels on inputs, meaningful buttons, focusable controls.

## Rules

| Rule | Detail |
|------|--------|
| Clone Items visuals | Default for resource pages |
| No drive-by design system | Tokens/components need plan + ADR/skill |
| CSS | Prefer existing `index.css` classes |
