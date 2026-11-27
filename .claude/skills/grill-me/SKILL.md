---
name: grill-me
description: Interview the user one question at a time, each with a recommended answer, to lock requirements or stress-test a plan before coding. Use when the user says "grill me", or when plan-feature / fix-feature has open decisions.
---

# Grill Me

## Process

1. **Gather the gates.** Take the open decisions from the calling skill, or derive them from the topic and confirm the list with the user first.
2. **Explore before asking.** If the codebase answers it (existing Dataset, DAO, role config, string, API endpoint), state the finding and move on.
3. **Ask one question at a time.** Each question: the decision, why it matters, your **recommended answer**. Wait for confirm / override / "your call".
4. **Freeze.** An answered gate is final; a later conflict is raised as a blocker, not silently re-asked.
5. **Exit** with:

```
Grilling complete — N decisions:
| Decision | Answer |
|----------|--------|
```

If the user says "proceed with assumptions", list the unresolved items as assumptions and continue.

## Typical STOP-TB gates

Which roles see it · offline behaviour · new DB fields and their defaults for existing rows · server API/field names · validation rules · whether the record syncs immediately or with the next cycle · Hindi/Assamese copy source · UAT-only or prod · which release branch it targets (2.3/2.4 and 2.5 differ in DB and sync code) · whether it touches code shared by CFU and Community/Occupation contact tracing.
