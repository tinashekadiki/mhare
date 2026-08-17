#!/usr/bin/env bash

# Author: Tinashe K
# Proves curriculum-governed registration, two-stage approval, durable event
# publication, and authoritative Assessment/Results roster projection.

set -euo pipefail

current_step='initialising registration workflow harness'
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
academic_base_url="${ACADEMIC_BASE_URL:-http://localhost:18082}"
student_records_base_url="${STUDENT_RECORDS_BASE_URL:-http://localhost:18085}"
postgres_container="${POSTGRES_CONTAINER:-emhare-postgres}"

test_run_identifier=$(uuidgen | tr '[:upper:]' '[:lower:]')
client_id="e2e-registration-${test_run_identifier}"
staff_email="registry-${test_run_identifier}@example.test"
test_password='Temporary-E2E-Password-42'

academic_unit_type_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_unit_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_year_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_period_type_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_period_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_level_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_type_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_version_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
module_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
curriculum_module_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
student_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_enrolment_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
source_offer_id=$(uuidgen | tr '[:upper:]' '[:lower:]')

keycloak_client_uuid=''
staff_keycloak_user_id=''
registration_id=''

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
  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_assessment_results \
    -v registration_id="${registration_id:-00000000-0000-0000-0000-000000000000}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM assessment_roster_entries_aud WHERE roster_import_id IN (
  SELECT id FROM registration_roster_imports WHERE registration_session_id = :'registration_id'::uuid);
DELETE FROM assessment_roster_entries WHERE roster_import_id IN (
  SELECT id FROM registration_roster_imports WHERE registration_session_id = :'registration_id'::uuid);
DELETE FROM registration_roster_imports_aud WHERE registration_session_id = :'registration_id'::uuid;
DELETE FROM registration_roster_imports WHERE registration_session_id = :'registration_id'::uuid;
DELETE FROM integration_inbox WHERE payload ->> 'registrationSessionId' = :'registration_id';
COMMIT;
SQL

  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_student_records \
    -v registration_id="${registration_id:-00000000-0000-0000-0000-000000000000}" \
    -v student_id="${student_id}" -v enrolment_id="${programme_enrolment_id}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM integration_outbox WHERE payload ->> 'registrationSessionId' = :'registration_id';
DELETE FROM registration_status_events_aud WHERE registration_session_id = :'registration_id'::uuid;
DELETE FROM registration_status_events WHERE registration_session_id = :'registration_id'::uuid;
DELETE FROM registration_modules_aud WHERE registration_session_id = :'registration_id'::uuid;
DELETE FROM registration_modules WHERE registration_session_id = :'registration_id'::uuid;
DELETE FROM registration_sessions_aud WHERE id = :'registration_id'::uuid;
DELETE FROM registration_sessions WHERE id = :'registration_id'::uuid;
DELETE FROM student_programme_enrolments WHERE id = :'enrolment_id'::uuid;
DELETE FROM students WHERE id = :'student_id'::uuid;
COMMIT;
SQL

  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_academic_setup \
    -v curriculum_module_id="${curriculum_module_id}" -v module_id="${module_id}" \
    -v programme_version_id="${programme_version_id}" -v programme_id="${programme_id}" \
    -v programme_type_id="${programme_type_id}" -v programme_level_id="${programme_level_id}" \
    -v academic_period_id="${academic_period_id}" -v academic_period_type_id="${academic_period_type_id}" \
    -v academic_year_id="${academic_year_id}" -v academic_unit_id="${academic_unit_id}" \
    -v academic_unit_type_id="${academic_unit_type_id}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM curriculum_modules WHERE id = :'curriculum_module_id'::uuid;
DELETE FROM modules WHERE id = :'module_id'::uuid;
DELETE FROM programme_versions WHERE id = :'programme_version_id'::uuid;
DELETE FROM programmes WHERE id = :'programme_id'::uuid;
DELETE FROM programme_types WHERE id = :'programme_type_id'::uuid;
DELETE FROM programme_levels WHERE id = :'programme_level_id'::uuid;
DELETE FROM academic_periods WHERE id = :'academic_period_id'::uuid;
DELETE FROM academic_period_types WHERE id = :'academic_period_type_id'::uuid;
DELETE FROM academic_years WHERE id = :'academic_year_id'::uuid;
DELETE FROM academic_units WHERE id = :'academic_unit_id'::uuid;
DELETE FROM academic_unit_types WHERE id = :'academic_unit_type_id'::uuid;
COMMIT;
SQL

  if [[ -n "${staff_keycloak_user_id}" ]]; then
    curl -fsS -o /dev/null -X DELETE \
      "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${staff_keycloak_user_id}" \
      -H "Authorization: Bearer ${keycloak_admin_token}"
  fi
  if [[ -n "${keycloak_client_uuid}" ]]; then
    curl -fsS -o /dev/null -X DELETE \
      "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients/${keycloak_client_uuid}" \
      -H "Authorization: Bearer ${keycloak_admin_token}"
  fi
}
trap cleanup_disposable_records EXIT

current_step='creating temporary Keycloak client and registry operator'
curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients" \
  -H "Authorization: Bearer ${keycloak_admin_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg client_id "${client_id}" \
    '{clientId:$client_id,enabled:true,publicClient:true,directAccessGrantsEnabled:true,standardFlowEnabled:false}')"
keycloak_client_uuid=$(curl -fsS -G "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients" \
  -H "Authorization: Bearer ${keycloak_admin_token}" --data-urlencode clientId="${client_id}" | jq -er '.[0].id')

curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" \
  -H "Authorization: Bearer ${keycloak_admin_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg email "${staff_email}" --arg password "${test_password}" \
    '{username:$email,email:$email,emailVerified:true,enabled:true,firstName:"Registry",lastName:"Operator",credentials:[{type:"password",value:$password,temporary:false}]}')"
staff_keycloak_user_id=$(curl -fsS -G "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" \
  -H "Authorization: Bearer ${keycloak_admin_token}" --data-urlencode username="${staff_email}" \
  --data-urlencode exact=true | jq -er '.[0].id')
system_admin_role=$(curl -fsS "${keycloak_base_url}/admin/realms/${keycloak_realm}/roles/system-admin" \
  -H "Authorization: Bearer ${keycloak_admin_token}")
curl -fsS -o /dev/null -X POST \
  "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${staff_keycloak_user_id}/role-mappings/realm" \
  -H "Authorization: Bearer ${keycloak_admin_token}" -H 'Content-Type: application/json' \
  -d "[$(printf '%s' "${system_admin_role}")]"

access_token=$(curl -fsS -X POST \
  "${keycloak_base_url}/realms/${keycloak_realm}/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=password --data-urlencode client_id="${client_id}" \
  --data-urlencode username="${staff_email}" --data-urlencode password="${test_password}" | jq -er .access_token)

current_step='creating disposable academic and student source records'
docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_academic_setup \
  -v unit_type_id="${academic_unit_type_id}" -v unit_id="${academic_unit_id}" \
  -v year_id="${academic_year_id}" -v period_type_id="${academic_period_type_id}" \
  -v period_id="${academic_period_id}" -v level_id="${programme_level_id}" \
  -v type_id="${programme_type_id}" -v programme_id="${programme_id}" \
  -v version_id="${programme_version_id}" -v module_id="${module_id}" \
  -v curriculum_id="${curriculum_module_id}" >/dev/null <<'SQL'
INSERT INTO academic_unit_types (id,code,name,level_order,is_leaf_allowed,status,created_at,updated_at,version)
VALUES (:'unit_type_id','SCHOOL','School',1,true,'ACTIVE',now(),now(),0);
INSERT INTO academic_units (id,academic_unit_type_id,code,name,status,created_at,updated_at,version)
VALUES (:'unit_id',:'unit_type_id','E2E-SCHOOL','Registration Test School','ACTIVE',now(),now(),0);
INSERT INTO academic_years (id,name,start_date,end_date,status,created_at,updated_at,version)
VALUES (:'year_id','2027 Test Year',DATE '2027-01-01',DATE '2027-12-31','OPEN',now(),now(),0);
INSERT INTO academic_period_types (id,code,name,sort_order,status,created_at,updated_at,version)
VALUES (:'period_type_id','SEM','Semester',1,'ACTIVE',now(),now(),0);
INSERT INTO academic_periods (id,academic_year_id,academic_period_type_id,code,name,start_date,end_date,status,created_at,updated_at,version)
VALUES (:'period_id',:'year_id',:'period_type_id','2027-S1','Semester 1',DATE '2027-08-16',DATE '2027-12-15','OPEN',now(),now(),0);
INSERT INTO programme_levels (id,code,name,sort_order,status,created_at,updated_at,version)
VALUES (:'level_id','UG','Undergraduate',1,'ACTIVE',now(),now(),0);
INSERT INTO programme_types (id,code,name,status,created_at,updated_at,version)
VALUES (:'type_id','DEG','Degree','ACTIVE',now(),now(),0);
INSERT INTO programmes (id,code,name,award_name,owning_academic_unit_id,programme_type_id,programme_level_id,minimum_duration_periods,maximum_duration_periods,status,created_at,updated_at,version)
VALUES (:'programme_id','BACC-E2E','Bachelor of Accountancy Test','Bachelor of Accountancy',:'unit_id',:'type_id',:'level_id',6,8,'ACTIVE',now(),now(),0);
INSERT INTO programme_versions (id,programme_id,version_code,effective_from,status,created_at,updated_at,version)
VALUES (:'version_id',:'programme_id','2027',DATE '2027-01-01','DRAFT',now(),now(),0);
INSERT INTO modules (id,code,name,description,owning_academic_unit_id,credit_value,academic_level,status,created_at,updated_at,version)
VALUES (:'module_id','ACC101-E2E','Financial Accounting I','Registration workflow proof Module',:'unit_id',12.00,1,'ACTIVE',now(),now(),0);
INSERT INTO curriculum_modules (id,programme_version_id,module_id,period_number,module_type,credit_value,minimum_mark_required,sort_order,created_at,updated_at,version)
VALUES (:'curriculum_id',:'version_id',:'module_id',1,'COMPULSORY',12.00,50.00,1,now(),now(),0);
UPDATE programme_versions
SET status='APPROVED', approved_by_user_id=gen_random_uuid(), approved_at=now(), updated_at=now(), version=version+1
WHERE id=:'version_id';
SQL

docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_student_records \
  -v student_id="${student_id}" -v user_id="${staff_keycloak_user_id}" \
  -v offer_id="${source_offer_id}" -v enrolment_id="${programme_enrolment_id}" \
  -v programme_id="${programme_id}" -v version_id="${programme_version_id}" >/dev/null <<'SQL'
INSERT INTO students (
  id,student_number,user_id,source_applicant_id,source_applicant_number,source_application_id,
  source_offer_id,applicant_category_code,first_name,last_name,primary_email,status,activated_at,
  created_at,updated_at,version)
VALUES (:'student_id','STU-2027-E2E',:'user_id',gen_random_uuid(),'APL-E2E',gen_random_uuid(),
  :'offer_id','LOCAL','Tariro','Moyo','student@example.test','ACTIVE',now(),now(),now(),0);
INSERT INTO student_programme_enrolments (
  id,student_id,source_offer_id,source_programme_choice_id,programme_id,programme_version_id,
  programme_code,programme_name,intake_id,commencement_date,status,status_reason,approved_at,
  created_at,updated_at,version)
VALUES (:'enrolment_id',:'student_id',:'offer_id',gen_random_uuid(),:'programme_id',:'version_id',
  'BACC-E2E','Bachelor of Accountancy Test',gen_random_uuid(),DATE '2027-08-16','ACTIVE',
  'Provisioned for registration workflow proof',now(),now(),now(),0);
SQL

current_step='creating curriculum-governed registration'
registration=$(curl -fsS -X POST "${student_records_base_url}/api/student-records/registrations" \
  -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg student "${student_id}" --arg enrolment "${programme_enrolment_id}" \
    --arg period "${academic_period_id}" \
    '{studentId:$student,programmeEnrolmentId:$enrolment,academicPeriodId:$period,programmePeriodNumber:1,registrationType:"NORMAL",selectedElectiveCurriculumModuleIds:[]}')")
registration_id=$(jq -er .id <<<"${registration}")
[[ $(jq -er '.status == "DRAFT" and (.modules | length) == 1 and .modules[0].selectionSource == "AUTO_COMPULSORY"' <<<"${registration}") == true ]]

current_step='submitting registration for academic review'
registration=$(curl -fsS -X POST "${student_records_base_url}/api/student-records/registrations/${registration_id}/submit" \
  -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${registration}")" \
    '{expectedVersion:$version,reason:"Student and approved curriculum load verified."}')")

current_step='recording academic approval'
registration=$(curl -fsS -X POST "${student_records_base_url}/api/student-records/registrations/${registration_id}/academic-approve" \
  -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${registration}")" \
    '{expectedVersion:$version,reason:"Academic unit approved the compulsory Module load."}')")

current_step='confirming registration and publishing roster'
registration=$(curl -fsS -X POST "${student_records_base_url}/api/student-records/registrations/${registration_id}/confirm" \
  -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${registration}")" \
    '{expectedVersion:$version,reason:"Registry confirmed the registration."}')")
[[ $(jq -er '.status == "CONFIRMED" and .confirmedAt != null' <<<"${registration}") == true ]]

current_step='waiting for Assessment roster projection'
for _ in {1..30}; do
  projected_count=$(docker exec "${postgres_container}" psql -qAt -U postgres -d emhare_assessment_results -c \
    "SELECT count(*) FROM assessment_roster_entries entry JOIN registration_roster_imports import ON import.id=entry.roster_import_id WHERE import.registration_session_id='${registration_id}'::uuid")
  [[ "${projected_count}" == '1' ]] && break
  sleep 1
done
[[ "${projected_count}" == '1' ]]

status_event_count=$(docker exec "${postgres_container}" psql -qAt -U postgres -d emhare_student_records -c \
  "SELECT count(*) FROM registration_status_events WHERE registration_session_id='${registration_id}'::uuid")
outbox_status=$(docker exec "${postgres_container}" psql -qAt -U postgres -d emhare_student_records -c \
  "SELECT status FROM integration_outbox WHERE payload ->> 'registrationSessionId'='${registration_id}' ORDER BY occurred_at DESC LIMIT 1")
inbox_processed=$(docker exec "${postgres_container}" psql -qAt -U postgres -d emhare_assessment_results -c \
  "SELECT processed_at IS NOT NULL FROM integration_inbox WHERE payload ->> 'registrationSessionId'='${registration_id}' LIMIT 1")

jq -n \
  --arg result PASS --arg registrationStatus "$(jq -er .status <<<"${registration}")" \
  --arg moduleCode "$(jq -er .modules[0].moduleCode <<<"${registration}")" \
  --arg outboxStatus "${outbox_status}" --arg inboxProcessed "${inbox_processed}" \
  --argjson statusEvents "${status_event_count}" --argjson assessmentRosterEntries "${projected_count}" \
  '{result:$result,registrationStatus:$registrationStatus,moduleCode:$moduleCode,statusEvents:$statusEvents,outboxStatus:$outboxStatus,inboxProcessed:($inboxProcessed=="t"),assessmentRosterEntries:$assessmentRosterEntries}'
