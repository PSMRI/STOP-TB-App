---
name: room-migration
description: >-
  Changes the STOP-TB Room schema safely: new table/column/index, BEN_BASIC_CACHE view changes,
  InAppDb version bump, Migration object, builder registration and a migration test — without
  triggering the destructive fallback that wipes unsynced field data. Use when adding or changing
  an entity field, adding an index for performance, editing the BEN_BASIC_CACHE view, or the
  user says add column / add index / migration / bump DB version.
knowledge:
  - knowledge/stack.md
  - knowledge/android-conventions.md
  - knowledge/testing.md
---

# Room Migration

## Knowledge

[stack.md](../../../knowledge/stack.md) (sharp edges), [android-conventions.md](../../../knowledge/android-conventions.md) (Room),
[testing.md](../../../knowledge/testing.md) (migration test).

**Danger:** `InAppDb` builder has `.fallbackToDestructiveMigration()`. A missing path from the
installed version to the new one silently deletes the whole DB on field devices.

## Process

1. **Entity** (`model/*.kt`): add the field / `Index(name = "ind_<table>_<cols>", value = [...])`
   in `@Entity(indices = [...])`. Existing BENEFICIARY indices are in `model/Ben.kt` (`ind_ben_*`).
2. **Version**: `@Database(... version = N+1)` in `database/room/InAppDb.kt`.
3. **Migration** in the `InAppDb` companion, next to the latest one:

```kotlin
val MIGRATION_57_58 = object : Migration(57, 58) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `BENEFICIARY` ADD COLUMN `foo` TEXT")
        database.execSQL("CREATE INDEX IF NOT EXISTS `ind_ben_hh` ON `BENEFICIARY` (`householdId`)")
    }
}
```

   - SQL must produce **exactly** what Room expects: same column type/nullability/default, same
     index name and column order — otherwise Room throws "Migration didn't properly handle".
   - Non-null new column ⇒ `NOT NULL DEFAULT <value>` matching the entity `@ColumnInfo(defaultValue)`.
   - Guard re-runnable steps (`IF NOT EXISTS`, or `PRAGMA table_info` checks as other migrations do).
4. **Register**: add `.addMigrations(MIGRATION_57_58)` after `MIGRATION_56_57` in the builder
   (bottom of `InAppDb.kt`).
5. **View change** (`BEN_BASIC_CACHE`): edit both `@DatabaseView` in `model/Ben.kt` **and**
   `recreateBenBasicCacheView()` in `InAppDb.kt` with identical SQL, and call
   `recreateBenBasicCacheView(database)` in the new migration. Keep the view cheap — no new
   `GROUP BY`/joins that multiply rows; prefer `EXISTS(...)` flags.
6. **Test** via [android-test](../android-test/SKILL.md): copy `PrescriptionMigrationTest`
   (build DB, set `PRAGMA user_version` to the old version, reopen with `addMigrations`, assert
   data preserved + new column/index present via `PRAGMA index_list`).
7. Large data backfills: do them in the migration with set-based SQL (`UPDATE ... SELECT`), never
   row-by-row Kotlin loops.

## Rules

| Rule | Detail |
|------|--------|
| Always a migration | Never rely on destructive fallback |
| Exact match | Migration SQL ≡ entity/view definition |
| Tested | A migration test is part of the change |
| One step | One version step per release; never edit an already-shipped migration |
