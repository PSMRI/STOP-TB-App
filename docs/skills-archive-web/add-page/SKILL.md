---
name: add-page
description: >-
  Add a React page and optional API resource client under src/ using the
  shared api client. Use when adding a page, route, UI for a resource, or
  frontend screen.
knowledge:
  - knowledge/web-conventions.md
  - knowledge/stack.md
  - knowledge/tdd.md
rules:
  - .cursor/rules/web-conventions.mdc
  - .cursor/rules/skills-first.mdc
---

# Add Page

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:`.
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

**Parent only:** run from a resolve-skills matrix via `implement-feature` / `fix-feature` / `refactor-code` — not as an entry skill. New client behavior → [add-test](../add-test/SKILL.md) Vitest RED first ([tdd.md](../../../knowledge/tdd.md)).

## Process

1. Read `knowledge/web-conventions.md`.
2. If the page talks to the API, add `src/api/<resource>.ts` using `api` from `client.ts`.
3. **RED** Vitest for the client when behavior is new (clone `api/items.test.ts`) via [add-test](../add-test/SKILL.md).
4. Add `src/pages/<Name>Page.tsx` shell + route in `App.tsx`.
5. Hand off client state to [web-state](../web-state/SKILL.md).
6. Hand off markup/styles to [web-ui](../web-ui/SKILL.md).
7. Link from Home (or nav) when it is a primary surface.
8. No raw `fetch` inside pages.

## Patterns to copy

- `src/api/items.ts` + `api/items.test.ts`
- `src/pages/ItemsPage.tsx`
- Route registration in `App.tsx`

## Checklist

- [ ] Uses `api` / resource helper
- [ ] Vitest green for new client behavior (`make test`)
- [ ] Route registered
- [ ] Types colocated with resource client
- [ ] `web-state` + `web-ui` followed (not freestyled)
