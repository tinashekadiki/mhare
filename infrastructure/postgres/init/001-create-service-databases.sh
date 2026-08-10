#!/bin/bash
set -e

service_user="${EMHARE_SERVICE_DB_USER:-emhare_service}"
service_password="${EMHARE_SERVICE_DB_PASSWORD:-emhare_dev_password}"
keycloak_user="${KEYCLOAK_DB_USER:-keycloak}"
keycloak_password="${KEYCLOAK_DB_PASSWORD:-keycloak_dev_password}"

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" <<EOSQL
DO
\$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${service_user}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${service_user}', '${service_password}');
    END IF;

    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${keycloak_user}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${keycloak_user}', '${keycloak_password}');
    END IF;
END
\$\$;

SELECT format('CREATE DATABASE %I OWNER %I', 'emhare_keycloak', '${keycloak_user}')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'emhare_keycloak')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', 'emhare_core_identity', '${service_user}')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'emhare_core_identity')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', 'emhare_academic_setup', '${service_user}')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'emhare_academic_setup')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', 'emhare_admissions', '${service_user}')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'emhare_admissions')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', 'emhare_finance', '${service_user}')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'emhare_finance')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', 'emhare_student_records', '${service_user}')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'emhare_student_records')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', 'emhare_assessment_results', '${service_user}')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'emhare_assessment_results')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', 'emhare_exams_timetabling', '${service_user}')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'emhare_exams_timetabling')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', 'emhare_accommodation', '${service_user}')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'emhare_accommodation')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', 'emhare_dining', '${service_user}')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'emhare_dining')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', 'emhare_documents_reporting', '${service_user}')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'emhare_documents_reporting')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', 'emhare_notifications', '${service_user}')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'emhare_notifications')\gexec
EOSQL
