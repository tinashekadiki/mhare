#!/usr/bin/env bash

# Author: Tinashe K
# Runs the authenticated Admissions -> Finance -> Admissions happy path against
# isolated local services and removes every disposable business record it creates.

set -euo pipefail

keycloak_base_url="${KEYCLOAK_BASE_URL:-http://localhost:8099}"
keycloak_realm="${KEYCLOAK_REALM:-emhare}"
keycloak_admin_username="${KEYCLOAK_ADMIN_USERNAME:-admin}"
keycloak_admin_password="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
core_identity_base_url="${CORE_IDENTITY_BASE_URL:-http://localhost:18081}"
admissions_base_url="${ADMISSIONS_BASE_URL:-http://localhost:18083}"
finance_base_url="${FINANCE_BASE_URL:-http://localhost:18084}"
postgres_container="${POSTGRES_CONTAINER:-emhare-flyway-postgres}"
pause_after_reference_seconds="${E2E_PAUSE_AFTER_REFERENCE_SECONDS:-0}"

test_run_identifier=$(uuidgen | tr '[:upper:]' '[:lower:]')
keycloak_client_id="e2e-admissions-${test_run_identifier}"
test_email="${keycloak_client_id}@example.test"
test_password='Temporary-E2E-Password-42'
admission_cycle_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_year_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
intake_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_unit_type_root_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_unit_type_leaf_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_unit_root_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_unit_leaf_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_level_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_type_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_version_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
module_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
curriculum_module_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_type_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_fee_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
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
DELETE FROM application_programme_choices_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_programme_choices WHERE application_id = :'application_id'::uuid;
DELETE FROM applications_aud WHERE id = :'application_id'::uuid;
DELETE FROM applications WHERE id = :'application_id'::uuid;
SQL
  fi

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
    -v application_fee_id="${application_fee_id}" \
    -v application_type_id="${application_type_id}" \
    -v admission_cycle_id="${admission_cycle_id}" >/dev/null <<'SQL'
DELETE FROM application_fees_aud WHERE id = :'application_fee_id'::uuid;
DELETE FROM application_fees WHERE id = :'application_fee_id'::uuid;
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
    -v academic_year_id="${academic_year_id}" \
    -v academic_unit_leaf_id="${academic_unit_leaf_id}" \
    -v academic_unit_root_id="${academic_unit_root_id}" \
    -v academic_unit_type_leaf_id="${academic_unit_type_leaf_id}" \
    -v academic_unit_type_root_id="${academic_unit_type_root_id}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM curriculum_modules WHERE id = :'curriculum_module_id'::uuid;
DELETE FROM programme_versions WHERE id = :'programme_version_id'::uuid;
DELETE FROM programmes WHERE id = :'programme_id'::uuid;
DELETE FROM modules WHERE id = :'module_id'::uuid;
DELETE FROM programme_types WHERE id = :'programme_type_id'::uuid;
DELETE FROM programme_levels WHERE id = :'programme_level_id'::uuid;
DELETE FROM intakes WHERE id = :'intake_id'::uuid;
DELETE FROM academic_years WHERE id = :'academic_year_id'::uuid;
DELETE FROM academic_units WHERE id = :'academic_unit_leaf_id'::uuid;
DELETE FROM academic_units WHERE id = :'academic_unit_root_id'::uuid;
DELETE FROM academic_unit_types WHERE id = :'academic_unit_type_leaf_id'::uuid;
DELETE FROM academic_unit_types WHERE id = :'academic_unit_type_root_id'::uuid;
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
  -v programme_level_id="${programme_level_id}" \
  -v programme_type_id="${programme_type_id}" \
  -v programme_id="${programme_id}" \
  -v programme_version_id="${programme_version_id}" \
  -v module_id="${module_id}" \
  -v curriculum_module_id="${curriculum_module_id}" \
  -v actor_user_id="${keycloak_user_id}" >/dev/null <<'SQL'
INSERT INTO academic_unit_types (id, code, name, level_order, is_leaf_allowed, status, created_at, updated_at, version)
VALUES
  (:'academic_unit_type_root_id'::uuid, 'E2E_FACULTY', 'Faculty', 1, false, 'ACTIVE', now(), now(), 0),
  (:'academic_unit_type_leaf_id'::uuid, 'E2E_DEPARTMENT', 'Department', 2, true, 'ACTIVE', now(), now(), 0);
INSERT INTO academic_units (id, academic_unit_type_id, parent_id, code, name, status, created_at, updated_at, version)
VALUES
  (:'academic_unit_root_id'::uuid, :'academic_unit_type_root_id'::uuid, null, 'E2E_SCI', 'Faculty of Science', 'ACTIVE', now(), now(), 0),
  (:'academic_unit_leaf_id'::uuid, :'academic_unit_type_leaf_id'::uuid, :'academic_unit_root_id'::uuid, 'E2E_COMP', 'Department of Computing', 'ACTIVE', now(), now(), 0);
INSERT INTO academic_years (id, name, start_date, end_date, status, created_at, updated_at, version)
VALUES (:'academic_year_id'::uuid, '2027 E2E Academic Year', DATE '2027-01-01', DATE '2027-12-31', 'OPEN', now(), now(), 0);
INSERT INTO intakes (id, academic_year_id, code, name, starts_on, ends_on, status, created_at, updated_at, version)
VALUES (:'intake_id'::uuid, :'academic_year_id'::uuid, 'E2E_JAN_2027', 'January 2027 Intake', DATE '2027-01-01', DATE '2027-03-31', 'OPEN', now(), now(), 0);
INSERT INTO programme_levels (id, code, name, sort_order, status, created_at, updated_at, version)
VALUES (:'programme_level_id'::uuid, 'E2E_UG', 'Undergraduate', 1, 'ACTIVE', now(), now(), 0);
INSERT INTO programme_types (id, code, name, status, created_at, updated_at, version)
VALUES (:'programme_type_id'::uuid, 'E2E_DEGREE', 'Degree', 'ACTIVE', now(), now(), 0);
INSERT INTO modules (id, owning_academic_unit_id, code, name, description, credit_value, academic_level, status, created_at, updated_at, version)
VALUES (:'module_id'::uuid, :'academic_unit_leaf_id'::uuid, 'E2E_CSC101', 'Programming Fundamentals', 'Foundational programming and problem solving.', 12.00, 1, 'ACTIVE', now(), now(), 0);
INSERT INTO programmes (id, owning_academic_unit_id, programme_type_id, programme_level_id, code, name, award_name, minimum_duration_periods, maximum_duration_periods, status, created_at, updated_at, version)
VALUES (:'programme_id'::uuid, :'academic_unit_leaf_id'::uuid, :'programme_type_id'::uuid, :'programme_level_id'::uuid, 'E2E_BSCIT', 'Bachelor of Science in Information Technology', 'Bachelor of Science Honours Degree', 8, 12, 'ACTIVE', now(), now(), 0);
INSERT INTO programme_versions (id, programme_id, version_code, effective_from, status, created_at, updated_at, version)
VALUES (:'programme_version_id'::uuid, :'programme_id'::uuid, '2027.1', DATE '2027-01-01', 'DRAFT', now(), now(), 0);
INSERT INTO curriculum_modules (id, programme_version_id, module_id, period_number, module_type, credit_value, minimum_mark_required, sort_order, created_at, updated_at, version)
VALUES (:'curriculum_module_id'::uuid, :'programme_version_id'::uuid, :'module_id'::uuid, 1, 'COMPULSORY', 12.00, 50.00, 1, now(), now(), 0);
UPDATE programme_versions
SET status = 'APPROVED', approved_by_user_id = :'actor_user_id'::uuid, approved_at = now(), updated_at = now(), version = 1
WHERE id = :'programme_version_id'::uuid;
SQL

docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_admissions \
  -v admission_cycle_id="${admission_cycle_id}" \
  -v academic_year_id="${academic_year_id}" \
  -v intake_id="${intake_id}" \
  -v application_type_id="${application_type_id}" \
  -v application_fee_id="${application_fee_id}" >/dev/null <<'SQL'
INSERT INTO admission_cycles (id, academic_year_id, intake_id, code, name, opens_at, closes_at, status, maximum_programme_choices, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version)
VALUES (:'admission_cycle_id'::uuid, :'academic_year_id'::uuid, :'intake_id'::uuid, 'E2E-CYCLE-' || left(:'admission_cycle_id', 8), 'Disposable enterprise E2E cycle', now() - interval '1 day', now() + interval '30 days', 'OPEN', 3, now(), now(), null, null, null, null, 0);
INSERT INTO application_types (id, code, name, requires_employment_history, requires_referees, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version)
VALUES (:'application_type_id'::uuid, 'E2E-TYPE-' || left(:'application_type_id', 8), 'Disposable enterprise E2E application', false, false, true, now(), now(), null, null, null, null, 0);
INSERT INTO application_fees (id, application_type_id, applicant_category_code, currency_code, amount, effective_from, effective_to, is_active, created_at, updated_at, created_by_user_id, modified_by_user_id, deleted_at, deleted_by_user_id, version)
VALUES (:'application_fee_id'::uuid, :'application_type_id'::uuid, 'LOCAL', 'USD', 25.00, current_date - 1, null, true, now(), now(), null, null, null, null, 0);
SQL

start_options=$(curl -fsS \
  "${admissions_base_url}/api/admissions/applications/start-options?applicantCategoryCode=LOCAL" \
  -H "Authorization: Bearer ${access_token}")
jq -e --arg cycleId "${admission_cycle_id}" --arg applicationTypeId "${application_type_id}" --arg programmeId "${programme_id}" '
  (.admissionCycles | any(.id == $cycleId and .maximumProgrammeChoices == 3 and any(.programmes[]; .id == $programmeId and .programmeVersionCode == "2027.1")))
  and (.applicationTypes | any(.id == $applicationTypeId and .fee.required == true and .fee.amount == 25 and .fee.currencyCode == "USD"))
' <<<"${start_options}" >/dev/null

draft_application=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/applications" \
  -H "Authorization: Bearer ${access_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg cycle "${admission_cycle_id}" --arg type "${application_type_id}" --arg programme "${programme_id}" '{applicantCategoryCode:"LOCAL",firstName:"Enterprise",lastName:"Applicant",admissionCycleId:$cycle,applicationTypeId:$type,programmeIds:[$programme]}')")
application_id=$(jq -er '.applicationId // .id' <<<"${draft_application}")
jq -e --arg programmeId "${programme_id}" '
  .status == "DRAFT"
  and .paymentClearanceStatus == "PENDING"
  and .payment == null
  and .canSubmit == false
  and (.programmeChoices | length == 1)
  and (.programmeChoices[0].programmeId == $programmeId)
  and (.programmeChoices[0].programmeCode == "E2E_BSCIT")
  and (.programmeChoices[0].programmeVersionCode == "2027.1")
' <<<"${draft_application}" >/dev/null

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
jq -e '.payment.status == "PENDING" and .payment.ratingStatus == "RATED" and .canSubmit == false' \
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

submitted_application=$(curl -fsS -X POST \
  "${admissions_base_url}/api/admissions/applications/${application_id}/submission" \
  -H "Authorization: Bearer ${access_token}")
jq -e '.status == "SUBMITTED" and .canEnterReview == true and .canSubmit == false and .payment.workflowCleared == true' <<<"${submitted_application}" >/dev/null

jq -n \
  --arg applicationId "${application_id}" \
  --arg applicationNumber "$(jq -r .applicationNumber <<<"${submitted_application}")" \
  --arg financeReference "$(jq -r .payment.reference <<<"${submitted_application}")" \
  --arg status "$(jq -r .status <<<"${submitted_application}")" \
  --arg paymentStatus "$(jq -r .payment.status <<<"${submitted_application}")" \
  --arg ratingStatus "$(jq -r .payment.ratingStatus <<<"${submitted_application}")" \
  '{result:"PASS",applicationId:$applicationId,applicationNumber:$applicationNumber,financeReference:$financeReference,status:$status,paymentStatus:$paymentStatus,ratingStatus:$ratingStatus,workflowCleared:true}'
