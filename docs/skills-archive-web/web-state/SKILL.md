---
name: web-state
description: >-
  Apply this repo’s page-local React state pattern (useState/useEffect +
  resource client) when building or changing interactive pages. Use when
  implementing forms, lists, loading/error UI, or any client state for a
  page — not for choosing a new global store without an ADR.
knowledge:
  - knowledge/web-conventions.md
  - knowledge/stack.md
rules:
  - .cursor/rules/web-conventions.mdc
  - .cursor/rules/skills-first.mdc
---

# Web State

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:`.
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

**Parent only:** run from a resolve-skills matrix via `implement-feature` / `fix-feature` / `add-page` / `add-crud-resource` — not as an entry skill.

## Pipeline position

```
add-page | implement-feature → web-state → page code
```

## Default pattern (Items)

Mirror `src/pages/ItemsPage.tsx`:

- Page-local `useState` for list, form fields, `loading`, `error`
- `useEffect` to load on mount
- Mutations via `src/api/<resource>.ts`, then reload
- No raw `fetch` in the page

## Process

1. Confirm the plan does **not** require shared/global state across routes.
2. If it does → **stop** → [write-adr](../write-adr/SKILL.md) + [find-skills](../find-skills/SKILL.md) / [write-a-skill](../write-a-skill/SKILL.md) for the new approach. Do not add Zustand/Redux/Query ad hoc.
3. Otherwise implement Items-style local state only.
4. Keep types next to the resource client.

## Rules

| Rule | Detail |
|------|--------|
| Local by default | No new state library without ADR |
| Resource client | All HTTP through `api/<resource>.ts` |
| Thin page | load → render → mutate → reload |
