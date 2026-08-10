# eMhare AI Coding Assistant Instructions

Author: Tinashe K

## Project Context

eMhare is being rebuilt fresh from the legacy `emhare_2_9` CakePHP application. The legacy checkout is a requirements baseline only; do not continue the new system in CakePHP.

Before implementing code, read these files:

- `fresh-design/adrs/README.md`
- `fresh-design/new-emhare-functional-requirements.md`
- `fresh-design/core-and-admissions-data-model.md`
- `fresh-design/legacy-requirements-and-data-model.md`

The legacy checkout at `sparse-emhare/` is read-only reference material unless explicitly instructed otherwise.

## Binding Architecture Decisions

- eMhare is single-institution. Do not add multi-tenancy or repeated `institution_id` ownership columns.
- The system is split into services, not a modular monolith.
- Preserve original legacy-baseline requirements unless an accepted ADR explicitly supersedes them.
- Use `Module` terminology, not `Course`, except in migration mappings and legacy extraction notes.
- Applicants must sign up or log in before applying.
- Application fees are required where configured; fee-required applications cannot enter review, evaluation, or selection without confirmed payment or authorised waiver.
- Admissions rules use relational tables plus small versioned `jsonb` rule expressions for advanced local cases.
- Official outputs must be generated and stored as documents, not browser print pages.
- Object storage must use the S3-compatible API. RustFS is the default self-hosted reference implementation; Garage is an approved alternative.

## Approved Technology Stack

- Backend: Java, Spring Boot 4.x, Maven.
- API: REST with OpenAPI documentation.
- Gateway: Spring Cloud Gateway.
- Auth: Keycloak with OpenID Connect and OAuth 2.0.
- Authorization: Spring Security OAuth2 Resource Server plus local RBAC and workflow permissions.
- Database: PostgreSQL 18.
- Persistence: Spring Data JPA, Hibernate ORM, Hibernate Envers.
- Migrations: Flyway Community with service-owned migration history.
- Messaging: RabbitMQ with transactional outbox/inbox tables.
- Cache and rate limits: Valkey.
- Frontend: TypeScript, Vue 3, Nuxt 4, Nuxt UI, Tailwind CSS, Pinia where needed.
- Reports and PDFs: JasperReports Library and Apache PDFBox.
- Testing: JUnit 5, Spring Boot Test, Testcontainers, Playwright, and contract tests.
- Observability: OpenTelemetry, Prometheus-compatible metrics, Grafana dashboards, structured JSON logs.
- Packaging: OCI containers. Use Docker Compose or Podman Compose for local development.

## Service Split

Expected services are:

- Core and Identity
- Academic Setup
- Admissions
- Finance
- Student Records and Registration
- Assessment and Results
- Exams and Timetabling
- Accommodation
- Dining
- Documents and Reporting
- Notifications

Keep each service's database private to that service. Cross-service access must use APIs, events, or reporting projections.

## Database Rules

- Always create migrations for database changes.
- Never edit an existing migration. Create a new migration to fix or evolve schema.
- Run `mvn flyway:info` before migration work once a service exists and database configuration is available.
- Every business table must include:
  - `id`
  - `created_at`
  - `updated_at`
  - `created_by_user_id`
  - `modified_by_user_id`
  - `deleted_at`
  - `deleted_by_user_id`
  - `version`
- Every business entity must have a Hibernate Envers audit table named `<table_name>_aud`.
- Use `version` for optimistic locking.
- Use soft delete for business records unless an ADR or explicit requirement says otherwise.
- Prefer UUID identifiers for new business entities.
- Store advanced admissions rules in PostgreSQL `jsonb` only where relational requirement tables are not expressive enough.

## Finance And Currency Rules

- Transaction base currency is USD.
- ZWG transactions must use the available effective exchange rate.
- If no effective rate exists, do not hardcode `1`; leave the transaction unrated until a valid rate is captured.
- Finance remains the source of truth for payment details, receipts, exchange rates, and posting.

## Frontend Rules

- Prefer UZ colours for UI components.
- Use common/shared components rather than app-specific duplicates.
- Use SweetAlert modals instead of JavaScript `alert` or `confirm`.
- Do not import `useRuntimeConfig`.
- Use `Module` in user-facing labels, not `Course`.
- Build operational screens first, not marketing pages.
- Keep forms dense, clear, and suitable for repeated administrative use.

## Documentation Rules

- Only create new Markdown files when they materially help implementation or decision-making.
- New architecture or technology changes require an ADR before code changes.
- Put `Author: Tinashe K` in new project documents.
- Prefer updating existing design documents over creating duplicate documents.

## Coding Workflow

- Do not commit unless explicitly asked.
- Preserve unrelated local changes.
- Keep changes narrow and aligned to the existing ADRs.
- Use descriptive names for classes, packages, modules, migrations, tests, and database objects.
- Add tests proportional to risk and service boundary impact.
- For service integrations, include contract tests or explicit API/event schema validation.
- Before claiming completion, run the narrowest meaningful verification available and report exactly what was and was not verified.
