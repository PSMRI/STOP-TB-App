# Route-task dry-run (skills-first smoke checklist)

Checklist-only. Do **not** implement a feature while walking this.

## Simulated request

> Add a Notes CRUD resource with `title` and `body`.

## Expected skill chain

- [ ] Load `route-task` (classify → feature) per `knowledge/workflow.md`
- [ ] `resolve-skills` → needed-vs-have matrix (CRUD, state, UI, …)
- [ ] Close any GAP before continuing
- [ ] `plan-feature` → knowledge + gates
- [ ] `grill-me` freezes fields/routes/UX
- [ ] Plan written to `plans/YYYY-MM-DD-notes.md` (includes matrix)
- [ ] User asks to implement → `implement-feature`
- [ ] Stack/craft from matrix only
- [ ] Shared playbooks: `knowledge/workflow.md` + `knowledge/execution.md` + `knowledge/tdd.md`
- [ ] TDD: RED tests via `add-test` before production UI code
- [ ] Executor conflict check — raise on contradictory knowledge/rules (`Contract conflict`) per `knowledge/execution.md`
- [ ] `review-work` then `verify-feature` (incl. `make test`) before done

> Note: full Notes CRUD spanning API + DB is an **API-repo** concern (ADR-0040). In this UI repo the slice is the Notes page + `src/api/notes.ts` client against the remote API.

## Skill-gap branch

> “Help me set up Playwright e2e for this app.”

- [ ] `route-task` → `resolve-skills` → Tests = GAP or `add-test` gate
- [ ] `find-skills` / `write-a-skill` → `review-skill`
- [ ] Ask before `npx skills add`

## Contract-conflict branch

> “Change Postgres host port to 5432 but leave stack.md on 5433.”

- [ ] `route-task` → `resolve-skills` → `manage-infra`
- [ ] Raise `Contract conflict` — Hard stop (do not silently pick a side)
- [ ] `update-docs` to align, then re-resolve / continue

## Bug branch

> “Items list returns 500.”

- [ ] `route-task` → `resolve-skills` (no full plan required)
- [ ] `fix-feature` → `add-test` (RED) → patch → `review-work` → `verify-feature`
