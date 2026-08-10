#!/usr/bin/env bash

# Author: Tinashe K
# Verifies governed exchange rates, suspense, reconciliation, FX allocation, reversals, receipts, and credit notes.

set -euo pipefail
current_step='initialising Finance collections harness'
trap 'status=$?; printf "FAIL: %s (exit %s)\n" "${current_step}" "${status}" >&2; exit "${status}"' ERR
keycloak_base_url="${KEYCLOAK_BASE_URL:-http://localhost:8099}"
finance_base_url="${FINANCE_BASE_URL:-http://localhost:19084}"
postgres_container="${POSTGRES_CONTAINER:-emhare-flyway-postgres}"
run_identifier=$(uuidgen | tr '[:upper:]' '[:lower:]');suffix=$(tr -d '-' <<<"${run_identifier}" | cut -c1-8 | tr '[:lower:]' '[:upper:]')
client_id="e2e-finance-collections-${run_identifier}";test_password='Temporary-Finance-Collections-42'
client_uuid='';cashier_user_id='';treasury_user_id='';reconciler_user_id='';allocator_user_id='';poster_user_id=''
account_id=$(uuidgen | tr '[:upper:]' '[:lower:]');student_id=$(uuidgen | tr '[:upper:]' '[:lower:]');historical_rate_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
catalogue_id=$(uuidgen | tr '[:upper:]' '[:lower:]');rule_id=$(uuidgen | tr '[:upper:]' '[:lower:]');event_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
invoice_id=$(uuidgen | tr '[:upper:]' '[:lower:]');invoice_line_id=$(uuidgen | tr '[:upper:]' '[:lower:]');payment_id='';active_rate_id='';credit_note_id=''

current_step='obtaining Keycloak administration token'
admin_token=$(curl -fsS -X POST "${keycloak_base_url}/realms/master/protocol/openid-connect/token" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode grant_type=password --data-urlencode client_id=admin-cli --data-urlencode username="${KEYCLOAK_ADMIN_USERNAME:-admin}" --data-urlencode password="${KEYCLOAK_ADMIN_PASSWORD:-admin}" | jq -er .access_token)
cleanup(){
  set +e
  docker exec -i "${postgres_container}" psql -q -U postgres -d emhare_finance -v account_id="${account_id}" -v catalogue_id="${catalogue_id}" -v historical_rate_id="${historical_rate_id}" -v active_rate_id="${active_rate_id:-00000000-0000-0000-0000-000000000000}" >/dev/null <<'SQL'
BEGIN; SET LOCAL session_replication_role=replica;
DELETE FROM finance_credit_note_lines_aud WHERE credit_note_id IN (SELECT id FROM finance_credit_notes WHERE invoice_id IN (SELECT id FROM finance_invoices WHERE student_finance_account_id=:'account_id'::uuid));
DELETE FROM finance_credit_notes_aud WHERE invoice_id IN (SELECT id FROM finance_invoices WHERE student_finance_account_id=:'account_id'::uuid);
DELETE FROM finance_credit_note_lines WHERE credit_note_id IN (SELECT id FROM finance_credit_notes WHERE invoice_id IN (SELECT id FROM finance_invoices WHERE student_finance_account_id=:'account_id'::uuid));
DELETE FROM finance_credit_notes WHERE invoice_id IN (SELECT id FROM finance_invoices WHERE student_finance_account_id=:'account_id'::uuid);
DELETE FROM student_payment_allocation_reversals_aud WHERE allocation_id IN (SELECT id FROM student_payment_allocations WHERE payment_id IN (SELECT id FROM student_account_payments WHERE provider_code=upper('E2E-'||:'account_id')));
DELETE FROM student_payment_reversals_aud WHERE payment_id IN (SELECT id FROM student_account_payments WHERE provider_code=upper('E2E-'||:'account_id'));
DELETE FROM student_payment_allocations_aud WHERE payment_id IN (SELECT id FROM student_account_payments WHERE provider_code=upper('E2E-'||:'account_id'));
DELETE FROM student_payment_receipts_aud WHERE payment_id IN (SELECT id FROM student_account_payments WHERE provider_code=upper('E2E-'||:'account_id'));
DELETE FROM student_payment_suspense_resolutions_aud WHERE payment_id IN (SELECT id FROM student_account_payments WHERE provider_code=upper('E2E-'||:'account_id'));
DELETE FROM student_account_payments_aud WHERE provider_code=upper('E2E-'||:'account_id');
DELETE FROM student_payment_allocation_reversals WHERE allocation_id IN (SELECT id FROM student_payment_allocations WHERE payment_id IN (SELECT id FROM student_account_payments WHERE provider_code=upper('E2E-'||:'account_id')));
DELETE FROM student_payment_reversals WHERE payment_id IN (SELECT id FROM student_account_payments WHERE provider_code=upper('E2E-'||:'account_id'));
DELETE FROM student_payment_allocations WHERE payment_id IN (SELECT id FROM student_account_payments WHERE provider_code=upper('E2E-'||:'account_id'));
DELETE FROM student_payment_receipts WHERE payment_id IN (SELECT id FROM student_account_payments WHERE provider_code=upper('E2E-'||:'account_id'));
DELETE FROM student_payment_suspense_resolutions WHERE payment_id IN (SELECT id FROM student_account_payments WHERE provider_code=upper('E2E-'||:'account_id'));
DELETE FROM student_account_payments WHERE provider_code=upper('E2E-'||:'account_id');
DELETE FROM finance_invoice_lines_aud WHERE invoice_id IN (SELECT id FROM finance_invoices WHERE student_finance_account_id=:'account_id'::uuid);
DELETE FROM finance_invoices_aud WHERE student_finance_account_id=:'account_id'::uuid;
DELETE FROM finance_billing_event_scopes_aud WHERE billing_event_id IN (SELECT id FROM finance_billing_events WHERE student_finance_account_id=:'account_id'::uuid);
DELETE FROM finance_billing_events_aud WHERE student_finance_account_id=:'account_id'::uuid;
DELETE FROM finance_invoice_lines WHERE invoice_id IN (SELECT id FROM finance_invoices WHERE student_finance_account_id=:'account_id'::uuid);
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
DELETE FROM exchange_rates_aud WHERE id IN (:'historical_rate_id'::uuid,:'active_rate_id'::uuid);
DELETE FROM exchange_rates WHERE id IN (:'historical_rate_id'::uuid,:'active_rate_id'::uuid);
COMMIT;
SQL
  for user_id in "${cashier_user_id}" "${treasury_user_id}" "${reconciler_user_id}" "${allocator_user_id}" "${poster_user_id}";do [[ -z "${user_id}" ]]||curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/emhare/users/${user_id}" -H "Authorization: Bearer ${admin_token}";done
  [[ -z "${client_uuid}" ]]||curl -fsS -o /dev/null -X DELETE "${keycloak_base_url}/admin/realms/emhare/clients/${client_uuid}" -H "Authorization: Bearer ${admin_token}"
}
trap cleanup EXIT

current_step='creating independent Finance operators'
curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/clients" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' -d "$(jq -nc --arg id "${client_id}" '{clientId:$id,enabled:true,publicClient:true,directAccessGrantsEnabled:true,standardFlowEnabled:false}')"
client_uuid=$(curl -fsS -G "${keycloak_base_url}/admin/realms/emhare/clients" -H "Authorization: Bearer ${admin_token}" --data-urlencode clientId="${client_id}" | jq -er '.[0].id')
system_admin_role=$(curl -fsS "${keycloak_base_url}/admin/realms/emhare/roles/system-admin" -H "Authorization: Bearer ${admin_token}")
create_operator(){ local label="$1" email="${1}-${run_identifier}@example.test";curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/users" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' -d "$(jq -nc --arg email "${email}" --arg password "${test_password}" --arg first "${label}" '{username:$email,email:$email,emailVerified:true,enabled:true,firstName:$first,lastName:"Finance Operator",credentials:[{type:"password",value:$password,temporary:false}]}')";local user_id;user_id=$(curl -fsS -G "${keycloak_base_url}/admin/realms/emhare/users" -H "Authorization: Bearer ${admin_token}" --data-urlencode username="${email}" --data-urlencode exact=true | jq -er '.[0].id');curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/users/${user_id}/role-mappings/realm" -H "Authorization: Bearer ${admin_token}" -H 'Content-Type: application/json' -d "[${system_admin_role}]";printf '%s' "${user_id}";}
login(){ curl -fsS -X POST "${keycloak_base_url}/realms/emhare/protocol/openid-connect/token" -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode grant_type=password --data-urlencode client_id="${client_id}" --data-urlencode username="$1-${run_identifier}@example.test" --data-urlencode password="${test_password}" | jq -er .access_token;}
cashier_user_id=$(create_operator cashier);treasury_user_id=$(create_operator treasury);reconciler_user_id=$(create_operator reconciler);allocator_user_id=$(create_operator allocator);poster_user_id=$(create_operator poster)
cashier_token=$(login cashier);treasury_token=$(login treasury);reconciler_token=$(login reconciler);allocator_token=$(login allocator);poster_token=$(login poster)
post_json(){ local token="$1" path="$2" payload="$3";curl -fsS -X POST "${finance_base_url}${path}" -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' -d "${payload}";}

current_step='creating a foreign-currency invoice fixture with historical USD basis'
docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_finance -v account_id="${account_id}" -v student_id="${student_id}" -v suffix="${suffix}" -v historical_rate_id="${historical_rate_id}" -v catalogue_id="${catalogue_id}" -v rule_id="${rule_id}" -v event_id="${event_id}" -v invoice_id="${invoice_id}" -v invoice_line_id="${invoice_line_id}" >/dev/null <<'SQL'
BEGIN;
INSERT INTO exchange_rates(id,source_currency_code,base_currency_code,rate_to_base,effective_from,effective_to,source_name,source_reference,status,prepared_by_user_id,approved_by_user_id,approved_at,approval_reason,created_at,updated_at,version) VALUES (:'historical_rate_id'::uuid,'ZWG','USD',0.04,'2026-01-01','2027-01-01','RBZ','E2E-HISTORICAL','ACTIVE',gen_random_uuid(),gen_random_uuid(),now(),'Historical invoice valuation fixture',now(),now(),0);
INSERT INTO student_finance_accounts(id,account_number,student_id,student_number,user_id,source_offer_id,primary_email,base_currency_code,status,opened_at,created_at,updated_at,version) VALUES (:'account_id'::uuid,'SFA-'||:'suffix',:'student_id'::uuid,'R'||:'suffix',gen_random_uuid(),gen_random_uuid(),lower(:'suffix')||'@example.test','USD','ACTIVE',now(),now(),now(),0);
INSERT INTO finance_fee_catalogues(id,code,name,charge_type,receivable_account_code,revenue_account_code,base_currency_code,status,prepared_by_user_id,activated_by_user_id,activated_at,activation_reason,created_at,updated_at,version) VALUES (:'catalogue_id'::uuid,'COL-'||:'suffix','Collections validation tuition','PROGRAMME','1100-AR','4100-TUITION','USD','ACTIVE',gen_random_uuid(),gen_random_uuid(),now(),'Fixture approval',now(),now(),0);
INSERT INTO finance_fee_rules(id,fee_catalogue_id,rule_version,transaction_currency_code,transaction_amount,base_currency_code,exchange_rate_id,base_amount,rating_status,effective_from,effective_until,status,prepared_by_user_id,created_at,updated_at,version) VALUES (:'rule_id'::uuid,:'catalogue_id'::uuid,1,'ZWG',1000,'USD',:'historical_rate_id'::uuid,40,'RATED','2026-02-01','2026-12-01','DRAFT',gen_random_uuid(),now(),now(),0);
INSERT INTO finance_fee_rule_scopes(id,fee_rule_id,scope_dimension,created_at,updated_at,version) VALUES (gen_random_uuid(),:'rule_id'::uuid,'GLOBAL',now(),now(),0);
UPDATE finance_fee_rules SET status='APPROVED',approved_by_user_id=gen_random_uuid(),approved_at=now(),approval_reason='Fixture approval',updated_at=now() WHERE id=:'rule_id'::uuid;
INSERT INTO finance_billing_events(id,event_number,source_service,source_event_type,source_event_id,source_aggregate_type,source_aggregate_id,source_line_reference,student_finance_account_id,student_id,student_number,fee_catalogue_id,fee_rule_id,description,quantity,transaction_currency_code,transaction_unit_amount,transaction_amount,base_currency_code,exchange_rate_id,base_unit_amount,base_amount,effective_at,status,prepared_by_user_id,submitted_at,created_at,updated_at,version) VALUES (:'event_id'::uuid,'BLE-'||:'suffix','e2e','e2e.collections.v1',gen_random_uuid(),'TEST',gen_random_uuid(),'LINE',:'account_id'::uuid,:'student_id'::uuid,'R'||:'suffix',:'catalogue_id'::uuid,:'rule_id'::uuid,'ZWG tuition',1,'ZWG',1000,1000,'USD',:'historical_rate_id'::uuid,40,40,'2026-03-01','PENDING_APPROVAL',gen_random_uuid(),now(),now(),now(),0);
UPDATE finance_billing_events SET status='APPROVED',approved_by_user_id=gen_random_uuid(),approved_at=now(),approval_reason='Fixture approval',updated_at=now() WHERE id=:'event_id'::uuid;
INSERT INTO finance_invoices(id,invoice_number,student_finance_account_id,student_id,student_number,transaction_currency_code,base_currency_code,gross_transaction_amount,gross_base_amount,invoice_date,due_date,status,posted_by_user_id,posted_at,posting_reason,created_at,updated_at,version) VALUES (:'invoice_id'::uuid,'INV-'||:'suffix',:'account_id'::uuid,:'student_id'::uuid,'R'||:'suffix','ZWG','USD',1000,40,'2026-03-01','2026-03-31','POSTED',gen_random_uuid(),now(),'Fixture posting',now(),now(),0);
INSERT INTO finance_invoice_lines(id,invoice_id,line_number,billing_event_id,fee_catalogue_id,fee_rule_id,fee_code,description,quantity,transaction_currency_code,transaction_unit_amount,transaction_amount,base_currency_code,exchange_rate_id,base_unit_amount,base_amount,receivable_account_code,revenue_account_code,created_at,updated_at,version) VALUES (:'invoice_line_id'::uuid,:'invoice_id'::uuid,1,:'event_id'::uuid,:'catalogue_id'::uuid,:'rule_id'::uuid,'COL-'||:'suffix','ZWG tuition',1,'ZWG',1000,1000,'USD',:'historical_rate_id'::uuid,40,40,'1100-AR','4100-TUITION',now(),now(),0);
UPDATE finance_billing_events SET status='INVOICED',invoiced_at=now(),updated_at=now() WHERE id=:'event_id'::uuid;
COMMIT;
SQL

current_step='capturing an unrated ZWG payment into suspense'
payment=$(post_json "${cashier_token}" /api/finance/collections/payments "$(jq -nc --arg provider "E2E-${account_id}" --arg reference "TX-${suffix}" --arg fingerprint "SHA256-${run_identifier}" '{payerName:"Unidentified student payer",providerCode:$provider,providerTransactionReference:$reference,paymentChannel:"BANK_TRANSFER",transactionCurrencyCode:"ZWG",transactionAmount:1000,paidAt:"2027-02-15T10:00:00Z",providerEventFingerprint:$fingerprint}')")
payment_id=$(jq -er .id <<<"${payment}");[[ "$(jq -er '[.ratingStatus,.reconciliationStatus,.inSuspense,.baseAmount] | map(tostring) | join("|")' <<<"${payment}")" == 'UNRATED|PENDING|true|null' ]]

current_step='proving an unrated payment cannot be reconciled'
unrated_status=$(curl -sS -o /tmp/emhare-finance-unrated-reconcile -w '%{http_code}' -X POST "${finance_base_url}/api/finance/collections/payments/${payment_id}/reconcile" -H "Authorization: Bearer ${reconciler_token}" -H 'Content-Type: application/json' -d "$(jq -nc --argjson version "$(jq -er .version <<<"${payment}")" '{reason:"Premature reconciliation",expectedVersion:$version}')")
[[ "${unrated_status}" == '409' ]]

current_step='creating and independently approving the effective ZWG rate'
rate=$(post_json "${treasury_token}" /api/finance/collections/exchange-rates "$(jq -nc '{sourceCurrencyCode:"ZWG",rateToBase:0.05,effectiveFrom:"2027-01-01T00:00:00Z",effectiveTo:"2028-01-01T00:00:00Z",sourceName:"Reserve Bank of Zimbabwe",sourceReference:"RBZ-E2E-2027"}')");active_rate_id=$(jq -er .id <<<"${rate}")
self_rate_status=$(curl -sS -o /tmp/emhare-finance-rate-self -w '%{http_code}' -X POST "${finance_base_url}/api/finance/collections/exchange-rates/${active_rate_id}/approve" -H "Authorization: Bearer ${treasury_token}" -H 'Content-Type: application/json' -d "$(jq -nc --argjson version "$(jq -er .version <<<"${rate}")" '{reason:"Invalid self approval",expectedVersion:$version}')")
[[ "${self_rate_status}" == '409' ]]
rate=$(post_json "${reconciler_token}" "/api/finance/collections/exchange-rates/${active_rate_id}/approve" "$(jq -nc --argjson version "$(jq -er .version <<<"${rate}")" '{reason:"Published rate and effectivity independently verified.",expectedVersion:$version}')")

current_step='applying the effective rate and independently reconciling the payment'
payment=$(post_json "${treasury_token}" "/api/finance/collections/payments/${payment_id}/apply-rate?expectedVersion=$(jq -er .version <<<"${payment}")" '{}')
[[ "$(jq -er '[.ratingStatus,.baseCurrencyCode,.baseAmount] | join("|")' <<<"${payment}")" == 'RATED|USD|50.00' ]]
payment=$(post_json "${reconciler_token}" "/api/finance/collections/payments/${payment_id}/reconcile" "$(jq -nc --argjson version "$(jq -er .version <<<"${payment}")" '{reason:"Bank statement and provider evidence reconciled.",expectedVersion:$version}')")
[[ "$(jq -er '[.reconciliationStatus,.inSuspense,.receiptNumber] | map(tostring) | join("|")' <<<"${payment}")" == 'RECONCILED|true|null' ]]

current_step='resolving suspense and issuing the controlled receipt'
payment=$(post_json "${allocator_token}" "/api/finance/collections/payments/${payment_id}/resolve-suspense" "$(jq -nc --arg account "${account_id}" --argjson version "$(jq -er .version <<<"${payment}")" '{studentFinanceAccountId:$account,reason:"Student number and bank narrative independently matched.",expectedPaymentVersion:$version}')")
receipt_number=$(jq -er .receiptNumber <<<"${payment}");[[ "$(jq -er '[.inSuspense,(.receiptNumber!=null)] | map(tostring) | join("|")' <<<"${payment}")" == 'false|true' ]]

current_step='allocating with separate payment and invoice USD bases'
allocation=$(post_json "${allocator_token}" "/api/finance/collections/payments/${payment_id}/allocations" "$(jq -nc --arg invoice "${invoice_id}" --argjson version "$(jq -er .version <<<"${payment}")" '{invoiceId:$invoice,transactionAmount:1000,reason:"Full settlement against the matching student invoice.",expectedPaymentVersion:$version}')")
allocation_id=$(jq -er .id <<<"${allocation}");[[ "$(jq -er '[.transactionAmount,.paymentBaseAmount,.invoiceBaseAmount,.realisedExchangeDifference] | join("|")' <<<"${allocation}")" == '1000.00|50.00|40.00|10.00' ]]

current_step='proving active allocations block payment reversal'
blocked_reversal_status=$(curl -sS -o /tmp/emhare-finance-payment-reversal -w '%{http_code}' -X POST "${finance_base_url}/api/finance/collections/payments/${payment_id}/reverse" -H "Authorization: Bearer ${poster_token}" -H 'Content-Type: application/json' -d "$(jq -nc --argjson version "$(jq -er .version <<<"${payment}")" '{reason:"Premature payment reversal",expectedVersion:$version}')")
[[ "${blocked_reversal_status}" == '409' ]]

current_step='reversing allocation and payment through append-only corrections'
allocation=$(post_json "${poster_token}" "/api/finance/collections/allocations/${allocation_id}/reverse" "$(jq -nc --argjson version "$(jq -er '.version // 0' <<<"${allocation}")" '{reason:"Allocation reversal independently authorised.",expectedVersion:$version}')")
[[ "$(jq -er .reversed <<<"${allocation}")" == 'true' ]]
payment=$(post_json "${poster_token}" "/api/finance/collections/payments/${payment_id}/reverse" "$(jq -nc --argjson version "$(jq -er .version <<<"${payment}")" '{reason:"Returned payment independently authorised.",expectedVersion:$version}')")
[[ "$(jq -er .reversed <<<"${payment}")" == 'true' ]]

current_step='preparing and independently posting a line-linked credit note'
credit=$(post_json "${allocator_token}" /api/finance/collections/credit-notes "$(jq -nc --arg invoice "${invoice_id}" --arg line "${invoice_line_id}" '{invoiceId:$invoice,creditNoteDate:"2027-02-16",preparationReason:"Approved tuition correction submitted.",lines:[{invoiceLineId:$line,transactionAmount:100,baseAmount:4,reason:"Ten percent tuition correction"}]}')");credit_note_id=$(jq -er .id <<<"${credit}")
self_credit_status=$(curl -sS -o /tmp/emhare-finance-credit-self -w '%{http_code}' -X POST "${finance_base_url}/api/finance/collections/credit-notes/${credit_note_id}/post" -H "Authorization: Bearer ${allocator_token}" -H 'Content-Type: application/json' -d "$(jq -nc --argjson version "$(jq -er .version <<<"${credit}")" '{reason:"Invalid self posting",expectedVersion:$version}')")
[[ "${self_credit_status}" == '409' ]]
credit=$(post_json "${poster_token}" "/api/finance/collections/credit-notes/${credit_note_id}/post" "$(jq -nc --argjson version "$(jq -er .version <<<"${credit}")" '{reason:"Source invoice and correction authority independently verified.",expectedVersion:$version}')")
[[ "$(jq -er '[.status,.transactionAmount,.baseAmount,(.lines|length)] | join("|")' <<<"${credit}")" == 'POSTED|100.00|4.00|1' ]]

current_step='verifying the chronological student account statement'
statement=$(curl -fsS "${finance_base_url}/api/finance/collections/accounts/${account_id}/statement" -H "Authorization: Bearer ${poster_token}")
[[ "$(jq -er '[.account.baseBalance,(.lines|length),([.lines[].lineType]|sort|join(","))] | join("|")' <<<"${statement}")" == '36.00|4|CREDIT_NOTE,INVOICE,PAYMENT,PAYMENT_REVERSAL' ]]

current_step='proving posted credit-note mutation is rejected'
if docker exec "${postgres_container}" psql -v ON_ERROR_STOP=1 -U postgres -d emhare_finance -c "UPDATE finance_credit_notes SET transaction_amount=1 WHERE id='${credit_note_id}'" >/tmp/emhare-finance-credit-mutation 2>&1;then exit 1;fi

current_step='verifying persistent audit and correction evidence'
evidence=$(docker exec "${postgres_container}" psql -U postgres -d emhare_finance -Atc "SELECT (SELECT count(*) FROM student_account_payments_aud WHERE id='${payment_id}'),(SELECT count(*) FROM student_payment_receipts WHERE payment_id='${payment_id}'),(SELECT count(*) FROM student_payment_allocation_reversals r JOIN student_payment_allocations a ON a.id=r.allocation_id WHERE a.payment_id='${payment_id}'),(SELECT count(*) FROM student_payment_reversals WHERE payment_id='${payment_id}'),(SELECT count(*) FROM finance_credit_notes WHERE id='${credit_note_id}' AND status='POSTED'),(SELECT count(*) FROM finance_credit_notes_aud WHERE id='${credit_note_id}'),(SELECT realised_exchange_difference FROM student_payment_allocations WHERE id='${allocation_id}')" | tr '|' ':')
[[ "${evidence}" == '3:1:1:1:1:2:10.00' ]]
jq -nc --arg evidence "${evidence}" --arg receipt "${receipt_number}" '{result:"PASS",evidenceCounts:$evidence,receiptNumber:$receipt,unratedPaymentBlocked:true,rateMakerCheckerEnforced:true,suspenseResolved:true,realisedFxRecorded:true,activeAllocationBlockedPaymentReversal:true,appendOnlyReversalsRecorded:true,creditNoteMakerCheckerEnforced:true,studentStatementReconciled:true,postedCreditImmutable:true,usdBasePreserved:true}'
