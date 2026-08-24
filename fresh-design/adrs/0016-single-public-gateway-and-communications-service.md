# ADR-0016: Single public gateway and Communications service

Author: Tinashe K

## Status

Accepted

## Context

eMhare needs one recognisable public entry point for applicants, students, staff, and public institutional content. Separate portal home pages would duplicate news, notices, events, campaigns, and important links, while placing editorial publishing in Notifications would mix content governance with delivery infrastructure.

Academic Setup already owns academic years, periods, intakes, and the institutional academic calendar. Notifications owns addressed email, SMS, and in-application delivery. Neither boundary owns public editorial publishing.

## Decision

The student Nuxt application owns the public eMhare gateway at `/` and the public detail routes `/news/{slug}`, `/notices/{slug}`, `/events/{slug}`, and `/campaigns/{slug}`. Its authenticated student workspace begins at `/student`. The applicant and staff applications use `/applicant` and `/staff` respectively. The Spring API Gateway remains under `/api`.

Production uses one origin and an edge proxy with explicit route precedence: `/api/**`, `/applicant/**`, `/staff/**`, `/student/**` and public content routes, then `/`. Portal destination cards are code-owned. Governed secondary resources are Communications `LINK` items.

A `communications-service` owns categories, structured content, immutable approved versions, four-eye review, publication windows, editorial events, media metadata, public event occurrences, and authenticated read receipts. It registers with Eureka, uses database `emhare_communications`, listens on port `8092`, and is exposed only through the explicit gateway route `/api/communications/**`.

Communications content uses schema-versioned JSON blocks from a fixed allowlist and never stores raw HTML. Content kinds are `NEWS`, `NOTICE`, `ALERT`, `CAMPAIGN`, `LINK`, and `EVENT`. The workflow is `DRAFT -> IN_REVIEW -> APPROVED` or `REJECTED`; an author cannot approve their own version, and corrections create a new version. Publications are `SCHEDULED`, `LIVE`, `EXPIRED`, or `WITHDRAWN` and target the single public gateway only.

Academic Setup retains ownership of academic calendars. Communications may present public editorial events but does not become the source of truth for registration, examination, intake, or teaching dates. Notifications retains delivery ownership; future addressed distribution consumes an approved Communications publication through an explicit event or API contract.

## Consequences

- Public information has one canonical surface and is not repeated across portal landings.
- Existing sessions never bypass `/`; authentication starts only at the protected portal route.
- Frontend deployments require path-aware base URLs and Keycloak callbacks.
- Editorial access uses separate author and approver permissions and preserves immutable workflow evidence.
- A future standalone public application would require an ADR that supersedes this decision.

## Verification

- Migration tests enforce all business and Envers audit tables, constraints, and append-only workflow evidence.
- Application tests cover four-eye approval, immutable approved versions, publication windows, withdrawal, timezone-aware events, calendar generation, media validation, and idempotent read receipts.
- Gateway tests prove public read access and protected editorial access.
- Desktop and mobile browser tests prove public loading, portal routing, callback paths, deep links, calendar download, and accessible overflow-free layouts.
