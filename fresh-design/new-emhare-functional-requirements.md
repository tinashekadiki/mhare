# New eMhare Functional Requirements Document

Author: Tinashe K

Version: 0.1  
Date: 2026-08-08  
Status: Draft baseline for enhancement

## 1. Purpose

This Functional Requirements Document defines the required behaviour for the new eMhare system. It uses the legacy `emhare_2_9` application as the baseline for what existed, then reshapes those requirements for a fresh, cleaner, auditable, modular platform.

The first implementation focus is:

1. Core platform foundation.
2. Academic setup.
3. Admissions and applicant-to-student conversion.

The document also captures future modules from the legacy system so the new design can support student records, registration, finance, assessment, exams, results, accommodation, dining, awards, reporting, and audit without later structural rewrites.

## 2. Product Scope

eMhare shall be a higher-education administration platform for managing the full student lifecycle, starting from applicant registration and ending with student records, finance, academic progression, and official outputs.

The new system shall not be a direct copy of the CakePHP legacy application. It shall preserve the business workflows while improving the model, terminology, workflow controls, auditability, identity integration, and reporting foundation.

## 3. Core Design Principles

- The system shall use `Module` terminology in all new UI, API, documentation, and data model surfaces.
- Legacy `Course` references shall only appear in migration mappings, compatibility fields, and source extraction notes.
- The system shall use migrations for every database change.
- The system shall be single-institution by design for eMhare. It shall keep one institution profile for configuration and branding, but shall not implement multi-tenancy or tenant isolation.
- The system shall support configurable academic structures instead of hardcoded Faculty and Department assumptions.
- The system shall store workflow history and status events, not only the current status.
- The system shall preserve applicant academic evidence as historical snapshots.
- The system shall store official generated documents instead of relying on browser print output.
- The system shall use USD as transaction base currency.
- ZWG transactions shall use an available effective exchange rate; if none exists, the transaction shall remain unrated until a rate is captured.
- The system shall expose operationally complete workflows, not isolated CRUD screens.
- The system shall be split into services rather than built as one modular monolith.
- Applicants shall sign up or log in before they can start an application.
- Applicants shall pay application fees as part of the application process.
- Admissions requirement rules shall support structured relational rules plus a small expression/rules JSON for advanced local cases.
- Enhancements shall preserve the original legacy-baseline requirements unless a requirement is explicitly superseded with a replacement requirement and rationale.

## 4. Architecture Decision Records

The accepted architecture decisions are recorded in [fresh-design/adrs](adrs/README.md). Implementation must follow these ADRs unless a later ADR supersedes them.

## 5. Service Architecture

**FR-ARCH-001:** The system shall be implemented as split services with clear ownership boundaries.

**FR-ARCH-002:** The service split shall include Core/Identity, Academic Setup, Admissions, Finance, Student Records/Registration, Assessment/Results, Exams/Timetabling, Accommodation, Dining, Documents/Reporting, and Notifications.

**FR-ARCH-003:** Each service shall own its database migrations for the tables in its boundary.

**FR-ARCH-004:** Cross-service communication shall use explicit APIs or events, not direct writes into another service's tables.

**FR-ARCH-005:** Admissions shall call Finance to create or verify application-fee payment state, and shall not implement full finance ledger posting itself.

**FR-ARCH-006:** Admissions shall call Core/Identity for applicant account identity and shall not create anonymous applications.

**FR-ARCH-007:** Admissions shall call Documents/Reporting for generated offer letters and official documents.

**FR-ARCH-008:** Services shall preserve idempotency for commands that may be retried, including payment confirmation, offer response, and applicant-to-student conversion.

**FR-ARCH-009:** Exams/Timetabling shall be an explicit service boundary responsible for exam sessions, venues, durations, module exam requirements, master timetables, student timetables, and timetable generation runs.

**FR-ARCH-010:** Accommodation shall be an explicit service boundary responsible for premises, halls, rooms, facilities, rates, applications, waiting lists, allocation rules, room allocations, room events, damages, and blacklists.

## 6. Users And Roles

### 6.1 Primary User Groups

- Applicant
- Student
- Admissions Officer
- Admissions Manager
- Highest academic-unit staff member assigned directly to that unit
- Registry Officer
- Finance Officer
- Accommodation Officer
- Dining Officer
- Examination Officer
- Lecturer or Module Instructor
- System Administrator
- Internal Auditor
- Reporting Officer

### 6.2 Role Requirements

**FR-ROLE-001:** The system shall support roles scoped at system and academic-unit level.

**FR-ROLE-002:** The system shall allow one user to hold multiple active role assignments.

**FR-ROLE-003:** The system shall support time-bound role assignments with start and end dates.

**FR-ROLE-004:** The system shall prevent users from accessing academic-unit records outside their assigned role scope unless they hold an explicit system-wide role.

**FR-ROLE-005:** The system shall record the acting user for all workflow decisions, financial transactions, academic changes, and document verification actions.

## 7. System Modules

The new eMhare shall be organised into these product modules:

1. Core Platform
2. Academic Setup
3. Admissions
4. Student Records
5. Registration
6. Finance
7. Assessment
8. Exams and Timetabling
9. Results and Progression
10. Accommodation
11. Dining
12. Staff and Teaching Assignment
13. Awards and Graduate Tracing
14. Reporting and Generated Documents
15. Audit and Administration

## 8. Core Platform Requirements

### 8.1 Institution Profile

**FR-CORE-001:** The system shall allow system administrators to maintain the eMhare institution profile.

**FR-CORE-002:** The institution profile shall have a code, name, legal name, country, timezone, and default currency.

**FR-CORE-003:** The institution profile shall support branding, contact details, official document header data, and operational settings.

**FR-CORE-004:** The system shall not include multi-institution tenant switching or tenant data isolation.

### 8.2 Identity And Access

**FR-CORE-010:** The system shall maintain users independently from applicant, student, and staff profiles.

**FR-CORE-011:** The system shall support linking users to external identity providers such as Keycloak.

**FR-CORE-012:** The system shall support username, email, display name, phone number, and account status.

**FR-CORE-013:** The system shall support account states including invited, active, locked, and disabled.

**FR-CORE-014:** The system shall record login events for audit and security reporting.

**FR-CORE-015:** The system shall allow applicant accounts to later become student accounts without losing applicant history.

### 8.3 Reference Data

**FR-CORE-020:** The system shall provide managed lookup sets for titles, genders, marital statuses, applicant categories, disability statuses, sponsor types, delivery methods, document types, and workflow reason codes.

**FR-CORE-021:** Lookup values shall support active/inactive status and display ordering.

**FR-CORE-022:** Lookup values shall be configurable for the eMhare institution.

**FR-CORE-023:** Global reference data such as countries and nationalities shall be reusable across modules.

### 8.4 Documents

**FR-CORE-030:** The system shall support document upload for applicants, students, staff, finance records, and academic workflows.

**FR-CORE-031:** Uploaded documents shall store file name, storage key, MIME type, size, checksum, owner type, owner ID, uploader, and upload timestamp.

**FR-CORE-032:** Documents shall support verification statuses of pending, verified, and rejected.

**FR-CORE-033:** Document verification shall record verifier, verification timestamp, and rejection reason where applicable.

**FR-CORE-034:** The system shall prevent official generated documents from being overwritten after publication.

**FR-CORE-035:** Every active staff member assigned directly to the highest academic unit immediately below the institution shall be able to view the consolidated application and all documents for programme choices owned by any descendant academic unit. Staff assigned only to a lower descendant unit shall not receive this recommendation scope unless they also hold an active assignment at the highest unit.

### 8.5 Workflow

**FR-CORE-040:** The system shall provide a generic workflow engine for review and approval processes.

**FR-CORE-041:** Workflow instances shall reference a subject type and subject ID.

**FR-CORE-042:** Workflow tasks shall support assignment by role, user, institution, and academic unit.

**FR-CORE-043:** Workflow decisions shall record decision, comment, actor, and timestamp.

**FR-CORE-044:** The system shall expose pending tasks to authorised users by role and scope.

### 8.6 Audit

**FR-CORE-050:** The system shall record audit events for create, update, delete, workflow decision, status change, document verification, and financial posting actions.

**FR-CORE-051:** Audit events shall include actor, event type, subject type, subject ID, summary, before state, after state, and timestamp.

**FR-CORE-052:** Audit records shall be immutable to ordinary users.

**FR-CORE-053:** Every business entity shall be audited with Hibernate Envers.

**FR-CORE-054:** Every business table shall include `created_by_user_id`, `modified_by_user_id`, `deleted_at`, `deleted_by_user_id`, and `version`.

**FR-CORE-055:** The `version` column shall be used for optimistic locking.

**FR-CORE-056:** Every business table shall have a matching Envers audit table named `<table_name>_aud`.

**FR-CORE-057:** Envers revision metadata shall capture revision ID, timestamp, actor user ID, service name, request/correlation ID, and optional reason/comment where available.

**FR-CORE-058:** Soft-deleted rows shall populate both `deleted_at` and `deleted_by_user_id`.

**FR-CORE-059:** Active rows shall keep both `deleted_at` and `deleted_by_user_id` null.

**FR-CORE-060:** New enhancement requirements shall be additive to the extracted legacy baseline unless the FRD explicitly marks a legacy requirement as superseded.

## 9. Academic Setup Requirements

### 9.1 Academic Unit Structure

**FR-ACAD-001:** The system shall allow eMhare administrators to define academic unit types.

**FR-ACAD-002:** Academic unit types shall support ordering to represent hierarchy depth.

**FR-ACAD-003:** The system shall allow academic units to form a parent-child tree.

**FR-ACAD-004:** Programmes and modules shall be owned by leaf academic units.

**FR-ACAD-005:** The system shall prevent a unit that owns programmes or modules from receiving child units.

**FR-ACAD-006:** The system shall preserve legacy faculty and department codes during migration.

### 9.2 Academic Calendar

**FR-ACAD-010:** The system shall allow administrators to create academic years.

**FR-ACAD-011:** The system shall allow administrators to define academic period types such as semester, term, block, or session.

**FR-ACAD-012:** Academic periods shall belong to academic years and period types.

**FR-ACAD-013:** The system shall allow intakes to be linked to academic years and date ranges.

**FR-ACAD-014:** Admissions, registration, finance, exams, and accommodation shall reference the shared academic calendar.

**FR-ACAD-015:** Every intake shall target one or more programme levels, such as undergraduate or postgraduate.

**FR-ACAD-016:** An intake may optionally restrict eligibility to specific active programmes within its selected programme levels. When no specific programmes are selected, every active programme in the selected programme levels shall be eligible.

**FR-ACAD-017:** Programme eligibility may be changed only while an intake is in draft status. An intake shall not be opened without at least one selected programme level.

### 9.3 Programme Management

**FR-ACAD-020:** The system shall allow authorised users to create and maintain programmes.

**FR-ACAD-021:** A programme shall have a unique code, name, owning academic unit, programme type, programme level, award name, minimum duration, maximum duration, and status.

**FR-ACAD-022:** Programme changes that affect curriculum shall be versioned.

**FR-ACAD-023:** Programme versions shall support draft, approved, and retired states.

**FR-ACAD-024:** Approved programme versions shall record approver and approval timestamp.

**FR-ACAD-025:** Programme codes shall not exceed 5 characters. Programme codes are an internal identifier and are not reported to ZIMCHE, so no external code-format constraint applies.

### 9.4 Module Management

**FR-ACAD-030:** The system shall allow authorised users to create and maintain modules.

**FR-ACAD-031:** A module shall have a unique code, name, description, credit value, level, owning academic unit, and status.

**FR-ACAD-032:** The system shall maintain legacy module mappings from old course codes where applicable.

**FR-ACAD-033:** Modules shall be reusable across programme versions where institution policy allows.

### 9.5 Curriculum Management

**FR-ACAD-040:** The system shall allow modules to be attached to programme versions through curriculum module records.

**FR-ACAD-041:** Curriculum modules shall define period placement, requirement type, credits, sort order, and optional pass rules.

**FR-ACAD-042:** Requirement type shall support compulsory, elective, and optional modules.

**FR-ACAD-043:** Curriculum changes shall not mutate already approved historical programme versions.

## 10. Admissions Setup Requirements

### 10.1 Admissions Intakes

**FR-ADM-001:** Academic Setup intakes shall be the only administrator-managed and applicant-visible admissions windows. The system shall not require a separate admission cycle.

**FR-ADM-002:** Every intake shall belong to an academic year and shall directly scope applications, requirements, quotas, selection rounds, and offers.

**FR-ADM-003:** Intake start and end dates shall define the application window.

**FR-ADM-004:** Intakes shall support draft, open, closed, and archived statuses. Selection and offer lifecycle states shall belong to selection rounds, offer batches, and offers.

**FR-ADM-005:** The system shall prevent application creation and submission outside an open intake unless an authorised override is recorded.

**FR-ADM-006:** Application types shall be independently activated, while intake programme-level and programme targets determine which routes and programmes are available in that intake.

**FR-ADM-007:** Archiving an intake shall preserve application and outcome statistics for reporting without deleting historical admissions records.

### 10.2 Application Types And Routes

**FR-ADM-010:** The system shall maintain application types such as undergraduate, postgraduate, transfer, mature entry, RPL, HEXCO, and foreign equivalence.

**FR-ADM-011:** Application types shall define required sections, required documents, required referees, employment-history requirements, and fee rules.

**FR-ADM-012:** Application types shall be independently activated or deactivated.

### 10.3 Fees And Quotas

**FR-ADM-020:** The system shall maintain application fees by application type, applicant category, currency, and effective date.

**FR-ADM-021:** Per ADR-0014, the system may retain admission quotas by intake, programme, quota type, capacity, and reserved capacity for institutional planning and reporting only. Quotas shall not gate eligibility evaluation, academic recommendation, or admission decisions.

**FR-ADM-022:** Where retained, quotas shall support local categories such as disability, sport, staff dependent, international, or institution-defined categories, for reporting purposes only.

### 10.4 Subject And Grade Setup

**FR-ADM-030:** The system shall maintain exam bodies such as ZIMSEC, Cambridge, and other recognised bodies.

**FR-ADM-031:** The system shall maintain a managed subject catalogue for O Level, A Level, and other qualification levels.

**FR-ADM-032:** Subject catalogue entries shall support code, name, level, group, science-subject classification, status, and legacy source codes. O Level and A Level subjects shall be maintained as level-specific reference data.

**FR-ADM-033:** The system shall maintain grading scales and grade-to-point mappings. The ZIMSEC A Level baseline shall award A = 5, B = 4, C = 3, D = 2, and E = 1 point.

**FR-ADM-034:** Grading scales shall be effective-dated.

## 11. Applicant Portal Requirements

### 11.1 Applicant Account

**FR-APP-001:** The system shall allow applicants to create an account using email and secure credentials.

**FR-APP-002:** The system shall require applicants to log in before they can start, resume, edit, submit, or respond to an application.

**FR-APP-003:** The system shall allow authenticated applicants to resume incomplete applications.

**FR-APP-004:** The system shall show applicants their active application status and required next actions.

**FR-APP-005:** The system shall prevent one applicant from accessing another applicant's records.

**FR-APP-006:** The system shall prevent applicants from editing an application after it has been submitted, except through an authorised staff-initiated correction workflow.

### 11.2 Applicant Profile

**FR-APP-010:** The system shall capture applicant personal details including title, first name, middle names, last name, date of birth, gender, marital status, national ID, passport number, place of birth, nationality, disability status, sponsor type, email, phone number, postal address, and residential address.

The applicant's first and last name shall be sourced from the authenticated account registration, prefilled in Applicant Details, and not editable through the applicant application workflow.

**FR-APP-011:** The system shall capture next of kin details including full name, relationship, phone number, email, and address.

**FR-APP-012:** The system shall support applicant categories including local, SADC, international, and CLE.

**FR-APP-013:** The system shall validate required fields based on application type and applicant category.

### 11.3 Application Draft

**FR-APP-020:** The system shall create an application draft before final submission.

**FR-APP-021:** The application draft shall track completion of required sections.

**FR-APP-022:** Applicants shall be able to save and return to each section before submission.

**FR-APP-023:** The system shall prevent final submission until required sections are complete and the required application fee has been paid, unless an authorised staff override exists.

**FR-APP-024:** The system shall use the applicant's national ID number as a natural key to detect duplicate applications.

**FR-APP-025:** The system shall prevent more than one application per intake for the same national ID.

**FR-APP-026:** Authorised admissions staff shall be able to return a submitted or under-review application to draft, with a recorded correction reason, before an eligibility evaluation is recorded. The applicant shall then be able to edit and resubmit the application.

### 11.4 Documents

**FR-APP-030:** The system shall allow applicants to upload required supporting documents.

**FR-APP-031:** The system shall show missing, pending, verified, and rejected documents.

**FR-APP-032:** Applicants shall be able to replace rejected documents before the application deadline.

**FR-APP-033:** Staff document decisions shall be auditable.

## 12. Qualification Requirements

### 12.1 Qualification Sittings

**FR-QUAL-001:** The system shall allow applicants to record O Level sittings.

**FR-QUAL-002:** The system shall allow applicants to record A Level sittings.

**FR-QUAL-003:** The system shall allow applicants to record diplomas, degrees, certificates, professional qualifications, and other academic records.

**FR-QUAL-004:** Qualification sittings shall capture qualification level, exam body, institution or school, centre number, candidate number, year, country, and supporting document.

**FR-QUAL-005:** The system shall support multiple sittings for one applicant.

### 12.2 Qualification Results

**FR-QUAL-010:** The system shall allow applicants to enter one or more subject results per sitting.

**FR-QUAL-011:** Each result shall capture a managed subject, a system-derived subject name snapshot, grade, and principal/subsidiary indicator where applicable.

**FR-QUAL-012:** The system shall validate new subject selection against the managed subject catalogue.

**FR-QUAL-013:** The system shall preserve the applicant-entered subject snapshot for historical readability.

**FR-QUAL-014:** The system shall prevent duplicate subject results within the same sitting unless a staff override records the reason.

**FR-QUAL-015:** Applicants and admissions staff shall not manually capture marks or points for O Level or A Level subject results. Points and pass status shall be calculated from the applicable grading scale.

### 12.3 Qualification Verification

**FR-QUAL-020:** Admissions staff shall be able to verify or reject qualification sittings and results.

**FR-QUAL-021:** Verification decisions shall record verifier, timestamp, decision, and reason.

**FR-QUAL-022:** Rejected qualification evidence shall remain visible in audit history.

## 13. Programme Choice Requirements

**FR-CHOICE-001:** Applicants shall be able to choose programmes for an application.

**FR-CHOICE-002:** The number of programme choices shall be configurable per intake.

**FR-CHOICE-003:** Each programme choice shall have a rank.

**FR-CHOICE-004:** The system shall prevent duplicate ranks within one application.

**FR-CHOICE-005:** The system shall prevent the same programme from being selected more than once in one application.

**FR-CHOICE-006:** Staff shall be able to amend programme choices before selection if they have permission and record a reason.

**FR-CHOICE-007:** Programme choices shall carry independent evaluation and decision statuses.

**FR-CHOICE-008:** Programme choice options presented to an applicant shall be limited to programmes marked as on offer for the applicable intake and application type (for example, only current Masters programmes on offer shall be selectable for postgraduate applications).

**FR-CHOICE-009:** Programme choice selection shall support lookup by programme code.

**FR-CHOICE-010:** The applicant portal shall allow applicants to download the current list of programmes on offer.

## 14. Eligibility And Selection Requirements

### 14.1 Requirement Sets

**FR-SEL-001:** The system shall allow authorised users to define admission requirement sets by programme, application type, cycle, and effective date.

**FR-SEL-002:** Requirement sets shall support draft, approved, and retired states. Approving a replacement shall retire any overlapping approved version for the same programme, application type, and intake before activating the replacement.

**FR-SEL-003:** Requirement sets shall support minimum total points, gender-specific cutoffs where used, English requirements, a Mathematics-or-Science pass requirement, subject requirements, subject groups, and alternative-route rules. Points totals are computed only from A Level results; O Level results carry a grade but no points and shall not contribute to `total_points`.

**FR-SEL-004:** Requirement changes shall not alter historical evaluation results.

**FR-SEL-005:** Requirement sets shall support a small versioned expression/rules JSON for advanced local rules that cannot be represented cleanly by relational subject and points rules.

**FR-SEL-006:** The rules JSON shall be treated as configuration data, stored with the approved requirement-set version, and included in evaluation audit output.

### 14.2 Evaluation

**FR-SEL-010:** The system shall evaluate each programme choice against the applicable requirement set.

**FR-SEL-011:** Evaluation outcomes shall include eligible, conditionally eligible, not eligible, and requires review.

**FR-SEL-012:** Evaluation results shall store total points, rank score, missing requirements, rule results, evaluated timestamp, and evaluator where manual review occurs.

**FR-SEL-013:** The system shall expose missing subject, missing pass, missing points, and missing document reasons in machine-readable form.

**FR-SEL-014:** Alternative routes such as HEXCO, RPL, mature entry, and foreign equivalence shall be supported as route-level eligibility rules with review notes.

**FR-SEL-015:** Eligibility evaluation shall only consider subject results with a passing grade under the applicable grading scale; failed or ungraded results shall be excluded from points and requirement calculations. ZIMSEC is the baseline grading scale; results from other exam bodies (FR-ADM-030) shall be evaluated against the applicable grading scale for that exam body and level (mapping mechanism to the ZIMSEC baseline is an open question — see section 34).

**FR-SEL-016:** Final application submission shall calculate and persist the applicant's total points and calculation timestamp from captured qualification results. Eligibility officers shall use this server-calculated value and shall not enter or override total points manually.

### 14.3 Rolling Admissions Processing

**FR-SEL-020:** Per ADR-0014, the system shall process each submitted, payment-cleared programme choice individually while its intake is open, through Verification, Eligibility, Academic review, Admission decision, Offer, and Response stages. There is no administrator-opened selection round gating this processing.

**FR-SEL-021:** *(Superseded by FR-SEL-020 and ADR-0014. Selection-round draft/open/approved/closed states are retained only on historical `selection_rounds` records and are not used by new processing.)*

**FR-SEL-022:** Eligibility evaluation shall determine whether a programme choice is eligible, conditionally eligible, not eligible, or requires review, using the applicable requirement set. Ranking, quota category, and other institution-defined comparative criteria shall not determine an eligibility outcome or an admission decision.

**FR-SEL-023:** Active staff assigned directly to the programme's highest academic-unit ancestor immediately below the institution shall be able to record an advisory `Recommend admission` or `Recommend rejection` for programme choices owned anywhere in that unit's descendant tree.

**FR-SEL-024:** Academic recommendations and admission decisions shall record the acting user and timestamp, and a reason where the outcome is a rejection or an override of the recommendation.

**FR-SEL-025:** *(Superseded by FR-SEL-020 and ADR-0014. There is no batch review or approval step; each programme choice reaches its own admission decision independently.)*

**FR-SEL-026:** Admissions shall confirm payment or waiver, required sections, required documents, qualifications, and duplicate checks in a separate audited application-clearance record while retaining the internal under-review application status.

**FR-SEL-027:** The system shall automatically create an academic review for the highest-ranked eligible, unblocked programme choice as soon as it becomes eligible, and shall resolve its recommendation unit from the programme-owning leaf to the root academic unit whose parent is null. This creation is automatic, not a manual "release" action, and does not depend on an open selection round.

**FR-SEL-028:** The academic review shall snapshot the owning leaf, resolved highest academic unit, and full ancestor path so later hierarchy changes do not silently move active work.

**FR-SEL-029:** Recommendation authority shall belong to every active staff member assigned directly to the snapshotted highest academic unit. A recommendation shall remain advisory and shall neither admit the application nor create an offer.

**FR-SEL-030:** Admissions shall approve admission or reject each programme choice directly against its academic recommendation, recording a mandatory reason for a rejection or for any override of the recommendation. Only this admission decision creates the record that can generate an offer.

**FR-SEL-031:** Programme choices shall be processed sequentially by applicant preference rank. An open academic review or an admitted decision on a choice shall block lower-ranked choices; an approved rejection on a choice shall automatically open the next eligible choice for academic review.

**FR-SEL-032:** Spreadsheet export shall be an audited reporting option and shall not serve as a workflow handoff or application status.

**FR-SEL-033:** Every Admissions workflow stage shall show a compact applicant identity summary containing the full display name, applicant number, and application number, with a direct action to open the consolidated applicant profile. Academic-unit access to that profile shall remain read-only and scoped to an active assignment at the exact snapshotted highest academic unit.

**FR-SEL-034:** The consolidated applicant profile shall show a vertical six-stage workflow tracker for Verification, Eligibility, Academic review, Admission decision, Offer, and Response. Each stage shall derive its completed, current, pending, or not-applicable state from the persisted workflow records and show the relevant programme, academic unit, outcome, and event timestamp where available.

**FR-SEL-035:** The admin portal shall present a single compact Admissions table with columns for Applicant, Application, Intake/type, Programme, Points, Payment, Stage, Updated, and Open, supporting server-side search, pagination, and filters for stage, intake, application type, programme, and outcome. Separate pages for verification, evaluation, selection, academic release, recommendations, decisions, and offers are retired; their routes redirect to this table filtered to the equivalent stage.

## 15. Application Payment Requirements

**FR-PAY-001:** The system shall generate a payment reference for application fees.

**FR-PAY-002:** Payment references shall include application, amount due, currency, base currency, exchange rate if available, and status.

**FR-PAY-003:** The system shall accept payment confirmations from configured providers.

**FR-PAY-004:** Provider transaction references shall be unique per provider.

**FR-PAY-005:** Confirmed application payments shall be available for finance reconciliation.

**FR-PAY-006:** ZWG payments shall be converted to USD base amount only when an effective rate exists.

**FR-PAY-007:** If no effective exchange rate exists, the payment shall remain unrated and require finance review.

**FR-PAY-008:** The system shall issue or link receipts for confirmed payments.

**FR-PAY-009:** Applications that require a fee shall not enter admissions review, eligibility evaluation, or selection until payment is confirmed or an authorised waiver/override is recorded.

**FR-PAY-010:** The applicant portal shall show the fee amount, payment reference, payment status, and next action before submission.

## 16. Offer Requirements

### 16.1 Offer Generation

**FR-OFFER-001:** The system shall generate offers from approved selection decisions.

**FR-OFFER-002:** Offers shall include application, selected programme choice, programme, intake, offer type, conditions, registration date, orientation date, commencement date, and acceptance deadline.

**FR-OFFER-003:** Offer types shall include firm and conditional.

**FR-OFFER-004:** Offers shall support draft, approved, sent, accepted, declined, expired, withdrawn, and converted states.

**FR-OFFER-005:** Offer numbers shall be unique.

### 16.2 Offer Batches

**FR-OFFER-010:** The system shall support offer batches by intake and selection round.

**FR-OFFER-011:** Offer batches shall support institution, academic-unit, and programme scopes.

**FR-OFFER-012:** Offer batches shall support approval before dispatch.

### 16.3 Offer Documents And Dispatch

**FR-OFFER-020:** The system shall generate offer letters as stored generated documents.

**FR-OFFER-020A:** Offer creation shall request document generation through the versioned integration event contract. Offer approval and dispatch shall remain blocked until Documents and Reporting confirms that the generated PDF is stored in S3-compatible object storage.

**FR-OFFER-021:** The system shall support dispatch by email and other configured delivery methods.

**FR-OFFER-022:** Dispatch records shall include delivery method, recipient, sent timestamp, provider message ID where available, and status.

### 16.4 Offer Response

**FR-OFFER-030:** Applicants shall be able to accept or decline an offer before the deadline.

**FR-OFFER-031:** Offer response shall record response, timestamp, actor, and notes.

**FR-OFFER-032:** Only accepted offers shall be eligible for student conversion.

**FR-OFFER-033:** Declined offers shall release the occupied place for selection or waitlist processing according to institution rules.

## 17. Applicant-To-Student Conversion Requirements

**FR-CONV-001:** The system shall convert accepted offers into student records through a controlled service.

**FR-CONV-002:** Conversion shall be transactional.

**FR-CONV-003:** Conversion shall be idempotent so repeated requests do not create duplicate students.

**FR-CONV-004:** Conversion shall generate or assign a student number.

**FR-CONV-005:** Conversion shall create a student profile linked to the source applicant and accepted offer.

**FR-CONV-006:** Conversion shall create an initial student programme enrolment.

**FR-CONV-007:** Conversion shall provision or ensure a student finance account.

**FR-CONV-008:** Conversion shall provision student portal access according to the configured identity workflow.

**FR-CONV-009:** Conversion shall mark the application, offer, and programme choice as converted.

**FR-CONV-010:** Conversion shall preserve the full applicant and admissions history.

## 18. Student Records Requirements

These requirements are in scope for the product architecture and future phases. They do not need to be fully implemented before the first admissions release unless conversion requires them.

**FR-STU-001:** The system shall maintain student profiles with student number, identity link, source applicant, demographic details, contact details, disability status, sponsor information, and status.

**FR-STU-002:** The system shall maintain student status history.

**FR-STU-003:** The system shall support student programme enrolments.

**FR-STU-004:** The system shall support programme changes, transfers, deferments, suspensions, withdrawals, and reactivations with workflow approval.

**FR-STU-005:** The system shall preserve historical student programme assignments.

## 19. Registration Requirements

**FR-REG-001:** The system shall support student registration sessions by academic period.

**FR-REG-002:** The system shall allow students or authorised staff to register modules from the approved curriculum.

**FR-REG-003:** The system shall automatically include compulsory modules where institution rules require it.

**FR-REG-004:** The system shall support elective selection, carry modules, repeat modules, late registration, and registration amendments.

**FR-REG-005:** Registration shall support approval workflow by academic unit and registry where configured.

**FR-REG-006:** Confirmed registration shall be available to finance for billing and to assessment/exams for operational planning.

**FR-REG-007:** The system shall prevent duplicate registration records for the same registration number and programme combination.

## 20. Finance Requirements

**FR-FIN-001:** The system shall maintain student finance accounts.

**FR-FIN-002:** The system shall support fee catalogues for application fees, programme fees, module fees, accommodation fees, dining fees, graduation fees, and institution-defined charges.

**FR-FIN-003:** The system shall generate invoices and invoice lines from approved billing events.

**FR-FIN-004:** The system shall record payments, receipts, allocations, reversals, and adjustments.

**FR-FIN-005:** Posted finance records shall be immutable; corrections shall use reversals or credit notes.

**FR-FIN-006:** The system shall support cashbook, bank reconciliation, payment suspense, and GL postings in later finance phases.

**FR-FIN-007:** All finance posting shall preserve USD base currency and rate-source evidence for foreign or ZWG transactions.

## 21. Assessment Requirements

**FR-ASMT-001:** The system shall allow assessment schemes to be defined for module offerings.

**FR-ASMT-002:** Assessment schemes shall support components such as coursework, practical, in-class test, and final exam.

**FR-ASMT-003:** Components shall support weights, maximum marks, capture windows, and approval status.

**FR-ASMT-004:** Authorised instructors shall be able to capture and upload assessment marks.

**FR-ASMT-005:** The system shall calculate aggregate coursework and component totals from approved rules.

**FR-ASMT-006:** Mark changes after submission shall require amendment workflow and audit.

## 22. Exams And Timetabling Requirements

**FR-EXAM-001:** The system shall support exam sessions by academic period, test type, and date range.

**FR-EXAM-002:** The system shall maintain exam venues, venue types, capacity, and availability.

**FR-EXAM-003:** The system shall maintain module exam requirements and durations.

**FR-EXAM-004:** The system shall generate master timetable entries from registered students, module requirements, venues, and slots.

**FR-EXAM-005:** The system shall detect student timetable clashes.

**FR-EXAM-006:** The system shall generate student exam timetables from approved master timetable entries.

**FR-EXAM-007:** Exam timetable changes shall be auditable.

## 23. Results And Progression Requirements

**FR-RES-001:** The system shall maintain module results with coursework, exam mark, final mark, grade, remark, and status.

**FR-RES-002:** The system shall support result calculation runs.

**FR-RES-003:** The system shall support moderation and approval workflow before publication.

**FR-RES-004:** The system shall support programme progression decisions based on approved rules.

**FR-RES-005:** The system shall publish approved results in controlled batches.

**FR-RES-006:** Published results shall not be overwritten; corrections shall use amendment records.

**FR-RES-007:** Official result slips, transcripts, certificates, and statements shall be generated and stored as generated documents.

## 24. Accommodation Requirements

**FR-ACC-001:** The system shall maintain accommodation premises, halls, rooms, facilities, room types, rates, and allocation periods.

**FR-ACC-002:** Students shall be able to apply for accommodation when an application period is open.

**FR-ACC-003:** The system shall support allocation groups and rules based on disability, gender, programme, sponsor, level, location, payment state, and institution-defined priorities.

**FR-ACC-004:** The system shall support waiting lists, allocations, room moves, swaps, withdrawals, blacklists, damages, check-in, and check-out.

**FR-ACC-005:** Accommodation billing events shall integrate with finance.

**FR-ACC-006:** Accommodation staff shall have operational reports for occupancy, allocation, unpaid students, gender, disability, country, and priority groups.

## 25. Dining Requirements

**FR-DINE-001:** The system shall maintain dining halls, meal options, meal service times, and attendants.

**FR-DINE-002:** The system shall assign students to dining halls or dining plans.

**FR-DINE-003:** The system shall capture meal attendance events.

**FR-DINE-004:** The system shall maintain student dietary requirements.

**FR-DINE-005:** Dining reports shall support statistics by dining hall, meal, period, and student group.

## 26. Staff And Teaching Assignment Requirements

**FR-STAFF-001:** The system shall maintain staff profiles with staff number, department or academic-unit assignment, contact details, and status.

**FR-STAFF-002:** Staff profiles may be linked to system users.

**FR-STAFF-003:** The system shall support teaching assignments by module offering.

**FR-STAFF-004:** The system shall support academic-unit leadership terms and nominations where configured.

**FR-STAFF-005:** The system shall support staff service assignments such as dining attendants or exam invigilators in later phases.

## 27. Awards And Graduate Tracing Requirements

**FR-AWARD-001:** The system shall maintain award definitions.

**FR-AWARD-002:** The system shall maintain award sponsors.

**FR-AWARD-003:** The system shall support award nominations and student award records.

**FR-AWARD-004:** The system shall support graduate tracer records for alumni follow-up.

## 28. Reporting And Generated Documents

**FR-RPT-001:** The system shall provide operational reports for admissions, student records, finance, registration, exams, results, accommodation, dining, and audit.

**FR-RPT-002:** Reports shall respect institution and role scope.

**FR-RPT-003:** Reports shall support export where authorised.

**FR-RPT-004:** Official outputs shall be generated as stored generated documents.

**FR-RPT-005:** Generated documents shall record template, input parameters, owner, generated by, generated at, storage key, checksum, and publication status.

**FR-RPT-006:** The system shall support preview and download of generated documents through controlled access.

## 29. Notifications

**FR-NOTIF-001:** The system shall send notifications for application submission, missing documents, payment confirmation, verification decisions, offer dispatch, offer response, conversion, registration actions, and workflow tasks.

**FR-NOTIF-002:** Notification events shall be recorded with recipient, channel, status, and provider reference where available.

**FR-NOTIF-003:** Failed notification delivery shall not silently change business state.

## 30. Migration Requirements

**FR-MIG-001:** The system shall preserve legacy identifiers in dedicated legacy mapping fields or mapping tables.

**FR-MIG-002:** Legacy applicant records shall migrate into separate applicant and application records.

**FR-MIG-003:** Legacy O Level and A Level result records shall migrate into qualification sittings and qualification results.

**FR-MIG-004:** Legacy Faculty and Department records shall migrate into configurable academic units.

**FR-MIG-005:** Legacy Course records shall migrate into Module records.

**FR-MIG-006:** Legacy programme choices shall be deduplicated before enforcing new uniqueness constraints.

**FR-MIG-007:** Migration shall not overwrite official historical records.

**FR-MIG-008:** Migration and redesign shall preserve fulfilment of the extracted original legacy requirements unless a requirement is explicitly superseded.

## 31. Non-Functional Requirements

### 31.1 Security

**NFR-SEC-001:** The system shall enforce authentication for all non-public operations.

**NFR-SEC-002:** The system shall enforce role and academic-unit scope authorisation on protected resources.

**NFR-SEC-003:** Sensitive actions shall be auditable.

**NFR-SEC-004:** Uploaded files shall be validated for allowed type and size.

### 31.2 Data Integrity

**NFR-DATA-001:** The system shall enforce database constraints for natural uniqueness rules such as application number, student number, programme choice rank, provider transaction reference, and one application per intake per national ID.

**NFR-DATA-002:** The system shall use transactional boundaries for applicant submission, offer response, conversion, billing, and result publication.

**NFR-DATA-003:** The system shall use append-only histories for status changes where audit matters.

**NFR-DATA-004:** Every business table migration shall add `created_by_user_id`, `modified_by_user_id`, `deleted_at`, `deleted_by_user_id`, `version`, and the Hibernate Envers audit table.

**NFR-DATA-005:** Services shall reject stale updates when the submitted entity version does not match the current row version.

### 31.3 Performance

**NFR-PERF-001:** Applicant application pages shall load within an acceptable operational time under normal admission traffic.

**NFR-PERF-002:** Selection and evaluation jobs shall support batch processing.

**NFR-PERF-003:** Reports with large datasets shall run asynchronously where synchronous execution would degrade user experience.

### 31.4 Availability And Recovery

**NFR-OPS-001:** Critical workflows shall avoid partial state after failures.

**NFR-OPS-002:** The system shall support retryable background jobs for notifications, document generation, and batch evaluations.

**NFR-OPS-003:** The system shall record job failures with enough context for operational support.

### 31.5 Usability

**NFR-UX-001:** Applicant and staff workflows shall show clear status, required actions, and validation feedback.

**NFR-UX-002:** The system shall use modal confirmation patterns suitable for the frontend, with SweetAlert preferred over browser alert or confirm dialogs.

**NFR-UX-003:** User-facing academic text shall use Module terminology.

## 32. Release Scope Recommendation

### Release 1: Core And Admissions

Release 1 should include:

- Institution profile setup.
- Split service boundaries for Core/Identity, Academic Setup, Admissions, Finance, Student Records/Registration, Assessment/Results, Exams/Timetabling, Accommodation, Dining, Documents/Reporting, and Notifications.
- Identity and roles.
- Academic-unit setup.
- Academic calendar and intakes.
- Programme and Module setup.
- Intake-owned admissions windows.
- Application types and fees.
- Subject catalogue and grading scales.
- Applicant portal.
- Qualification capture.
- Programme choices.
- Application payment reference and confirmation.
- Staff verification.
- Eligibility evaluation.
- Selection rounds.
- Offer generation and response.
- Applicant-to-student conversion.
- Generated offer documents.
- Audit events and core reports.

### Release 2: Student Registration And Finance

Release 2 should include:

- Student records.
- Programme enrolment management.
- Registration sessions.
- Module registration.
- Student accounts.
- Invoices.
- Payments, receipts, allocations, reversals.
- Registration billing.

### Release 3: Assessment, Exams, Results

Release 3 should include:

- Assessment schemes.
- Mark capture.
- Exam sessions and timetables.
- Module results.
- Progression decisions.
- Official result outputs.

### Release 4: Student Services

Release 4 should include:

- Accommodation.
- Dining.
- Awards.
- Graduate tracing.

## 33. Acceptance Criteria For Release 1

Release 1 shall be accepted only when:

1. The eMhare institution profile can be configured with academic units, periods, intakes, programmes, and modules.
2. An intake can be opened as the application window.
3. An applicant can sign up or log in, complete an application, upload documents, capture qualifications, select programme choices, pay or confirm application fee, review, and submit.
4. Admissions staff can verify application sections and documents.
5. The system can evaluate programme choices and show eligibility outcomes.
6. Admissions can release eligible choices to the resolved highest academic unit, authorised root-unit staff can record advisory recommendations across the descendant programme tree, and Admissions can approve, return, or override them into final decisions.
7. The system can generate and dispatch offer letters.
8. Applicants can accept or decline offers.
9. Accepted offers can be converted into student records without duplicate students.
10. Conversion creates student profile, programme enrolment, finance account hook, and student portal access hook.
11. All major status changes and decisions are auditable.
12. Official offer letters are stored as generated documents.
13. Programme-choice duplicate constraints are enforced.
14. ZWG payment handling does not hardcode a 1:1 USD rate.

## 34. Open Questions

- What official institution name, branding, and document header data should the first eMhare profile use?
- How many programme choices should be the default per intake?
- Which payment providers must be supported in Release 1?
- Which staff roles approve offer batches in the first implementation?
- Should offer acceptance require payment, document verification, or both?
- Which legacy reports are mandatory for the first admissions go-live?
- What criteria determine whether an applicant is classified as local for admissions purposes? National ID alone may not be sufficient (raised by Admissions & Registration, still to be confirmed).
- What specific admissions and registration reports does Admissions & Registration require for go-live, beyond the general FR-RPT-001 operational reporting requirement?
- What contact-detail capture is specifically required for Masters/postgraduate applicants that differs from the standard applicant profile in FR-APP-010?
- Given some existing/legacy programme codes exceed 5 characters, how should the new 5-character limit (FR-ACAD-025) be enforced during migration: renaming, an exception list, or a hard constraint applied only to new programmes?
- ZIMSEC is confirmed as the baseline grading scale (FR-SEL-015). What is the mechanism for mapping non-ZIMSEC exam body results (e.g. Cambridge, FR-ADM-030) onto or against that baseline: a separate `grading_scales` row per exam body, an explicit grade-conversion table, or something else?
