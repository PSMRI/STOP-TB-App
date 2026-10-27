# Find Skills — reference

## Skills CLI

- `npx skills find [query] [--owner <owner>]` — search by keyword
- `npx skills add <package>` — install from GitHub / ecosystem
- `npx skills update` — update installed skills

Browse: https://skills.sh/

## Example searches

| User ask | Query |
|----------|--------|
| Make React faster | `npx skills find react performance` |
| PR reviews | `npx skills find pr review` |
| Changelog | `npx skills find changelog` |

## Presenting a candidate

Include skill name, purpose, install count/source, install command, and
`https://skills.sh/<owner>/<repo>/<skill>` when available.

## Quality checks

1. Install count — prefer 1K+; be cautious under 100
2. Source reputation — prefer `vercel-labs`, `anthropics`, `microsoft`, etc.
3. Repo stars — treat &lt;100 stars skeptically

## Common categories

| Category | Example queries |
|----------|-----------------|
| Web | react, nextjs, typescript, css |
| Testing | testing, playwright, e2e |
| DevOps | deploy, docker, kubernetes |
| Docs | readme, changelog, api-docs |
| Quality | review, lint, refactor |
| Design | ui, ux, design-system |
| Workflow | automation, git |

## After install in this repo

1. Ensure files live under `.cursor/skills/<slug>/`
2. List the skill in `.cursor/skills/README.md`
3. Add to `EXPECTED_CATEGORIES` in `scripts/validate_skills.py` if it is a core workflow/stack skill
4. Run `make validate-skills`
