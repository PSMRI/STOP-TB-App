---
name: factory-llm
description: >-
  Extend Project Factory’s in-app LLM runtime (LangGraph graphs, LangChain
  models, Pydantic I/O, knowledge packs). Use when adding or changing Factory
  LLM graphs, agent skills/tools, prompt KB, model wiring, or migrating off
  raw provider calls in API routes — not for Ideation AntD/compare UI.
knowledge:
  - knowledge/factory-llm.md
  - knowledge/stack.md
  - knowledge/api-conventions.md
  - knowledge/tdd.md
  - knowledge/workflow.md
  - knowledge/execution.md
rules:
  - .cursor/rules/factory-llm.mdc
  - .cursor/rules/api-conventions.mdc
  - .cursor/rules/skills-first.mdc
---

**Out of tree (ADR-0040):** This skill targets the separate Project Factory **API** repository. Do not recreate `apps/api` in this UI repo — mark the concern N/A here and implement there.
# Factory LLM

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — especially [factory-llm.md](../../../knowledge/factory-llm.md) and [execution.md](../../../knowledge/execution.md).
Obey frontmatter `rules:`.
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

**Parent only:** run from a resolve-skills matrix via `implement-feature` / `fix-feature` / `refactor-code` — not as an entry skill.

## Pipeline position

```
implement-feature → add-test → factory-llm (graph/KB/runtime) → thin route via api-patterns
```

## Process

1. Add or extend a graph under `apps/api/src/api/llm/graphs/`.
2. Put prompts in `apps/api/src/api/llm/knowledge/`; load via runtime helpers.
3. Use Pydantic models for invoke I/O; LangGraph for control flow.
4. Obtain chat models only via `api/llm/runtime/` (mock when configured).
5. Keep `routes/` thin — invoke graph, map HTTP errors.
6. TDD with mocked LLM; never require live keys in CI.
7. Ideation enhance **UI** → [factory-ui](../factory-ui/SKILL.md), not this skill.

## Rules

| Rule | Detail |
|------|--------|
| No raw SDK in routes | Provider access only through runtime |
| Parent only | Not an entry skill |
| Mock in CI | Live LLM optional locally only |
| Shared playbooks | Obey workflow.md / execution.md when declared |
