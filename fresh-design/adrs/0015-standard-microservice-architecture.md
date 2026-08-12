# ADR-0015: Standard microservice architecture

Author: Tinashe K

## Status

Accepted

## Context

eMhare is a split-service platform, but the first implementation phase used broad layer packages, a single shared library, fixed internal service URLs, and hand-built `RestClient` instances. A standard is required before the services grow further.

Spring Cloud OpenFeign is feature-complete and is not the standard for this new project. Spring Framework HTTP Service Clients provide declarative blocking and reactive clients, and Spring Cloud LoadBalancer 5 integrates those clients with service discovery.

## Decision

### Repository and package organisation

Business services shall be organised by capability first. Each capability may contain `api`, `application`, `domain`, and `infrastructure` packages. Infrastructure may be divided into `persistence`, `client`, and `messaging` adapters.

- HTTP controllers shall live only in `<capability>.api.controller`.
- HTTP request and response models shall live in `<capability>.api.model`; they shall never be declared inside a controller class.
- HTTP input types shall use `Request` terminology. A type named `Command` is an internal use-case input and shall live only in `<capability>.application.command`.
- Controllers may map a validated request into an application command, but a controller method shall never bind a command directly as an HTTP parameter.
- Controllers handle transport, validation, and response mapping only.
- Application services own use cases and transaction boundaries.
- Business entities, value types, domain policies, and domain events shall live below `<capability>.domain`; JPA business entities and their enums shall use `<capability>.domain.model`.
- Spring Data repository interfaces and persistence adapters shall live below `<capability>.infrastructure.persistence`; repositories shall never be declared beside entities or in `domain`.
- Technical persistence models are explicit exceptions to business entity placement: inbox and outbox entities use `<context>.infrastructure.messaging.model`, reporting projections use `<context>.infrastructure.persistence.projection.model`, and generated-document job metadata that maintains JPA links to reporting projections uses `<capability>.infrastructure.persistence.model`. Their repositories still use an `infrastructure.persistence` package.
- Domain code shall not depend on `api`, `application`, `infrastructure`, controllers, HTTP clients, broker listeners, or another service's domain model.
- JPA entities shall not be exposed as API responses.
- Synchronous client DTOs are consumer-owned and shall not be shared between services.
- Shared libraries contain technical primitives or versioned integration contracts only.

The transitional `service-common` library shall be replaced incrementally by:

- `service-foundation` for security, correlation, API errors, and standard service configuration;
- `persistence-audit` for the audited entity and Envers infrastructure;
- `integration-contracts` for versioned RabbitMQ event contracts;
- `test-support` for architecture rules and shared test utilities.

### Discovery and routing

Spring Cloud Netflix Eureka is the service registry for Docker Compose and VM deployments. Local development uses one registry. Production uses two peer-aware instances behind private networking and TLS. Kubernetes-native discovery requires a later ADR.

Every service registers with its existing `spring.application.name`. The API Gateway keeps an explicit public route allowlist and routes to `lb://<service-name>`. Automatic discovery route exposure is prohibited.

### Synchronous and asynchronous integration

Internal synchronous calls use Spring HTTP Service Client interfaces backed by `RestClient` for the blocking MVC services. Client groups use `lb://` service IDs and Spring Cloud LoadBalancer. OpenFeign dependencies are prohibited by the parent build.

Client interfaces live in consumer infrastructure packages and are wrapped by adapters implementing application-facing ports. Current user calls relay the bearer token. Background calls that require an immediate response use Keycloak client credentials. Direct `RestClient` remains appropriate for external infrastructure such as Keycloak.

RabbitMQ with transactional outbox/inbox remains the standard for asynchronous business workflows. No synchronous client may replace an event merely to avoid durable messaging.

### Resilience and observability

- Default HTTP connection and read timeouts are two and five seconds respectively.
- Automatic retries are disabled for commands. A single controlled retry is allowed only for idempotent reads; retried commands require an idempotency key.
- Spring Cloud CircuitBreaker with Resilience4j protects synchronous dependency calls.
- Fallbacks must not invent business data. Network and provider `5xx` failures are represented as correlation-aware dependency-unavailable errors.
- Actuator, Micrometer, OpenTelemetry, Prometheus-compatible metrics, and structured logs carry service, trace, span, correlation, and actor context.

Spring Cloud Config and central secret management are deferred. Configuration uses typed properties, profiles, environment variables, and deployment secrets.

## Consequences

- Service instances can move or scale without changing consumer URLs.
- Explicit gateway routes continue to protect internal APIs.
- Business code is insulated from the HTTP client implementation.
- Existing and new services use one visible API boundary convention, enforced by shared architecture tests.
- Eureka becomes production infrastructure and must be monitored and deployed redundantly.

## Verification

- Maven Enforcer rejects OpenFeign and direct service-artifact dependencies.
- ArchUnit verifies domain isolation, service boundaries, controller and command placement, business and technical entity placement, and repository placement.
- Contract tests verify internal HTTP and RabbitMQ payloads.
- Topology tests verify registration, load-balanced routing, removal, and recovery.
- Failure tests cover timeouts, circuit opening, and recovery without fabricated responses.
