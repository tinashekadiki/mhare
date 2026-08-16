// Author: Tinashe K

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const processManagerSource = readFileSync(
  resolve(process.cwd(), 'infrastructure/dev/manage-local-process.sh'),
  'utf8'
)

describe('local service process manager', () => {
  it('uses the readiness health group for every backend service', () => {
    expect(processManagerSource).toContain(
      "printf 'http://localhost:%s/actuator/health/readiness' \"${process_port}\""
    )
    expect(processManagerSource).not.toContain(
      "printf 'http://localhost:%s/actuator/health' \"${process_port}\""
    )
  })

  it('launches backend services from a runtime jar copy that builds cannot overwrite', () => {
    expect(processManagerSource).toContain(
      'runtime_service_jar="${runtime_jar_directory}/${process_name}.jar"'
    )
    expect(processManagerSource).toContain(
      'cp "${service_jar}" "${temporary_runtime_service_jar}"'
    )
    expect(processManagerSource).toContain(
      'java "${jvm_arguments[@]}" -jar "${runtime_service_jar}"'
    )
  })
})
