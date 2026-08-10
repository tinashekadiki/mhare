# ADR-0005: Preserve legacy-baseline requirements

Author: Tinashe K

## Status

Accepted

## Context

The legacy eMhare application covered far more than admissions. It included academic setup, student records, registration, finance, assessment, exams, results, accommodation, dining, staff, awards, reporting, ACL, and audit.

The new system should improve the architecture and data model without losing business capability.

## Decision

Enhancements shall preserve the original legacy-baseline requirements unless a requirement is explicitly superseded with a replacement requirement and rationale.

## Consequences

- New design work must not silently drop legacy workflows.
- A legacy feature can be redesigned, renamed, split, or phased, but its business purpose must remain covered.
- If a requirement is no longer needed, it must be explicitly superseded by a later ADR or FRD section.

## Implementation Notes

- Use `legacy-requirements-and-data-model.md` as the baseline inventory.
- Use `new-emhare-functional-requirements.md` as the current product requirement source.
- When adding enhancements, mark them additive or superseding.
- Do not delete or weaken legacy-derived requirements without a superseding ADR.
