---
name: capture-learnings
description: Approval-first capture of what the user taught in this session (corrections, confirmed decisions, conventions, edge cases), saved to the right persistent place (memory, CLAUDE.md or a skill) only after the user approves the exact wording and target. Use when the user says "wrap up", "capture learnings", "remember this" or "save this rule"; as the last step of bug-fix-workflow, fix-feature, implement-feature and plan-feature; and when reviewing stale memories.
---

# Capture Learnings

A fresh Claude Code session knows only what is written in memory (MEMORY.md and its files), CLAUDE.md and the skills. Anything not written there is gone when the session ends. Nothing in a session changes the model itself.

This skill decides what to write and where. Every write needs the user's approval.

## Approval policy

- Never create, update or delete a memory file, a MEMORY.md line, CLAUDE.md text or a skill file without approval. This includes small preferences. It overrides the built-in auto-memory behaviour.
- "Remember this" from the user authorizes that one item. Still show the exact wording and target before writing.
- Approval for one item doesn't cover others.

## 1. Collect candidates (this session only)

Look for:
- Corrections: "don't do X", "why did you…", rejected edits or approaches.
- Confirmed decisions: the user's own, or ones they report from the manager, BSA, backend developers or QA.
- Conventions: git, code style, debugging approach, review habits.
- Edge cases and project rules found while working on tickets.
- Changes to a rule that is already stored.

Do not collect:
- My own hypotheses, or options that were discussed but not chosen.
- Anything still being investigated.
- Facts the code or git history already records.
- One-off details of the current task.
- Secrets, tokens or patient data.

## 2. Classify each candidate

| Class | Meaning | Default target |
|---|---|---|
| Team rule | Applies to everyone on the project | The skill that owns the topic, or a short line in CLAUDE.md |
| Personal preference | How this user wants me to work | Memory, type feedback |
| Ticket or module decision | Specific to one STOP ticket or one module | The module's skill if one exists, otherwise memory type project with the ticket ID |
| Tentative | Not explicitly confirmed by the user | Not saved. Listed as an open question. |

Evidence: every candidate needs the user's own words with the date, or "confirmed by the user on YYYY-MM-DD". Without that, it is tentative.

If a correction was phrased as "do it as X instead", it may apply only to that task. Ask whether it is a general rule before proposing it as one.

## 3. Check before proposing

1. Search MEMORY.md, the memory files, CLAUDE.md and .claude/skills/ for the same topic.
2. If an entry already exists, propose updating it instead of adding a new one.
3. Keep each rule in exactly one place. Other files point to it rather than repeat it.
4. If a candidate conflicts with a stored rule, don't choose between them. Show both and ask the user.
5. If a candidate names a file, function or branch, check that it exists on the current branch.

## 4. Propose and wait

Show one table:

| # | Learning (one line) | Evidence (quote and date) | Class | Scope | Target file | Action |

Action is one of: new, update, move to History, delete.

Under the table, give the exact text or diff for each row. Then wait for the user's choice ("all", "1 and 3", "skip 2", or edits to the wording). Write nothing until then.

## 5. Write the approved items

- Memory: one fact per file in the memory directory. Frontmatter has name, description and metadata.type. The body is the rule, then "Why:" with the quote and an absolute date, then "How to apply:" with the scope (modules, branches, tickets), then [[related]] links. Add or update the one-line pointer in MEMORY.md.
- CLAUDE.md and skills: use the Edit tool, one file at a time. Keep the rule short. If another file owns the rule, point to that file instead.
- Convert relative dates ("today", "yesterday") to absolute dates.
- Afterwards, list what was written and where.

## 6. Memory review

Run this when the user asks, or before relying on a project memory that is more than 30 days old.

For each project memory:
1. Check what it references (status, branch, files, functions) against the current code.
2. Propose one action: keep, update status, move outdated parts under a "History" heading, or delete.
3. Never delete useful history just because the implementation status changed.
4. Follow the approval policy for every change.

## Precedence and conflicts

When stored sources disagree, the order is: the user's latest explicit instruction, then memory feedback, then CLAUDE.md, then skill text. Never resolve a conflict silently. Raise it, and after the user decides, propose a fix to the out-of-date source.

## Rules

- Never commit or push. The user commits CLAUDE.md and .claude/skills/ themselves.
- Never store secrets, tokens or patient data.
