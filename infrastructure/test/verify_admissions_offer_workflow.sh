#!/usr/bin/env bash

# Author: Tinashe K
# Proves the authenticated rolling Admissions lifecycle from verified applicant
# evidence through eligibility, academic recommendation, decision, a generated
# and published offer, immutable applicant response, and student conversion.

set -euo pipefail

current_step='initialising offer workflow harness'
report_failure() {
  local exit_status=$?
  printf 'FAIL: %s (exit %s)\n' "${current_step}" "${exit_status}" >&2
  exit "${exit_status}"
}
trap report_failure ERR

keycloak_base_url="${KEYCLOAK_BASE_URL:-http://localhost:8099}"
keycloak_realm="${KEYCLOAK_REALM:-emhare}"
keycloak_admin_username="${KEYCLOAK_ADMIN_USERNAME:-admin}"
keycloak_admin_password="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
core_identity_base_url="${CORE_IDENTITY_BASE_URL:-http://localhost:8081}"
admissions_base_url="${ADMISSIONS_BASE_URL:-http://localhost:8083}"
gateway_base_url="${GATEWAY_BASE_URL:-http://localhost:8080}"
document_storage_endpoint="${DOCUMENT_STORAGE_ENDPOINT:-http://localhost:9000}"
document_storage_bucket="${DOCUMENT_STORAGE_BUCKET:-emhare-official-documents}"
document_storage_access_key="${DOCUMENT_STORAGE_ACCESS_KEY:-emhare-dev-access}"
document_storage_secret_key="${DOCUMENT_STORAGE_SECRET_KEY:-emhare-dev-secret-change-me}"
postgres_container="${POSTGRES_CONTAINER:-emhare-postgres}"

test_run_identifier=$(uuidgen | tr '[:upper:]' '[:lower:]')
keycloak_client_id="e2e-offers-${test_run_identifier}"
applicant_email="applicant-${test_run_identifier}@example.test"
staff_email="admissions-${test_run_identifier}@example.test"
test_password='Temporary-E2E-Password-42'

academic_unit_type_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_unit_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_year_id=''
intake_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
intake_programme_level_target_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
intake_programme_target_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_level_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_type_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_version_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_type_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_type_programme_mapping_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_type_qualification_section_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_type_programme_section_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_type_document_requirement_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
admission_cycle_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
generated_document_id=''
uploaded_document_id=''
uploaded_document_storage_key=''
qualification_document_id=''
qualification_document_storage_key=''

keycloak_client_uuid=''
applicant_keycloak_user_id=''
staff_keycloak_user_id=''
applicant_core_user_id=''
staff_core_user_id=''
application_id=''
programme_choice_id=''
qualification_sitting_id=''
requirement_set_id=''
offer_id=''
conversion_request_id=''
student_id=''
programme_enrolment_id=''
finance_account_id=''

current_step='obtaining Keycloak administration token'
academic_year_id=$(docker exec -i "${postgres_container}" psql -At -v ON_ERROR_STOP=1 \
  -U postgres -d emhare_academic_setup -c \
  "SELECT id
     FROM academic_years
    WHERE deleted_at IS NULL
      AND status = 'OPEN'
      AND start_date <= current_date
      AND end_date >= current_date + 120
    ORDER BY start_date DESC
    LIMIT 1")
[[ -n "${academic_year_id}" ]]
keycloak_admin_token=$(curl -fsS -X POST \
  "${keycloak_base_url}/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=password \
  --data-urlencode client_id=admin-cli \
  --data-urlencode username="${keycloak_admin_username}" \
  --data-urlencode password="${keycloak_admin_password}" | jq -er .access_token)

cleanup_disposable_records() {
  set +e

  if [[ -n "${uploaded_document_storage_key}" ]]; then
    curl -fsS -o /dev/null -X DELETE \
      --aws-sigv4 'aws:amz:us-east-1:s3' \
      --user "${document_storage_access_key}:${document_storage_secret_key}" \
      "${document_storage_endpoint}/${document_storage_bucket}/${uploaded_document_storage_key}"
  fi

  if [[ -n "${qualification_document_storage_key}" ]]; then
    curl -fsS -o /dev/null -X DELETE \
      --aws-sigv4 'aws:amz:us-east-1:s3' \
      --user "${document_storage_access_key}:${document_storage_secret_key}" \
      "${document_storage_endpoint}/${document_storage_bucket}/${qualification_document_storage_key}"
  fi

  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_documents_reporting \
    -v offer_id="${offer_id:-00000000-0000-0000-0000-000000000000}" \
    -v generated_document_id="${generated_document_id:-00000000-0000-0000-0000-000000000000}" \
    -v uploaded_document_id="${uploaded_document_id:-00000000-0000-0000-0000-000000000000}" \
    -v qualification_document_id="${qualification_document_id:-00000000-0000-0000-0000-000000000000}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM integration_outbox WHERE payload ->> 'offerId' = :'offer_id';
DELETE FROM integration_inbox WHERE payload ->> 'offerId' = :'offer_id';
DELETE FROM integration_outbox WHERE payload ->> 'documentId' = :'uploaded_document_id';
DELETE FROM integration_inbox WHERE payload ->> 'documentId' = :'uploaded_document_id';
DELETE FROM integration_outbox WHERE payload ->> 'documentId' = :'qualification_document_id';
DELETE FROM integration_inbox WHERE payload ->> 'documentId' = :'qualification_document_id';
DELETE FROM uploaded_documents_aud WHERE id = :'uploaded_document_id'::uuid;
DELETE FROM uploaded_documents WHERE id = :'uploaded_document_id'::uuid;
DELETE FROM uploaded_documents_aud WHERE id = :'qualification_document_id'::uuid;
DELETE FROM uploaded_documents WHERE id = :'qualification_document_id'::uuid;
DELETE FROM published_offer_letter_projections_aud WHERE offer_id = :'offer_id'::uuid;
DELETE FROM published_offer_letter_projections WHERE offer_id = :'offer_id'::uuid;
DELETE FROM generated_documents_aud WHERE id = :'generated_document_id'::uuid;
DELETE FROM generated_documents WHERE id = :'generated_document_id'::uuid;
DELETE FROM offer_letter_projections_aud WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_letter_projections WHERE offer_id = :'offer_id'::uuid;
COMMIT;
SQL

  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_student_records \
    -v offer_id="${offer_id:-00000000-0000-0000-0000-000000000000}" \
    -v conversion_request_id="${conversion_request_id:-00000000-0000-0000-0000-000000000000}" \
    -v student_id="${student_id:-00000000-0000-0000-0000-000000000000}" \
    -v programme_enrolment_id="${programme_enrolment_id:-00000000-0000-0000-0000-000000000000}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM integration_outbox
 WHERE payload ->> 'sourceOfferId' = :'offer_id'
    OR payload ->> 'conversionRequestId' = :'conversion_request_id'
    OR payload ->> 'studentId' = :'student_id';
DELETE FROM integration_inbox
 WHERE payload ->> 'offerId' = :'offer_id'
    OR payload ->> 'conversionRequestId' = :'conversion_request_id'
    OR payload ->> 'studentId' = :'student_id';
DELETE FROM student_conversion_requests_aud WHERE id = :'conversion_request_id'::uuid;
DELETE FROM student_conversion_requests WHERE id = :'conversion_request_id'::uuid;
DELETE FROM student_status_events_aud WHERE student_id = :'student_id'::uuid;
DELETE FROM student_status_events WHERE student_id = :'student_id'::uuid;
DELETE FROM student_programme_enrolments_aud WHERE id = :'programme_enrolment_id'::uuid;
DELETE FROM student_programme_enrolments WHERE id = :'programme_enrolment_id'::uuid;
DELETE FROM students_aud WHERE id = :'student_id'::uuid;
DELETE FROM students WHERE id = :'student_id'::uuid;
COMMIT;
SQL

  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_finance \
    -v offer_id="${offer_id:-00000000-0000-0000-0000-000000000000}" \
    -v conversion_request_id="${conversion_request_id:-00000000-0000-0000-0000-000000000000}" \
    -v student_id="${student_id:-00000000-0000-0000-0000-000000000000}" \
    -v finance_account_id="${finance_account_id:-00000000-0000-0000-0000-000000000000}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM integration_outbox
 WHERE payload ->> 'conversionRequestId' = :'conversion_request_id'
    OR payload ->> 'studentId' = :'student_id';
DELETE FROM integration_inbox
 WHERE payload ->> 'sourceOfferId' = :'offer_id'
    OR payload ->> 'conversionRequestId' = :'conversion_request_id'
    OR payload ->> 'studentId' = :'student_id';
DELETE FROM student_finance_accounts_aud WHERE id = :'finance_account_id'::uuid;
DELETE FROM student_finance_accounts WHERE id = :'finance_account_id'::uuid;
COMMIT;
SQL

  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_admissions \
    -v application_id="${application_id:-00000000-0000-0000-0000-000000000000}" \
    -v requirement_set_id="${requirement_set_id:-00000000-0000-0000-0000-000000000000}" \
    -v qualification_sitting_id="${qualification_sitting_id:-00000000-0000-0000-0000-000000000000}" \
    -v offer_id="${offer_id:-00000000-0000-0000-0000-000000000000}" \
    -v application_type_id="${application_type_id}" \
    -v application_type_programme_mapping_id="${application_type_programme_mapping_id}" \
    -v application_type_qualification_section_id="${application_type_qualification_section_id}" \
    -v application_type_programme_section_id="${application_type_programme_section_id}" \
    -v application_type_document_requirement_id="${application_type_document_requirement_id}" \
    -v uploaded_document_id="${uploaded_document_id:-00000000-0000-0000-0000-000000000000}" \
    -v qualification_document_id="${qualification_document_id:-00000000-0000-0000-0000-000000000000}" \
    -v admission_cycle_id="${admission_cycle_id}" \
    -v applicant_core_user_id="${applicant_core_user_id:-00000000-0000-0000-0000-000000000000}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM integration_outbox
 WHERE payload ->> 'applicationId' = :'application_id' OR payload ->> 'offerId' = :'offer_id';
DELETE FROM integration_inbox
 WHERE payload ->> 'applicationId' = :'application_id' OR payload ->> 'offerId' = :'offer_id';
DELETE FROM integration_inbox WHERE payload ->> 'documentId' = :'uploaded_document_id';
DELETE FROM integration_inbox WHERE payload ->> 'documentId' = :'qualification_document_id';
DELETE FROM application_documents_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_documents WHERE application_id = :'application_id'::uuid;
DELETE FROM application_document_requirement_snapshots_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_document_requirement_snapshots WHERE application_id = :'application_id'::uuid;
DELETE FROM offer_status_events_aud WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_status_events WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_responses_aud WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_responses WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_dispatches_aud WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_dispatches WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_publications_aud WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_publications WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_document_versions_aud WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_document_versions WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_conditions_aud WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_conditions WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offers_aud WHERE id = :'offer_id'::uuid;
DELETE FROM offers WHERE id = :'offer_id'::uuid;
DELETE FROM programme_choice_decisions_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM programme_choice_decisions WHERE application_id = :'application_id'::uuid;
DELETE FROM academic_recommendations_aud
 WHERE academic_review_id IN (SELECT id FROM academic_reviews WHERE application_id = :'application_id'::uuid);
DELETE FROM academic_recommendations
 WHERE academic_review_id IN (SELECT id FROM academic_reviews WHERE application_id = :'application_id'::uuid);
DELETE FROM academic_reviews_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM academic_reviews WHERE application_id = :'application_id'::uuid;
DELETE FROM application_clearances_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_clearances WHERE application_id = :'application_id'::uuid;
DELETE FROM application_evaluations_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_evaluations WHERE application_id = :'application_id'::uuid;
DELETE FROM admission_requirement_sets_aud WHERE id = :'requirement_set_id'::uuid;
DELETE FROM admission_requirement_sets WHERE id = :'requirement_set_id'::uuid;
DELETE FROM applicant_qualification_results_aud WHERE qualification_sitting_id = :'qualification_sitting_id'::uuid;
DELETE FROM applicant_qualification_results WHERE qualification_sitting_id = :'qualification_sitting_id'::uuid;
DELETE FROM applicant_qualification_sittings_aud WHERE id = :'qualification_sitting_id'::uuid;
DELETE FROM applicant_qualification_sittings WHERE id = :'qualification_sitting_id'::uuid;
DELETE FROM application_status_events_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_status_events WHERE application_id = :'application_id'::uuid;
DELETE FROM application_sections_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_sections WHERE application_id = :'application_id'::uuid;
DELETE FROM application_programme_choices_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_programme_choices WHERE application_id = :'application_id'::uuid;
DELETE FROM applications_aud WHERE id = :'application_id'::uuid;
DELETE FROM applications WHERE id = :'application_id'::uuid;
DELETE FROM applicants_aud WHERE user_id = :'applicant_core_user_id'::uuid;
DELETE FROM applicants WHERE user_id = :'applicant_core_user_id'::uuid;
DELETE FROM application_type_programme_mappings_aud
 WHERE id = :'application_type_programme_mapping_id'::uuid;
DELETE FROM application_type_programme_mappings
 WHERE id = :'application_type_programme_mapping_id'::uuid;
DELETE FROM application_type_sections_aud
 WHERE id IN (:'application_type_qualification_section_id'::uuid,
              :'application_type_programme_section_id'::uuid);
DELETE FROM application_type_sections
 WHERE id IN (:'application_type_qualification_section_id'::uuid,
              :'application_type_programme_section_id'::uuid);
DELETE FROM application_type_document_requirements_aud
 WHERE id = :'application_type_document_requirement_id'::uuid;
DELETE FROM application_type_document_requirements
 WHERE id = :'application_type_document_requirement_id'::uuid;
DELETE FROM application_types_aud WHERE id = :'application_type_id'::uuid;
DELETE FROM application_types WHERE id = :'application_type_id'::uuid;
DELETE FROM admission_cycles_aud WHERE id = :'admission_cycle_id'::uuid;
DELETE FROM admission_cycles WHERE id = :'admission_cycle_id'::uuid;
COMMIT;
SQL

  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_academic_setup \
    -v academic_unit_type_id="${academic_unit_type_id}" \
    -v academic_unit_id="${academic_unit_id}" \
    -v academic_year_id="${academic_year_id}" \
    -v intake_id="${intake_id}" \
    -v intake_programme_level_target_id="${intake_programme_level_target_id}" \
    -v intake_programme_target_id="${intake_programme_target_id}" \
    -v programme_level_id="${programme_level_id}" \
    -v programme_type_id="${programme_type_id}" \
    -v programme_id="${programme_id}" \
    -v programme_version_id="${programme_version_id}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM programme_versions WHERE id = :'programme_version_id'::uuid;
DELETE FROM programmes WHERE id = :'programme_id'::uuid;
DELETE FROM programme_types WHERE id = :'programme_type_id'::uuid;
DELETE FROM programme_levels WHERE id = :'programme_level_id'::uuid;
DELETE FROM intake_programme_targets_aud WHERE id = :'intake_programme_target_id'::uuid;
DELETE FROM intake_programme_targets WHERE id = :'intake_programme_target_id'::uuid;
DELETE FROM intake_programme_level_targets_aud
 WHERE id = :'intake_programme_level_target_id'::uuid;
DELETE FROM intake_programme_level_targets
 WHERE id = :'intake_programme_level_target_id'::uuid;
DELETE FROM intakes WHERE id = :'intake_id'::uuid;
DELETE FROM academic_units WHERE id = :'academic_unit_id'::uuid;
DELETE FROM academic_unit_types WHERE id = :'academic_unit_type_id'::uuid;
COMMIT;
SQL

  for core_user_id in "${applicant_core_user_id}" "${staff_core_user_id}"; do
    if [[ -n "${core_user_id}" ]]; then
      docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_core_identity \
        -v core_user_id="${core_user_id}" \
        -v conversion_request_id="${conversion_request_id:-00000000-0000-0000-0000-000000000000}" \
        -v student_id="${student_id:-00000000-0000-0000-0000-000000000000}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM integration_outbox
 WHERE payload ->> 'conversionRequestId' = :'conversion_request_id'
    OR payload ->> 'studentId' = :'student_id';
DELETE FROM integration_inbox
 WHERE payload ->> 'conversionRequestId' = :'conversion_request_id'
    OR payload ->> 'studentId' = :'student_id';
DELETE FROM student_portal_access_provisioning_aud
 WHERE conversion_request_id = :'conversion_request_id'::uuid;
DELETE FROM student_portal_access_provisioning
 WHERE conversion_request_id = :'conversion_request_id'::uuid;
DELETE FROM user_role_assignments_aud WHERE user_id = :'core_user_id'::uuid;
DELETE FROM user_role_assignments WHERE user_id = :'core_user_id'::uuid;
DELETE FROM login_events_aud WHERE user_id = :'core_user_id'::uuid;
DELETE FROM login_events WHERE user_id = :'core_user_id'::uuid;
DELETE FROM users_aud WHERE id = :'core_user_id'::uuid;
DELETE FROM users WHERE id = :'core_user_id'::uuid;
COMMIT;
SQL
    fi
  done

  for keycloak_user_id in "${applicant_keycloak_user_id}" "${staff_keycloak_user_id}"; do
    if [[ -n "${keycloak_user_id}" ]]; then
      curl -fsS -o /dev/null -X DELETE \
        "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${keycloak_user_id}" \
        -H "Authorization: Bearer ${keycloak_admin_token}"
    fi
  done
  if [[ -n "${keycloak_client_uuid}" ]]; then
    curl -fsS -o /dev/null -X DELETE \
      "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients/${keycloak_client_uuid}" \
      -H "Authorization: Bearer ${keycloak_admin_token}"
  fi
}
trap cleanup_disposable_records EXIT

create_keycloak_user() {
  local email="$1"
  curl -fsS -o /dev/null -X POST \
    "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" \
    -H "Authorization: Bearer ${keycloak_admin_token}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg email "${email}" --arg password "${test_password}" \
      '{username:$email,email:$email,emailVerified:true,enabled:true,firstName:"Enterprise",lastName:"Operator",credentials:[{type:"password",value:$password,temporary:false}]}')"
  curl -fsS -G "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" \
    -H "Authorization: Bearer ${keycloak_admin_token}" \
    --data-urlencode username="${email}" --data-urlencode exact=true | jq -er '.[0].id'
}

assign_realm_roles() {
  local user_id="$1"
  shift
  local role_payload
  role_payload=$(for role_name in "$@"; do
    curl -fsS "${keycloak_base_url}/admin/realms/${keycloak_realm}/roles/${role_name}" \
      -H "Authorization: Bearer ${keycloak_admin_token}"
  done | jq -s '.')
  curl -fsS -o /dev/null -X POST \
    "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${user_id}/role-mappings/realm" \
    -H "Authorization: Bearer ${keycloak_admin_token}" \
    -H 'Content-Type: application/json' -d "${role_payload}"
}

access_token_for() {
  local email="$1"
  curl -fsS -X POST "${keycloak_base_url}/realms/${keycloak_realm}/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode grant_type=password \
    --data-urlencode client_id="${keycloak_client_id}" \
    --data-urlencode username="${email}" \
    --data-urlencode password="${test_password}" | jq -er .access_token
}

request_json() {
  local response status body
  response=$(curl -sS -w $'\n%{http_code}' "$@")
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"
  if ((status < 200 || status >= 300)); then
    printf 'HTTP %s during %s: %s\n' "${status}" "${current_step}" "${body}" >&2
    return 22
  fi
  printf '%s' "${body}"
}

current_step='creating disposable Keycloak client and users'
curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients" \
  -H "Authorization: Bearer ${keycloak_admin_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg clientId "${keycloak_client_id}" \
    '{clientId:$clientId,enabled:true,publicClient:true,directAccessGrantsEnabled:true,standardFlowEnabled:false,protocol:"openid-connect"}')"
keycloak_client_uuid=$(curl -fsS -G "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients" \
  -H "Authorization: Bearer ${keycloak_admin_token}" --data-urlencode clientId="${keycloak_client_id}" | jq -er '.[0].id')

applicant_keycloak_user_id=$(create_keycloak_user "${applicant_email}")
staff_keycloak_user_id=$(create_keycloak_user "${staff_email}")
assign_realm_roles "${applicant_keycloak_user_id}" applicant
assign_realm_roles "${staff_keycloak_user_id}" admissions-officer system-admin

applicant_access_token=$(access_token_for "${applicant_email}")
staff_access_token=$(access_token_for "${staff_email}")
current_step='provisioning disposable users in Core Identity'
applicant_core_user_id=$(curl -fsS "${core_identity_base_url}/api/core/me" \
  -H "Authorization: Bearer ${applicant_access_token}" | jq -er .user.id)
staff_core_user_id=$(curl -fsS "${core_identity_base_url}/api/core/me" \
  -H "Authorization: Bearer ${staff_access_token}" | jq -er .user.id)

current_step='creating authoritative Academic Setup fixtures'
parent_academic_unit_id=$(docker exec -i "${postgres_container}" psql -At -v ON_ERROR_STOP=1 \
  -U postgres -d emhare_academic_setup -c \
  "SELECT academic_unit.id
     FROM academic_units academic_unit
     JOIN academic_unit_types unit_type ON unit_type.id = academic_unit.academic_unit_type_id
    WHERE academic_unit.deleted_at IS NULL
      AND academic_unit.status = 'ACTIVE'
      AND unit_type.deleted_at IS NULL
      AND unit_type.status = 'ACTIVE'
      AND NOT EXISTS (
        SELECT 1 FROM programmes programme
         WHERE programme.owning_academic_unit_id = academic_unit.id
           AND programme.deleted_at IS NULL
      )
      AND NOT EXISTS (
        SELECT 1 FROM modules module
         WHERE module.owning_academic_unit_id = academic_unit.id
           AND module.deleted_at IS NULL
      )
    ORDER BY unit_type.level_order DESC, academic_unit.id
    LIMIT 1")
[[ -n "${parent_academic_unit_id}" ]]
docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_academic_setup \
  -v academic_unit_type_id="${academic_unit_type_id}" -v academic_unit_id="${academic_unit_id}" \
  -v parent_academic_unit_id="${parent_academic_unit_id}" \
  -v academic_year_id="${academic_year_id}" -v intake_id="${intake_id}" \
  -v intake_programme_level_target_id="${intake_programme_level_target_id}" \
  -v intake_programme_target_id="${intake_programme_target_id}" \
  -v programme_level_id="${programme_level_id}" -v programme_type_id="${programme_type_id}" \
  -v programme_id="${programme_id}" -v programme_version_id="${programme_version_id}" \
  -v actor_user_id="${staff_core_user_id}" >/dev/null <<'SQL'
INSERT INTO academic_unit_types (id, code, name, level_order, is_leaf_allowed, status, created_at, updated_at, version)
VALUES (:'academic_unit_type_id'::uuid, 'E2E_SCHOOL', 'School',
    (SELECT COALESCE(MAX(level_order), 0) + 1 FROM academic_unit_types),
    true, 'ACTIVE', now(), now(), 0);
INSERT INTO academic_units (id, academic_unit_type_id, parent_id, code, name, status, created_at, updated_at, version)
VALUES (:'academic_unit_id'::uuid, :'academic_unit_type_id'::uuid, :'parent_academic_unit_id'::uuid,
    'E2E_BUS', 'School of Business', 'ACTIVE', now(), now(), 0);
INSERT INTO intakes (id, academic_year_id, code, name, starts_on, ends_on, status,
    offer_acceptance_deadline, registration_date, orientation_date, commencement_date,
    created_at, updated_at, version)
SELECT :'intake_id'::uuid, :'academic_year_id'::uuid, 'E2E_INTAKE', 'E2E Offer Intake',
    current_date - 1, current_date + 120, 'DRAFT', now() + interval '30 days',
    current_date + 45, current_date + 50, current_date + 60, now(), now(), 0
FROM academic_years academic_year
WHERE academic_year.id = :'academic_year_id'::uuid;
INSERT INTO programme_levels (id, code, name, sort_order, status, created_at, updated_at, version)
VALUES (:'programme_level_id'::uuid, 'E2E_UG', 'Undergraduate',
    (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM programme_levels),
    'ACTIVE', now(), now(), 0);
INSERT INTO programme_types (id, code, name, status, created_at, updated_at, version)
VALUES (:'programme_type_id'::uuid, 'E2E_DEGREE', 'Degree', 'ACTIVE', now(), now(), 0);
INSERT INTO programmes (id, owning_academic_unit_id, programme_type_id, programme_level_id, code, name, award_name,
    minimum_duration_periods, maximum_duration_periods, status, created_at, updated_at, version)
VALUES (:'programme_id'::uuid, :'academic_unit_id'::uuid, :'programme_type_id'::uuid, :'programme_level_id'::uuid,
    'E2BC', 'Bachelor of Commerce', 'Bachelor of Commerce Honours Degree', 8, 12, 'ACTIVE', now(), now(), 0);
INSERT INTO programme_versions (id, programme_id, version_code, effective_from, status, approved_by_user_id, approved_at,
    created_at, updated_at, version)
VALUES (:'programme_version_id'::uuid, :'programme_id'::uuid, 'E2E.1', current_date - 1, 'APPROVED',
    :'actor_user_id'::uuid, now(), now(), now(), 0);
INSERT INTO intake_programme_level_targets
    (id, intake_id, programme_level_id, created_at, updated_at, version)
VALUES (:'intake_programme_level_target_id'::uuid, :'intake_id'::uuid,
    :'programme_level_id'::uuid, now(), now(), 0);
INSERT INTO intake_programme_targets
    (id, intake_id, programme_id, created_at, updated_at, version)
VALUES (:'intake_programme_target_id'::uuid, :'intake_id'::uuid,
    :'programme_id'::uuid, now(), now(), 0);
UPDATE intakes SET status = 'OPEN', updated_at = now(), version = version + 1
WHERE id = :'intake_id'::uuid;
SQL

current_step='assigning exact academic-unit authority'
docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_core_identity \
  -v staff_user_id="${staff_core_user_id}" -v academic_unit_id="${academic_unit_id}" >/dev/null <<'SQL'
INSERT INTO user_role_assignments (id, user_id, role_id, academic_unit_id, starts_at,
    created_at, updated_at, created_by_user_id, modified_by_user_id, version)
VALUES (gen_random_uuid(), :'staff_user_id'::uuid, '10000000-0000-4000-8000-000000000006'::uuid,
    :'academic_unit_id'::uuid, now(), now(), now(), :'staff_user_id'::uuid, :'staff_user_id'::uuid, 0);
SQL

current_step='creating Admissions cycle fixtures'
docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_admissions \
  -v admission_cycle_id="${admission_cycle_id}" -v academic_year_id="${academic_year_id}" \
  -v intake_id="${intake_id}" -v application_type_id="${application_type_id}" \
  -v application_type_programme_mapping_id="${application_type_programme_mapping_id}" \
  -v application_type_qualification_section_id="${application_type_qualification_section_id}" \
  -v application_type_programme_section_id="${application_type_programme_section_id}" \
  -v application_type_document_requirement_id="${application_type_document_requirement_id}" \
  -v programme_id="${programme_id}" -v actor_user_id="${staff_core_user_id}" >/dev/null <<'SQL'
INSERT INTO admission_cycles (id, academic_year_id, intake_id, code, name, opens_at, closes_at, status,
    maximum_programme_choices, created_at, updated_at, version)
VALUES (:'admission_cycle_id'::uuid, :'academic_year_id'::uuid, :'intake_id'::uuid,
    'E2E-OFFER-' || left(:'admission_cycle_id', 8), 'Disposable offer workflow cycle',
    now() - interval '1 day', now() + interval '30 days', 'OPEN', 2, now(), now(), 0);
INSERT INTO application_types (id, code, name, requires_employment_history, requires_referees, is_active,
    fee_policy_status, fee_free_reason, fee_policy_decided_by_user_id, fee_policy_decided_at,
    created_at, updated_at, version)
VALUES (:'application_type_id'::uuid, 'E2E-OFFER-' || left(:'application_type_id', 8),
    'Disposable offer workflow application', false, false, true, 'FEE_FREE',
    'Approved fee-free policy for the disposable admissions golden path.',
    :'actor_user_id'::uuid, now(), now(), now(), 0);
INSERT INTO application_type_programme_mappings
    (id, application_type_id, programme_id, programme_code, programme_name, is_active,
     created_at, updated_at, version)
VALUES (:'application_type_programme_mapping_id'::uuid, :'application_type_id'::uuid,
    :'programme_id'::uuid, 'E2BC', 'Bachelor of Commerce', true, now(), now(), 0);
INSERT INTO application_type_sections
    (id, application_type_id, section_code, section_name, is_required, is_repeatable,
     minimum_records, sort_order, is_active, created_at, updated_at, version)
VALUES
    (:'application_type_qualification_section_id'::uuid, :'application_type_id'::uuid,
     'QUALIFICATIONS', 'Qualifications', true, true, 1, 10, true, now(), now(), 0),
    (:'application_type_programme_section_id'::uuid, :'application_type_id'::uuid,
     'PROGRAMME_CHOICES', 'Programme choices', true, true, 1, 20, true, now(), now(), 0);
INSERT INTO application_type_document_requirements
    (id, application_type_id, requirement_code, requirement_name, is_required, sort_order,
     is_active, created_at, updated_at, version)
VALUES (:'application_type_document_requirement_id'::uuid, :'application_type_id'::uuid,
    'NATIONAL_ID', 'National identity document', true, 1, true, now(), now(), 0);
SQL

current_step='creating applicant application and governed requirement set'
draft_application=$(request_json -X POST "${admissions_base_url}/api/admissions/applications" \
  -H "Authorization: Bearer ${applicant_access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg intake "${intake_id}" --arg type "${application_type_id}" --arg programme "${programme_id}" \
    '{applicantCategoryCode:"LOCAL",intakeId:$intake,applicationTypeId:$type,programmeIds:[$programme]}')")
application_id=$(jq -er .id <<<"${draft_application}")
programme_choice_id=$(jq -er .programmeChoices[0].id <<<"${draft_application}")
if ! jq -e '.status == "DRAFT" and .canSubmit == false and .programmeChoices[0].programmeCode == "E2BC"' \
  <<<"${draft_application}" >/dev/null; then
  jq . <<<"${draft_application}" >&2
  false
fi

requirement_set=$(request_json -X POST "${admissions_base_url}/api/admissions/requirement-sets" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg programme "${programme_id}" --arg type "${application_type_id}" --arg intake "${intake_id}" \
    --arg today "$(date +%F)" \
    '{programmeId:$programme,applicationTypeId:$type,intakeId:$intake,versionCode:"E2E.1",effectiveFrom:$today,requiresEnglish:false,requiresMathematics:false,requiresScience:false,requiresMathematicsOrScience:false,subjectRequirements:[],qualificationGroups:[{code:"PRIOR_DEGREE",name:"Prior degree duration",minimumSatisfiedItems:1,sortOrder:1,items:[{qualificationLevel:"DEGREE",minimumCount:1,minimumDurationMonths:36,sortOrder:1}]}]}')")
requirement_set_id=$(jq -er .id <<<"${requirement_set}")
current_step='approving governed admission requirement set'
approved_requirement_set=$(request_json -X POST "${admissions_base_url}/api/admissions/requirement-sets/${requirement_set_id}/approve" \
  -H "Authorization: Bearer ${staff_access_token}")
jq -e '.status == "APPROVED"
    and (.subjectRequirements | length) == 0
    and .qualificationGroups[0].items[0].minimumDurationMonths == 36' \
  <<<"${approved_requirement_set}" >/dev/null

current_step='uploading and independently verifying qualification evidence'
qualification_document=$(request_json -X POST "${gateway_base_url}/api/documents/uploads" \
  -H "Authorization: Bearer ${applicant_access_token}" \
  -F ownerType=APPLICATION \
  -F ownerId="${application_id}" \
  -F documentTypeCode=ACADEMIC_QUALIFICATION_EVIDENCE \
  -F "file=@services/documents-reporting-service/src/main/resources/documents/uz-logo.jpg;type=image/jpeg")
qualification_document_id=$(jq -er .id <<<"${qualification_document}")
qualification_document_version=$(jq -er .version <<<"${qualification_document}")
qualification_document_storage_key=$(docker exec -i "${postgres_container}" psql -At -v ON_ERROR_STOP=1 \
  -U postgres -d emhare_documents_reporting -c \
  "SELECT storage_key FROM uploaded_documents WHERE id = '${qualification_document_id}'::uuid")
jq -e --arg applicationId "${application_id}" '
  .ownerType == "APPLICATION"
    and .ownerId == $applicationId
    and .documentTypeCode == "ACADEMIC_QUALIFICATION_EVIDENCE"
    and .verificationStatus == "PENDING"
' <<<"${qualification_document}" >/dev/null

verified_qualification_document=$(request_json -X POST \
  "${gateway_base_url}/api/documents/uploads/${qualification_document_id}/verify" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson expectedVersion "${qualification_document_version}" \
    '{expectedVersion:$expectedVersion,comment:"Qualification evidence verified against the issuing institution record."}')")
jq -e '.verificationStatus == "VERIFIED" and .version == 1' \
  <<<"${verified_qualification_document}" >/dev/null

current_step='capturing applicant qualification aggregate against verified evidence'
qualification_workspace=$(request_json -X POST \
  "${admissions_base_url}/api/admissions/applications/${application_id}/qualification-aggregates" \
  -H "Authorization: Bearer ${applicant_access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg documentId "${qualification_document_id}" \
    '{level:"DEGREE",awardTypeCode:"DEGREE",qualificationName:"Bachelor of Commerce Honours Degree",institutionName:"University of Zimbabwe",yearWritten:2025,durationMonths:48,documentId:$documentId,results:[],expectedVersion:0}')")
qualification_sitting_id=$(jq -er '.qualifications[0].id' <<<"${qualification_workspace}")
if ! jq -e --arg documentId "${qualification_document_id}" '
    .qualifications[0].durationMonths == 48
    and .qualifications[0].documentId == $documentId
    and .qualifications[0].awardTypeCode == "DEGREE"
    and .qualifications[0].qualificationName == "Bachelor of Commerce Honours Degree"
    and .qualifications[0].verificationStatus == "CAPTURED"' \
  <<<"${qualification_workspace}" >/dev/null; then
  jq . <<<"${qualification_workspace}" >&2
  false
fi

current_step='uploading clean applicant evidence through the malware scanner'
uploaded_document=$(request_json -X POST "${gateway_base_url}/api/documents/uploads" \
  -H "Authorization: Bearer ${applicant_access_token}" \
  -F ownerType=APPLICATION \
  -F ownerId="${application_id}" \
  -F documentTypeCode=NATIONAL_ID \
  -F "file=@services/documents-reporting-service/src/main/resources/documents/uz-logo.jpg;type=image/jpeg")
uploaded_document_id=$(jq -er .id <<<"${uploaded_document}")
uploaded_document_version=$(jq -er .version <<<"${uploaded_document}")
uploaded_document_storage_key=$(docker exec -i "${postgres_container}" psql -At -v ON_ERROR_STOP=1 \
  -U postgres -d emhare_documents_reporting -c \
  "SELECT storage_key FROM uploaded_documents WHERE id = '${uploaded_document_id}'::uuid")
jq -e --arg applicationId "${application_id}" '
  .ownerType == "APPLICATION"
    and .ownerId == $applicationId
    and .documentTypeCode == "NATIONAL_ID"
    and .verificationStatus == "PENDING"
    and .mimeType == "image/jpeg"
    and (.checksumSha256 | length) == 64
' <<<"${uploaded_document}" >/dev/null

current_step='linking uploaded evidence to the snapshotted application requirement'
linked_document_register=$(request_json -X POST \
  "${admissions_base_url}/api/admissions/applications/${application_id}/documents" \
  -H "Authorization: Bearer ${applicant_access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg documentId "${uploaded_document_id}" \
    '{documentId:$documentId,requirementCode:"NATIONAL_ID"}')")
jq -e --arg documentId "${uploaded_document_id}" '
  .requiredDocumentsUploaded == true
    and .requiredDocumentsVerified == false
    and .pendingRequirementCodes == ["NATIONAL_ID"]
    and .requirements[0].documentId == $documentId
' <<<"${linked_document_register}" >/dev/null

current_step='submitting applicant evidence'
submitted_application=$(request_json -X POST "${admissions_base_url}/api/admissions/applications/${application_id}/submission" \
  -H "Authorization: Bearer ${applicant_access_token}")
jq -e '.status == "SUBMITTED"' <<<"${submitted_application}" >/dev/null

current_step='independently verifying uploaded applicant evidence'
verified_document=$(request_json -X POST \
  "${gateway_base_url}/api/documents/uploads/${uploaded_document_id}/verify" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson expectedVersion "${uploaded_document_version}" \
    '{expectedVersion:$expectedVersion,comment:"Identity evidence verified against the applicant record."}')")
jq -e '.verificationStatus == "VERIFIED" and .version == 1' <<<"${verified_document}" >/dev/null

projected_document_register=''
for _ in {1..30}; do
  projected_document_register=$(request_json \
    "${admissions_base_url}/api/admissions/applications/${application_id}/documents" \
    -H "Authorization: Bearer ${staff_access_token}")
  if jq -e '.requiredDocumentsVerified == true and .requirements[0].state == "VERIFIED"' \
    <<<"${projected_document_register}" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
jq -e '.requiredDocumentsVerified == true and .requirements[0].state == "VERIFIED"
    and .requirements[0].documentVersion == 1' <<<"${projected_document_register}" >/dev/null

current_step='verifying applicant qualification evidence'
verified_qualification=$(request_json -X POST \
  "${admissions_base_url}/api/admissions/applications/${application_id}/qualifications/${qualification_sitting_id}/decision" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d '{"decision":"VERIFIED","reason":"Qualification evidence matched the issuing institution record.","expectedVersion":0}')
jq -e '.verificationStatus == "VERIFIED" and .durationMonths == 48' <<<"${verified_qualification}" >/dev/null

current_step='confirming automatic eligibility and academic-review routing'
rolling_case=$(request_json \
  "${admissions_base_url}/api/admissions/work-items/${application_id}" \
  -H "Authorization: Bearer ${staff_access_token}")
jq -e '.workspace.application.status == "UNDER_ACADEMIC_REVIEW"
    and .workspace.application.programmeChoices[0].choiceStatus == "UNDER_ACADEMIC_REVIEW"
    and .academicReview.status == "OPEN"' <<<"${rolling_case}" >/dev/null
recommendation_academic_unit_id=$(jq -er .academicReview.recommendationAcademicUnitId <<<"${rolling_case}")
if [[ "${recommendation_academic_unit_id}" != "${academic_unit_id}" ]]; then
  current_step='assigning exact recommendation-root authority'
  docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_core_identity \
    -v staff_user_id="${staff_core_user_id}" \
    -v recommendation_academic_unit_id="${recommendation_academic_unit_id}" >/dev/null <<'SQL'
INSERT INTO user_role_assignments (id, user_id, role_id, academic_unit_id, starts_at,
    created_at, updated_at, created_by_user_id, modified_by_user_id, version)
VALUES (gen_random_uuid(), :'staff_user_id'::uuid, '10000000-0000-4000-8000-000000000006'::uuid,
    :'recommendation_academic_unit_id'::uuid, now(), now(), now(),
    :'staff_user_id'::uuid, :'staff_user_id'::uuid, 0);
SQL
fi

current_step='recording independent academic recommendation'
recommendation=$(request_json -X POST \
  "${admissions_base_url}/api/admissions/applications/${application_id}/choices/${programme_choice_id}/academic-recommendation" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d '{"recommendation":"RECOMMEND_ADMIT","reason":"Verified evidence satisfies the published academic requirements."}')
jq -e '.academicReview.status == "RECOMMENDED" and .academicRecommendation.recommendation == "RECOMMEND_ADMIT"' \
  <<<"${recommendation}" >/dev/null

current_step='recording direct rolling admission decision'
offer=$(curl -fsS -X POST \
  "${admissions_base_url}/api/admissions/applications/${application_id}/choices/${programme_choice_id}/decision" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d '{"decision":"ADMIT","reason":"Academic recommendation approved for direct admission."}')
offer_id=$(jq -er .id <<<"${offer}")
jq -e '.status == "DRAFT" and .offerNumber != null' <<<"${offer}" >/dev/null

updated_offer=$(curl -fsS -X PUT "${admissions_base_url}/api/admissions/offers/${offer_id}" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d '{"offerType":"FIRM","conditionsText":null}')
jq -e '.status == "DRAFT" and .offerType == "FIRM" and .acceptanceDeadline != null and .commencementDate != null' \
  <<<"${updated_offer}" >/dev/null

current_step='generating and storing official offer document'
document_request=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/offers/${offer_id}/document-generation" \
  -H "Authorization: Bearer ${staff_access_token}")
jq -e '.status == "REQUESTED" and .documentVersion == 1' <<<"${document_request}" >/dev/null

stored_document=''
for _ in {1..30}; do
  stored_document=$(curl -fsS "${admissions_base_url}/api/admissions/work-items/${application_id}" \
    -H "Authorization: Bearer ${staff_access_token}" \
    | jq -cer '.documentVersions[] | select(.version == 1)')
  if jq -e '.status == "STORED" and .generatedDocumentId != null' <<<"${stored_document}" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
jq -e '.status == "STORED" and .checksumSha256 != null' <<<"${stored_document}" >/dev/null
generated_document_id=$(jq -er .generatedDocumentId <<<"${stored_document}")

current_step='publishing current offer document to the applicant'
published_offer=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/offers/${offer_id}/publish-and-send" \
  -H "Authorization: Bearer ${staff_access_token}")
jq -e '.publicationId != null and .documentVersionId != null and .portalPublishedAt != null' \
  <<<"${published_offer}" >/dev/null

current_step='accepting immutable published offer'
applicant_offers=$(curl -fsS "${admissions_base_url}/api/admissions/offers/mine" \
  -H "Authorization: Bearer ${applicant_access_token}")
jq -e --arg offerId "${offer_id}" 'any(.[]; .id == $offerId and .status == "SENT")' <<<"${applicant_offers}" >/dev/null

accepted_offer=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/offers/${offer_id}/response" \
  -H "Authorization: Bearer ${applicant_access_token}" -H 'Content-Type: application/json' \
  -d '{"response":"ACCEPTED","notes":"I accept this governed offer."}')
jq -e '.status == "ACCEPTED" and .response.response == "ACCEPTED"' <<<"${accepted_offer}" >/dev/null

conflicting_response=$(curl -sS -w $'\n%{http_code}' -X POST \
  "${admissions_base_url}/api/admissions/offers/${offer_id}/response" \
  -H "Authorization: Bearer ${applicant_access_token}" -H 'Content-Type: application/json' \
  -d '{"response":"DECLINED","notes":"Attempted conflicting response."}')
[[ "$(tail -n 1 <<<"${conflicting_response}")" == '409' ]]

current_step='waiting for cross-service student conversion'
conversion_snapshot=''
for _ in {1..30}; do
  conversion_snapshot=$(docker exec -i "${postgres_container}" psql -qAt -U postgres \
    -d emhare_student_records -v offer_id="${offer_id}" <<'SQL'
SELECT json_build_object(
  'conversionRequestId', conversion.id,
  'conversionStatus', conversion.status,
  'financeStatus', conversion.finance_provisioning_status,
  'portalStatus', conversion.portal_provisioning_status,
  'studentId', student.id,
  'studentNumber', student.student_number,
  'studentStatus', student.status,
  'programmeEnrolmentId', enrolment.id,
  'enrolmentStatus', enrolment.status
)::text
FROM student_conversion_requests conversion
JOIN students student ON student.id = conversion.student_id
JOIN student_programme_enrolments enrolment ON enrolment.id = conversion.programme_enrolment_id
WHERE conversion.source_offer_id = :'offer_id'::uuid;
SQL
  )
  if jq -e '.conversionStatus == "COMPLETED"' <<<"${conversion_snapshot}" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

jq -e '.conversionStatus == "COMPLETED"
    and .financeStatus == "COMPLETED"
    and .portalStatus == "COMPLETED"
    and .studentStatus == "ACTIVE"
    and .enrolmentStatus == "ACTIVE"' <<<"${conversion_snapshot}" >/dev/null
conversion_request_id=$(jq -er .conversionRequestId <<<"${conversion_snapshot}")
student_id=$(jq -er .studentId <<<"${conversion_snapshot}")
programme_enrolment_id=$(jq -er .programmeEnrolmentId <<<"${conversion_snapshot}")

current_step='validating Finance student account'
finance_snapshot=$(docker exec -i "${postgres_container}" psql -qAt -U postgres -d emhare_finance \
  -v student_id="${student_id}" -v offer_id="${offer_id}" <<'SQL'
SELECT json_build_object(
  'id', id,
  'accountNumber', account_number,
  'status', status,
  'baseCurrencyCode', base_currency_code,
  'studentNumber', student_number
)::text
FROM student_finance_accounts
WHERE student_id = :'student_id'::uuid AND source_offer_id = :'offer_id'::uuid;
SQL
)
jq -e --arg studentNumber "$(jq -r .studentNumber <<<"${conversion_snapshot}")" \
  '.status == "ACTIVE" and .baseCurrencyCode == "USD" and .studentNumber == $studentNumber' \
  <<<"${finance_snapshot}" >/dev/null
finance_account_id=$(jq -er .id <<<"${finance_snapshot}")

current_step='validating Core Identity student portal access'
portal_snapshot=$(docker exec -i "${postgres_container}" psql -qAt -U postgres -d emhare_core_identity \
  -v conversion_request_id="${conversion_request_id}" -v student_id="${student_id}" \
  -v user_id="${applicant_core_user_id}" <<'SQL'
SELECT json_build_object(
  'status', provisioning.status,
  'studentNumber', provisioning.student_number,
  'roleCode', role.code,
  'roleAssignmentId', assignment.id
)::text
FROM student_portal_access_provisioning provisioning
JOIN user_role_assignments assignment ON assignment.id = provisioning.role_assignment_id
JOIN roles role ON role.id = assignment.role_id
WHERE provisioning.conversion_request_id = :'conversion_request_id'::uuid
  AND provisioning.student_id = :'student_id'::uuid
  AND provisioning.user_id = :'user_id'::uuid;
SQL
)
jq -e '.status == "PROVISIONED" and .roleCode == "STUDENT" and .roleAssignmentId != null' \
  <<<"${portal_snapshot}" >/dev/null

current_step='loading converted offer API projection'
converted_offer=''
for _ in {1..30}; do
  converted_offer=$(curl -fsS "${admissions_base_url}/api/admissions/offers" \
    -H "Authorization: Bearer ${staff_access_token}" \
    | jq -cer --arg offerId "${offer_id}" '.[] | select(.id == $offerId)')
  if jq -e '.status == "CONVERTED"' <<<"${converted_offer}" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
current_step='validating converted offer API projection'
if ! jq -e --arg conversionRequestId "${conversion_request_id}" --arg studentId "${student_id}" \
    --arg studentNumber "$(jq -r .studentNumber <<<"${conversion_snapshot}")" \
    '.status == "CONVERTED" and .conversionRequestId == $conversionRequestId
      and .convertedStudentId == $studentId and .convertedStudentNumber == $studentNumber
      and .convertedAt != null' <<<"${converted_offer}" >/dev/null; then
  jq '{status, conversionRequestId, convertedStudentId, convertedStudentNumber, convertedAt}' \
    <<<"${converted_offer}" >&2
  false
fi

current_step='validating converted application and programme choice states'
admissions_conversion_snapshot=$(docker exec -i "${postgres_container}" psql -qAt -U postgres \
  -d emhare_admissions -v application_id="${application_id}" -v choice_id="${programme_choice_id}" <<'SQL'
SELECT json_build_object(
  'applicationStatus', (SELECT status FROM applications WHERE id = :'application_id'::uuid),
  'choiceStatus', (SELECT choice_status FROM application_programme_choices WHERE id = :'choice_id'::uuid)
)::text;
SQL
)
jq -e '.applicationStatus == "CONVERTED" and .choiceStatus == "CONVERTED"' \
  <<<"${admissions_conversion_snapshot}" >/dev/null

current_step='validating audit evidence'
audit_counts=$(docker exec -i "${postgres_container}" psql -qAt -U postgres -d emhare_admissions \
  -v application_id="${application_id}" -v offer_id="${offer_id}" \
  -v qualification_sitting_id="${qualification_sitting_id}" <<'SQL'
SELECT json_build_object(
  'applicationEvents', (SELECT count(*) FROM application_status_events WHERE application_id = :'application_id'::uuid),
  'offerEvents', (SELECT count(*) FROM offer_status_events WHERE offer_id = :'offer_id'::uuid),
  'offerAudits', (SELECT count(*) FROM offers_aud WHERE id = :'offer_id'::uuid),
  'qualificationAudits', (SELECT count(*) FROM applicant_qualification_sittings_aud WHERE id = :'qualification_sitting_id'::uuid),
  'academicReviews', (SELECT count(*) FROM academic_reviews WHERE application_id = :'application_id'::uuid),
  'academicRecommendations', (SELECT count(*) FROM academic_recommendations recommendation
      JOIN academic_reviews review ON review.id = recommendation.academic_review_id
      WHERE review.application_id = :'application_id'::uuid),
  'decisions', (SELECT count(*) FROM programme_choice_decisions WHERE application_id = :'application_id'::uuid),
  'storedDocuments', (SELECT count(*) FROM offer_document_versions WHERE offer_id = :'offer_id'::uuid AND status = 'STORED'),
  'publications', (SELECT count(*) FROM offer_publications WHERE offer_id = :'offer_id'::uuid AND current_publication)
)::text;
SQL
)
jq -e '.applicationEvents >= 7 and .offerEvents >= 3 and .offerAudits >= 3
    and .qualificationAudits >= 2 and .academicReviews == 1 and .academicRecommendations == 1
    and .decisions == 1 and .storedDocuments == 1 and .publications == 1' \
  <<<"${audit_counts}" >/dev/null

jq -n \
  --arg applicationId "${application_id}" \
  --arg offerId "${offer_id}" \
  --arg offerNumber "$(jq -r .offerNumber <<<"${accepted_offer}")" \
  --arg applicationStatus "CONVERTED" \
  --arg offerStatus "$(jq -r .status <<<"${converted_offer}")" \
  --arg conversionRequestId "${conversion_request_id}" \
  --arg studentId "${student_id}" \
  --arg studentNumber "$(jq -r .studentNumber <<<"${conversion_snapshot}")" \
  --arg financeAccountNumber "$(jq -r .accountNumber <<<"${finance_snapshot}")" \
  --argjson audit "${audit_counts}" \
  '{result:"PASS",workflow:"ROLLING_ADMISSIONS",applicationId:$applicationId,offerId:$offerId,offerNumber:$offerNumber,
    applicationStatus:$applicationStatus,offerStatus:$offerStatus,
    conversionRequestId:$conversionRequestId,studentId:$studentId,
    studentNumber:$studentNumber,financeAccountNumber:$financeAccountNumber,
    baseCurrency:"USD",portalRole:"STUDENT",immutableResponseGuard:true,audit:$audit}'
