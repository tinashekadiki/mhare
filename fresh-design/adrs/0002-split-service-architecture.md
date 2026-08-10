# ADR-0002: Split-service architecture

Author: Tinashe K

## Status

Accepted

## Context

The legacy application was a broad monolith covering admissions, academic setup, student records, registration, finance, assessment, exams, results, accommodation, dining, staff, awards, reporting, ACL, and audit. The new eMhare needs clean ownership boundaries before implementation starts.

The system should not be rebuilt as one modular monolith. Services must own their own data, migrations, business rules, and integration contracts.

## Decision

The new eMhare shall be split into services.

The service split is:

- Core / Identity
- Academic Setup
- Admissions
- Finance
- Student Records / Registration
- Assessment / Results
- Exams / Timetabling
- Accommodation
- Dining
- Documents / Reporting
- Notifications

## Consequences

- Each service has a clear boundary and release path.
- Cross-service calls must be explicit APIs or events.
- No service may directly write another service's tables.
- Release 1 can implement admissions depth while still reserving proper service boundaries for later phases.

## Implementation Notes

- Admissions depends on Core/Identity, Academic Setup, Finance, Documents/Reporting, Notifications, and a minimal Student Records conversion endpoint.
- Exams/Timetabling and Accommodation are explicit service boundaries, not screens inside another service.
- Use idempotent commands where retries are expected, including payment confirmation, offer response, and applicant-to-student conversion.
