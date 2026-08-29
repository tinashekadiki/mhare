#!/usr/bin/env bash

# Author: Tinashe K
# Runs the authenticated Admissions -> Finance -> Admissions happy path against
# isolated local services and removes every disposable business record it creates.

set -euo pipefail
trap 'printf "Application fee golden path failed at line %s.\n" "${LINENO}" >&2' ERR

keycloak_base_url="${KEYCLOAK_BASE_URL:-http://localhost:8099}"
keycloak_realm="${KEYCLOAK_REALM:-emhare}"
keycloak_admin_username="${KEYCLOAK_ADMIN_USERNAME:-admin}"
keycloak_admin_password="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
core_identity_base_url="${CORE_IDENTITY_BASE_URL:-http://localhost:18081}"
admissions_base_url="${ADMISSIONS_BASE_URL:-http://localhost:18083}"
finance_base_url="${FINANCE_BASE_URL:-http://localhost:18084}"
postgres_container="${POSTGRES_CONTAINER:-emhare-postgres}"
pause_after_reference_seconds="${E2E_PAUSE_AFTER_REFERENCE_SECONDS:-0}"

test_run_identifier=$(uuidgen | tr '[:upper:]' '[:lower:]')
keycloak_client_id="e2e-admissions-${test_run_identifier}"
test_email="${keycloak_client_id}@example.test"
test_password='Temporary-E2E-Password-42'
admission_cycle_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_year_id=$(docker exec "${postgres_container}" psql -qAt -U postgres \
  -d emhare_academic_setup -c \
  "SELECT id FROM academic_years WHERE status = 'OPEN' AND start_date <= current_date AND end_date >= current_date + 30 ORDER BY end_date LIMIT 1")
[[ -n "${academic_year_id}" ]]
intake_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
intake_programme_level_target_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
intake_programme_target_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_unit_type_root_id=$(docker exec "${postgres_container}" psql -qAt -U postgres \
  -d emhare_academic_setup -c \
  "SELECT id FROM academic_unit_types WHERE code = 'FACULTY' AND status = 'ACTIVE' AND deleted_at IS NULL LIMIT 1")
academic_unit_type_leaf_id=$(docker exec "${postgres_container}" psql -qAt -U postgres \
  -d emhare_academic_setup -c \
  "SELECT id FROM academic_unit_types WHERE code = 'DEPARTMENT' AND status = 'ACTIVE' AND deleted_at IS NULL LIMIT 1")
[[ -n "${academic_unit_type_root_id}" && -n "${academic_unit_type_leaf_id}" ]]
academic_unit_root_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_unit_leaf_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_level_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_type_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_version_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
module_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
curriculum_module_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_type_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_type_programme_mapping_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_type_programme_section_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_type_optional_document_requirement_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
fee_catalogue_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
fee_structure_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
fee_rule_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_id=''
core_user_id=''
keycloak_user_id=''
keycloak_client_uuid=''

keycloak_admin_token=$(curl -fsS -X POST \
  "${keycloak_base_url}/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=password \
  --data-urlencode client_id=admin-cli \
  --data-urlencode username="${keycloak_admin_username}" \
  --data-urlencode password="${keycloak_admin_password}" | jq -er .access_token)

cleanup_disposable_records() {
  set +e

  if [[ -n "${application_id}" ]]; then
    docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_finance \
      -v application_id="${application_id}" >/dev/null <<'SQL'
DELETE FROM integration_outbox WHERE payload ->> 'applicationId' = :'application_id';
DELETE FROM integration_inbox WHERE payload ->> 'applicationId' = :'application_id';
DELETE FROM finance_receipts_aud WHERE id IN (SELECT receipt.id FROM finance_receipts receipt JOIN application_payments payment ON payment.id = receipt.application_payment_id WHERE payment.source_application_id = :'application_id'::uuid);
DELETE FROM finance_receipts WHERE application_payment_id IN (SELECT id FROM application_payments WHERE source_application_id = :'application_id'::uuid);
DELETE FROM application_payments_aud WHERE id IN (SELECT id FROM application_payments WHERE source_application_id = :'application_id'::uuid);
DELETE FROM application_payments WHERE source_application_id = :'application_id'::uuid;
DELETE FROM application_payment_references_aud WHERE id IN (SELECT id FROM application_payment_references WHERE source_application_id = :'application_id'::uuid);
DELETE FROM application_payment_references WHERE source_application_id = :'application_id'::uuid;
SQL

    docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_admissions \
      -v application_id="${application_id}" >/dev/null <<'SQL'
DELETE FROM integration_outbox WHERE payload ->> 'applicationId' = :'application_id';
DELETE FROM integration_inbox WHERE payload ->> 'applicationId' = :'application_id';
DELETE FROM application_status_events_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_status_events WHERE application_id = :'application_id'::uuid;
DELETE FROM application_payment_references_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_payment_references WHERE application_id = :'application_id'::uuid;
DELETE FROM application_document_requirement_snapshots_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_document_requirement_snapshots WHERE application_id = :'application_id'::uuid;
DELETE FROM application_sections_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_sections WHERE application_id = :'application_id'::uuid;
DELETE FROM application_programme_choices_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_programme_choices WHERE application_id = :'application_id'::uuid;
DELETE FROM application_programme_option_snapshots_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_programme_option_snapshots WHERE application_id = :'application_id'::uuid;
DELETE FROM applications_aud WHERE id = :'application_id'::uuid;
DELETE FROM applications WHERE id = :'application_id'::uuid;
SQL
  fi

  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_finance \
    -v fee_catalogue_id="${fee_catalogue_id}" \
    -v fee_structure_id="${fee_structure_id}" \
    -v fee_rule_id="${fee_rule_id}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM finance_fee_rule_scopes_aud WHERE fee_rule_id = :'fee_rule_id'::uuid;
DELETE FROM finance_fee_rule_scopes WHERE fee_rule_id = :'fee_rule_id'::uuid;
DELETE FROM finance_fee_rules_aud WHERE id = :'fee_rule_id'::uuid;
DELETE FROM finance_fee_rules WHERE id = :'fee_rule_id'::uuid;
DELETE FROM finance_fee_structures_aud WHERE id = :'fee_structure_id'::uuid;
DELETE FROM finance_fee_structures WHERE id = :'fee_structure_id'::uuid;
DELETE FROM finance_fee_catalogues_aud WHERE id = :'fee_catalogue_id'::uuid;
DELETE FROM finance_fee_catalogues WHERE id = :'fee_catalogue_id'::uuid;
COMMIT;
SQL

  if [[ -n "${core_user_id}" ]]; then
    docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_admissions \
      -v core_user_id="${core_user_id}" >/dev/null <<'SQL'
DELETE FROM applicants_aud WHERE user_id = :'core_user_id'::uuid;
DELETE FROM applicants WHERE user_id = :'core_user_id'::uuid;
SQL

    docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_core_identity \
      -v core_user_id="${core_user_id}" >/dev/null <<'SQL'
DELETE FROM user_role_assignments_aud WHERE user_id = :'core_user_id'::uuid;
DELETE FROM user_role_assignments WHERE user_id = :'core_user_id'::uuid;
DELETE FROM login_events_aud WHERE user_id = :'core_user_id'::uuid;
DELETE FROM login_events WHERE user_id = :'core_user_id'::uuid;
DELETE FROM users_aud WHERE id = :'core_user_id'::uuid;
DELETE FROM users WHERE id = :'core_user_id'::uuid;
SQL
  fi

  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_admissions \
    -v application_type_id="${application_type_id}" \
    -v application_type_programme_mapping_id="${application_type_programme_mapping_id}" \
    -v admission_cycle_id="${admission_cycle_id}" >/dev/null <<'SQL'
DELETE FROM application_type_programme_mappings_aud WHERE id = :'application_type_programme_mapping_id'::uuid;
DELETE FROM application_type_programme_mappings WHERE id = :'application_type_programme_mapping_id'::uuid;
DELETE FROM application_type_document_requirements_aud WHERE application_type_id = :'application_type_id'::uuid;
DELETE FROM application_type_document_requirements WHERE application_type_id = :'application_type_id'::uuid;
DELETE FROM application_type_sections_aud WHERE application_type_id = :'application_type_id'::uuid;
DELETE FROM application_type_sections WHERE application_type_id = :'application_type_id'::uuid;
DELETE FROM application_types_aud WHERE id = :'application_type_id'::uuid;
DELETE FROM application_types WHERE id = :'application_type_id'::uuid;
DELETE FROM admission_cycles_aud WHERE id = :'admission_cycle_id'::uuid;
DELETE FROM admission_cycles WHERE id = :'admission_cycle_id'::uuid;
SQL

  docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_academic_setup \
    -v curriculum_module_id="${curriculum_module_id}" \
    -v programme_version_id="${programme_version_id}" \
    -v programme_id="${programme_id}" \
    -v module_id="${module_id}" \
    -v programme_type_id="${programme_type_id}" \
    -v programme_level_id="${programme_level_id}" \
    -v intake_id="${intake_id}" \
    -v intake_programme_level_target_id="${intake_programme_level_target_id}" \
    -v intake_programme_target_id="${intake_programme_target_id}" \
    -v academic_year_id="${academic_year_id}" \
    -v academic_unit_leaf_id="${academic_unit_leaf_id}" \
    -v academic_unit_root_id="${academic_unit_root_id}" \
    -v academic_unit_type_leaf_id="${academic_unit_type_leaf_id}" \
    -v academic_unit_type_root_id="${academic_unit_type_root_id}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM curriculum_modules WHERE id = :'curriculum_module_id'::uuid;
DELETE FROM intake_programme_targets WHERE id = :'intake_programme_target_id'::uuid;
DELETE FROM intake_programme_level_targets WHERE id = :'intake_programme_level_target_id'::uuid;
DELETE FROM programme_versions WHERE id = :'programme_version_id'::uuid;
DELETE FROM programmes WHERE id = :'programme_id'::uuid;
DELETE FROM modules WHERE id = :'module_id'::uuid;
DELETE FROM programme_types WHERE id = :'programme_type_id'::uuid;
DELETE FROM programme_levels WHERE id = :'programme_level_id'::uuid;
DELETE FROM intakes WHERE id = :'intake_id'::uuid;
DELETE FROM academic_units WHERE id = :'academic_unit_leaf_id'::uuid;
DELETE FROM academic_units WHERE id = :'academic_unit_root_id'::uuid;
COMMIT;
SQL

  if [[ -n "${keycloak_user_id}" ]]; then
    curl -fsS -o /dev/null -X DELETE \
      "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${keycloak_user_id}" \
      -H "Authorization: Bearer ${keycloak_admin_token}"
  fi
  if [[ -n "${keycloak_client_uuid}" ]]; then
    curl -fsS -o /dev/null -X DELETE \
      "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients/${keycloak_client_uuid}" \
      -H "Authorization: Bearer ${keycloak_admin_token}"
  fi
}
trap cleanup_disposable_records EXIT

curl -fsS -o /dev/null -X POST \
  "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients" \
  -H "Authorization: Bearer ${keycloak_admin_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg clientId "${keycloak_client_id}" '{clientId:$clientId,enabled:true,publicClient:true,directAccessGrantsEnabled:true,standardFlowEnabled:false,protocol:"openid-connect"}')"
keycloak_client_uuid=$(curl -fsS -G \
  "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients" \
  -H "Authorization: Bearer ${keycloak_admin_token}" \
  --data-urlencode clientId="${keycloak_client_id}" | jq -er '.[0].id')

curl -fsS -o /dev/null -X POST \
  "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" \
  -H "Authorization: Bearer ${keycloak_admin_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg email "${test_email}" --arg password "${test_password}" '{username:$email,email:$email,emailVerified:true,enabled:true,firstName:"Enterprise",lastName:"Applicant",credentials:[{type:"password",value:$password,temporary:false}]}')"
keycloak_user_id=$(curl -fsS -G \
  "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" \
  -H "Authorization: Bearer ${keycloak_admin_token}" \
  --data-urlencode username="${test_email}" \
  --data-urlencode exact=true | jq -er '.[0].id')

role_payload=$(for role_name in applicant finance-officer admissions-officer; do
  curl -fsS "${keycloak_base_url}/admin/realms/${keycloak_realm}/roles/${role_name}" \
    -H "Authorization: Bearer ${keycloak_admin_token}"
done | jq -s '.')
curl -fsS -o /dev/null -X POST \
  "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${keycloak_user_id}/role-mappings/realm" \
  -H "Authorization: Bearer ${keycloak_admin_token}" \
  -H 'Content-Type: application/json' \
  -d "${role_payload}"

access_token=$(curl -fsS -X POST \
  "${keycloak_base_url}/realms/${keycloak_realm}/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=password \
  --data-urlencode client_id="${keycloak_client_id}" \
  --data-urlencode username="${test_email}" \
  --data-urlencode password="${test_password}" | jq -er .access_token)

core_profile=$(curl -fsS "${core_identity_base_url}/api/core/me" \
  -H "Authorization: Bearer ${access_token}")
core_user_id=$(jq -er .user.id <<<"${core_profile}")

invalid_request_correlation_id="e2e-invalid-${test_run_identifier}"
invalid_request_response=$(curl -sS -w $'\n%{http_code}' -X POST \
  "${admissions_base_url}/api/admissions/applications" \
  -H "Authorization: Bearer ${access_token}" \
  -H "X-Correlation-ID: ${invalid_request_correlation_id}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg cycle "$(uuidgen)" --arg type "$(uuidgen)" '{applicantCategoryCode:"LOCAL",firstName:"Enterprise",lastName:"Applicant",admissionCycleId:$cycle,applicationTypeId:$type}')")
invalid_request_status=$(tail -n 1 <<<"${invalid_request_response}")
invalid_request_body=$(sed '$d' <<<"${invalid_request_response}")
[[ "${invalid_request_status}" == '400' ]]
jq -e --arg correlationId "${invalid_request_correlation_id}" '
  .status == 400 and .title == "Validation failed" and .correlationId == $correlationId
' <<<"${invalid_request_body}" >/dev/null

docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_academic_setup \
  -v academic_unit_type_root_id="${academic_unit_type_root_id}" \
  -v academic_unit_type_leaf_id="${academic_unit_type_leaf_id}" \
  -v academic_unit_root_id="${academic_unit_root_id}" \
  -v academic_unit_leaf_id="${academic_unit_leaf_id}" \
  -v academic_year_id="${academic_year_id}" \
  -v intake_id="${intake_id}" \
  -v intake_programme_level_target_id="${intake_programme_level_target_id}" \
  -v intake_programme_target_id="${intake_programme_target_id}" \
  -v programme_level_id="${programme_level_id}" \
  -v programme_type_id="${programme_type_id}" \
  -v programme_id="${programme_id}" \
  -v programme_version_id="${programme_version_id}" \
  -v module_id="${module_id}" \
  -v curriculum_module_id="${curriculum_module_id}" \
  -v actor_user_id="${keycloak_user_id}" >/dev/null <<'SQL'
INSERT INTO academic_units (id, academic_unit_type_id, parent_id, code, name, status, created_at, updated_at, version)
VALUES
  (:'academic_unit_root_id'::uuid, :'academic_unit_type_root_id'::uuid, null, 'E2E_SCI', 'Faculty of Science', 'ACTIVE', now(), now(), 0),
  (:'academic_unit_leaf_id'::uuid, :'academic_unit_type_leaf_id'::uuid, :'academic_unit_root_id'::uuid, 'E2E_COMP', 'Department of Computing', 'ACTIVE', now(), now(), 0);
INSERT INTO intakes (id, academic_year_id, code, name, starts_on, ends_on, status, created_at, updated_at, version)
SELECT :'intake_id'::uuid, :'academic_year_id'::uuid, 'E2E_INTAKE', 'Disposable E2E Intake', current_date - 1, current_date + 30, 'DRAFT', now(), now(), 0
FROM academic_years
WHERE id = :'academic_year_id'::uuid;
WITH next_programme_level AS (
  SELECT COALESCE(MAX(sort_order), 0) + 1 AS sort_order
  FROM programme_levels
)
INSERT INTO programme_levels (id, code, name, sort_order, status, created_at, updated_at, version)
SELECT :'programme_level_id'::uuid, 'E2E_UG', 'Undergraduate', sort_order, 'ACTIVE', now(), now(), 0
FROM next_programme_level;
INSERT INTO programme_types (id, code, name, status, created_at, updated_at, version)
VALUES (:'programme_type_id'::uuid, 'E2E_DEGREE', 'Degree', 'ACTIVE', now(), now(), 0);
INSERT INTO modules (id, owning_academic_unit_id, code, name, description, credit_value, academic_level, status, created_at, updated_at, version)
VALUES (:'module_id'::uuid, :'academic_unit_leaf_id'::uuid, 'E2E_CSC101', 'Programming Fundamentals', 'Foundational programming and problem solving.', 12.00, 1, 'ACTIVE', now(), now(), 0);
INSERT INTO programmes (id, owning_academic_unit_id, programme_type_id, programme_level_id, code, name, award_name, minimum_duration_periods, maximum_duration_periods, status, created_at, updated_at, version)
VALUES (:'programme_id'::uuid, :'academic_unit_leaf_id'::uuid, :'programme_type_id'::uuid, :'programme_level_id'::uuid, 'E2EIT', 'Bachelor of Science in Information Technology', 'Bachelor of Science Honours Degree', 8, 12, 'ACTIVE', now(), now(), 0);
INSERT INTO programme_versions (id, programme_id, version_code, effective_from, status, created_at, updated_at, version)
VALUES (:'programme_version_id'::uuid, :'programme_id'::uuid, 'E2E.1', current_date - 1, 'DRAFT', now(), now(), 0);
INSERT INTO curriculum_modules (id, programme_version_id, module_id, period_number, module_type, credit_value, minimum_mark_required, sort_order, created_at, updated_at, version)
VALUES (:'curriculum_module_id'::uuid, :'programme_version_id'::uuid, :'module_id'::uuid, 1, 'COMPULSORY', 12.00, 50.00, 1, now(), now(), 0);
UPDATE programme_versions
SET status = 'APPROVED', approved_by_user_id = :'actor_user_id'::uuid, approved_at = now(), updated_at = now(), version = 1
WHERE id = :'programme_version_id'::uuid;
INSERT INTO intake_programme_level_targets
    (id, intake_id, programme_level_id, created_at, updated_at, version)
VALUES (:'intake_programme_level_target_id'::uuid, :'intake_id'::uuid,
    :'programme_level_id'::uuid, now(), now(), 0);
INSERT INTO intake_programme_targets
    (id, intake_id, programme_id, created_at, updated_at, version)
VALUES (:'intake_programme_target_id'::uuid, :'intake_id'::uuid,
    :'programme_id'::uuid, now(), now(), 0);
UPDATE intakes
SET status = 'OPEN', updated_at = now(), version = 1
WHERE id = :'intake_id'::uuid;
SQL

docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_finance \
  -v fee_catalogue_id="${fee_catalogue_id}" \
  -v fee_structure_id="${fee_structure_id}" \
  -v fee_rule_id="${fee_rule_id}" \
  -v programme_level_id="${programme_level_id}" \
  -v actor_user_id="${core_user_id}" >/dev/null <<'SQL'
BEGIN;
INSERT INTO finance_fee_catalogues (
    id, code, name, description, charge_type, receivable_account_code,
    revenue_account_code, base_currency_code, status, prepared_by_user_id,
    activated_by_user_id, activated_at, activation_reason, created_at, updated_at, version)
VALUES (
    :'fee_catalogue_id'::uuid, 'APP-' || left(:'fee_catalogue_id', 8),
    'Disposable application fee', 'Finance-owned application fee golden-path fixture.',
    'APPLICATION', 'AR-APPLICATION', 'REV-APPLICATION', 'USD', 'ACTIVE',
    :'actor_user_id'::uuid, gen_random_uuid(), now(),
    'Independent Finance activation for golden-path verification.', now(), now(), 0);
INSERT INTO finance_fee_structures (
    id, code, name, description, fee_context, scope_type, scope_reference_id,
    scope_reference_code, scope_reference_name, programme_level_id,
    programme_level_code, programme_level_name, transaction_currency_code,
    effective_from, status, prepared_by_user_id, activated_by_user_id,
    activated_at, activation_reason, created_at, updated_at, version)
VALUES (
    :'fee_structure_id'::uuid, 'APP-' || left(:'fee_structure_id', 8),
    'Disposable application fee structure',
    'Finance-owned application fee structure golden-path fixture.',
    'APPLICATION', 'PROGRAMME_LEVEL', :'programme_level_id'::uuid,
    'E2E_UG', 'Undergraduate', :'programme_level_id'::uuid, 'E2E_UG',
    'Undergraduate', 'USD', now() - interval '1 day', 'ACTIVE',
    :'actor_user_id'::uuid, gen_random_uuid(), now(),
    'Independent Finance activation for golden-path verification.', now(), now(), 0);
INSERT INTO finance_fee_rules (
    id, fee_catalogue_id, fee_structure_id, structure_line_number,
    structure_line_description, rule_version, transaction_currency_code,
    transaction_amount, base_currency_code, base_amount, rating_status,
    effective_from, status, prepared_by_user_id, created_at, updated_at, version)
VALUES (
    :'fee_rule_id'::uuid, :'fee_catalogue_id'::uuid, :'fee_structure_id'::uuid, 1,
    'Application processing fee', 1, 'USD', 25.00, 'USD', 25.00, 'RATED',
    now() - interval '1 day', 'DRAFT', :'actor_user_id'::uuid, now(), now(), 0);
INSERT INTO finance_fee_rule_scopes (
    id, fee_rule_id, scope_dimension, reference_id, reference_code, reference_name,
    created_at, updated_at, version)
VALUES (
    gen_random_uuid(), :'fee_rule_id'::uuid, 'PROGRAMME_LEVEL',
    :'programme_level_id'::uuid, 'E2E_UG', 'Undergraduate', now(), now(), 0);
UPDATE finance_fee_rules
SET status = 'APPROVED', approved_by_user_id = gen_random_uuid(), approved_at = now(),
    approval_reason = 'Independent Finance approval for golden-path verification.', version = 1
WHERE id = :'fee_rule_id'::uuid;
COMMIT;
SQL

docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_admissions \
  -v admission_cycle_id="${admission_cycle_id}" \
  -v academic_year_id="${academic_year_id}" \
  -v intake_id="${intake_id}" \
  -v application_type_id="${application_type_id}" \
  -v application_type_programme_mapping_id="${application_type_programme_mapping_id}" \
  -v application_type_programme_section_id="${application_type_programme_section_id}" \
  -v application_type_optional_document_requirement_id="${application_type_optional_document_requirement_id}" \
  -v programme_id="${programme_id}" \
  -v fee_structure_id="${fee_structure_id}" \
  -v actor_user_id="${core_user_id}" >/dev/null <<'SQL'
INSERT INTO admission_cycles (id, academic_year_id, intake_id, code, name, opens_at, closes_at, status, maximum_programme_choices, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version)
VALUES (:'admission_cycle_id'::uuid, :'academic_year_id'::uuid, :'intake_id'::uuid, 'E2E-CYCLE-' || left(:'admission_cycle_id', 8), 'Disposable enterprise E2E cycle', now() - interval '1 day', now() + interval '30 days', 'OPEN', 3, now(), now(), null, null, null, null, 0);
INSERT INTO application_types (
    id, code, name, requires_employment_history, requires_referees, is_active,
    finance_fee_structure_id, finance_fee_structure_code, finance_fee_structure_name,
    fee_policy_status, fee_policy_decided_by_user_id, fee_policy_decided_at,
    created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at,
    deleted_by_user_id, version)
VALUES (
    :'application_type_id'::uuid, 'E2E-TYPE-' || left(:'application_type_id', 8),
    'Disposable enterprise E2E application', false, false, true,
    :'fee_structure_id'::uuid, 'APP-' || left(:'fee_structure_id', 8),
    'Disposable application fee structure', 'FEE_STRUCTURE', :'actor_user_id'::uuid,
    now(), now(), now(), null, null, null, null, 0);
INSERT INTO application_type_programme_mappings (
    id, application_type_id, programme_id, programme_code, programme_name, is_active,
    created_at, updated_at, version)
VALUES (
    :'application_type_programme_mapping_id'::uuid, :'application_type_id'::uuid,
    :'programme_id'::uuid, 'E2EIT', 'Bachelor of Science in Information Technology',
    true, now(), now(), 0);
INSERT INTO application_type_sections (
    id, application_type_id, section_code, section_name, is_required, is_repeatable,
    minimum_records, sort_order, is_active, created_at, updated_at, version)
VALUES (
    :'application_type_programme_section_id'::uuid, :'application_type_id'::uuid,
    'PROGRAMME_CHOICES', 'Programme choices', true, true, 1, 10, true, now(), now(), 0);
INSERT INTO application_type_document_requirements (
    id, application_type_id, requirement_code, requirement_name, is_required,
    sort_order, is_active, created_at, updated_at, version)
VALUES (
    :'application_type_optional_document_requirement_id'::uuid,
    :'application_type_id'::uuid, 'OPTIONAL_SUPPORTING_EVIDENCE',
    'Optional supporting evidence', false, 10, true, now(), now(), 0);
SQL

start_options=$(curl -fsS \
  "${admissions_base_url}/api/admissions/applications/start-options?applicantCategoryCode=LOCAL" \
  -H "Authorization: Bearer ${access_token}")
if ! jq -e --arg intakeId "${intake_id}" --arg applicationTypeId "${application_type_id}" --arg programmeId "${programme_id}" '
  (.intakes | any(.id == $intakeId and .maximumProgrammeChoices == 3 and any(.programmes[]; .id == $programmeId and .programmeVersionCode == "E2E.1")))
  and (.routes | any(.applicationTypeId == $applicationTypeId and any(.programmes[]; .id == $programmeId)))
  and (.applicationTypes | any(.id == $applicationTypeId and .fee.required == true and .fee.policyStatus == "FEE_STRUCTURE" and .fee.amount == null and .fee.currencyCode == null))
' <<<"${start_options}" >/dev/null; then
  jq . <<<"${start_options}" >&2
  false
fi

draft_application=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/applications" \
  -H "Authorization: Bearer ${access_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg intake "${intake_id}" --arg type "${application_type_id}" --arg programme "${programme_id}" '{applicantCategoryCode:"LOCAL",intakeId:$intake,applicationTypeId:$type,programmeIds:[$programme]}')")
application_id=$(jq -er '.applicationId // .id' <<<"${draft_application}")
if ! jq -e --arg programmeId "${programme_id}" '
  .status == "DRAFT"
  and .paymentClearanceStatus == "PENDING"
  and .payment == null
  and .canSubmit == true
  and (.programmeChoices | length == 1)
  and (.programmeChoices[0].programmeId == $programmeId)
  and (.programmeChoices[0].programmeCode == "E2EIT")
  and (.programmeChoices[0].programmeVersionCode == "E2E.1")
' <<<"${draft_application}" >/dev/null; then
  jq . <<<"${draft_application}" >&2
  false
fi

projected_draft_application=''
for _ in {1..120}; do
  owned_applications=$(curl -fsS "${admissions_base_url}/api/admissions/applications/mine" \
    -H "Authorization: Bearer ${access_token}")
  projected_draft_application=$(jq -c --arg id "${application_id}" \
    '.[] | select((.applicationId // .id) == $id and .payment.reference != null)' <<<"${owned_applications}")
  if [[ -n "${projected_draft_application}" ]]; then
    break
  fi
  sleep 0.25
done
[[ -n "${projected_draft_application}" ]]
jq -e '.payment.status == "PENDING" and .payment.ratingStatus == "RATED" and .canSubmit == true' \
  <<<"${projected_draft_application}" >/dev/null

if [[ "${pause_after_reference_seconds}" != '0' ]]; then
  sleep "${pause_after_reference_seconds}"
fi

provider_reference="E2E-$(uuidgen)"
confirmed_payment=$(curl -fsS -X POST \
  "${finance_base_url}/api/finance/application-payment-references/reconciled-payments" \
  -H "Authorization: Bearer ${access_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg applicationId "${application_id}" --arg providerRef "${provider_reference}" --arg paidAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" '{applicationId:$applicationId,providerCode:"E2E-BANK",providerTransactionReference:$providerRef,amount:25.00,currencyCode:"USD",paidAt:$paidAt,providerEventFingerprint:$providerRef}')")
jq -e '.status == "PAID" and .ratingStatus == "RATED" and .workflowCleared == true' <<<"${confirmed_payment}" >/dev/null

operations_application=''
for _ in {1..120}; do
  operations_queue=$(curl -fsS "${admissions_base_url}/api/admissions/applications" \
    -H "Authorization: Bearer ${access_token}")
  operations_application=$(jq -c --arg id "${application_id}" \
    '.[] | select(.id == $id and .payment.status == "PAID")' <<<"${operations_queue}")
  if [[ -n "${operations_application}" ]]; then
    break
  fi
  sleep 0.25
done
[[ -n "${operations_application}" ]]
jq -e '.paymentClearanceStatus == "PAID" and .payment.status == "PAID" and .payment.workflowCleared == true and .canSubmit == true' <<<"${operations_application}" >/dev/null

owned_applications=$(curl -fsS "${admissions_base_url}/api/admissions/applications/mine" \
  -H "Authorization: Bearer ${access_token}")
projected_application=$(jq -er --arg id "${application_id}" '.[] | select((.applicationId // .id) == $id)' <<<"${owned_applications}")
jq -e '.payment.status == "PAID" and .payment.ratingStatus == "RATED" and .payment.workflowCleared == true and .canSubmit == true' <<<"${projected_application}" >/dev/null

submission_response=$(curl -sS -w $'\n%{http_code}' -X POST \
  "${admissions_base_url}/api/admissions/applications/${application_id}/submission" \
  -H "Authorization: Bearer ${access_token}")
submission_status=$(tail -n 1 <<<"${submission_response}")
submitted_application=$(sed '$d' <<<"${submission_response}")
if [[ "${submission_status}" -lt 200 || "${submission_status}" -ge 300 ]]; then
  printf 'HTTP %s while submitting the paid application: %s\n' \
    "${submission_status}" "${submitted_application}" >&2
  false
fi
jq -e '.status == "SUBMITTED" and .canEnterReview == true and .canSubmit == false and .payment.workflowCleared == true' <<<"${submitted_application}" >/dev/null

jq -n \
  --arg applicationId "${application_id}" \
  --arg applicationNumber "$(jq -r .applicationNumber <<<"${submitted_application}")" \
  --arg financeReference "$(jq -r .payment.reference <<<"${submitted_application}")" \
  --arg status "$(jq -r .status <<<"${submitted_application}")" \
  --arg paymentStatus "$(jq -r .payment.status <<<"${submitted_application}")" \
  --arg ratingStatus "$(jq -r .payment.ratingStatus <<<"${submitted_application}")" \
  '{result:"PASS",applicationId:$applicationId,applicationNumber:$applicationNumber,financeReference:$financeReference,status:$status,paymentStatus:$paymentStatus,ratingStatus:$ratingStatus,workflowCleared:true}'
