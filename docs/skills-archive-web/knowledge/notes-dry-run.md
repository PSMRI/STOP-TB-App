# Notes resource dry-run (skill smoke test)

This is a **checklist-only** validation. Do **not** implement Notes unless a feature request asks for it — Items remains the golden path client slice.

> **ADR-0040:** API model/migration steps below belong in the **separate API repo**. In this UI repo only §3 (`add-page`) applies.

## Simulated request

> Add a Notes CRUD resource with `title` and `body` fields.

## Walkthrough against skills

### 1. add-api-endpoint (API repo)

Would create (in the API repository):

- [ ] model / schemas / routes mirroring `items`

### 2. add-migration (API repo)

Would run Alembic migrate targets in the API repository.

### 3. add-page (this UI repo)

Would create:

- [ ] `src/api/notes.ts`
- [ ] `src/pages/NotesPage.tsx`
- [ ] Route `/notes` in `App.tsx`
- [ ] Link from Home

**Gap check:** Covered. Client must use `api` from `client.ts`. OK.

### 4. implement-feature / plan-feature

- [ ] plan-feature → grill-me gates (fields, routes) → plan
- [ ] implement-feature selects `add-page` (+ craft) here; API skills in the API repo

**Gap check:** Pipeline links present in workflow skills. OK.

## Composition check

- [ ] `add-crud-resource` still references `add-api-endpoint` / `add-migration` / `add-page` (API + UI composition across repos)
- [ ] `add-page` → `web-state` / `web-ui`
