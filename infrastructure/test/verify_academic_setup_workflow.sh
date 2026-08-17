#!/usr/bin/env bash

# Author: Tinashe K
# Verifies the governed Academic Setup lifecycle and removes every disposable record.

set -euo pipefail

keycloak_base_url="${KEYCLOAK_BASE_URL:-http://localhost:8099}"
keycloak_realm="${KEYCLOAK_REALM:-emhare}"
academic_setup_base_url="${ACADEMIC_SETUP_BASE_URL:-http://localhost:18082}"
postgres_container="${POSTGRES_CONTAINER:-emhare-postgres}"
run_identifier=$(uuidgen | tr '[:upper:]' '[:lower:]')
code_suffix=$(tr -d '-' <<<"${run_identifier}" | cut -c1-8 | tr '[:lower:]' '[:upper:]')
keycloak_client_id="e2e-academic-${run_identifier}"
test_email="${keycloak_client_id}@example.test"
test_password='Temporary-Academic-Password-42'

keycloak_client_uuid=''
keycloak_user_id=''
academic_unit_type_root_id=''
academic_unit_type_leaf_id=''
academic_unit_root_id=''
academic_unit_leaf_id=''
academic_year_id=''
academic_period_type_id=''
academic_period_id=''
intake_id=''
programme_level_id=''
programme_type_id=''
programme_id=''
programme_version_id=''
module_id=''
curriculum_module_id=''

keycloak_admin_token=$(curl -fsS -X POST \
  "${keycloak_base_url}/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=password \
  --data-urlencode client_id=admin-cli \
  --data-urlencode username="${KEYCLOAK_ADMIN_USERNAME:-admin}" \
  --data-urlencode password="${KEYCLOAK_ADMIN_PASSWORD:-admin}" | jq -er .access_token)

cleanup_disposable_records() {
  set +e

  docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_academic_setup \
    -v curriculum_module_id="${curriculum_module_id:-00000000-0000-0000-0000-000000000000}" \
    -v programme_version_id="${programme_version_id:-00000000-0000-0000-0000-000000000000}" \
    -v programme_id="${programme_id:-00000000-0000-0000-0000-000000000000}" \
    -v module_id="${module_id:-00000000-0000-0000-0000-000000000000}" \
    -v programme_type_id="${programme_type_id:-00000000-0000-0000-0000-000000000000}" \
    -v programme_level_id="${programme_level_id:-00000000-0000-0000-0000-000000000000}" \
    -v intake_id="${intake_id:-00000000-0000-0000-0000-000000000000}" \
    -v academic_period_id="${academic_period_id:-00000000-0000-0000-0000-000000000000}" \
    -v academic_period_type_id="${academic_period_type_id:-00000000-0000-0000-0000-000000000000}" \
    -v academic_year_id="${academic_year_id:-00000000-0000-0000-0000-000000000000}" \
    -v academic_unit_leaf_id="${academic_unit_leaf_id:-00000000-0000-0000-0000-000000000000}" \
    -v academic_unit_root_id="${academic_unit_root_id:-00000000-0000-0000-0000-000000000000}" \
    -v academic_unit_type_leaf_id="${academic_unit_type_leaf_id:-00000000-0000-0000-0000-000000000000}" \
    -v academic_unit_type_root_id="${academic_unit_type_root_id:-00000000-0000-0000-0000-000000000000}" >/dev/null <<'SQL'
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM curriculum_modules_aud WHERE id = :'curriculum_module_id'::uuid;
DELETE FROM curriculum_modules WHERE id = :'curriculum_module_id'::uuid;
DELETE FROM programme_versions_aud WHERE id = :'programme_version_id'::uuid;
DELETE FROM programme_versions WHERE id = :'programme_version_id'::uuid;
DELETE FROM programmes_aud WHERE id = :'programme_id'::uuid;
DELETE FROM programmes WHERE id = :'programme_id'::uuid;
DELETE FROM modules_aud WHERE id = :'module_id'::uuid;
DELETE FROM modules WHERE id = :'module_id'::uuid;
DELETE FROM programme_types_aud WHERE id = :'programme_type_id'::uuid;
DELETE FROM programme_types WHERE id = :'programme_type_id'::uuid;
DELETE FROM programme_levels_aud WHERE id = :'programme_level_id'::uuid;
DELETE FROM programme_levels WHERE id = :'programme_level_id'::uuid;
DELETE FROM intakes_aud WHERE id = :'intake_id'::uuid;
DELETE FROM intakes WHERE id = :'intake_id'::uuid;
DELETE FROM academic_periods_aud WHERE id = :'academic_period_id'::uuid;
DELETE FROM academic_periods WHERE id = :'academic_period_id'::uuid;
DELETE FROM academic_period_types_aud WHERE id = :'academic_period_type_id'::uuid;
DELETE FROM academic_period_types WHERE id = :'academic_period_type_id'::uuid;
DELETE FROM academic_years_aud WHERE id = :'academic_year_id'::uuid;
DELETE FROM academic_years WHERE id = :'academic_year_id'::uuid;
DELETE FROM academic_units_aud WHERE id IN (:'academic_unit_leaf_id'::uuid, :'academic_unit_root_id'::uuid);
DELETE FROM academic_units WHERE id = :'academic_unit_leaf_id'::uuid;
DELETE FROM academic_units WHERE id = :'academic_unit_root_id'::uuid;
DELETE FROM academic_unit_types_aud WHERE id IN (:'academic_unit_type_leaf_id'::uuid, :'academic_unit_type_root_id'::uuid);
DELETE FROM academic_unit_types WHERE id = :'academic_unit_type_leaf_id'::uuid;
DELETE FROM academic_unit_types WHERE id = :'academic_unit_type_root_id'::uuid;
DELETE FROM revinfo revision
WHERE NOT EXISTS (SELECT 1 FROM academic_unit_types_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM academic_units_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM academic_years_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM academic_period_types_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM academic_periods_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM intakes_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM programme_levels_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM programme_types_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM programmes_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM programme_versions_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM modules_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM curriculum_modules_aud audit WHERE audit.rev = revision.rev);
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
  -d "$(jq -nc --arg email "${test_email}" --arg password "${test_password}" '{username:$email,email:$email,emailVerified:true,enabled:true,firstName:"Academic",lastName:"Administrator",credentials:[{type:"password",value:$password,temporary:false}]}')"
keycloak_user_id=$(curl -fsS -G \
  "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" \
  -H "Authorization: Bearer ${keycloak_admin_token}" \
  --data-urlencode username="${test_email}" \
  --data-urlencode exact=true | jq -er '.[0].id')
system_admin_role=$(curl -fsS \
  "${keycloak_base_url}/admin/realms/${keycloak_realm}/roles/system-admin" \
  -H "Authorization: Bearer ${keycloak_admin_token}" | jq -s '.')
curl -fsS -o /dev/null -X POST \
  "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${keycloak_user_id}/role-mappings/realm" \
  -H "Authorization: Bearer ${keycloak_admin_token}" \
  -H 'Content-Type: application/json' \
  -d "${system_admin_role}"
access_token=$(curl -fsS -X POST \
  "${keycloak_base_url}/realms/${keycloak_realm}/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=password \
  --data-urlencode client_id="${keycloak_client_id}" \
  --data-urlencode username="${test_email}" \
  --data-urlencode password="${test_password}" | jq -er .access_token)

post_json() {
  local path="$1"
  local payload="$2"
  curl -fsS -X POST "${academic_setup_base_url}${path}" \
    -H "Authorization: Bearer ${access_token}" \
    -H 'Content-Type: application/json' \
    -d "${payload}"
}

root_unit_type=$(post_json /api/academic/unit-types "$(jq -nc --arg code "FACULTY_${code_suffix}" '{code:$code,name:"Faculty",levelOrder:1,leafAllowed:false}')")
academic_unit_type_root_id=$(jq -er .id <<<"${root_unit_type}")
leaf_unit_type=$(post_json /api/academic/unit-types "$(jq -nc --arg code "DEPARTMENT_${code_suffix}" '{code:$code,name:"Department",levelOrder:2,leafAllowed:true}')")
academic_unit_type_leaf_id=$(jq -er .id <<<"${leaf_unit_type}")

root_unit=$(post_json /api/academic/units "$(jq -nc --arg typeId "${academic_unit_type_root_id}" --arg code "SCI_${code_suffix}" '{academicUnitTypeId:$typeId,code:$code,name:"Faculty of Science"}')")
academic_unit_root_id=$(jq -er .id <<<"${root_unit}")
leaf_unit=$(post_json /api/academic/units "$(jq -nc --arg typeId "${academic_unit_type_leaf_id}" --arg parentId "${academic_unit_root_id}" --arg code "COMP_${code_suffix}" '{academicUnitTypeId:$typeId,parentId:$parentId,code:$code,name:"Department of Computing"}')")
academic_unit_leaf_id=$(jq -er .id <<<"${leaf_unit}")

academic_year=$(post_json /api/academic/years "$(jq -nc --arg name "2027-${code_suffix}" '{name:$name,startDate:"2027-01-01",endDate:"2027-12-31"}')")
academic_year_id=$(jq -er .id <<<"${academic_year}")
academic_year=$(post_json "/api/academic/years/${academic_year_id}/open" "$(jq -nc --argjson version "$(jq -er .version <<<"${academic_year}")" '{expectedVersion:$version}')")

academic_period_type=$(post_json /api/academic/period-types "$(jq -nc --arg code "SEM_${code_suffix}" '{code:$code,name:"Semester",sortOrder:1}')")
academic_period_type_id=$(jq -er .id <<<"${academic_period_type}")
academic_period=$(post_json /api/academic/periods "$(jq -nc --arg yearId "${academic_year_id}" --arg typeId "${academic_period_type_id}" --arg code "2027S1_${code_suffix}" '{academicYearId:$yearId,academicPeriodTypeId:$typeId,code:$code,name:"Semester 1",startDate:"2027-01-12",endDate:"2027-06-20"}')")
academic_period_id=$(jq -er .id <<<"${academic_period}")
intake=$(post_json /api/academic/intakes "$(jq -nc --arg yearId "${academic_year_id}" --arg code "JAN27_${code_suffix}" '{academicYearId:$yearId,code:$code,name:"January 2027 Intake",startsOn:"2027-01-01",endsOn:"2027-02-28"}')")
intake_id=$(jq -er .id <<<"${intake}")

academic_period=$(post_json "/api/academic/periods/${academic_period_id}/open" "$(jq -nc --argjson version "$(jq -er .version <<<"${academic_period}")" '{expectedVersion:$version}')")
intake=$(post_json "/api/academic/intakes/${intake_id}/open" "$(jq -nc --argjson version "$(jq -er .version <<<"${intake}")" '{expectedVersion:$version}')")

open_child_rejection_status=$(curl -sS -o /tmp/emhare-academic-open-child-response -w '%{http_code}' -X POST \
  "${academic_setup_base_url}/api/academic/years/${academic_year_id}/close" \
  -H "Authorization: Bearer ${access_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson version "$(jq -er .version <<<"${academic_year}")" '{expectedVersion:$version}')")
[[ "${open_child_rejection_status}" == '409' ]]
jq -e '.detail == "Close all open academic periods and intakes before closing the academic year."' /tmp/emhare-academic-open-child-response >/dev/null

academic_period=$(post_json "/api/academic/periods/${academic_period_id}/close" "$(jq -nc --argjson version "$(jq -er .version <<<"${academic_period}")" '{expectedVersion:$version}')")
intake=$(post_json "/api/academic/intakes/${intake_id}/close" "$(jq -nc --argjson version "$(jq -er .version <<<"${intake}")" '{expectedVersion:$version}')")
academic_year=$(post_json "/api/academic/years/${academic_year_id}/close" "$(jq -nc --argjson version "$(jq -er .version <<<"${academic_year}")" '{expectedVersion:$version}')")

[[ "$(jq -er .status <<<"${academic_period}")" == 'CLOSED' ]]
[[ "$(jq -er .status <<<"${intake}")" == 'CLOSED' ]]
[[ "$(jq -er .status <<<"${academic_year}")" == 'CLOSED' ]]

programme_level=$(post_json /api/academic/programme-levels "$(jq -nc --arg code "UG_${code_suffix}" '{code:$code,name:"Undergraduate",sortOrder:1}')")
programme_level_id=$(jq -er .id <<<"${programme_level}")
programme_type=$(post_json /api/academic/programme-types "$(jq -nc --arg code "DEGREE_${code_suffix}" '{code:$code,name:"Degree"}')")
programme_type_id=$(jq -er .id <<<"${programme_type}")

academic_module=$(post_json /api/academic/modules "$(jq -nc --arg ownerId "${academic_unit_leaf_id}" --arg code "CSC101_${code_suffix}" '{owningAcademicUnitId:$ownerId,code:$code,name:"Programming Fundamentals",description:"Foundational programming and problem solving.",creditValue:12.00,academicLevel:1}')")
module_id=$(jq -er .id <<<"${academic_module}")
academic_module=$(post_json "/api/academic/modules/${module_id}/activate" "$(jq -nc --argjson version "$(jq -er .version <<<"${academic_module}")" '{expectedVersion:$version}')")

programme=$(post_json /api/academic/programmes "$(jq -nc --arg ownerId "${academic_unit_leaf_id}" --arg typeId "${programme_type_id}" --arg levelId "${programme_level_id}" --arg code "BSCIT_${code_suffix}" '{owningAcademicUnitId:$ownerId,programmeTypeId:$typeId,programmeLevelId:$levelId,code:$code,name:"Bachelor of Science in Information Technology",awardName:"Bachelor of Science Honours Degree",minimumDurationPeriods:8,maximumDurationPeriods:12}')")
programme_id=$(jq -er .id <<<"${programme}")
programme_version=$(post_json "/api/academic/programmes/${programme_id}/versions" '{"versionCode":"2027.1","effectiveFrom":"2027-01-01"}')
programme_version_id=$(jq -er .id <<<"${programme_version}")
curriculum_module=$(post_json "/api/academic/programme-versions/${programme_version_id}/curriculum" "$(jq -nc --arg moduleId "${module_id}" '{moduleId:$moduleId,periodNumber:1,moduleType:"COMPULSORY",creditValue:12.00,minimumMarkRequired:50.00,sortOrder:1}')")
curriculum_module_id=$(jq -er .id <<<"${curriculum_module}")
programme_version=$(post_json "/api/academic/programme-versions/${programme_version_id}/approve" "$(jq -nc --argjson version "$(jq -er .version <<<"${programme_version}")" '{expectedVersion:$version}')")
programme=$(post_json "/api/academic/programmes/${programme_id}/activate" "$(jq -nc --argjson version "$(jq -er .version <<<"${programme}")" '{expectedVersion:$version}')")

immutable_status=$(curl -sS -o /tmp/emhare-academic-immutable-response -w '%{http_code}' -X POST \
  "${academic_setup_base_url}/api/academic/programme-versions/${programme_version_id}/curriculum" \
  -H "Authorization: Bearer ${access_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg moduleId "${module_id}" '{moduleId:$moduleId,periodNumber:2,moduleType:"OPTIONAL",creditValue:12.00,sortOrder:2}')")
[[ "${immutable_status}" == '409' ]]
jq -e '.title == "Operation not allowed"' /tmp/emhare-academic-immutable-response >/dev/null

child_rejection_status=$(curl -sS -o /tmp/emhare-academic-child-response -w '%{http_code}' -X POST \
  "${academic_setup_base_url}/api/academic/units" \
  -H "Authorization: Bearer ${access_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg typeId "${academic_unit_type_leaf_id}" --arg parentId "${academic_unit_leaf_id}" --arg code "INVALID_${code_suffix}" '{academicUnitTypeId:$typeId,parentId:$parentId,code:$code,name:"Invalid Child"}')")
[[ "${child_rejection_status}" == '409' ]]

overview=$(curl -fsS "${academic_setup_base_url}/api/academic/overview" -H "Authorization: Bearer ${access_token}")
jq -e --arg programmeId "${programme_id}" --arg moduleId "${module_id}" '
  any(.programmes[]; .id == $programmeId and .status == "ACTIVE")
  and any(.modules[]; .id == $moduleId and .status == "ACTIVE")
' <<<"${overview}" >/dev/null

audit_count=$(docker exec "${postgres_container}" psql -qAt -U postgres -d emhare_academic_setup \
  -c "SELECT count(*) FROM programmes_aud WHERE id = '${programme_id}'::uuid;")
[[ "${audit_count}" -ge 2 ]]

printf 'PASS academic setup workflow programme=%s version=%s module=%s auditRevisions=%s\n' \
  "${programme_id}" "${programme_version_id}" "${module_id}" "${audit_count}"
