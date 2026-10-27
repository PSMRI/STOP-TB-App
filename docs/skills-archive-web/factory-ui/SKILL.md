---
name: factory-ui
description: >-
  Apply Project Factory wizard chrome and landing (dark agents_flow-style
  Pipeline shell, tokens, stage colors, landing hero) when building or
  restyling Factory surfaces. Use when editing LandingPage, FactoryPage,
  PhaseRail, PhasePanel, factory CSS tokens, or any designed Project Factory
  surface — not for Items CRUD or plain web-ui pages.
knowledge:
  - knowledge/factory-ui.md
  - knowledge/web-conventions.md
  - knowledge/stack.md
rules:
  - .cursor/rules/factory-ui.mdc
  - .cursor/rules/web-conventions.mdc
  - .cursor/rules/skills-first.mdc
---

# Factory UI

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — especially [factory-ui.md](../../../knowledge/factory-ui.md).
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

**Parent only:** run from a resolve-skills matrix via `implement-feature` / `fix-feature` / `add-page` — not as an entry skill.

## Pipeline position

```
add-page | implement-feature → factory-ui → Factory markup/styles
```

For plain Items/CRUD visuals use [web-ui](../web-ui/SKILL.md) instead.

## Process

1. Read [factory-ui.md](../../../knowledge/factory-ui.md) and skim `LandingPage`, `FactoryPage`, `components/factory/`, and `factory/tokens.css` + `Shell.module.css`.
2. Keep `/` as landing and `/factory` as wizard unless a plan + docs change says otherwise.
3. Preserve wizard layout contract: Pipeline left rail + main column; stage-colored steps; dark panels.
4. Reuse `--factory-*` from `tokens.css` only; extend sparingly and document new tokens in `factory-ui.md`. Prefer colocated `*.module.css` over growing `index.css`.
5. Keep accessibility: labels, `aria-current` on active step, disabled locked steps.
6. Prefer Phosphor icons from `factory/icons.ts` for stage marks and landing CTA; do not mix other icon sets for those.
7. Do not migrate Factory to Tailwind without plan + docs/ADR update.

## Rules

| Rule | Detail |
|------|--------|
| agents_flow language | Dark zinc + indigo accent + stage colors |
| Tokens first | `--factory-*` only for Factory chrome |
| Split from Items | CRUD stays on web-ui cream defaults |
| Forms | Ant Design (`antd`) + `FactoryAntdProvider` / `factory/antd-theme.ts` |
| Icons | Phosphor via `@phosphor-icons/react` + `factory/icons.ts` |
| Markdown | Compare: `MarkdownContent`; Ideation Description: preview → `IdeaFullscreenEditor`; Discovery/Solution edit: TipTap `IdeaRichEditor` |
| Parent only | Not an entry skill |
