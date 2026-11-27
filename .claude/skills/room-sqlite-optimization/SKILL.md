---
name: room-sqlite-optimization
description: Analyze and optimize Room DAO queries in STOP-TB — indexes, joins, pagination, transactions, Flow emissions, N+1 reads, redundant writes — validated with EXPLAIN QUERY PLAN on a pulled debug database.
---

# Room / SQLite Optimization (STOP-TB)

Never rewrite queries blindly. Every change needs a query plan or timing as evidence.

## Project facts
- DB class: `app/src/main/java/org/piramalswasthya/stoptb/database/room/InAppDb.kt` (version 63, `exportSchema = false`)
- DB file name: `Sakhi-2.0-In-app-database`
- **Debuggable builds are plain SQLite; release builds use SQLCipher** (`DatabaseKeyManager`, `RoomDbEncryptionHelper`). Always inspect a debug build. Never pull or decrypt a production/release DB or real patient data.
- DAOs: `database/room/dao/` (BenDao, HouseholdDao, TBDao, SyncDao, VitalDao, ... and `dynamicSchemaDao/`)

## Steps

1. **Inventory the queries on the slow path.** For each DAO method: SQL, return type (`Flow`, `PagingSource`, `suspend`, blocking), tables touched, callers.

2. **Pull a debug DB copy** (synthetic/test data only):
   ```bash
   PKG=org.piramalswasthya.stoptb   # or .uat
   adb exec-out run-as $PKG cat databases/Sakhi-2.0-In-app-database > scratch.db
   adb exec-out run-as $PKG cat databases/Sakhi-2.0-In-app-database-wal > scratch.db-wal 2>/dev/null
   sqlite3 scratch.db ".tables"
   ```
   Put the copy in the session scratchpad, not the repo.

3. **Check the plans:**
   ```sql
   EXPLAIN QUERY PLAN <query with realistic params>;
   PRAGMA index_list('BENEFICIARY');
   SELECT count(*) FROM <table>;
   .timer on
   ```
   Look for `SCAN <table>` on large tables, `USE TEMP B-TREE FOR ORDER BY`, correlated subqueries, `LIKE '%x%'` searches, JSON-in-TEXT columns parsed in Kotlin.

4. **Check usage patterns in Kotlin:**
   - N+1: a DAO call inside a loop or inside `map {}` over a list
   - `Flow` over many tables re-emitting on every sync write (Room invalidates on any write to an observed table) → `distinctUntilChanged()`, narrower queries
   - multiple writes without `@Transaction` / `withTransaction`
   - full lists loaded where `PagingSource` exists
   - `SELECT *` returning large blob/JSON columns for list screens
   - reads that WorkManager sync and UI do at the same time

5. **Propose changes** with before/after plan and timing: index (`@Entity(indices=...)` + migration), query rewrite, projection POJO, `@Transaction`, batching, paging.

## Rules
- A new index or column means a schema change → follow `/room-migration` (version bump + Migration, using the `columnExists`/`tableExists` helpers already in `InAppDb`).
- Propose the plan and wait for approval; edit with the Edit tool.
- Verify by compile: `./gradlew :app:compileStoptbDebugKotlin --console=plain -q`, then re-time the query.
