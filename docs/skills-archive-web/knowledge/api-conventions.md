# API conventions (as-built)

How the API is structured today. **Musts (auto):** `.cursor/rules/api-conventions.mdc`. **Why:** [ADR-0004](../docs/adr/0004-items-golden-path.md). Factory projects: [ADR-0017](../docs/adr/0017-factory-projects-resource.md).

## Layering

```
routes → schemas → models
```

- Routes: HTTP, Depends(`get_db`), status codes, `HTTPException`
- Schemas: Pydantic request/response only
- Models: SQLAlchemy mapped columns only
- Do **not** put business logic in `main.py`

## Routing

- All resource routers live under `apps/api/src/api/routes/`
- Include them on `api_router` (`prefix="/api/v1"`) in `routes/__init__.py`
- Resource prefix: plural noun, e.g. `/items`
- Endpoints: `GET ""`, `GET /{id}`, `POST ""`, `PATCH /{id}`, `DELETE /{id}`

## Naming

| Kind | Pattern | Example |
|------|---------|---------|
| Model | PascalCase singular | `Item` |
| Table | snake plural | `items` |
| Schema | `{Model}Create` / `Update` / `Read` | `ItemCreate` |
| Router module | plural | `items.py` |

**Also:** `Project` / `projects` / `/api/v1/projects` for Factory ideation (ADR-0017).
Nested Discovery uploads: `ProjectDocument` / `project_documents` / `/api/v1/projects/{id}/documents` (disk originals + extracted text; [ADR-0025](../docs/adr/0025-discovery-document-storage.md)).  
Durable gate/feedback log: `ProjectEvent` / `project_events` / `GET /api/v1/projects/{id}/events` ([ADR-0033](../docs/adr/0033-project-events-table.md)).

## Errors

- 404: `HTTPException(status_code=404, detail="<Resource> not found")`
- Validation: rely on Pydantic (422)
- Success delete: `204 No Content`

## New resource checklist

1. `models/<name>.py` + export in `models/__init__.py`
2. `schemas/<name>.py`
3. `routes/<name>.py` + include in `routes/__init__.py`
4. Alembic revision (`make migrate-rev` / `add-migration` skill)
5. `make migrate`
