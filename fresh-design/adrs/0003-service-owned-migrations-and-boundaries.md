# ADR-0003: Service-owned migrations and data boundaries

Author: Tinashe K

## Status

Accepted

## Context

The new eMhare will be split into services. Shared database ownership would undo the benefit of the split and recreate monolith-style coupling.

The project rule is to use migrations for database changes and never edit old migrations.

## Decision

Each service shall own its database schema and Flyway migrations.

No service shall directly write another service's tables. Cross-service operations shall use APIs or events.

## Consequences

- Service boundaries are enforceable at the data layer.
- Migration ownership is clear.
- Cross-service workflows need explicit contracts.
- Reporting may need read models or service APIs instead of direct joins across service schemas.

## Implementation Notes

- All DB changes must be introduced through new migrations.
- Do not edit existing migrations after they are created.
- Name migrations by service and sequence.
- Cross-service reads should prefer APIs or replicated read models.
- Shared reference concepts should either live in Core/Identity or be explicitly copied as immutable snapshots.
