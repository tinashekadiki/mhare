# eMhare Core and Admissions Data Model

Author: Tinashe K

## Purpose

This is a fresh data model for eMhare using the legacy `emhare_2_9` repository as a baseline for business concepts, not as a schema to copy directly. The legacy application is a CakePHP student administration system with admissions, academic structure, registration, finance, accommodation, exams, dining, awards, staff, ACL, and audit concepts. This first pass covers the new foundation in two layers:

1. Core platform and academic setup.
2. Admissions and applicant-to-student conversion.

The fresh model should be UUID-first, single-institution, migration-driven, auditable, and explicit about workflow state. Legacy `Course` concepts are modelled as `Module` in the new system.

## Legacy Baseline Used

- `ApplicantsDetail` captures applicant type, application type, intake, names, date of birth, title, gender, disability, sponsor, marital status, place of birth, and status.
- `ProgrammeChoice` links applicants to ordered programme choices and the legacy repo already has an integrity patch for duplicate applicant choices.
- `ApplicantOlevelQualification` and `ApplicantAlevelQualification` store centre number, candidate number, exam body, subject, grade, year, and A Level marks/points.
- `ProgrammeRequirement` and `ProgrammeRequirementsSubject` store programme cutoffs, subject requirements, level, and compulsory flags.
- `OfferLetter`, `ApplicantPayment`, `ApplicantsPoint`, `ApplicationType`, `Intake`, `Subject`, `OlevelSubject`, `Point`, `Faculty`, `Department`, and `Programme` provide the main admissions support concepts.
- `FUNCTIONAL_REQUIREMENTS.md` defines the applicant pipeline: applicant account, personal data, qualifications, programme choices, documents, payment, verification, points calculation, selection, offer, offer response, student creation, first registration, invoice, and activation.

## Design Rules

- Every business table has `id uuid primary key`, `created_at`, `updated_at`, `created_by_user_id`, `modified_by_user_id`, `version`, `deleted_at`, and `deleted_by_user_id`.
- Every business table has a Hibernate Envers audit table.
- eMhare is single-institution. Records do not need `institution_id`; academic ownership is handled through `academic_unit_id` where needed.
- Use status history tables for state changes that need audit, not only a mutable status column.
- Store applicant-entered academic evidence as a snapshot. Catalogues validate new capture, but historical evidence must remain readable even if a subject name, exam body, or grading rule later changes.
- Do not hardcode legacy integer codes as primary keys. Keep legacy codes as `legacy_code` or `external_code` during migration.
- Financial transactions must use USD as base currency. ZWG payments must use an effective exchange rate when available and remain unrated if no rate exists.
- eMhare will be split into services. Each table should have a clear owning service and migrations should live with that service.
- Applicants must sign up or log in before applying. Applications are never anonymous.
- Admission requirements use relational rules for normal cases and a small `advanced_rules_json` field for advanced local rules.
- Applicants must pay application fees before fee-required applications enter review, evaluation, or selection.
- Enhancements must preserve the original legacy-baseline requirements unless a requirement is explicitly superseded in this document with a replacement requirement and rationale.

## Persistence And Audit Standard

Every business table shall include:

- `id uuid primary key`
- `created_at`
- `updated_at`
- `created_by_user_id`
- `modified_by_user_id`
- `version`
- `deleted_at` nullable
- `deleted_by_user_id` nullable

Implementation rules:

- These standard fields are mandatory even when an individual table definition below does not repeat them.
- `created_by_user_id` references the user that created the row.
- `modified_by_user_id` references the last user that changed the row.
- `deleted_by_user_id` references the user that soft-deleted the row.
- `deleted_at` and `deleted_by_user_id` are both null for active rows and both populated for soft-deleted rows.
- `version` is the optimistic-lock column used by Hibernate `@Version`.
- Immutable records still carry `created_by_user_id`, `modified_by_user_id`, `deleted_by_user_id`, `deleted_at`, and `version`; after creation, changes require a reversal, amendment, superseding record, or workflow event rather than in-place edits.
- Each service shall configure Hibernate Envers for every business entity.
- Each business table shall have a matching Envers audit table named `<table_name>_aud`.
- Each audit table shall include the audited entity columns plus Envers revision metadata, including `rev` and `revtype`.
- The Envers revision table shall capture revision ID, timestamp, actor user ID, service name, request/correlation ID, and optional reason/comment where available.
- Technical lookup tables may be excluded from Envers only by explicit architecture approval; operational, academic, admissions, finance, accommodation, exam, result, document, and workflow tables are not excluded.

## Service Split

Initial service ownership:

- Core/Identity: institution profile, users, roles, lookup sets, workflow base, audit base.
- Academic Setup: academic unit tree, academic years, periods, intakes, programmes, programme versions, modules, curriculum modules.
- Admissions: intake-scoped application types, applicants, applications, choices, qualifications, requirement sets, evaluations, selection, and offers.
- Finance: fee rules, application payment references, payment confirmations, receipts, exchange rates, finance-account provisioning.
- Student Records/Registration: students, programme enrolments, registration sessions, registration modules, transfer/deferment/amendment workflows.
- Assessment/Results: assessment schemes, mark capture, calculation runs, module results, progression decisions, published results.
- Exams/Timetabling: exam sessions, venues, durations, module exam requirements, master timetables, student timetables, timetable generation runs.
- Accommodation: premises, halls, rooms, facilities, rates, applications, waiting lists, allocation rules, room allocations, room events, damages, blacklists.
- Dining: dining halls, meal options, student dining assignments, dietary requirements, meal attendance.
- Documents/Reporting: uploaded documents, generated documents, document verification, official offer letters.
- Notifications: email/SMS/message dispatch events and provider state.

## Core Schema

### Identity and Institution Profile

`institution_profile`
- `id`
- `code`
- `name`
- `legal_name`
- `default_currency_code` default `USD`
- `country_code`
- `timezone`
- `contact_details_json`
- `branding_json`
- `legacy_code`

`users`
- `id`
- `keycloak_user_id` unique nullable
- `username`
- `email`
- `phone_number`
- `display_name`
- `status` enum: `INVITED`, `ACTIVE`, `LOCKED`, `DISABLED`
- `last_login_at`

`roles`
- `id`
- `code` unique
- `name`
- `scope` enum: `SYSTEM`, `ACADEMIC_UNIT`

`user_role_assignments`
- `id`
- `user_id`
- `academic_unit_id` nullable
- `role_id`
- `starts_at`
- `ends_at`
- unique active assignment on `user_id`, `academic_unit_id`, `role_id`

### Reference Data

`countries`
- `id`
- `iso2_code` unique
- `iso3_code` unique
- `name`
- `nationality_name`

`lookup_sets`
- `id`
- `code`
- `name`
- unique `code`

`lookup_values`
- `id`
- `lookup_set_id`
- `code`
- `name`
- `sort_order`
- `is_active`
- unique `lookup_set_id`, `code`

Use lookup sets for titles, genders, marital statuses, applicant types, sponsor types, disability categories, application routes, delivery methods, and document types. Promote a lookup to a dedicated table only when it needs business rules or relationships.

### Academic Structure

`academic_unit_types`
- `id`
- `code`
- `name`
- `level_order`
- `is_leaf_allowed`
- unique `code`

`academic_units`
- `id`
- `academic_unit_type_id`
- `parent_id` nullable
- `code`
- `name`
- `status`
- `legacy_faculty_code`
- `legacy_department_code`
- unique `code`

This replaces hardcoded `Faculty` and `Department` assumptions. Modern workflows use configurable academic-unit names and types; those legacy terms remain only in migration mappings.

`academic_years`
- `id`
- `name`
- `start_date`
- `end_date`
- `status`
- unique `name`

`academic_period_types`
- `id`
- `code`
- `name`
- `sort_order`

`academic_periods`
- `id`
- `academic_year_id`
- `academic_period_type_id`
- `code`
- `name`
- `start_date`
- `end_date`
- `status`
- unique `code`

`intakes`
- `id`
- `academic_year_id`
- `code`
- `name`
- `starts_on`
- `ends_on`
- `status` enum: `DRAFT`, `OPEN`, `CLOSED`, `ARCHIVED`
- `maximum_programme_choices` default `3`
- unique `code`

`intake_programme_level_targets`
- `id`
- `intake_id`
- `programme_level_id`
- unique active pair: `intake_id`, `programme_level_id`

Every intake must have at least one active programme-level target before it can be opened. Programme levels represent the applicant route, such as undergraduate or postgraduate; programme types remain classifications of individual programmes.

`intake_programme_targets`
- `id`
- `intake_id`
- `programme_id`
- unique active pair: `intake_id`, `programme_id`

Specific programme targets form an optional whitelist. An empty whitelist means every active programme in the intake's selected programme levels is eligible. A targeted programme must be active and belong to one of those levels. Targets may be changed only while the intake is in `DRAFT` status.

### Programmes and Modules

`programme_levels`
- `id`
- `code`
- `name`
- `sort_order`

`programme_types`
- `id`
- `code`
- `name`

`programmes`
- `id`
- `owning_academic_unit_id`
- `programme_type_id`
- `programme_level_id`
- `code`
- `name`
- `award_name`
- `minimum_duration_periods`
- `maximum_duration_periods`
- `status`
- `legacy_programme_code`
- unique `code`

`programme_versions`
- `id`
- `programme_id`
- `version_code`
- `effective_from`
- `effective_to`
- `status` enum: `DRAFT`, `APPROVED`, `RETIRED`
- `approved_by_user_id`
- `approved_at`
- unique `programme_id`, `version_code`

`modules`
- `id`
- `owning_academic_unit_id`
- `code`
- `name`
- `description`
- `credit_value`
- `level`
- `status`
- `legacy_course_code`
- unique `code`

`curriculum_modules`
- `id`
- `programme_version_id`
- `module_id`
- `period_number`
- `module_type` enum: `COMPULSORY`, `ELECTIVE`, `OPTIONAL`
- `minimum_mark_required` nullable
- `sort_order`
- unique `programme_version_id`, `module_id`

### Documents, Workflow, Audit

`documents`
- `id`
- `owner_type`
- `owner_id`
- `document_type_code`
- `file_name`
- `storage_key`
- `mime_type`
- `file_size_bytes`
- `checksum`
- `uploaded_by_user_id`
- `verified_status` enum: `PENDING`, `VERIFIED`, `REJECTED`
- `verified_by_user_id`
- `verified_at`

`workflow_instances`
- `id`
- `workflow_code`
- `subject_type`
- `subject_id`
- `status`
- `started_by_user_id`
- `started_at`
- `completed_at`

`workflow_tasks`
- `id`
- `workflow_instance_id`
- `step_code`
- `assigned_role_id`
- `assigned_user_id`
- `status`
- `decision`
- `comment`
- `completed_by_user_id`
- `completed_at`

`audit_events`
- `id`
- `actor_user_id`
- `event_type`
- `subject_type`
- `subject_id`
- `summary`
- `before_json`
- `after_json`
- `occurred_at`

## Admissions Schema

### Admissions Setup

There is no separately managed admission-cycle business entity. The Academic Setup intake owns the application window, status, maximum programme choices, and programme availability. Admissions may retain a one-to-one internal compatibility projection during migration, but public APIs and new relationships use `intake_id`.

`application_types`
- `id`
- `code`
- `name`
- `requires_employment_history`
- `requires_referees`
- `is_active`
- unique `code`

Examples: undergraduate, postgraduate, transfer, mature entry, RPL, HEXCO, foreign equivalence.

`application_fees`
- `id`
- `application_type_id`
- `applicant_category_code`
- `currency_code`
- `amount`
- `effective_from`
- `effective_to`
- `is_active`

`admission_quotas`
- `id`
- `intake_id`
- `programme_id`
- `quota_type_code`
- `capacity`
- `reserved_capacity`
- `notes`
- unique `intake_id`, `programme_id`, `quota_type_code`

Per ADR-0014, `admission_quotas` is retained only for institutional capacity planning and reporting. No eligibility evaluation, academic recommendation, or admission decision may read or gate on this table.

### Applicant Profile

`applicants`
- `id`
- `user_id`
- `applicant_number` unique
- `applicant_category_code` enum-like: `LOCAL`, `SADC`, `INTERNATIONAL`, `CLE`
- `title_code`
- `first_name`
- `middle_names`
- `last_name`
- `date_of_birth`
- `gender_code`
- `marital_status_code`
- `national_id_number`
- `passport_number`
- `country_id`
- `nationality_country_id`
- `place_of_birth`
- `disability_status_code`
- `special_needs`
- `sponsor_type_code`
- `primary_email`
- `primary_phone`
- `postal_address`
- `residential_address`
- `legacy_applicants_detail_id`

Constraint: `user_id` is required because applicants must sign up or log in before applying. `first_name` and `last_name` are copied from the authenticated registration identity and are read-only in the applicant-owned application workflow; authorised staff correction remains separately governed and audited.

`applicant_next_of_kin`
- `id`
- `applicant_id`
- `full_name`
- `relationship_code`
- `phone_number`
- `email`
- `address`
- `is_primary`

`applicant_employment_histories`
- `id`
- `applicant_id`
- `employer_name`
- `position_title`
- `start_date`
- `end_date`
- `is_current`
- `responsibilities`

`applicant_referees`
- `id`
- `applicant_id`
- `full_name`
- `title`
- `organisation`
- `position_title`
- `email`
- `phone_number`
- `verification_status`
- `reference_document_id`

### Applications and Choices

`applications`
- `id`
- `intake_id`
- `applicant_id`
- `application_type_id`
- `application_number`
- `submitted_at`
- `calculated_total_points` nullable until final submission
- `points_calculated_at` nullable until final submission
- `payment_required`
- `payment_confirmed_at` nullable
- `payment_override_by_user_id` nullable
- `payment_override_reason` nullable
- `status` enum: `DRAFT`, `SUBMITTED`, `PAYMENT_PENDING`, `UNDER_REVIEW`, `INCOMPLETE`, `NOT_ELIGIBLE`, `UNDER_ACADEMIC_REVIEW`, `ADMITTED`, `REJECTED`, `OFFERED`, `ACCEPTED`, `DECLINED`, `WITHDRAWN`, `CONVERTED` (per ADR-0014: `SHORTLISTED` and `SELECTED` are retired; `UNDER_ACADEMIC_REVIEW` and `ADMITTED` replace them)
- `status_reason`
- `verified_by_user_id`
- `verified_at`
- `legacy_statu_id`
- unique `application_number`
- unique active application guard as appropriate for `intake_id`, `applicant_id`, `application_type_id`

Constraint: fee-required applications cannot move to `UNDER_REVIEW`, evaluation, or selection unless `payment_confirmed_at` is present or a payment override is recorded.

`application_status_events`
- `id`
- `application_id`
- `from_status`
- `to_status`
- `reason`
- `changed_by_user_id`
- `changed_at`

`application_programme_choices`
- `id`
- `application_id`
- `programme_id`
- `choice_rank`
- `choice_status` enum: `PENDING`, `ELIGIBLE`, `INELIGIBLE`, `REQUIRES_REVIEW`, `UNDER_ACADEMIC_REVIEW`, `ADMITTED`, `REJECTED`, `OFFERED` (per ADR-0014: `SHORTLISTED` and `SELECTED` are retired; `REQUIRES_REVIEW` and `UNDER_ACADEMIC_REVIEW` are new, `ADMITTED` replaces `SELECTED`)
- `evaluation_summary`
- `decision_reason`
- unique `application_id`, `choice_rank`
- unique `application_id`, `programme_id`

The two uniqueness constraints directly fix the legacy duplicate programme-choice problem.

`application_documents`
- `id`
- `application_id`
- `document_id`
- `requirement_code`
- `is_required`
- `status`
- unique `application_id`, `requirement_code`, `document_id`

### Academic Evidence and Subject Catalogues

`exam_bodies`
- `id`
- `code`
- `name`
- `country_id`
- `is_active`
- unique `code`

`admission_subjects`
- `id`
- `code`
- `name`
- `level` enum: `O_LEVEL`, `A_LEVEL`, `OTHER`
- `subject_group_code`
- `is_science_subject`
- `is_active`
- `legacy_olevel_subject_code`
- `legacy_subject_code`
- unique `level`, `code`

`grading_scales`
- `id`
- `code`
- `name`
- `level`
- `effective_from`
- `effective_to`

`grading_scale_values`
- `id`
- `grading_scale_id`
- `grade`
- `points`
- `is_pass`
- `sort_order`
- unique `grading_scale_id`, `grade`

ZIMSEC is the baseline grading scale. Only A Level `grading_scale_values` carry a meaningful `points` value; O Level results are graded (pass/fail via `is_pass`) but do not contribute to `application_evaluations.total_points`. How other exam bodies (FR-ADM-030) map onto or alongside the ZIMSEC baseline scale is open (see functional requirements section 34).

`applicant_qualification_sittings`
- `id`
- `application_id`
- `level` enum: `O_LEVEL`, `A_LEVEL`, `DIPLOMA`, `DEGREE`, `PROFESSIONAL`, `OTHER`
- `exam_body_id` nullable
- `institution_name`
- `centre_number`
- `candidate_number`
- `year_written`
- `country_id`
- `document_id`
- `legacy_source_table`
- `legacy_source_id`

`applicant_qualification_results`
- `id`
- `qualification_sitting_id`
- `subject_id` nullable
- `subject_name_snapshot`
- `grade`
- `mark` nullable, retained for migrated legacy evidence only and not manually captured for O Level or A Level results
- `points` nullable, calculated by the system from the applicable grading scale
- `is_principal_subject` nullable
- `result_status` enum: `CAPTURED`, `VERIFIED`, `REJECTED`
- unique `qualification_sitting_id`, `subject_id` where `subject_id` is not null

This merges legacy O Level and A Level tables into one evidence model while keeping level-specific rules on the sitting/result rows.

For ZIMSEC A Level results the system-calculated point mapping is A = 5, B = 4, C = 3, D = 2, and E = 1. O Level pass status is calculated from the grading scale but contributes no points. Application submission persists the resulting total on `applications` so later evaluation uses a stable submission snapshot.

### Eligibility, Scoring, Selection

`admission_requirement_sets`
- `id`
- `programme_id`
- `application_type_id`
- `intake_id` nullable
- `version_code`
- `effective_from`
- `effective_to`
- `status` enum: `DRAFT`, `APPROVED`, `RETIRED`
- `minimum_total_points`
- `male_cutoff_points` nullable
- `female_cutoff_points` nullable
- `requires_english`
- `requires_mathematics_or_science`
- `advanced_rules_json` nullable
- `advanced_rules_version` nullable
- `approved_by_user_id`
- `approved_at`

Use relational `admission_subject_requirements` for common rules and `advanced_rules_json` only for exceptional local rules that need expressions, combinations, or route-specific conditions.

Approval of a replacement requirement set retires overlapping approved versions for the same programme, application type, and intake before the new version becomes approved.

`admission_subject_requirements`
- `id`
- `requirement_set_id`
- `level`
- `subject_id` nullable
- `subject_group_code` nullable
- `requirement_type` enum: `COMPULSORY`, `ONE_OF`, `ANY_OF`, `EXCLUDED`, `WEIGHTED`
- `minimum_grade`
- `minimum_points`
- `minimum_count`
- `weight`
- `sort_order`

`application_evaluations`
- `id`
- `application_id`
- `programme_choice_id`
- `requirement_set_id`
- `status` enum: `ELIGIBLE`, `CONDITIONALLY_ELIGIBLE`, `NOT_ELIGIBLE`, `REQUIRES_REVIEW`
- `total_points`
- `rank_score`
- `missing_requirements_json`
- `rule_results_json`
- `evaluated_at`
- `evaluated_by_user_id` nullable
- unique `programme_choice_id`, `requirement_set_id`

`total_points` is copied from the server-calculated application submission snapshot; it is not captured manually by an admissions officer.

`application_clearances`
- `id`
- `application_id`
- `outcome` enum: `CONFIRMED`, `INVALIDATED`
- `payment_cleared`
- `sections_complete`
- `required_documents_verified`
- `qualifications_verified`
- `confirmed_by_user_id`
- `confirmed_at`
- `reason`
- `invalidated_by_user_id` nullable
- `invalidated_at` nullable
- `invalidation_reason` nullable
- unique active confirmed clearance per application

This is the audited evidence behind the user-facing “Confirmed by Admissions” state; the application retains its internal `UNDER_REVIEW` status.

`selection_rounds` (historical — read-only from ADR-0014 onward)
- `id`
- `intake_id`
- `code`
- `name`
- `status` enum: `DRAFT`, `OPEN`, `APPROVED`, `CLOSED`
- `started_at`
- `closed_at`

`selection_decisions` (historical — read-only from ADR-0014 onward)
- `id`
- `selection_round_id`
- `programme_choice_id`
- `decision` enum: `SHORTLIST`, `SELECT`, `REJECT`, `WAITLIST`
- `rank_position`
- `quota_type_code`
- `reason`
- `decided_by_user_id`
- `decided_at`
- unique `selection_round_id`, `programme_choice_id`

`academic_review_assignments` (historical — read-only from ADR-0014 onward)
- `id`
- `selection_round_id`
- `application_id`
- `programme_choice_id`
- `owning_academic_unit_id`, `owning_academic_unit_code`, `owning_academic_unit_name`
- `recommendation_academic_unit_id`, `recommendation_academic_unit_code`, `recommendation_academic_unit_name`
- `hierarchy_path_json`
- `choice_rank`
- `status` enum: `OPEN`, `CLAIMED`, `RECOMMENDED`, `RETURNED`, `COMPLETED`, `CANCELLED`
- `release_attempt`
- release, claim, completion actors and timestamps

`academic_unit_recommendations` (historical — read-only from ADR-0014 onward)
- `id`
- `academic_review_assignment_id`
- `recommendation_sequence`
- `recommendation` enum: `SHORTLIST`, `SELECT`, `REJECT`, `WAITLIST`
- `rank_position`
- `quota_type_code`
- `reason`
- `recommended_by_user_id`
- `recommended_at`
- `review_status` enum: `PENDING`, `APPROVED`, `RETURNED`, `OVERRIDDEN`
- `reviewed_by_user_id`, `reviewed_at`, `review_reason`
- `final_decision` nullable

These four tables are preserved exactly as-is for audit and case history per ADR-0014. No new row is ever written to any of them after this ADR; new academic review, recommendation, and decision processing uses the three tables below instead.

`academic_reviews`
- `id`
- `application_id`
- `programme_choice_id`
- `owning_academic_unit_id`, `owning_academic_unit_code`, `owning_academic_unit_name`
- `recommendation_academic_unit_id`, `recommendation_academic_unit_code`, `recommendation_academic_unit_name`
- `hierarchy_path_json`
- `choice_rank`
- `status` enum: `OPEN`, `CLAIMED`, `RECOMMENDED`, `RETURNED`, `COMPLETED`, `CANCELLED`
- `claimed_by_user_id`, `claimed_at`
- `completed_at`
- unique `application_id`, `programme_choice_id`

The successor to `academic_review_assignments`, scoped directly to the application and programme choice instead of a `selection_round_id`. Created automatically per FR-SEL-027 as soon as a choice becomes eligible, not released as part of a round batch. The owning leaf and resolved highest unit remain immutable workflow snapshots; recommendation authority still requires an active staff assignment at the exact `recommendation_academic_unit_id`.

`academic_recommendations`
- `id`
- `academic_review_id`
- `recommendation_sequence`
- `recommendation` enum: `RECOMMEND_ADMIT`, `RECOMMEND_REJECT`
- `reason`
- `recommended_by_user_id`
- `recommended_at`
- `review_status` enum: `PENDING`, `APPROVED`, `RETURNED`, `OVERRIDDEN`
- `reviewed_by_user_id`, `reviewed_at`, `review_reason`
- `final_decision_id` nullable, references `programme_choice_decisions.id`

The successor to `academic_unit_recommendations`, referencing `academic_reviews` instead of `academic_review_assignments`. `rank_position` and `quota_type_code` are dropped — per ADR-0014, ranking and quota category no longer factor into a recommendation. Recommendations remain advisory.

`programme_choice_decisions`
- `id`
- `application_id`
- `programme_choice_id`
- `decision` enum: `ADMIT`, `REJECT`
- `reason`
- `source_recommendation_id` nullable, references `academic_recommendations.id`
- `decided_by_user_id`
- `decided_at`
- unique `programme_choice_id`

The successor to `selection_decisions`, scoped directly to the application and programme choice instead of a `selection_round_id`, with no `rank_position` or `quota_type_code`. Only Admissions review creates a `programme_choice_decision`; only an `ADMIT` decision can create an offer.

### Payments

`application_payment_references`
- `id`
- `application_id`
- `reference`
- `amount_due`
- `currency_code`
- `base_currency_code` default `USD`
- `exchange_rate_id` nullable
- `base_amount_due` nullable
- `status` enum: `PENDING`, `PAID`, `EXPIRED`, `CANCELLED`
- `required_for_submission`
- unique `reference`

`application_payments`
- `id`
- `application_id`
- `payment_reference_id`
- `provider_code`
- `provider_transaction_reference`
- `amount`
- `currency_code`
- `base_currency_code` default `USD`
- `exchange_rate_id` nullable
- `base_amount` nullable
- `paid_at`
- `status` enum: `PENDING`, `CONFIRMED`, `FAILED`, `REVERSED`
- unique `provider_code`, `provider_transaction_reference`

If a ZWG application payment has no effective exchange rate, `base_amount` stays null and the payment remains unrated for finance review.

Payment ownership note: Finance owns payment references, payment confirmations, receipts, exchange rates, and posting. Admissions consumes payment status before submission/review/selection.

### Offers and Conversion

`offer_batches`
- `id`
- `intake_id`
- `selection_round_id`
- `code`
- `scope_type` enum: `INSTITUTION`, `ACADEMIC_UNIT`, `PROGRAMME`
- `scope_id` nullable
- `status` enum: `DRAFT`, `APPROVED`, `DISPATCHED`, `CLOSED`
- `approved_by_user_id`
- `approved_at`

`offers`
- `id`
- `application_id`
- `programme_choice_id`
- `offer_batch_id` nullable
- `programme_id`
- `intake_id`
- `offer_number`
- `offer_type` enum: `FIRM`, `CONDITIONAL`
- `status` enum: `DRAFT`, `APPROVED`, `SENT`, `ACCEPTED`, `DECLINED`, `EXPIRED`, `WITHDRAWN`, `CONVERTED`
- `conditions_text`
- `acceptance_deadline`
- `registration_date`
- `orientation_date`
- `commencement_date`
- `generated_document_id`
- unique `offer_number`
- unique active offer guard on `application_id`, `programme_id`

`offer_dispatches`
- `id`
- `offer_id`
- `delivery_method_code`
- `sent_to`
- `sent_at`
- `status`
- `provider_message_id`

`offer_responses`
- `id`
- `offer_id`
- `response` enum: `ACCEPTED`, `DECLINED`
- `responded_at`
- `responded_by_user_id` nullable
- `notes`
- unique `offer_id`

`students`
- `id`
- `person_source_applicant_id`
- `student_number`
- `status` enum: `PROVISIONAL`, `ACTIVE`, `DEFERRED`, `SUSPENDED`, `WITHDRAWN`, `GRADUATED`
- `created_from_offer_id`
- unique `student_number`

`student_programme_enrolments`
- `id`
- `student_id`
- `programme_id`
- `programme_version_id`
- `intake_id`
- `status` enum: `PROVISIONAL`, `ACTIVE`, `COMPLETED`, `WITHDRAWN`, `TRANSFERRED`
- `started_on`
- `ended_on`
- unique active enrolment guard on `student_id`, `programme_id`

The conversion service should atomically mark the offer accepted/converted, create the student, assign the programme, provision the student account, and trigger initial billing.

## Suggested Implementation Order

1. Core migrations: `institution_profile`, users/roles, lookups, academic unit types/units, academic years/periods/intakes.
2. Academic setup migrations: programme levels/types, programmes, programme versions, modules, curriculum modules.
3. Documents/workflow/audit base.
4. Admissions setup: intake-scoped application types, fees, quotas, exam bodies, admission subjects, and grading scales.
5. Applicant profile and applications.
6. Programme choices with both uniqueness constraints.
7. Qualification sittings/results and document links.
8. Requirement sets, subject requirements, evaluations, selection rounds/decisions.
9. Payment references/payments.
10. Offers, dispatch, responses, students, and student programme enrolments.

## Legacy Migration Mapping

| Legacy model/table concept | Fresh target |
| --- | --- |
| `ApplicantsDetail` | `applicants` plus `applications` |
| `ApplicationType` | `application_types` |
| `Intake` | `intakes` |
| `ProgrammeChoice` | `application_programme_choices` |
| `ApplicantOlevelQualification` | `applicant_qualification_sittings` + `applicant_qualification_results` where level is `O_LEVEL` |
| `ApplicantAlevelQualification` | `applicant_qualification_sittings` + `applicant_qualification_results` where level is `A_LEVEL` |
| `OlevelSubject`, `Subject` | `admission_subjects` |
| `Point` | `grading_scale_values` |
| `ApplicantsPoint` | `application_evaluations` |
| `ProgrammeRequirement` | `admission_requirement_sets` |
| `ProgrammeRequirementsSubject` | `admission_subject_requirements` |
| `ApplicantPayment` | `application_payment_references` + `application_payments` |
| `OfferLetter` | `offers`, `offer_dispatches`, generated document record |
| `Faculty`, `Department` | `academic_unit_types`, `academic_units` |
| `Programme` | `programmes`, `programme_versions` |
| legacy `Course` | `modules`, `curriculum_modules` |

## Settled Decisions

- eMhare will be split into services, not built as one modular monolith.
- Applicants must sign up or log in before starting, editing, submitting, or responding to an application.
- Admission requirements will use relational rules plus a small versioned expression/rules JSON for advanced local cases.
- Applicants must pay application fees. Fee-required applications cannot enter review, evaluation, or selection until payment is confirmed or an authorised override/waiver is recorded.
