# ADR-0014: Rolling per-applicant admissions processing

Author: Tinashe K

## Status

Accepted

## Context

ADR-0008 established admissions rules as relational tables plus versioned `jsonb` for advanced cases, and ADR-0012 made the Academic Setup intake the sole admissions window, already stating that "Selection and offer workflow states remain properties of their own records rather than additional intake lifecycle states." Despite that, the current admissions-service (migrations V1–V33) and admin-portal still implement a batch-oriented pipeline: a `selection_round` must open before any academic-unit release, applicants are ranked and shortlisted/selected/waitlisted against per-programme quotas within that round, and offers are grouped into `offer_batches` scoped to institution/academic-unit/programme before approval and dispatch. This forces every applicant, including one who is fully verified, eligible, and academically recommended, to wait for an administrator to open a round and later assemble and approve a batch before an offer can exist. It also spreads processing across ten separate admin-portal pages (`admissions.vue`, `admissions-verification.vue`, `admissions-evaluation.vue`, `admissions-selection.vue`, `admissions-academic-release.vue`, `admissions-recommendations.vue`, `admissions-decisions.vue`, `admissions-offers.vue`, `admissions-documents.vue`, and the consolidated `admissions/[applicationId].vue` workspace that most of them still redirect staff back to).

## Decision

Admissions processes each submitted, payment-cleared application as an individual, continuous pipeline while its intake is open: **Verification → Eligibility → Academic review → Admission decision → Offer → Response**. There is no administrator-opened round and no offer batch in this pipeline.

- A submitted application enters **Verification** once its required sections, documents, qualifications, and payment (or authorised waiver) are confirmed, recorded as today on `application_clearances`.
- **Eligibility** evaluation of each programme choice against its approved `admission_requirement_sets` runs automatically as soon as verification clears; it does not wait for a manually opened round. A choice that cannot be automatically resolved, because a rule is missing or the route is an alternative-entry case such as HEXCO, RPL, mature entry, or foreign equivalence, is held in `REQUIRES_REVIEW` for a human evaluator.
- Programme choices are still processed one at a time by applicant preference rank, per FR-SEL-031: an open review or an admitted decision on a higher-ranked choice blocks lower choices; an approved rejection on a choice automatically opens the next eligible choice.
- Once a choice is eligible, Admissions automatically creates an **Academic review** assignment for it at the resolved highest academic-unit ancestor, exactly as `academic_review_assignments` did, but the assignment is created immediately rather than released as part of a round batch. Active staff at that unit record an advisory `RECOMMEND_ADMIT` or `RECOMMEND_REJECT` recommendation.
- Admissions makes the **Admission decision** directly against the recommendation, using two actions only: `Approve admission` and `Reject`. There is no shortlist, waitlist, rank position, or quota input to this decision; capacity and quota records, where they still exist for institutional planning, do not gate it.
- An approved admission decision generates one **Offer** directly from the applicant's consolidated profile, with no offer batch. Offer publication and delivery are covered by ADR-0009 (generated documents) and the Documents/Notifications changes in the implementation plans that follow this ADR.
- The applicant's **Response** (accept or decline) is unchanged from the existing offer-response model.
- Selection rounds and offer batches are removed as an active gate on any new academic review, admission decision, or offer. Existing `selection_rounds`, `selection_decisions`, `offer_batches`, `academic_review_assignments`, and `academic_unit_recommendations` records are preserved as read-only history; they are never written to again and remain visible only in audit and case history. New processing uses new tables scoped directly to the application and programme choice: `academic_reviews`, `academic_recommendations`, and `programme_choice_decisions`, described in the updated core-and-admissions data model.
- Intake closure stops new application submissions, per ADR-0012 and FR-ADM-005; it does not stop or gate the processing of applications already submitted before closure. There is no intake-close processing gate.
- The admin portal exposes admissions processing through exactly two screens: a compact `/operations/admissions` table and the single `/operations/admissions/{applicationId}` case workspace already described by FR-SEL-033/034. `admissions-verification.vue`, `admissions-evaluation.vue`, `admissions-selection.vue`, `admissions-academic-release.vue`, `admissions-recommendations.vue`, `admissions-decisions.vue`, `admissions-offers.vue`, and `admissions-documents.vue` are retired as separate pages; their routes redirect to the compact table filtered to the equivalent stage. The per-academic-unit document-completeness view `admissions-documents.vue` previously provided becomes a filter on the compact table rather than a separate page.
- Bulk functionality is retained only as a programme-level, intake-scoped print/export of already-published offer letters. It reads published documents; it never creates, approves, decides, or dispatches an offer, and it introduces no batch lifecycle of its own.

This decision supersedes FR-SEL-020 through FR-SEL-025, FR-SEL-027 through FR-SEL-031, and FR-SEL-034; FR-OFFER-010 through FR-OFFER-012; FR-ADM-021, FR-ADM-022, FR-ADM-002, and FR-ADM-004; and the "Selection rounds" line item in the Release 1 scope recommendation. It amends FR-OFFER-001 and FR-OFFER-033, and adds FR-SEL-035, FR-OFFER-040 through FR-OFFER-047, and FR-NOTIF-004 through FR-NOTIF-008 — all as reflected in the accompanying functional-requirements and data-model changes. It builds on, and does not conflict with, ADR-0012's rule that intake governs the application window; it narrows ADR-0012's remaining reference to "a selection round may start only after application capture for its intake is closed" by removing the selection round it referred to.

## Consequences

- An applicant who clears verification, is automatically found eligible, and receives an academic recommendation can reach an admission decision and an offer without waiting for an administrator to open or close anything, other than the intake itself being open at submission time.
- Admissions staff work from one compact table and one case workspace instead of eight separate processing pages, reducing the surface area for stage-tracking bugs and inconsistent applicant summaries.
- Historical selection-round and offer-batch outcomes remain fully auditable and reportable; no backfill or reinterpretation of that data into the new tables is required or attempted.
- Capacity and quota data, if retained for institutional planning, must not be read by any new eligibility, recommendation, or decision code path; enforcing that becomes a code-review and contract-test concern for the implementation plans rather than a database constraint, since the tables may still exist for reporting.
- Programme-level bulk printing becomes a strictly read-only reporting feature; it can be built, changed, or removed without any risk to the live decision/offer pipeline, and its own audit trail (requesting user, intake, programme, format, document count, timestamp) is independent of offer audit history.
- The eight retired admin-portal pages and their route-specific navigation entries are deleted from primary navigation; only Admissions, Applicant register, and Application types remain as top-level Admissions navigation items, per the implementation plans that follow this ADR.

## Implementation Notes

- New tables (`academic_reviews`, `academic_recommendations`, `programme_choice_decisions`) and updated status enums are specified in the "Eligibility, Scoring, Selection" section of `core-and-admissions-data-model.md`, dated to this ADR.
- Migrations backing this ADR must be created fresh against the current admissions-service baseline (Flyway V33 at the time of this ADR) and must never edit V1–V33; `mvn flyway:info` must be run against a live database before authoring them, per project convention.
- `offers.offer_batch_id` remains in the schema as a nullable, no-longer-populated column referencing historical batches; new offers always leave it null.
