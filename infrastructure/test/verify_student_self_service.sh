#!/usr/bin/env bash
set -euo pipefail

# Author: Tinashe K

current_step="initialising student self-service verification"
report_failure() {
  local exit_status=$?
  printf 'FAIL: %s (exit %s)\n' "${current_step}" "${exit_status}" >&2
  exit "${exit_status}"
}
trap report_failure ERR

gateway_base_url="${GATEWAY_BASE_URL:-http://localhost:8080}"
keycloak_base_url="${KEYCLOAK_BASE_URL:-http://localhost:8099}"
postgres_container="${POSTGRES_CONTAINER:-emhare-postgres}"
test_password="${TEST_PASSWORD:-Temporary-Student-Self-Service-42}"
run_identifier="$(uuidgen | tr '[:upper:]' '[:lower:]')"
short_identifier="${run_identifier%%-*}"
code_identifier="$(printf '%s' "${short_identifier}" | tr '[:lower:]' '[:upper:]')"
programme_code="S${code_identifier:0:4}"
keycloak_client_id="e2e-student-self-${run_identifier}"

uuid() {
  uuidgen | tr '[:upper:]' '[:lower:]'
}

database_sql() {
  local database="$1"
  docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d "${database}"
}

database_value() {
  local database="$1"
  local sql="$2"
  docker exec "${postgres_container}" psql -q -A -t -v ON_ERROR_STOP=1 -U postgres -d "${database}" -c "${sql}"
}

current_step="obtaining Keycloak administration token"
admin_token="$(curl -fsS -X POST "${keycloak_base_url}/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode client_id=admin-cli \
  --data-urlencode username=admin \
  --data-urlencode password=admin \
  --data-urlencode grant_type=password | jq -er '.access_token')"
curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/clients" \
  -H "Authorization: Bearer ${admin_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg clientId "${keycloak_client_id}" \
    '{clientId:$clientId,enabled:true,publicClient:true,directAccessGrantsEnabled:true,standardFlowEnabled:false,protocol:"openid-connect"}')"
student_role="$(curl -fsS "${keycloak_base_url}/admin/realms/emhare/roles/student" \
  -H "Authorization: Bearer ${admin_token}")"

create_student_identity() {
  local label="$1"
  local email="student-${label}-${short_identifier}@example.test"
  curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/users" \
    -H "Authorization: Bearer ${admin_token}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg email "${email}" --arg password "${test_password}" --arg label "${label}" \
      '{username:$email,email:$email,emailVerified:true,enabled:true,firstName:$label,lastName:"Self Service",credentials:[{type:"password",value:$password,temporary:false}]}')"
  local keycloak_user_id
  keycloak_user_id="$(curl -fsS -G "${keycloak_base_url}/admin/realms/emhare/users" \
    -H "Authorization: Bearer ${admin_token}" \
    --data-urlencode username="${email}" \
    --data-urlencode exact=true | jq -er '.[0].id')"
  curl -fsS -o /dev/null -X POST \
    "${keycloak_base_url}/admin/realms/emhare/users/${keycloak_user_id}/role-mappings/realm" \
    -H "Authorization: Bearer ${admin_token}" \
    -H 'Content-Type: application/json' \
    -d "[${student_role}]"
  local access_token
  access_token="$(curl -fsS -X POST "${keycloak_base_url}/realms/emhare/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode client_id="${keycloak_client_id}" \
    --data-urlencode username="${email}" \
    --data-urlencode password="${test_password}" \
    --data-urlencode grant_type=password | jq -er '.access_token')"
  local local_user_id
  local_user_id="$(curl -fsS "${gateway_base_url}/api/core/me" \
    -H "Authorization: Bearer ${access_token}" | jq -er '.user.id')"
  jq -nc \
    --arg email "${email}" \
    --arg keycloakUserId "${keycloak_user_id}" \
    --arg localUserId "${local_user_id}" \
    --arg accessToken "${access_token}" \
    '{email:$email,keycloakUserId:$keycloakUserId,localUserId:$localUserId,accessToken:$accessToken}'
}

current_step="creating primary and secondary student identities"
primary_identity="$(create_student_identity primary)"
secondary_identity="$(create_student_identity secondary)"
primary_email="$(jq -r '.email' <<<"${primary_identity}")"
primary_local_user_id="$(jq -r '.localUserId' <<<"${primary_identity}")"
primary_access_token="$(jq -r '.accessToken' <<<"${primary_identity}")"
secondary_email="$(jq -r '.email' <<<"${secondary_identity}")"
secondary_local_user_id="$(jq -r '.localUserId' <<<"${secondary_identity}")"
secondary_access_token="$(jq -r '.accessToken' <<<"${secondary_identity}")"

academic_unit_id="$(database_value emhare_academic_setup "SELECT id FROM academic_units WHERE status = 'ACTIVE' AND deleted_at IS NULL AND id NOT IN (SELECT parent_id FROM academic_units WHERE parent_id IS NOT NULL AND deleted_at IS NULL) ORDER BY created_at LIMIT 1;")"
[[ -n "${academic_unit_id}" ]] || { echo 'An active leaf academic unit is required.' >&2; exit 1; }

academic_year_id="$(database_value emhare_academic_setup "SELECT id FROM academic_years WHERE status = 'OPEN' AND deleted_at IS NULL ORDER BY start_date LIMIT 1;")"
academic_year_start="$(database_value emhare_academic_setup "SELECT start_date FROM academic_years WHERE id = '${academic_year_id}';")"
academic_year_end="$(database_value emhare_academic_setup "SELECT end_date FROM academic_years WHERE id = '${academic_year_id}';")"
academic_period_type_id="$(uuid)"
academic_period_id="$(uuid)"
intake_id="$(uuid)"
programme_level_id="$(database_value emhare_academic_setup "SELECT id FROM programme_levels WHERE code = 'UG' AND status = 'ACTIVE' AND deleted_at IS NULL ORDER BY created_at LIMIT 1;")"
programme_type_id="$(uuid)"
programme_id="$(uuid)"
programme_version_id="$(uuid)"
compulsory_module_id="$(uuid)"
elective_module_id="$(uuid)"
compulsory_curriculum_module_id="$(uuid)"
elective_curriculum_module_id="$(uuid)"
primary_student_id="$(uuid)"
primary_enrolment_id="$(uuid)"
secondary_student_id="$(uuid)"
secondary_enrolment_id="$(uuid)"

current_step="creating governed academic registration fixture"
database_sql emhare_academic_setup <<SQL
BEGIN;
DO \$\$ BEGIN
  PERFORM pg_advisory_xact_lock(hashtext('emhare-student-self-service-academic-fixture'));
END \$\$;
INSERT INTO academic_period_types
  (id, code, name, sort_order, status, change_reason, created_at, updated_at, created_by_user_id, version)
VALUES
  ('${academic_period_type_id}', 'SELF_${code_identifier}', 'Semester', (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM academic_period_types), 'ACTIVE', 'Student self-service verification.', now(), now(), '${primary_local_user_id}', 0);
INSERT INTO academic_periods
  (id, academic_year_id, academic_period_type_id, code, name, start_date, end_date, status, change_reason, created_at, updated_at, created_by_user_id, version)
VALUES
  ('${academic_period_id}', '${academic_year_id}', '${academic_period_type_id}', 'SELF-S2-${code_identifier}', 'Semester 2', DATE '${academic_year_start}', DATE '${academic_year_end}', 'OPEN', 'Student self-service verification.', now(), now(), '${primary_local_user_id}', 0);
INSERT INTO intakes
  (id, academic_year_id, code, name, starts_on, ends_on, status, change_reason, created_at, updated_at, created_by_user_id, version)
VALUES
  ('${intake_id}', '${academic_year_id}', 'SELF-${code_identifier}', 'Self-service intake', DATE '${academic_year_start}', DATE '${academic_year_end}', 'OPEN', 'Student self-service verification.', now(), now(), '${primary_local_user_id}', 0);
INSERT INTO programme_types
  (id, code, name, status, created_at, updated_at, created_by_user_id, version)
VALUES
  ('${programme_type_id}', 'SELF_${code_identifier}', 'Self-service degree', 'ACTIVE', now(), now(), '${primary_local_user_id}', 0);
INSERT INTO programmes
  (id, owning_academic_unit_id, programme_type_id, programme_level_id, code, name, award_name, minimum_duration_periods, maximum_duration_periods, status, change_reason, created_at, updated_at, created_by_user_id, version)
VALUES
  ('${programme_id}', '${academic_unit_id}', '${programme_type_id}', '${programme_level_id}', '${programme_code}', 'Self-service Biology', 'Bachelor of Science Honours', 8, 8, 'ACTIVE', 'Student self-service verification.', now(), now(), '${primary_local_user_id}', 0);
INSERT INTO programme_versions
  (id, programme_id, version_code, effective_from, status, approved_by_user_id, approved_at, created_at, updated_at, created_by_user_id, version)
VALUES
  ('${programme_version_id}', '${programme_id}', '2027.1', DATE '2026-08-01', 'DRAFT', NULL, NULL, now(), now(), '${primary_local_user_id}', 0);
INSERT INTO modules
  (id, owning_academic_unit_id, code, name, description, credit_value, academic_level, status, created_at, updated_at, created_by_user_id, version)
VALUES
  ('${compulsory_module_id}', '${academic_unit_id}', 'BIO4C${code_identifier}', 'Advanced Cell Biology', 'Compulsory self-service verification Module.', 12.00, 2, 'ACTIVE', now(), now(), '${primary_local_user_id}', 0),
  ('${elective_module_id}', '${academic_unit_id}', 'BIO4E${code_identifier}', 'Plant Ecology', 'Elective self-service verification Module.', 12.00, 2, 'ACTIVE', now(), now(), '${primary_local_user_id}', 0);
INSERT INTO curriculum_modules
  (id, programme_version_id, module_id, period_number, module_type, credit_value, minimum_mark_required, sort_order, created_at, updated_at, created_by_user_id, version)
VALUES
  ('${compulsory_curriculum_module_id}', '${programme_version_id}', '${compulsory_module_id}', 4, 'COMPULSORY', 12.00, 50.00, 1, now(), now(), '${primary_local_user_id}', 0),
  ('${elective_curriculum_module_id}', '${programme_version_id}', '${elective_module_id}', 4, 'ELECTIVE', 12.00, 50.00, 2, now(), now(), '${primary_local_user_id}', 0);
UPDATE programme_versions
SET status = 'APPROVED', approved_by_user_id = '${primary_local_user_id}', approved_at = now(), updated_at = now(), version = 1
WHERE id = '${programme_version_id}';
COMMIT;
SQL

current_step="creating linked active student records"
database_sql emhare_student_records <<SQL
INSERT INTO students
  (id, student_number, user_id, source_applicant_id, source_applicant_number, source_application_id, source_offer_id, applicant_category_code, first_name, last_name, primary_email, status, activated_at, created_at, updated_at, created_by_user_id, version)
VALUES
  ('${primary_student_id}', 'STU-SELF-${short_identifier}-1', '${primary_local_user_id}', '$(uuid)', 'APP-SELF-${short_identifier}-1', '$(uuid)', '$(uuid)', 'LOCAL', 'Primary', 'Self Service', '${primary_email}', 'ACTIVE', now(), now(), now(), '${primary_local_user_id}', 0),
  ('${secondary_student_id}', 'STU-SELF-${short_identifier}-2', '${secondary_local_user_id}', '$(uuid)', 'APP-SELF-${short_identifier}-2', '$(uuid)', '$(uuid)', 'LOCAL', 'Secondary', 'Self Service', '${secondary_email}', 'ACTIVE', now(), now(), now(), '${secondary_local_user_id}', 0);
INSERT INTO student_programme_enrolments
  (id, student_id, source_offer_id, source_programme_choice_id, programme_id, programme_version_id, programme_code, programme_name, intake_id, commencement_date, status, status_reason, approved_by_user_id, approved_at, created_at, updated_at, created_by_user_id, version)
VALUES
  ('${primary_enrolment_id}', '${primary_student_id}', '$(uuid)', '$(uuid)', '${programme_id}', '${programme_version_id}', '${programme_code}', 'Self-service Biology', '${intake_id}', DATE '2026-08-08', 'ACTIVE', 'Provisioning completed.', '${primary_local_user_id}', now(), now(), now(), '${primary_local_user_id}', 0),
  ('${secondary_enrolment_id}', '${secondary_student_id}', '$(uuid)', '$(uuid)', '${programme_id}', '${programme_version_id}', '${programme_code}', 'Self-service Biology', '${intake_id}', DATE '2026-08-08', 'ACTIVE', 'Provisioning completed.', '${secondary_local_user_id}', now(), now(), now(), '${secondary_local_user_id}', 0);
SQL

current_step="loading the owned student workspace through the gateway"
workspace_response="$(curl -fsS "${gateway_base_url}/api/student-records/me" -H "Authorization: Bearer ${primary_access_token}")"
jq -e --arg studentId "${primary_student_id}" --arg enrolmentId "${primary_enrolment_id}" \
  '.id == $studentId and .status == "ACTIVE" and (.programmeEnrolments | any(.id == $enrolmentId and .status == "ACTIVE"))' \
  <<<"${workspace_response}" >/dev/null

current_step="creating the owned draft registration through the gateway"
registration_response="$(curl -fsS -X POST "${gateway_base_url}/api/student-records/registrations/mine" \
  -H "Authorization: Bearer ${primary_access_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc \
    --arg enrolmentId "${primary_enrolment_id}" \
    --arg academicPeriodId "${academic_period_id}" \
    --arg electiveId "${elective_curriculum_module_id}" \
    '{programmeEnrolmentId:$enrolmentId,academicPeriodId:$academicPeriodId,programmePeriodNumber:4,selectedElectiveCurriculumModuleIds:[$electiveId]}')")"
registration_id="$(jq -er '.id' <<<"${registration_response}")"
jq -e \
  '.status == "DRAFT" and .programmePeriodNumber == 4 and (.modules | length == 2) and (.modules | any(.selectionSource == "AUTO_COMPULSORY")) and (.modules | any(.selectionSource == "STUDENT_ELECTIVE"))' \
  <<<"${registration_response}" >/dev/null

current_step="proving cross-student submission is rejected"
cross_student_response_file="$(mktemp /tmp/emhare-student-cross-owner-response.XXXXXX)"
cross_student_status="$(curl -sS -o "${cross_student_response_file}" -w '%{http_code}' \
  -X POST "${gateway_base_url}/api/student-records/registrations/mine/${registration_id}/submit" \
  -H "Authorization: Bearer ${secondary_access_token}" \
  -H 'Content-Type: application/json' \
  -d '{"expectedVersion":0,"declarationAccepted":true}')"
[[ "${cross_student_status}" == "400" ]] || {
  echo "Cross-student submission returned ${cross_student_status}; expected 400." >&2
  exit 1
}

current_step="submitting the owned draft registration"
submitted_response="$(curl -fsS -X POST \
  "${gateway_base_url}/api/student-records/registrations/mine/${registration_id}/submit" \
  -H "Authorization: Bearer ${primary_access_token}" \
  -H 'Content-Type: application/json' \
  -d '{"expectedVersion":0,"declarationAccepted":true}')"
jq -e '.status == "SUBMITTED" and .version == 1' <<<"${submitted_response}" >/dev/null

current_step="verifying durable registration events and notification outbox records"
status_event_count="$(database_value emhare_student_records "SELECT COUNT(*) FROM registration_status_events WHERE registration_session_id = '${registration_id}';")"
notification_event_count="$(database_value emhare_student_records "SELECT COUNT(*) FROM integration_outbox WHERE payload ->> 'idempotencyKey' LIKE '%${registration_id}%' AND event_type = 'notifications.requested.v1';")"
[[ "${status_event_count}" == "2" ]] || { echo 'Expected draft and submitted status events.' >&2; exit 1; }
[[ "${notification_event_count}" == "2" ]] || { echo 'Expected email and in-app notification outbox events.' >&2; exit 1; }

jq -n \
  --arg status PASS \
  --arg runId "${run_identifier}" \
  --arg primaryStudentId "${primary_student_id}" \
  --arg secondaryStudentId "${secondary_student_id}" \
  --arg registrationId "${registration_id}" \
  --arg programmePeriodNumber "$(jq -r '.programmePeriodNumber' <<<"${submitted_response}")" \
  --arg registrationStatus "$(jq -r '.status' <<<"${submitted_response}")" \
  --arg statusEvents "${status_event_count}" \
  --arg notificationEvents "${notification_event_count}" \
  --arg crossStudentStatus "${cross_student_status}" \
  '{status:$status,runId:$runId,primaryStudentId:$primaryStudentId,secondaryStudentId:$secondaryStudentId,registrationId:$registrationId,programmePeriodNumber:($programmePeriodNumber|tonumber),registrationStatus:$registrationStatus,statusEvents:($statusEvents|tonumber),notificationEvents:($notificationEvents|tonumber),crossStudentSubmissionHttpStatus:($crossStudentStatus|tonumber),retainedEvidence:true}'
