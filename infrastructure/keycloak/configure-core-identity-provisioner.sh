#!/usr/bin/env bash

# Author: Tinashe K

set -euo pipefail

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_directory="$(cd "${script_directory}/../.." && pwd)"

if [[ -f "${project_directory}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${project_directory}/.env"
  set +a
fi

keycloak_base_url="${KEYCLOAK_BASE_URL:-http://localhost:8099}"
keycloak_realm="${KEYCLOAK_REALM:-emhare}"
keycloak_admin_username="${KEYCLOAK_ADMIN_USERNAME:-admin}"
keycloak_admin_password="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
provisioning_client_id="${CORE_IDENTITY_KEYCLOAK_CLIENT_ID:-emhare-core-identity-provisioner}"
provisioning_client_secret="${CORE_IDENTITY_KEYCLOAK_CLIENT_SECRET:-}"

if [[ -z "${provisioning_client_secret}" ]]; then
  echo 'CORE_IDENTITY_KEYCLOAK_CLIENT_SECRET must be configured before Keycloak user provisioning can start.' >&2
  exit 1
fi

admin_access_token="$(curl --fail --silent --show-error \
  --request POST \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'client_id=admin-cli' \
  --data-urlencode "username=${keycloak_admin_username}" \
  --data-urlencode "password=${keycloak_admin_password}" \
  "${keycloak_base_url}/realms/master/protocol/openid-connect/token" \
  | jq --raw-output '.access_token // empty')"

if [[ -z "${admin_access_token}" ]]; then
  echo 'Keycloak did not issue an administration token.' >&2
  exit 1
fi

authorization_header="Authorization: Bearer ${admin_access_token}"
client_payload="$(jq --null-input \
  --arg client_id "${provisioning_client_id}" \
  --arg client_secret "${provisioning_client_secret}" \
  '{
    clientId: $client_id,
    name: "eMhare Core Identity Provisioner",
    enabled: true,
    publicClient: false,
    secret: $client_secret,
    serviceAccountsEnabled: true,
    standardFlowEnabled: false,
    directAccessGrantsEnabled: false,
    authorizationServicesEnabled: false,
    fullScopeAllowed: true,
    protocol: "openid-connect"
  }')"

client_uuid="$(curl --fail --silent --show-error \
  --header "${authorization_header}" \
  --get \
  --data-urlencode "clientId=${provisioning_client_id}" \
  "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients" \
  | jq --raw-output '.[0].id // empty')"

if [[ -z "${client_uuid}" ]]; then
  curl --fail --silent --show-error \
    --request POST \
    --header "${authorization_header}" \
    --header 'Content-Type: application/json' \
    --data "${client_payload}" \
    "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients" >/dev/null
  client_uuid="$(curl --fail --silent --show-error \
    --header "${authorization_header}" \
    --get \
    --data-urlencode "clientId=${provisioning_client_id}" \
    "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients" \
    | jq --raw-output '.[0].id // empty')"
else
  curl --fail --silent --show-error \
    --request PUT \
    --header "${authorization_header}" \
    --header 'Content-Type: application/json' \
    --data "${client_payload}" \
    "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients/${client_uuid}" >/dev/null
fi

if [[ -z "${client_uuid}" ]]; then
  echo 'The Keycloak provisioning client could not be resolved after configuration.' >&2
  exit 1
fi

service_account_user_id="$(curl --fail --silent --show-error \
  --header "${authorization_header}" \
  "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients/${client_uuid}/service-account-user" \
  | jq --raw-output '.id // empty')"

realm_management_client_uuid="$(curl --fail --silent --show-error \
  --header "${authorization_header}" \
  --get \
  --data-urlencode 'clientId=realm-management' \
  "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients" \
  | jq --raw-output '.[0].id // empty')"

if [[ -z "${service_account_user_id}" || -z "${realm_management_client_uuid}" ]]; then
  echo 'Keycloak service-account role configuration could not be resolved.' >&2
  exit 1
fi

role_mappings='[]'
for role_name in manage-users query-users view-users; do
  role_representation="$(curl --fail --silent --show-error \
    --header "${authorization_header}" \
    "${keycloak_base_url}/admin/realms/${keycloak_realm}/clients/${realm_management_client_uuid}/roles/${role_name}")"
  role_mappings="$(jq --argjson role "${role_representation}" '. + [$role]' <<<"${role_mappings}")"
done

curl --fail --silent --show-error \
  --request POST \
  --header "${authorization_header}" \
  --header 'Content-Type: application/json' \
  --data "${role_mappings}" \
  "${keycloak_base_url}/admin/realms/${keycloak_realm}/users/${service_account_user_id}/role-mappings/clients/${realm_management_client_uuid}" >/dev/null

provisioning_access_token="$(curl --fail --silent --show-error \
  --request POST \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode "client_id=${provisioning_client_id}" \
  --data-urlencode "client_secret=${provisioning_client_secret}" \
  "${keycloak_base_url}/realms/${keycloak_realm}/protocol/openid-connect/token" \
  | jq --raw-output '.access_token // empty')"

if [[ -z "${provisioning_access_token}" ]]; then
  echo 'The Keycloak provisioning client could not authenticate.' >&2
  exit 1
fi

curl --fail --silent --show-error \
  --header "Authorization: Bearer ${provisioning_access_token}" \
  --get \
  --data-urlencode 'max=1' \
  "${keycloak_base_url}/admin/realms/${keycloak_realm}/users" >/dev/null

echo "Keycloak provisioning client '${provisioning_client_id}' is configured for realm '${keycloak_realm}'."
