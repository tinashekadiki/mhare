# Admissions Rolling Workflow — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the governance and reference-documentation foundation — a superseding ADR plus updated functional requirements and data model — for replacing Admissions' round/batch-driven selection and offer pipeline with one rolling per-applicant workflow (Verification → Eligibility → Academic review → Admission decision → Offer → Response), before any backend or frontend code changes.

**Architecture:** This is a documentation-only plan. It touches four markdown files under `fresh-design/`: a new ADR, the ADR index, the functional requirements document, and the core-and-admissions data model document. No Java, Vue, or migration files change in this plan — those are separate downstream plans (Admissions backend, Documents backend, Notifications backend, admin-portal frontend, applicant-portal frontend) that depend on this one being reviewed and accepted first.

**Tech Stack:** Markdown documentation only. No build tooling is invoked by this plan.

## Global Constraints

- Author every new or edited document as Tinashe K, matching the existing convention in `fresh-design/adrs/` and `fresh-design/*.md`.
- New ADRs in this project are written directly as `Status: Accepted` (see ADR-0012, ADR-0013) — this project's ADR process is single-architect authorship, not a multi-stakeholder proposal/review cycle. Do not write ADR-0014 as "Proposed."
- Do not edit any existing migration file. This plan creates no migrations; downstream backend plans must run `mvn flyway:info` against a live database before authoring new ones (current confirmed baselines: admissions-service V33, documents-reporting-service V5, notifications-service V7).
- Do not delete historical requirement or data-model content. Superseded items are marked superseded in place (with a one-line pointer to ADR-0014) so requirement IDs and traceability remain stable; they are not renumbered or removed.
- Local commits per task are allowed and expected: this plan is executed via superpowers:subagent-driven-development, whose review mechanism depends on one commit per task (resolved with the user 2026-08-10 — choosing subagent-driven execution is the explicit ask). No push, PR, or remote operation of any kind.
- Do not create new `.md` files beyond the one ADR this plan requires (`fresh-design/adrs/0014-rolling-per-applicant-admissions-processing.md`). All other changes are edits to existing files.
- Preserve `Module` terminology (never `Course`) and UZ terminology conventions already used throughout `fresh-design/`.

---

## Source-of-Truth Reference (read before starting)

These exact locations were confirmed present before this plan was written. If any have moved, stop and re-verify before proceeding — the edits below use exact `old_string` anchors that must match byte-for-byte.

- `fresh-design/adrs/README.md` — ADR index table, highest entry is ADR-0013.
- `fresh-design/adrs/0012-intakes-as-the-sole-admissions-window.md` — establishes intake as the sole admissions window; contains the line this ADR narrows: "A selection round may start only after application capture for its intake is closed."
- `fresh-design/new-emhare-functional-requirements.md` — sections 10.3 (lines 303–309), 14.3 (lines 467–497), 16.1–16.4 (lines 523–561), 29 (lines 739–745), 32 (lines 813–839).
- `fresh-design/core-and-admissions-data-model.md` — `admission_quotas` (lines 361–369), `selection_rounds` through `academic_unit_recommendations` (lines 632–682), `offer_batches` through `offer_responses` (lines 720–766), Suggested Implementation Order (lines 789–800).

---

### Task 1: Write ADR-0014 and register it in the ADR index

**Files:**
- Create: `fresh-design/adrs/0014-rolling-per-applicant-admissions-processing.md`
- Modify: `fresh-design/adrs/README.md`

**Interfaces:**
- Produces: the ADR number (`0014`) and its exact table/enum vocabulary (`academic_reviews`, `academic_recommendations`, `programme_choice_decisions`, recommendation values `RECOMMEND_ADMIT`/`RECOMMEND_REJECT`, decision values `ADMIT`/`REJECT`) that Tasks 2–10 and every downstream plan must reuse verbatim. Do not invent alternate names for these tables/enums in later tasks or plans.

- [ ] **Step 1: Create the ADR file**

Write the following as the complete content of `fresh-design/adrs/0014-rolling-per-applicant-admissions-processing.md`:

```markdown
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
- The admin portal exposes admissions processing through exactly two screens: a compact `/operations/admissions` table and the single `/operations/admissions/{applicationId}` case workspace already described by FR-SEL-033/034. `admissions-verification.vue`, `admissions-evaluation.vue`, `admissions-selection.vue`, `admissions-academic-release.vue`, `admissions-recommendations.vue`, `admissions-decisions.vue`, and `admissions-offers.vue` are retired as separate pages; their routes redirect to the compact table filtered to the equivalent stage.
- Bulk functionality is retained only as a programme-level, intake-scoped print/export of already-published offer letters. It reads published documents; it never creates, approves, decides, or dispatches an offer, and it introduces no batch lifecycle of its own.

This decision supersedes FR-SEL-020 through FR-SEL-032, FR-OFFER-010 through FR-OFFER-012, FR-ADM-021 and FR-ADM-022, and the "Selection rounds" line item in the Release 1 scope recommendation, as updated in the accompanying functional-requirements and data-model changes. It builds on, and does not conflict with, ADR-0012's rule that intake governs the application window; it narrows ADR-0012's remaining reference to "a selection round may start only after application capture for its intake is closed" by removing the selection round it referred to.

## Consequences

- An applicant who clears verification, is automatically found eligible, and receives an academic recommendation can reach an admission decision and an offer without waiting for an administrator to open or close anything, other than the intake itself being open at submission time.
- Admissions staff work from one compact table and one case workspace instead of eight separate processing pages, reducing the surface area for stage-tracking bugs and inconsistent applicant summaries.
- Historical selection-round and offer-batch outcomes remain fully auditable and reportable; no backfill or reinterpretation of that data into the new tables is required or attempted.
- Capacity and quota data, if retained for institutional planning, must not be read by any new eligibility, recommendation, or decision code path; enforcing that becomes a code-review and contract-test concern for the implementation plans rather than a database constraint, since the tables may still exist for reporting.
- Programme-level bulk printing becomes a strictly read-only reporting feature; it can be built, changed, or removed without any risk to the live decision/offer pipeline, and its own audit trail (requesting user, intake, programme, format, document count, timestamp) is independent of offer audit history.
- The seven retired admin-portal pages and their route-specific navigation entries are deleted from primary navigation; only Admissions, Applicant register, and Application types remain as top-level Admissions navigation items, per the implementation plans that follow this ADR.

## Implementation Notes

- New tables (`academic_reviews`, `academic_recommendations`, `programme_choice_decisions`) and updated status enums are specified in the "Eligibility, Scoring, Selection" section of `core-and-admissions-data-model.md`, dated to this ADR.
- Migrations backing this ADR must be created fresh against the current admissions-service baseline (Flyway V33 at the time of this ADR) and must never edit V1–V33; `mvn flyway:info` must be run against a live database before authoring them, per project convention.
- `offers.offer_batch_id` remains in the schema as a nullable, no-longer-populated column referencing historical batches; new offers always leave it null.
```

- [ ] **Step 2: Register ADR-0014 in the index**

In `fresh-design/adrs/README.md`, find this exact table row (the last row of the index table):

```
| [ADR-0013](0013-application-payment-evidence-and-hosted-card-payments.md) | Application payment evidence and hosted card payments | Accepted |
```

Replace it with:

```
| [ADR-0013](0013-application-payment-evidence-and-hosted-card-payments.md) | Application payment evidence and hosted card payments | Accepted |
| [ADR-0014](0014-rolling-per-applicant-admissions-processing.md) | Rolling per-applicant admissions processing | Accepted |
```

- [ ] **Step 3: Verify**

Run:
```bash
grep -n "ADR-0014" fresh-design/adrs/README.md
ls fresh-design/adrs/0014-rolling-per-applicant-admissions-processing.md
```
Expected: the README grep prints the new table row; the `ls` prints the file path with no "No such file" error.

---

### Task 2: Update functional requirements — retire quotas as a gating mechanism (Section 10.3)

**Files:**
- Modify: `fresh-design/new-emhare-functional-requirements.md`

**Interfaces:**
- Consumes: ADR-0014's statement that "capacity and quota records, where they still exist for institutional planning, do not gate" any decision.
- Produces: none (leaf documentation change).

- [ ] **Step 1: Replace FR-ADM-021 and FR-ADM-022**

Find this exact text:

```
**FR-ADM-021:** The system shall support admission quotas by cycle, programme, quota type, capacity, and reserved capacity.

**FR-ADM-022:** Quotas shall support local categories such as disability, sport, staff dependent, international, or institution-defined categories.
```

Replace it with:

```
**FR-ADM-021:** Per ADR-0014, the system may retain admission quotas by intake, programme, quota type, capacity, and reserved capacity for institutional planning and reporting only. Quotas shall not gate eligibility evaluation, academic recommendation, or admission decisions.

**FR-ADM-022:** Where retained, quotas shall support local categories such as disability, sport, staff dependent, international, or institution-defined categories, for reporting purposes only.
```

- [ ] **Step 2: Verify**

Run: `grep -n "FR-ADM-021\|FR-ADM-022" fresh-design/new-emhare-functional-requirements.md`
Expected: both requirements print with the new "for institutional planning and reporting only" / "for reporting purposes only" wording, no duplicate lines.

---

### Task 3: Update functional requirements — rewrite Section 14.3 as rolling admissions processing

**Files:**
- Modify: `fresh-design/new-emhare-functional-requirements.md`

**Interfaces:**
- Consumes: ADR-0014's stage names (Verification, Eligibility, Academic review, Admission decision, Offer, Response) and action names (`Recommend admission`, `Recommend rejection`, `Approve admission`, `Reject`).
- Produces: FR-SEL-035, the new requirement for the compact Admissions table columns/filters, which Task 4's admin-portal-facing plan (future) must implement exactly as listed.

- [ ] **Step 1: Replace the entire "14.3 Selection" section**

Find this exact block (the section header through the end of FR-SEL-034, immediately before `## 15. Application Payment Requirements`):

```
### 14.3 Selection

**FR-SEL-020:** The system shall support selection rounds per intake.

**FR-SEL-021:** Selection rounds shall support draft, open, approved, and closed states.

**FR-SEL-022:** The system shall rank eligible applicants by programme choice, score, quota category, and institution-defined criteria.

**FR-SEL-023:** Active staff assigned directly to the programme's highest academic-unit ancestor immediately below the institution shall be able to record an advisory shortlist, select, reject, or waitlist recommendation for programmes owned anywhere in that unit's descendant tree.

**FR-SEL-024:** Selection decisions shall record rank position, quota type, reason, actor, and timestamp.

**FR-SEL-025:** The system shall support batch review and approval of selected applicants.

**FR-SEL-026:** Admissions shall confirm payment or waiver, required sections, required documents, qualifications, and duplicate checks in a separate audited application-clearance record while retaining the internal under-review application status.

**FR-SEL-027:** Admissions shall release the highest-ranked eligible, unblocked programme choice into an open selection round and shall resolve its recommendation unit from the programme-owning leaf to the root academic unit whose parent is null.

**FR-SEL-028:** The release assignment shall snapshot the owning leaf, resolved highest academic unit, and full ancestor path so later hierarchy changes do not silently move active work.

**FR-SEL-029:** Recommendation authority shall belong to every active staff member assigned directly to the snapshotted highest academic unit. A recommendation shall remain advisory and shall neither select the application nor create an offer.

**FR-SEL-030:** Admissions shall approve a recommendation, return it for reconsideration, or record a different final decision with a mandatory override reason. Only this reviewed outcome shall create the selection decision.

**FR-SEL-031:** Programme choices shall be processed sequentially by applicant preference rank. A shortlist, selection, or waitlist shall block lower choices; an Admissions-approved rejection or explicit waitlist release shall open the next eligible choice; selection shall close all lower choices.

**FR-SEL-032:** Spreadsheet export shall be an audited reporting option and shall not serve as a workflow handoff or application status.

**FR-SEL-033:** Every Admissions workflow stage shall show a compact applicant identity summary containing the full display name, applicant number, and application number, with a direct action to open the consolidated applicant profile. Academic-unit access to that profile shall remain read-only and scoped to an active assignment at the exact snapshotted highest academic unit.

**FR-SEL-034:** The consolidated applicant profile shall show a vertical five-stage workflow tracker for confirmation, academic release, academic-unit recommendation, Admissions final decision, and offer processing. Each stage shall derive its completed, current, pending, or not-applicable state from the persisted workflow records and show the relevant programme, academic unit, outcome, and event timestamp where available.
```

Replace it with:

```
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
```

- [ ] **Step 2: Verify**

Run: `grep -n "^### 14.3\|FR-SEL-0[2-3][0-9]" fresh-design/new-emhare-functional-requirements.md`
Expected: section header reads "Rolling Admissions Processing"; FR-SEL-020 through FR-SEL-035 all present exactly once each, with no leftover "selection round," "shortlist," "quota," or "batch" language in FR-SEL-020–034 (FR-SEL-021 and FR-SEL-025 legitimately still mention "selection-round"/"batch" only inside their superseded-pointer text).

---

### Task 4: Update functional requirements — offers, retire batches, add programme export

**Files:**
- Modify: `fresh-design/new-emhare-functional-requirements.md`

**Interfaces:**
- Consumes: ADR-0014's offer-generation and bulk-print decisions.
- Produces: FR-OFFER-040 through FR-OFFER-047, which the Documents backend plan must implement exactly (merged PDF + ZIP export contract, audit fields, publication-event projection).

- [ ] **Step 1: Amend FR-OFFER-001**

Find this exact text:

```
**FR-OFFER-001:** The system shall generate offers from approved selection decisions.
```

Replace it with:

```
**FR-OFFER-001:** Per ADR-0014, the system shall generate one offer directly from an approved admission decision on a programme choice, without an offer batch.
```

- [ ] **Step 2: Retire the "16.2 Offer Batches" section**

Find this exact block:

```
### 16.2 Offer Batches

**FR-OFFER-010:** The system shall support offer batches by intake and selection round.

**FR-OFFER-011:** Offer batches shall support institution, academic-unit, and programme scopes.

**FR-OFFER-012:** Offer batches shall support approval before dispatch.
```

Replace it with:

```
### 16.2 Offer Batches (Retired)

**FR-OFFER-010:** *(Superseded by ADR-0014. Offer batches are retired as an active concept; the system no longer supports creating, approving, or dispatching an offer batch.)*

**FR-OFFER-011:** *(Superseded by ADR-0014.)*

**FR-OFFER-012:** *(Superseded by ADR-0014.)* Historical `offer_batches` records remain visible in audit and case history only.
```

- [ ] **Step 3: Add "16.5 Programme Offer-Letter Export" after Section 16.4**

Find this exact text (the end of Section 16.4, immediately before `## 17. Applicant-To-Student Conversion Requirements`):

```
**FR-OFFER-033:** Declined offers shall release the occupied place for selection or waitlist processing according to institution rules.

## 17. Applicant-To-Student Conversion Requirements
```

Replace it with:

```
**FR-OFFER-033:** Declined offers shall release the occupied place according to institution rules.

### 16.5 Programme Offer-Letter Export

**FR-OFFER-040:** The Admissions table shall provide a `Print offer letters` action that opens a modal requiring an intake and a programme before export.

**FR-OFFER-041:** The export modal shall show the number of eligible published letters for the selected intake and programme before download.

**FR-OFFER-042:** Export shall include only the latest published document version for each current offer in the selected intake and programme. Drafts, generating or failed documents, withdrawn offers, superseded document versions, and deleted records shall be excluded.

**FR-OFFER-043:** Sent, accepted, declined, expired, and converted offers shall be treated as published records for export purposes. Email delivery failure shall not exclude an otherwise portal-published offer.

**FR-OFFER-044:** The system shall support exporting the selected offer letters as one merged, print-ready PDF ordered by applicant name and application number, or as one ZIP containing the individual PDFs with entries named using the application and offer numbers.

**FR-OFFER-045:** Programme offer-letter export is a read-only reporting operation. It shall never create, approve, decide, or dispatch an offer, change an offer or application status, publish a document, send an email, or introduce a batch lifecycle.

**FR-OFFER-046:** Every export shall record the requesting user, intake, programme, format, included document count, and timestamp as audit evidence.

**FR-OFFER-047:** Documents and Reporting shall maintain a programme, intake, publication timestamp, applicant ownership, and latest-version projection from an offer-publication event, so exports can be served without querying Admissions directly.

## 17. Applicant-To-Student Conversion Requirements
```

Note: FR-OFFER-033's original text referenced "selection or waitlist processing," which no longer exists after ADR-0014; the replacement text above shortens it to "according to institution rules" rather than leaving a dangling reference.

- [ ] **Step 4: Verify**

Run: `grep -n "FR-OFFER-0[0-4][0-9]\|^### 16" fresh-design/new-emhare-functional-requirements.md`
Expected: FR-OFFER-001 through FR-OFFER-047 all present exactly once (010–012 as superseded stubs), section headers read "16.2 Offer Batches (Retired)" and "16.5 Programme Offer-Letter Export", and `grep -n "waitlist" fresh-design/new-emhare-functional-requirements.md` no longer matches anywhere in Section 16.

---

### Task 5: Update functional requirements — offer email delivery (Section 29)

**Files:**
- Modify: `fresh-design/new-emhare-functional-requirements.md`

**Interfaces:**
- Consumes: ADR-0014's "Offer" and "Response" stage delivery requirements from the original spec (attachment email, Queued/Sent/Failed/Bounced status, retry, idempotent publish-and-send).
- Produces: FR-NOTIF-004 through FR-NOTIF-008, which the Notifications backend plan must implement exactly.

- [ ] **Step 1: Add new requirements after FR-NOTIF-003**

Find this exact text:

```
**FR-NOTIF-003:** Failed notification delivery shall not silently change business state.

## 30. Migration Requirements
```

Replace it with:

```
**FR-NOTIF-003:** Failed notification delivery shall not silently change business state.

**FR-NOTIF-004:** Offer dispatch shall email the generated offer-letter PDF as an attachment to the applicant's primary registered email only.

**FR-NOTIF-005:** Email delivery status shall be tracked as Queued, Sent, Failed, or Bounced, and shall be visible to Admissions staff on the offer.

**FR-NOTIF-006:** A failed or bounced offer email shall not remove or hide the applicant's portal-published offer letter; the portal copy shall remain available regardless of email delivery outcome.

**FR-NOTIF-007:** Admissions staff shall be able to retry a failed or bounced offer email through a controlled retry action, and the retry shall be recorded as separate delivery evidence.

**FR-NOTIF-008:** Publishing and sending an offer shall be idempotent: retrying the publish-and-send action after a partial failure shall not create a duplicate publication, a duplicate email, or a duplicate audit record for a delivery that already succeeded.

## 30. Migration Requirements
```

- [ ] **Step 2: Verify**

Run: `grep -n "FR-NOTIF-00[1-8]" fresh-design/new-emhare-functional-requirements.md`
Expected: FR-NOTIF-001 through FR-NOTIF-008 each present exactly once.

---

### Task 6: Update functional requirements — Release 1 scope line

**Files:**
- Modify: `fresh-design/new-emhare-functional-requirements.md`

**Interfaces:**
- Consumes: none beyond ADR-0014's terminology.
- Produces: none (leaf documentation change).

- [ ] **Step 1: Replace the "Selection rounds" scope bullet**

Find this exact text:

```
- Staff verification.
- Eligibility evaluation.
- Selection rounds.
- Offer generation and response.
```

Replace it with:

```
- Staff verification.
- Eligibility evaluation.
- Rolling per-applicant academic review and admission decisions (ADR-0014).
- Offer generation, response, and programme offer-letter export.
```

- [ ] **Step 2: Verify**

Run: `grep -n "Selection rounds\." fresh-design/new-emhare-functional-requirements.md`
Expected: no match remains anywhere in the file (the phrase existed only in this one bullet).

---

### Task 7: Update data model — quotas become non-gating (Admissions Setup)

**Files:**
- Modify: `fresh-design/core-and-admissions-data-model.md`

**Interfaces:**
- Consumes: ADR-0014.
- Produces: none (leaf documentation change).

- [ ] **Step 1: Add a note after the `admission_quotas` table spec**

Find this exact text:

```
`admission_quotas`
- `id`
- `intake_id`
- `programme_id`
- `quota_type_code`
- `capacity`
- `reserved_capacity`
- `notes`
- unique `intake_id`, `programme_id`, `quota_type_code`

### Applicant Profile
```

Replace it with:

```
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
```

- [ ] **Step 2: Verify**

Run: `grep -n "ADR-0014" fresh-design/core-and-admissions-data-model.md`
Expected: at least this occurrence is present (more will be added by later tasks in this plan).

---

### Task 8: Update data model — status enums for the rolling pipeline

**Files:**
- Modify: `fresh-design/core-and-admissions-data-model.md`

**Interfaces:**
- Consumes: ADR-0014's stage names.
- Produces: the `applications.status` and `application_programme_choices.choice_status` enum values that Task 9's new tables, and the downstream Admissions backend plan, must use verbatim: `applications.status` = `DRAFT, SUBMITTED, PAYMENT_PENDING, UNDER_REVIEW, INCOMPLETE, NOT_ELIGIBLE, UNDER_ACADEMIC_REVIEW, ADMITTED, REJECTED, OFFERED, ACCEPTED, DECLINED, WITHDRAWN, CONVERTED`; `application_programme_choices.choice_status` = `PENDING, ELIGIBLE, INELIGIBLE, REQUIRES_REVIEW, UNDER_ACADEMIC_REVIEW, ADMITTED, REJECTED, OFFERED`.

- [ ] **Step 1: Replace the `applications.status` enum line**

Find this exact text:

```
- `status` enum: `DRAFT`, `SUBMITTED`, `PAYMENT_PENDING`, `UNDER_REVIEW`, `INCOMPLETE`, `ELIGIBLE`, `NOT_ELIGIBLE`, `SHORTLISTED`, `SELECTED`, `OFFERED`, `ACCEPTED`, `DECLINED`, `WITHDRAWN`, `CONVERTED`
```

Replace it with:

```
- `status` enum: `DRAFT`, `SUBMITTED`, `PAYMENT_PENDING`, `UNDER_REVIEW`, `INCOMPLETE`, `NOT_ELIGIBLE`, `UNDER_ACADEMIC_REVIEW`, `ADMITTED`, `REJECTED`, `OFFERED`, `ACCEPTED`, `DECLINED`, `WITHDRAWN`, `CONVERTED` (per ADR-0014: `SHORTLISTED` and `SELECTED` are retired; `UNDER_ACADEMIC_REVIEW` and `ADMITTED` replace them)
```

- [ ] **Step 2: Replace the `application_programme_choices.choice_status` enum line**

Find this exact text:

```
- `choice_status` enum: `PENDING`, `ELIGIBLE`, `INELIGIBLE`, `SHORTLISTED`, `SELECTED`, `OFFERED`, `REJECTED`
```

Replace it with:

```
- `choice_status` enum: `PENDING`, `ELIGIBLE`, `INELIGIBLE`, `REQUIRES_REVIEW`, `UNDER_ACADEMIC_REVIEW`, `ADMITTED`, `REJECTED`, `OFFERED` (per ADR-0014: `SHORTLISTED` and `SELECTED` are retired; `REQUIRES_REVIEW` and `UNDER_ACADEMIC_REVIEW` are new, `ADMITTED` replaces `SELECTED`)
```

- [ ] **Step 3: Verify**

Run: `grep -n "SHORTLISTED\|SELECTED\`" fresh-design/core-and-admissions-data-model.md`
Expected: no remaining matches for the retired `SHORTLISTED`/`SELECTED` choice/application status values in this file (the `selection_decisions`/`academic_unit_recommendations` historical enums touched in Task 9 will still legitimately contain the word `SELECT` inside their own enum values until that task runs — if this grep is run before Task 9, that is expected and not a failure of this task).

---

### Task 9: Update data model — historical selection/offer tables plus new rolling-pipeline tables

**Files:**
- Modify: `fresh-design/core-and-admissions-data-model.md`

**Interfaces:**
- Consumes: Task 1's table/enum vocabulary (`academic_reviews`, `academic_recommendations`, `programme_choice_decisions`, `RECOMMEND_ADMIT`/`RECOMMEND_REJECT`, `ADMIT`/`REJECT`) and Task 8's `choice_status`/`applications.status` values.
- Produces: the exact column lists for `academic_reviews`, `academic_recommendations`, and `programme_choice_decisions` that the Admissions backend plan's JPA entities and migrations must match field-for-field.

- [ ] **Step 1: Replace the "Eligibility, Scoring, Selection" subsection from `selection_rounds` through the end of `academic_unit_recommendations`**

Find this exact block (starts right after the `application_clearances` paragraph, ends right before `### Payments`):

```
`selection_rounds`
- `id`
- `intake_id`
- `code`
- `name`
- `status` enum: `DRAFT`, `OPEN`, `APPROVED`, `CLOSED`
- `started_at`
- `closed_at`

`selection_decisions`
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

`academic_review_assignments`
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

The owning leaf and resolved highest unit are immutable workflow snapshots. Recommendation authority requires an active staff assignment at the exact `recommendation_academic_unit_id`.

`academic_unit_recommendations`
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

Recommendations are advisory. Only Admissions review creates a `selection_decision`; only an approved `SELECT` decision can create an offer.

### Payments
```

Replace it with:

```
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
```

- [ ] **Step 2: Verify**

Run: `grep -n "^\`academic_reviews\`\|^\`academic_recommendations\`\|^\`programme_choice_decisions\`\|historical — read-only from ADR-0014" fresh-design/core-and-admissions-data-model.md`
Expected: all three new table headers print exactly once each; the "historical — read-only" annotation prints exactly four times (once per retired table).

---

### Task 10: Update data model — offers section and implementation order

**Files:**
- Modify: `fresh-design/core-and-admissions-data-model.md`

**Interfaces:**
- Consumes: Task 9's `programme_choice_decisions` table (offers now reference an admission decision, not a `selection_decision`).
- Produces: the note on `offers.offer_batch_id` and the offer-publication-event pointer that the Documents backend plan's projection design must satisfy.

- [ ] **Step 1: Mark `offer_batches` historical and annotate `offers.offer_batch_id`**

Find this exact block:

```
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
```

Replace it with:

```
`offer_batches` (historical — read-only from ADR-0014 onward)
- `id`
- `intake_id`
- `selection_round_id`
- `code`
- `scope_type` enum: `INSTITUTION`, `ACADEMIC_UNIT`, `PROGRAMME`
- `scope_id` nullable
- `status` enum: `DRAFT`, `APPROVED`, `DISPATCHED`, `CLOSED`
- `approved_by_user_id`
- `approved_at`

Preserved for audit and case history; no new offer batch is created after ADR-0014.

`offers`
- `id`
- `application_id`
- `programme_choice_id`
- `offer_batch_id` nullable — always null for offers created after ADR-0014; retained only so historical batch-scoped offers keep their link
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
- `generated_document_id` — references the latest published document version projected from Documents and Reporting; full version history is owned by Documents and Reporting, not Admissions
- unique `offer_number`
- unique active offer guard on `application_id`, `programme_id`

Per ADR-0014, an offer is created directly from an `ADMIT` `programme_choice_decisions` row, with no `offer_batch_id`. Regenerating the offer letter creates a new document version in Documents and Reporting and republishes `generated_document_id` to point at it; it does not create a new `offers` row. Publishing an offer emits an offer-publication event so Documents and Reporting can maintain its own programme/intake/publication-timestamp/applicant-ownership/latest-version export projection without querying Admissions directly (see FR-OFFER-047).
```

- [ ] **Step 2: Update the Suggested Implementation Order line**

Find this exact text:

```
8. Requirement sets, subject requirements, evaluations, selection rounds/decisions.
```

Replace it with:

```
8. Requirement sets, subject requirements, evaluations, academic reviews/recommendations, programme choice decisions (selection rounds/decisions preserved as historical predecessor tables per ADR-0014, not part of new implementation work).
```

- [ ] **Step 3: Verify**

Run: `grep -n "offer_batches\` (historical\|generated_document_id.*latest published\|selection rounds/decisions preserved as historical" fresh-design/core-and-admissions-data-model.md`
Expected: all three lines print exactly once.

---

### Task 11: Self-review pass across all four files

**Files:**
- Read (no modification): `fresh-design/adrs/0014-rolling-per-applicant-admissions-processing.md`, `fresh-design/adrs/README.md`, `fresh-design/new-emhare-functional-requirements.md`, `fresh-design/core-and-admissions-data-model.md`

**Interfaces:**
- Consumes: the complete output of Tasks 1–10.
- Produces: a pass/fail confirmation that this plan's deliverable is internally consistent, gating whether the plan is ready for the user's review step described in the writing-plans skill.

- [ ] **Step 1: Confirm no stale references to retired concepts outside their superseded/historical markers**

Run:
```bash
grep -n "selection round\|offer batch\|shortlist\|waitlist" fresh-design/new-emhare-functional-requirements.md | grep -vi "superseded\|retired\|historical"
```
Expected: no output. If any line prints, it is a live (non-superseded) requirement still describing the retired mechanism — fix it by applying the same superseded-pointer pattern used in Task 3/4 before proceeding.

- [ ] **Step 2: Confirm requirement ID sequences have no duplicates**

Run:
```bash
grep -oE "FR-SEL-[0-9]+|FR-OFFER-[0-9]+|FR-NOTIF-[0-9]+|FR-ADM-[0-9]+" fresh-design/new-emhare-functional-requirements.md | sort | uniq -c | sort -rn | head -5
```
Expected: every count is `1`. Any count greater than `1` means an ID was duplicated instead of extended (e.g. two different `FR-SEL-035` lines) — fix before proceeding.

- [ ] **Step 3: Confirm the data model's three new tables and four historical tables all cross-reference ADR-0014**

Run:
```bash
grep -c "ADR-0014" fresh-design/core-and-admissions-data-model.md
```
Expected: at least `7` (one for the quotas note, four for the "historical — read-only" table annotations, plus the `academic_reviews`/`academic_recommendations`/`programme_choice_decisions` explanatory paragraphs and the offers-section paragraph). If lower, revisit Tasks 7, 9, and 10.

- [ ] **Step 4: Confirm the ADR file and README are mutually consistent**

Run:
```bash
grep -n "^# ADR-0014" fresh-design/adrs/0014-rolling-per-applicant-admissions-processing.md
grep -n "ADR-0014" fresh-design/adrs/README.md
```
Expected: both commands print exactly one matching line, and the ADR title text in both files reads "Rolling per-applicant admissions processing" identically.

- [ ] **Step 5: Report results**

Summarize which of Steps 1–4 passed or failed. Do not proceed to any downstream plan (Admissions backend, Documents backend, Notifications backend, admin-portal frontend, applicant-portal frontend) until every step in this task passes.

---

### Task 12: Close residual stale references found during Task 11's self-review

**Added 2026-08-10, mid-execution.** Task 11's Step 1 grep, run across the *whole* functional-requirements document rather than just the sections Tasks 2–6 edited, found two live (non-superseded) requirements in Section 10.1 and one now-moot open question in Section 34 that still describe selection rounds and offer batches as active concepts. None of these were in the original Task 2–6 scope — they were missed when this plan was first written. This is a real documentation-consistency gap, not a contestable style choice: FR-ADM-004 in particular flatly contradicts the rewritten Section 14.3/16 by stating selection/offer lifecycle states "belong to selection rounds, offer batches, and offers," which is no longer true after ADR-0014.

**Files:**
- Modify: `fresh-design/new-emhare-functional-requirements.md`

**Interfaces:**
- Consumes: ADR-0014, and the exact successor-table vocabulary from Task 9 (`academic_reviews`, `academic_recommendations`, `programme_choice_decisions`).
- Produces: none (leaf documentation change).

- [ ] **Step 1: Amend FR-ADM-002**

Find this exact text:

```
**FR-ADM-002:** Every intake shall belong to an academic year and shall directly scope applications, requirements, quotas, selection rounds, and offers.
```

Replace it with:

```
**FR-ADM-002:** Every intake shall belong to an academic year and shall directly scope applications, requirements, quotas, and offers. Per ADR-0014, admissions processing (verification, eligibility, academic review, admission decision) is scoped directly to the application and programme choice, not to an intake-scoped selection round.
```

- [ ] **Step 2: Amend FR-ADM-004**

Find this exact text:

```
**FR-ADM-004:** Intakes shall support draft, open, closed, and archived statuses. Selection and offer lifecycle states shall belong to selection rounds, offer batches, and offers.
```

Replace it with:

```
**FR-ADM-004:** Intakes shall support draft, open, closed, and archived statuses. Per ADR-0014, selection and offer lifecycle states shall belong to academic reviews, academic recommendations, programme choice decisions, and offers — not to selection rounds or offer batches, which are retired and preserved only as historical records.
```

- [ ] **Step 3: Resolve the stale open question in Section 34**

Find this exact text:

```
- Which staff roles approve offer batches in the first implementation?
```

Replace it with:

```
- *(Resolved by ADR-0014: offer batches are retired; there is no batch approval step.)*
```

- [ ] **Step 4: Verify**

Run:
```bash
grep -n "selection round\|offer batch\|shortlist\|waitlist" fresh-design/new-emhare-functional-requirements.md | grep -vi "superseded\|retired\|historical\|resolved by adr-0014"
```
Expected: no output, except the already-known negation-clause uses inside FR-SEL-020, FR-SEL-022, FR-SEL-027, and FR-OFFER-001 (these are correct as specified in Tasks 3 and 4 — they describe what no longer happens, not stale live functionality). If any other line appears, stop and report it rather than fixing it inline.

## What This Plan Deliberately Does Not Do

- It does not write any migration, entity, controller, or Vue file. Those belong to the five downstream plans this Foundation plan unblocks (Admissions backend; Documents backend; Notifications backend; admin-portal frontend; applicant-portal frontend), each to be scoped and written separately once this plan is reviewed and accepted.
- It does not run `mvn flyway:info` — that check applies to migration authoring, which happens in the Admissions/Documents/Notifications backend plans, not here.
- It does not add SMTP/Mailpit to `docker-compose.yml` — that is Notifications backend plan work.
- It does not touch `admissions-service`, `documents-reporting-service`, or `notifications-service` source code in any way.
