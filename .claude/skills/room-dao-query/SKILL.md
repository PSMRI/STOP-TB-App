---
name: room-dao-query
description: >-
  Writes or changes STOP-TB DAO queries and write paths so they stay fast at 5k+ beneficiaries:
  village scoping, minimal columns, indexed joins, PagingSource queries, transactional batch
  writes, skip-unchanged updates, and Flow invalidation awareness. Use when adding or editing a
  @Query/@Insert/@Update in database/room/dao, a repository save/sync loop, or when a list is slow
  or re-queries too often.
knowledge:
  - knowledge/stack.md
  - knowledge/android-conventions.md
  - knowledge/testing.md
---

# Room DAO Query

## Knowledge

[android-conventions.md](../../../knowledge/android-conventions.md) (Room/DAO musts), [stack.md](../../../knowledge/stack.md).

## Reads

1. Every UI query takes the village: `villageId IN (:villageIds)` / `loc_village_id = :selectedVillage`
   (`RecordsRepo.selectedVillage`). No unscoped `SELECT benId FROM TB_SCREENING` flows.
2. Select only needed columns into a small data class (e.g. `BenChildCount`), not whole entities,
   for badges/status maps.
3. Every column in `JOIN`/`WHERE`/correlated subquery is indexed — check `@Entity(indices)`;
   missing → [room-migration](../room-migration/SKILL.md).
4. `BEN_BASIC_CACHE` is a heavy view (see stack.md). For new list queries prefer selecting from
   `BENEFICIARY` with targeted `EXISTS` subqueries; never add per-row correlated subqueries to
   `ORDER BY` — store a sort key/status column instead.
5. Lists → `PagingSource<Int, T>` (Room paging), not `Flow<List<T>>` of thousands of rows.
6. Remember: a `Flow`/`PagingSource` re-runs on **any** write to **any** table it reads — keep the
   table set minimal.

## Writes

1. Multi-row writes in one transaction: inject `db: InAppDb` in the repository
   (example: `repositories/dynamicRepo/CounsellingRepositoryImpl.kt`) and wrap in
   `db.withTransaction { }`. Do network calls **outside** the transaction.
2. Skip no-op writes: compare the new object with the stored one before saving.
3. Prefer `@Update` / `@Upsert` / targeted `UPDATE ... SET col = :v WHERE ...` over
   `@Insert(onConflict = REPLACE)` for rows observed by lists.
4. Lookups inside loops: load once into a `Map` before the loop (no `getDiagnosticsList()` per item,
   no per-item `getBenByRegId` on un-indexed columns).
5. Server pulls respect local unsynced rows (`shouldApplyServerRecord`, `SyncState`).

## Test

DAO test via [android-test](../android-test/SKILL.md): village scoping, filter/sort, empty case.

## Rules

| Rule | Detail |
|------|--------|
| Scoped | Village filter on UI queries |
| Batched | `withTransaction` for > 1 row |
| Cheap invalidation | No unchanged REPLACE writes |
