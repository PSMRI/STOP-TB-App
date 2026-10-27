---
name: grill-me
description: >-
  Interview the user one question at a time to resolve design decisions or lock
  requirements before implementation. Provides a recommended answer per question
  and explores the codebase instead of asking when possible. Use when the user
  says "grill me", a calling skill invokes grill-me to lock gates, or the user
  wants to stress-test a plan or design.
knowledge:
  - knowledge/workflow.md
  - knowledge/stack.md
rules:
  - .cursor/rules/skills-first.mdc
---

# Grill Me

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — pipeline in [workflow.md](../../../knowledge/workflow.md) §3.
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

Structured interview to freeze gates before code. Called by `plan-feature` / `implement-feature` / `fix-feature` or the user.

## Process

### 1. Receive gates from caller (or user)

Calling skill provides a **gates table**. User-direct → derive gates from topic and confirm the list first.

### 2. Ask one question at a time

For each gate: state question → **recommended answer** → wait for confirm / override / "your call".

### 3. Explore before asking

If the codebase already answers it, state the finding and move on — only ask on real ambiguity.

### 4. Freeze decisions

Answered once → frozen. Conflicts later → blocker, never silent re-grill.

### 5. Exit

```
Grilling complete — N gates resolved:
| Gate | Decision |
|------|----------|
| …    | …        |
```

"Proceed with assumptions" → list unresolved as assumptions (workflow escape hatch).

## Rules

| Rule | Detail |
|------|--------|
| Shared workflow | Obey `knowledge/workflow.md` |
| One question at a time | Never batch 2+ |
| Always recommend | Every question has a suggested answer |
| Explore first | Codebase beats asking |
| Frozen once answered | No re-grilling |
| Caller owns gates | This skill does not invent domain gates |
