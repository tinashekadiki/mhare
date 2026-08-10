# ADR-0001: Single-institution eMhare

Author: Tinashe K

## Status

Accepted

## Context

The legacy eMhare app served one institutional context. The new eMhare does not need multi-tenancy. Adding tenant isolation would add repeated `institution_id` fields, tenant switching, tenant-scoped security rules, and cross-tenant test burden without solving a current product need.

eMhare still needs an institution profile for branding, document headers, contact details, timezone, country, and default currency.

## Decision

The new eMhare shall be single-institution.

The system shall use one `institution_profile` record for institution-level configuration. Business records shall not include `institution_id` as a repeated ownership column. Academic ownership shall be represented through academic units where needed.

## Consequences

- Database tables are simpler.
- Access control focuses on system roles and academic-unit scope, not tenant scope.
- There is no tenant switcher in the UI.
- Future multi-institution support would require a deliberate redesign ADR.

## Implementation Notes

- Use `institution_profile`, not `institutions`.
- Do not add `institution_id` to business tables unless this ADR is superseded.
- Keep academic ownership through `academic_unit_id` where needed.
