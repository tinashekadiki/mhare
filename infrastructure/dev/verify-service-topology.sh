#!/usr/bin/env bash

# Author: Tinashe K
# Verifies that every gateway upstream is healthy on its canonical local port.

set -uo pipefail

gateway_base_url="${GATEWAY_BASE_URL:-http://localhost:8080}"
discovery_base_url="${EUREKA_BASE_URL:-http://localhost:8761}"
discovery_username="${EUREKA_USERNAME:-emhare-discovery}"
discovery_password="${EUREKA_PASSWORD:-emhare-discovery-dev}"
request_timeout_seconds="${EMHARE_HEALTH_TIMEOUT_SECONDS:-2}"
failed_service_count=0

services=(
  "Eureka Discovery|8761"
  "API Gateway|8080"
  "Core Identity|8081"
  "Academic Setup|8082"
  "Admissions|8083"
  "Finance|8084"
  "Student Records|8085"
  "Assessment and Results|8086"
  "Exams and Timetabling|8087"
  "Accommodation|8088"
  "Dining|8089"
  "Documents and Reporting|8090"
  "Notifications|8091"
)

probe_health() {
  local base_url="$1"
  local response response_body http_status
  response="$(curl -sS --max-time "${request_timeout_seconds}" -w $'\n%{http_code}' "${base_url}/actuator/health" 2>/dev/null || printf '\n000')"
  http_status="${response##*$'\n'}"
  response_body="${response%$'\n'*}"
  if [[ "${http_status}" == "200" && "${response_body}" == *'"status":"UP"'* ]]; then
    return 0
  fi
  return 1
}

printf '%-28s %-28s %-10s %s\n' "SERVICE" "EXPECTED ENDPOINT" "RESULT" "DETAIL"
printf '%-28s %-28s %-10s %s\n' "----------------------------" "----------------------------" "----------" "------"

for service_entry in "${services[@]}"; do
  service_name="${service_entry%%|*}"
  canonical_port="${service_entry##*|}"
  canonical_base_url="http://localhost:${canonical_port}"

  if probe_health "${canonical_base_url}"; then
    printf '%-28s %-28s %-10s %s\n' "${service_name}" "${canonical_base_url}" "UP" "canonical route available"
    continue
  fi

  alternate_port="$((canonical_port + 10000))"
  alternate_base_url="http://localhost:${alternate_port}"
  if probe_health "${alternate_base_url}"; then
    printf '%-28s %-28s %-10s %s\n' "${service_name}" "${canonical_base_url}" "MISROUTED" "healthy on ${alternate_base_url}; gateway will not reach it"
  else
    printf '%-28s %-28s %-10s %s\n' "${service_name}" "${canonical_base_url}" "DOWN" "no healthy process on the canonical port"
  fi
  failed_service_count=$((failed_service_count + 1))
done

expected_registry_service_ids=(
  "api-gateway"
  "core-identity-service"
  "academic-setup-service"
  "admissions-service"
  "finance-service"
  "student-records-service"
  "assessment-results-service"
  "exams-timetabling-service"
  "accommodation-service"
  "dining-service"
  "documents-reporting-service"
  "notifications-service"
)

registry_response="$(curl -fsS --max-time "${request_timeout_seconds}" \
  --user "${discovery_username}:${discovery_password}" \
  --header 'Accept: application/json' \
  "${discovery_base_url}/eureka/apps" 2>/dev/null || true)"

printf '\nEureka registrations:\n'
if [[ -z "${registry_response}" ]]; then
  printf '  UNAVAILABLE · could not read %s/eureka/apps\n' "${discovery_base_url}"
  failed_service_count=$((failed_service_count + 1))
else
  registered_service_ids="$(jq -r '.applications.application[]?.name // empty' <<<"${registry_response}" \
    | tr '[:upper:]' '[:lower:]')"
  for expected_service_id in "${expected_registry_service_ids[@]}"; do
    if grep -Fxq "${expected_service_id}" <<<"${registered_service_ids}"; then
      printf '  %-32s REGISTERED\n' "${expected_service_id}"
    else
      printf '  %-32s MISSING\n' "${expected_service_id}"
      failed_service_count=$((failed_service_count + 1))
    fi
  done
fi

readiness_response="$(curl -sS --max-time "${request_timeout_seconds}" -w $'\n%{http_code}' "${gateway_base_url}/actuator/health/readiness" 2>/dev/null || printf '\n000')"
readiness_http_status="${readiness_response##*$'\n'}"
readiness_body="${readiness_response%$'\n'*}"

printf '\nGateway readiness: HTTP %s' "${readiness_http_status}"
if [[ "${readiness_http_status}" == "200" && "${readiness_body}" == *'"status":"UP"'* ]]; then
  printf ' · UP\n'
else
  printf ' · DOWN\n'
  failed_service_count=$((failed_service_count + 1))
fi

if ((failed_service_count > 0)); then
  printf '\nTopology check failed: %s service or readiness check(s) require attention.\n' "${failed_service_count}" >&2
  exit 1
fi

printf '\nTopology check passed: every configured service is healthy and registered in Eureka.\n'
