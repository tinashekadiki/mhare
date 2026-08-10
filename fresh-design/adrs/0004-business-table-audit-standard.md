# ADR-0004: Business table audit and Envers standard

Author: Tinashe K

## Status

Accepted

## Context

The legacy system had many audit trail tables spread across domains. The new eMhare needs a consistent audit model across services, especially for admissions, finance, student records, exams, results, accommodation, documents, and workflow decisions.

The system will use Spring Boot and Hibernate, so Hibernate Envers is the standard audit mechanism.

## Decision

Every business table shall include:

- `id`
- `created_at`
- `updated_at`
- `created_by_user_id`
- `modified_by_user_id`
- `deleted_at`
- `deleted_by_user_id`
- `version`

Every business entity shall be audited with Hibernate Envers and shall have a matching `<table_name>_aud` table.

## Consequences

- Every business row records who created, modified, and soft-deleted it.
- `version` supports optimistic locking.
- Envers gives consistent historical record inspection.
- Migration templates must include audit columns and audit table support from the start.

## Implementation Notes

- `created_by_user_id` records the creating user.
- `modified_by_user_id` records the last modifying user.
- `deleted_by_user_id` records the user who soft-deleted the row.
- `deleted_at` and `deleted_by_user_id` are both null for active rows.
- `deleted_at` and `deleted_by_user_id` are both populated for soft-deleted rows.
- The Envers revision table must capture revision ID, timestamp, actor user ID, service name, request/correlation ID, and optional reason/comment where available.
- Technical lookup exclusions require explicit architecture approval. Operational business tables are not excluded.
