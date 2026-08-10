# ADR-0011: Object storage reference implementation

Author: Tinashe K

## Status

Accepted

## Context

ADR-0010 selected an S3-compatible object storage API and named Garage as the open-source self-hosted reference implementation. eMhare needs object storage for applicant documents, generated official records, receipts, exports, archived imports, and evidence attachments.

The storage layer must be self-hostable, open-source, easy to operate, compatible with Java S3 clients, and legally straightforward for institutional deployments.

## Decision

The eMhare application shall depend on the S3-compatible API, not on a specific object-storage product.

RustFS shall be the default self-hosted reference implementation for new eMhare deployments.

Garage remains an approved alternative deployment target where its resilience model, operational footprint, or geo-distributed design is a better fit.

This ADR supersedes the Garage default named in ADR-0010.

## Rationale

| Criterion | RustFS | Garage |
| --- | --- | --- |
| License | Apache 2.0 | AGPL-3.0 |
| Primary fit | High-performance S3-compatible object storage with MinIO-style deployment patterns | Lightweight resilient storage for small-to-medium and geo-distributed clusters |
| Java integration | Use AWS SDK for Java v2 against the S3 endpoint | Use AWS SDK for Java v2 against the S3 endpoint |
| Production topology | Multi-node multi-disk mode documented for production workloads | Replicated cluster model designed for resilience across machines and locations |
| Operational familiarity | Closer to MinIO-style object-storage operations | Distinct Garage administration model |
| Legal simplicity for bundled deployments | Stronger because Apache 2.0 is permissive | Requires AGPL review, especially if modified or redistributed |
| eMhare fit | Strong default | Strong alternative |

RustFS is the better default for eMhare because it is open-source, S3-compatible, has permissive licensing, documents Docker, Podman, Linux, Kubernetes, high availability, audit logs, observability, OIDC, and Java SDK integration, and avoids AGPL friction for bundled institutional deployment.

Garage is not rejected. It remains useful where the deployment values lightweight geo-distributed resilience over RustFS' MinIO-style operational model.

## Consequences

- eMhare services must use an internal storage abstraction backed by an S3 client.
- No service shall call RustFS-specific APIs for normal document storage.
- Deployment templates may include RustFS by default.
- Garage, MinIO, Ceph, AWS S3, and other S3-compatible implementations may be supported through configuration.
- Production deployments must not use single-node/single-disk storage for critical records unless an explicit backup and recovery plan is approved.

## Implementation Notes

- Use separate buckets or prefixes for applicant uploads, generated documents, finance receipts, exports, imports, and temporary files.
- Store object metadata in the owning service database: bucket, object key, content type, size, checksum, storage provider, version ID where supported, retention state, and audit fields.
- Store sensitive documents encrypted at rest.
- Virus/malware scanning must be added before documents are trusted by admissions, finance, or records workflows.
- Generated official records must remain immutable after issue; corrections should create replacement documents with traceability.
- Local development may run RustFS as a single-node container.
- Production should use RustFS multi-node multi-disk mode or another approved redundant S3-compatible backend.

## Reference Checks

Reference checks were made on 2026-08-06 against official or primary project sources:

- RustFS documentation describes it as a distributed S3-compatible object storage system released under Apache 2.0.
- RustFS documentation includes Docker, Podman, Linux, Kubernetes, high availability, observability, audit targets, OIDC, and Java SDK guidance.
- Garage documentation describes it as an S3-compatible object store focused on resilience, lightweight operation, and replication across machines or locations.
- Garage source documentation identifies AGPL-3.0 licensing.
