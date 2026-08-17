#!/usr/bin/env bash

# Author: Tinashe K
# Verifies confirmed-registration delivery through Finance-owned billing policies.

set -euo pipefail
current_step='initialising registration billing integration harness'
trap 'status=$?; printf "FAIL: %s (exit %s)\n" "${current_step}" "${status}" >&2; exit "${status}"' ERR
keycloak_base_url="${KEYCLOAK_BASE_URL:-http://localhost:8099}"; finance_base_url="${FINANCE_BASE_URL:-http://localhost:19084}"; rabbit_management_url="${RABBIT_MANAGEMENT_URL:-http://localhost:15672}"; postgres_container="${POSTGRES_CONTAINER:-emhare-postgres}"
run_identifier=$(uuidgen | tr '[:upper:]' '[:lower:]'); suffix=$(tr -d '-' <<<"${run_identifier}" | cut -c1-8 | tr '[:lower:]' '[:upper:]'); client_id="e2e-registration-billing-${run_identifier}"; password='Temporary-Registration-Billing-42'
client_uuid=''; preparer_user_id=''; approver_user_id=''; programme_catalogue_id=''; module_catalogue_id=''; account_id=$(uuidgen | tr '[:upper:]' '[:lower:]'); student_id=$(uuidgen | tr '[:upper:]' '[:lower:]'); registration_event_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
admin_token=$(curl -fsS -X POST "${keycloak_base_url}/realms/master/protocol/openid-connect/token" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode grant_type=password --data-urlencode client_id=admin-cli --data-urlencode username=admin --data-urlencode password=admin | jq -er .access_token)
cleanup(){ set +e; docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_finance -v account_id="${account_id}" -v event_id="${registration_event_id}" -v programme_catalogue_id="${programme_catalogue_id:-00000000-0000-0000-0000-000000000000}" -v module_catalogue_id="${module_catalogue_id:-00000000-0000-0000-0000-000000000000}" >/dev/null <<'SQL'
BEGIN; SET LOCAL session_replication_role=replica;
DELETE FROM finance_billing_event_scopes_aud WHERE billing_event_id IN (SELECT id FROM finance_billing_events WHERE source_event_id=:'event_id'::uuid);
DELETE FROM finance_billing_events_aud WHERE source_event_id=:'event_id'::uuid;
DELETE FROM finance_billing_event_scopes WHERE billing_event_id IN (SELECT id FROM finance_billing_events WHERE source_event_id=:'event_id'::uuid);
DELETE FROM finance_billing_events WHERE source_event_id=:'event_id'::uuid;
DELETE FROM integration_inbox WHERE event_id=:'event_id'::uuid;
DELETE FROM finance_billing_policies_aud WHERE fee_catalogue_id IN (:'programme_catalogue_id'::uuid,:'module_catalogue_id'::uuid);
DELETE FROM finance_billing_policies WHERE fee_catalogue_id IN (:'programme_catalogue_id'::uuid,:'module_catalogue_id'::uuid);
DELETE FROM finance_fee_rule_scopes_aud WHERE fee_rule_id IN (SELECT id FROM finance_fee_rules WHERE fee_catalogue_id IN (:'programme_catalogue_id'::uuid,:'module_catalogue_id'::uuid));
DELETE FROM finance_fee_rules_aud WHERE fee_catalogue_id IN (:'programme_catalogue_id'::uuid,:'module_catalogue_id'::uuid);
DELETE FROM finance_fee_catalogues_aud WHERE id IN (:'programme_catalogue_id'::uuid,:'module_catalogue_id'::uuid);
DELETE FROM finance_fee_rule_scopes WHERE fee_rule_id IN (SELECT id FROM finance_fee_rules WHERE fee_catalogue_id IN (:'programme_catalogue_id'::uuid,:'module_catalogue_id'::uuid));
DELETE FROM finance_fee_rules WHERE fee_catalogue_id IN (:'programme_catalogue_id'::uuid,:'module_catalogue_id'::uuid);
DELETE FROM finance_fee_catalogues WHERE id IN (:'programme_catalogue_id'::uuid,:'module_catalogue_id'::uuid);
DELETE FROM student_finance_accounts_aud WHERE id=:'account_id'::uuid; DELETE FROM student_finance_accounts WHERE id=:'account_id'::uuid;
COMMIT;
SQL
for user_id in "${preparer_user_id}" "${approver_user_id}"; do [[ -z "${user_id}" ]] || curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/emhare/users/${user_id}" -H "Authorization: Bearer ${admin_token}"; done; [[ -z "${client_uuid}" ]] || curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/emhare/clients/${client_uuid}" -H "Authorization: Bearer ${admin_token}"; }
trap cleanup EXIT

current_step='creating independent Finance policy operators'
curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/clients" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' -d "$(jq -nc --arg id "${client_id}" '{clientId:$id,enabled:true,publicClient:true,directAccessGrantsEnabled:true,standardFlowEnabled:false}')";client_uuid=$(curl -fsS -G "${keycloak_base_url}/admin/realms/emhare/clients" -H "Authorization: Bearer ${admin_token}" --data-urlencode clientId="${client_id}" | jq -er '.[0].id');role=$(curl -fsS "${keycloak_base_url}/admin/realms/emhare/roles/system-admin" -H "Authorization: Bearer ${admin_token}")
create_user(){ local label="$1";local email="${label}-${run_identifier}@example.test";curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/users" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' -d "$(jq -nc --arg email "${email}" --arg password "${password}" --arg label "${label}" '{username:$email,email:$email,emailVerified:true,enabled:true,firstName:$label,lastName:"Finance",credentials:[{type:"password",value:$password,temporary:false}]}')";local id;id=$(curl -fsS -G "${keycloak_base_url}/admin/realms/emhare/users" -H "Authorization: Bearer ${admin_token}" --data-urlencode username="${email}" --data-urlencode exact=true | jq -er '.[0].id');curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/users/${id}/role-mappings/realm" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' -d "[${role}]";printf %s "${id}";}
token(){ curl -fsS -X POST "${keycloak_base_url}/realms/emhare/protocol/openid-connect/token" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode grant_type=password --data-urlencode client_id="${client_id}" --data-urlencode username="$1-${run_identifier}@example.test" --data-urlencode password="${password}" | jq -er .access_token; }
preparer_user_id=$(create_user preparer);approver_user_id=$(create_user approver);preparer_token=$(token preparer);approver_token=$(token approver)
post(){ curl -fsS -X POST "${finance_base_url}$2" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' -d "$3"; }

current_step='creating the active student finance account'
docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_finance -v account_id="${account_id}" -v student_id="${student_id}" -v suffix="${suffix}" >/dev/null <<'SQL'
INSERT INTO student_finance_accounts(id,account_number,student_id,student_number,user_id,source_offer_id,primary_email,base_currency_code,status,opened_at,created_at,updated_at,version) VALUES (:'account_id'::uuid,'SFA-'||:'suffix',:'student_id'::uuid,'R'||:'suffix',gen_random_uuid(),gen_random_uuid(),lower(:'suffix')||'@example.test','USD','ACTIVE',now(),now(),now(),0);
SQL

create_price(){ local code="$1" name="$2" type="$3" amount="$4" revenue="$5";local catalogue rule;catalogue=$(post "${preparer_token}" /api/finance/fee-catalogues "$(jq -nc --arg code "${code}-${suffix}" --arg name "${name}" --arg type "${type}" --arg revenue "${revenue}" '{code:$code,name:$name,chargeType:$type,receivableAccountCode:"1100-AR",revenueAccountCode:$revenue}')");catalogue=$(post "${approver_token}" "/api/finance/fee-catalogues/$(jq -er .id <<<"${catalogue}")/activate" "$(jq -nc --argjson version "$(jq -er .version <<<"${catalogue}")" '{reason:"Independent registration-billing fee approval.",expectedVersion:$version}')");rule=$(post "${preparer_token}" "/api/finance/fee-catalogues/$(jq -er .id <<<"${catalogue}")/rules" "$(jq -nc --argjson amount "${amount}" '{transactionCurrencyCode:"USD",transactionAmount:$amount,effectiveFrom:"2027-01-01T00:00:00Z",effectiveUntil:"2028-01-01T00:00:00Z",scopes:[{scopeDimension:"GLOBAL"}]}')");post "${approver_token}" "/api/finance/fee-catalogues/rules/$(jq -er .id <<<"${rule}")/approve" "$(jq -nc --argjson version "$(jq -er .version <<<"${rule}")" '{reason:"Independent registration-billing price approval.",expectedVersion:$version}')" >/dev/null;printf %s "$(jq -er .id <<<"${catalogue}")"; }
current_step='creating governed programme and Module prices'
programme_catalogue_id=$(create_price PROGRAMME-REG 'Programme registration tuition' PROGRAMME 500 4100-TUITION);module_catalogue_id=$(create_price MODULE-REG 'Registered Module charge' MODULE 25 4200-MODULE)

create_policy(){ local code="$1" name="$2" catalogue_id="$3" line_basis="$4" quantity_basis="$5" fixed_quantity="$6";local policy payload;payload=$(jq -nc --arg code "${code}-${suffix}" --arg name "${name}" --arg catalogue "${catalogue_id}" --arg line "${line_basis}" --arg quantity "${quantity_basis}" --argjson fixed "${fixed_quantity}" '{code:$code,name:$name,sourceEventType:"student-records.registration-confirmed.v1",feeCatalogueId:$catalogue,lineBasis:$line,quantityBasis:$quantity,fixedQuantity:$fixed,effectiveFrom:"2027-01-01T00:00:00Z",effectiveUntil:"2028-01-01T00:00:00Z"}');policy=$(post "${preparer_token}" /api/finance/billing/policies "${payload}");post "${approver_token}" "/api/finance/billing/policies/$(jq -er .id <<<"${policy}")/activate" "$(jq -nc --argjson version "$(jq -er .version <<<"${policy}")" '{reason:"Independent source trigger and quantity approval.",expectedVersion:$version}')" >/dev/null; }
current_step='activating registration and per-Module billing policies'
create_policy PROGRAMME-BILLING 'Programme registration tuition' "${programme_catalogue_id}" REGISTRATION FIXED 1
create_policy MODULE-BILLING 'Registered Module charge' "${module_catalogue_id}" REGISTERED_MODULE FIXED 1

current_step='publishing an authoritative confirmed-registration event'
registration_session_id=$(uuidgen | tr '[:upper:]' '[:lower:]');programme_id=$(uuidgen | tr '[:upper:]' '[:lower:]');period_id=$(uuidgen | tr '[:upper:]' '[:lower:]');programme_version_id=$(uuidgen | tr '[:upper:]' '[:lower:]');enrolment_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
module_one=$(uuidgen | tr '[:upper:]' '[:lower:]');module_two=$(uuidgen | tr '[:upper:]' '[:lower:]')
payload=$(jq -nc --arg event "${registration_event_id}" --arg session "${registration_session_id}" --arg student "${student_id}" --arg number "R${suffix}" --arg enrolment "${enrolment_id}" --arg programme "${programme_id}" --arg version "${programme_version_id}" --arg period "${period_id}" --arg module_one "${module_one}" --arg module_two "${module_two}" '{eventId:$event,schemaVersion:1,occurredAt:"2027-02-01T10:00:00Z",registrationSessionId:$session,studentId:$student,studentNumber:$number,programmeEnrolmentId:$enrolment,programmeId:$programme,programmeVersionId:$version,academicPeriodId:$period,academicPeriodCode:"2027-S1",academicPeriodName:"Semester 1",academicPeriodStartsOn:"2027-01-15",academicPeriodEndsOn:"2027-06-15",programmePeriodNumber:1,modules:[{registrationModuleId:$module_one,curriculumModuleId:$module_one,moduleId:$module_one,moduleCode:"ACC101",moduleName:"Financial Accounting I",curriculumModuleType:"COMPULSORY",creditValue:15,minimumMarkRequired:50},{registrationModuleId:$module_two,curriculumModuleId:$module_two,moduleId:$module_two,moduleCode:"ECO101",moduleName:"Economics I",curriculumModuleType:"COMPULSORY",creditValue:15,minimumMarkRequired:50}]}')
publish(){ curl -fsS -u guest:guest -X POST "${rabbit_management_url}/api/exchanges/%2F/emhare.events/publish" -H 'Content-Type: application/json' -d "$(jq -nc --arg payload "${payload}" '{properties:{content_type:"application/json",type:"student-records.registration-confirmed.v1"},routing_key:"student-records.registration-confirmed.v1",payload:$payload,payload_encoding:"string"}')" | jq -e '.routed==true' >/dev/null; }
publish
for _ in {1..40}; do count=$(docker exec "${postgres_container}" psql -U postgres -d emhare_finance -Atc "SELECT count(*) FROM finance_billing_events WHERE source_event_id='${registration_event_id}'");[[ "${count}" == 3 ]] && break;sleep 0.25;done
[[ "${count}" == 3 ]]

current_step='proving inbox idempotency and line-basis expansion'
publish;sleep 1
evidence=$(docker exec "${postgres_container}" psql -U postgres -d emhare_finance -Atc "SELECT (SELECT count(*) FROM finance_billing_events WHERE source_event_id='${registration_event_id}'),(SELECT count(*) FROM finance_billing_events WHERE source_event_id='${registration_event_id}' AND source_line_reference LIKE 'PROGRAMME-BILLING%'),(SELECT count(*) FROM finance_billing_events WHERE source_event_id='${registration_event_id}' AND source_line_reference LIKE 'MODULE-BILLING%'),(SELECT sum(base_amount) FROM finance_billing_events WHERE source_event_id='${registration_event_id}'),(SELECT count(*) FROM integration_inbox WHERE event_id='${registration_event_id}' AND processed_at IS NOT NULL)" | tr '|' ':')
[[ "${evidence}" == '3:1:2:550.00:1' ]]
jq -nc --arg evidence "${evidence}" '{result:"PASS",evidenceCounts:$evidence,registrationChargeCreated:true,moduleChargesCreated:2,inboxIdempotencyPreserved:true,financeOwnedPoliciesApplied:true,usdBasePreserved:true}'
