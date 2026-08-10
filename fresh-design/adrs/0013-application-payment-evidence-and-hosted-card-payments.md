# ADR-0013: Application payment evidence and hosted card payments

Author: Tinashe K

## Status

Accepted

## Context

Applicants need two application-fee paths: upload bank payment evidence for independent Finance reconciliation, or pay by card through CBZ's iVeri Lite facility. Card details must not pass through eMhare, and neither a document upload nor a browser return can by itself clear the admissions payment gate.

## Decision

- Proof of payment is a Finance record stored through the shared S3-compatible document service using the `FINANCE_RECORD` owner type and `PROOF_OF_PAYMENT` document type.
- Finance remains the owner of payment reconciliation and the source of the payment status consumed by Admissions.
- iVeri Lite is integrated as a configurable hosted-payment provider behind a Finance provider boundary. The applicant portal uses the provider's LiteBox flow so checkout remains over the current application page; eMhare never captures card data.
- Applicant-facing labels are provider-neutral: `Make payment` and `Pay now`. Provider names and implementation details remain internal to Finance.
- Provider attempts are immutable, auditable Finance records with a unique merchant trace.
- Merchant credentials, the shared secret, gateway address, and return URLs are external configuration. The online option is unavailable until all required configuration is present.
- A successful browser return is not sufficient payment confirmation. Finance must verify the provider transaction response or query the provider status before calling the existing idempotent reconciliation contract.

## Consequences

- Manual evidence can be submitted immediately without coupling Finance to an object-storage implementation.
- Verified document evidence and a reconciled payment remain distinct controls.
- The CBZ iVeri Lite adapter can be activated when CBZ supplies test and live Application IDs, shared secrets, return URLs, and merchant acceptance details.
- Further providers can implement the same checkout boundary without changing applicant workflow code.

## Implementation Notes

- The contained checkout uses the provider-hosted LiteBox iframe and sends the server-generated transaction parameters using the provider's documented `postMessage` contract.
- Amounts are supplied in minor units. eMhare continues to use USD as base currency and never substitutes a rate of 1 for ZWG.
- Provider return handling must validate the unique merchant trace and random per-attempt nonce, then perform an authoritative status check before payment confirmation.

## Reference Checks

Reference checks were made on 2026-08-10 against the supplied Full Redirect document and the official iVeri developer and BackOffice guides. The official developer guide distinguishes Full Redirect from LiteBox and documents LiteBox as the contained merchant-site checkout. It also describes the hosted gateway address, server-side hash-token generation, four result URLs, merchant trace status queries, and test-to-live merchant activation.
