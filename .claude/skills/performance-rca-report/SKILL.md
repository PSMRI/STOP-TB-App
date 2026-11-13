---
name: performance-rca-report
description: Write a root-cause analysis report for a STOP-TB performance problem or production bug — symptom, evidence, root cause, fix, before/after measurements, risks, test results and rollback plan.
---

# Performance / Bug RCA Report (STOP-TB)

Use findings already gathered (from `/android-performance-audit`, `/room-sqlite-optimization`, `/crash-anr-triage`, `/offline-sync-debug`). Don't invent numbers; anything missing is written as "not measured".

## Template

```markdown
# RCA: <ticket id> – <short title>

## Summary
One paragraph: what users saw, root cause, fix, result.

## Impact
Screens/flows affected, users/devices, build versions, data size, how often.

## Reproduction
Device, flavor (stoptb / stoptbUat), build, data, exact steps.

## Evidence
- Measurements (median of N runs): cold start, frame stats, query time, API call count
- Trace / logcat excerpts (personal data removed)
- EXPLAIN QUERY PLAN output if DB related

## Root cause
Code path with file:line references. Why it happens, and why it wasn't caught earlier.

## Fix
What changed and why it is the minimal safe change. Files touched. DB migration (yes/no, version).

## Before / after
| Metric | Before | After | Delta |

## Verification
Compile result, manual journeys run (pass/fail), regression check results.

## Risks
Side effects, shared code touched (e.g. CFU vs Community/Occupation), sync/data migration risk.

## Rollback
How to revert (commit/PR), whether a DB migration makes rollback unsafe, data cleanup needed.

## Follow-ups
Deferred items and their owners.
```

## Rules
- No patient names, IDs, phone numbers, tokens or real health records in the report.
- Print the report in chat. Save it to a file only if the user says where (e.g. `docs/`).
