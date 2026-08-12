# eMhare Legacy Requirements and Data Model Extraction

Author: Tinashe K

## Scope

This document extracts what the legacy `emhare_2_9` application was doing end to end. It is not the improved design yet. It is the baseline business map to preserve before rebuilding eMhare fresh.

Evidence used:

- `sparse-emhare/FUNCTIONAL_REQUIREMENTS.md`
- `sparse-emhare/app/Model/*.php`
- `sparse-emhare/app/Controller/*.php`
- `sparse-emhare/app/Config/Schema/2026_02_17_programme_choice_integrity.sql`
- Generated inventories:
  - `legacy-analysis/model-inventory.json`
  - `legacy-analysis/controller-inventory.json`
  - `graphify-out/.graphify_ast.json`

Extraction coverage:

- 779 application files were available in the sparse legacy checkout.
- 358 model classes were parsed.
- 349 controllers were parsed.
- Graph extraction produced 5,814 AST nodes and 6,729 edges.
- Views were not available in the current sparse checkout, so UI details are inferred from controller actions and functional requirements.

## Executive Summary

The legacy app was a full university administration monolith. Admissions was only one entry point. The app also handled academic setup, applicant selection, student records, registration, module/course assignments, coursework capture, exams, results, overall decisions, graduation, finance, accommodation, dining, staff, awards, reporting, ACL, and audit trails.

The strongest rebuild principle is this:

> Do not rebuild admissions alone. Rebuild core first, because admissions depends on identity, institution setup, academic structure, programme setup, subject catalogues, documents, payments, workflow, audit, and student creation.

Legacy language uses `Course`. Fresh eMhare should use `Module`, while retaining `legacy_course_code` during migration.

## Legacy Module Map

| Module | Legacy evidence | What it did |
| --- | --- | --- |
| Identity, ACL, admin | `User`, `SystemGroup`, ACL schema, `LoginHistory`, `PasswordManagement` | User accounts, staff/student/applicant-linked accounts, system groups, login audit, password management. |
| Core reference data | `Country`, `Disability`, `Location`, `Status`, `Category`, `Priority`, `ConditionType`, `BillingMode`, many type tables | Shared catalogues used across admissions, accommodation, finance, and academic workflows. |
| Academic organisation | `Faculty`, `Department`, `StaffDetail`, `DepartmentDeadline`, `DepartmentChairpersonNomination` | Faculty/department hierarchy, staff by department, leadership nomination, departmental deadlines. |
| Academic setup | `Programme`, `ProgrammesType`, `ProgrammeSubtype`, `Course`, `CourseAssignment`, `CourseCapacity`, `CourseWeight`, `CourseInstructor` | Programmes, courses/modules, programme-course links, capacities, weights, instructors, programme/course reports. |
| Admissions | `ApplicantsDetail`, `ApplicationsController`, `ProgrammeChoice`, O/A Level qualifications, `ApplicantsPoint`, `OfferLetter` | Applicant self-service, data capture, qualification capture, programme choices, payment, review, selection, offers, acceptance. |
| Student records | `StudentDetail`, `StudentAssignments`, `StudentRegister`, `StudentDetailsHistory`, `PreviousStudent`, `CurrentRepeatStudent` | Student profile, programme assignment, course/module registration, repeat/carry handling, history, reports. |
| Assessment | `StudentAssignment`, `AssignmentTotal`, `CourseAssignment`, `CourseWeight` | Coursework, practicals, in-class tests, weighting, uploads, computation. |
| Exams | `ExamSession`, `ExamMasterTimetable`, `ExamStudentTimetable`, `ExamVenue`, `ExamDuration`, `ExamCourseTestRegister` | Exam sessions, timetable generation, venues, slots, occupancy, student timetables. |
| Results and decisions | `StudentExamResult`, `CourseResult`, `CurrentResult`, `OverallDecision`, `PublishedStudentOverallDecision`, `GraduantsRegister` | Exam marks, coursework, final results, classifications, progression decisions, publication, graduands. |
| Finance | `Fee`, `ProgrammeFee`, `CourseFee`, `StudentInvoice`, `StudentPayment`, `Receipt`, `GeneralLedger`, `GlRecord`, `BankAccount`, online payment models | Fees, invoices, payments, receipts, statements, bank/payment imports, GL, reversals, reports. |
| Accommodation | `Room`, `RoomAllocation`, `AccomodationGroup`, `AccomodationWaitingList`, `AccomodationRate`, `AccomodationBlacklist`, `AccomodationDamage` | Accommodation applications, groups, priorities, allocations, rooms, moves, check-in/out, billing, blacklists, damages. |
| Dining | `DiningHall`, `StudentDiningHall`, `DiningMealCheck`, `MealOption`, `MealTypeTime` | Dining hall setup, student dining assignment, meal attendance, dining reports. |
| Awards and graduate tracing | `Award`, `AwardNominee`, `AwardSponsor`, `GraduateTracer`, `StudentUniversityAward` | Awards, sponsors, nominations, student awards, graduate tracer records. |
| Reporting and audit | Many controller report actions, audit trail models | Excel/PDF reports, dashboards, audit copies of finance/academic records. |

## End-to-End Legacy Workflows

### 1. Platform And User Administration

What happened:

- System users belonged to groups through `SystemGroup`/ACL concepts.
- Users could be linked to staff, students, or finance activity.
- Login history and password management were tracked.
- Many transactional records stored `user_id`, username, IP address, or audit models.

Fresh model implications:

- Keep identity separate from person/student/applicant records.
- Use role assignments scoped by institution and academic unit.
- Do not rely on one mutable group column for permissions.
- Use a general `audit_events` table plus domain-specific immutable transaction histories.

Core entities:

- `users`
- `roles`
- `permissions`
- `role_permissions`
- `user_role_assignments`
- `login_events`
- `audit_events`

### 2. Academic Organisation Setup

What happened:

- `Faculty` was a top-level academic container.
- `Department` belonged to a faculty.
- `Programme` belonged to a faculty and often a department.
- Staff belonged to departments.
- Departments had deadlines and chairperson nomination workflows.

Fresh model implications:

- Do not hardcode Faculty/Department as universal tables.
- Use configurable `academic_unit_types` and `academic_units`.
- Preserve legacy faculty/department codes during migration.
- Attach programmes and modules to leaf academic units.

Core entities:

- `academic_unit_types`
- `academic_units`
- `staff_profiles`
- `academic_unit_staff_assignments`
- `academic_unit_leadership_terms`
- `academic_deadlines`

### 3. Academic Calendar, Intakes, Periods

What happened:

- `Intake` was used in applications and student intake movement.
- Academic registration dates, semesters, late registration dates, override dates, finance registration periods, and accommodation semester periods existed separately.
- Controllers included actions to move accepted or undecided applicants between February/August intake style periods.

Fresh model implications:

- Create one academic calendar model and let admissions, registration, exams, finance, and accommodation reference it.
- Use `academic_period_types` rather than assuming only semesters.
- Model intakes as first-class admission and enrolment periods.

Core entities:

- `academic_years`
- `academic_period_types`
- `academic_periods`
- `intakes`
- `calendar_events`
- `registration_windows`
- `late_registration_windows`

### 4. Programme And Module Setup

What happened:

- Programmes had code, name, faculty code, department code, type, subtype, min/max duration.
- Courses had course code, name, programme linkage, department, semester, year of study.
- Course assignment records configured programme-course relationships and weightings.
- Course capacity, course exam requirements, course instructors, course fees, and course weights were separate models.
- Programme requirements and requirement subjects drove admissions and academic validation.

Fresh model implications:

- Rename legacy `Course` to `Module`.
- Use `programmes`, `programme_versions`, `modules`, and `curriculum_modules`.
- Separate module catalog from curriculum placement.
- Preserve course/module weights and assessment schemes as versioned academic rules.

Core entities:

- `programme_types`
- `programme_levels`
- `programmes`
- `programme_versions`
- `modules`
- `curriculum_modules`
- `module_offerings`
- `module_instructors`
- `module_capacities`
- `module_assessment_schemes`

### 5. Applicant Self-Service Application

What happened:

- `ApplicationsController` had a guided workflow:
  - `welcome`
  - `personal_details`
  - `create_personal_draft`
  - document upload
  - O Level qualifications
  - A Level qualifications
  - programme choices
  - schools attended
  - sponsor information
  - payment
  - review
  - submit
- Applicants could resume incomplete applications.
- Personal details included applicant type, application type, intake, names, date of birth, title, gender, disability, sponsor, marital status, birthplace, ID/passport-style data, contact details, and status.

Fresh model implications:

- Separate `applicants` from `applications`.
- Allow an applicant to have multiple applications across cycles.
- Store draft progress and completeness by section.
- Link documents to both applicant and application context.
- Require authentication before application creation; unauthenticated legacy application entry is retired.
- Govern sections per application type and snapshot them at draft creation; legacy skippable evidence is retired.
- Treat duplicate education forms as normalized qualification sittings rather than reproducing parallel legacy records.

Admissions entities:

- `applicants`
- `applicant_next_of_kin`
- `applicant_contacts`
- `applications`
- `application_sections`
- `application_documents`
- `application_status_events`

### 6. Qualification Capture

What happened:

- O Level results were captured with centre number, candidate number, exam body, O Level subject, grade, and year/sitting details.
- A Level results were captured with centre number, candidate number, exam body, subject, grade, mark, points, and year written.
- Additional academic records, educational qualifications, work experience, employment history, achievements, referees, and references existed as separate applicant models.
- Legacy O Level subjects and A Level subjects were separate catalogues.

Fresh model implications:

- Merge O Level, A Level, and other academic evidence into a normalized `qualification_sittings` and `qualification_results` model.
- Keep subject snapshots so old applications remain readable even if subject catalogues change.
- Use managed subject catalogues for new capture.
- Model special routes like HEXCO, RPL, mature entry, and foreign equivalence without forcing everything into A Level points.

Admissions entities:

- `exam_bodies`
- `admission_subjects`
- `grading_scales`
- `grading_scale_values`
- `applicant_qualification_sittings`
- `applicant_qualification_results`
- `applicant_prior_education`
- `applicant_employment_histories`
- `applicant_referees`

### 7. Programme Choices And Eligibility

What happened:

- Applicants selected ordered programme choices.
- The legacy app already needed a hardening patch to remove duplicate programme choices and add uniqueness by applicant and choice.
- Programme requirements had cutoffs and subject requirements.
- Applicant points were calculated and used for reports/selection.
- Selection actions included faculty selection, batch approval, final approval, accept, deselect, offered applicant reports, and not-yet-selected reports.

Fresh model implications:

- Store programme choices under `applications`, not directly under applicant profile.
- Enforce uniqueness by application plus rank and application plus programme.
- Use a proper evaluation table that records eligible, conditionally eligible, not eligible, missing subjects, missing points, and review notes.
- Keep selection decisions separately from evaluation results.
- Retire faculty-code route inference, including Education faculty code `F`; MBA and Education are explicit programme mappings.
- Retire batch selection and preserve the rolling application/choice workflow through clearance, eligibility, recommendation, decision, stored offer, response, and idempotent student conversion.

Admissions entities:

- `application_programme_choices`
- `admission_requirement_sets`
- `admission_subject_requirements`
- `application_evaluations`
- `selection_rounds`
- `selection_decisions`
- `admission_quotas`

### 8. Application Payments

What happened:

- Application fees and applicant payments existed.
- Payments were tied to users and applicant records.
- The broader app also included online payment, Ecocash, CBZ deposits, receipts, student payments, bank reports, reversals, and GL/cashbook records.

Fresh model implications:

- Admissions needs application payment references and payment confirmations, but finance ownership should be shared with the finance module.
- Base currency must be USD.
- ZWG payments must use an effective rate when available; no hardcoded 1:1 fallback.

Admissions/finance entities:

- `application_fees`
- `application_payment_references`
- `application_payments`
- `exchange_rates`
- `receipts`
- `ledger_postings`

### 9. Offer Generation And Applicant Response

What happened:

- Offer letters captured registration, commencement, and orientation dates.
- Applicants could be offered places by programme and report batches.
- Actions existed for saving offered applicants, final offer by programme, generating letters, accepted/rejected reports, and accepting/deselecting applicants.
- Functional requirements include delivery tracking, conditional offers, acceptance status, acceptance date, and batch generation by faculty/programme/intake/selection round.

Fresh model implications:

- Treat offers as workflow records, not just generated letters.
- Generated official letters should be stored as generated documents.
- Offer response should be explicit and immutable.
- Applicant-to-student conversion should only happen from an accepted offer.

Admissions entities:

- `offer_batches`
- `offers`
- `offer_conditions`
- `offer_dispatches`
- `offer_responses`
- `generated_documents`

### 10. Applicant-To-Student Conversion

What happened:

- Functional requirements describe conversion after acceptance:
  - accepted applicant converted to student
  - student number assigned
  - student profile created
  - programme assignment recorded
  - first registration starts
  - compulsory courses assigned
  - invoice generated
  - student becomes active
- Legacy models include moved accepted students, reversed accepted applicants, student details, student assignments, student register, student histories, and student finance records.

Fresh model implications:

- Conversion must be transactional and idempotent.
- Accepted applicant should retain its admissions history.
- Student number generation should be explicit and configurable.
- Finance account creation must be idempotent.
- Initial student programme enrolment and initial registration should be separate records.

Student entities:

- `students`
- `student_identifiers`
- `student_programme_enrolments`
- `student_status_events`
- `student_finance_accounts`
- `student_registration_sessions`

### 11. Student Records And Registration

What happened:

- `StudentDetailsController` had many operational actions:
  - profile mini view
  - registration history
  - programme changes
  - intake edits
  - course confirmation
  - repeat/carry registration
  - roll-forward
  - amendments
  - result slips
  - quotations/invoices
  - accommodation selection
  - reports by faculty/programme/level
- `StudentRegister` linked students, programmes, courses, semester, year of study, registered year, and status.
- Repeat, carry, previous, current repeat, deregistered, and history models existed.

Fresh model implications:

- Separate student profile, programme enrolment, term registration, and module registration.
- Model repeat/carry as academic decision outcomes that feed registration, not as ad hoc flags.
- Keep registration amendments as auditable events.

Student/registration entities:

- `students`
- `student_profiles`
- `student_programme_enrolments`
- `registration_sessions`
- `registration_modules`
- `registration_amendments`
- `carry_modules`
- `repeat_modules`
- `registration_status_events`

### 12. Coursework And Continuous Assessment

What happened:

- `StudentAssignmentsController` handled coursework, in-class tests, practical marks, uploads, templates, computation, minimum coursework, and reports.
- `CourseAssignmentsController` handled course weightings and practical weights.
- `AssignmentTotal`, `CourseWeight`, and related tables backed aggregated marks.

Fresh model implications:

- Assessment setup belongs to module offerings or curriculum modules.
- Captured marks should be component-based.
- Computed totals should be reproducible from component rules.
- Uploads should be staged, validated, then posted/approved.

Assessment entities:

- `assessment_schemes`
- `assessment_components`
- `module_assessment_offerings`
- `student_assessment_marks`
- `assessment_upload_batches`
- `assessment_calculations`
- `assessment_status_events`

### 13. Exam Scheduling And Timetables

What happened:

- Exam sessions were configured by test type, programme type, academic year, semester, dates, weekend exclusion, and active status.
- Exam timetable generation existed.
- Exam sessions had slots, datetime patterns, programme type links, venue allocation, occupancy reports, unallocated slot reports, student timetable PDFs, and resets.
- Master timetable linked course test register, duration, session slot, venue, start/end times, registered student count, and status.

Fresh model implications:

- Exam sessions should be independent operational periods.
- Module exam requirements should drive scheduling.
- Venue capacity/occupancy and conflicts need first-class constraints.
- Student timetables should be generated from approved master timetable rows.

Exam entities:

- `exam_sessions`
- `exam_session_slots`
- `exam_venues`
- `exam_venue_types`
- `exam_durations`
- `module_exam_requirements`
- `exam_module_registrations`
- `exam_master_timetable_entries`
- `exam_student_timetable_entries`
- `exam_timetable_generation_runs`

### 14. Results, Decisions, Graduation

What happened:

- Student exam results stored coursework, exam mark, final mark, aggregate mark, classification, result, remark, status, user, and date.
- Current results and course results existed separately.
- Overall decisions were computed from programme rules, compulsory units, cumulative weights, total credits, year ranges, and decisions.
- Published overall decisions and audit models existed.
- Graduation and graduands registers existed.
- Student result slips and transcripts were generated through student detail actions.

Fresh model implications:

- Store component marks, final module results, programme decisions, and published results separately.
- Keep moderation/approval workflow before publication.
- Do not overwrite published official results; corrections need amendment records.
- Official result slips/transcripts should be generated documents.

Results entities:

- `module_results`
- `student_result_components`
- `result_calculation_runs`
- `programme_progression_rules`
- `student_overall_decisions`
- `student_overall_decision_events`
- `published_results`
- `graduation_clearances`
- `graduands`
- `generated_documents`

### 15. Finance And Billing

What happened:

- Finance covered application fees, programme fees, course fees, faculty fees, level fee parameters, invoices, invoice line items, quotations, payments, receipts, cashbook records, bank accounts, GL accounts, GL records, payment suspense, online payments, Ecocash, CBZ deposits, reversals, day-end reports, statements, and account adjustments.
- Student invoices and payments stored debit/credit, account link, transaction date, user, IP, semester/year, descriptions, receipt/reference numbers.
- Reports included accounts receivable, bank payment reports, detailed bank reports, day-end reports, statements, and fee reports.

Fresh model implications:

- Use a proper AR/GL boundary.
- Keep immutable posted ledger entries.
- Application, registration, accommodation, graduation, and other billing should all post through the same finance contracts.
- USD base currency and exchange-rate handling must be central.

Finance entities:

- `fee_catalogues`
- `fee_rules`
- `student_accounts`
- `invoices`
- `invoice_lines`
- `payment_references`
- `payments`
- `receipts`
- `payment_allocations`
- `cashbook_entries`
- `gl_accounts`
- `gl_journals`
- `gl_entries`
- `exchange_rates`
- `reversals`

### 16. Accommodation

What happened:

- Accommodation had premises, halls, rooms, facilities, landlords, suburbs, resident gender rules, rental periods, rates, cut-off dates, groups, group attributes, priorities, application periods, waiting list, pool students, allocation type, allocation process, reserved rooms, room moves/swaps, holding table movement, wrong allocation fixes, check-in/out, reset, blacklists, damages, withdrawal, modification trails, and account receivable reports.
- Allocation was sensitive to gender, disability, country, level, priority, sponsor, programme, and payment state.
- Warden and janitor views existed.

Fresh model implications:

- Accommodation is a separate operational module with its own application, eligibility, allocation, billing, and occupancy state.
- Avoid putting accommodation flags directly on student profile.
- Model room movements and check-in/out as events.

Accommodation entities:

- `accommodation_premises`
- `residence_halls`
- `rooms`
- `room_facilities`
- `accommodation_rates`
- `accommodation_application_periods`
- `accommodation_applications`
- `accommodation_waitlist_entries`
- `accommodation_groups`
- `accommodation_group_rules`
- `room_allocations`
- `room_allocation_events`
- `room_swaps`
- `accommodation_blacklist_entries`
- `accommodation_damage_records`

### 17. Dining

What happened:

- Dining halls, hostel dining halls, attendants, surname filters, meal options, meal times, student dining hall assignments, diets, and meal checks existed.
- Dining meal checks had statistics and PDF reporting.

Fresh model implications:

- Dining should connect to accommodation where relevant, but not depend on accommodation only.
- Meal access should be auditable by student, meal, date/time, and dining hall.

Dining entities:

- `dining_halls`
- `meal_options`
- `meal_service_times`
- `student_dining_assignments`
- `student_dietary_requirements`
- `meal_attendance_events`

### 18. Staff And Teaching Assignment

What happened:

- Staff details included department, EC number, email, title, names, phone contacts.
- Course instructors and dining attendants linked staff to duties.
- Department chairperson nomination periods and nominations existed.

Fresh model implications:

- Staff should be a person/profile record linked to users where needed.
- Teaching assignments should attach to module offerings, not only module catalog records.
- Leadership roles should be time-bound assignments.

Staff entities:

- `staff_profiles`
- `staff_contacts`
- `teaching_assignments`
- `academic_unit_leadership_terms`
- `staff_service_assignments`

### 19. Awards, Sponsors, Graduate Tracing

What happened:

- Awards, award sponsors, nominees, university awards, student university awards, sponsors, prospective sponsors, waivered sponsors, and graduate tracer records existed.

Fresh model implications:

- Sponsors are cross-cutting reference/finance entities.
- Awards and graduate tracking can be later modules but should not be ignored in the legacy migration map.

Entities:

- `sponsors`
- `awards`
- `award_sponsors`
- `award_nominees`
- `student_awards`
- `graduate_tracer_records`

### 20. Reporting, Documents, Audit

What happened:

- Many controller actions generated Excel and PDF outputs.
- Legacy had audit trail tables for courses, fees, GL, programme fees, receipts, student assignments, student exam results, student invoices, student payments, and transactions.
- Functional requirements mention document templates, export, dashboards, and management reports.

Fresh model implications:

- Reports should be regenerated from canonical records.
- Official outputs should be stored in generated document records.
- Audit should be event-based and immutable.

Entities:

- `report_templates`
- `generated_documents`
- `document_dispatches`
- `audit_events`
- `domain_snapshots`

## Fresh Core Data Model Required Before Admissions

The core layer must exist before admissions work starts:

1. `institution_profile`
2. `users`
3. `roles`
4. `permissions`
5. `role_permissions`
6. `user_role_assignments`
7. `lookup_sets`
8. `lookup_values`
9. `countries`
10. `academic_unit_types`
11. `academic_units`
12. `academic_years`
13. `academic_period_types`
14. `academic_periods`
15. `intakes`
16. `programme_types`
17. `programme_levels`
18. `programmes`
19. `programme_versions`
20. `modules`
21. `curriculum_modules`
22. `documents`
23. `workflow_instances`
24. `workflow_tasks`
25. `audit_events`
26. `exchange_rates`

## Fresh Admissions Data Model Required Next

Admissions should start with:

1. `admission_cycles`
2. `application_types`
3. `application_fees`
4. `admission_quotas`
5. `exam_bodies`
6. `admission_subjects`
7. `grading_scales`
8. `grading_scale_values`
9. `applicants`
10. `applicant_next_of_kin`
11. `applicant_contacts`
12. `applicant_employment_histories`
13. `applicant_referees`
14. `applications`
15. `application_sections`
16. `application_status_events`
17. `application_documents`
18. `application_programme_choices`
19. `applicant_qualification_sittings`
20. `applicant_qualification_results`
21. `admission_requirement_sets`
22. `admission_subject_requirements`
23. `application_evaluations`
24. `selection_rounds`
25. `selection_decisions`
26. `application_payment_references`
27. `application_payments`
28. `offer_batches`
29. `offers`
30. `offer_conditions`
31. `offer_dispatches`
32. `offer_responses`
33. `students`
34. `student_programme_enrolments`

## Legacy Constraints To Preserve Or Improve

| Legacy issue or rule | Fresh model action |
| --- | --- |
| Duplicate programme choices needed a cleanup patch. | Add unique constraints on `application_id, choice_rank` and `application_id, programme_id`. |
| Applicant profile and application state were mixed in `ApplicantsDetail`. | Separate `applicants` from `applications`. |
| O Level and A Level were separate tables with similar structure. | Use shared qualification sittings/results with level-specific metadata. |
| Subject catalogues were split between O Level and A Level. | Use `admission_subjects` with `level`. |
| Applicant evidence could be invalidated by catalogue changes. | Store `subject_name_snapshot`, exam body snapshot where needed, and legacy source IDs. |
| Selection and eligibility were embedded in actions/reports. | Store evaluations and selection decisions as auditable rows. |
| Offer letters were a record plus generated output behavior. | Separate offer lifecycle, conditions, dispatch, response, and generated document. |
| Student conversion crossed admissions, student records, registration, and finance. | Make conversion a transactional application service with idempotency guards. |
| Faculty/Department were hardcoded. | Use configurable academic unit tree. |
| Course terminology was legacy. | Use Module terminology with legacy code mapping. |
| Finance models mixed operational payment capture and ledger concepts. | Introduce explicit AR/GL contracts and immutable postings. |
| Audit was duplicated per domain. | Keep domain history where necessary, but add a common `audit_events` stream. |

## Suggested Rebuild Phases

## Settled Product Decisions

- eMhare will be split into services rather than rebuilt as one modular monolith.
- The service split includes Accommodation and Exams/Timetabling as explicit service boundaries, not just future screens inside another service.
- Applicants must sign up or log in before they can start, resume, submit, or respond to an application.
- Admission requirement rules will use relational rules for normal cases plus a small versioned expression/rules JSON for advanced local rules.
- Applicants must pay application fees. Fee-required applications cannot enter review, evaluation, or selection until payment is confirmed or an authorised waiver/override is recorded.
- Every business table will include `created_by_user_id`, `modified_by_user_id`, `deleted_at`, `deleted_by_user_id`, and `version`, and every business entity will have a Hibernate Envers `<table_name>_aud` audit table.
- Enhancement work must preserve the original legacy-baseline requirements unless a specific requirement is explicitly superseded with a replacement requirement and rationale.

### Phase 1: Core Foundation

- Institution setup.
- Identity and role scope.
- Lookup/reference data.
- Academic unit tree.
- Academic years, periods, intakes.
- Documents, workflow, audit.

### Phase 2: Academic Setup

- Programme catalog.
- Programme versions.
- Module catalog.
- Curriculum modules.
- Module assessment scheme placeholders.

### Phase 3: Admissions Setup

- Admission cycles.
- Application types/routes.
- Fees and quotas.
- Exam bodies.
- Managed O Level/A Level subject catalogues.
- Grading scales and points.
- Requirement sets.

### Phase 4: Applicant Portal

- Applicant account/profile.
- Application draft and sections.
- Qualification capture.
- Document uploads.
- Programme choices.
- Payment reference.
- Review and submit.

### Phase 5: Staff Admissions Workflow

- Verification queue.
- Completeness checks.
- Eligibility engine.
- Selection rounds.
- Programme/faculty review.
- Offers and offer documents.
- Applicant response.

### Phase 6: Conversion

- Accepted offer to student.
- Student number generation.
- Student profile.
- Programme enrolment.
- Student finance account.
- Initial registration hook.

### Phase 7: Next Modules After Admissions

- Registration.
- Finance AR/GL.
- Assessment.
- Exams/results.
- Accommodation.
- Dining.
- Awards/graduation.

## Open Questions For Enhancement

- What official institution profile, branding, and document header details should the fresh eMhare use first?
- Should applicants be allowed multiple active applications in one cycle if they use different application routes?
- How many programme choices should be configurable per admission cycle?
- Which payment providers are required in the first build?
- Should accommodation applications start in the first admissions release or remain a later student-services module?
- Which reports are mandatory for go-live: applicant statistics, accepted vs registered, offer batches, qualification summaries, payment reports, or programme capacity reports?
