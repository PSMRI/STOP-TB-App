# Skill scenarios

JSON fixtures for `make validate-skills-strict`.

Each file declares what skills a prompt **should** resolve to (catalog contract),
not a live LLM transcript.

## Schema

| Field | Meaning |
|-------|---------|
| `id` | Stable scenario id |
| `prompt` | Example user request |
| `expect_skills` | Skills that must exist and be part of the intended path |
| `forbid_skills` | Skills that must not be required for this prompt |
| `expect_hard_stop` | If true, `hard_stop_skill` body must contain "Hard stop" |
| `hard_stop_skill` | Gate skill (e.g. `api-auth`, `add-test`) |
| `expect_matrix` | Concern → skill (must appear in `expect_skills`; skill must be on `route-task` checklist unless orchestrator-only) |

## Commands

```bash
make validate-skills          # structure only
make validate-skills-strict   # + contracts + scenarios + efficiency
```
