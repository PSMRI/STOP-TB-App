# PR #377 CodeRabbit follow-ups

PR: [#377 Merge updated release-2.4 into release-2.5](https://github.com/PSMRI/STOP-TB-App/pull/377)

CodeRabbit left 18 review comments on this merge PR, all labelled **Major**. None of them is on code written in the merge commit (`e991fd68`). `git blame` traces every commented line to an existing `release-2.4` commit, which the merge brought into `release-2.5` unchanged. The PR diff shows those lines as new only because it compares against `release-2.5`.

These items are therefore out of scope for the merge PR and will be addressed in separate PRs. Fixes should go to `release-2.4` first, then be merged forward into `release-2.5`.

Line numbers refer to the PR head (`e991fd68`).

## Triage summary

| # | File | Area | Origin commit | Triage |
|---|---|---|---|---|
| 1 | `helpers/TokenExpiryManager.kt:38` | Auth | `633ce5cc` | Follow-up PR, high priority |
| 2 | `repositories/UserRepo.kt:339` | Auth | `6230885a` | Follow-up PR, high priority |
| 3 | `work/DiagnosticResultPollWorker.kt:78` | Order results | `72793e5b` (STOP-800) | Follow-up PR, high priority |
| 4 | `repositories/TBRepo.kt:3646` | Logging / privacy | `24e93523` | Follow-up PR, quick win |
| 5 | `repositories/TBRepo.kt:906` | Contact tracing | `20b0495c` | Follow-up PR, quick win |
| 6 | `repositories/TBRepo.kt:847` | Order feed | `72793e5b` (STOP-800) | Needs design decision |
| 7 | `repositories/TBRepo.kt:880` | Order feed / sync state | `24e93523` | Needs design decision |
| 8 | `repositories/TBRepo.kt:2811` | Manual result | `72793e5b` (STOP-800) | Needs backend input |
| 9 | `repositories/TBRepo.kt:3527` | X-ray result | `24e93523` | Needs verification |
| 10 | `repositories/TBRepo.kt:3600` | TB confirmation | `9727305d` (STOP-800) | Needs design decision |
| 11 | `repositories/TBRepo.kt:3698` | Order discovery | `30c38612` (STOP-471) | Needs design decision |
| 12 | `repositories/TBRepo.kt:3970` | MTB result | `72793e5b` (STOP-800) | Needs verification |
| 13 | `repositories/ChangesFeedRepo.kt:127` | Hub order ownership | `d97a4adc` | Needs design decision |
| 14 | `tb_suspected/quick/TBSuspectedQuickFragment.kt:275` | Form save | `72793e5b` (STOP-800) | Follow-up PR, quick win |
| 15 | `tb_suspected/quick/TBSuspectedQuickViewModel.kt:350` | RIF result | `72793e5b` (STOP-800) | Needs verification |
| 16 | `tb_suspected/quick/TBSuspectedQuickViewModel.kt:407` | Manual result | `72793e5b` (STOP-800) | Needs backend input |
| 17 | `configuration/TBSuspectedQuickDataset.kt:1142` | RIF-only entry | `72793e5b` (STOP-800) | Needs product decision |
| 18 | `repositories/BenRepo.kt:732` | Single-beneficiary push | `e9ba226b` | Dormant (caller disabled) |

## Details

### 1. Authentication cleared after a response-parsing failure
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/helpers/TokenExpiryManager.kt:38`
- **Comment:** [discussion_r4627735787](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627735787)
- **Issue:** If fallback login returns HTTP 200 with malformed JSON, a generic exception handler can call `onRefreshFailed()`. Because the threshold is one, this deletes credentials and cancels all work without a real authentication rejection.
- **Suggested fix:** Separate response and server failures from authentication rejection. Clear stored tokens only after an authentication rejection, not after transient failures.

### 2. Re-authentication guard cleared before sign-in completes
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/repositories/UserRepo.kt:339`
- **Comment:** [discussion_r4627735796](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627735796)
- **Issue:** The flag is cleared before `authenticateUser()` calls `setUserRole()`. If user-details loading fails, the flag stays false. A later sign-in by another user passes `expectedUserId = null` and can replace the saved user while the previous user's unsynced records remain.
- **Suggested fix:** For interactive sign-in, clear the flag only after `setUserRole()` succeeds.

### 3. Feed-skipped orders dropped before their result is fetched
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/work/DiagnosticResultPollWorker.kt:78`
- **Comment:** [discussion_r4627721940](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627721940)
- **Issue:** Every feed-active run clears the feed-skipped set without requesting those results. `feedSkippedOrdersPastGrace` has no caller and `hasSkippedFeedOrders` is fixed to `false`. A completed order can stay `PENDING` locally.
- **Suggested fix:** Remove the unconditional clear. Fetch each skipped order after the grace period through `feedSkippedOrdersPastGrace`, and drop it only after `fetchOrderResult` succeeds.

### 4. Raw refusal text written to sync logs
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/repositories/TBRepo.kt:3646`
- **Comment:** [discussion_r4627721866](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627721866)
- **Issue:** The `TB_SUSPECTED_DIRTY` warning includes old and new values of the four `reasonForRefusal*` fields, which can hold free text, together with `benId`. Release builds send it to the sync log store and Crashlytics.
- **Suggested fix:** Log only the field names for these four fields.

### 5. One failed save stops the contact risk-factor loop
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/repositories/TBRepo.kt:906`
- **Comment:** [discussion_r4627721825](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627721825)
- **Issue:** `applyKnownTbContactRiskFactors` has no per-item error handling. If `tbDao.saveTbScreening` fails for one contact, the remaining contacts do not get the "Contact of Known TB" risk factor, and nothing retries.
- **Suggested fix:** Catch failures per screening (rethrow `CancellationException`), log them and continue. Decide whether failed contacts need a retry.

### 6. Result discarded while the local order ID is missing
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/repositories/TBRepo.kt:847`
- **Comment:** [discussion_r4627721805](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627721805)
- **Issue:** `createOrder()` can succeed without an order ID and start the 90-second grace period. A completed result arriving through the feed in that window is dropped by `isOlderThanLocalOrder()`, and the feed token advances, so the card can remain pending.
- **Suggested fix:** Keep a durable reconciliation item when the order identity is uncertain, fetch it after the grace period, and remove it only after a successful fetch or a matching feed update.

### 7. Feed merge marks a row `SYNCED` while it holds unsent local edits
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/repositories/TBRepo.kt:880`
- **Comment:** [discussion_r4627721816](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627721816)
- **Issue:** The feed transaction forces `syncState = SYNCED` on the merged diagnostics row. Unsent local edits in that row (e.g. culture) are then never uploaded. The same assignment exists in `applyOrderResult`.
- **Suggested fix:** Preserve `UNSYNCED` when the row has unsent local edits.

### 8. Manual-result response can be saved onto a newer order
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/repositories/TBRepo.kt:2811`
- **Comment:** [discussion_r4627721838](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627721838)
- **Issue:** If a result is submitted for order A and order B is created before the response arrives, A's response is saved onto B's row. Offline replay has the same gap.
- **Suggested fix:** Carry the expected order ID through submission, replay and acknowledgment, and revalidate under the lock before saving. Related to #16.

### 9. Stale X-ray result restored into `TB_SUSPECTED`
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/repositories/TBRepo.kt:3527`
- **Comment:** [discussion_r4627721850](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627721850)
- **Issue:** A new PENDING X-ray order with `results=[]` clears `TB_DIAGNOSTICS.chestXRayResult`, but the fallback restores the previous result in `TB_SUSPECTED`, so the presumptive dashboard can still count the beneficiary.
- **Suggested fix:** Carry the authoritative clear through to `TB_SUSPECTED`; keep the fallback only when the field was omitted.

### 10. Confirmation recorded outside the diagnostic order is cleared
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/repositories/TBRepo.kt:3600`
- **Comment:** [discussion_r4627721858](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627721858)
- **Issue:** Contact tracing can set the suspected-case confirmation flags without an MTB or culture result. A later unrelated X-ray order sets both flags to false and queues that for upload.
- **Suggested fix:** Clear only confirmation that came from the result being withdrawn.

### 11. No discovery of server orders for PENDING rows without an order ID
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/repositories/TBRepo.kt:3698`
- **Comment:** [discussion_r4627721904](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627721904)
- **Issue:** The removed status sweep was the only automatic path that backfilled missing order IDs on hubs without the changes feed. `DiagnosticResultPollWorker` only selects rows with a real order ID, so these rows never get a result, and Retry Referral can create a duplicate order. Applies to X-ray, TrueNat and RIF.
- **Suggested fix:** On hubs without the changes feed, discover the server order for each PENDING row that lacks an ID, then store the ID and result.

### 12. Existing MTB result erased by a legacy reply
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/repositories/TBRepo.kt:3970`
- **Comment:** [discussion_r4627721913](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627721913)
- **Issue:** A same-order flat `COMPLETED` reply without `resultSummary` sets `naatResult = null`, erasing the result but keeping the confirmation flags.
- **Suggested fix:** Preserve the saved result when a same-order flat response omits it; still allow explicit closure, a new order or an empty `results` list to clear it.

### 13. Referrals saved while the hub owns order creation may never get an order
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/repositories/ChangesFeedRepo.kt:127`
- **Comment:** [discussion_r4627721791](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627721791)
- **Issue:** With `hubCreatesOrders = true`, a referral is saved without an order status and skips the order push. If the flag later clears, nothing revisits those referrals.
- **Suggested fix:** Define a rule that gives each such referral an order, or a clear status, when the tablet takes back order creation.

### 14. Preview read failure leaves the submit guard set
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/ui/home_activity/non_communicable_diseases/tb_suspected/quick/TBSuspectedQuickFragment.kt:275`
- **Comment:** [discussion_r4627721921](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627721921)
- **Issue:** If the Room read for the preview throws, the save error is not shown and `confirmingSubmit` is not released.
- **Suggested fix:** Catch non-cancellation failures, show a retryable error and release the guard.

### 15. RIF eligibility checked against a stale snapshot
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/ui/home_activity/non_communicable_diseases/tb_suspected/quick/TBSuspectedQuickViewModel.kt:350`
- **Comment:** [discussion_r4627721932](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627721932)
- **Issue:** If the feed stores a RIF result while a manual result is being confirmed for the same order, `planSubmissions` still uses the old dataset snapshot and sends the manual submission.
- **Suggested fix:** Re-check RIF eligibility against the fresh Room row before `submitManualResult`.

### 16. Manual-result POST is not bound to the checked order
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/ui/home_activity/non_communicable_diseases/tb_suspected/quick/TBSuspectedQuickViewModel.kt:407`
- **Comment:** [discussion_r4627735807](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627735807)
- **Issue:** The `order/manualResult` request carries only `beneficiaryId`, `orderType` and the result fields, so the backend picks the order. If the current order changes between the check and the POST, the result goes to the replacement order.
- **Suggested fix:** Send the checked order ID in the request (needs backend support), or re-verify it immediately before each POST. Related to #8.

### 17. RIF-only entry blocked by an older saved collection date
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/configuration/TBSuspectedQuickDataset.kt:1142`
- **Comment:** [discussion_r4627735780](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627735780)
- **Issue:** On RIF-only entry for a saved MTB Detected result, the restored collection date falls outside the yesterday-to-today range, so validation fails. The worker can only submit by changing the recorded specimen date.
- **Suggested fix:** Product to define the expected behavior for an older saved collection date on RIF-only entry.

### 18. Targeted beneficiary push skips the stale `SYNCING` reset
- **File:** `app/src/main/java/org/piramalswasthya/stoptb/repositories/BenRepo.kt:732`
- **Comment:** [discussion_r4627721772](https://github.com/PSMRI/STOP-TB-App/pull/377#discussion_r4627721772)
- **Issue:** If an upload throws after `claimForSync`, the beneficiary stays `SYNCING`. A repeated targeted `processNewBen(beneficiaryId)` call skips the reset, so the retry sends nothing.
- **Status:** Dormant. The only caller, `updateBenMissingDataStatus`, is commented out for release-2.4 (PR #376). Fix this when the Nikshay missing-data feature is re-enabled.
