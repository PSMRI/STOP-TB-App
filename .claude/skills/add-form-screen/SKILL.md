---
name: add-form-screen
description: >-
  Adds or changes a STOP-TB data-entry form using the project's Dataset + FormElement +
  FormInputAdapter pattern: field definitions and dependencies, setUpPage from saved data,
  mapValues to the Room cache, ViewModel save with SyncState, and fragment wiring. Use when
  adding a new form, adding/removing a field or validation, changing field visibility logic, or
  fixing form save/edit/view-only behaviour.
knowledge:
  - knowledge/stack.md
  - knowledge/android-conventions.md
  - knowledge/testing.md
---

# Add Form Screen

## Knowledge

[stack.md](../../../knowledge/stack.md), [android-conventions.md](../../../knowledge/android-conventions.md).
Reference: `configuration/TBScreeningDataset.kt` + `ui/home_activity/non_communicable_diseases/tb_screening/form/`
(`TBScreeningFormViewModel`, `TBScreeningFormFragment`).

## Process

1. **Entity/field**: new persisted field → entity change + [room-migration](../room-migration/SKILL.md).
2. **Dataset** (`configuration/<Name>Dataset.kt` extends `Dataset`):
   - declare each `FormElement(id = unique int, inputType, title = resources.getString(...), required, entries...)`;
     strings go to `res/values*/strings.xml` (all locales present in the repo);
   - `setUpPage(ben, saved)` fills values from the saved cache and builds the visible list;
   - `handleListOnValueChanged(formId, index)` → show/hide dependants via `triggerDependants(...)`;
   - `mapValues(cacheModel, pageNumber)` copies values back into the cache (keep existing
     timestamps unless changed — see `TBScreeningDataset.mapValues`).
3. **ViewModel**: `val formList = dataset.listFlow`; `updateListOnValueChanged` →
   `dataset.updateList`; `saveForm()` on `Dispatchers.IO`: map values, set
   `syncState = UNSYNCED`, save through the repository (single transaction if several tables),
   then trigger the push worker via `WorkerUtils`.
4. **Fragment**: `FormInputAdapter(formValueListener, isEnabled = !viewOnly)`; collect
   `formList` with `viewLifecycleOwner.lifecycleScope.launch` **once** in `onViewCreated`
   (not inside an observer) — see [fragment-lifecycle](../fragment-lifecycle/SKILL.md).
   Validate with the adapter's validation before save.
5. **Navigation**: add the destination/args in `res/navigation/nav_home.xml` (SafeArgs).
6. **Roles**: gate entry points with `RoleManager` privileges, not role-name string checks.
7. **Tests**: unit test the dataset's `mapValues`/dependency logic when it has branching
   ([android-test](../android-test/SKILL.md)); manual: new, edit, view-only, offline save, sync.

## Rules

| Rule | Detail |
|------|--------|
| Pattern | Dataset + FormInputAdapter — no hand-built form layouts |
| Offline first | Save locally as UNSYNCED, push via worker |
| Localised | No hard-coded user-visible strings |
