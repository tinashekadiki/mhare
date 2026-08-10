#!/usr/bin/env bash

# Author: Tinashe K
# Proves the authenticated application review -> evaluation -> selection ->
# governed offer -> immutable applicant response -> active student conversion
# lifecycle across Admissions, Student Records, Finance, and Core Identity.

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
postgres_container="${POSTGRES_CONTAINER:-emhare-flyway-postgres}"

test_run_identifier=$(uuidgen | tr '[:upper:]' '[:lower:]')
keycloak_client_id="e2e-offers-${test_run_identifier}"
applicant_email="applicant-${test_run_identifier}@example.test"
staff_email="admissions-${test_run_identifier}@example.test"
test_password='Temporary-E2E-Password-42'

academic_unit_type_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_unit_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_year_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
intake_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_level_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_type_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_version_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_type_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
admission_cycle_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
generated_document_id=$(uuidgen | tr '[:upper:]' '[:lower:]')

keycloak_client_uuid=''
applicant_keycloak_user_id=''
staff_keycloak_user_id=''
applicant_core_user_id=''
staff_core_user_id=''
application_id=''
programme_choice_id=''
requirement_set_id=''
selection_round_id=''
offer_batch_id=''
offer_id=''
conversion_request_id=''
student_id=''
programme_enrolment_id=''
finance_account_id=''

current_step='obtaining Keycloak administration token'
keycloak_admin_token=$(curl -fsS -X POST \
  "${keycloak_base_url}/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=password \
  --data-urlencode client_id=admin-cli \
  --data-urlencode username="${keycloak_admin_username}" \
  --data-urlencode password="${keycloak_admin_password}" | jq -er .access_token)

cleanup_disposable_records() {
  set +e

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
    -v selection_round_id="${selection_round_id:-00000000-0000-0000-0000-000000000000}" \
    -v offer_batch_id="${offer_batch_id:-00000000-0000-0000-0000-000000000000}" \
    -v offer_id="${offer_id:-00000000-0000-0000-0000-000000000000}" \
    -v application_type_id="${application_type_id}" \
    -v admission_cycle_id="${admission_cycle_id}" \
    -v applicant_core_user_id="${applicant_core_user_id:-00000000-0000-0000-0000-000000000000}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM integration_outbox WHERE payload ->> 'applicationId' = :'application_id';
DELETE FROM integration_inbox WHERE payload ->> 'applicationId' = :'application_id';
DELETE FROM offer_status_events_aud WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_status_events WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_responses_aud WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_responses WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_dispatches_aud WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_dispatches WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_conditions_aud WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offer_conditions WHERE offer_id = :'offer_id'::uuid;
DELETE FROM offers_aud WHERE id = :'offer_id'::uuid;
DELETE FROM offers WHERE id = :'offer_id'::uuid;
DELETE FROM offer_batches_aud WHERE id = :'offer_batch_id'::uuid;
DELETE FROM offer_batches WHERE id = :'offer_batch_id'::uuid;
DELETE FROM selection_decisions_aud WHERE selection_round_id = :'selection_round_id'::uuid;
DELETE FROM selection_decisions WHERE selection_round_id = :'selection_round_id'::uuid;
DELETE FROM selection_rounds_aud WHERE id = :'selection_round_id'::uuid;
DELETE FROM selection_rounds WHERE id = :'selection_round_id'::uuid;
DELETE FROM application_evaluations_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_evaluations WHERE application_id = :'application_id'::uuid;
DELETE FROM admission_requirement_sets_aud WHERE id = :'requirement_set_id'::uuid;
DELETE FROM admission_requirement_sets WHERE id = :'requirement_set_id'::uuid;
DELETE FROM application_status_events_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_status_events WHERE application_id = :'application_id'::uuid;
DELETE FROM application_programme_choices_aud WHERE application_id = :'application_id'::uuid;
DELETE FROM application_programme_choices WHERE application_id = :'application_id'::uuid;
DELETE FROM applications_aud WHERE id = :'application_id'::uuid;
DELETE FROM applications WHERE id = :'application_id'::uuid;
DELETE FROM applicants_aud WHERE user_id = :'applicant_core_user_id'::uuid;
DELETE FROM applicants WHERE user_id = :'applicant_core_user_id'::uuid;
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
DELETE FROM intakes WHERE id = :'intake_id'::uuid;
DELETE FROM academic_years WHERE id = :'academic_year_id'::uuid;
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
docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_academic_setup \
  -v academic_unit_type_id="${academic_unit_type_id}" -v academic_unit_id="${academic_unit_id}" \
  -v academic_year_id="${academic_year_id}" -v intake_id="${intake_id}" \
  -v programme_level_id="${programme_level_id}" -v programme_type_id="${programme_type_id}" \
  -v programme_id="${programme_id}" -v programme_version_id="${programme_version_id}" \
  -v actor_user_id="${staff_core_user_id}" >/dev/null <<'SQL'
INSERT INTO academic_unit_types (id, code, name, level_order, is_leaf_allowed, status, created_at, updated_at, version)
VALUES (:'academic_unit_type_id'::uuid, 'E2E_SCHOOL', 'School', 1, true, 'ACTIVE', now(), now(), 0);
INSERT INTO academic_units (id, academic_unit_type_id, code, name, status, created_at, updated_at, version)
VALUES (:'academic_unit_id'::uuid, :'academic_unit_type_id'::uuid, 'E2E_BUS', 'School of Business', 'ACTIVE', now(), now(), 0);
INSERT INTO academic_years (id, name, start_date, end_date, status, created_at, updated_at, version)
VALUES (:'academic_year_id'::uuid, 'E2E Offer Academic Year', current_date, current_date + 365, 'OPEN', now(), now(), 0);
INSERT INTO intakes (id, academic_year_id, code, name, starts_on, ends_on, status, created_at, updated_at, version)
VALUES (:'intake_id'::uuid, :'academic_year_id'::uuid, 'E2E_INTAKE', 'E2E Offer Intake', current_date + 30, current_date + 60, 'OPEN', now(), now(), 0);
INSERT INTO programme_levels (id, code, name, sort_order, status, created_at, updated_at, version)
VALUES (:'programme_level_id'::uuid, 'E2E_UG', 'Undergraduate', 1, 'ACTIVE', now(), now(), 0);
INSERT INTO programme_types (id, code, name, status, created_at, updated_at, version)
VALUES (:'programme_type_id'::uuid, 'E2E_DEGREE', 'Degree', 'ACTIVE', now(), now(), 0);
INSERT INTO programmes (id, owning_academic_unit_id, programme_type_id, programme_level_id, code, name, award_name,
    minimum_duration_periods, maximum_duration_periods, status, created_at, updated_at, version)
VALUES (:'programme_id'::uuid, :'academic_unit_id'::uuid, :'programme_type_id'::uuid, :'programme_level_id'::uuid,
    'E2E_BCOM', 'Bachelor of Commerce', 'Bachelor of Commerce Honours Degree', 8, 12, 'ACTIVE', now(), now(), 0);
INSERT INTO programme_versions (id, programme_id, version_code, effective_from, status, approved_by_user_id, approved_at,
    created_at, updated_at, version)
VALUES (:'programme_version_id'::uuid, :'programme_id'::uuid, 'E2E.1', current_date - 1, 'APPROVED',
    :'actor_user_id'::uuid, now(), now(), now(), 0);
SQL

current_step='creating Admissions cycle fixtures'
docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_admissions \
  -v admission_cycle_id="${admission_cycle_id}" -v academic_year_id="${academic_year_id}" \
  -v intake_id="${intake_id}" -v application_type_id="${application_type_id}" >/dev/null <<'SQL'
INSERT INTO admission_cycles (id, academic_year_id, intake_id, code, name, opens_at, closes_at, status,
    maximum_programme_choices, created_at, updated_at, version)
VALUES (:'admission_cycle_id'::uuid, :'academic_year_id'::uuid, :'intake_id'::uuid,
    'E2E-OFFER-' || left(:'admission_cycle_id', 8), 'Disposable offer workflow cycle',
    now() - interval '1 day', now() + interval '30 days', 'OPEN', 2, now(), now(), 0);
INSERT INTO application_types (id, code, name, requires_employment_history, requires_referees, is_active,
    created_at, updated_at, version)
VALUES (:'application_type_id'::uuid, 'E2E-OFFER-' || left(:'application_type_id', 8),
    'Disposable offer workflow application', false, false, true, now(), now(), 0);
SQL

current_step='creating and submitting applicant application'
draft_application=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/applications" \
  -H "Authorization: Bearer ${applicant_access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg cycle "${admission_cycle_id}" --arg type "${application_type_id}" --arg programme "${programme_id}" \
    '{applicantCategoryCode:"LOCAL",firstName:"Enterprise",lastName:"Applicant",admissionCycleId:$cycle,applicationTypeId:$type,programmeIds:[$programme]}')")
application_id=$(jq -er .id <<<"${draft_application}")
programme_choice_id=$(jq -er .programmeChoices[0].id <<<"${draft_application}")
jq -e '.status == "DRAFT" and .canSubmit == true and .programmeChoices[0].programmeCode == "E2E_BCOM"' \
  <<<"${draft_application}" >/dev/null

submitted_application=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/applications/${application_id}/submission" \
  -H "Authorization: Bearer ${applicant_access_token}")
jq -e '.status == "SUBMITTED"' <<<"${submitted_application}" >/dev/null

current_step='reviewing and evaluating application'
review_application=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/applications/${application_id}/review" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d '{"reason":"Documents and identity evidence verified for governed E2E evaluation."}')
jq -e '.status == "UNDER_REVIEW"' <<<"${review_application}" >/dev/null

requirement_set=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/requirement-sets" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg programme "${programme_id}" --arg type "${application_type_id}" --arg cycle "${admission_cycle_id}" \
    --arg today "$(date +%F)" '{programmeId:$programme,applicationTypeId:$type,admissionCycleId:$cycle,versionCode:"E2E.1",effectiveFrom:$today,minimumTotalPoints:10,requiresEnglish:true,advancedRules:{evidencePolicy:"MANUAL_VERIFIED"},advancedRulesVersion:"1.0"}')")
requirement_set_id=$(jq -er .id <<<"${requirement_set}")
approved_requirement_set=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/requirement-sets/${requirement_set_id}/approve" \
  -H "Authorization: Bearer ${staff_access_token}")
jq -e '.status == "APPROVED" and .advancedRulesVersion == "1.0"' <<<"${approved_requirement_set}" >/dev/null

evaluation=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/applications/${application_id}/choices/${programme_choice_id}/evaluations" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg requirementSet "${requirement_set_id}" \
    '{requirementSetId:$requirementSet,status:"ELIGIBLE",totalPoints:15,rankScore:87.5,missingRequirements:[],ruleResults:{english:true,totalPoints:true},summary:"Approved requirement version E2E.1 satisfied from verified evidence."}')")
jq -e '.status == "ELIGIBLE" and .rankScore == 87.5' <<<"${evaluation}" >/dev/null

current_step='governing selection decision'
curl -fsS -o /dev/null -X POST "${admissions_base_url}/api/admissions/cycles/${admission_cycle_id}/prepare-selection" \
  -H "Authorization: Bearer ${staff_access_token}"
selection_round=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/selection-rounds" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg cycle "${admission_cycle_id}" '{admissionCycleId:$cycle,code:"E2E-MERIT-1",name:"E2E Merit Selection"}')")
selection_round_id=$(jq -er .id <<<"${selection_round}")
curl -fsS -o /dev/null -X POST "${admissions_base_url}/api/admissions/selection-rounds/${selection_round_id}/open" \
  -H "Authorization: Bearer ${staff_access_token}"
selection_decision=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/selection-rounds/${selection_round_id}/decisions" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg choice "${programme_choice_id}" \
    '{programmeChoiceId:$choice,decision:"SELECT",rankPosition:1,quotaTypeCode:"MERIT",reason:"Ranked first within approved merit quota."}')")
jq -e '.decision == "SELECT" and .rankPosition == 1 and .quotaTypeCode == "MERIT"' <<<"${selection_decision}" >/dev/null
approved_round=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/selection-rounds/${selection_round_id}/approve" \
  -H "Authorization: Bearer ${staff_access_token}")
jq -e '.status == "APPROVED"' <<<"${approved_round}" >/dev/null

current_step='creating governed offer batch and offer'
offer_batch=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/offer-batches" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg cycle "${admission_cycle_id}" --arg round "${selection_round_id}" \
    '{admissionCycleId:$cycle,selectionRoundId:$round,code:"E2E-OFFERS-1",name:"E2E Controlled Offer Release",scopeType:"INSTITUTION",scopeId:null}')")
offer_batch_id=$(jq -er .id <<<"${offer_batch}")
approved_batch=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/offer-batches/${offer_batch_id}/approve" \
  -H "Authorization: Bearer ${staff_access_token}")
jq -e '.status == "APPROVED"' <<<"${approved_batch}" >/dev/null

acceptance_deadline=$(jq -nr 'now + 2592000 | todateiso8601')
commencement_date=$(date -v+60d +%F)
offer=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/offers" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg batch "${offer_batch_id}" --arg choice "${programme_choice_id}" \
    --arg deadline "${acceptance_deadline}" --arg commencement "${commencement_date}" --arg document "${generated_document_id}" \
    '{offerBatchId:$batch,programmeChoiceId:$choice,offerType:"CONDITIONAL",conditionsText:"Submit the original certificate before registration.",acceptanceDeadline:$deadline,commencementDate:$commencement,generatedDocumentId:$document,conditions:[{code:"ORIGINAL_CERTIFICATE",description:"Submit the original certificate before registration.",required:true}]}')")
offer_id=$(jq -er .id <<<"${offer}")
condition_id=$(jq -er .conditions[0].id <<<"${offer}")
jq -e '.status == "DRAFT" and .offerType == "CONDITIONAL" and .conditions[0].status == "PENDING"' <<<"${offer}" >/dev/null

approved_offer=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/offers/${offer_id}/approve" \
  -H "Authorization: Bearer ${staff_access_token}")
jq -e '.status == "APPROVED" and .generatedDocumentId != null' <<<"${approved_offer}" >/dev/null
sent_offer=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/offers/${offer_id}/dispatch" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg email "${applicant_email}" --arg provider "E2E-${test_run_identifier}" \
    '{deliveryMethodCode:"EMAIL",sentTo:$email,providerMessageId:$provider}')")
jq -e '.status == "SENT" and .sentAt != null' <<<"${sent_offer}" >/dev/null

current_step='accepting offer and resolving required condition'
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

resolved_offer=$(curl -fsS -X POST \
  "${admissions_base_url}/api/admissions/offers/${offer_id}/conditions/${condition_id}/resolve" \
  -H "Authorization: Bearer ${staff_access_token}" -H 'Content-Type: application/json' \
  -d '{"resolution":"SATISFIED","notes":"Original certificate verified against the issuing body record."}')
jq -e '.status == "ACCEPTED" and .conditions[0].status == "SATISFIED" and .conditions[0].resolvedAt != null' \
  <<<"${resolved_offer}" >/dev/null

current_step='waiting for cross-service student conversion'
conversion_snapshot=''
for attempt in {1..30}; do
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
for attempt in {1..30}; do
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

current_step='closing governed offer batch'
dispatched_batch=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/offer-batches/${offer_batch_id}/dispatch" \
  -H "Authorization: Bearer ${staff_access_token}")
jq -e '.status == "DISPATCHED" and .dispatchedAt != null' <<<"${dispatched_batch}" >/dev/null
closed_batch=$(curl -fsS -X POST "${admissions_base_url}/api/admissions/offer-batches/${offer_batch_id}/close" \
  -H "Authorization: Bearer ${staff_access_token}")
jq -e '.status == "CLOSED" and .closedAt != null' <<<"${closed_batch}" >/dev/null

current_step='validating audit evidence'
audit_counts=$(docker exec -i "${postgres_container}" psql -qAt -U postgres -d emhare_admissions \
  -v application_id="${application_id}" -v offer_id="${offer_id}" <<'SQL'
SELECT json_build_object(
  'applicationEvents', (SELECT count(*) FROM application_status_events WHERE application_id = :'application_id'::uuid),
  'offerEvents', (SELECT count(*) FROM offer_status_events WHERE offer_id = :'offer_id'::uuid),
  'offerAudits', (SELECT count(*) FROM offers_aud WHERE id = :'offer_id'::uuid),
  'conditionAudits', (SELECT count(*) FROM offer_conditions_aud WHERE offer_id = :'offer_id'::uuid)
)::text;
SQL
)
jq -e '.applicationEvents >= 5 and .offerEvents >= 4 and .offerAudits >= 4 and .conditionAudits >= 2' \
  <<<"${audit_counts}" >/dev/null

jq -n \
  --arg applicationId "${application_id}" \
  --arg offerId "${offer_id}" \
  --arg offerNumber "$(jq -r .offerNumber <<<"${resolved_offer}")" \
  --arg applicationStatus "CONVERTED" \
  --arg offerStatus "$(jq -r .status <<<"${converted_offer}")" \
  --arg conditionStatus "$(jq -r .conditions[0].status <<<"${resolved_offer}")" \
  --arg batchStatus "$(jq -r .status <<<"${closed_batch}")" \
  --arg conversionRequestId "${conversion_request_id}" \
  --arg studentId "${student_id}" \
  --arg studentNumber "$(jq -r .studentNumber <<<"${conversion_snapshot}")" \
  --arg financeAccountNumber "$(jq -r .accountNumber <<<"${finance_snapshot}")" \
  --argjson audit "${audit_counts}" \
  '{result:"PASS",applicationId:$applicationId,offerId:$offerId,offerNumber:$offerNumber,
    applicationStatus:$applicationStatus,offerStatus:$offerStatus,conditionStatus:$conditionStatus,
    batchStatus:$batchStatus,conversionRequestId:$conversionRequestId,studentId:$studentId,
    studentNumber:$studentNumber,financeAccountNumber:$financeAccountNumber,
    baseCurrency:"USD",portalRole:"STUDENT",immutableResponseGuard:true,audit:$audit}'
