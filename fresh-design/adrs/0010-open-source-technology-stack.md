# ADR-0010: Open-source technology stack

Author: Tinashe K

## Status

Accepted

## Context

The new eMhare is a single-institution, split-service system. It must support long-lived institutional records, strong auditability, applicant self-service, payments, admissions workflow, generated documents, exams, accommodation, dining, finance, and reporting.

The technology stack should favour open-source software, operational maturity, local hosting options, and skills that can be sustained by an institutional engineering team. The legacy CakePHP codebase is a requirements baseline, not the target implementation stack.

## Decision

The new eMhare shall use this default technology stack:

| Layer | Decision |
| --- | --- |
| Backend language | Java |
| Backend framework | Spring Boot 4.x |
| Runtime baseline | Java 21 LTS or newer LTS supported by the chosen Spring Boot release |
| Build tool | Maven |
| API style | REST APIs documented with OpenAPI |
| Internal async integration | RabbitMQ with transactional outbox/inbox tables |
| API gateway | Spring Cloud Gateway |
| Identity provider | Keycloak using OpenID Connect and OAuth 2.0 |
| Service authorization | Spring Security OAuth2 Resource Server with local RBAC and workflow permissions |
| Primary database | PostgreSQL 18 |
| Persistence | Spring Data JPA, Hibernate ORM, Hibernate Envers |
| Database migrations | Flyway Community, with service-owned migration history |
| Advanced admissions rules storage | PostgreSQL `jsonb` columns plus relational rule tables |
| Cache and rate-limit store | Valkey |
| Object/document storage | S3-compatible object storage API; reference implementation is governed by ADR-0011 |
| Official report generation | JasperReports Library for templated reports and Apache PDFBox for PDF post-processing |
| Frontend language | TypeScript |
| Frontend framework | Vue 3 with Nuxt 4 |
| Frontend UI | Nuxt UI, Tailwind CSS, UZ colour theme, Pinia for client state where needed |
| Browser testing | Playwright |
| Backend testing | JUnit 5, Spring Boot Test, Testcontainers, contract tests for cross-service APIs/events |
| Observability | OpenTelemetry instrumentation, Prometheus-compatible metrics, Grafana dashboards, structured JSON logs |
| Packaging | OCI containers |
| Local development | Docker Compose or Podman Compose |
| Production deployment | Container platform first; Kubernetes is optional, not mandatory for Release 1 |

Exact dependency versions shall be selected during implementation using current supported patch releases and locked in parent dependency management.

## Consequences

- The stack is mostly open-source and self-hostable.
- Java and Spring Boot fit the audit, transaction, validation, and service-boundary needs of a records-heavy university system.
- Hibernate Envers directly supports the accepted audit-table decision.
- PostgreSQL remains the source of truth for structured business data and advanced local rules.
- RabbitMQ is the default broker because the expected integration pattern is workflow events and reliable service notification, not high-volume event-stream analytics.
- Kafka is not part of the default Release 1 stack. It can be introduced later by ADR if high-volume event streaming becomes a real requirement.
- The frontend stack is suitable for form-heavy portals, admin workflows, route-level access control, and a shared design system.
- S3-compatible storage keeps documents portable across Garage, MinIO, Ceph, AWS S3, or another compatible implementation.
- AGPL-licensed infrastructure components require license review before modification or redistribution.

## Implementation Notes

- Do not implement new backend services in CakePHP, Laravel, Node.js, or .NET unless a later ADR supersedes this decision.
- Do not use MongoDB or another document database as the primary business-record store.
- Keep service databases private to their owning service. Cross-service access must use APIs, events, or reporting projections.
- Use OpenAPI for public and internal synchronous APIs.
- Use transactional outbox publishing for business events that must not be lost.
- Use PostgreSQL full-text search first. Add OpenSearch or another search engine only if PostgreSQL search no longer satisfies a measured requirement.
- Keep object storage behind an application-level storage interface so the deployment target can change without changing domain code.
- Use generated documents for official records, in line with ADR-0009.

## Alternatives Considered

| Alternative | Reason Not Chosen |
| --- | --- |
| Continue with CakePHP | Useful as a requirements baseline, but not ideal for the new split-service, audit-heavy architecture. |
| Node.js/NestJS backend | Strong productivity, but weaker fit for Hibernate Envers, JPA-style auditability, and long-lived relational business records. |
| .NET backend | Technically viable, but does not improve the open-source/self-hosting story enough to displace Java and Spring Boot for this design. |
| Kafka as default broker | Powerful, but operationally heavier than needed for Release 1 workflow events. |
| MongoDB as primary database | Poor fit for strongly relational academic, finance, admissions, audit, and results records. |
| Next.js frontend | Technically strong, but Nuxt 4 is a cleaner open-source fit for the preferred Vue-based operational UI approach. |

## Reference Checks

Reference checks were made on 2026-08-06 against official or primary project sources:

- Spring Boot current project and system requirements documentation.
- PostgreSQL current documentation and release documentation.
- Keycloak server administration and OpenID Connect documentation.
- Nuxt 4 documentation and roadmap.
- RabbitMQ project documentation.
- Apache Kafka project documentation.
- OpenTelemetry documentation.
- Valkey project documentation.
- Garage S3-compatible object storage documentation.
- JasperReports Library repository and community documentation.
- Apache PDFBox documentation.
