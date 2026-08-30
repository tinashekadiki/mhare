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
keycloak_realm="${KEYCLOAK_REALM:-emhare}"
keycloak_admin_username="${KEYCLOAK_ADMIN_USERNAME:-admin}"
keycloak_admin_password="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
postgres_container="${POSTGRES_CONTAINER:-emhare-postgres}"
test_password="${TEST_PASSWORD:-Temporary-Student-Self-Service-42}"
run_identifier="$(uuidgen | tr '[:upper:]' '[:lower:]')"
short_identifier="${run_identifier%%-*}"
code_identifier="$(printf '%s' "${short_identifier}" | tr '[:lower:]' '[:upper:]')"
programme_code="S${code_identifier:0:4}"
keycloak_client_id="e2e-student-self-${run_identifier}"
primary_email="student-primary-${short_identifier}@example.test"
secondary_email="student-secondary-${short_identifier}@example.test"
fixture_manifest_path="${SELF_SERVICE_FIXTURE_MANIFEST_PATH:-}"

admin_token=''
keycloak_client_uuid=''
primary_keycloak_user_id=''
secondary_keycloak_user_id=''
primary_local_user_id=''
secondary_local_user_id=''
academic_period_type_id=''
academic_period_id=''
intake_id=''
programme_type_id=''
programme_id=''
programme_version_id=''
compulsory_module_id=''
elective_module_id=''
compulsory_curriculum_module_id=''
elective_curriculum_module_id=''
primary_student_id=''
primary_enrolment_id=''
secondary_student_id=''
secondary_enrolment_id=''
registration_id=''
cross_student_response_file=''

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

cleanup_disposable_records() {
  local original_exit_status=$?
  local cleanup_failed=0
  local empty_uuid='00000000-0000-0000-0000-000000000000'
  trap - ERR
  set +e

  docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres \
    -d emhare_student_records \
    -v registration_id="${registration_id:-${empty_uuid}}" \
    -v primary_student_id="${primary_student_id:-${empty_uuid}}" \
    -v secondary_student_id="${secondary_student_id:-${empty_uuid}}" \
    -v primary_enrolment_id="${primary_enrolment_id:-${empty_uuid}}" \
    -v secondary_enrolment_id="${secondary_enrolment_id:-${empty_uuid}}" >/dev/null <<'SQL' || cleanup_failed=1
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM integration_outbox WHERE payload::text LIKE '%' || :'registration_id' || '%';
DELETE FROM registration_status_events_aud WHERE registration_session_id = :'registration_id'::uuid;
DELETE FROM registration_status_events WHERE registration_session_id = :'registration_id'::uuid;
DELETE FROM registration_modules_aud WHERE registration_session_id = :'registration_id'::uuid;
DELETE FROM registration_modules WHERE registration_session_id = :'registration_id'::uuid;
DELETE FROM registration_sessions_aud WHERE id = :'registration_id'::uuid;
DELETE FROM registration_sessions WHERE id = :'registration_id'::uuid;
DELETE FROM student_programme_enrolments_aud
 WHERE id IN (:'primary_enrolment_id'::uuid, :'secondary_enrolment_id'::uuid);
DELETE FROM student_programme_enrolments
 WHERE id IN (:'primary_enrolment_id'::uuid, :'secondary_enrolment_id'::uuid);
DELETE FROM students_aud WHERE id IN (:'primary_student_id'::uuid, :'secondary_student_id'::uuid);
DELETE FROM students WHERE id IN (:'primary_student_id'::uuid, :'secondary_student_id'::uuid);
COMMIT;
SQL

  docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres \
    -d emhare_notifications \
    -v registration_id="${registration_id:-${empty_uuid}}" >/dev/null <<'SQL' || cleanup_failed=1
BEGIN;
SET LOCAL session_replication_role = replica;
CREATE TEMP TABLE disposable_notification_requests (id uuid PRIMARY KEY) ON COMMIT DROP;
INSERT INTO disposable_notification_requests (id)
SELECT id FROM notification_requests
 WHERE idempotency_key LIKE '%' || :'registration_id' || '%';
DELETE FROM notification_request_attachments_aud
 WHERE notification_request_id IN (SELECT id FROM disposable_notification_requests);
DELETE FROM notification_request_attachments
 WHERE notification_request_id IN (SELECT id FROM disposable_notification_requests);
DELETE FROM notification_delivery_attempts_aud
 WHERE notification_request_id IN (SELECT id FROM disposable_notification_requests);
DELETE FROM notification_delivery_attempts
 WHERE notification_request_id IN (SELECT id FROM disposable_notification_requests);
DELETE FROM notification_provider_callbacks_aud
 WHERE notification_request_id IN (SELECT id FROM disposable_notification_requests);
DELETE FROM notification_provider_callbacks
 WHERE notification_request_id IN (SELECT id FROM disposable_notification_requests);
DELETE FROM in_app_notifications_aud
 WHERE notification_request_id IN (SELECT id FROM disposable_notification_requests);
DELETE FROM in_app_notifications
 WHERE notification_request_id IN (SELECT id FROM disposable_notification_requests);
DELETE FROM notification_requests_aud WHERE id IN (SELECT id FROM disposable_notification_requests);
DELETE FROM notification_requests WHERE id IN (SELECT id FROM disposable_notification_requests);
DELETE FROM notification_delivery_outbox WHERE payload::text LIKE '%' || :'registration_id' || '%';
DELETE FROM notification_event_inbox_aud
 WHERE id IN (SELECT id FROM notification_event_inbox WHERE payload::text LIKE '%' || :'registration_id' || '%');
DELETE FROM notification_event_inbox WHERE payload::text LIKE '%' || :'registration_id' || '%';
COMMIT;
SQL

  docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres \
    -d emhare_academic_setup \
    -v academic_period_type_id="${academic_period_type_id:-${empty_uuid}}" \
    -v academic_period_id="${academic_period_id:-${empty_uuid}}" \
    -v intake_id="${intake_id:-${empty_uuid}}" \
    -v programme_type_id="${programme_type_id:-${empty_uuid}}" \
    -v programme_id="${programme_id:-${empty_uuid}}" \
    -v programme_version_id="${programme_version_id:-${empty_uuid}}" \
    -v compulsory_module_id="${compulsory_module_id:-${empty_uuid}}" \
    -v elective_module_id="${elective_module_id:-${empty_uuid}}" \
    -v compulsory_curriculum_module_id="${compulsory_curriculum_module_id:-${empty_uuid}}" \
    -v elective_curriculum_module_id="${elective_curriculum_module_id:-${empty_uuid}}" >/dev/null <<'SQL' || cleanup_failed=1
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM curriculum_modules_aud
 WHERE id IN (:'compulsory_curriculum_module_id'::uuid, :'elective_curriculum_module_id'::uuid);
DELETE FROM curriculum_modules
 WHERE id IN (:'compulsory_curriculum_module_id'::uuid, :'elective_curriculum_module_id'::uuid);
DELETE FROM modules_aud WHERE id IN (:'compulsory_module_id'::uuid, :'elective_module_id'::uuid);
DELETE FROM modules WHERE id IN (:'compulsory_module_id'::uuid, :'elective_module_id'::uuid);
DELETE FROM programme_versions_aud WHERE id = :'programme_version_id'::uuid;
DELETE FROM programme_versions WHERE id = :'programme_version_id'::uuid;
DELETE FROM programmes_aud WHERE id = :'programme_id'::uuid;
DELETE FROM programmes WHERE id = :'programme_id'::uuid;
DELETE FROM programme_types_aud WHERE id = :'programme_type_id'::uuid;
DELETE FROM programme_types WHERE id = :'programme_type_id'::uuid;
DELETE FROM intakes_aud WHERE id = :'intake_id'::uuid;
DELETE FROM intakes WHERE id = :'intake_id'::uuid;
DELETE FROM academic_periods_aud WHERE id = :'academic_period_id'::uuid;
DELETE FROM academic_periods WHERE id = :'academic_period_id'::uuid;
DELETE FROM academic_period_types_aud WHERE id = :'academic_period_type_id'::uuid;
DELETE FROM academic_period_types WHERE id = :'academic_period_type_id'::uuid;
COMMIT;
SQL

  docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres \
    -d emhare_core_identity \
    -v primary_email="${primary_email}" \
    -v secondary_email="${secondary_email}" >/dev/null <<'SQL' || cleanup_failed=1
BEGIN;
SET LOCAL session_replication_role = replica;
CREATE TEMP TABLE disposable_core_users (id uuid PRIMARY KEY) ON COMMIT DROP;
INSERT INTO disposable_core_users (id)
SELECT id FROM users WHERE email IN (:'primary_email', :'secondary_email');
DELETE FROM official_name_synchronizations_aud
 WHERE user_id IN (SELECT id FROM disposable_core_users);
DELETE FROM official_name_synchronizations
 WHERE user_id IN (SELECT id FROM disposable_core_users);
DELETE FROM student_portal_access_provisioning_aud
 WHERE user_id IN (SELECT id FROM disposable_core_users);
DELETE FROM student_portal_access_provisioning
 WHERE user_id IN (SELECT id FROM disposable_core_users);
DELETE FROM user_role_assignments_aud WHERE user_id IN (SELECT id FROM disposable_core_users);
DELETE FROM user_role_assignments WHERE user_id IN (SELECT id FROM disposable_core_users);
DELETE FROM login_events_aud WHERE user_id IN (SELECT id FROM disposable_core_users);
DELETE FROM login_events WHERE user_id IN (SELECT id FROM disposable_core_users);
DELETE FROM users_aud WHERE id IN (SELECT id FROM disposable_core_users);
DELETE FROM users WHERE id IN (SELECT id FROM disposable_core_users);
COMMIT;
SQL

  if [[ -n "${admin_token}" ]]; then
    if [[ -z "${primary_keycloak_user_id}" ]]; then
      primary_keycloak_user_id="$(curl -fsS -G \
        "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" \
        -H "Authorization: Bearer ${admin_token}" \
        --data-urlencode username="${primary_email}" --data-urlencode exact=true | jq -r '.[0].id // empty')"
    fi
    if [[ -z "${secondary_keycloak_user_id}" ]]; then
      secondary_keycloak_user_id="$(curl -fsS -G \
        "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" \
        -H "Authorization: Bearer ${admin_token}" \
        --data-urlencode username="${secondary_email}" --data-urlencode exact=true | jq -r '.[0].id // empty')"
    fi
    for keycloak_user_id in "${primary_keycloak_user_id}" "${secondary_keycloak_user_id}"; do
      if [[ -n "${keycloak_user_id}" ]]; then
        curl -fsS -o /dev/null -X DELETE \
          "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${keycloak_user_id}" \
          -H "Authorization: Bearer ${admin_token}" || cleanup_failed=1
      fi
    done
    if [[ -n "${keycloak_client_uuid}" ]]; then
      curl -fsS -o /dev/null -X DELETE \
        "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients/${keycloak_client_uuid}" \
        -H "Authorization: Bearer ${admin_token}" || cleanup_failed=1
    fi
  fi

  if [[ -n "${cross_student_response_file}" ]]; then
    rm -f "${cross_student_response_file}" || cleanup_failed=1
  fi

  if [[ "${original_exit_status}" -ne 0 ]]; then
    return "${original_exit_status}"
  fi
  if [[ "${cleanup_failed}" -ne 0 ]]; then
    printf 'FAIL: disposable self-service fixture cleanup did not complete.\n' >&2
    return 1
  fi
}

if [[ "${1:-}" == "--cleanup-manifest" ]]; then
  fixture_manifest_path="${2:-}"
  [[ -n "${fixture_manifest_path}" && -f "${fixture_manifest_path}" ]] || {
    printf 'A valid fixture manifest path is required.\n' >&2
    exit 1
  }

  run_identifier="$(jq -er '.runId' "${fixture_manifest_path}")"
  short_identifier="${run_identifier%%-*}"
  primary_email="$(jq -er '.primaryEmail' "${fixture_manifest_path}")"
  secondary_email="$(jq -er '.secondaryEmail' "${fixture_manifest_path}")"
  keycloak_client_uuid="$(jq -r '.keycloakClientUuid // empty' "${fixture_manifest_path}")"
  primary_keycloak_user_id="$(jq -r '.primaryKeycloakUserId // empty' "${fixture_manifest_path}")"
  secondary_keycloak_user_id="$(jq -r '.secondaryKeycloakUserId // empty' "${fixture_manifest_path}")"
  academic_period_type_id="$(jq -er '.academicPeriodTypeId' "${fixture_manifest_path}")"
  academic_period_id="$(jq -er '.academicPeriodId' "${fixture_manifest_path}")"
  intake_id="$(jq -er '.intakeId' "${fixture_manifest_path}")"
  programme_type_id="$(jq -er '.programmeTypeId' "${fixture_manifest_path}")"
  programme_id="$(jq -er '.programmeId' "${fixture_manifest_path}")"
  programme_version_id="$(jq -er '.programmeVersionId' "${fixture_manifest_path}")"
  compulsory_module_id="$(jq -er '.compulsoryModuleId' "${fixture_manifest_path}")"
  elective_module_id="$(jq -er '.electiveModuleId' "${fixture_manifest_path}")"
  compulsory_curriculum_module_id="$(jq -er '.compulsoryCurriculumModuleId' "${fixture_manifest_path}")"
  elective_curriculum_module_id="$(jq -er '.electiveCurriculumModuleId' "${fixture_manifest_path}")"
  primary_student_id="$(jq -er '.primaryStudentId' "${fixture_manifest_path}")"
  primary_enrolment_id="$(jq -er '.primaryEnrolmentId' "${fixture_manifest_path}")"
  secondary_student_id="$(jq -er '.secondaryStudentId' "${fixture_manifest_path}")"
  secondary_enrolment_id="$(jq -er '.secondaryEnrolmentId' "${fixture_manifest_path}")"
  registration_id="$(jq -er '.registrationId' "${fixture_manifest_path}")"

  current_step="obtaining Keycloak administration token for fixture cleanup"
  admin_token="$(curl -fsS -X POST "${keycloak_base_url}/realms/master/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode client_id=admin-cli \
    --data-urlencode username="${keycloak_admin_username}" \
    --data-urlencode password="${keycloak_admin_password}" \
    --data-urlencode grant_type=password | jq -er '.access_token')"
  cleanup_disposable_records || exit 1
  rm -f "${fixture_manifest_path}" || exit 1
  exit 0
fi

trap cleanup_disposable_records EXIT

current_step="obtaining Keycloak administration token"
admin_token="$(curl -fsS -X POST "${keycloak_base_url}/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode client_id=admin-cli \
  --data-urlencode username="${keycloak_admin_username}" \
  --data-urlencode password="${keycloak_admin_password}" \
  --data-urlencode grant_type=password | jq -er '.access_token')"
curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients" \
  -H "Authorization: Bearer ${admin_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg clientId "${keycloak_client_id}" \
    '{clientId:$clientId,enabled:true,publicClient:true,directAccessGrantsEnabled:true,standardFlowEnabled:false,protocol:"openid-connect"}')"
keycloak_client_uuid="$(curl -fsS -G "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients" \
  -H "Authorization: Bearer ${admin_token}" \
  --data-urlencode clientId="${keycloak_client_id}" | jq -er '.[0].id')"
student_role="$(curl -fsS "${keycloak_base_url}/admin/realms/${keycloak_realm}/roles/student" \
  -H "Authorization: Bearer ${admin_token}")"

create_student_identity() {
  local label="$1"
  local email="$2"
  curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" \
    -H "Authorization: Bearer ${admin_token}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg email "${email}" --arg password "${test_password}" --arg label "${label}" \
      '{username:$email,email:$email,emailVerified:true,enabled:true,firstName:$label,lastName:"Self Service",credentials:[{type:"password",value:$password,temporary:false}]}')"
  local keycloak_user_id
  keycloak_user_id="$(curl -fsS -G "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" \
    -H "Authorization: Bearer ${admin_token}" \
    --data-urlencode username="${email}" \
    --data-urlencode exact=true | jq -er '.[0].id')"
  curl -fsS -o /dev/null -X POST \
    "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${keycloak_user_id}/role-mappings/realm" \
    -H "Authorization: Bearer ${admin_token}" \
    -H 'Content-Type: application/json' \
    -d "[${student_role}]"
  local access_token
  access_token="$(curl -fsS -X POST "${keycloak_base_url}/realms/${keycloak_realm}/protocol/openid-connect/token" \
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
primary_identity="$(create_student_identity primary "${primary_email}")"
secondary_identity="$(create_student_identity secondary "${secondary_email}")"
primary_keycloak_user_id="$(jq -r '.keycloakUserId' <<<"${primary_identity}")"
primary_local_user_id="$(jq -r '.localUserId' <<<"${primary_identity}")"
primary_access_token="$(jq -r '.accessToken' <<<"${primary_identity}")"
secondary_keycloak_user_id="$(jq -r '.keycloakUserId' <<<"${secondary_identity}")"
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

current_step="waiting for notification requests before disposable fixture cleanup"
notification_request_count='0'
for _ in {1..30}; do
  notification_request_count="$(database_value emhare_notifications "SELECT COUNT(*) FROM notification_requests WHERE idempotency_key LIKE '%${registration_id}%';")"
  [[ "${notification_request_count}" == "2" ]] && break
  sleep 1
done
[[ "${notification_request_count}" == "2" ]] || { echo 'Expected email and in-app notification requests.' >&2; exit 1; }

retained_evidence=false
if [[ -n "${fixture_manifest_path}" ]]; then
  current_step="writing the disposable browser fixture manifest"
  jq -n \
    --arg runId "${run_identifier}" \
    --arg primaryEmail "${primary_email}" \
    --arg secondaryEmail "${secondary_email}" \
    --arg keycloakClientUuid "${keycloak_client_uuid}" \
    --arg primaryKeycloakUserId "${primary_keycloak_user_id}" \
    --arg secondaryKeycloakUserId "${secondary_keycloak_user_id}" \
    --arg academicPeriodTypeId "${academic_period_type_id}" \
    --arg academicPeriodId "${academic_period_id}" \
    --arg intakeId "${intake_id}" \
    --arg programmeTypeId "${programme_type_id}" \
    --arg programmeId "${programme_id}" \
    --arg programmeVersionId "${programme_version_id}" \
    --arg compulsoryModuleId "${compulsory_module_id}" \
    --arg electiveModuleId "${elective_module_id}" \
    --arg compulsoryCurriculumModuleId "${compulsory_curriculum_module_id}" \
    --arg electiveCurriculumModuleId "${elective_curriculum_module_id}" \
    --arg primaryStudentId "${primary_student_id}" \
    --arg primaryEnrolmentId "${primary_enrolment_id}" \
    --arg secondaryStudentId "${secondary_student_id}" \
    --arg secondaryEnrolmentId "${secondary_enrolment_id}" \
    --arg registrationId "${registration_id}" \
    '{runId:$runId,primaryEmail:$primaryEmail,secondaryEmail:$secondaryEmail,keycloakClientUuid:$keycloakClientUuid,primaryKeycloakUserId:$primaryKeycloakUserId,secondaryKeycloakUserId:$secondaryKeycloakUserId,academicPeriodTypeId:$academicPeriodTypeId,academicPeriodId:$academicPeriodId,intakeId:$intakeId,programmeTypeId:$programmeTypeId,programmeId:$programmeId,programmeVersionId:$programmeVersionId,compulsoryModuleId:$compulsoryModuleId,electiveModuleId:$electiveModuleId,compulsoryCurriculumModuleId:$compulsoryCurriculumModuleId,electiveCurriculumModuleId:$electiveCurriculumModuleId,primaryStudentId:$primaryStudentId,primaryEnrolmentId:$primaryEnrolmentId,secondaryStudentId:$secondaryStudentId,secondaryEnrolmentId:$secondaryEnrolmentId,registrationId:$registrationId}' \
    >"${fixture_manifest_path}"
  retained_evidence=true
fi

result_json="$(jq -n \
  --arg status PASS \
  --arg runId "${run_identifier}" \
  --arg primaryStudentId "${primary_student_id}" \
  --arg secondaryStudentId "${secondary_student_id}" \
  --arg registrationId "${registration_id}" \
  --arg programmePeriodNumber "$(jq -r '.programmePeriodNumber' <<<"${submitted_response}")" \
  --arg registrationStatus "$(jq -r '.status' <<<"${submitted_response}")" \
  --arg statusEvents "${status_event_count}" \
  --arg notificationEvents "${notification_event_count}" \
  --arg notificationRequests "${notification_request_count}" \
  --arg crossStudentStatus "${cross_student_status}" \
  --argjson retainedEvidence "${retained_evidence}" \
  '{status:$status,runId:$runId,primaryStudentId:$primaryStudentId,secondaryStudentId:$secondaryStudentId,registrationId:$registrationId,programmePeriodNumber:($programmePeriodNumber|tonumber),registrationStatus:$registrationStatus,statusEvents:($statusEvents|tonumber),notificationEvents:($notificationEvents|tonumber),notificationRequests:($notificationRequests|tonumber),crossStudentSubmissionHttpStatus:($crossStudentStatus|tonumber),retainedEvidence:$retainedEvidence}')"

if [[ "${retained_evidence}" == "true" ]]; then
  trap - EXIT
fi
printf '%s\n' "${result_json}"
