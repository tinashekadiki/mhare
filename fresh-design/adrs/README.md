# eMhare Architecture Decision Records

Author: Tinashe K

This folder records the architecture decisions for the new eMhare before implementation starts. These ADRs are binding for the first code phase unless a later ADR supersedes them.

## Index

| ADR | Title | Status |
| --- | --- | --- |
| [ADR-0001](0001-single-institution.md) | Single-institution eMhare | Accepted |
| [ADR-0002](0002-split-service-architecture.md) | Split-service architecture | Accepted |
| [ADR-0003](0003-service-owned-migrations-and-boundaries.md) | Service-owned migrations and data boundaries | Accepted |
| [ADR-0004](0004-business-table-audit-standard.md) | Business table audit and Envers standard | Accepted |
| [ADR-0005](0005-preserve-legacy-baseline-requirements.md) | Preserve legacy-baseline requirements | Accepted |
| [ADR-0006](0006-module-terminology-and-academic-structure.md) | Module terminology and configurable academic structure | Accepted |
| [ADR-0007](0007-applicant-authentication-and-application-fees.md) | Applicant authentication and application-fee gate | Accepted |
| [ADR-0008](0008-admissions-rules-model.md) | Admissions rules model | Accepted |
| [ADR-0009](0009-generated-documents-for-official-outputs.md) | Generated documents for official outputs | Accepted |
| [ADR-0010](0010-open-source-technology-stack.md) | Open-source technology stack | Accepted |
| [ADR-0011](0011-object-storage-reference-implementation.md) | Object storage reference implementation | Accepted |
| [ADR-0012](0012-intakes-as-the-sole-admissions-window.md) | Intakes as the sole admissions window | Accepted |
| [ADR-0013](0013-application-payment-evidence-and-hosted-card-payments.md) | Application payment evidence and hosted card payments | Accepted |
| [ADR-0014](0014-rolling-per-applicant-admissions-processing.md) | Rolling per-applicant admissions processing | Accepted |
| [ADR-0015](0015-standard-microservice-architecture.md) | Standard microservice architecture | Accepted |

## Rules

- Do not start implementation that conflicts with an accepted ADR.
- If an implementation need conflicts with an ADR, write a new ADR that supersedes the old one before changing code.
- Enhancements must preserve the extracted legacy-baseline requirements unless an ADR explicitly supersedes a requirement with a replacement and rationale.
