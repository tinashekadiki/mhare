#!/usr/bin/env bash

# Author: Tinashe K
# Verifies governed billing approval and atomic immutable invoice posting with cleanup.

set -euo pipefail
current_step='initialising Finance billing harness'
trap 'status=$?; printf "FAIL: %s (exit %s)\n" "${current_step}" "${status}" >&2; exit "${status}"' ERR
keycloak_base_url="${KEYCLOAK_BASE_URL:-http://localhost:8099}"
finance_base_url="${FINANCE_BASE_URL:-http://localhost:19084}"
postgres_container="${POSTGRES_CONTAINER:-emhare-postgres}"
run_identifier=$(uuidgen | tr '[:upper:]' '[:lower:]'); compact_identifier=$(tr -d '-' <<<"${run_identifier}"); suffix=$(cut -c1-8 <<<"${compact_identifier}" | tr '[:lower:]' '[:upper:]')
client_id="e2e-finance-billing-${run_identifier}"; test_password='Temporary-Finance-Billing-42'
client_uuid=''; preparer_user_id=''; approver_user_id=''; poster_user_id=''; catalogue_id=''; account_id=$(uuidgen | tr '[:upper:]' '[:lower:]'); student_id=$(uuidgen | tr '[:upper:]' '[:lower:]')

current_step='obtaining Keycloak administration token'
admin_token=$(curl -fsS -X POST "${keycloak_base_url}/realms/master/protocol/openid-connect/token" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode grant_type=password --data-urlencode client_id=admin-cli --data-urlencode username="${KEYCLOAK_ADMIN_USERNAME:-admin}" --data-urlencode password="${KEYCLOAK_ADMIN_PASSWORD:-admin}" | jq -er .access_token)
cleanup() {
  set +e
  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_finance -v catalogue_id="${catalogue_id:-00000000-0000-0000-0000-000000000000}" -v account_id="${account_id}" >/dev/null <<'SQL'
BEGIN; SET LOCAL session_replication_role=replica;
DELETE FROM finance_invoice_lines_aud WHERE billing_event_id IN (SELECT id FROM finance_billing_events WHERE student_finance_account_id=:'account_id'::uuid);
DELETE FROM finance_invoices_aud WHERE student_finance_account_id=:'account_id'::uuid;
DELETE FROM finance_billing_event_scopes_aud WHERE billing_event_id IN (SELECT id FROM finance_billing_events WHERE student_finance_account_id=:'account_id'::uuid);
DELETE FROM finance_billing_events_aud WHERE student_finance_account_id=:'account_id'::uuid;
DELETE FROM finance_invoice_lines WHERE billing_event_id IN (SELECT id FROM finance_billing_events WHERE student_finance_account_id=:'account_id'::uuid);
DELETE FROM finance_invoices WHERE student_finance_account_id=:'account_id'::uuid;
DELETE FROM finance_billing_event_scopes WHERE billing_event_id IN (SELECT id FROM finance_billing_events WHERE student_finance_account_id=:'account_id'::uuid);
DELETE FROM finance_billing_events WHERE student_finance_account_id=:'account_id'::uuid;
DELETE FROM finance_fee_rule_scopes_aud WHERE fee_rule_id IN (SELECT id FROM finance_fee_rules WHERE fee_catalogue_id=:'catalogue_id'::uuid);
DELETE FROM finance_fee_rules_aud WHERE fee_catalogue_id=:'catalogue_id'::uuid;
DELETE FROM finance_fee_catalogues_aud WHERE id=:'catalogue_id'::uuid;
DELETE FROM finance_fee_rule_scopes WHERE fee_rule_id IN (SELECT id FROM finance_fee_rules WHERE fee_catalogue_id=:'catalogue_id'::uuid);
DELETE FROM finance_fee_rules WHERE fee_catalogue_id=:'catalogue_id'::uuid;
DELETE FROM finance_fee_catalogues WHERE id=:'catalogue_id'::uuid;
DELETE FROM student_finance_accounts_aud WHERE id=:'account_id'::uuid;
DELETE FROM student_finance_accounts WHERE id=:'account_id'::uuid;
COMMIT;
SQL
  for user_id in "${preparer_user_id}" "${approver_user_id}" "${poster_user_id}"; do [[ -z "${user_id}" ]] || curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/emhare/users/${user_id}" -H "Authorization: Bearer ${admin_token}"; done
  [[ -z "${client_uuid}" ]] || curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/emhare/clients/${client_uuid}" -H "Authorization: Bearer ${admin_token}"
}
trap cleanup EXIT

current_step='creating independent Finance operators'
curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/clients" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' -d "$(jq -nc --arg id "${client_id}" '{clientId:$id,enabled:true,publicClient:true,directAccessGrantsEnabled:true,standardFlowEnabled:false}')"
client_uuid=$(curl -fsS -G "${keycloak_base_url}/admin/realms/emhare/clients" -H "Authorization: Bearer ${admin_token}" --data-urlencode clientId="${client_id}" | jq -er '.[0].id')
system_admin_role=$(curl -fsS "${keycloak_base_url}/admin/realms/emhare/roles/system-admin" -H "Authorization: Bearer ${admin_token}")
create_operator() { local label="$1" email="${1}-${run_identifier}@example.test"; curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/users" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' -d "$(jq -nc --arg email "${email}" --arg password "${test_password}" --arg first "${label}" '{username:$email,email:$email,emailVerified:true,enabled:true,firstName:$first,lastName:"Finance Operator",credentials:[{type:"password",value:$password,temporary:false}]}')"; local user_id; user_id=$(curl -fsS -G "${keycloak_base_url}/admin/realms/emhare/users" -H "Authorization: Bearer ${admin_token}" --data-urlencode username="${email}" --data-urlencode exact=true | jq -er '.[0].id'); curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/users/${user_id}/role-mappings/realm" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' -d "[${system_admin_role}]"; printf '%s' "${user_id}"; }
login() { curl -fsS -X POST "${keycloak_base_url}/realms/emhare/protocol/openid-connect/token" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode grant_type=password --data-urlencode client_id="${client_id}" --data-urlencode username="$1-${run_identifier}@example.test" --data-urlencode password="${test_password}" | jq -er .access_token; }
preparer_user_id=$(create_operator preparer);approver_user_id=$(create_operator approver);poster_user_id=$(create_operator poster)
preparer_token=$(login preparer);approver_token=$(login approver);poster_token=$(login poster)
post_json(){ local token="$1" path="$2" payload="$3";curl -fsS -X POST "${finance_base_url}${path}" -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' -d "${payload}"; }

current_step='creating active student finance account fixture'
docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_finance -v account_id="${account_id}" -v student_id="${student_id}" -v suffix="${suffix}" >/dev/null <<'SQL'
INSERT INTO student_finance_accounts(id,account_number,student_id,student_number,user_id,source_offer_id,primary_email,base_currency_code,status,opened_at,created_at,updated_at,version)
VALUES (:'account_id'::uuid,'SFA-'||:'suffix',:'student_id'::uuid,'R'||:'suffix',gen_random_uuid(),gen_random_uuid(),lower(:'suffix')||'@example.test','USD','ACTIVE',now(),now(),now(),0);
SQL

current_step='creating independently governed billing price'
catalogue=$(post_json "${preparer_token}" /api/finance/fee-catalogues "$(jq -nc --arg code "REG-${suffix}" '{code:$code,name:"Registration tuition",description:"Registration-triggered programme tuition",chargeType:"PROGRAMME",receivableAccountCode:"1100-AR",revenueAccountCode:"4100-TUITION"}')");catalogue_id=$(jq -er .id <<<"${catalogue}")
catalogue=$(post_json "${approver_token}" "/api/finance/fee-catalogues/${catalogue_id}/activate" "$(jq -nc --argjson version "$(jq -er .version <<<"${catalogue}")" '{reason:"Independent charge and posting-account approval.",expectedVersion:$version}')")
rule=$(post_json "${preparer_token}" "/api/finance/fee-catalogues/${catalogue_id}/rules" "$(jq -nc '{transactionCurrencyCode:"USD",transactionAmount:125,effectiveFrom:"2027-01-01T00:00:00Z",effectiveUntil:"2028-01-01T00:00:00Z",scopes:[{scopeDimension:"GLOBAL"}]}')")
rule=$(post_json "${approver_token}" "/api/finance/fee-catalogues/rules/$(jq -er .id <<<"${rule}")/approve" "$(jq -nc --argjson version "$(jq -er .version <<<"${rule}")" '{reason:"Independent price and effectivity approval.",expectedVersion:$version}')")

current_step='submitting two idempotent billing source lines'
source_event_id=$(uuidgen | tr '[:upper:]' '[:lower:]');source_aggregate_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
create_event(){ local reference="$1" quantity="$2" description="$3"; post_json "${preparer_token}" /api/finance/billing/events "$(jq -nc --arg source_event_id "${source_event_id}" --arg source_aggregate_id "${source_aggregate_id}" --arg reference "${reference}" --arg account_id "${account_id}" --arg catalogue_id "${catalogue_id}" --arg description "${description}" --argjson quantity "${quantity}" '{sourceService:"student-records-service",sourceEventType:"student-records.registration-confirmed.v1",sourceEventId:$source_event_id,sourceAggregateType:"REGISTRATION",sourceAggregateId:$source_aggregate_id,sourceLineReference:$reference,studentFinanceAccountId:$account_id,feeCatalogueId:$catalogue_id,description:$description,quantity:$quantity,effectiveAt:"2027-02-01T00:00:00Z",scopes:[{scopeDimension:"GLOBAL"}]}')"; }
first_event=$(create_event PROGRAMME 2 'Programme registration tuition');second_event=$(create_event ADMIN 1 'Registration administration charge')
first_event_id=$(jq -er .id <<<"${first_event}");second_event_id=$(jq -er .id <<<"${second_event}")
[[ "$(jq -er '[.status,.transactionAmount,.baseAmount,.baseCurrencyCode] | join("|")' <<<"${first_event}")" == 'PENDING_APPROVAL|250.00|250.00|USD' ]]

current_step='proving self-approval and duplicate source lines are rejected'
self_status=$(curl -sS -o /tmp/emhare-finance-billing-self -w '%{http_code}' -X POST "${finance_base_url}/api/finance/billing/events/${first_event_id}/approve" -H "Authorization: Bearer ${preparer_token}" -H 'Content-Type: application/json' -d "$(jq -nc --argjson version "$(jq -er .version <<<"${first_event}")" '{reason:"Invalid self approval",expectedVersion:$version}')")
[[ "${self_status}" == '409' ]]
duplicate_status=$(curl -sS -o /tmp/emhare-finance-billing-duplicate -w '%{http_code}' -X POST "${finance_base_url}/api/finance/billing/events" -H "Authorization: Bearer ${preparer_token}" -H 'Content-Type: application/json' -d "$(jq -nc --arg source_event_id "${source_event_id}" --arg source_aggregate_id "${source_aggregate_id}" --arg account_id "${account_id}" --arg catalogue_id "${catalogue_id}" '{sourceService:"student-records-service",sourceEventType:"student-records.registration-confirmed.v1",sourceEventId:$source_event_id,sourceAggregateType:"REGISTRATION",sourceAggregateId:$source_aggregate_id,sourceLineReference:"PROGRAMME",studentFinanceAccountId:$account_id,feeCatalogueId:$catalogue_id,description:"Duplicate",quantity:2,effectiveAt:"2027-02-01T00:00:00Z",scopes:[{scopeDimension:"GLOBAL"}]}')")
[[ "${duplicate_status}" == '409' ]]

current_step='independently approving billing events'
first_event=$(post_json "${approver_token}" "/api/finance/billing/events/${first_event_id}/approve" "$(jq -nc --argjson version "$(jq -er .version <<<"${first_event}")" '{reason:"Registration source and approved pricing independently verified.",expectedVersion:$version}')")
second_event=$(post_json "${approver_token}" "/api/finance/billing/events/${second_event_id}/approve" "$(jq -nc --argjson version "$(jq -er .version <<<"${second_event}")" '{reason:"Registration source and approved pricing independently verified.",expectedVersion:$version}')")

current_step='atomically posting a reconciled multi-line invoice'
invoice=$(post_json "${poster_token}" /api/finance/billing/invoices "$(jq -nc --arg first "${first_event_id}" --arg second "${second_event_id}" '{billingEventIds:[$first,$second],invoiceDate:"2027-02-01",dueDate:"2027-02-28",postingReason:"Approved registration billing batch posted after total reconciliation."}')")
[[ "$(jq -er '[.status,.grossTransactionAmount,.grossBaseAmount,(.lines|length)] | join("|")' <<<"${invoice}")" == 'POSTED|375.00|375.00|2' ]]

current_step='proving posted invoice mutation is rejected by the database'
invoice_id=$(jq -er .id <<<"${invoice}")
if docker exec "${postgres_container}" psql -v ON_ERROR_STOP=1 -U postgres -d emhare_finance -c "UPDATE finance_invoices SET gross_base_amount=1 WHERE id='${invoice_id}'" >/tmp/emhare-finance-invoice-mutation 2>&1; then exit 1; fi

current_step='verifying immutable audit and reconciliation evidence'
evidence=$(docker exec "${postgres_container}" psql -U postgres -d emhare_finance -Atc "SELECT (SELECT count(*) FROM finance_billing_events WHERE student_finance_account_id='${account_id}' AND status='INVOICED'),(SELECT count(*) FROM finance_billing_events_aud WHERE student_finance_account_id='${account_id}'),(SELECT count(*) FROM finance_invoice_lines WHERE invoice_id='${invoice_id}'),(SELECT count(*) FROM finance_invoices_aud WHERE id='${invoice_id}'),(SELECT gross_base_amount FROM finance_invoices WHERE id='${invoice_id}')" | tr '|' ':')
[[ "${evidence}" == '2:6:2:1:375.00' ]]
jq -nc --arg evidence "${evidence}" '{result:"PASS",evidenceCounts:$evidence,selfApprovalRejected:true,duplicateSourceRejected:true,postedInvoiceImmutable:true,usdBasePreserved:true,reconciledLineCount:2}'
