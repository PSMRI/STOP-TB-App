# Skills catalog — STOP-TB

Project skills for Claude Code (`.claude/skills/<name>/SKILL.md`). Pipeline:
[knowledge/workflow.md](../../knowledge/workflow.md). Musts: [knowledge/android-conventions.md](../../knowledge/android-conventions.md).

## Workflow

| Skill | Role |
|-------|------|
| [route-task](route-task/SKILL.md) | Entry point: classify, hand off to resolve-skills |
| [resolve-skills](resolve-skills/SKILL.md) | Needed-vs-have matrix; close GAPs |
| [plan-feature](plan-feature/SKILL.md) | Plan file under `plans/` |
| [grill-me](grill-me/SKILL.md) | Freeze open decisions, one question at a time |
| [implement-feature](implement-feature/SKILL.md) | Execute a plan via matrix skills |
| [fix-feature](fix-feature/SKILL.md) | Bug / performance fix, root cause first |
| [refactor-code](refactor-code/SKILL.md) | Behaviour-preserving restructure |
| [review-work](review-work/SKILL.md) | Compliance table vs conventions |
| [verify-feature](verify-feature/SKILL.md) | Build/tests/smoke evidence |

## Android craft

| Skill | Role |
|-------|------|
| [room-migration](room-migration/SKILL.md) | Schema change, version bump, migration + test |
| [room-dao-query](room-dao-query/SKILL.md) | Fast, scoped queries; batched transactional writes |
| [paged-ben-list](paged-ben-list/SKILL.md) | Paging list screens and adapters at 5k+ rows |
| [fragment-lifecycle](fragment-lifecycle/SKILL.md) | `launchOnView`, view-scoped work, cleanup |
| [add-form-screen](add-form-screen/SKILL.md) | Dataset + FormInputAdapter forms |
| [diagnostic-worker](diagnostic-worker/SKILL.md) | Workers, camp-hub diagnostics, refresh triggers |
| [android-test](android-test/SKILL.md) | Unit, DAO and migration tests |

## Meta

| Skill | Role |
|-------|------|
| [write-adr](write-adr/SKILL.md) | Record lasting "why" in `docs/adr/` |
| [update-docs](update-docs/SKILL.md) | Keep knowledge/CLAUDE.md as-built |
| [write-a-skill](write-a-skill/SKILL.md) | Author a new skill |
| [review-skill](review-skill/SKILL.md) | Gate before a skill is used |
| [update-skill](update-skill/SKILL.md) | Edit a skill after a miss |

## Known gaps (write a skill when first needed)

Network/API contract changes (Retrofit services + DTOs), ABHA flows, navigation graph changes,
CI/fastlane releases, localisation.

Validate wiring: `python3 scripts/validate_skills.py`.
The previous web-stack catalog is archived in `docs/skills-archive-web/` (not loaded).
