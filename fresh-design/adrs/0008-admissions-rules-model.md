# ADR-0008: Admissions rules model

Author: Tinashe K

## Status

Accepted

## Context

Admissions requirements need clear relational data for normal cases: points, subject requirements, minimum grades, required groups, English requirements, and programme-specific rules.

Some local requirements are more advanced, such as exceptional subject combinations, HEXCO, RPL, mature entry, foreign equivalence, and other non-standard local cases. These should not force a huge custom rules schema before the first release.

## Decision

Admissions shall use relational requirement tables for normal rules and a small versioned expression/rules JSON for advanced local cases.

## Consequences

- Normal rules stay queryable and reportable.
- Advanced local rules can be represented without schema churn.
- The rules JSON must be versioned and audited with the requirement set.
- Evaluation output must record which relational and JSON rules were applied.

## Implementation Notes

- Use `admission_requirement_sets` for versioned requirement sets.
- Use `admission_subject_requirements` for normal subject and points rules.
- Use `advanced_rules_json` only for exceptional rules that do not fit relational fields cleanly.
- Store `advanced_rules_version`.
- Include rule results in `application_evaluations.rule_results_json`.
