# ADR-0006: Module terminology and configurable academic structure

Author: Tinashe K

## Status

Accepted

## Context

The legacy app used `Course` terminology and hardcoded Faculty/Department-style structures. The new eMhare should use local academic wording and support a configurable academic tree.

The preferred wording is `Module`, not `Course`.

## Decision

The new eMhare shall use `Module` terminology in new UI, API, documentation, and data model surfaces.

Academic organisation shall use configurable academic unit types and academic units rather than hardcoded Faculty and Department tables.

## Consequences

- New tables use names such as `modules`, `curriculum_modules`, and `registration_modules`.
- Legacy `Course` wording only appears in migration mappings, compatibility fields, and source extraction notes.
- Faculties and departments can still be configured as academic unit types, but they are not universal product assumptions.

## Implementation Notes

- Preserve legacy course codes in `legacy_course_code`.
- Preserve legacy faculty and department codes in academic-unit migration fields.
- Programmes and modules should attach to leaf academic units.
- UI labels should say Module unless a specific institution setting explicitly overrides it.
