---
name: cfu-tpt-module
description: Required reading before analysing or changing anything in the CFU (Contact Follow-up, FormType.CONTACT_FOLLOW_UP) or TPT (TPT Registration / Start TPT, TPT Follow-up, TPT History, FormType.TPT_FOLLOW_UP) modules. Holds the finalized STOP-602/STOP-620 backward-compatibility rules (V1→V2 locking, Allow edit after submit, requiresBackFill), form-version switching, submission and Examine-sheet behaviour, and where the code lives. Use for any CFU or TPT ticket, and for any change to ContactTracingFormViewModel, CounsellingDynamicAdapter, FormVersionAnswerMapper, DynamicFormSchemaHelper or form-version code.
---

# CFU and TPT Module

Read this whole skill before analysing or changing CFU or TPT code.

Part A is the documented, finalized behaviour. Part B is an additional implementation rule confirmed by the user. If a ticket asks for something that conflicts with Part A or Part B, stop and raise it with the user. Don't silently follow either side.

## Before starting

1. Check the base branch. The backward-compatibility implementation (requiresBackFill, editableAfterSubmit, lockedAnswer, setActiveVersion, VERSION_SCOPED_ID_FORMS) exists on release-2.4. It is not on release-2.5 as of 2026-11-10. A CFU or TPT problem on a 2.5-based branch may be missing code, not a new bug.
2. CFU shares code with the Community and Occupational contact tracing forms. Put CFU-only behaviour in separate functions gated on FormType.CONTACT_FOLLOW_UP, and leave the shared path unchanged.

## Part A: Documented rules

Source: Confluence, "STOP-602 & STOP-620: CFU and TPT Module – Implemented Flow & Backward Compatibility", page 198574096, version 1, updated 2026-11-04 by Ashutosh Gupta. Finalized with Madhava and Shivani.
URL: https://pmp.piramalswasthya.org/confluence/spaces/AMRIT/pages/198574096
If the page has a newer version, re-read it and propose updates to this part.

### A1. Form version management
- getLatestFormVersions is called when the Counselling role becomes active, when the app returns to the foreground, and when the user navigates back to Home.
- All CFU and TPT form versions (V1, V2 and later) are kept on the device.
- The active form version switches to the version the server returns.
- A required version that isn't stored locally is downloaded and stored.
- Existing responses follow the backward-compatibility rules in A4, A5, A6 and A7.

### A2. CFU form behaviour
- The CFU form has 13 fields and counts as V1, as confirmed by Madhava, because there is no CFU data in production.
- Follow-up Date: set to the current date automatically, and unchanged while the form is filled. After the first submission it is fixed for that beneficiary's CFU response and isn't updated on later visits or when reopened.
- Camp Chest X-ray Result and Camp NAAT Result: filled automatically from the beneficiary's latest camp diagnostic results. They are shown only when that investigation was done at camp and a valid result exists.
- When a valid camp result exists, the matching PHI question is hidden ("Is Chest X-ray conducted?" or "Is NAAT conducted?"). When the camp result is missing or invalid, the PHI question is shown.
- Invalid results, confirmed with Madhava and Shivani: Chest X-ray "AI Invalid Result" and NAAT "Invalid/Error" are not valid camp results. The matching PHI question stays visible.
- Conditional fields: Chest X-ray result at PHI shows when Chest X-ray conducted at PHI is Yes. NAAT result at PHI shows when NAAT conducted at PHI is Yes. Cy-TB result shows based on the Cy-TB conducted answer.
- Answers to hidden child questions are cleared when their parent condition no longer holds. This is also checked before submission.
- Clinical Screening Status: Recommend for ATT can be selected only when the applicable Chest X-ray result is TB Presumptive/Abnormal, or the applicable NAAT result is MTB Detected. Otherwise it stays disabled, and an existing ATT answer that is no longer valid is cleared.
- Remarks: optional text field.

### A3. CFU submission and reopening
- The first submission saves the response with SUBMITTED status and syncs it to the backend.
- Clinical Screening Status Recommend for ATT marks the beneficiary as a confirmed TB case.
- Clinical Screening Status TPT Eligible makes Start TPT available in the Examine sheet.

### A4. CFU backward compatibility (V1 → V2)
When a submitted V1 CFU response is opened after the upgrade to V2:
- Existing answers are carried forward by matching question UUID and selected option UUID.
- If the question exists in V2 and its selected option is still in the V2 schema, the answer stays locked (not editable).
- If a question is new in V2, or a saved question or answer can't be mapped to the current schema, the field is editable.
- If an option selected in V1 was removed in V2, the field becomes editable again so the user can choose a valid V2 option.
- Saved answers are not cleared just because a question or option is missing from V2.
- "Allow edit after submit" applies to fields 4, 5, 6, 8 and 10, as stated in Shivani's ticket comment: Is beneficiary referred to PHI?, Is Chest X-ray conducted?, Has beneficiary visited the PHI?, Is NAAT conducted?, Is Cy-TB Test conducted? These stay editable after submission, regardless of the normal locking.
- Submit stays available while any field still needs to be updated.

### A5. TPT Registration (Start TPT)
- A one-time form, available only after CFU is submitted with Clinical Screening Status TPT Eligible (RECOMMEND_FOR_TPT).
- Registration Date: the current date, filled automatically and read-only.
- Regimen Advised: radio selection.
- TPT Start Date: can't be earlier than the beneficiary's TB Screening Date.
- Expected Completion Date: calculated from TPT Start Date and Regimen, read-only, and recalculated whenever either changes.
- Submission saves with SUBMITTED status and syncs. Afterwards, TPT Follow-up becomes available in the Examine sheet.
- Reopening a submitted registration: compatible saved answers stay locked. If an option selected in V1 isn't in V2, that field becomes editable so a valid V2 option can be chosen. Expected Completion Date is recalculated when a relevant editable value changes.

### A6. TPT Follow-up visits
- Fill opens a new visit. Each visit is saved separately.
- An incomplete visit is saved as a draft and reopens where the user left off.
- Visit Date can't be earlier than the TB Screening Date.
- Outcome Status is captured. Outcome Date, Cause of Death and Outcome Remarks show depending on the selected outcome.
- Remarks is an additional field.
- Any Discomfort (Yes/No) controls whether Side Effects shows. Side Effects is a searchable multi-select.
- Submission saves the visit with COMPLETE status and syncs it.
- Completed visits count toward the regimen's required number (for example, 6LFX needs 6 visits). When that number is reached, Fill is hidden and only History remains.
- Backward compatibility (V1 → V2), confirmed with Madhava: a submitted visit must never become editable again, including after a version upgrade.
    - Submitted V1 visits are completely locked under V2. All fields are read-only, including Remarks.
    - There is no Submit or resubmit for existing visits.
    - A V1 answer or option that doesn't exist in V2 is shown empty in the V2 layout and stays non-editable. Submitted visits can't be changed or backfilled.
    - Original V1 answers are kept locally and are never deleted or overwritten because they are missing from V2.
- Visits created after the upgrade use the active form version (V2).

### A7. TPT Follow-up History
- History downloads the beneficiary's completed visits from the backend and shows them in order as "Follow-up n of N", with Next and Back navigation.
- History is completely read-only. No field can be edited, including Remarks, and visits can't be resubmitted or backfilled.
- V1 visits are shown in the V2 layout where questions and answers are compatible. Fields or options missing in V2 show empty. The original V1 data is kept.

### A8. Examine sheet and progress
- Contact Follow-up: always available for household contacts. Fill until submitted, then View.
- Start TPT: only after CFU is submitted with TPT Eligible. Fill until registered, then View.
- TPT Follow-up: after the TPT registration is submitted. Fill until the regimen's visit count is reached. History is always available for submitted visits.
- The confirmed TB patient is excluded from the TPT household contact list.
- Progress counts 3 items (Anthropometry, TB Screening, Contact Follow-up). For TPT-eligible beneficiaries it counts 5 (adding TPT Registration and TPT Follow-up).

### A9. Final summary (from the document)

| Module | Existing V1 response after the V2 upgrade |
|---|---|
| CFU | Compatible answers locked, except fields with Allow edit after submit. New or incompatible fields, and answers whose option was removed, are editable. |
| TPT Registration | Compatible answers locked. Answers whose option was removed in V2 are editable. |
| TPT Follow-up History | All submitted visits fully read-only. Compatible answers shown; missing fields or options empty. Original V1 data kept. |
| New TPT Follow-up visits | Use the active form version (V2 after the upgrade). |

## Part B: Additional implementation rule (user-confirmed, not in the Confluence page)

Confirmed by the user on 2026-11-10:

"requiresBackFill must not be used as the sole condition to determine field editability. Editability must be determined by comparing the existing saved response against the current form version, including question UUID and available option UUIDs. requiresBackFill is supporting context only, not the final editability decision."

## Part C: Where the code lives (release-2.4, checked 2026-11-10)

Package root: app/src/main/java/org/piramalswasthya/stoptb/

| Concern | Location |
|---|---|
| Form types | ui/counselling_activity/FormType.kt (CONTACT_FOLLOW_UP, TPT_FOLLOW_UP) |
| CFU/TPT screen logic, locking, history mode | ui/contact_tracing/ContactTracingFormViewModel.kt: VERSION_COMPATIBLE_FORMS, lockedQuestionUuids = compatibleAnsweredUuids(), and lockedAnswer = questionUuid in locked && !editableAfterSubmit; history mode locks everything |
| V1 → V2 answer mapping and compatibility check | helpers/dynamicMapper/FormVersionAnswerMapper.kt (applyAnswers, compatibleAnswerUuids, removedAnsweredUuids) |
| Field editability in the UI | adapters/dynamicAdapter/CounsellingDynamicAdapter.kt, fieldEditable() |
| Schema storage, version-scoped IDs | helpers/dynamicMapper/DynamicFormSchemaHelper.kt (VERSION_SCOPED_ID_FORMS, requiresBackFill and editableAfterSubmit mapping) |
| Version switching | repositories/dynamicRepo/CounsellingRepositoryImpl.kt (refreshFormsIfOutdated, downloadAndStoreAllForms) and database/room/dao/dynamicSchemaDao/DynamicFormMetadataDao.kt (setActiveVersion) |
| Flags on questions | model/dynamicEntity/SectionQuestionEntity.kt, FormSchemaDto.kt, CounsellingFormSchemaDto.kt; columns added in InAppDb.kt |
| Clinical Screening Status values | ui/contact_tracing/ClinicalScreeningStatus.kt (TPT_ELIGIBLE / RECOMMEND_FOR_TPT) |
| Regimen and follow-up dates | ui/contact_tracing/RegimenAdvised.kt, TptFollowUpDates.kt |

These references may change. Before relying on one, check that it still exists on the current branch.

## When working on a CFU or TPT ticket

1. Read Parts A and B. Name the rules that apply to the ticket.
2. Check the base branch (see "Before starting").
3. Trace the code through Part C before proposing anything.
4. Propose the change and wait for approval, following bug-fix-workflow or implement-feature.
5. In manual test steps, cover V1 → V2 reopening, the Allow-edit-after-submit fields, TPT History being read-only, and regression of Community and Occupational contact tracing.
