---
name: grill-me
description: >-
  Interviews the user one question at a time, each with a recommended answer, to freeze open
  design or product decisions before code. Explores the codebase first and only asks about
  real ambiguity. Use when plan-feature or fix-feature hands over a gates table, or the user
  says grill me / stress-test this plan.
knowledge:
  - knowledge/workflow.md
  - knowledge/stack.md
---

# Grill Me

## Knowledge

Read [workflow.md](../../../knowledge/workflow.md) §3 and [stack.md](../../../knowledge/stack.md).

## Process

1. Take the gates table from the caller (or derive gates from the topic and confirm the list).
2. For each gate: check the code first; if it answers the question, state the finding and move on.
3. Otherwise ask **one** question with a **recommended answer** and the reason; wait.
4. Freeze each answer. Later conflict → raise as a blocker, never silently re-ask.
5. Exit with:

```
Grilling complete — N gates resolved:
| Gate | Decision |
```

"Proceed with assumptions" → list the unresolved gates as assumptions.

## Rules

| Rule | Detail |
|------|--------|
| One at a time | Never batch questions |
| Always recommend | Every question has a suggested answer |
| Code first | Don't ask what the repo already answers |
