#!/usr/bin/env bash

# Author: Tinashe K
# Starts and stops local eMhare backend and frontend processes for the Makefile.

set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
local_environment_file="${project_root}/.env"
if [[ -f "${local_environment_file}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${local_environment_file}"
  set +a
fi
run_directory="${EMHARE_RUN_DIR:-/private/tmp/emhare}"
log_directory="${run_directory}/logs"
pid_directory="${run_directory}/pids"
runtime_jar_directory="${run_directory}/jars"
startup_timeout_seconds="${EMHARE_STARTUP_TIMEOUT_SECONDS:-180}"

usage() {
  printf 'Usage: %s <start|stop|force-stop|status|wait> <backend|frontend> <name> <port>\n' "$0" >&2
  exit 2
}

[[ $# -eq 4 ]] || usage

action="$1"
process_kind="$2"
process_name="$3"
process_port="$4"
health_request_timeout_seconds=3
[[ "${action}" == "status" ]] && health_request_timeout_seconds=2

case "${process_kind}" in
  backend|frontend) ;;
  *) usage ;;
esac

mkdir -p "${log_directory}" "${pid_directory}" "${runtime_jar_directory}"

pid_file="${pid_directory}/${process_name}.pid"
log_file="${log_directory}/${process_name}.log"
screen_session_name="emhare-${process_name}"

health_url() {
  if [[ "${process_kind}" == "backend" ]]; then
    printf 'http://localhost:%s/actuator/health/readiness' "${process_port}"
  else
    printf 'http://localhost:%s/' "${process_port}"
  fi
}

is_ready() {
  local response
  if [[ "${process_kind}" == "backend" ]]; then
    response="$(curl -fsS --max-time "${health_request_timeout_seconds}" "$(health_url)" 2>/dev/null || true)"
    [[ "${response}" == *'"status":"UP"'* ]]
  else
    curl -fsS --max-time "${health_request_timeout_seconds}" -o /dev/null "$(health_url)" 2>/dev/null
  fi
}

listening_pid() {
  lsof -nP -tiTCP:"${process_port}" -sTCP:LISTEN 2>/dev/null | head -n 1
}

process_command() {
  ps -p "$1" -o command= 2>/dev/null || true
}

screen_session_exists() {
  local screen_sessions
  screen_sessions="$(screen -ls 2>/dev/null || true)"
  [[ "${screen_sessions}" == *".${screen_session_name}"* ]]
}

is_repo_owned_command() {
  local command_text="$1"
  local expected_runtime_jar="${runtime_jar_directory}/${process_name}.jar"

  [[ "${command_text}" == *"${project_root}"* || "${command_text}" == *"${expected_runtime_jar}"* ]]
}

repo_owned_process_root() {
  local current_pid="$1"
  local current_command parent_pid parent_command

  current_command="$(process_command "${current_pid}")"
  is_repo_owned_command "${current_command}" || return 1

  while true; do
    parent_pid="$(ps -p "${current_pid}" -o ppid= 2>/dev/null | tr -d ' ' || true)"
    [[ -n "${parent_pid}" && "${parent_pid}" -gt 1 ]] || break
    parent_command="$(process_command "${parent_pid}")"
    is_repo_owned_command "${parent_command}" || break
    current_pid="${parent_pid}"
  done

  printf '%s' "${current_pid}"
}

terminate_process_tree() {
  local parent_pid="$1"
  local child_pid

  while IFS= read -r child_pid; do
    [[ -n "${child_pid}" ]] && terminate_process_tree "${child_pid}"
  done < <(pgrep -P "${parent_pid}" 2>/dev/null || true)

  kill "${parent_pid}" 2>/dev/null || true
}

stop_managed_process() {
  local managed_pid port_pid root_pid session_was_managed=false

  if screen_session_exists; then
    session_was_managed=true
    printf 'Stopping %s (screen: %s)\n' "${process_name}" "${screen_session_name}"
    screen -S "${screen_session_name}" -X quit >/dev/null 2>&1 || true
    for _ in {1..20}; do
      screen_session_exists || break
      sleep 0.25
    done
  fi

  # Old macOS screen versions may close the session without forwarding the
  # termination signal to the launched Java or Node process. If this target
  # owned the screen session, also stop the remaining checkout-owned listener.
  if [[ "${session_was_managed}" == true ]]; then
    port_pid="$(listening_pid || true)"
    if [[ -n "${port_pid}" ]]; then
      root_pid="$(repo_owned_process_root "${port_pid}" || true)"
      if [[ -n "${root_pid}" ]]; then
        terminate_process_tree "${root_pid}"
        for _ in {1..20}; do
          [[ -z "$(listening_pid || true)" ]] && break
          sleep 0.25
        done
        port_pid="$(listening_pid || true)"
        if [[ -n "${port_pid}" ]]; then
          root_pid="$(repo_owned_process_root "${port_pid}" || true)"
          [[ -n "${root_pid}" ]] && kill -KILL "${port_pid}" "${root_pid}" 2>/dev/null || true
        fi
      fi
    fi
  fi

  # Compatibility cleanup for processes created by the previous nohup-based
  # manager. New processes are owned by named screen sessions.
  [[ -f "${pid_file}" ]] || return 0

  managed_pid="$(cat "${pid_file}")"
  if kill -0 "${managed_pid}" 2>/dev/null; then
    printf 'Stopping %s (pid %s)\n' "${process_name}" "${managed_pid}"
    terminate_process_tree "${managed_pid}"
    for _ in {1..20}; do
      kill -0 "${managed_pid}" 2>/dev/null || break
      sleep 0.25
    done
    kill -KILL "${managed_pid}" 2>/dev/null || true
  fi
  rm -f "${pid_file}"
}

force_stop_repo_process() {
  local port_pid root_pid

  stop_managed_process
  port_pid="$(listening_pid || true)"
  [[ -n "${port_pid}" ]] || return 0

  root_pid="$(repo_owned_process_root "${port_pid}" || true)"
  if [[ -z "${root_pid}" ]]; then
    printf 'Refusing to stop pid %s on port %s because it is not owned by this checkout.\n' "${port_pid}" "${process_port}" >&2
    printf 'Process: %s\n' "$(process_command "${port_pid}")" >&2
    exit 1
  fi

  printf 'Stopping repo-owned %s process tree (root pid %s, port %s)\n' "${process_name}" "${root_pid}" "${process_port}"
  terminate_process_tree "${root_pid}"
  for _ in {1..20}; do
    [[ -z "$(listening_pid || true)" ]] && return 0
    sleep 0.25
  done
  port_pid="$(listening_pid || true)"
  [[ -n "${port_pid}" ]] && kill -KILL "${port_pid}" "${root_pid}" 2>/dev/null || true
}

start_process() {
  local port_pid

  if is_ready; then
    printf '%-32s already ready at %s\n' "${process_name}" "$(health_url)"
    return 0
  fi

  if screen_session_exists; then
    printf '%-32s already starting in screen session %s\n' "${process_name}" "${screen_session_name}"
    return 0
  fi

  port_pid="$(listening_pid || true)"
  if [[ -n "${port_pid}" ]]; then
    printf '%s cannot start: port %s is occupied by pid %s but its health check is not ready.\n' "${process_name}" "${process_port}" "${port_pid}" >&2
    printf 'Run `make force-stop SERVICE=%s` after confirming the process belongs to this checkout.\n' "${process_name}" >&2
    exit 1
  fi

  rm -f "${pid_file}"
  : > "${log_file}"
  printf 'Starting %s (screen: %s, log: %s)\n' "${process_name}" "${screen_session_name}" "${log_file}"

  if [[ "${process_kind}" == "backend" ]]; then
    local service_jar runtime_service_jar temporary_runtime_service_jar jvm_arguments_text
    local -a jvm_arguments
    service_jar="$(find "${project_root}/services/${process_name}/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | head -n 1)"
    if [[ -z "${service_jar}" ]]; then
      printf 'No packaged jar found for %s. Run `make build-all` or `make up` first.\n' "${process_name}" >&2
      exit 1
    fi
    runtime_service_jar="${runtime_jar_directory}/${process_name}.jar"
    temporary_runtime_service_jar="${runtime_service_jar}.tmp.$$"
    cp "${service_jar}" "${temporary_runtime_service_jar}"
    mv -f "${temporary_runtime_service_jar}" "${runtime_service_jar}"
    jvm_arguments_text="${EMHARE_SERVICE_JVM_ARGUMENTS:--Xms32m -Xmx256m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC}"
    read -r -a jvm_arguments <<< "${jvm_arguments_text}"
    screen -dmS "${screen_session_name}" bash -c \
      'working_directory="$1"; log_path="$2"; shift 2; cd "${working_directory}"; exec "$@" >"${log_path}" 2>&1' \
      _ "${project_root}/services/${process_name}" "${log_file}" env \
      EUREKA_INSTANCE_IP_ADDRESS="${EUREKA_INSTANCE_IP_ADDRESS:-127.0.0.1}" \
      EUREKA_PREFER_IP_ADDRESS="${EUREKA_PREFER_IP_ADDRESS:-true}" \
      NOTIFICATIONS_DELIVERY_PROVIDER="${NOTIFICATIONS_DELIVERY_PROVIDER:-local-log}" \
      java "${jvm_arguments[@]}" -jar "${runtime_service_jar}"
  else
    local npm_script portal_kind
    case "${process_name}" in
      admin-portal) npm_script="admin:dev"; portal_kind="staff" ;;
      applicant-portal) npm_script="applicant:dev"; portal_kind="applicant" ;;
      student-portal) npm_script="student:dev"; portal_kind="student" ;;
      *) printf 'Unknown frontend: %s\n' "${process_name}" >&2; exit 2 ;;
    esac
    screen -dmS "${screen_session_name}" bash -c \
      'working_directory="$1"; log_path="$2"; shift 2; cd "${working_directory}"; exec "$@" >"${log_path}" 2>&1' \
      _ "${project_root}" "${log_file}" env \
      VITE_EMHARE_PORTAL_KIND="${portal_kind}" \
      VITE_EMHARE_STAFF_PORTAL_URL="${EMHARE_ADMIN_PORTAL_URL:-http://localhost:3100}" \
      VITE_EMHARE_APPLICANT_PORTAL_URL="${EMHARE_APPLICANT_PORTAL_URL:-http://localhost:3001}" \
      VITE_EMHARE_STUDENT_PORTAL_URL="${EMHARE_STUDENT_PORTAL_URL:-http://localhost:3002}" \
      NUXT_PUBLIC_STAFF_PORTAL_URL="${EMHARE_ADMIN_PORTAL_URL:-http://localhost:3100}" \
      NUXT_PUBLIC_APPLICANT_PORTAL_URL="${EMHARE_APPLICANT_PORTAL_URL:-http://localhost:3001}" \
      NUXT_PUBLIC_STUDENT_PORTAL_URL="${EMHARE_STUDENT_PORTAL_URL:-http://localhost:3002}" \
      npm run "${npm_script}" -- --port "${process_port}"
  fi
}

wait_until_ready() {
  local deadline
  deadline=$((SECONDS + startup_timeout_seconds))

  while ((SECONDS < deadline)); do
    if is_ready; then
      printf '%-32s READY (%s)\n' "${process_name}" "$(health_url)"
      return 0
    fi

    if ! screen_session_exists; then
      printf '%s exited before becoming ready. Last log lines:\n' "${process_name}" >&2
      tail -n 40 "${log_file}" >&2 || true
      exit 1
    fi
    sleep 2
  done

  printf '%s did not become ready within %s seconds. Last log lines:\n' "${process_name}" "${startup_timeout_seconds}" >&2
  tail -n 40 "${log_file}" >&2 || true
  exit 1
}

show_status() {
  local port_pid ownership="external"
  if is_ready; then
    screen_session_exists && ownership="managed"
    printf '%-32s READY      port=%-5s process=%s\n' "${process_name}" "${process_port}" "${ownership}"
    return 0
  fi

  port_pid="$(listening_pid || true)"
  if [[ -n "${port_pid}" ]]; then
    printf '%-32s UNHEALTHY  port=%-5s pid=%s\n' "${process_name}" "${process_port}" "${port_pid}"
  else
    printf '%-32s STOPPED    port=%-5s\n' "${process_name}" "${process_port}"
  fi
}

case "${action}" in
  start) start_process ;;
  wait) wait_until_ready ;;
  stop) stop_managed_process ;;
  force-stop) force_stop_repo_process ;;
  status) show_status ;;
  *) usage ;;
esac
