# Skills catalog

Skills stay **flat** under `.cursor/skills/<name>/`.

**Pipeline (single source):** [`knowledge/workflow.md`](../../knowledge/workflow.md)  
**Execute / TDD:** [`knowledge/execution.md`](../../knowledge/execution.md) · [`knowledge/tdd.md`](../../knowledge/tdd.md)  
**Always-apply pointer:** [`.cursor/rules/skills-first.mdc`](../rules/skills-first.mdc)

Do **not** fork the pipeline diagram into this catalog — edit `workflow.md` only.

---

## Workflow

| Skill | Role |
|-------|------|
| [route-task](route-task/SKILL.md) | Classify only; hand off to resolve-skills |
| [resolve-skills](resolve-skills/SKILL.md) | Needed vs have matrix; close GAPs |
| [plan-feature](plan-feature/SKILL.md) | Scope + plan file (uses matrix) |
| [grill-me](grill-me/SKILL.md) | Freeze gates |
| [write-adr](write-adr/SKILL.md) | Lasting why |
| [implement-feature](implement-feature/SKILL.md) | Execute matrix skills (TDD) |
| [fix-feature](fix-feature/SKILL.md) | Bug fix via matrix (TDD) |
| [review-work](review-work/SKILL.md) | Compliance vs matrix knowledge/rules |
| [verify-feature](verify-feature/SKILL.md) | Smoke / DoD / `make test` |
| [find-skills](find-skills/SKILL.md) | skills.sh discovery |
| [write-a-skill](write-a-skill/SKILL.md) | Author skill |
| [review-skill](review-skill/SKILL.md) | Required before new skill use |
| [update-skill](update-skill/SKILL.md) | Edit skill |

---

## Craft

| Skill | Role |
|-------|------|
| [web-state](web-state/SKILL.md) | Page-local React state (parent only) |
| [web-ui](web-ui/SKILL.md) | Default UI (parent only) |
| [factory-ui](factory-ui/SKILL.md) | Project Factory wizard chrome / agents_flow look (parent only) |
| [factory-llm](factory-llm/SKILL.md) | **API repo** — LangGraph/LangChain LLM runtime |
| [api-auth](api-auth/SKILL.md) | Auth gate → keycloak-auth (server side → API repo) |
| [keycloak-auth](keycloak-auth/SKILL.md) | Client Keycloak SSO (parent only); server JWT in API repo |
| [api-patterns](api-patterns/SKILL.md) | **API repo** — beyond plain CRUD |
| [add-test](add-test/SKILL.md) | Web TDD (Vitest); E2E gated |

---

## Stack

| Skill | Role |
|-------|------|
| [add-page](add-page/SKILL.md) | Page shell → web-state/ui (parent only) |
| [add-crud-resource](add-crud-resource/SKILL.md) | **API repo** — full vertical slice (not used in this tree) |
| [add-migration](add-migration/SKILL.md) | **API repo** — Alembic (not used in this tree) |
| [add-api-endpoint](add-api-endpoint/SKILL.md) | **API repo** — model/schemas/routes (not used in this tree) |

> **ADR-0040:** This repository is UI-only. Mark DB/API/migration/factory-llm concerns **N/A** (or GAP → hand off to the API repo) in the resolve-skills matrix. Do not recreate `apps/api` here.

---

## Meta / maintain

| Skill | Role |
|-------|------|
| [refactor-code](refactor-code/SKILL.md) | Behavior-preserving refactor |
| [manage-infra](manage-infra/SKILL.md) | Docker/Make/CI/env |
| [update-docs](update-docs/SKILL.md) | knowledge/rules/README |

---

## When to use which

| Intent | Start with |
|--------|------------|
| Any mutating development task | `route-task` → `resolve-skills` (see workflow.md) |
| Feature (CRUD or not) | then `plan-feature` → `implement-feature` |
| Bug | then `fix-feature` |
| Refactor / infra / docs / skills | classify table in workflow.md |
| Skill gap | closed inside `resolve-skills` |

Knowledge (how) · rules (musts) · [docs/adr/](../../docs/adr/) (why) · [plans/](../../plans/)

```bash
make validate-skills
make validate-skills-strict
```

Scenarios: [`scenarios/`](scenarios/). See ADR-0008–0015.
