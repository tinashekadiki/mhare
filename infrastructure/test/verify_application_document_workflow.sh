#!/usr/bin/env bash

# Author: Tinashe K
# Verifies private document upload, Admissions linking, independent rejection,
# notifications, correction workflow creation, and broker-redelivery idempotency.

set -euo pipefail

gateway_base_url="${GATEWAY_BASE_URL:-http://localhost:8080}"
keycloak_base_url="${KEYCLOAK_BASE_URL:-http://localhost:8099}"
postgres_container="${POSTGRES_CONTAINER:-emhare-flyway-postgres}"
rabbitmq_management_url="${RABBITMQ_MANAGEMENT_URL:-http://localhost:15672}"

run_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
keycloak_client_id="e2e-documents-${run_id}"
applicant_email="e2e-document-applicant-${run_id}@example.test"
reviewer_email="e2e-document-reviewer-${run_id}@example.test"
test_password='Temporary-E2E-Password-42'
admission_cycle_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_type_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
applicant_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
academic_year_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
intake_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
application_number="E2E-DOC-${run_id:0:8}"
invalid_document_file=$(mktemp /tmp/emhare-invalid-document.XXXXXX)
valid_document_file=$(mktemp /tmp/emhare-valid-document.XXXXXX)
downloaded_document_file=$(mktemp /tmp/emhare-downloaded-document.XXXXXX)

printf 'not a real PDF' > "${invalid_document_file}"
printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=' \
  | openssl base64 -d -A > "${valid_document_file}"

keycloak_admin_token=$(curl -fsS -X POST \
  "${keycloak_base_url}/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=password \
  --data-urlencode client_id=admin-cli \
  --data-urlencode username="${KEYCLOAK_ADMIN_USERNAME:-admin}" \
  --data-urlencode password="${KEYCLOAK_ADMIN_PASSWORD:-admin}" | jq -er .access_token)

curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/clients" \
  -H "Authorization: Bearer ${keycloak_admin_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg clientId "${keycloak_client_id}" \
    '{clientId:$clientId,enabled:true,publicClient:true,directAccessGrantsEnabled:true,standardFlowEnabled:false,protocol:"openid-connect"}')"

create_keycloak_user() {
  local email_address="$1"
  local first_name="$2"
  local last_name="$3"
  curl -fsS -o /dev/null -X POST "${keycloak_base_url}/admin/realms/emhare/users" \
    -H "Authorization: Bearer ${keycloak_admin_token}" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc \
      --arg email "${email_address}" \
      --arg password "${test_password}" \
      --arg firstName "${first_name}" \
      --arg lastName "${last_name}" \
      '{username:$email,email:$email,emailVerified:true,enabled:true,firstName:$firstName,lastName:$lastName,credentials:[{type:"password",value:$password,temporary:false}]}')"
  curl -fsS -G "${keycloak_base_url}/admin/realms/emhare/users" \
    -H "Authorization: Bearer ${keycloak_admin_token}" \
    --data-urlencode username="${email_address}" \
    --data-urlencode exact=true | jq -er '.[0].id'
}

applicant_keycloak_user_id=$(create_keycloak_user "${applicant_email}" Enterprise Applicant)
reviewer_keycloak_user_id=$(create_keycloak_user "${reviewer_email}" Document Reviewer)
applicant_role=$(curl -fsS "${keycloak_base_url}/admin/realms/emhare/roles/applicant" \
  -H "Authorization: Bearer ${keycloak_admin_token}")
reviewer_roles=$(for role_name in admissions-officer system-admin; do
  curl -fsS "${keycloak_base_url}/admin/realms/emhare/roles/${role_name}" \
    -H "Authorization: Bearer ${keycloak_admin_token}"
done | jq -s '.')

curl -fsS -o /dev/null -X POST \
  "${keycloak_base_url}/admin/realms/emhare/users/${applicant_keycloak_user_id}/role-mappings/realm" \
  -H "Authorization: Bearer ${keycloak_admin_token}" \
  -H 'Content-Type: application/json' \
  -d "[${applicant_role}]"
curl -fsS -o /dev/null -X POST \
  "${keycloak_base_url}/admin/realms/emhare/users/${reviewer_keycloak_user_id}/role-mappings/realm" \
  -H "Authorization: Bearer ${keycloak_admin_token}" \
  -H 'Content-Type: application/json' \
  -d "${reviewer_roles}"

login() {
  curl -fsS -X POST "${keycloak_base_url}/realms/emhare/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode grant_type=password \
    --data-urlencode client_id="${keycloak_client_id}" \
    --data-urlencode username="$1" \
    --data-urlencode password="${test_password}" | jq -er .access_token
}

applicant_access_token=$(login "${applicant_email}")
reviewer_access_token=$(login "${reviewer_email}")
applicant_core_user_id=$(curl -fsS "${gateway_base_url}/api/core/me" \
  -H "Authorization: Bearer ${applicant_access_token}" | jq -er .user.id)
reviewer_core_user_id=$(curl -fsS "${gateway_base_url}/api/core/me" \
  -H "Authorization: Bearer ${reviewer_access_token}" | jq -er .user.id)

docker exec -i "${postgres_container}" psql -q -v ON_ERROR_STOP=1 -U postgres -d emhare_admissions \
  -v admission_cycle_id="${admission_cycle_id}" \
  -v academic_year_id="${academic_year_id}" \
  -v intake_id="${intake_id}" \
  -v application_type_id="${application_type_id}" \
  -v applicant_id="${applicant_id}" \
  -v applicant_user_id="${applicant_core_user_id}" \
  -v applicant_email="${applicant_email}" \
  -v application_id="${application_id}" \
  -v application_number="${application_number}" <<'SQL'
INSERT INTO admission_cycles (
    id, academic_year_id, intake_id, code, name, opens_at, closes_at, status,
    maximum_programme_choices, created_at, updated_at, version
) VALUES (
    :'admission_cycle_id'::uuid, :'academic_year_id'::uuid, :'intake_id'::uuid,
    'E2E-DOC-' || left(:'admission_cycle_id', 8), 'Disposable document workflow cycle',
    now() - interval '1 day', now() + interval '30 days', 'OPEN', 3, now(), now(), 0
);
INSERT INTO application_types (
    id, code, name, requires_employment_history, requires_referees, is_active,
    created_at, updated_at, version
) VALUES (
    :'application_type_id'::uuid, 'E2E-DOC-' || left(:'application_type_id', 8),
    'Disposable document workflow application', false, false, true, now(), now(), 0
);
INSERT INTO applicants (
    id, user_id, applicant_number, applicant_category_code, first_name, last_name,
    primary_email, created_at, updated_at, version
) VALUES (
    :'applicant_id'::uuid, :'applicant_user_id'::uuid,
    'E2E-APP-' || left(:'applicant_id', 8), 'LOCAL', 'Enterprise', 'Applicant',
    :'applicant_email', now(), now(), 0
);
INSERT INTO applications (
    id, admission_cycle_id, applicant_id, application_type_id, application_number,
    payment_required, status, created_at, updated_at, version
) VALUES (
    :'application_id'::uuid, :'admission_cycle_id'::uuid, :'applicant_id'::uuid,
    :'application_type_id'::uuid, :'application_number', false, 'DRAFT', now(), now(), 0
);
SQL

curl -fsS -X POST \
  "${gateway_base_url}/api/admissions/application-types/${application_type_id}/document-requirements" \
  -H "Authorization: Bearer ${reviewer_access_token}" \
  -H 'Content-Type: application/json' \
  -d '{"requirementCode":"NATIONAL_ID","requirementName":"National identity document","required":true,"sortOrder":1}' \
  | jq -e '.requirementCode == "NATIONAL_ID" and .required == true' >/dev/null

initial_register=$(curl -fsS \
  "${gateway_base_url}/api/admissions/applications/${application_id}/documents/mine" \
  -H "Authorization: Bearer ${applicant_access_token}")
jq -e '
  .requiredDocumentsUploaded == false
  and .requiredDocumentsVerified == false
  and .missingRequirementCodes == ["NATIONAL_ID"]
  and .requirements[0].state == "MISSING"
' <<<"${initial_register}" >/dev/null

invalid_upload_response=$(curl -sS -w $'\n%{http_code}' -X POST \
  "${gateway_base_url}/api/documents/uploads" \
  -H "Authorization: Bearer ${applicant_access_token}" \
  -F ownerType=APPLICATION \
  -F ownerId="${application_id}" \
  -F documentTypeCode=NATIONAL_ID \
  -F "file=@${invalid_document_file};type=application/pdf")
[[ "$(tail -n 1 <<<"${invalid_upload_response}")" == '400' ]]

uploaded_document=$(curl -fsS -X POST "${gateway_base_url}/api/documents/uploads" \
  -H "Authorization: Bearer ${applicant_access_token}" \
  -F ownerType=APPLICATION \
  -F ownerId="${application_id}" \
  -F documentTypeCode=NATIONAL_ID \
  -F "file=@${valid_document_file};type=image/png")
document_id=$(jq -er .id <<<"${uploaded_document}")
document_version=$(jq -er .version <<<"${uploaded_document}")
jq -e \
  --arg ownerId "${application_id}" \
  --arg uploaderId "${applicant_keycloak_user_id}" '
  .ownerType == "APPLICATION"
  and .ownerId == $ownerId
  and .documentTypeCode == "NATIONAL_ID"
  and .verificationStatus == "PENDING"
  and .uploadedByUserId == $uploaderId
  and .mimeType == "image/png"
  and (.checksumSha256 | length) == 64
  and .version == 0
' <<<"${uploaded_document}" >/dev/null

download_record=$(curl -fsS \
  "${gateway_base_url}/api/documents/uploads/${document_id}/download" \
  -H "Authorization: Bearer ${applicant_access_token}")
curl -fsS "$(jq -er .downloadUrl <<<"${download_record}")" -o "${downloaded_document_file}"
expected_checksum=$(shasum -a 256 "${valid_document_file}" | awk '{print $1}')
downloaded_checksum=$(shasum -a 256 "${downloaded_document_file}" | awk '{print $1}')
[[ "${expected_checksum}" == "${downloaded_checksum}" ]]
[[ "${expected_checksum}" == "$(jq -er .checksumSha256 <<<"${download_record}")" ]]

linked_register=$(curl -fsS -X POST \
  "${gateway_base_url}/api/admissions/applications/${application_id}/documents" \
  -H "Authorization: Bearer ${applicant_access_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg documentId "${document_id}" \
    '{documentId:$documentId,requirementCode:"NATIONAL_ID"}')")
jq -e --arg documentId "${document_id}" '
  .requiredDocumentsUploaded == true
  and .requiredDocumentsVerified == false
  and .pendingRequirementCodes == ["NATIONAL_ID"]
  and .requirements[0].state == "PENDING"
  and .requirements[0].documentId == $documentId
' <<<"${linked_register}" >/dev/null

applicant_rejection_status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST \
  "${gateway_base_url}/api/documents/uploads/${document_id}/reject" \
  -H "Authorization: Bearer ${applicant_access_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --argjson expectedVersion "${document_version}" \
    '{expectedVersion:$expectedVersion,reason:"Applicant must not be able to reject evidence."}')")
[[ "${applicant_rejection_status}" == '403' ]]

rejection_reason='The identity image is unreadable and must be replaced.'
rejected_document=$(curl -fsS -X POST \
  "${gateway_base_url}/api/documents/uploads/${document_id}/reject" \
  -H "Authorization: Bearer ${reviewer_access_token}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc \
    --argjson expectedVersion "${document_version}" \
    --arg reason "${rejection_reason}" \
    '{expectedVersion:$expectedVersion,reason:$reason}')")
rejected_document_version=$(jq -er .version <<<"${rejected_document}")
jq -e \
  --arg reviewerId "${reviewer_keycloak_user_id}" \
  --arg reason "${rejection_reason}" '
  .verificationStatus == "REJECTED"
  and .verifiedByUserId == $reviewerId
  and .rejectionReason == $reason
  and .version == 1
' <<<"${rejected_document}" >/dev/null

projected_register=''
workflow_count=0
notification_request_count=0
in_app_notification_count=0
for _ in $(seq 1 120); do
  projected_register=$(curl -fsS \
    "${gateway_base_url}/api/admissions/applications/${application_id}/documents/mine" \
    -H "Authorization: Bearer ${applicant_access_token}")
  workflow_count=$(docker exec "${postgres_container}" psql -qAt -U postgres \
    -d emhare_core_identity \
    -c "select count(*) from workflow_instances where workflow_code='APPLICATION_DOCUMENT_CORRECTION' and subject_id='${application_id}'::uuid and deleted_at is null;")
  notification_request_count=$(docker exec "${postgres_container}" psql -qAt -U postgres \
    -d emhare_notifications \
    -c "select count(*) from notification_requests where idempotency_key like 'admissions:missing-document:${document_id}:%';")
  in_app_notification_count=$(docker exec "${postgres_container}" psql -qAt -U postgres \
    -d emhare_notifications \
    -c "select count(*) from in_app_notifications where notification_request_id in (select id from notification_requests where idempotency_key like 'admissions:missing-document:${document_id}:%');")
  if jq -e '
      .rejectedRequirementCodes == ["NATIONAL_ID"]
      and .requirements[0].state == "REJECTED"
      and .requirements[0].documentVersion == 1
    ' <<<"${projected_register}" >/dev/null \
      && [[ "${workflow_count}" == '1' ]] \
      && [[ "${notification_request_count}" == '2' ]] \
      && [[ "${in_app_notification_count}" == '1' ]]; then
    break
  fi
  sleep 0.25
done

jq -e '
  .requiredDocumentsUploaded == false
  and .requiredDocumentsVerified == false
  and .rejectedRequirementCodes == ["NATIONAL_ID"]
  and .requirements[0].state == "REJECTED"
  and .requirements[0].documentVersion == 1
' <<<"${projected_register}" >/dev/null
[[ "${workflow_count}" == '1' ]]
[[ "${notification_request_count}" == '2' ]]
[[ "${in_app_notification_count}" == '1' ]]

workflow_evidence=$(docker exec "${postgres_container}" psql -qAt -F '|' -U postgres \
  -d emhare_core_identity \
  -c "select wi.workflow_code,wi.initiated_by_user_id,wt.assigned_user_id,wt.status,(wt.due_at is not null),count(*) over() from workflow_instances wi join workflow_tasks wt on wt.workflow_instance_id=wi.id where wi.subject_id='${application_id}'::uuid and wi.workflow_code='APPLICATION_DOCUMENT_CORRECTION';")
IFS='|' read -r workflow_code workflow_actor workflow_assignee workflow_status due_date_present workflow_rows \
  <<<"${workflow_evidence}"
[[ "${workflow_code}" == 'APPLICATION_DOCUMENT_CORRECTION' ]]
[[ "${workflow_actor}" == "${reviewer_core_user_id}" ]]
[[ "${workflow_assignee}" == "${applicant_core_user_id}" ]]
[[ "${workflow_status}" == 'OPEN' ]]
[[ "${due_date_present}" == 't' ]]
[[ "${workflow_rows}" == '1' ]]

document_outbox_payload=$(docker exec "${postgres_container}" psql -qAt -U postgres \
  -d emhare_documents_reporting \
  -c "select payload::text from integration_outbox where payload->>'documentId'='${document_id}' and payload->>'documentVersion'='${rejected_document_version}';")
[[ -n "${document_outbox_payload}" ]]
admissions_outbox_count_before_replay=$(docker exec "${postgres_container}" psql -qAt -U postgres \
  -d emhare_admissions \
  -c "select count(*) from integration_outbox where payload->>'applicationId'='${application_id}';")

publish_response=$(curl -fsS -u "${RABBITMQ_USERNAME:-guest}:${RABBITMQ_PASSWORD:-guest}" \
  -X POST "${rabbitmq_management_url}/api/exchanges/%2F/emhare.events/publish" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg payload "${document_outbox_payload}" '
    {
      properties:{
        content_type:"application/json",
        message_id:"duplicate-e2e-replay",
        type:"documents-reporting.document-verification-changed.v1",
        headers:{"source-service":"documents-reporting-service"}
      },
      routing_key:"documents-reporting.document-verification-changed.v1",
      payload:$payload,
      payload_encoding:"string"
    }
  ')")
jq -e '.routed == true' <<<"${publish_response}" >/dev/null
sleep 3

admissions_outbox_count_after_replay=$(docker exec "${postgres_container}" psql -qAt -U postgres \
  -d emhare_admissions \
  -c "select count(*) from integration_outbox where payload->>'applicationId'='${application_id}';")
workflow_count_after_replay=$(docker exec "${postgres_container}" psql -qAt -U postgres \
  -d emhare_core_identity \
  -c "select count(*) from workflow_instances where workflow_code='APPLICATION_DOCUMENT_CORRECTION' and subject_id='${application_id}'::uuid and deleted_at is null;")
notification_count_after_replay=$(docker exec "${postgres_container}" psql -qAt -U postgres \
  -d emhare_notifications \
  -c "select count(*) from notification_requests where idempotency_key like 'admissions:missing-document:${document_id}:%';")
[[ "${admissions_outbox_count_before_replay}" == "${admissions_outbox_count_after_replay}" ]]
[[ "${workflow_count}" == "${workflow_count_after_replay}" ]]
[[ "${notification_request_count}" == "${notification_count_after_replay}" ]]

dead_letter_counts=$(curl -fsS \
  -u "${RABBITMQ_USERNAME:-guest}:${RABBITMQ_PASSWORD:-guest}" \
  "${rabbitmq_management_url}/api/queues/%2F" \
  | jq -c '[.[] | select(.name | test("(document-verification-changed|missing-application-document-workflow-requested).*dead$")) | {name,messages}]')
jq -e 'all(.[]; .messages == 0)' <<<"${dead_letter_counts}" >/dev/null

jq -n \
  --arg runId "${run_id}" \
  --arg applicationId "${application_id}" \
  --arg applicationNumber "${application_number}" \
  --arg documentId "${document_id}" \
  --arg applicantUserId "${applicant_core_user_id}" \
  --arg reviewerUserId "${reviewer_core_user_id}" \
  --arg checksum "${expected_checksum}" \
  --argjson documentVersion "${rejected_document_version}" \
  --argjson notificationRequests "${notification_request_count}" \
  --argjson correctionWorkflows "${workflow_count}" \
  --argjson admissionsOutboxEvents "${admissions_outbox_count_after_replay}" \
  --argjson deadQueues "${dead_letter_counts}" \
  '{
    status:"PASS",
    runId:$runId,
    applicationId:$applicationId,
    applicationNumber:$applicationNumber,
    documentId:$documentId,
    documentVersion:$documentVersion,
    checksum:$checksum,
    applicantUserId:$applicantUserId,
    reviewerUserId:$reviewerUserId,
    notificationRequests:$notificationRequests,
    correctionWorkflows:$correctionWorkflows,
    admissionsOutboxEvents:$admissionsOutboxEvents,
    deadQueues:$deadQueues,
    retainedEvidence:true
  }'
