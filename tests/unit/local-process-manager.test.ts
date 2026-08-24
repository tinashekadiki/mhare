// Author: Tinashe K

import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const processManagerSource = readFileSync(
  resolve(process.cwd(), "infrastructure/dev/manage-local-process.sh"),
  "utf8",
);
const makefileSource = readFileSync(resolve(process.cwd(), "Makefile"), "utf8");
const topologyVerifierSource = readFileSync(
  resolve(process.cwd(), "infrastructure/dev/verify-service-topology.sh"),
  "utf8",
);

describe("local service process manager", () => {
  it("uses the readiness health group for every backend service", () => {
    expect(processManagerSource).toContain(
      "printf 'http://localhost:%s/actuator/health/readiness' \"${process_port}\"",
    );
    expect(processManagerSource).not.toContain(
      "printf 'http://localhost:%s/actuator/health' \"${process_port}\"",
    );
  });

  it("launches backend services from a runtime jar copy that builds cannot overwrite", () => {
    expect(processManagerSource).toContain(
      'runtime_service_jar="${runtime_jar_directory}/${process_name}.jar"',
    );
    expect(processManagerSource).toContain(
      'cp "${service_jar}" "${temporary_runtime_service_jar}"',
    );
    expect(processManagerSource).toContain(
      'java "${jvm_arguments[@]}" -jar "${runtime_service_jar}"',
    );
  });

  it("recognises its copied runtime jar as a checkout-owned process", () => {
    expect(processManagerSource).toContain(
      'expected_runtime_jar="${runtime_jar_directory}/${process_name}.jar"',
    );
    expect(processManagerSource).toContain(
      '[[ "${command_text}" == *"${project_root}"* || "${command_text}" == *"${expected_runtime_jar}"* ]]',
    );
  });

  it("uses the canonical portal ports and forwards them to Nuxt", () => {
    expect(makefileSource).toContain("ADMIN_PORTAL_PORT ?= 3100");
    expect(makefileSource).toContain("APPLICANT_PORTAL_PORT ?= 3001");
    expect(makefileSource).toContain("STUDENT_PORTAL_PORT ?= 3002");
    expect(makefileSource).toContain(
      "admin-portal) echo $(ADMIN_PORTAL_PORT);; applicant-portal) echo $(APPLICANT_PORTAL_PORT);; student-portal) echo $(STUDENT_PORTAL_PORT);;",
    );
    expect(makefileSource).toContain(
      "'Admin / Applicant / Student' '$(ADMIN_PORTAL_PORT) / $(APPLICANT_PORTAL_PORT) / $(STUDENT_PORTAL_PORT)'",
    );
    expect(makefileSource).toContain("npm run admin:dev -- --port $(ADMIN_PORTAL_PORT)");
    expect(processManagerSource).toContain('npm run "${npm_script}" -- --port "${process_port}"');
    expect(processManagerSource).toContain(
      'VITE_EMHARE_STAFF_PORTAL_URL="${EMHARE_ADMIN_PORTAL_URL:-http://localhost:3100}"',
    );
  });

  it("waits through the Eureka registry response-cache convergence window", () => {
    expect(topologyVerifierSource).toContain(
      'registry_probe_attempts="${EMHARE_REGISTRY_PROBE_ATTEMPTS:-45}"',
    );
  });
});
