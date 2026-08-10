#!/usr/bin/env bash

# Author: Tinashe K
# Verifies maker-checker fee catalogue and effective pricing governance with cleanup.

set -euo pipefail
current_step='initialising Finance fee governance harness'
trap 'status=$?; printf "FAIL: %s (exit %s)\n" "${current_step}" "${status}" >&2; exit "${status}"' ERR
keycloak_base_url="${KEYCLOAK_BASE_URL:-http://localhost:8099}"
finance_base_url="${FINANCE_BASE_URL:-http://localhost:19084}"
postgres_container="${POSTGRES_CONTAINER:-emhare-flyway-postgres}"
run_identifier=$(uuidgen | tr '[:upper:]' '[:lower:]'); suffix=$(tr -d '-' <<<"${run_identifier}" | cut -c1-8 | tr '[:lower:]' '[:upper:]')
client_id="e2e-finance-fees-${run_identifier}"; test_password='Temporary-Finance-Password-42'
client_uuid=''; preparer_user_id=''; approver_user_id=''; catalogue_id=''

current_step='obtaining Keycloak administration token'
admin_token=$(curl -fsS -X POST "${keycloak_base_url}/realms/master/protocol/openid-connect/token" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode grant_type=password --data-urlencode client_id=admin-cli --data-urlencode username="${KEYCLOAK_ADMIN_USERNAME:-admin}" --data-urlencode password="${KEYCLOAK_ADMIN_PASSWORD:-admin}" | jq -er .access_token)
cleanup() {
  set +e
  if [[ -n "${catalogue_id}" ]]; then
    docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_finance -v catalogue_id="${catalogue_id}" >/dev/null <<'SQL'
BEGIN; SET LOCAL session_replication_role=replica;
DELETE FROM finance_fee_rule_scopes_aud WHERE fee_rule_id IN (SELECT id FROM finance_fee_rules WHERE fee_catalogue_id=:'catalogue_id'::uuid);
DELETE FROM finance_fee_rules_aud WHERE fee_catalogue_id=:'catalogue_id'::uuid;
DELETE FROM finance_fee_catalogues_aud WHERE id=:'catalogue_id'::uuid;
DELETE FROM finance_fee_rule_scopes WHERE fee_rule_id IN (SELECT id FROM finance_fee_rules WHERE fee_catalogue_id=:'catalogue_id'::uuid);
DELETE FROM finance_fee_rules WHERE fee_catalogue_id=:'catalogue_id'::uuid;
DELETE FROM finance_fee_catalogues WHERE id=:'catalogue_id'::uuid;
COMMIT;
SQL
  fi
  for user_id in "${preparer_user_id}" "${approver_user_id}"; do [[ -z "${user_id}" ]] || curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/emhare/users/${user_id}" -H "Authorization: Bearer ${admin_token}"; done
  [[ -z "${client_uuid}" ]] || curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/emhare/clients/${client_uuid}" -H "Authorization: Bearer ${admin_token}"
}
trap cleanup EXIT

current_step='creating independent Finance operators'
curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/clients" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' -d "$(jq -nc --arg id "${client_id}" '{clientId:$id,enabled:true,publicClient:true,directAccessGrantsEnabled:true,standardFlowEnabled:false}')"
client_uuid=$(curl -fsS -G "${keycloak_base_url}/admin/realms/emhare/clients" -H "Authorization: Bearer ${admin_token}" --data-urlencode clientId="${client_id}" | jq -er '.[0].id')
system_admin_role=$(curl -fsS "${keycloak_base_url}/admin/realms/emhare/roles/system-admin" -H "Authorization: Bearer ${admin_token}")
create_operator() { local label="$1" email="${1}-${run_identifier}@example.test"; curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/users" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' -d "$(jq -nc --arg email "${email}" --arg password "${test_password}" --arg first "${label}" '{username:$email,email:$email,emailVerified:true,enabled:true,firstName:$first,lastName:"Finance Operator",credentials:[{type:"password",value:$password,temporary:false}]}')"; local user_id; user_id=$(curl -fsS -G "${keycloak_base_url}/admin/realms/emhare/users" -H "Authorization: Bearer ${admin_token}" --data-urlencode username="${email}" --data-urlencode exact=true | jq -er '.[0].id'); curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/users/${user_id}/role-mappings/realm" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' -d "[${system_admin_role}]"; printf '%s' "${user_id}"; }
login() { curl -fsS -X POST "${keycloak_base_url}/realms/emhare/protocol/openid-connect/token" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode grant_type=password --data-urlencode client_id="${client_id}" --data-urlencode username="$1-${run_identifier}@example.test" --data-urlencode password="${test_password}" | jq -er .access_token; }
preparer_user_id=$(create_operator preparer);approver_user_id=$(create_operator approver);preparer_token=$(login preparer);approver_token=$(login approver)
post_json(){ local token="$1" path="$2" payload="$3";curl -fsS -X POST "${finance_base_url}${path}" -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' -d "${payload}"; }

current_step='creating and independently activating a fee catalogue'
catalogue=$(post_json "${preparer_token}" /api/finance/fee-catalogues "$(jq -nc --arg code "TUITION-${suffix}" '{code:$code,name:"Undergraduate programme tuition",description:"Governed tuition charge",chargeType:"PROGRAMME",receivableAccountCode:"1100-AR",revenueAccountCode:"4100-TUITION"}')");catalogue_id=$(jq -er .id <<<"${catalogue}")
self_activation_status=$(curl -sS -o /tmp/emhare-finance-self-activation -w '%{http_code}' -X POST "${finance_base_url}/api/finance/fee-catalogues/${catalogue_id}/activate" -H "Authorization: Bearer ${preparer_token}" -H 'Content-Type: application/json' -d "$(jq -nc --argjson version "$(jq -er .version <<<"${catalogue}")" '{reason:"Invalid self activation",expectedVersion:$version}')")
[[ "${self_activation_status}" == '409' ]]
catalogue=$(post_json "${approver_token}" "/api/finance/fee-catalogues/${catalogue_id}/activate" "$(jq -nc --argjson version "$(jq -er .version <<<"${catalogue}")" '{reason:"Independent chart-account and charge-definition approval.",expectedVersion:$version}')")

current_step='creating and independently approving effective USD pricing'
rule=$(post_json "${preparer_token}" "/api/finance/fee-catalogues/${catalogue_id}/rules" "$(jq -nc '{transactionCurrencyCode:"USD",transactionAmount:1250,effectiveFrom:"2027-01-01T00:00:00Z",effectiveUntil:"2027-07-01T00:00:00Z",scopes:[{scopeDimension:"PROGRAMME",referenceCode:"BCOM",referenceName:"Bachelor of Commerce"}]}')")
self_approval_status=$(curl -sS -o /tmp/emhare-finance-self-price-approval -w '%{http_code}' -X POST "${finance_base_url}/api/finance/fee-catalogues/rules/$(jq -er .id <<<"${rule}")/approve" -H "Authorization: Bearer ${preparer_token}" -H 'Content-Type: application/json' -d "$(jq -nc --argjson version "$(jq -er .version <<<"${rule}")" '{reason:"Invalid self approval",expectedVersion:$version}')")
[[ "${self_approval_status}" == '409' ]]
rule=$(post_json "${approver_token}" "/api/finance/fee-catalogues/rules/$(jq -er .id <<<"${rule}")/approve" "$(jq -nc --argjson version "$(jq -er .version <<<"${rule}")" '{reason:"Independent price, scope, and effectivity approval.",expectedVersion:$version}')")
[[ "$(jq -er '[.status,.ratingStatus,.baseCurrencyCode,.baseAmount] | join("|")' <<<"${rule}")" == 'APPROVED|RATED|USD|1250.00' ]]

current_step='proving overlapping approved pricing is rejected'
overlap=$(post_json "${preparer_token}" "/api/finance/fee-catalogues/${catalogue_id}/rules" "$(jq -nc '{transactionCurrencyCode:"USD",transactionAmount:1350,effectiveFrom:"2027-02-01T00:00:00Z",effectiveUntil:"2027-06-01T00:00:00Z",scopes:[{scopeDimension:"PROGRAMME",referenceCode:"BCOM",referenceName:"Bachelor of Commerce"}]}')")
overlap_status=$(curl -sS -o /tmp/emhare-finance-overlap -w '%{http_code}' -X POST "${finance_base_url}/api/finance/fee-catalogues/rules/$(jq -er .id <<<"${overlap}")/approve" -H "Authorization: Bearer ${approver_token}" -H 'Content-Type: application/json' -d "$(jq -nc --argjson version "$(jq -er .version <<<"${overlap}")" '{reason:"Conflicting price",expectedVersion:$version}')")
[[ "${overlap_status}" == '409' ]]
evidence=$(docker exec "${postgres_container}" psql -U postgres -d emhare_finance -Atc "select (select count(*) from finance_fee_catalogues_aud where id='${catalogue_id}'),(select count(*) from finance_fee_rules where fee_catalogue_id='${catalogue_id}'),(select count(*) from finance_fee_rules where fee_catalogue_id='${catalogue_id}' and status='APPROVED'),(select count(*) from finance_fee_rule_scopes where fee_rule_id in (select id from finance_fee_rules where fee_catalogue_id='${catalogue_id}'))" | tr '|' ':')
jq -nc --arg evidence "${evidence}" '{result:"PASS",evidenceCounts:$evidence,selfActivationRejected:true,selfPriceApprovalRejected:true,overlappingPriceRejected:true,usdBasePreserved:true}'
