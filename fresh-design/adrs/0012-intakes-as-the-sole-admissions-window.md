# ADR-0012: Intakes as the sole admissions window

Author: Tinashe K

Status: Accepted; amended by ADR-0014

## Context

The initial fresh design introduced an Admissions-owned admission cycle in addition to the Academic Setup intake. Both records carried dates, status, programme availability, and applicant-facing selection meaning. This duplicated the same operational decision across services and required administrators and applicants to understand two overlapping calendar concepts.

## Decision

The Academic Setup intake is the only administrator-managed and applicant-visible admissions window.

- Intake start and end dates govern when applications may be created, edited, and submitted.
- Intake status governs whether applications are open or closed.
- Intake programme-level and optional programme targets govern the programmes on offer.
- The intake stores the maximum number of programme choices.
- Applicant, evaluation, selection, quota, and offer APIs identify the admissions period by `intakeId`.
- The admin portal shall not expose admission-cycle setup or lifecycle screens.
- The applicant portal shall present an Intake selector and shall not expose admission-cycle terminology.
- Admissions may temporarily retain a one-to-one internal compatibility projection for existing relational links and historical records. It is not independently managed, must use the Academic Setup intake identifier as its source, and must not reintroduce a second application window.
- Per ADR-0014, each payment-cleared application proceeds continuously while its intake is open. No selection round gates academic review, admission decision, or offer creation; the intake remains the sole admissions window.

This decision supersedes the separate admission-cycle requirements in FR-ADM-001 through FR-ADM-007 and every fresh-model relationship that scopes admissions work by `admission_cycle_id`. Those relationships are replaced by intake scope. ADR-0014 subsequently removed the selection-round and offer-batch gates while retaining intake ownership of the application window. The legacy extraction remains historical evidence only.

## Consequences

- Administrators configure the application window, programme coverage, and maximum choices once on the intake.
- Applicants choose an application type and intake without seeing a duplicate cycle.
- Academic Setup remains the source of truth for intake dates, status, and programme availability.
- Admissions validates intake state through the Academic Setup API and preserves intake snapshots needed for historical readability.
- Existing admission-cycle data is preserved through a compatibility projection while public contracts and new relationships move to intake terminology.
