#!/usr/bin/env bash

# Author: Tinashe K
# Verifies capacity-aware generation, four-person publication, invigilation evidence, incidents, and cleanup.

set -euo pipefail

current_step='initialising governed exam timetable harness'
trap 'status=$?; printf "FAIL: %s (exit %s)\n" "${current_step}" "${status}" >&2; exit "${status}"' ERR

keycloak_base_url="${KEYCLOAK_BASE_URL:-http://localhost:8099}"
exam_base_url="${EXAM_BASE_URL:-http://localhost:18087}"
postgres_container="${POSTGRES_CONTAINER:-emhare-flyway-postgres}"
run_identifier=$(uuidgen | tr '[:upper:]' '[:lower:]')
code_suffix=$(tr -d '-' <<<"${run_identifier}" | cut -c1-8 | tr '[:lower:]' '[:upper:]')
client_id="e2e-exams-${run_identifier}"
test_password='Temporary-Exams-Password-42'
academic_period_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_version_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
module_one_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
module_two_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
student_one_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
student_two_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
source_event_one_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
source_event_two_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
client_uuid=''; generator_user_id=''; reviewer_user_id=''; approver_user_id=''; publisher_user_id=''

current_step='obtaining Keycloak administration token'
admin_token=$(curl -fsS -X POST "${keycloak_base_url}/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode grant_type=password \
  --data-urlencode client_id=admin-cli --data-urlencode username="${KEYCLOAK_ADMIN_USERNAME:-admin}" \
  --data-urlencode password="${KEYCLOAK_ADMIN_PASSWORD:-admin}" | jq -er .access_token)

cleanup_disposable_records() {
  set +e
  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_exams_timetabling \
    -v period_id="${academic_period_id}" -v event_one="${source_event_one_id}" -v event_two="${source_event_two_id}" \
    -v code_pattern="%${code_suffix}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role=replica;
DELETE FROM exam_incident_reports_aud WHERE attendance_session_id IN (SELECT id FROM exam_attendance_sessions WHERE venue_allocation_id IN (SELECT a.id FROM exam_timetable_venue_allocations a JOIN exam_master_timetable_entries e ON e.id=a.master_timetable_entry_id JOIN exam_timetable_generation_runs r ON r.id=e.generation_run_id JOIN exam_sessions s ON s.id=r.exam_session_id WHERE s.academic_period_id=:'period_id'::uuid));
DELETE FROM exam_attendance_records_aud WHERE attendance_session_id IN (SELECT id FROM exam_attendance_sessions WHERE venue_allocation_id IN (SELECT a.id FROM exam_timetable_venue_allocations a JOIN exam_master_timetable_entries e ON e.id=a.master_timetable_entry_id JOIN exam_timetable_generation_runs r ON r.id=e.generation_run_id JOIN exam_sessions s ON s.id=r.exam_session_id WHERE s.academic_period_id=:'period_id'::uuid));
DELETE FROM exam_attendance_sessions_aud WHERE venue_allocation_id IN (SELECT a.id FROM exam_timetable_venue_allocations a JOIN exam_master_timetable_entries e ON e.id=a.master_timetable_entry_id JOIN exam_timetable_generation_runs r ON r.id=e.generation_run_id JOIN exam_sessions s ON s.id=r.exam_session_id WHERE s.academic_period_id=:'period_id'::uuid);
DELETE FROM exam_incident_reports WHERE attendance_session_id IN (SELECT id FROM exam_attendance_sessions WHERE venue_allocation_id IN (SELECT a.id FROM exam_timetable_venue_allocations a JOIN exam_master_timetable_entries e ON e.id=a.master_timetable_entry_id JOIN exam_timetable_generation_runs r ON r.id=e.generation_run_id JOIN exam_sessions s ON s.id=r.exam_session_id WHERE s.academic_period_id=:'period_id'::uuid));
DELETE FROM exam_attendance_records WHERE attendance_session_id IN (SELECT id FROM exam_attendance_sessions WHERE venue_allocation_id IN (SELECT a.id FROM exam_timetable_venue_allocations a JOIN exam_master_timetable_entries e ON e.id=a.master_timetable_entry_id JOIN exam_timetable_generation_runs r ON r.id=e.generation_run_id JOIN exam_sessions s ON s.id=r.exam_session_id WHERE s.academic_period_id=:'period_id'::uuid));
DELETE FROM exam_attendance_sessions WHERE venue_allocation_id IN (SELECT a.id FROM exam_timetable_venue_allocations a JOIN exam_master_timetable_entries e ON e.id=a.master_timetable_entry_id JOIN exam_timetable_generation_runs r ON r.id=e.generation_run_id JOIN exam_sessions s ON s.id=r.exam_session_id WHERE s.academic_period_id=:'period_id'::uuid);
DELETE FROM exam_timetable_run_events_aud WHERE generation_run_id IN (SELECT id FROM exam_timetable_generation_runs WHERE exam_session_id IN (SELECT id FROM exam_sessions WHERE academic_period_id=:'period_id'::uuid));
DELETE FROM exam_student_timetable_entries_aud WHERE generation_run_id IN (SELECT id FROM exam_timetable_generation_runs WHERE exam_session_id IN (SELECT id FROM exam_sessions WHERE academic_period_id=:'period_id'::uuid));
DELETE FROM exam_timetable_venue_allocations_aud WHERE master_timetable_entry_id IN (SELECT id FROM exam_master_timetable_entries WHERE generation_run_id IN (SELECT id FROM exam_timetable_generation_runs WHERE exam_session_id IN (SELECT id FROM exam_sessions WHERE academic_period_id=:'period_id'::uuid)));
DELETE FROM exam_master_timetable_entries_aud WHERE generation_run_id IN (SELECT id FROM exam_timetable_generation_runs WHERE exam_session_id IN (SELECT id FROM exam_sessions WHERE academic_period_id=:'period_id'::uuid));
DELETE FROM exam_timetable_generation_runs_aud WHERE exam_session_id IN (SELECT id FROM exam_sessions WHERE academic_period_id=:'period_id'::uuid);
DELETE FROM exam_timetable_run_events WHERE generation_run_id IN (SELECT id FROM exam_timetable_generation_runs WHERE exam_session_id IN (SELECT id FROM exam_sessions WHERE academic_period_id=:'period_id'::uuid));
DELETE FROM exam_student_timetable_entries WHERE generation_run_id IN (SELECT id FROM exam_timetable_generation_runs WHERE exam_session_id IN (SELECT id FROM exam_sessions WHERE academic_period_id=:'period_id'::uuid));
DELETE FROM exam_timetable_venue_allocations WHERE master_timetable_entry_id IN (SELECT id FROM exam_master_timetable_entries WHERE generation_run_id IN (SELECT id FROM exam_timetable_generation_runs WHERE exam_session_id IN (SELECT id FROM exam_sessions WHERE academic_period_id=:'period_id'::uuid)));
DELETE FROM exam_master_timetable_entries WHERE generation_run_id IN (SELECT id FROM exam_timetable_generation_runs WHERE exam_session_id IN (SELECT id FROM exam_sessions WHERE academic_period_id=:'period_id'::uuid));
DELETE FROM exam_timetable_generation_runs WHERE exam_session_id IN (SELECT id FROM exam_sessions WHERE academic_period_id=:'period_id'::uuid);
DELETE FROM exam_session_slots_aud WHERE exam_session_id IN (SELECT id FROM exam_sessions WHERE academic_period_id=:'period_id'::uuid);
DELETE FROM exam_sessions_aud WHERE academic_period_id=:'period_id'::uuid;
DELETE FROM module_exam_requirements_aud WHERE academic_period_id=:'period_id'::uuid;
DELETE FROM exam_session_slots WHERE exam_session_id IN (SELECT id FROM exam_sessions WHERE academic_period_id=:'period_id'::uuid);
DELETE FROM exam_sessions WHERE academic_period_id=:'period_id'::uuid;
DELETE FROM module_exam_requirements WHERE academic_period_id=:'period_id'::uuid;
DELETE FROM exam_candidate_modules_aud WHERE registration_import_id IN (SELECT id FROM exam_registration_imports WHERE academic_period_id=:'period_id'::uuid);
DELETE FROM exam_registration_imports_aud WHERE academic_period_id=:'period_id'::uuid;
DELETE FROM exam_candidate_modules WHERE registration_import_id IN (SELECT id FROM exam_registration_imports WHERE academic_period_id=:'period_id'::uuid);
DELETE FROM exam_registration_imports WHERE academic_period_id=:'period_id'::uuid;
DELETE FROM integration_inbox WHERE event_id IN (:'event_one'::uuid,:'event_two'::uuid);
DELETE FROM exam_venue_availability_windows_aud WHERE venue_id IN (SELECT id FROM exam_venues WHERE code LIKE :'code_pattern');
DELETE FROM exam_venues_aud WHERE code LIKE :'code_pattern';
DELETE FROM exam_venue_types_aud WHERE code LIKE :'code_pattern';
DELETE FROM exam_venue_availability_windows WHERE venue_id IN (SELECT id FROM exam_venues WHERE code LIKE :'code_pattern');
DELETE FROM exam_venues WHERE code LIKE :'code_pattern';
DELETE FROM exam_venue_types WHERE code LIKE :'code_pattern';
COMMIT;
SQL
  for user_id in "${generator_user_id}" "${reviewer_user_id}" "${approver_user_id}" "${publisher_user_id}"; do
    [[ -z "${user_id}" ]] || curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/emhare/users/${user_id}" -H "Authorization: Bearer ${admin_token}"
  done
  [[ -z "${client_uuid}" ]] || curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/emhare/clients/${client_uuid}" -H "Authorization: Bearer ${admin_token}"
}
trap cleanup_disposable_records EXIT

current_step='creating four independent temporary exam operators'
curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/clients" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg id "${client_id}" '{clientId:$id,enabled:true,publicClient:true,directAccessGrantsEnabled:true,standardFlowEnabled:false}')"
client_uuid=$(curl -fsS -G "${keycloak_base_url}/admin/realms/emhare/clients" -H "Authorization: Bearer ${admin_token}" --data-urlencode clientId="${client_id}" | jq -er '.[0].id')
system_admin_role=$(curl -fsS "${keycloak_base_url}/admin/realms/emhare/roles/system-admin" -H "Authorization: Bearer ${admin_token}")
create_operator() {
  local label="$1" email="${1}-${run_identifier}@example.test"
  curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/users" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg email "${email}" --arg password "${test_password}" --arg first "${label}" '{username:$email,email:$email,emailVerified:true,enabled:true,firstName:$first,lastName:"Exam Operator",credentials:[{type:"password",value:$password,temporary:false}]}')"
  local user_id
  user_id=$(curl -fsS -G "${keycloak_base_url}/admin/realms/emhare/users" -H "Authorization: Bearer ${admin_token}" --data-urlencode username="${email}" --data-urlencode exact=true | jq -er '.[0].id')
  curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/users/${user_id}/role-mappings/realm" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' -d "[${system_admin_role}]"
  printf '%s' "${user_id}"
}
login_operator() { curl -fsS -X POST "${keycloak_base_url}/realms/emhare/protocol/openid-connect/token" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode grant_type=password --data-urlencode client_id="${client_id}" --data-urlencode username="$1-${run_identifier}@example.test" --data-urlencode password="${test_password}" | jq -er .access_token; }
generator_user_id=$(create_operator generator); reviewer_user_id=$(create_operator reviewer); approver_user_id=$(create_operator approver); publisher_user_id=$(create_operator publisher)
generator_token=$(login_operator generator); reviewer_token=$(login_operator reviewer); approver_token=$(login_operator approver); publisher_token=$(login_operator publisher)
post_json() { local token="$1" path="$2" payload="$3"; curl -fsS -X POST "${exam_base_url}${path}" -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' -d "${payload}"; }

current_step='publishing two authoritative confirmed-registration events'
publish_registration() {
  local event_id="$1" student_id="$2" student_number="$3" registration_session_id
  registration_session_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
  local registration_module_one_id registration_module_two_id curriculum_module_one_id curriculum_module_two_id
  registration_module_one_id=$(uuidgen | tr '[:upper:]' '[:lower:]'); registration_module_two_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
  curriculum_module_one_id=$(uuidgen | tr '[:upper:]' '[:lower:]'); curriculum_module_two_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
  local payload
  payload=$(jq -nc --arg eventId "${event_id}" --arg occurredAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --arg registrationSessionId "${registration_session_id}" \
    --arg studentId "${student_id}" --arg studentNumber "${student_number}" --arg programmeEnrolmentId "$(uuidgen | tr '[:upper:]' '[:lower:]')" \
    --arg programmeId "${programme_id}" --arg programmeVersionId "${programme_version_id}" --arg academicPeriodId "${academic_period_id}" \
    --arg moduleOneId "${module_one_id}" --arg moduleTwoId "${module_two_id}" \
    --arg registrationModuleOneId "${registration_module_one_id}" --arg registrationModuleTwoId "${registration_module_two_id}" \
    --arg curriculumModuleOneId "${curriculum_module_one_id}" --arg curriculumModuleTwoId "${curriculum_module_two_id}" \
    '{eventId:$eventId,schemaVersion:1,occurredAt:$occurredAt,registrationSessionId:$registrationSessionId,studentId:$studentId,studentNumber:$studentNumber,programmeEnrolmentId:$programmeEnrolmentId,programmeId:$programmeId,programmeVersionId:$programmeVersionId,academicPeriodId:$academicPeriodId,academicPeriodCode:"2027-S1",academicPeriodName:"Semester 1",academicPeriodStartsOn:"2027-01-12",academicPeriodEndsOn:"2027-06-20",programmePeriodNumber:1,modules:[{registrationModuleId:$registrationModuleOneId,curriculumModuleId:$curriculumModuleOneId,moduleId:$moduleOneId,moduleCode:"E2E-ACC101",moduleName:"Financial Accounting I",curriculumModuleType:"COMPULSORY",creditValue:12,minimumMarkRequired:50},{registrationModuleId:$registrationModuleTwoId,curriculumModuleId:$curriculumModuleTwoId,moduleId:$moduleTwoId,moduleCode:"E2E-ECO101",moduleName:"Economics I",curriculumModuleType:"COMPULSORY",creditValue:12,minimumMarkRequired:50}]}')
  curl -fsS -u guest:guest -X POST 'http://localhost:15672/api/exchanges/%2F/emhare.events/publish' -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg payload "${payload}" '{properties:{content_type:"application/json"},routing_key:"student-records.registration-confirmed.v1",payload:$payload,payload_encoding:"string"}')" | jq -e '.routed==true' >/dev/null
}
publish_registration "${source_event_one_id}" "${student_one_id}" 'STU-E2E-EXAM-1'
publish_registration "${source_event_two_id}" "${student_two_id}" 'STU-E2E-EXAM-2'
for attempt in {1..20}; do imported=$(docker exec "${postgres_container}" psql -U postgres -d emhare_exams_timetabling -Atc "select count(*) from integration_inbox where event_id in ('${source_event_one_id}','${source_event_two_id}') and processed_at is not null"); [[ "${imported}" == '2' ]] && break; sleep 1; done
[[ "${imported}" == '2' ]]

current_step='creating governed venue, session, slots, and approved Module requirements'
venue_type=$(post_json "${generator_token}" /api/exams/setup/venue-types "$(jq -nc --arg code "E2E-HALL-${code_suffix}" '{code:$code,name:"Examination hall"}')"); venue_type_id=$(jq -er .id <<<"${venue_type}")
venue_one=$(post_json "${generator_token}" /api/exams/setup/venues "$(jq -nc --arg type "${venue_type_id}" --arg code "E2E-V1-${code_suffix}" '{venueTypeId:$type,code:$code,name:"E2E Hall 1",campusName:"Main Campus",examinationCapacity:1}')"); venue_one_id=$(jq -er .id <<<"${venue_one}")
post_json "${generator_token}" "/api/exams/setup/venues/${venue_one_id}/availability" '{"availableFrom":"2027-05-01T00:00:00Z","availableUntil":"2027-05-10T23:59:59Z","notes":"Certified exam window"}' >/dev/null
session=$(post_json "${generator_token}" /api/exams/setup/sessions "$(jq -nc --arg period "${academic_period_id}" --arg code "E2E-FINAL-${code_suffix}" '{academicPeriodId:$period,academicPeriodCode:"2027-S1",code:$code,name:"Final examinations",assessmentType:"FINAL_EXAM",startsOn:"2027-05-01",endsOn:"2027-05-10"}')"); session_id=$(jq -er .id <<<"${session}")
post_json "${generator_token}" "/api/exams/setup/sessions/${session_id}/slots" '{"code":"DAY-1-AM","startsAt":"2027-05-03T08:00:00Z","endsAt":"2027-05-03T12:00:00Z"}' >/dev/null
session=$(post_json "${generator_token}" "/api/exams/setup/sessions/${session_id}/slots" '{"code":"DAY-1-PM","startsAt":"2027-05-03T13:00:00Z","endsAt":"2027-05-03T17:00:00Z"}')
session=$(post_json "${generator_token}" "/api/exams/setup/sessions/${session_id}/approve" "$(jq -nc --argjson version "$(jq -er .version <<<"${session}")" '{reason:"Registrar approved the examination window and slot plan.",expectedVersion:$version}')")
create_requirement() { local module_id="$1" code="$2" name="$3"; post_json "${generator_token}" /api/exams/setup/requirements "$(jq -nc --arg period "${academic_period_id}" --arg module "${module_id}" --arg code "${code}" --arg name "${name}" --arg type "${venue_type_id}" '{academicPeriodId:$period,moduleId:$module,moduleCode:$code,moduleName:$name,durationMinutes:180,readingTimeMinutes:15,requiredVenueTypeId:$type,specialRequirements:"Standard invigilation"}')"; }
requirement_one=$(create_requirement "${module_one_id}" E2E-ACC101 'Financial Accounting I'); requirement_one_id=$(jq -er .id <<<"${requirement_one}")
post_json "${generator_token}" "/api/exams/setup/requirements/${requirement_one_id}/approve" "$(jq -nc --argjson version "$(jq -er .version <<<"${requirement_one}")" '{reason:"Approved assessment specification.",expectedVersion:$version}')" >/dev/null
requirement_two=$(create_requirement "${module_two_id}" E2E-ECO101 'Economics I'); requirement_two_id=$(jq -er .id <<<"${requirement_two}")
post_json "${generator_token}" "/api/exams/setup/requirements/${requirement_two_id}/approve" "$(jq -nc --argjson version "$(jq -er .version <<<"${requirement_two}")" '{reason:"Approved assessment specification.",expectedVersion:$version}')" >/dev/null

current_step='proving insufficient capacity is rejected atomically'
capacity_status=$(curl -sS -o /tmp/emhare-exam-capacity-response -w '%{http_code}' -X POST "${exam_base_url}/api/timetabling/runs" -H "Authorization: Bearer ${generator_token}" -H 'Content-Type: application/json' -d "$(jq -nc --arg session "${session_id}" '{examSessionId:$session}')")
[[ "${capacity_status}" == '409' ]]
[[ "$(docker exec "${postgres_container}" psql -U postgres -d emhare_exams_timetabling -Atc "select count(*) from exam_timetable_generation_runs where exam_session_id='${session_id}'")" == '0' ]]
venue_two=$(post_json "${generator_token}" /api/exams/setup/venues "$(jq -nc --arg type "${venue_type_id}" --arg code "E2E-V2-${code_suffix}" '{venueTypeId:$type,code:$code,name:"E2E Hall 2",campusName:"Main Campus",examinationCapacity:1}')"); venue_two_id=$(jq -er .id <<<"${venue_two}")
post_json "${generator_token}" "/api/exams/setup/venues/${venue_two_id}/availability" '{"availableFrom":"2027-05-01T00:00:00Z","availableUntil":"2027-05-10T23:59:59Z","notes":"Certified exam window"}' >/dev/null

current_step='generating and governing a clash-free timetable'
run=$(post_json "${generator_token}" /api/timetabling/runs "$(jq -nc --arg session "${session_id}" '{examSessionId:$session}')"); run_id=$(jq -er .id <<<"${run}")
[[ "$(jq -er '[.candidateCount,.moduleCount,.timetableEntryCount,.conflictCount] | join("|")' <<<"${run}")" == '2|2|2|0' ]]
self_review_status=$(curl -sS -o /tmp/emhare-exam-self-review-response -w '%{http_code}' -X POST "${exam_base_url}/api/timetabling/runs/${run_id}/review" -H "Authorization: Bearer ${generator_token}" -H 'Content-Type: application/json' -d "$(jq -nc --argjson version "$(jq -er .version <<<"${run}")" '{reason:"Invalid self review.",expectedVersion:$version}')")
[[ "${self_review_status}" == '409' ]]
run=$(post_json "${reviewer_token}" "/api/timetabling/runs/${run_id}/review" "$(jq -nc --argjson version "$(jq -er .version <<<"${run}")" '{reason:"Independent clash and capacity review completed.",expectedVersion:$version}')")
run=$(post_json "${approver_token}" "/api/timetabling/runs/${run_id}/approve" "$(jq -nc --argjson version "$(jq -er .version <<<"${run}")" '{reason:"Examinations board approval recorded.",expectedVersion:$version}')")
run=$(post_json "${publisher_token}" "/api/timetabling/runs/${run_id}/publish" "$(jq -nc --argjson version "$(jq -er .version <<<"${run}")" '{reason:"Published to candidate timetables.",expectedVersion:$version}')")
student_timetable=$(curl -fsS "${exam_base_url}/api/timetabling/students/${student_one_id}" -H "Authorization: Bearer ${publisher_token}")
[[ "$(jq -er 'length' <<<"${student_timetable}")" == '2' ]]
[[ "$(jq -er '[.[].venueCode] | unique | length' <<<"${student_timetable}")" == '1' ]]

current_step='opening and reconciling the published invigilation register'
workspace=$(curl -fsS "${exam_base_url}/api/exams/invigilation" -H "Authorization: Bearer ${publisher_token}")
venue_allocation_id=$(jq -er --arg run "${run_id}" '.venueOperations | map(select(.generationRunId==$run)) | first | .venueAllocationId' <<<"${workspace}")
attendance_session=$(post_json "${publisher_token}" "/api/exams/invigilation/venue-allocations/${venue_allocation_id}/attendance-session" '{"openingReason":"Chief invigilator reconciled the room, sealed materials, and published seat register."}')
attendance_session_id=$(jq -er .id <<<"${attendance_session}")
attendance_record_id=$(jq -er '.attendanceRecords[0].id' <<<"${attendance_session}")
student_timetable_entry_id=$(jq -er '.attendanceRecords[0].studentTimetableEntryId' <<<"${attendance_session}")
[[ "$(jq -er '[.expectedCandidateCount,.outstandingCandidateCount] | join("|")' <<<"${attendance_session}")" == '1|1' ]]
premature_close_status=$(curl -sS -o /tmp/emhare-exam-premature-close-response -w '%{http_code}' -X POST "${exam_base_url}/api/exams/invigilation/attendance-sessions/${attendance_session_id}/close" -H "Authorization: Bearer ${publisher_token}" -H 'Content-Type: application/json' -d "$(jq -nc --argjson version "$(jq -er .version <<<"${attendance_session}")" '{closureReason:"Invalid incomplete closure.",expectedVersion:$version}')")
[[ "${premature_close_status}" == '409' ]]
attendance_session=$(curl -fsS -X PUT "${exam_base_url}/api/exams/invigilation/attendance-records/${attendance_record_id}" -H "Authorization: Bearer ${publisher_token}" -H 'Content-Type: application/json' -d "$(jq -nc --argjson version "$(jq -er '.attendanceRecords[0].version' <<<"${attendance_session}")" '{attendanceStatus:"PRESENT",evidenceNotes:"Identity and admission evidence checked at the allocated seat.",expectedVersion:$version}')")

current_step='proving incident review and resolution actor separation'
incident_time=$(date -u +%Y-%m-%dT%H:%M:%SZ)
attendance_session=$(post_json "${generator_token}" "/api/exams/invigilation/attendance-sessions/${attendance_session_id}/incidents" "$(jq -nc --arg student "${student_timetable_entry_id}" --arg occurred "${incident_time}" '{studentTimetableEntryId:$student,incidentType:"LATE_ARRIVAL",severity:"LOW",description:"Candidate arrived after the controlled room opening and was admitted after identity verification.",occurredAt:$occurred}')")
incident_id=$(jq -er '.incidents[0].id' <<<"${attendance_session}")
self_incident_review_status=$(curl -sS -o /tmp/emhare-exam-self-incident-review-response -w '%{http_code}' -X POST "${exam_base_url}/api/exams/invigilation/incidents/${incident_id}/review" -H "Authorization: Bearer ${generator_token}" -H 'Content-Type: application/json' -d "$(jq -nc --argjson version "$(jq -er '.incidents[0].version' <<<"${attendance_session}")" '{reason:"Invalid reporter self-review.",expectedVersion:$version}')")
[[ "${self_incident_review_status}" == '409' ]]
attendance_session=$(post_json "${reviewer_token}" "/api/exams/invigilation/incidents/${incident_id}/review" "$(jq -nc --argjson version "$(jq -er '.incidents[0].version' <<<"${attendance_session}")" '{reason:"Independent reviewer checked the invigilator narrative and candidate identity evidence.",expectedVersion:$version}')")
attendance_session=$(post_json "${approver_token}" "/api/exams/invigilation/incidents/${incident_id}/resolve" "$(jq -nc --argjson version "$(jq -er '.incidents[0].version' <<<"${attendance_session}")" '{reason:"Late arrival was accepted with the original examination end time retained and board notification recorded.",expectedVersion:$version}')")
attendance_session=$(post_json "${publisher_token}" "/api/exams/invigilation/attendance-sessions/${attendance_session_id}/close" "$(jq -nc --argjson version "$(jq -er .version <<<"${attendance_session}")" '{closureReason:"Candidate, seat, script, attendance, and incident evidence reconciled after the examination.",expectedVersion:$version}')")
[[ "$(jq -er '[.status,.presentCandidateCount,.absentCandidateCount,.excusedCandidateCount,.outstandingCandidateCount] | join("|")' <<<"${attendance_session}")" == 'CLOSED|1|0|0|0' ]]
[[ "$(jq -er '.incidents[0].status' <<<"${attendance_session}")" == 'RESOLVED' ]]

evidence=$(docker exec "${postgres_container}" psql -U postgres -d emhare_exams_timetabling -Atc "select (select count(*) from exam_timetable_run_events where generation_run_id='${run_id}'),(select count(*) from exam_student_timetable_entries where generation_run_id='${run_id}'),(select count(*) from exam_timetable_venue_allocations a join exam_master_timetable_entries e on e.id=a.master_timetable_entry_id where e.generation_run_id='${run_id}'),(select count(*) from exam_timetable_generation_runs_aud where id='${run_id}')" | tr '|' ':')
invigilation_evidence=$(docker exec "${postgres_container}" psql -U postgres -d emhare_exams_timetabling -Atc "select (select count(*) from exam_attendance_sessions where id='${attendance_session_id}'),(select count(*) from exam_attendance_records where attendance_session_id='${attendance_session_id}'),(select count(*) from exam_incident_reports where attendance_session_id='${attendance_session_id}' and status='RESOLVED'),(select count(*) from exam_attendance_sessions_aud where id='${attendance_session_id}')" | tr '|' ':')
jq -nc --arg status "$(jq -er .status <<<"${run}")" --arg counts "${evidence}" --arg invigilation "${invigilation_evidence}" --arg studentEntries "$(jq -er 'length' <<<"${student_timetable}")" '{result:"PASS",status:$status,workflowAndEvidenceCounts:$counts,invigilationEvidenceCounts:$invigilation,publishedStudentEntries:($studentEntries|tonumber),capacityFailureWasAtomic:true,selfReviewRejected:true,prematureAttendanceClosureRejected:true,selfIncidentReviewRejected:true}'
