# ADR-0007: Applicant authentication and application-fee gate

Author: Tinashe K

## Status

Accepted

## Context

The applicant workflow needs continuity, document uploads, payment references, qualification evidence, offer responses, and eventual applicant-to-student conversion. Anonymous applications would make ownership, audit, resumption, and offer response weaker.

Applicants are also required to pay application fees.

## Decision

Applicants must sign up or log in before they can start, edit, submit, or respond to an application.

Fee-required applications cannot enter admissions review, eligibility evaluation, or selection until the application fee is confirmed or an authorised waiver/override is recorded.

## Consequences

- Applications are never anonymous.
- `applicants.user_id` is mandatory.
- The applicant portal must expose fee amount, payment reference, payment state, and next action.
- Admissions must consume Finance payment state before progressing the application.

## Implementation Notes

- Applicant identity belongs to Core/Identity.
- Application fee references, payment confirmations, receipts, exchange rates, and finance posting belong to Finance.
- Admissions stores payment state needed for workflow gating, but Finance remains the source of truth for payment details.
- ZWG payments must use an available effective exchange rate. If no rate exists, the payment remains unrated for finance review.
