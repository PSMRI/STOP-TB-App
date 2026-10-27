# Factory LLM (as-built)

In-app LLM control plane for Project Factory. **Why:** [ADR-0020](../docs/adr/0020-factory-llm-langgraph.md), [ADR-0028](../docs/adr/0028-discovery-langgraph-war-room.md).  
Cursor skill: [factory-llm](../.cursor/skills/factory-llm/SKILL.md).

**Not** the same as Cursor skills-first (`.cursor/skills/` = how we develop this repo).

## Layers

| Layer | Path |
|-------|------|
| Runtime | `apps/api/src/api/llm/runtime/` |
| Graphs | `apps/api/src/api/llm/graphs/` |
| Skills / tools | `apps/api/src/api/llm/skills/` |
| Knowledge (prompts) | `apps/api/src/api/llm/knowledge/` |
| HTTP | `apps/api/src/api/routes/` (thin; invoke graphs only) |

## Stack

- **LangGraph** — Discovery / War Room control flow (checkpoints + `interrupt` for human gates)
- **Checkpointer** — Postgres `AsyncPostgresSaver` in production (`LANGGRAPH_CHECKPOINT=auto|postgres`); `MemorySaver` for tests / `memory` ([ADR-0029](../docs/adr/0029-durable-langgraph-checkpoints.md))
- **LangChain agents** — each War Room specialist/lead node runs `create_agent` with local tools + `response_format=AgentOutputEnvelope` ([ADR-0031](../docs/adr/0031-war-room-langchain-agents.md), envelope [ADR-0030](../docs/adr/0030-structured-agent-envelope.md)); idea refine stays a simple chat graph. Per-node tool-loop limits: specialists 40, Leads 60; on limit/failure the graph interrupts so **Re-run stage** retries that agent (not a full phase restart).
- **Pydantic** — graph I/O models / SSE event schemas

## Rules

- Do **not** call OpenAI/Anthropic SDKs from routes or pages.
- New LLM behavior = new/extended **graph** + KB markdown + tests with **mock** LLM.
- CI / local flow review: never require a live API key (`LLM_MOCK=true`, `REFINE_IDEA_MOCK=true`, or missing key → mock). **Mock is LLM-layer only** (`WarRoomMockChatModel` via `get_chat_model`); LangGraph gates, SSE, tools callbacks, and UI stay on the live path. Packs: `runtime/mock_packs.py` + `knowledge/mock_brd_body.md`. Dry-run harness (auto-continues gates): `make discovery-dry-run`.
- Default chat models ([ADR-0035](../docs/adr/0035-dual-llm-models.md)): `OPENAI_MODEL` for Lead + idea/artifact refine; `OPENAI_MODEL_SPECIALIST` for War Room specialists (falls back to `OPENAI_MODEL`). Deprecated alias: `OPENAI_MODEL_DISCOVERY` → specialist when specialist unset. War Room `agent:start` / `agent:tool` SSE payloads and communications rows include the resolved `model` id for UI visibility. While an agent writes, token callbacks emit `agent:delta` `{delta, draft}` (markdown extracted from incremental envelope JSON) — not persisted to communications. War Room chat models use `streaming=True`; `WarRoomMockChatModel` chunks the envelope via `_stream` / `_generate`.
- Ideation enhance UI remains [factory-ui](../.cursor/skills/factory-ui/SKILL.md); refine HTTP stays `POST /api/v1/ideas/refine`. Artifact **Edit with AI** uses `POST /api/v1/discovery/refine-artifact` (`artifact_refine` graph) for Discovery, Architecture, and Design agent IDs (specialists + Lead). Scoped edits may send `scope` (`all`|`section`|`selection`), `full_content`, and `section_heading` / `selection_text` with the target fragment in `content`; the graph returns only the revised fragment when scoped.

## Graphs

- `idea_refine` — Ideation enhance
- `idea_suggest_domain` — Ideation Domain auto-suggest (`POST /api/v1/ideas/suggest-domain`)
- `discovery_qa` — seeds hybrid static Discovery MCQ bank (ADR-0024) and prefills grounded answers from Ideation + document extracts via Lead model ([ADR-0036](../docs/adr/0036-discovery-qa-prefill.md)); UI loads questions from `POST /api/v1/projects/{id}/discovery-questions` only (no FE bank overlay); response is `questions` + partial `answers`
- **`discovery_war_room`** — LangGraph War Room ([ADR-0028](../docs/adr/0028-discovery-langgraph-war-room.md)): static → specialists → **Lead review** (`step: lead_review`) and optional specialist **lead_feedback** revise when gaps are Lead-fixable ([ADR-0055](../docs/adr/0055-lead-review-before-clarification.md)) → when checklist still fails, Lead AC fill (`step: ac_fill`) + Lead revalidates → clarification interrupt when user-facing Ambiguities / Open Decisions remain (deduped vs Phase Q&A + prior clarifications) → review interrupts → on Continue, Lead absorbs into `discovery_brd_draft` without a second LLM gap fill ([ADR-0053](../docs/adr/0053-lead-fill-specialist-ac-before-absorb.md)) → next specialist → last polish into packaged BRD. `review_action: rerun_stage` cancels a pending clarification bank and re-runs that specialist.
- **`solution_qa`** — seeds Solution MCQ bank + prefills from **Discovery Q&A + BRD** ([ADR-0037](../docs/adr/0037-solution-phase.md)); `auth_approach` is fully public (match Discovery) **or** Keycloak styles; `data_store` may be ephemeral when Discovery is stateless; UI loads questions from `POST /api/v1/projects/{id}/solution-questions` only (no FE bank overlay). API autofixes Discovery↔Solution contradictions on seed / agent start ([API ADR-0054](../../the-project-factory-api/docs/adr/0054-cross-phase-qa-consistency.md)).
- **`solution_war_room`** — LangGraph Solution War Room ([ADR-0037](../docs/adr/0037-solution-phase.md)): static → sequential (Open Decisions clarification) + parallel stage 2 (skips clarification) → reviews → `solutionLead` HLD/LLD → pragmatic AC gate; façade `orchestration/solution.py` (`phase_id: "solution"`)

## Phase orchestration

Registry: `api/llm/registry/phase_registry.py` + `agents.py` + `discovery_questions.py` + `solution_questions.py`.  
Engine: LangGraph graph + thin SSE façade under `api/llm/orchestration/` (discovery + solution).  
Per-agent skill+KB packs under `skills/` and `knowledge/`. Mock when no key. Static gate validates MCQ selections (`__default__` = Let AI decide). Review actions: `continue` | `send_fix` | `rerun_stage`. Solution also supports `ac_gate` (`accept_gaps` | `revise`).  
Approve Discovery: blockers on missing/low-quality BRD; optional `approve_source=upload` → `phase=solution` ([ADR-0032](../docs/adr/0032-discovery-polish.md)). Approve Solution: HLD/LLD + AC gaps → `phase=design`. Tool steps emit `agent:tool` for the war-room feed; token writes emit `agent:delta`. Live smoke: `make test-api-live` (needs `OPENAI_API_KEY`).  
Human gate actions also append durable rows to **`project_events`** (`GET /api/v1/projects/{id}/events`) ([ADR-0033](../docs/adr/0033-project-events-table.md)).

## Discovery BRD format

Canonical **13-section** enterprise BRD ([ADR-0027](../docs/adr/0027-standard-brd-format.md)): completeness is a **runtime post-package gate** in the API (`brd_runtime_gaps`, up to two auto-revises, then deterministic fold of settled Q&A/clarifications into Assumptions, approve blockers for remaining structural gaps). UI BRD Review shows `discovery_checklist` + `discovery_completeness_gaps`; Q&A picks persist before Start ([ADR-0046](../docs/adr/0046-persist-discovery-qa.md)). After Lead synthesis, API `package_discovery_brd` applies client packaging. No Screen Inventory in Discovery.

## Solution HLD / LLD format

Canonical HLD + LLD ([ADR-0037](../docs/adr/0037-solution-phase.md)): KBs `standard-hld-format.md` / `standard-lld-format.md`, packager `solution_formatter.py`, mocks `mock_hld_body.md` / `mock_lld_body.md`. HLD owns **Screen Inventory** as `| Screen | Role | Purpose | Primary FRs |` — **Role** from BRD Stakeholders/personas (`All users` for shared auth); Design Studio segregates by `allowed_roles`. Lead **absorbs** Architect Screen Candidates or lists drops in Open Decisions. **Pragmatic AC** machine-checks table shape / Role / Primary FRs / section substance (and optional BRD FR coverage); users may still **Accept gaps** — not Discovery hard-approve. Settled phase Q&A → Assumptions & Constraints; Open Decisions/gaps are true leftovers only (do not re-ask answered axes). Design maps 1:1 later. Artifacts: `solution_hld`, `solution_lld`, `solution_ac`.
Solution agents: per-role `skills/<id>.md` list required `##` headings; `solutionLead` emits `<<<HLD>>>` / `<<<LLD>>>` / `<<<AC>>>` markers for packaging.
Architecture diagrams in Solution HLD use **two** Mermaid sections ([ADR-0038](../docs/adr/0038-mermaid-architecture-diagrams.md)):

1. **`## System Context`** — C4 Level 1: actors, product boundary, externals (5–15 nodes; no Client/Edge/Application/Data layers).
2. **`## Architecture Diagram`** — container detail: required Client / Edge / Application / Data subgraphs (`flowchart TB`, short labels, critical-path edges; no node-count cap).

When Discovery auth ≠ fully public, **Keycloak** appears in both (external in System Context; OIDC/JWKS in Architecture Diagram). AC includes layered-subgraph, Keycloak, and System Context C4 rows. Factory UI renders both via `MarkdownContent`; PDF download frames both at 4× density; Architecture Diagram also gets a landscape HD appendix.
