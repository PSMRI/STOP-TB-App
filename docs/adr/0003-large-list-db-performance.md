# ADR-0003: DB write and query rules for 5k+ beneficiary lists

- Status: Accepted
- Date: 2026-10-28

## Context

With 1k–5k beneficiaries the All Beneficiaries / TrueNat / X-ray lists lagged or never finished
loading. Causes found: (1) duplicated network refreshes (ViewModel `init` + `onResume` + poll
worker re-enqueued with `REPLACE`); (2) row-by-row writes without transactions and
`OnConflictStrategy.REPLACE` writes of unchanged rows, each invalidating every observing query;
(3) expensive observed queries — the `BEN_BASIC_CACHE` view (5-table join + `GROUP BY`, cannot
push the village filter down), correlated subqueries in `ORDER BY`, an un-indexed `householdId`
self-join for child counts, and ~20 unscoped full-table flows; (4) O(n) work per row on the main
thread in adapters.

## Decision

Adopt the Room/DAO, list and worker musts in `knowledge/android-conventions.md`: batch writes in
transactions and skip unchanged rows; one throttled refresh per screen; `KEEP` for UI-triggered
unique work; village-scoped, column-minimal queries; index join/filter columns; no per-row
correlated subqueries in sort; Set/Map lookups and no full rebinds in adapters.

## Consequences

- Each DB invalidation is cheaper and much rarer; list cost no longer grows with sync activity.
- Schema/index changes need migrations + tests (destructive fallback is a data-loss risk).
- Some display data (sort key, latest order status) may need to be denormalized and maintained on
  write.

## Alternatives considered

- Bigger page size / more prefetch — hides the first-page cost, doesn't fix invalidation storms.
- Moving lists to in-memory filtering — worse memory and still re-runs on every change.
