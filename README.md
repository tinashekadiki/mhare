# eMhare

Author: Tinashe K

eMhare is a higher-education administration platform for the full student lifecycle: institutional setup, admissions, student records, registration, finance, assessment, examinations, results, accommodation, dining, official documents, and notifications.

This repository contains the fresh split-service implementation. The CakePHP application in `sparse-emhare/` is a read-only requirements baseline, not the application to run or extend.

## Start here

Use this path when setting up a development environment for the first time:

1. [Install the prerequisites](#prerequisites).
2. [Configure and start the infrastructure](#1-configure-the-local-environment).
3. [Build and start the backend](#3-build-the-backend).
4. [Create the first system administrator](#5-create-the-first-system-administrator).
5. [Start the required portal](#6-start-the-frontends).
6. [Verify the environment](#7-verify-the-environment).

## Architecture at a glance

```text
Admin, applicant, and student browsers
                  |
          Nuxt applications
                  |
      API Gateway :8080 + Keycloak :8099
                  |
  +---------------+----------------+----------------+
  | Core | Academic | Admissions | Finance | Student |
  | Assessment | Exams | Accommodation | Dining      |
  | Documents/Reporting | Notifications              |
  +---------------+----------------+----------------+
                  |
  Service-owned PostgreSQL databases, RabbitMQ,
  Valkey, and S3-compatible RustFS object storage
```

Each service owns its database and Flyway migration history. Services exchange data through APIs and events; they must not write directly to another service's database.

## Prerequisites

Install the following tools:

| Tool | Required version | Purpose |
| --- | --- | --- |
| JDK | 21 | Spring Boot services and Maven tests |
| Maven | 3.9 or newer | Backend build and service startup |
| Node.js | 24.11.0 recommended | Nuxt applications and Nx workspace |
| npm | 11 or newer | Frontend dependencies and scripts |
| Docker Desktop or Docker Engine | Current supported release | PostgreSQL, Keycloak, RabbitMQ, Valkey, RustFS, and Testcontainers |
| Docker Compose | Compose v2 or newer | Local infrastructure orchestration |
| curl | Current release | Health checks |

The repository pins Node.js in `.nvmrc` and `.node-version`.

Confirm the toolchain before continuing:

```bash
java -version
mvn -version
node --version
npm --version
docker --version
docker compose version
```

`java -version` and the Java version reported by Maven must both be 21. On macOS, select JDK 21 with:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

If you use `nvm`, select the repository's Node.js version with:

```bash
nvm install
nvm use
```

## Local development tutorial

### 1. Configure the local environment

From the repository root, create the local environment file:

```bash
cp .env.example .env
```

The supplied values are development defaults only. Do not reuse them in a shared, test, staging, or production environment.

Docker Compose reads `.env` automatically. If you change values that are also consumed by a Spring service, export them in every terminal used to start that service:

```bash
set -a
source .env
set +a
```

### 2. Start PostgreSQL, Keycloak, RabbitMQ, Valkey, and RustFS

```bash
make infra-up
```

This is equivalent to:

```bash
docker compose up -d postgres rabbitmq valkey rustfs keycloak
```

Verify the containers:

```bash
docker compose ps
docker compose exec postgres pg_isready -U postgres -d emhare_admin
curl -fsS http://localhost:8099/realms/emhare/.well-known/openid-configuration
```

The PostgreSQL initialization script creates one private database for each service. It runs only when the PostgreSQL data volume is created for the first time.

### 3. Build the backend

Install the parent project and shared service library into the local Maven repository:

```bash
mvn -DskipTests install
```

Every business service validates its schema and applies its own pending Flyway migrations when it starts.

### 4. Start the backend services

For the smallest working admin environment, open three terminals and run:

```bash
# Terminal 1
mvn -f services/core-identity-service/pom.xml spring-boot:run
```

```bash
# Terminal 2
mvn -f services/api-gateway/pom.xml spring-boot:run
```

```bash
# Terminal 3
npm install
npm run admin:dev
```

You can now open [http://localhost:3000](http://localhost:3000). Continue with the first-operator setup below before signing in.

For complete end-to-end workflows, start every domain service in its own terminal. Start the API Gateway after the domain services:

```bash
mvn -f services/core-identity-service/pom.xml spring-boot:run
mvn -f services/academic-setup-service/pom.xml spring-boot:run
mvn -f services/admissions-service/pom.xml spring-boot:run
mvn -f services/finance-service/pom.xml spring-boot:run
mvn -f services/student-records-service/pom.xml spring-boot:run
mvn -f services/assessment-results-service/pom.xml spring-boot:run
mvn -f services/exams-timetabling-service/pom.xml spring-boot:run
mvn -f services/accommodation-service/pom.xml spring-boot:run
mvn -f services/dining-service/pom.xml spring-boot:run
mvn -f services/documents-reporting-service/pom.xml spring-boot:run
mvn -f services/notifications-service/pom.xml spring-boot:run
mvn -f services/api-gateway/pom.xml spring-boot:run
```

Do not paste that block into one terminal: each command is a long-running process.

### 5. Create the first system administrator

The imported `emhare` realm contains roles and clients but intentionally contains no personal user account.

1. Open [http://localhost:8099/admin](http://localhost:8099/admin).
2. Sign in with the local Keycloak bootstrap credentials from `.env`. The defaults are `admin` / `admin`.
3. Select the **emhare** realm.
4. Open **Users**, choose **Create new user**, and enter the operator's username and email.
5. Enable **Email verified** and save the user.
6. Open **Credentials**, set a password, and turn **Temporary** off.
7. Open **Role mapping**, choose **Assign role**, and assign the `system-admin` realm role.
8. Sign in at [http://localhost:3000](http://localhost:3000).

The first authenticated request to `/api/core/me` synchronizes the Keycloak identity into the Core Identity service. The `system-admin` realm role grants the initial operator access to governed setup screens. After changing a Keycloak role, sign out and sign in again so the new role is included in the access token.

### 6. Start the frontends

Install frontend dependencies once:

```bash
npm install
```

Run each required application in a separate terminal:

| Application | Command | URL |
| --- | --- | --- |
| Admin portal | `npm run admin:dev` | [http://localhost:3000](http://localhost:3000) |
| Applicant portal | `npm run applicant:dev` | [http://localhost:3001](http://localhost:3001) |
| Student portal | `npm run student:dev` | [http://localhost:3002](http://localhost:3002) |
| UI workbench | `npm run workbench:dev` | [http://localhost:3003](http://localhost:3003) |

All browser API traffic must go through the API Gateway at `http://localhost:8080`.

### 7. Verify the environment

Verify the gateway topology and all services:

```bash
make backend-health
```

Expected result: every service is `UP` on its canonical port and Gateway readiness is `UP`. The check explicitly reports a `MISROUTED` service when it is healthy on a common alternate port such as `18084` but the Gateway expects `8084`.

Then verify the browser flow:

1. Open the admin portal.
2. Sign in through Keycloak.
3. Confirm that the Operations workspace loads without a 401 or 403 error.
4. Open **Core Identity** and confirm the synchronized operator appears under **Users**.

## Service reference

### Backend services

| Port | Service | Database |
| --- | --- | --- |
| 8080 | API Gateway | None |
| 8081 | Core Identity | `emhare_core_identity` |
| 8082 | Academic Setup | `emhare_academic_setup` |
| 8083 | Admissions | `emhare_admissions` |
| 8084 | Finance | `emhare_finance` |
| 8085 | Student Records and Registration | `emhare_student_records` |
| 8086 | Assessment and Results | `emhare_assessment_results` |
| 8087 | Exams and Timetabling | `emhare_exams_timetabling` |
| 8088 | Accommodation | `emhare_accommodation` |
| 8089 | Dining | `emhare_dining` |
| 8090 | Documents and Reporting | `emhare_documents_reporting` |
| 8091 | Notifications | `emhare_notifications` |
| 8092 | Communications | `emhare_communications` |

### Infrastructure

| Port | Component | Local purpose |
| --- | --- | --- |
| 5433 | PostgreSQL 18 | Service-owned databases (remapped from 5432 to avoid clashing with any native/host Postgres) |
| 5672 | RabbitMQ | Integration events and background work |
| 15672 | RabbitMQ management | Local queue inspection; default `guest` / `guest` |
| 6379 | Valkey | Cache and rate-limit foundation |
| 8099 | Keycloak | `emhare` realm and administration console |
| 9000 | RustFS | S3-compatible object API |
| 9001 | RustFS console | Local object-storage administration |

## Database migrations

Database changes are service-owned and migration-only:

- Never edit an existing migration.
- Run `flyway:info` against the target service before creating a migration.
- Add a new, sequentially numbered `V<version>__<description>.sql` file under that service's `src/main/resources/db/migration/` directory.
- Include the required audit fields, optimistic-lock version, soft-delete fields, and matching Envers audit table for every business table.
- Run the target service's migration and integration tests before handing off the change.

Example for Core Identity using the local defaults:

```bash
mvn -f services/core-identity-service/pom.xml flyway:info \
  -Dflyway.url=jdbc:postgresql://localhost:5433/emhare_core_identity \
  -Dflyway.user=emhare_service \
  -Dflyway.password=emhare_dev_password
```

After adding a migration, verify the target module and its dependencies:

```bash
mvn -pl services/core-identity-service -am test
```

## Tests and quality checks

### Backend

The backend test suite uses JUnit 5 and PostgreSQL 18 Testcontainers. Docker must be running.

```bash
mvn test
```

Run one service and its required reactor dependencies with:

```bash
mvn -pl services/student-records-service -am test
```

OpenAPI is generated from each business service's live MVC mappings at `/v3/api-docs`; Swagger UI is available at `/swagger-ui.html`. Both require the `system-admin` role.

### Frontend

```bash
npm run frontend:typecheck
npm run frontend:build
```

Run the complete handoff gate, including backend tests, changed-file Java/Vue formatting, frontend unit coverage, type checks, production builds, changed-code coverage, and Playwright:

```bash
npm run quality
```

Target one application when iterating:

```bash
npm run admin:typecheck
npm run admin:build
```

### Browser tests

Install the Chromium browser once:

```bash
npx playwright install chromium
```

With the infrastructure and backend services running, execute:

```bash
npm run e2e
```

Playwright starts or reuses the admin portal, student portal, and UI workbench. Failed tests retain screenshots, video, and traces under `test-results/`.

### Live workflow verifiers

The scripts in `infrastructure/test/` exercise cross-service workflows and retain local evidence. Their historical default PostgreSQL container name is `emhare-flyway-postgres`; when using this README's Docker Compose stack, override it explicitly:

```bash
POSTGRES_CONTAINER=emhare-postgres \
  ./infrastructure/test/verify_student_self_service.sh
```

Run these scripts only against disposable local development data. Several verifiers intentionally create and retain workflow records for inspection.

## Common development tasks

### Stop the local infrastructure

```bash
make infra-down
```

This stops and removes the containers but preserves the named data volumes.

### Reset all local infrastructure data

```bash
docker compose down -v
```

**Warning:** this permanently deletes the local PostgreSQL databases, Keycloak data, RabbitMQ state, Valkey data, and RustFS objects managed by this Compose project. Use it only when a full local reset is intended.

### Use non-default frontend endpoints

The shared portal shell reads public environment variables directly. Set them before starting a portal:

```bash
export NUXT_PUBLIC_API_BASE=http://localhost:8080
export NUXT_PUBLIC_OIDC_ISSUER=http://localhost:8099/realms/emhare
export NUXT_PUBLIC_OIDC_CLIENT_ID=emhare-web
npm run admin:dev
```

## Troubleshooting

### Maven reports `release version 21 not supported`

Maven is using an older JDK. Check both commands:

```bash
java -version
mvn -version
```

Select JDK 21 and rerun the build.

### The portal shows `/api/core/me: 401`

- Confirm Keycloak is available at `http://localhost:8099`.
- Confirm the API Gateway is available at `http://localhost:8080/actuator/health`.
- Sign out, clear the stale local session if necessary, and sign in again.
- Confirm the portal issuer is `http://localhost:8099/realms/emhare`.

The application must redirect unauthenticated users to Keycloak; a 401 must not be treated as a generic server failure.

### A system administrator receives 403 responses

- Open the user's Keycloak **Role mapping** page in the `emhare` realm.
- Confirm the `system-admin` realm role is assigned.
- Sign out and sign in again to obtain a token containing the role.
- Confirm Core Identity is running on port 8081 and accessible through the gateway.

### PostgreSQL rejects the configured password

The initialization script creates database roles only on the first initialization of the PostgreSQL volume. Changing `.env` later does not change passwords already stored in PostgreSQL.

Either restore the credentials used to create the volume, update the development role deliberately, or reset the local volumes after reviewing the destructive warning above.

### A port is already in use or PostgreSQL reports too many clients

Each domain service uses a bounded Hikari connection pool with a default maximum of `5` connections and `1` minimum idle connection. Override the `EMHARE_DATABASE_*` values from `.env.example` only after sizing PostgreSQL for the combined connection budget of every concurrently running service.

Check for duplicate backend processes before starting another copy:

```bash
lsof -nP -iTCP -sTCP:LISTEN | grep -E ':(300[0-3]|808[0-9]|8090|8091|8099)\b'
```

Stop only the duplicate process. Do not delete databases or volumes to solve a process conflict.

If the portal reports a generic 500 while a service appears healthy, verify the complete route topology:

```bash
make backend-health
```

A `MISROUTED` result means the service was started on a port that differs from the API Gateway route. Stop that one service cleanly and restart it without a `SERVER_PORT` override, or set the matching `<SERVICE>_SERVICE_URL` before starting the Gateway. Do not run a second copy merely to occupy the expected port.

### Keycloak does not reflect a changed realm export

`--import-realm` does not overwrite an existing realm in the Keycloak database. Apply ordinary development changes through the administration console. For a complete disposable reset, remove the Compose volumes only after accepting the data-loss warning above.

### A newly created record is not visible

Registers are paginated. Clear active search/status filters or use the pagination controls. Create and update workflows normally focus the saved record automatically.

## Repository layout

```text
apps/                    Nuxt admin, applicant, student, and UI workbench apps
packages/portal-shell/   Shared UI, authentication, API, and operational components
libraries/service-foundation/Shared Spring security, web, and operational service configuration
libraries/persistence-audit/Shared JPA and Envers audit infrastructure
libraries/integration-contracts/Versioned cross-service event contracts
services/                Independently owned Spring Boot services
infrastructure/          Compose initialization, Keycloak realm, and live verifiers
tests/e2e/               Desktop and mobile Playwright workflows
fresh-design/            Binding requirements, data model, and ADRs
sparse-emhare/           Read-only CakePHP legacy requirements baseline
```

## Engineering rules

- Use `Module`, not `Course`, in new user-facing surfaces.
- Keep transaction base currency in USD. A ZWG transaction requires the effective exchange rate; without one it remains unrated.
- Use Keycloak for authentication and local RBAC/workflow permissions for authorization.
- Send browser API traffic through the API Gateway.
- Use common portal-shell components for tables, pagination, drawers, status display, errors, and confirmation flows.
- Use SweetAlert instead of browser `alert` or `confirm` dialogs.
- Store official outputs as governed documents in S3-compatible storage.
- Preserve audit history, workflow evidence, ownership checks, and optimistic locking.
- Keep commits capability-scoped and stage explicit paths so migrations, contracts, implementation, and verification remain traceable; do not commit unless explicitly requested.
- Do not commit generated secrets, local `.env` files, build output, or test evidence.
- Do not change an accepted architecture decision without a superseding ADR.

## Design and requirements

- [Architecture Decision Records](fresh-design/adrs/README.md)
- [Functional requirements](fresh-design/new-emhare-functional-requirements.md)
- [Core and Admissions data model](fresh-design/core-and-admissions-data-model.md)
- [Legacy requirements and data-model extraction](fresh-design/legacy-requirements-and-data-model.md)
