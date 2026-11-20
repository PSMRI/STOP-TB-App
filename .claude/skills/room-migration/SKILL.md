---
name: room-migration
description: Make a safe Room schema change in STOP-TB on any release branch — new entity, column, index or database view — by detecting the branch's migration style, bumping the InAppDb version, writing the Migration, updating DTO/API mapping and verifying upgrades. Also use for "Migration didn't properly handle" crashes. Plan-first workflow.
---

# Room Schema Change + Migration (STOP-TB)

Field tablets hold **unsynced patient data**. A bad or missing migration either crashes the app at launch or wipes records, so this skill is strict.

## Step 0 — Detect the branch's migration style (never assume)

```bash
git branch --show-current
grep -nE "const val DB_VERSION|version = [0-9]+|val MIGRATIONS|exportSchema|fallbackToDestructive|addMigrations" \
  app/src/main/java/org/piramalswasthya/stoptb/database/room/InAppDb.kt
```

| | **Style A — array** (release-2.3, release-2.4) | **Style B — inline** (release-1.1 … 2.2.3, release-2.5, main) |
|---|---|---|
| Version | `const val DB_VERSION = N` in the companion | `@Database(version = N, …)` literal |
| Registration | `internal val MIGRATIONS: Array<Migration>` + `.addMigrations(*MIGRATIONS)` | One `.addMigrations(MIGRATION_x_y)` call per migration in `getInstance` |
| Schema export | `exportSchema = true`, `room.schemaLocation` → `app/schemas/` | `exportSchema = false` — no schema JSON |
| Chain test | `app/src/test/.../database/room/InAppDbMigrationsTest.kt` | none |
| Fallback | `.fallbackToDestructiveMigrationFrom(1)` | plain `.fallbackToDestructiveMigration()` — **any gap in the chain wipes local data** |

Use whichever style the branch has; don't convert B → A as part of a feature. If the branch is Style B, mention the destructive-fallback risk in the plan, but don't change it unless the user asks.

## Files

- `app/src/main/java/org/piramalswasthya/stoptb/database/room/InAppDb.kt` — `@Database(entities, views)`, version, `MIGRATION_x_y` objects
- Entities: `model/…Cache.kt` · DAOs: `database/room/dao/` · new DAO → `abstract val xDao` in `InAppDb` + `@Provides` in `di/AppModule.kt`
- Helpers already in `InAppDb` (all branches): `columnExists(db, table, column)`, `tableExists(db, table)`, `addLocationRecordExtraColumns(...)` — use them so migrations are idempotent
- Data-preserving test example: `app/src/androidTest/.../database/room/PrescriptionMigrationTest.kt` (release-2.3, 2.4, 2.5)

## Step 1 — Plan (send to the user and wait for OK)

- Branch and style detected in Step 0, current version → new version (exactly +1) and the `MIGRATION_<old>_<new>` name
- **Version clash check**: release branches have diverged (at time of writing 2.3 = 61, 2.4 = 67, 2.5 = 61). Before picking `<new>`, check it isn't already used on another release branch that will be merged into this one:
  `git grep -n "MIGRATION_<old>_<new>" release-2.3 release-2.4 release-2.5 -- '*InAppDb.kt'`
  If it is, raise it with the user instead of reusing the number.
- Entity and table names, new columns with Kotlin type → SQLite type, nullability, default
- DTO / API field names (Moshi `@Json(name=...)` / Gson `@SerializedName`) for push and pull, and where entity ↔ DTO mapping happens
- DAO queries that need updating
- Whether existing rows need a backfill

## Step 2 — Implement (Edit tool, one file at a time)

1. **Entity** field — nullable or with a default, so existing rows stay valid.
2. **Bump** the version by exactly 1 — Style A: `DB_VERSION`; Style B: `version = N`.
3. **Migration**:
   ```kotlin
   val MIGRATION_61_62 = object : Migration(61, 62) {
       override fun migrate(database: SupportSQLiteDatabase) {
           if (!columnExists(database, "TABLE", "newColumn")) {
               database.execSQL("ALTER TABLE `TABLE` ADD COLUMN `newColumn` TEXT")
           }
       }
   }
   ```
   - Type, nullability and default must match the entity **exactly** (Kotlin non-null primitive → `NOT NULL DEFAULT …`).
   - New table / index: copy the `CREATE TABLE` / `CREATE INDEX` SQL Room generates instead of writing it by hand — from `app/schemas/…/<N>.json` (Style A, after a build) or the generated `InAppDb_Impl` (both styles). Index names must match `@Entity(indices)`: `CREATE INDEX IF NOT EXISTS index_<table>_<col> ON <table>(<col>)`.
   - View change: `DROP VIEW IF EXISTS` then `CREATE VIEW` with the exact `@DatabaseView` SQL (see the `BEN_BASIC_CACHE` migrations).
   - Column rename / type change / drop: create new table → copy data → drop old → rename (SQLite can't alter in place). Preserve `syncState` and IDs.
4. **Register** — Style A: append to the end of `MIGRATIONS`; Style B: add `.addMigrations(MIGRATION_<old>_<new>)` after the last one.
5. **DTO + mapping + DAO** changes. If the new data must reach the server, update the sync payload too (`add-sync-flow`).

## Step 3 — Verify

- Compile: `./gradlew :app:compileStoptbDebugKotlin --console=plain -q`
- Style A only:
  - Chain test: `./gradlew :app:testStoptbUatDebugUnitTest --tests "*InAppDbMigrationsTest"`
  - Build once so Room exports `app/schemas/…/<new>.json`. `app/schemas/` isn't committed on any branch yet — ask the user whether to include it.
- If the migration transforms existing data (copy / rename / backfill), propose an instrumented test like `PrescriptionMigrationTest`: build the DB at the old version, insert rows, migrate, assert the rows survive.
- Upgrade test: install the **previous** build (e.g. from the `release-x` branch), create data including unsynced rows, then `adb install -r` the new build and open the affected screens. Watch logcat for `IllegalStateException: Migration didn't properly handle` / `Expected ... Found ...` — the diff between Expected and Found shows which column/index SQL doesn't match the entity.
- Fresh install test.
- Release builds are SQLCipher-encrypted: migrations run the same way, but test an upgrade on a release-signed or UAT build before shipping.

## Rules

- Never add or widen a destructive fallback — offline unsynced health data would be lost. Style A keeps only the existing `From(1)`.
- Never edit or reorder a migration that has already shipped; fix forward with a new one.
- Never reuse a migration number already used on another release branch.

## Checklist

- [ ] Style detected; version +1 with a matching `MIGRATION_<old>_<new>`, registered the branch's way
- [ ] Number not already used on another release branch
- [ ] SQL matches the entity (types, NOT NULL, defaults, indices); idempotent via `columnExists` / `tableExists`
- [ ] Existing rows get sensible defaults; `syncState` and IDs untouched
- [ ] No destructive fallback added; shipped migrations unchanged
- [ ] Compiles; Style A chain test passes and schema JSON exported; upgrade + fresh install tested
