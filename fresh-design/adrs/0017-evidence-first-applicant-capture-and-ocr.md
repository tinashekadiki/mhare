# ADR-0017: Evidence-First Applicant Capture And OCR

Author: Tinashe K

Date: 2026-08-23

Status: Accepted

## Context

Applicant evidence was captured in a generic supporting-documents step and qualification sittings could be saved before their evidence and results. This made the journey harder to understand and allowed incomplete qualification headers. Uploaded identity and qualification documents also required applicants to retype information that a self-hosted OCR service can safely propose.

## Decision

- Documents and Reporting owns uploaded files, malware scanning, OCR jobs, extracted content, retry state, and the Docling integration.
- Admissions owns applicant data, managed subjects, qualification aggregates, subject matching, and the applicant's final saved values.
- OCR uses a self-hosted Docling Serve v1.29.0 service with RapidOCR. The Java integration uses the official `ai.docling` client line and is pinned to the nearest published compatible artifact; upgrades require the compatibility contract test to remain green.
- OCR starts only after content inspection, malware scanning, and successful S3-compatible storage.
- OCR runs asynchronously, retries transient failures no more than three times, and never writes extracted document text or personal values to application logs.
- OCR output is a proposal. Admissions persists no extracted personal or qualification value until the applicant explicitly saves the editable form.
- Extracted first and last names never overwrite the authenticated registration identity. A mismatch is returned as a structured comparison and remains visible until the applicant replaces the draft evidence, corrects the OCR reading, or requests an official-name correction.
- Admissions owns the evidence-backed name-correction request and the application official-name snapshot. Core and Identity owns account and Keycloak synchronization. Staff approval calls Core idempotently, then records the approved Admissions snapshot; both services retain Envers history and a source-request audit record.
- Pending identity evidence may be replaced while its application remains a draft. Verified evidence remains protected, and rejected evidence remains replaceable under the existing correction workflow.
- Required evidence is placed in its business section: identity evidence in Personal Details and one evidence document in each Qualification aggregate. Payment and confidential reference evidence keep their existing owners.
- Application-route document rules carry a capture-section code and applicant-category applicability. The selected applicant category is immutable after the application draft snapshots its fee and evidence rules.
- O Level and A Level qualification capture saves the sitting, document reference, and all result rows atomically. A header-only applicant qualification is not permitted.
- Historical submitted applications are preserved. Open drafts recognise the legacy `IDENTITY_DOCUMENT` and academic-qualification evidence codes as compatibility aliases.

## Consequences

- The applicant portal can unlock editable fields after evidence upload even when OCR fails, while showing a manual-entry warning.
- Documents and Reporting stores sensitive extraction evidence, so access remains owner- or staff-authorised and retention must follow institutional policy.
- Representative redacted documents are required before OCR accuracy can be called intake-ready; synthetic fixtures demonstrate integration only.
- A pending official-name correction does not block completion of the application draft. Approval updates the applicable application snapshot, applicant profile, Core user, and Keycloak; rejection preserves the registered identity and records the staff reason.
