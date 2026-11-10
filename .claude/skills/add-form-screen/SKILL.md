---
name: add-form-screen
description: Add or change a STOP-TB form or list screen the house way — Dataset, HiltViewModel, Fragment with FormInputAdapter, nav graph entry, role visibility and strings in English, Hindi and Assamese. Use for new forms, new fields, validation changes or new list screens.
---

# Add Form Screen

Reference implementation: TB Screening.

| Piece | Reference |
|---|---|
| Form definition | `configuration/TBScreeningDataset.kt` (extends `configuration/Dataset.kt`) |
| ViewModel | `ui/home_activity/non_communicable_diseases/tb_screening/form/TBScreeningFormViewModel.kt` |
| Fragment | `.../tb_screening/form/TBScreeningFormFragment.kt` (uses `adapters/FormInputAdapter`) |
| List | `.../tb_screening/list/TBScreeningListFragment.kt` + ViewModel |
| Navigation | `res/navigation/nav_home.xml` (`TBScreeningFormFragment` with `benId`, `viewOnly` args; 2.3+ also `autoFlow`, `syncImmediately`) |
| Role visibility | `configuration/RoleModuleConfig.kt` (release-2.2 and later; doesn't exist on 1.1–2.1) |

## New field on an existing form

1. Add the `FormElement` in the Dataset; wire it into `setUpPage(...)`.
2. Dependent visibility/auto-fill → `handleListOnValueChanged(formId, index)`.
3. Persist: `mapValues(cacheModel, …)` writes it to the cache entity → new column → `room-migration`.
4. If the server needs it → `add-sync-flow`.
5. Strings (title, options, errors) in all three locales.

## New form screen

1. **Dataset** `configuration/<Name>Dataset.kt` extending `Dataset(context, currentLanguage)`: elements, `setUpPage`, `handleListOnValueChanged`, `mapValues`.
2. **ViewModel** `@HiltViewModel` with `SavedStateHandle` (Safe Args), `State { IDLE, SAVING, SAVE_SUCCESS, SAVE_FAILED }`, `formList = dataset.listFlow`, `updateListOnValueChanged`, `saveForm()` on `Dispatchers.IO`. Re-resolve temp `benId` before saving; set `syncState = UNSYNCED`; trigger sync via `WorkerUtils` the way the reference does.
3. **Fragment** `@AndroidEntryPoint`, binding to the shared form layout, `FormInputAdapter` with `FormValueListener`, validate via `adapter.validateInput(...)` before save, observe `state`.
4. **Nav graph**: add the `<fragment>` with typed `<argument>`s and an `<action>` from the list/home screen.
5. **Role access**: update `RoleModuleConfig` if only some roles see it (`RoleModuleConfigTest` covers this). On branches without `RoleModuleConfig`, follow how the nearest existing screen gates access.
6. **Strings**: every label/option/error in `res/values/strings.xml`, `values-hi/strings.xml`, `values-as/strings.xml`. Ask the user for Hindi/Assamese copy if not provided; never leave a locale missing.
7. **Tablet layouts**: if you add a layout that has a `layout-sw600dp` twin, update both.
8. **Tests** (new feature): write JVM tests for the Dataset rules (visibility, auto-fill, validation, `mapValues`) via `add-unit-test`.

## Rules

- Forms are XML + `FormInputAdapter`; no Compose.
- Reuse the existing fragment/adapter/card for new lists; only the content changes.
- No DB/network work in Fragments; go through ViewModel → Repository.
- `viewOnly` mode must disable inputs and hide save.
- Don't edit or reword existing UI strings or shared constants.
- CFU shares form code with Community/Occupation contact tracing: put CFU-only behaviour in guarded CFU-only functions; don't change the shared path.
- Show the touch list and wait for OK before editing several files; edit with the Edit tool.
