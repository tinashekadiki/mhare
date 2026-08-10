#!/usr/bin/env bash

# Author: Tinashe K
# Proves approved assessment rules, controlled capture, immutable submission,
# auditable amendment, reproducible progression, and stored official documents through live APIs.

set -euo pipefail

current_step='initialising assessment workflow harness'
report_failure() {
  local exit_status=$?
  printf 'FAIL: %s (exit %s)\n' "${current_step}" "${exit_status}" >&2
  exit "${exit_status}"
}
trap report_failure ERR

keycloak_base_url="${KEYCLOAK_BASE_URL:-http://localhost:8099}"
keycloak_realm="${KEYCLOAK_REALM:-emhare}"
assessment_base_url="${ASSESSMENT_BASE_URL:-http://localhost:18086}"
documents_base_url="${DOCUMENTS_BASE_URL:-http://localhost:18090}"
postgres_container="${POSTGRES_CONTAINER:-emhare-flyway-postgres}"
test_run_identifier=$(uuidgen | tr '[:upper:]' '[:lower:]')
client_id="e2e-assessment-${test_run_identifier}"
staff_email="assessment-${test_run_identifier}@example.test"
moderator_email="moderator-${test_run_identifier}@example.test"
approver_email="approver-${test_run_identifier}@example.test"
publisher_email="publisher-${test_run_identifier}@example.test"
test_password='Temporary-E2E-Password-42'

source_event_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
registration_session_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
student_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_enrolment_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
programme_version_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_period_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
roster_import_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
roster_entry_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
module_id=$(uuidgen | tr '[:upper:]' '[:lower:]')

keycloak_client_uuid=''
staff_keycloak_user_id=''
moderator_keycloak_user_id=''
approver_keycloak_user_id=''
publisher_keycloak_user_id=''
offering_id=''
generated_pdf_path=''

current_step='obtaining Keycloak administration token'
keycloak_admin_token=$(curl -fsS -X POST \
  "${keycloak_base_url}/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=password --data-urlencode client_id=admin-cli \
  --data-urlencode username="${KEYCLOAK_ADMIN_USERNAME:-admin}" \
  --data-urlencode password="${KEYCLOAK_ADMIN_PASSWORD:-admin}" | jq -er .access_token)

cleanup_disposable_records() {
  set +e
  if [[ -n "${generated_pdf_path}" ]]; then
    rm -f "${generated_pdf_path}"
  fi
  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_documents_reporting >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM generated_documents_aud WHERE student_number='STU-E2E-ASMT';
DELETE FROM generated_documents WHERE student_number='STU-E2E-ASMT';
DELETE FROM progression_decision_result_projections_aud WHERE progression_decision_projection_id IN (
  SELECT id FROM progression_decision_projections WHERE student_number='STU-E2E-ASMT');
DELETE FROM progression_decision_result_projections WHERE progression_decision_projection_id IN (
  SELECT id FROM progression_decision_projections WHERE student_number='STU-E2E-ASMT');
DELETE FROM progression_decision_projections_aud WHERE student_number='STU-E2E-ASMT';
DELETE FROM progression_decision_projections WHERE student_number='STU-E2E-ASMT';
DELETE FROM published_result_projections_aud WHERE student_number='STU-E2E-ASMT';
DELETE FROM published_result_projections WHERE student_number='STU-E2E-ASMT';
DELETE FROM integration_inbox WHERE payload->>'studentNumber'='STU-E2E-ASMT';
COMMIT;
SQL
  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_assessment_results \
    -v registration_session_id="${registration_session_id}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM integration_outbox WHERE payload->>'studentNumber'='STU-E2E-ASMT';
DELETE FROM student_overall_decision_events_aud WHERE student_overall_decision_id IN (
  SELECT id FROM student_overall_decisions WHERE registration_roster_import_id IN (
    SELECT id FROM registration_roster_imports WHERE registration_session_id=:'registration_session_id'::uuid));
DELETE FROM student_overall_decision_events WHERE student_overall_decision_id IN (
  SELECT id FROM student_overall_decisions WHERE registration_roster_import_id IN (
    SELECT id FROM registration_roster_imports WHERE registration_session_id=:'registration_session_id'::uuid));
DELETE FROM student_overall_decision_results_aud WHERE student_overall_decision_id IN (
  SELECT id FROM student_overall_decisions WHERE registration_roster_import_id IN (
    SELECT id FROM registration_roster_imports WHERE registration_session_id=:'registration_session_id'::uuid));
DELETE FROM student_overall_decision_results WHERE student_overall_decision_id IN (
  SELECT id FROM student_overall_decisions WHERE registration_roster_import_id IN (
    SELECT id FROM registration_roster_imports WHERE registration_session_id=:'registration_session_id'::uuid));
DELETE FROM student_overall_decisions_aud WHERE registration_roster_import_id IN (
  SELECT id FROM registration_roster_imports WHERE registration_session_id=:'registration_session_id'::uuid);
DELETE FROM student_overall_decisions WHERE registration_roster_import_id IN (
  SELECT id FROM registration_roster_imports WHERE registration_session_id=:'registration_session_id'::uuid);
DELETE FROM progression_rule_outcomes_aud WHERE progression_rule_set_id IN (
  SELECT id FROM progression_rule_sets WHERE rule_code='E2E-PROGRESSION');
DELETE FROM progression_rule_outcomes WHERE progression_rule_set_id IN (
  SELECT id FROM progression_rule_sets WHERE rule_code='E2E-PROGRESSION');
DELETE FROM progression_rule_sets_aud WHERE rule_code='E2E-PROGRESSION';
DELETE FROM progression_rule_sets WHERE rule_code='E2E-PROGRESSION';
DELETE FROM published_result_amendment_events_aud WHERE published_result_amendment_id IN (
  SELECT id FROM published_result_amendments WHERE original_published_result_id IN (
    SELECT id FROM published_results WHERE module_code='E2E-ASMT'));
DELETE FROM published_result_amendment_events WHERE published_result_amendment_id IN (
  SELECT id FROM published_result_amendments WHERE original_published_result_id IN (
    SELECT id FROM published_results WHERE module_code='E2E-ASMT'));
DELETE FROM published_result_amendments_aud WHERE original_published_result_id IN (
  SELECT id FROM published_results WHERE module_code='E2E-ASMT');
DELETE FROM published_result_amendments WHERE original_published_result_id IN (
  SELECT id FROM published_results WHERE module_code='E2E-ASMT');
DELETE FROM published_results_aud WHERE result_batch_id IN (SELECT id FROM result_batches WHERE module_offering_id IN (SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT'));
DELETE FROM published_results WHERE result_batch_id IN (SELECT id FROM result_batches WHERE module_offering_id IN (SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT'));
DELETE FROM result_batch_status_events_aud WHERE result_batch_id IN (SELECT id FROM result_batches WHERE module_offering_id IN (SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT'));
DELETE FROM result_batch_status_events WHERE result_batch_id IN (SELECT id FROM result_batches WHERE module_offering_id IN (SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT'));
DELETE FROM module_results_aud WHERE result_batch_id IN (SELECT id FROM result_batches WHERE module_offering_id IN (SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT'));
DELETE FROM module_results WHERE result_batch_id IN (SELECT id FROM result_batches WHERE module_offering_id IN (SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT'));
DELETE FROM result_batches_aud WHERE module_offering_id IN (SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT');
DELETE FROM result_batches WHERE module_offering_id IN (SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT');
DELETE FROM grading_bands_aud WHERE grading_scheme_id IN (SELECT id FROM grading_schemes WHERE code='E2E-GRADE');
DELETE FROM grading_bands WHERE grading_scheme_id IN (SELECT id FROM grading_schemes WHERE code='E2E-GRADE');
DELETE FROM grading_schemes_aud WHERE code='E2E-GRADE';
DELETE FROM grading_schemes WHERE code='E2E-GRADE';
DELETE FROM assessment_calculation_component_evidence_aud WHERE calculation_run_id IN (
  SELECT id FROM assessment_calculation_runs WHERE module_offering_id IN (
    SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT'));
DELETE FROM assessment_calculation_component_evidence WHERE calculation_run_id IN (
  SELECT id FROM assessment_calculation_runs WHERE module_offering_id IN (
    SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT'));
DELETE FROM assessment_calculation_outcomes_aud WHERE calculation_run_id IN (
  SELECT id FROM assessment_calculation_runs WHERE module_offering_id IN (
    SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT'));
DELETE FROM assessment_calculation_outcomes WHERE calculation_run_id IN (
  SELECT id FROM assessment_calculation_runs WHERE module_offering_id IN (
    SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT'));
DELETE FROM assessment_calculation_runs_aud WHERE module_offering_id IN (
  SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT');
DELETE FROM assessment_calculation_runs WHERE module_offering_id IN (
  SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT');
DELETE FROM mark_amendment_requests_aud WHERE original_mark_id IN (
  SELECT id FROM student_assessment_marks WHERE assessment_roster_entry_id IN (
    SELECT id FROM assessment_roster_entries WHERE roster_import_id IN (
      SELECT id FROM registration_roster_imports WHERE registration_session_id=:'registration_session_id'::uuid)));
DELETE FROM mark_amendment_requests WHERE original_mark_id IN (
  SELECT id FROM student_assessment_marks WHERE assessment_roster_entry_id IN (
    SELECT id FROM assessment_roster_entries WHERE roster_import_id IN (
      SELECT id FROM registration_roster_imports WHERE registration_session_id=:'registration_session_id'::uuid)));
DELETE FROM student_assessment_marks_aud WHERE assessment_roster_entry_id IN (
  SELECT id FROM assessment_roster_entries WHERE roster_import_id IN (
    SELECT id FROM registration_roster_imports WHERE registration_session_id=:'registration_session_id'::uuid));
DELETE FROM student_assessment_marks WHERE assessment_roster_entry_id IN (
  SELECT id FROM assessment_roster_entries WHERE roster_import_id IN (
    SELECT id FROM registration_roster_imports WHERE registration_session_id=:'registration_session_id'::uuid));
DELETE FROM assessment_components_aud WHERE assessment_scheme_id IN (
  SELECT id FROM assessment_schemes WHERE module_offering_id IN (
    SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT'));
DELETE FROM assessment_components WHERE assessment_scheme_id IN (
  SELECT id FROM assessment_schemes WHERE module_offering_id IN (
    SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT'));
DELETE FROM assessment_schemes_aud WHERE module_offering_id IN (
  SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT');
DELETE FROM assessment_schemes WHERE module_offering_id IN (
  SELECT id FROM assessment_module_offerings WHERE module_code='E2E-ASMT');
DELETE FROM assessment_module_offerings_aud WHERE module_code='E2E-ASMT';
DELETE FROM assessment_module_offerings WHERE module_code='E2E-ASMT';
DELETE FROM assessment_roster_entries_aud WHERE roster_import_id IN (
  SELECT id FROM registration_roster_imports WHERE registration_session_id=:'registration_session_id'::uuid);
DELETE FROM assessment_roster_entries WHERE roster_import_id IN (
  SELECT id FROM registration_roster_imports WHERE registration_session_id=:'registration_session_id'::uuid);
DELETE FROM registration_roster_imports_aud WHERE registration_session_id=:'registration_session_id'::uuid;
DELETE FROM registration_roster_imports WHERE registration_session_id=:'registration_session_id'::uuid;
COMMIT;
SQL
  if [[ -n "${staff_keycloak_user_id}" ]]; then
    curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${staff_keycloak_user_id}" -H "Authorization: Bearer ${keycloak_admin_token}"
  fi
  if [[ -n "${moderator_keycloak_user_id}" ]]; then
    curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${moderator_keycloak_user_id}" -H "Authorization: Bearer ${keycloak_admin_token}"
  fi
  if [[ -n "${approver_keycloak_user_id}" ]]; then
    curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${approver_keycloak_user_id}" -H "Authorization: Bearer ${keycloak_admin_token}"
  fi
  if [[ -n "${publisher_keycloak_user_id}" ]]; then
    curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${publisher_keycloak_user_id}" -H "Authorization: Bearer ${keycloak_admin_token}"
  fi
  if [[ -n "${keycloak_client_uuid}" ]]; then
    curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients/${keycloak_client_uuid}" -H "Authorization: Bearer ${keycloak_admin_token}"
  fi
}
trap cleanup_disposable_records EXIT

current_step='creating temporary assessment operator'
curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients" \
  -H "Authorization: Bearer ${keycloak_admin_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg client_id "${client_id}" '{clientId:$client_id,enabled:true,publicClient:true,directAccessGrantsEnabled:true,standardFlowEnabled:false}')"
keycloak_client_uuid=$(curl -fsS -G "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients" -H "Authorization: Bearer ${keycloak_admin_token}" --data-urlencode clientId="${client_id}" | jq -er '.[0].id')
curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" \
  -H "Authorization: Bearer ${keycloak_admin_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg email "${staff_email}" --arg password "${test_password}" '{username:$email,email:$email,emailVerified:true,enabled:true,firstName:"Assessment",lastName:"Operator",credentials:[{type:"password",value:$password,temporary:false}]}')"
staff_keycloak_user_id=$(curl -fsS -G "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" -H "Authorization: Bearer ${keycloak_admin_token}" --data-urlencode username="${staff_email}" --data-urlencode exact=true | jq -er '.[0].id')
system_admin_role=$(curl -fsS "${keycloak_base_url}/admin/realms/${keycloak_realm}/roles/system-admin" -H "Authorization: Bearer ${keycloak_admin_token}")
curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${staff_keycloak_user_id}/role-mappings/realm" \
  -H "Authorization: Bearer ${keycloak_admin_token}" -H 'Content-Type: application/json' -d "[$(printf '%s' "${system_admin_role}")]"
access_token=$(curl -fsS -X POST "${keycloak_base_url}/realms/${keycloak_realm}/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode grant_type=password \
  --data-urlencode client_id="${client_id}" --data-urlencode username="${staff_email}" \
  --data-urlencode password="${test_password}" | jq -er .access_token)

create_result_reviewer() {
  local reviewer_email="$1"
  local reviewer_first_name="$2"
  curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" \
    -H "Authorization: Bearer ${keycloak_admin_token}" -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg email "${reviewer_email}" --arg password "${test_password}" --arg first_name "${reviewer_first_name}" '{username:$email,email:$email,emailVerified:true,enabled:true,firstName:$first_name,lastName:"Reviewer",credentials:[{type:"password",value:$password,temporary:false}]}')"
  local reviewer_id
  reviewer_id=$(curl -fsS -G "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" -H "Authorization: Bearer ${keycloak_admin_token}" --data-urlencode username="${reviewer_email}" --data-urlencode exact=true | jq -er '.[0].id')
  curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${reviewer_id}/role-mappings/realm" \
    -H "Authorization: Bearer ${keycloak_admin_token}" -H 'Content-Type: application/json' -d "[$(printf '%s' "${system_admin_role}")]"
  printf '%s' "${reviewer_id}"
}

moderator_keycloak_user_id=$(create_result_reviewer "${moderator_email}" 'Moderation')
approver_keycloak_user_id=$(create_result_reviewer "${approver_email}" 'Approval')
publisher_keycloak_user_id=$(create_result_reviewer "${publisher_email}" 'Publication')
moderator_token=$(curl -fsS -X POST "${keycloak_base_url}/realms/${keycloak_realm}/protocol/openid-connect/token" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode grant_type=password --data-urlencode client_id="${client_id}" --data-urlencode username="${moderator_email}" --data-urlencode password="${test_password}" | jq -er .access_token)
approver_token=$(curl -fsS -X POST "${keycloak_base_url}/realms/${keycloak_realm}/protocol/openid-connect/token" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode grant_type=password --data-urlencode client_id="${client_id}" --data-urlencode username="${approver_email}" --data-urlencode password="${test_password}" | jq -er .access_token)
publisher_token=$(curl -fsS -X POST "${keycloak_base_url}/realms/${keycloak_realm}/protocol/openid-connect/token" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode grant_type=password --data-urlencode client_id="${client_id}" --data-urlencode username="${publisher_email}" --data-urlencode password="${test_password}" | jq -er .access_token)

current_step='creating authoritative confirmed-registration roster fixture'
docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_assessment_results \
  -v source_event_id="${source_event_id}" -v registration_session_id="${registration_session_id}" \
  -v student_id="${student_id}" -v programme_enrolment_id="${programme_enrolment_id}" \
  -v programme_id="${programme_id}" -v programme_version_id="${programme_version_id}" \
  -v academic_period_id="${academic_period_id}" -v roster_import_id="${roster_import_id}" \
  -v roster_entry_id="${roster_entry_id}" -v module_id="${module_id}" >/dev/null <<'SQL'
INSERT INTO registration_roster_imports (
  id,source_event_id,registration_session_id,student_id,student_number,programme_enrolment_id,
  programme_id,programme_version_id,academic_period_id,academic_period_code,academic_period_name,
  academic_period_starts_on,academic_period_ends_on,programme_period_number,imported_at,
  created_at,updated_at,version)
VALUES (:'roster_import_id',:'source_event_id',:'registration_session_id',:'student_id','STU-E2E-ASMT',
  :'programme_enrolment_id',:'programme_id',:'programme_version_id',:'academic_period_id','2027-S1',
  'Semester 1',DATE '2027-08-16',DATE '2027-12-15',1,now(),now(),now(),0);
INSERT INTO assessment_roster_entries (
  id,roster_import_id,registration_module_id,curriculum_module_id,module_id,module_code,module_name,
  curriculum_module_type,credit_value,minimum_mark_required,eligibility_status,created_at,updated_at,version)
VALUES (:'roster_entry_id',:'roster_import_id',gen_random_uuid(),gen_random_uuid(),:'module_id','E2E-ASMT',
  'ERP Assessment Workflow', 'COMPULSORY',12.00,50.00,'ELIGIBLE',now(),now(),0);
SQL

capture_opens_at=$(date -u -v-1H '+%Y-%m-%dT%H:%M:%SZ')
capture_closes_at=$(date -u -v+1H '+%Y-%m-%dT%H:%M:%SZ')
current_step='discovering the authoritative roster source'
roster_sources=$(curl -fsS "${assessment_base_url}/api/assessment-results/roster-sources" -H "Authorization: Bearer ${access_token}")
[[ $(jq -er --arg module "${module_id}" --arg period "${academic_period_id}" 'any(.[]; .moduleId == $module and .academicPeriodId == $period and .eligibleStudentCount == 1 and .offeringCreated == false)' <<<"${roster_sources}") == true ]]

current_step='creating Module offering and assessment scheme'
offering=$(curl -fsS -X POST "${assessment_base_url}/api/assessment-results/offerings" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' -d "$(jq -nc --arg module "${module_id}" --arg period "${academic_period_id}" --arg instructor "${staff_keycloak_user_id}" '{moduleId:$module,academicPeriodId:$period,assignedInstructorUserId:$instructor}')")
offering_id=$(jq -er .id <<<"${offering}")
scheme=$(curl -fsS -X POST "${assessment_base_url}/api/assessment-results/offerings/${offering_id}/schemes" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' -d "$(jq -nc --arg opens "${capture_opens_at}" --arg closes "${capture_closes_at}" '{name:"Approved 2027 assessment scheme",components:[{code:"TOTAL",name:"Integrated assessment",componentType:"COURSEWORK",weightPercent:100.00,maximumMark:100.00,captureOpensAt:$opens,captureClosesAt:$closes,sortOrder:1}]}')")
scheme_id=$(jq -er .id <<<"${scheme}")
component_id=$(jq -er .components[0].id <<<"${scheme}")

current_step='approving the exactly weighted assessment scheme'
scheme=$(curl -fsS -X POST "${assessment_base_url}/api/assessment-results/schemes/${scheme_id}/approve" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' -d "$(jq -nc --argjson version "$(jq -er .version <<<"${scheme}")" '{expectedVersion:$version,reason:"Assessment board approved weights and capture window."}')")
[[ $(jq -er '.status == "APPROVED"' <<<"${scheme}") == true ]]

current_step='capturing and submitting the original mark'
marks=$(curl -fsS -X POST "${assessment_base_url}/api/assessment-results/components/${component_id}/marks" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' -d "$(jq -nc --arg roster "${roster_entry_id}" '{captureMethod:"MANUAL",marks:[{rosterEntryId:$roster,score:68.00,expectedVersion:0}]}')")
mark_id=$(jq -er '.[0].id' <<<"${marks}")
mark=$(curl -fsS -X POST "${assessment_base_url}/api/assessment-results/marks/${mark_id}/submit?expectedVersion=$(jq -er '.[0].version' <<<"${marks}")" -H "Authorization: Bearer ${access_token}")
[[ $(jq -er '.status == "SUBMITTED" and .score == 68' <<<"${mark}") == true ]]

current_step='running deterministic aggregate calculation'
first_calculation=$(curl -fsS -X POST "${assessment_base_url}/api/assessment-results/offerings/${offering_id}/calculations" -H "Authorization: Bearer ${access_token}")
[[ $(jq -er '.completeResultCount == 1 and .incompleteResultCount == 0 and .outcomes[0].weightedTotal == 68' <<<"${first_calculation}") == true ]]

current_step='creating and approving a versioned grading scheme'
grading=$(curl -fsS -X POST "${assessment_base_url}/api/results/grading-schemes" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d '{"code":"E2E-GRADE","name":"E2E standard grading","bands":[{"minimumMark":0.00,"maximumMark":49.99,"grade":"F","remark":"Fail","passing":false,"sortOrder":1},{"minimumMark":50.00,"maximumMark":100.00,"grade":"P","remark":"Pass","passing":true,"sortOrder":2}]}')
grading=$(curl -fsS -X POST "${assessment_base_url}/api/results/grading-schemes/$(jq -er .id <<<"${grading}")/approve" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${grading}")" '{expectedVersion:$version,reason:"Academic board approved complete grading bands."}')")
[[ $(jq -er '.status == "APPROVED"' <<<"${grading}") == true ]]

current_step='materialising the original result batch from immutable calculation evidence'
original_result_batch=$(curl -fsS -X POST "${assessment_base_url}/api/results/batches" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg calculation "$(jq -er .id <<<"${first_calculation}")" --arg grading "$(jq -er .id <<<"${grading}")" '{calculationRunId:$calculation,gradingSchemeId:$grading}')")
[[ $(jq -er '.status == "DRAFT" and .resultCount == 1 and .results[0].finalMark == 68 and .results[0].grade == "P"' <<<"${original_result_batch}") == true ]]

current_step='submitting the original results for independent moderation'
original_result_batch=$(curl -fsS -X POST "${assessment_base_url}/api/results/batches/$(jq -er .id <<<"${original_result_batch}")/submit" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${original_result_batch}")" '{expectedVersion:$version,reason:"Capture completeness and calculation evidence verified."}')")

current_step='proving the submitter cannot moderate their own batch'
same_actor_status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "${assessment_base_url}/api/results/batches/$(jq -er .id <<<"${original_result_batch}")/moderate" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${original_result_batch}")" '{expectedVersion:$version,reason:"Improper same-actor moderation attempt."}')")
[[ "${same_actor_status}" == '409' ]]

current_step='recording independent moderation and approval of the original batch'
original_result_batch=$(curl -fsS -X POST "${assessment_base_url}/api/results/batches/$(jq -er .id <<<"${original_result_batch}")/moderate" -H "Authorization: Bearer ${moderator_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${original_result_batch}")" '{expectedVersion:$version,reason:"Independent moderator reconciled source marks and aggregate."}')")
original_result_batch=$(curl -fsS -X POST "${assessment_base_url}/api/results/batches/$(jq -er .id <<<"${original_result_batch}")/approve" -H "Authorization: Bearer ${approver_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${original_result_batch}")" '{expectedVersion:$version,reason:"Results board approved the moderated batch."}')")

current_step='publishing the immutable original result'
original_result_batch=$(curl -fsS -X POST "${assessment_base_url}/api/results/batches/$(jq -er .id <<<"${original_result_batch}")/publish" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${original_result_batch}")" '{expectedVersion:$version,reason:"Authorised release window opened for students."}')")
[[ $(jq -er '.status == "PUBLISHED" and .publishedAt != null' <<<"${original_result_batch}") == true ]]

current_step='discovering the current publication through the paginated API'
published_page=$(curl -fsS -G "${assessment_base_url}/api/results/published-results" -H "Authorization: Bearer ${access_token}" --data-urlencode studentNumber='STU-E2E-ASMT')
[[ $(jq -er '.totalElements == 1 and .content[0].publicationVersion == 1 and .content[0].finalMark == 68' <<<"${published_page}") == true ]]
original_published_result_id=$(jq -er '.content[0].id' <<<"${published_page}")

current_step='requesting and approving a post-submission mark amendment'
mark_amendment=$(curl -fsS -X POST "${assessment_base_url}/api/assessment-results/marks/${mark_id}/amendments" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' -d '{"proposedScore":72.00,"reason":"Verified transcription error against signed source sheet."}')
mark_amendment=$(curl -fsS -X POST "${assessment_base_url}/api/assessment-results/amendments/$(jq -er .id <<<"${mark_amendment}")/approve" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' -d "$(jq -nc --argjson version "$(jq -er .version <<<"${mark_amendment}")" '{expectedVersion:$version,reason:"Independent reviewer verified the source evidence."}')")
[[ $(jq -er '.status == "APPROVED" and .replacementMarkId != null' <<<"${mark_amendment}") == true ]]

current_step='proving the amended mark revision drives a reproducible recalculation'
second_calculation=$(curl -fsS -X POST "${assessment_base_url}/api/assessment-results/offerings/${offering_id}/calculations" -H "Authorization: Bearer ${access_token}")
[[ $(jq -er '.completeResultCount == 1 and .outcomes[0].weightedTotal == 72' <<<"${second_calculation}") == true ]]

current_step='materialising the approved replacement result evidence'
replacement_result_batch=$(curl -fsS -X POST "${assessment_base_url}/api/results/batches" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg calculation "$(jq -er .id <<<"${second_calculation}")" --arg grading "$(jq -er .id <<<"${grading}")" '{calculationRunId:$calculation,gradingSchemeId:$grading}')")
replacement_result_batch=$(curl -fsS -X POST "${assessment_base_url}/api/results/batches/$(jq -er .id <<<"${replacement_result_batch}")/submit" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${replacement_result_batch}")" '{expectedVersion:$version,reason:"Corrected calculation evidence prepared for moderation."}')")
replacement_result_batch=$(curl -fsS -X POST "${assessment_base_url}/api/results/batches/$(jq -er .id <<<"${replacement_result_batch}")/moderate" -H "Authorization: Bearer ${moderator_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${replacement_result_batch}")" '{expectedVersion:$version,reason:"Independent moderator verified replacement evidence."}')")
replacement_result_batch=$(curl -fsS -X POST "${assessment_base_url}/api/results/batches/$(jq -er .id <<<"${replacement_result_batch}")/approve" -H "Authorization: Bearer ${approver_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${replacement_result_batch}")" '{expectedVersion:$version,reason:"Results board approved corrected result evidence."}')")
[[ $(jq -er '.status == "APPROVED" and .results[0].finalMark == 72' <<<"${replacement_result_batch}") == true ]]

current_step='proving a second ordinary publication is blocked'
duplicate_publication_status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "${assessment_base_url}/api/results/batches/$(jq -er .id <<<"${replacement_result_batch}")/publish" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${replacement_result_batch}")" '{expectedVersion:$version,reason:"Improper overwrite attempt."}')")
[[ "${duplicate_publication_status}" == '409' ]]

current_step='discovering exact approved replacement evidence'
correction_sources=$(curl -fsS "${assessment_base_url}/api/results/published-results/${original_published_result_id}/correction-sources" -H "Authorization: Bearer ${access_token}")
[[ $(jq -er '. | length == 1 and .[0].finalMark == 72' <<<"${correction_sources}") == true ]]
replacement_module_result_id=$(jq -er '.[0].moduleResultId' <<<"${correction_sources}")

current_step='requesting an append-only published result correction'
result_amendment=$(curl -fsS -X POST "${assessment_base_url}/api/results/published-result-amendments" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg original "${original_published_result_id}" --arg replacement "${replacement_module_result_id}" '{originalPublishedResultId:$original,replacementModuleResultId:$replacement,reason:"Approved mark amendment changed the reproducible Module result."}')")
[[ $(jq -er '.status == "REQUESTED" and .originalFinalMark == 68 and .proposedFinalMark == 72' <<<"${result_amendment}") == true ]]

current_step='proving the correction requester cannot self-review'
self_review_status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "${assessment_base_url}/api/results/published-result-amendments/$(jq -er .id <<<"${result_amendment}")/review" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${result_amendment}")" '{expectedVersion:$version,reason:"Improper self-review attempt."}')")
[[ "${self_review_status}" == '409' ]]

current_step='recording independent correction review and approval'
result_amendment=$(curl -fsS -X POST "${assessment_base_url}/api/results/published-result-amendments/$(jq -er .id <<<"${result_amendment}")/review" -H "Authorization: Bearer ${moderator_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${result_amendment}")" '{expectedVersion:$version,reason:"Reviewer reconciled the original and replacement evidence."}')")
result_amendment=$(curl -fsS -X POST "${assessment_base_url}/api/results/published-result-amendments/$(jq -er .id <<<"${result_amendment}")/approve" -H "Authorization: Bearer ${approver_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${result_amendment}")" '{expectedVersion:$version,reason:"Results board approved a new publication version."}')")

current_step='proving the correction approver cannot release their own decision'
self_application_status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "${assessment_base_url}/api/results/published-result-amendments/$(jq -er .id <<<"${result_amendment}")/apply" -H "Authorization: Bearer ${approver_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${result_amendment}")" '{expectedVersion:$version,reason:"Improper same-actor release attempt."}')")
[[ "${self_application_status}" == '409' ]]

current_step='releasing the corrected immutable publication version'
result_amendment=$(curl -fsS -X POST "${assessment_base_url}/api/results/published-result-amendments/$(jq -er .id <<<"${result_amendment}")/apply" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${result_amendment}")" '{expectedVersion:$version,reason:"Registry released the corrected result in the authorised window."}')")
[[ $(jq -er '.status == "APPLIED" and .appliedAt != null' <<<"${result_amendment}") == true ]]

current_step='verifying current publication and permanent lineage'
corrected_page=$(curl -fsS -G "${assessment_base_url}/api/results/published-results" -H "Authorization: Bearer ${access_token}" --data-urlencode studentNumber='STU-E2E-ASMT')
[[ $(jq -er --arg original "${original_published_result_id}" '.totalElements == 1 and .content[0].publicationVersion == 2 and .content[0].finalMark == 72 and .content[0].supersedesPublishedResultId == $original' <<<"${corrected_page}") == true ]]

current_step='creating and approving programme-owned progression rules'
progression_rule=$(curl -fsS -X POST "${assessment_base_url}/api/results/progression/rule-sets" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg programme "${programme_id}" --arg programme_version "${programme_version_id}" '{ruleCode:"E2E-PROGRESSION",ruleName:"E2E programme period progression",programmeId:$programme,programmeVersionId:$programme_version,programmePeriodNumber:1,outcomes:[{priority:1,decisionCode:"PROCEED",decisionLabel:"Proceed to programme period 2",minimumWeightedAverage:50.00,minimumPassedCredits:12.00,maximumFailedCredits:0.00,maximumFailedModules:0,requireAllCompulsoryPassed:true,nextProgrammePeriodNumber:2,fallbackOutcome:false},{priority:2,decisionCode:"REPEAT",decisionLabel:"Repeat programme period 1",requireAllCompulsoryPassed:false,nextProgrammePeriodNumber:1,fallbackOutcome:true}]}')")
progression_rule=$(curl -fsS -X POST "${assessment_base_url}/api/results/progression/rule-sets/$(jq -er .id <<<"${progression_rule}")/approve" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${progression_rule}")" '{expectedVersion:$version,reason:"Academic board approved the programme progression thresholds."}')")
[[ $(jq -er '.status == "APPROVED" and (.outcomes | length) == 2' <<<"${progression_rule}") == true ]]

current_step='proving the complete current publication set is progression-ready'
progression_rosters=$(curl -fsS "${assessment_base_url}/api/results/progression/rosters" -H "Authorization: Bearer ${access_token}")
[[ $(jq -er --arg roster "${roster_import_id}" 'any(.[]; .id == $roster and .eligibleModules == 1 and .publishedModules == 1 and .readyForProgression == true)' <<<"${progression_rosters}") == true ]]

current_step='calculating an evidence-bound progression decision'
progression_decision=$(curl -fsS -X POST "${assessment_base_url}/api/results/progression/decisions" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg roster "${roster_import_id}" --arg rule "$(jq -er .id <<<"${progression_rule}")" '{registrationRosterImportId:$roster,progressionRuleSetId:$rule}')")
[[ $(jq -er '.status == "CALCULATED" and .decisionCode == "PROCEED" and .weightedAverage == 72 and .attemptedCredits == 12 and .passedCredits == 12 and .failedCredits == 0 and (.results | length) == 1 and .results[0].publicationVersion == 2' <<<"${progression_decision}") == true ]]

current_step='proving the progression calculator cannot self-review'
self_progression_review_status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "${assessment_base_url}/api/results/progression/decisions/$(jq -er .id <<<"${progression_decision}")/review" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${progression_decision}")" '{expectedVersion:$version,reason:"Improper progression self-review attempt."}')")
[[ "${self_progression_review_status}" == '409' ]]

current_step='recording independent progression review and approval'
progression_decision=$(curl -fsS -X POST "${assessment_base_url}/api/results/progression/decisions/$(jq -er .id <<<"${progression_decision}")/review" -H "Authorization: Bearer ${moderator_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${progression_decision}")" '{expectedVersion:$version,reason:"Independent reviewer reconciled credits, results, and rule thresholds."}')")
progression_decision=$(curl -fsS -X POST "${assessment_base_url}/api/results/progression/decisions/$(jq -er .id <<<"${progression_decision}")/approve" -H "Authorization: Bearer ${approver_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${progression_decision}")" '{expectedVersion:$version,reason:"Academic board approved the deterministic progression outcome."}')")

current_step='proving the progression approver cannot publish their own decision'
self_progression_publication_status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "${assessment_base_url}/api/results/progression/decisions/$(jq -er .id <<<"${progression_decision}")/publish" -H "Authorization: Bearer ${approver_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${progression_decision}")" '{expectedVersion:$version,reason:"Improper same-actor progression publication attempt."}')")
[[ "${self_progression_publication_status}" == '409' ]]

current_step='publishing through a fourth independent progression actor'
progression_decision=$(curl -fsS -X POST "${assessment_base_url}/api/results/progression/decisions/$(jq -er .id <<<"${progression_decision}")/publish" -H "Authorization: Bearer ${publisher_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${progression_decision}")" '{expectedVersion:$version,reason:"Registry released the approved progression decision."}')")
[[ $(jq -er '.status == "PUBLISHED" and .publishedAt != null' <<<"${progression_decision}") == true ]]

current_step='proving unchanged evidence cannot create a duplicate progression version'
duplicate_progression_status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "${assessment_base_url}/api/results/progression/decisions" -H "Authorization: Bearer ${access_token}" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg roster "${roster_import_id}" --arg rule "$(jq -er .id <<<"${progression_rule}")" '{registrationRosterImportId:$roster,progressionRuleSetId:$rule}')")
[[ "${duplicate_progression_status}" == '409' ]]

current_step='waiting for transactional publication events and official PDF storage'
official_documents='[]'
for _attempt in $(seq 1 30); do
  official_documents=$(curl -fsS "${documents_base_url}/api/documents" \
    -H "Authorization: Bearer ${access_token}")
  if [[ $(jq -r 'any(.[]; .studentNumber == "STU-E2E-ASMT" and .status == "STORED")' \
      <<<"${official_documents}") == 'true' ]]; then
    break
  fi
  sleep 1
done
official_document=$(jq -cer '[.[] | select(.studentNumber == "STU-E2E-ASMT")][0]' \
  <<<"${official_documents}")
[[ $(jq -er '.status == "STORED" and .documentType == "RESULT_SLIP"
    and .checksumSha256 != null and (.checksumSha256 | length) == 64
    and .sizeBytes > 0 and .pageCount > 0' <<<"${official_document}") == true ]]

current_step='downloading and verifying the stored official result slip'
download_details=$(curl -fsS "${documents_base_url}/api/documents/$(jq -er .id <<<"${official_document}")/download" \
  -H "Authorization: Bearer ${access_token}")
generated_pdf_path=$(mktemp -t emhare-official-result.XXXXXX.pdf)
curl -fsS "$(jq -er .downloadUrl <<<"${download_details}")" -o "${generated_pdf_path}"
[[ "$(head -c 5 "${generated_pdf_path}")" == '%PDF-' ]]
generated_pdf_checksum=$(shasum -a 256 "${generated_pdf_path}" | awk '{print $1}')
[[ "${generated_pdf_checksum}" == "$(jq -er .checksumSha256 <<<"${official_document}")" ]]

current_step='verifying downstream projection, inbox, and outbox evidence'
outbox_evidence=$(docker exec "${postgres_container}" psql -qAt -F '|' -U postgres \
  -d emhare_assessment_results -c "SELECT count(*),count(*) FILTER (WHERE status='PUBLISHED')
    FROM integration_outbox WHERE payload->>'studentNumber'='STU-E2E-ASMT'")
[[ "${outbox_evidence}" == '3|3' ]]
reporting_evidence=$(docker exec "${postgres_container}" psql -qAt -F '|' -U postgres \
  -d emhare_documents_reporting -c "SELECT
    (SELECT count(*) FROM published_result_projections WHERE student_number='STU-E2E-ASMT'),
    (SELECT count(*) FROM published_result_projections WHERE student_number='STU-E2E-ASMT' AND current_version),
    (SELECT count(*) FROM progression_decision_projections WHERE student_number='STU-E2E-ASMT' AND current_version),
    (SELECT count(*) FROM progression_decision_result_projections evidence
      JOIN progression_decision_projections decision ON decision.id=evidence.progression_decision_projection_id
      WHERE decision.student_number='STU-E2E-ASMT'),
    (SELECT count(*) FROM integration_inbox WHERE payload->>'studentNumber'='STU-E2E-ASMT' AND processed_at IS NOT NULL)")
[[ "${reporting_evidence}" == '2|1|1|1|3' ]]

current_step='proving progression source evidence is physically immutable'
if docker exec "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_assessment_results \
  -c "UPDATE student_overall_decision_results SET final_mark=99 WHERE student_overall_decision_id='$(jq -er .id <<<"${progression_decision}")'::uuid" >/dev/null 2>&1; then
  printf 'Progression evidence unexpectedly allowed an update\n' >&2
  exit 1
fi

current_step='proving the original published row remains physically immutable'
if docker exec "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_assessment_results \
  -c "UPDATE published_results SET final_mark=72 WHERE id='${original_published_result_id}'::uuid" >/dev/null 2>&1; then
  printf 'Original published result unexpectedly allowed an update\n' >&2
  exit 1
fi

original_status=$(docker exec "${postgres_container}" psql -qAt -U postgres -d emhare_assessment_results -c "SELECT status FROM student_assessment_marks WHERE id='${mark_id}'::uuid")
mark_revision_count=$(docker exec "${postgres_container}" psql -qAt -U postgres -d emhare_assessment_results -c "SELECT count(*) FROM student_assessment_marks WHERE assessment_roster_entry_id='${roster_entry_id}'::uuid")
audit_revision_count=$(docker exec "${postgres_container}" psql -qAt -U postgres -d emhare_assessment_results -c "SELECT count(*) FROM student_assessment_marks_aud WHERE assessment_roster_entry_id='${roster_entry_id}'::uuid")
calculation_evidence_count=$(docker exec "${postgres_container}" psql -qAt -U postgres -d emhare_assessment_results -c "SELECT count(*) FROM assessment_calculation_component_evidence WHERE calculation_run_id='$(jq -er .id <<<"${second_calculation}")'::uuid AND score=72.00")
[[ "${calculation_evidence_count}" == '1' ]]
published_result_count=$(docker exec "${postgres_container}" psql -qAt -U postgres -d emhare_assessment_results -c "SELECT count(*) FROM published_results WHERE student_id='${student_id}'::uuid AND module_id='${module_id}'::uuid AND academic_period_id='${academic_period_id}'::uuid")
amendment_event_count=$(docker exec "${postgres_container}" psql -qAt -U postgres -d emhare_assessment_results -c "SELECT count(*) FROM published_result_amendment_events WHERE published_result_amendment_id='$(jq -er .id <<<"${result_amendment}")'::uuid")
progression_event_count=$(docker exec "${postgres_container}" psql -qAt -U postgres -d emhare_assessment_results -c "SELECT count(*) FROM student_overall_decision_events WHERE student_overall_decision_id='$(jq -er .id <<<"${progression_decision}")'::uuid")
publication_versions=$(docker exec "${postgres_container}" psql -qAt -U postgres -d emhare_assessment_results -c "SELECT string_agg(publication_version::text,',' ORDER BY publication_version) FROM published_results WHERE student_id='${student_id}'::uuid AND module_id='${module_id}'::uuid AND academic_period_id='${academic_period_id}'::uuid")
[[ "${published_result_count}" == '2' && "${amendment_event_count}" == '4' && "${publication_versions}" == '1,2' && "${progression_event_count}" == '4' ]]

jq -n --arg result PASS --arg schemeStatus "$(jq -er .status <<<"${scheme}")" \
  --arg originalMarkStatus "${original_status}" --arg markAmendmentStatus "$(jq -er .status <<<"${mark_amendment}")" \
  --argjson originalTotal "$(jq -er .outcomes[0].weightedTotal <<<"${first_calculation}")" \
  --argjson amendedTotal "$(jq -er .outcomes[0].weightedTotal <<<"${second_calculation}")" \
  --argjson markRevisions "${mark_revision_count}" --argjson auditRevisions "${audit_revision_count}" \
  --argjson calculationEvidence "${calculation_evidence_count}" \
  --arg originalBatchStatus "$(jq -er .status <<<"${original_result_batch}")" --arg replacementBatchStatus "$(jq -er .status <<<"${replacement_result_batch}")" \
  --arg resultAmendmentStatus "$(jq -er .status <<<"${result_amendment}")" --argjson publishedResults "${published_result_count}" \
  --arg publicationVersions "${publication_versions}" --argjson amendmentEvents "${amendment_event_count}" \
  --arg progressionStatus "$(jq -er .status <<<"${progression_decision}")" --arg progressionDecision "$(jq -er .decisionCode <<<"${progression_decision}")" --argjson progressionEvents "${progression_event_count}" \
  --arg officialDocumentStatus "$(jq -er .status <<<"${official_document}")" --arg officialDocumentType "$(jq -er .documentType <<<"${official_document}")" \
  --arg officialDocumentChecksum "${generated_pdf_checksum}" --arg outboxEvidence "${outbox_evidence}" --arg reportingEvidence "${reporting_evidence}" \
  '{result:$result,schemeStatus:$schemeStatus,originalMarkStatus:$originalMarkStatus,markAmendmentStatus:$markAmendmentStatus,originalTotal:$originalTotal,amendedTotal:$amendedTotal,markRevisions:$markRevisions,auditRevisions:$auditRevisions,calculationEvidence:$calculationEvidence,originalBatchStatus:$originalBatchStatus,replacementBatchStatus:$replacementBatchStatus,resultAmendmentStatus:$resultAmendmentStatus,publishedResults:$publishedResults,publicationVersions:$publicationVersions,amendmentEvents:$amendmentEvents,progressionStatus:$progressionStatus,progressionDecision:$progressionDecision,progressionEvents:$progressionEvents,officialDocumentStatus:$officialDocumentStatus,officialDocumentType:$officialDocumentType,officialDocumentChecksum:$officialDocumentChecksum,outboxEvidence:$outboxEvidence,reportingEvidence:$reportingEvidence}'
