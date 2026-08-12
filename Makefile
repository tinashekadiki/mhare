SHELL := /bin/bash
.DEFAULT_GOAL := help

ROOT_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
PROCESS_MANAGER := $(ROOT_DIR)/infrastructure/dev/manage-local-process.sh

BACKEND_SERVICES := core-identity-service academic-setup-service admissions-service \
	finance-service student-records-service assessment-results-service \
	exams-timetabling-service accommodation-service dining-service \
	documents-reporting-service notifications-service
DISCOVERY_SERVICE := discovery-server
SERVICES := $(DISCOVERY_SERVICE) $(BACKEND_SERVICES) api-gateway
FRONTENDS := admin-portal applicant-portal student-portal

DB_HOST ?= localhost
DB_PORT ?= 5433
DB_USER ?= $(if $(EMHARE_SERVICE_DB_USER),$(EMHARE_SERVICE_DB_USER),emhare_service)
DB_PASSWORD ?= $(if $(EMHARE_SERVICE_DB_PASSWORD),$(EMHARE_SERVICE_DB_PASSWORD),emhare_dev_password)
EMHARE_STARTUP_TIMEOUT_SECONDS ?= 180
export EMHARE_STARTUP_TIMEOUT_SECONDS

.PHONY: help doctor ports \
	infra-up infra-wait keycloak-provisioner-config infra-down infra-restart infra-status infra-logs \
	build-commons build-all template-validate backend-validate backend-test frontend-install frontend-typecheck frontend-build test \
	db-info db-migrate migrate \
	services-up services-down services-restart services-status backend-health \
	frontends-up frontends-down frontends-restart frontends-status frontend-health \
	admin-dev applicant-dev student-dev \
	start up backend-up resume stop down restart status health \
	logs logs-once force-stop

help: ## Show the local development commands
	@printf '\neMhare local development\n\n'
	@printf '  make up                         Start infrastructure, migrate, backends, and all portals\n'
	@printf '  make resume                     Start stopped processes without rebuilding or migrating\n'
	@printf '  make status                     Show infrastructure, backend, and portal status\n'
	@printf '  make health                     Verify the complete local topology\n'
	@printf '  make logs SERVICE=<name>        Follow one service or portal log\n'
	@printf '  make logs-once SERVICE=<name>   Print the last 100 log lines\n'
	@printf '  make restart                    Restart all local application processes\n'
	@printf '  make stop                       Stop application processes; keep infrastructure running\n'
	@printf '  make down                       Stop applications and Docker infrastructure\n\n'
	@printf 'Focused commands\n\n'
	@printf '  make services-up SERVICE=core-identity-service\n'
	@printf '  make services-restart SERVICE=core-identity-service\n'
	@printf '  make db-info SERVICE=core-identity-service\n'
	@printf '  make db-migrate SERVICE=core-identity-service\n'
	@printf '  make frontends-up FRONTEND=admin-portal\n'
	@printf '  make force-stop SERVICE=api-gateway\n'
	@printf '  make doctor\n'
	@printf '  make ports\n\n'
	@printf 'Available backend services: %s\n' '$(SERVICES)'
	@printf 'Available frontends: %s\n\n' '$(FRONTENDS)'

doctor: ## Check required tools and show local runtime versions
	@set -euo pipefail; \
	for command_name in docker mvn java node npm curl jq lsof screen; do \
		command -v "$$command_name" >/dev/null || { echo "Missing required command: $$command_name" >&2; exit 1; }; \
	done; \
	docker info >/dev/null 2>&1 || { echo 'Docker is installed but the daemon is not running.' >&2; exit 1; }; \
	printf '%-18s %s\n' 'Docker Compose' "$$(docker compose version --short)"; \
	printf '%-18s %s\n' 'Java' "$$(java -version 2>&1 | head -n 1)"; \
	printf '%-18s %s\n' 'Maven' "$$(mvn -version | head -n 1)"; \
	printf '%-18s %s\n' 'Node' "$$(node --version)"; \
	printf '%-18s %s\n' 'npm' "$$(npm --version)"; \
	node_major="$$(node -p 'process.versions.node.split(".")[0]')"; \
	if [[ "$$node_major" == '25' || "$$node_major" -lt 22 ]]; then \
		echo 'Warning: package.json supports Node 22.19+, 24.11+, or 26+; the active Node version is outside that range.' >&2; \
	fi

ports: ## Show canonical local ports
	@printf '%-32s %s\n' 'COMPONENT' 'PORT'
	@printf '%-32s %s\n' 'PostgreSQL' '5433'
	@printf '%-32s %s\n' 'RabbitMQ / management' '5672 / 15672'
	@printf '%-32s %s\n' 'Valkey' '6379'
	@printf '%-32s %s\n' 'RustFS API / console' '9000 / 9001'
	@printf '%-32s %s\n' 'Keycloak' '8099'
	@printf '%-32s %s\n' 'Mailpit SMTP / UI' '1025 / 8025'
	@printf '%-32s %s\n' 'Eureka Discovery' '8761'
	@printf '%-32s %s\n' 'API Gateway' '8080'
	@printf '%-32s %s\n' 'Core Identity' '8081'
	@printf '%-32s %s\n' 'Academic Setup' '8082'
	@printf '%-32s %s\n' 'Admissions' '8083'
	@printf '%-32s %s\n' 'Finance' '8084'
	@printf '%-32s %s\n' 'Student Records' '8085'
	@printf '%-32s %s\n' 'Assessment and Results' '8086'
	@printf '%-32s %s\n' 'Exams and Timetabling' '8087'
	@printf '%-32s %s\n' 'Accommodation' '8088'
	@printf '%-32s %s\n' 'Dining' '8089'
	@printf '%-32s %s\n' 'Documents and Reporting' '8090'
	@printf '%-32s %s\n' 'Notifications' '8091'
	@printf '%-32s %s\n' 'Admin / Applicant / Student' '3000 / 3001 / 3002'

# Infrastructure

infra-up: doctor ## Start PostgreSQL, RabbitMQ, Valkey, RustFS, Keycloak, and Mailpit
	docker compose up -d postgres rabbitmq valkey rustfs keycloak mailpit
	@$(MAKE) infra-wait
	@$(MAKE) keycloak-provisioner-config

infra-wait: ## Wait until every infrastructure dependency is ready
	@set -euo pipefail; \
	deadline=$$((SECONDS + $(EMHARE_STARTUP_TIMEOUT_SECONDS))); \
	until docker compose exec -T postgres pg_isready -U "$${POSTGRES_ADMIN_USER:-postgres}" -d emhare_admin >/dev/null 2>&1; do \
		(( SECONDS < deadline )) || { echo 'PostgreSQL did not become ready.' >&2; exit 1; }; sleep 2; \
	done; \
	until docker compose exec -T rabbitmq rabbitmq-diagnostics -q ping >/dev/null 2>&1; do \
		(( SECONDS < deadline )) || { echo 'RabbitMQ did not become ready.' >&2; exit 1; }; sleep 2; \
	done; \
	until [[ "$$(docker compose exec -T valkey valkey-cli ping 2>/dev/null | tr -d '\r')" == 'PONG' ]]; do \
		(( SECONDS < deadline )) || { echo 'Valkey did not become ready.' >&2; exit 1; }; sleep 2; \
	done; \
	until curl -fsS --max-time 3 http://localhost:9000/health >/dev/null 2>&1; do \
		(( SECONDS < deadline )) || { echo 'RustFS did not become ready.' >&2; exit 1; }; sleep 2; \
	done; \
	until curl -fsS --max-time 3 http://localhost:8099/realms/emhare/.well-known/openid-configuration >/dev/null 2>&1; do \
		(( SECONDS < deadline )) || { echo 'Keycloak realm did not become ready.' >&2; exit 1; }; sleep 2; \
	done; \
	echo 'Infrastructure is ready.'

keycloak-provisioner-config: ## Reconcile the Core Identity Keycloak service account and user-management roles
	@bash infrastructure/keycloak/configure-core-identity-provisioner.sh

infra-down: ## Stop Docker infrastructure without deleting volumes
	docker compose down

infra-restart: infra-down infra-up ## Restart Docker infrastructure

infra-status: ## Show Docker infrastructure status
	@docker compose ps -a

infra-logs: ## Follow infrastructure logs; optionally set SERVICE=postgres
	docker compose logs -f --tail=100 $(SERVICE)

# Builds and tests

build-commons: ## Install the shared Java library into the local Maven repository
	mvn -pl libraries/service-common,libraries/service-foundation,libraries/persistence-audit,libraries/integration-contracts,libraries/test-support -am install -DskipTests

build-all: ## Build installable modules and executable service jars without tests
	mvn -DskipTests install
	@set -euo pipefail; \
	for service_name in $(SERVICES); do \
		echo "== packaging executable jar: $$service_name =="; \
		mvn -q -f "services/$$service_name/pom.xml" -DskipTests package spring-boot:repackage; \
	done

template-validate: ## Build and test the canonical Spring service template
	mvn -pl libraries/service-foundation,libraries/test-support -am install -DskipTests
	mvn -f templates/spring-service/pom.xml test

backend-validate: ## Validate all Maven modules
	mvn -DskipTests validate

backend-test: ## Run all backend tests
	mvn test

frontend-install: ## Install frontend dependencies
	npm install

frontend-typecheck: ## Typecheck every frontend
	npm run frontend:typecheck

frontend-build: ## Build every frontend
	npm run frontend:build

test: backend-test frontend-typecheck ## Run backend tests and frontend typechecking

# Database migrations. Each migration is preceded by flyway:info and failures
# stop the entire command before any backend process is launched.

db-info: ## Show Flyway state for all service databases or SERVICE=<name>
	@set -euo pipefail; \
	services='$(if $(SERVICE),$(SERVICE),$(BACKEND_SERVICES))'; \
	for service_name in $$services; do \
		case ' $(BACKEND_SERVICES) ' in *" $$service_name "*) ;; *) echo "Unknown migratable service: $$service_name" >&2; exit 2 ;; esac; \
		database_name="emhare_$$(echo "$$service_name" | sed 's/-service$$//; s/-/_/g')"; \
		echo "== Flyway info: $$service_name ($$database_name) =="; \
		mvn -f "services/$$service_name/pom.xml" flyway:info \
			-Dflyway.url="jdbc:postgresql://$(DB_HOST):$(DB_PORT)/$$database_name" \
			-Dflyway.user='$(DB_USER)' -Dflyway.password='$(DB_PASSWORD)'; \
	done

db-migrate: ## Inspect then migrate all service databases or SERVICE=<name>
	@set -euo pipefail; \
	services='$(if $(SERVICE),$(SERVICE),$(BACKEND_SERVICES))'; \
	for service_name in $$services; do \
		case ' $(BACKEND_SERVICES) ' in *" $$service_name "*) ;; *) echo "Unknown migratable service: $$service_name" >&2; exit 2 ;; esac; \
		database_name="emhare_$$(echo "$$service_name" | sed 's/-service$$//; s/-/_/g')"; \
		echo "== Flyway info: $$service_name ($$database_name) =="; \
		mvn -q -f "services/$$service_name/pom.xml" flyway:info \
			-Dflyway.url="jdbc:postgresql://$(DB_HOST):$(DB_PORT)/$$database_name" \
			-Dflyway.user='$(DB_USER)' -Dflyway.password='$(DB_PASSWORD)'; \
		echo "== Flyway migrate: $$service_name ($$database_name) =="; \
		mvn -q -f "services/$$service_name/pom.xml" flyway:migrate \
			-Dflyway.url="jdbc:postgresql://$(DB_HOST):$(DB_PORT)/$$database_name" \
			-Dflyway.user='$(DB_USER)' -Dflyway.password='$(DB_PASSWORD)'; \
	done

migrate: db-migrate

# Backend processes

services-up: ## Start and await every backend, or SERVICE=<name>
	@set -euo pipefail; \
	port_for() { \
		case "$$1" in \
			discovery-server) echo 8761 ;; \
			api-gateway) echo 8080 ;; core-identity-service) echo 8081 ;; academic-setup-service) echo 8082 ;; \
			admissions-service) echo 8083 ;; finance-service) echo 8084 ;; student-records-service) echo 8085 ;; \
			assessment-results-service) echo 8086 ;; exams-timetabling-service) echo 8087 ;; accommodation-service) echo 8088 ;; \
			dining-service) echo 8089 ;; documents-reporting-service) echo 8090 ;; notifications-service) echo 8091 ;; \
			*) return 1 ;; \
		esac; \
	}; \
	if [[ -n '$(SERVICE)' ]]; then \
		service_port="$$(port_for '$(SERVICE)')" || { echo 'Unknown service: $(SERVICE)' >&2; exit 2; }; \
		'$(PROCESS_MANAGER)' start backend '$(SERVICE)' "$$service_port"; \
		'$(PROCESS_MANAGER)' wait backend '$(SERVICE)' "$$service_port"; \
	else \
		service_port="$$(port_for discovery-server)"; \
		'$(PROCESS_MANAGER)' start backend discovery-server "$$service_port"; \
		'$(PROCESS_MANAGER)' wait backend discovery-server "$$service_port"; \
		for service_name in $(BACKEND_SERVICES); do \
			service_port="$$(port_for "$$service_name")"; \
			'$(PROCESS_MANAGER)' start backend "$$service_name" "$$service_port"; \
		done; \
		for service_name in $(BACKEND_SERVICES); do \
			service_port="$$(port_for "$$service_name")"; \
			'$(PROCESS_MANAGER)' wait backend "$$service_name" "$$service_port"; \
		done; \
		'$(PROCESS_MANAGER)' start backend api-gateway 8080; \
		'$(PROCESS_MANAGER)' wait backend api-gateway 8080; \
	fi

services-down: ## Stop Make-managed backends, or SERVICE=<name>
	@set -euo pipefail; \
	port_for() { case "$$1" in discovery-server) echo 8761;; api-gateway) echo 8080;; core-identity-service) echo 8081;; academic-setup-service) echo 8082;; admissions-service) echo 8083;; finance-service) echo 8084;; student-records-service) echo 8085;; assessment-results-service) echo 8086;; exams-timetabling-service) echo 8087;; accommodation-service) echo 8088;; dining-service) echo 8089;; documents-reporting-service) echo 8090;; notifications-service) echo 8091;; *) return 1;; esac; }; \
	services='$(if $(SERVICE),$(SERVICE),api-gateway $(BACKEND_SERVICES) discovery-server)'; \
	for service_name in $$services; do \
		service_port="$$(port_for "$$service_name")" || { echo "Unknown service: $$service_name" >&2; exit 2; }; \
		'$(PROCESS_MANAGER)' stop backend "$$service_name" "$$service_port"; \
	done

services-restart: ## Restart backends, or SERVICE=<name>
	@$(MAKE) services-down SERVICE='$(SERVICE)'
	@$(MAKE) services-up SERVICE='$(SERVICE)'

services-status: ## Show backend status
	@set -euo pipefail; \
	'$(PROCESS_MANAGER)' status backend discovery-server 8761; \
	port=8080; for service_name in api-gateway $(BACKEND_SERVICES); do \
		'$(PROCESS_MANAGER)' status backend "$$service_name" "$$port"; \
		port=$$((port + 1)); \
	done

backend-health: ## Verify every backend and gateway readiness
	./infrastructure/dev/verify-service-topology.sh

# Frontend processes

frontends-up: ## Start all portals, or FRONTEND=<name>
	@set -euo pipefail; \
	port_for() { case "$$1" in admin-portal) echo 3000;; applicant-portal) echo 3001;; student-portal) echo 3002;; *) return 1;; esac; }; \
	frontends='$(if $(FRONTEND),$(FRONTEND),$(FRONTENDS))'; \
	[[ -d node_modules ]] || $(MAKE) frontend-install; \
	for frontend_name in $$frontends; do \
		frontend_port="$$(port_for "$$frontend_name")" || { echo "Unknown frontend: $$frontend_name" >&2; exit 2; }; \
		'$(PROCESS_MANAGER)' start frontend "$$frontend_name" "$$frontend_port"; \
	done; \
	for frontend_name in $$frontends; do \
		frontend_port="$$(port_for "$$frontend_name")"; \
		'$(PROCESS_MANAGER)' wait frontend "$$frontend_name" "$$frontend_port"; \
	done

frontends-down: ## Stop Make-managed portals, or FRONTEND=<name>
	@set -euo pipefail; \
	port_for() { case "$$1" in admin-portal) echo 3000;; applicant-portal) echo 3001;; student-portal) echo 3002;; *) return 1;; esac; }; \
	frontends='$(if $(FRONTEND),$(FRONTEND),$(FRONTENDS))'; \
	for frontend_name in $$frontends; do \
		frontend_port="$$(port_for "$$frontend_name")" || { echo "Unknown frontend: $$frontend_name" >&2; exit 2; }; \
		'$(PROCESS_MANAGER)' stop frontend "$$frontend_name" "$$frontend_port"; \
	done

frontends-restart: ## Restart portals, or FRONTEND=<name>
	@$(MAKE) frontends-down FRONTEND='$(FRONTEND)'
	@$(MAKE) frontends-up FRONTEND='$(FRONTEND)'

frontends-status: ## Show portal status
	@'$(PROCESS_MANAGER)' status frontend admin-portal 3000
	@'$(PROCESS_MANAGER)' status frontend applicant-portal 3001
	@'$(PROCESS_MANAGER)' status frontend student-portal 3002

frontend-health: ## Verify all three portals respond
	@set -euo pipefail; \
	for port in 3000 3001 3002; do curl -fsS --max-time 5 -o /dev/null "http://localhost:$$port/"; done; \
	echo 'All portals are responding.'

admin-dev: ## Run the admin portal in the foreground
	npm run admin:dev

applicant-dev: ## Run the applicant portal in the foreground
	npm run applicant:dev

student-dev: ## Run the student portal in the foreground
	npm run student:dev

# Complete workflows

up: ## Perform a safe full startup of the complete local platform
	@$(MAKE) infra-up
	@$(MAKE) build-all
	@$(MAKE) db-migrate
	@$(MAKE) services-up
	@$(MAKE) frontends-up
	@$(MAKE) health

start: up

backend-up: ## Start infrastructure, migrate databases, and start all backends
	@$(MAKE) infra-up
	@$(MAKE) build-all
	@$(MAKE) db-migrate
	@$(MAKE) services-up
	@$(MAKE) backend-health

resume: ## Start stopped infrastructure and apps without build or migration work
	@$(MAKE) infra-up
	@$(MAKE) services-up
	@$(MAKE) frontends-up
	@$(MAKE) health

stop: ## Stop Make-managed application processes and keep infrastructure running
	@$(MAKE) frontends-down
	@$(MAKE) services-down

down: ## Stop application processes and Docker infrastructure; preserve volumes
	@$(MAKE) stop
	@$(MAKE) infra-down

restart: ## Restart all application processes while preserving infrastructure
	@$(MAKE) stop
	@$(MAKE) resume

status: ## Show the entire local platform status
	@$(MAKE) infra-status
	@printf '\nBackends\n'
	@$(MAKE) services-status
	@printf '\nFrontends\n'
	@$(MAKE) frontends-status

health: ## Verify infrastructure, backends, gateway readiness, and portals
	@$(MAKE) infra-wait
	@$(MAKE) backend-health
	@$(MAKE) frontend-health

logs: ## Follow logs for SERVICE=<backend|portal>
	@test -n '$(SERVICE)' || { echo 'Usage: make logs SERVICE=core-identity-service' >&2; exit 2; }
	@mkdir -p /private/tmp/emhare/logs
	@touch '/private/tmp/emhare/logs/$(SERVICE).log'
	tail -f '/private/tmp/emhare/logs/$(SERVICE).log'

logs-once: ## Print the last 100 lines for SERVICE=<backend|portal>
	@test -n '$(SERVICE)' || { echo 'Usage: make logs-once SERVICE=core-identity-service' >&2; exit 2; }
	@tail -n 100 '/private/tmp/emhare/logs/$(SERVICE).log'

force-stop: ## Stop a repo-owned process occupying SERVICE's canonical port
	@set -euo pipefail; \
	case '$(SERVICE)' in \
		api-gateway) kind=backend; port=8080;; core-identity-service) kind=backend; port=8081;; academic-setup-service) kind=backend; port=8082;; \
		admissions-service) kind=backend; port=8083;; finance-service) kind=backend; port=8084;; student-records-service) kind=backend; port=8085;; \
		assessment-results-service) kind=backend; port=8086;; exams-timetabling-service) kind=backend; port=8087;; accommodation-service) kind=backend; port=8088;; \
		dining-service) kind=backend; port=8089;; documents-reporting-service) kind=backend; port=8090;; notifications-service) kind=backend; port=8091;; \
		admin-portal) kind=frontend; port=3000;; applicant-portal) kind=frontend; port=3001;; student-portal) kind=frontend; port=3002;; \
		*) echo 'Set SERVICE to one backend or portal name. Run `make help` for the list.' >&2; exit 2;; \
	esac; \
	'$(PROCESS_MANAGER)' force-stop "$$kind" '$(SERVICE)' "$$port"
